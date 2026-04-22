package com.ringhud.hud

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "VoiceManager"

sealed class VoiceState {
    object Idle     : VoiceState()
    object Listening: VoiceState()
    data class Partial(val text: String)  : VoiceState()
    data class Final(val text: String)    : VoiceState()
    data class Error(val message: String) : VoiceState()
}

@Singleton
class VoiceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var recognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening() {
        if (!isAvailable) {
            _state.value = VoiceState.Error("Speech recognition not available on this device")
            return
        }
        // SpeechRecognizer must be created/used on main thread
        stopListening()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
            startListening(buildIntent())
        }
        _state.value = VoiceState.Listening
        Log.d(TAG, "Listening…")
    }

    fun stopListening() {
        recognizer?.apply {
            stopListening()
            destroy()
        }
        recognizer = null
        _state.value = VoiceState.Idle
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onPartialResults(partial: Bundle?) {
            val text = partial
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: return
            _state.value = VoiceState.Partial(text)
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: return
            Log.d(TAG, "Recognised: $text")
            _state.value = VoiceState.Final(text)
        }

        override fun onError(error: Int) {
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH          -> "No speech detected"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT    -> "Listening timed out"
                SpeechRecognizer.ERROR_NETWORK           -> "Network error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied"
                else -> "Speech error $error"
            }
            Log.w(TAG, msg)
            _state.value = VoiceState.Error(msg)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun buildIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
}
