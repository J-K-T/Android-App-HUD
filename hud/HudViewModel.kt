package com.ringhud.hud

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ringhud.ble.BleConnectionManager
import com.ringhud.gesture.GestureEvent
import com.ringhud.gesture.GestureRepository
import com.ringhud.data.GestureDao
import com.ringhud.data.GestureRecord
import com.ringhud.platform.RingHudService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

private const val TAG = "HudViewModel"

@HiltViewModel
class HudViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bleManager: BleConnectionManager,
    private val gestureRepo: GestureRepository,
    private val claudeClient: ClaudeApiClient,
    private val voiceManager: VoiceManager,
    private val gestureDao: GestureDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(HudUiState())
    val uiState: StateFlow<HudUiState> = _uiState.asStateFlow()

    // Camera capture use case — injected from the Composable after CameraX init
    var imageCapture: ImageCapture? = null

    // Conversation history for multi-turn Claude chat
    private val conversationHistory = mutableListOf<Pair<String, String>>()

    // ── Initialisation ────────────────────────────────────────────────────────

    init {
        observeBleState()
        observeGestures()
        observeVoice()
        startRingService()
    }

    // ── Public actions ────────────────────────────────────────────────────────

    fun onCameraReady() { _uiState.update { it.copy(cameraReady = true) } }

    fun onCaptureAndAnalyze() {
        val capture = imageCapture ?: run {
            Log.w(TAG, "ImageCapture not ready")
            return
        }
        if (_uiState.value.isAnalyzing) return

        _uiState.update { it.copy(isAnalyzing = true) }
        val outFile = File.createTempFile("hud_capture", ".jpg", context.cacheDir)

        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(outFile).build(),
            context.mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val bytes = outFile.readBytes()
                    outFile.delete()
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    viewModelScope.launch(Dispatchers.IO) {
                        runDetection(base64)
                    }
                }
                override fun onError(e: ImageCaptureException) {
                    Log.e(TAG, "Capture failed: ${e.message}")
                    _uiState.update { it.copy(isAnalyzing = false) }
                }
            }
        )
    }

    fun onVoiceToggle() {
        if (_uiState.value.isListening) {
            voiceManager.stopListening()
        } else {
            voiceManager.startListening()
        }
    }

    fun onDismissAiResponse() {
        _uiState.update { it.copy(aiStatus = AiStatus.Idle) }
    }

    fun onHudOpacityChanged(opacity: Float) {
        _uiState.update { it.copy(hudOpacity = opacity) }
    }

    fun onBatteryLevelChanged(pct: Int) {
        _uiState.update { it.copy(batteryPct = pct) }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun observeBleState() {
        bleManager.state.onEach { bleState ->
            _uiState.update { it.copy(bleState = bleState) }
        }.launchIn(viewModelScope)
    }

    private fun observeGestures() {
        gestureRepo.gestures
            .debounce(150)          // suppress noise bursts
            .onEach { event ->
                _uiState.update { it.copy(lastGesture = event) }
                handleGesture(event)
                logGesture(event)
            }
            .launchIn(viewModelScope)
    }

    private fun handleGesture(event: GestureEvent) {
        when (event) {
            is GestureEvent.Tap        -> onCaptureAndAnalyze()
            is GestureEvent.DoubleTap  -> onVoiceToggle()
            is GestureEvent.Hold       -> { /* show gesture hints */ }
            is GestureEvent.HoldRelease -> { /* hide hints */ }
            is GestureEvent.Swipe      -> when (event.direction) {
                GestureEvent.Swipe.Direction.UP   -> onHudOpacityChanged(
                    (_uiState.value.hudOpacity + 0.1f).coerceAtMost(1f))
                GestureEvent.Swipe.Direction.DOWN -> onHudOpacityChanged(
                    (_uiState.value.hudOpacity - 0.1f).coerceAtLeast(0.3f))
                GestureEvent.Swipe.Direction.LEFT,
                GestureEvent.Swipe.Direction.RIGHT -> onDismissAiResponse()
            }
        }
    }

    private fun observeVoice() {
        voiceManager.state.onEach { voiceState ->
            when (voiceState) {
                is VoiceState.Listening -> _uiState.update { it.copy(isListening = true, transcript = "") }
                is VoiceState.Partial   -> _uiState.update { it.copy(transcript = voiceState.text) }
                is VoiceState.Final     -> {
                    _uiState.update { it.copy(isListening = false, transcript = "") }
                    sendVoiceQuery(voiceState.text)
                }
                is VoiceState.Error     -> _uiState.update { it.copy(isListening = false,
                    aiStatus = AiStatus.Error(voiceState.message)) }
                VoiceState.Idle         -> _uiState.update { it.copy(isListening = false) }
            }
        }.launchIn(viewModelScope)
    }

    private suspend fun runDetection(jpegBase64: String) {
        val objects = claudeClient.detectObjects(jpegBase64)
        _uiState.update { it.copy(detectedObjects = objects, isAnalyzing = false) }
        Log.d(TAG, "Detected ${objects.size} objects")
    }

    private fun sendVoiceQuery(query: String) {
        _uiState.update { it.copy(aiStatus = AiStatus.Thinking) }
        val context = _uiState.value.detectedObjects
            .joinToString(", ") { it.name }
            .let { if (it.isNotBlank()) "seeing: $it" else "" }

        viewModelScope.launch(Dispatchers.IO) {
            val response = claudeClient.chat(query, conversationHistory, context)
            conversationHistory.add("user" to query)
            conversationHistory.add("assistant" to response)
            // Keep last 10 turns to avoid context blowout
            while (conversationHistory.size > 20) conversationHistory.removeAt(0)
            _uiState.update { it.copy(aiStatus = AiStatus.Response(response)) }
            // Auto-dismiss after 8 s
            delay(8_000)
            if (_uiState.value.aiStatus is AiStatus.Response) {
                _uiState.update { it.copy(aiStatus = AiStatus.Idle) }
            }
        }
    }

    private fun logGesture(event: GestureEvent) {
        viewModelScope.launch(Dispatchers.IO) {
            gestureDao.insert(GestureRecord(
                timestampMs = System.currentTimeMillis(),
                gestureType = event::class.simpleName ?: "Unknown"
            ))
        }
    }

    private fun startRingService() {
        val intent = Intent(context, RingHudService::class.java)
        context.startForegroundService(intent)
    }
}
