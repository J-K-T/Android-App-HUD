package com.ringhud.hud.ui

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ringhud.ble.BleState
import com.ringhud.gesture.GestureEvent
import com.ringhud.hud.*
import kotlinx.coroutines.guava.await
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Cyan    = Color(0xFF00E5FF)
private val CyanDim = Color(0x6600E5FF)
private val Dark    = Color(0xCC0A0B0F)

@Composable
fun HudScreen(viewModel: HudViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Time ticker
    var timeText by remember { mutableStateOf(currentTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            timeText = currentTime()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── 1. Camera preview ───────────────────────────────────────────────
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            onCameraReady = { capture ->
                viewModel.imageCapture = capture
                viewModel.onCameraReady()
            }
        )

        // ── 2. Grid overlay ─────────────────────────────────────────────────
        GridOverlay(modifier = Modifier.fillMaxSize())

        // ── 3. Scan line ────────────────────────────────────────────────────
        ScanLine(modifier = Modifier.fillMaxSize())

        // ── 4. AR bounding boxes ────────────────────────────────────────────
        ArBoxesOverlay(
            objects = state.detectedObjects,
            modifier = Modifier.fillMaxSize()
        )

        // ── 5. Central reticle ──────────────────────────────────────────────
        CentralReticle(
            isAnalyzing = state.isAnalyzing,
            modifier = Modifier.align(Alignment.Center)
        )

        // ── 6. Top status bar ────────────────────────────────────────────────
        TopStatusBar(
            timeText = timeText,
            batteryPct = state.batteryPct,
            bleState = state.bleState,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // ── 7. AR mode corner label ──────────────────────────────────────────
        CornerArLabel(
            bleState = state.bleState,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 20.dp, top = 72.dp)
        )

        // ── 8. Environment info card ─────────────────────────────────────────
        EnvironmentCard(
            objectCount = state.detectedObjects.size,
            isAnalyzing = state.isAnalyzing,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 16.dp, top = 72.dp)
        )

        // ── 9. Voice transcript bubble ───────────────────────────────────────
        AnimatedVisibility(
            visible = state.isListening && state.transcript.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit  = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 112.dp, start = 20.dp, end = 20.dp)
        ) {
            TranscriptBubble(text = state.transcript)
        }

        // ── 10. Claude AI response ───────────────────────────────────────────
        AnimatedVisibility(
            visible = state.aiStatus is AiStatus.Response,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit  = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 112.dp, start = 20.dp, end = 20.dp)
        ) {
            val text = (state.aiStatus as? AiStatus.Response)?.text ?: ""
            AssistantResponseCard(
                text = text,
                onDismiss = viewModel::onDismissAiResponse
            )
        }

        // ── 11. Last gesture flash ────────────────────────────────────────────
        GestureFlash(
            gesture = state.lastGesture,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 130.dp)
        )

        // ── 12. Bottom control bar ────────────────────────────────────────────
        BottomControlBar(
            isListening    = state.isListening,
            isAnalyzing    = state.isAnalyzing,
            aiThinking     = state.aiStatus is AiStatus.Thinking,
            onMicClick     = viewModel::onVoiceToggle,
            onCaptureClick = viewModel::onCaptureAndAnalyze,
            modifier       = Modifier.align(Alignment.BottomCenter)
        )

        // ── 13. Neural version label ──────────────────────────────────────────
        Text(
            text  = "CLAUDE v4.6",
            color = CyanDim,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 104.dp)
                .alpha(0.5f)
        )
    }
}

// ── Composable pieces ──────────────────────────────────────────────────────────

@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    onCameraReady: (ImageCapture) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val capture = ImageCapture.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture
                    )
                    onCameraReady(capture)
                } catch (e: Exception) {
                    // Camera unavailable — fallback bg stays visible
                }
            }, context.mainExecutor)
        }
    )
}

@Composable
private fun GridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val gridSize = 40.dp.toPx()
        val color = Color(0x08, 0xFF.toByte(), 0xFF.toByte(), 0x08)
        var x = 0f
        while (x < size.width) {
            drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.5f)
            x += gridSize
        }
        var y = 0f
        while (y < size.height) {
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5f)
            y += gridSize
        }
    }
}

@Composable
private fun ScanLine(modifier: Modifier = Modifier) {
    val anim = rememberInfiniteTransition(label = "scan")
    val yFrac by anim.animateFloat(
        initialValue = -0.15f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "y"
    )
    Canvas(modifier = modifier) {
        val y = size.height * yFrac
        val h = 200.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color(0x0C00E5FF), Color.Transparent),
                startY = y, endY = y + h
            ),
            topLeft = Offset(0f, y), size = Size(size.width, h)
        )
    }
}

@Composable
private fun ArBoxesOverlay(objects: List<ArObject>, modifier: Modifier = Modifier) {
    if (objects.isEmpty()) return
    Canvas(modifier = modifier) {
        objects.forEachIndexed { _, obj ->
            val l = size.width  * obj.x / 100f
            val t = size.height * obj.y / 100f
            val w = size.width  * obj.width  / 100f
            val h = size.height * obj.height / 100f
            val cornerLen = 16.dp.toPx()
            val sw = 2.dp.toPx()
            // Draw corner brackets
            listOf(
                Offset(l, t) to listOf(Offset(l + cornerLen, t), Offset(l, t + cornerLen)),
                Offset(l + w, t) to listOf(Offset(l + w - cornerLen, t), Offset(l + w, t + cornerLen)),
                Offset(l, t + h) to listOf(Offset(l + cornerLen, t + h), Offset(l, t + h - cornerLen)),
                Offset(l + w, t + h) to listOf(Offset(l + w - cornerLen, t + h), Offset(l + w, t + h - cornerLen))
            ).forEach { (corner, ends) ->
                ends.forEach { end ->
                    drawLine(Cyan, corner, end, sw, StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
private fun CentralReticle(isAnalyzing: Boolean, modifier: Modifier = Modifier) {
    val spinAnim = rememberInfiniteTransition(label = "spin")
    val angle by spinAnim.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)),
        label = "angle"
    )
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 1f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            tween(1000), repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    Box(modifier = modifier.size(128.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2; val cy = size.height / 2
            val r = size.minDimension / 2 - 2.dp.toPx()
            // Spinning outer ring
            val radAngle = angle * PI.toFloat() / 180f
            drawCircle(CyanDim, r, strokeWidth = 1.dp.toPx(), style = Stroke(1.dp.toPx()))
            // Tick marks on ring
            listOf(0f, 90f, 180f, 270f).forEach { deg ->
                val a = (deg + angle) * PI.toFloat() / 180f
                val inner = r - 12.dp.toPx(); val outer = r
                drawLine(Cyan,
                    Offset(cx + cos(a) * inner, cy + sin(a) * inner),
                    Offset(cx + cos(a) * outer, cy + sin(a) * outer),
                    2.dp.toPx()
                )
            }
            // Pulsing ring
            drawCircle(
                color = CyanDim.copy(alpha = 0.3f),
                radius = r * pulseScale,
                style = Stroke(1.dp.toPx())
            )
            // Crosshair lines
            val half = 24.dp.toPx()
            drawLine(Cyan, Offset(cx, cy - half), Offset(cx, cy - 8.dp.toPx()), 1.dp.toPx())
            drawLine(Cyan, Offset(cx, cy + 8.dp.toPx()), Offset(cx, cy + half), 1.dp.toPx())
            drawLine(Cyan, Offset(cx - half, cy), Offset(cx - 8.dp.toPx(), cy), 1.dp.toPx())
            drawLine(Cyan, Offset(cx + 8.dp.toPx(), cy), Offset(cx + half, cy), 1.dp.toPx())
            // Center dot
            drawCircle(if (isAnalyzing) Color.Yellow else Cyan, 3.dp.toPx(),
                center = Offset(cx, cy))
        }
    }
}

@Composable
private fun TopStatusBar(
    timeText: String,
    batteryPct: Int,
    bleState: BleState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color(0xCC0A0B0F), Color.Transparent))
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = timeText,
            color = Cyan, fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp, fontWeight = FontWeight.Medium
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // BLE indicator
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (bleState is BleState.Streaming) Color(0xFF4ADE80) else Color(0xFFF87171))
            )
            Icon(Icons.Default.Wifi, contentDescription = null, tint = Cyan, modifier = Modifier.size(16.dp))
            Icon(Icons.Default.BatteryFull, contentDescription = null, tint = Cyan, modifier = Modifier.size(18.dp))
            Text("$batteryPct%", color = Cyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun CornerArLabel(bleState: BleState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.alpha(0.8f)) {
        Text(
            "AR MODE", color = CyanDim, fontSize = 10.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 4.sp
        )
        val active = bleState is BleState.Streaming
        Text(
            text = if (active) "● ACTIVE" else "● DEMO",
            color = if (active) Color(0xFF4ADE80) else Color(0xFFF87171),
            fontSize = 11.sp, fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EnvironmentCard(objectCount: Int, isAnalyzing: Boolean, modifier: Modifier = Modifier) {
    val analyzeAnim by rememberInfiniteTransition(label = "env").animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a"
    )
    Surface(
        modifier = modifier.width(160.dp),
        color = Color(0xAA0A141E),
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("ENVIRONMENT", color = CyanDim, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace, letterSpacing = 3.sp)
            InfoRow("Objects",
                if (isAnalyzing) "…" else "$objectCount",
                if (isAnalyzing) Color.Yellow.copy(alpha = analyzeAnim) else Color(0xFF67E8F9))
            InfoRow("Focus", "LOCKED", Color(0xFF4ADE80))
            InfoRow("AI", if (isAnalyzing) "SCANNING" else "READY",
                if (isAnalyzing) Color.Yellow.copy(alpha = analyzeAnim) else Color(0xFF67E8F9))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF9CA3AF), fontSize = 11.sp)
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TranscriptBubble(text: String) {
    Surface(
        color = Color(0x15, 0xFF.toByte(), 0xFF.toByte(), 0x15),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            "\"$text\"",
            color = CyanDim, fontSize = 13.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            modifier = Modifier.padding(12.dp, 8.dp)
        )
    }
}

@Composable
private fun AssistantResponseCard(text: String, onDismiss: () -> Unit) {
    Surface(
        color = Color(0x15, 0xFF.toByte(), 0xFF.toByte(), 0x15),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Cyan,
                modifier = Modifier.size(20.dp).padding(top = 2.dp))
            Text(text, color = Color(0xFFE0F7FF), fontSize = 13.sp, lineHeight = 20.sp,
                modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = CyanDim,
                    modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun GestureFlash(gesture: GestureEvent?, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(gesture) {
        if (gesture == null) return@LaunchedEffect
        shown = when (gesture) {
            is GestureEvent.Tap       -> "TAP — analyzing"
            is GestureEvent.DoubleTap -> "DOUBLE TAP — voice"
            is GestureEvent.Hold      -> "HOLD"
            is GestureEvent.HoldRelease -> null
            is GestureEvent.Swipe     -> "SWIPE ${gesture.direction.name}"
        }
        kotlinx.coroutines.delay(1500)
        shown = null
    }
    AnimatedVisibility(visible = shown != null, enter = fadeIn(), exit = fadeOut(),
        modifier = modifier) {
        Surface(
            color = CyanDim.copy(alpha = 0.2f),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(shown ?: "", color = Cyan, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace, letterSpacing = 2.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun BottomControlBar(
    isListening: Boolean,
    isAnalyzing: Boolean,
    aiThinking: Boolean,
    onMicClick: () -> Unit,
    onCaptureClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0xE50A0B0F)))
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mic
            MicButton(isListening = isListening, onClick = onMicClick)
            // Capture
            CaptureButton(isAnalyzing = isAnalyzing || aiThinking, onClick = onCaptureClick)
            // Info (placeholder)
            IconButton(
                onClick = {},
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0x0DFFFFFF))
            ) {
                Icon(Icons.Default.Info, contentDescription = "Info",
                    tint = Color(0xFF9CA3AF), modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun MicButton(isListening: Boolean, onClick: () -> Unit) {
    val pulseAnim = rememberInfiniteTransition(label = "mic")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 1f, targetValue = 1.45f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "s"
    )
    Box(contentAlignment = Alignment.Center) {
        if (isListening) {
            Box(
                Modifier
                    .size(56.dp * pulseScale)
                    .clip(CircleShape)
                    .background(Cyan.copy(alpha = 0.15f))
            )
        }
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (isListening) Color(0x4000E5FF) else Color(0x0DFFFFFF))
        ) {
            Icon(
                if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = "Mic",
                tint = if (isListening) Cyan else Color(0xFF9CA3AF),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun CaptureButton(isAnalyzing: Boolean, onClick: () -> Unit) {
    val spinAnim = rememberInfiniteTransition(label = "cap")
    val angle by spinAnim.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(1500, easing = LinearEasing)), "ang"
    )
    Box(contentAlignment = Alignment.Center) {
        if (isAnalyzing) {
            Canvas(Modifier.size(74.dp)) {
                val r = size.minDimension / 2 - 2.dp.toPx()
                val sweep = 90f
                drawArc(Cyan, angle, sweep, false,
                    Offset(size.width/2 - r, size.height/2 - r),
                    Size(r*2, r*2), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        IconButton(
            onClick = onClick,
            enabled = !isAnalyzing,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0x0DFFFFFF))
        ) {
            Icon(Icons.Default.RadioButtonUnchecked, contentDescription = "Capture",
                tint = Cyan, modifier = Modifier.size(44.dp))
        }
    }
}

private fun currentTime(): String {
    val cal = java.util.Calendar.getInstance()
    val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val m = cal.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')
    val s = cal.get(java.util.Calendar.SECOND).toString().padStart(2, '0')
    return "$h:$m:$s"
}
