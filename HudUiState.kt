package com.ringhud.hud

import com.ringhud.ble.BleState
import com.ringhud.gesture.GestureEvent

/** An object detected by Claude Vision, positioned in normalised screen coordinates (0..1). */
data class ArObject(
    val id: Int,
    val name: String,
    val distance: String,
    val confidence: Float,
    val x: Float,        // left edge  (0..1)
    val y: Float,        // top edge   (0..1)
    val width: Float,    // box width  (0..1)
    val height: Float    // box height (0..1)
)

sealed class AiStatus {
    object Idle     : AiStatus()
    object Thinking : AiStatus()
    data class Response(val text: String) : AiStatus()
    data class Error(val message: String) : AiStatus()
}

data class HudUiState(
    val bleState: BleState           = BleState.Idle,
    val cameraReady: Boolean         = false,
    val isListening: Boolean         = false,
    val transcript: String           = "",
    val detectedObjects: List<ArObject> = emptyList(),
    val aiStatus: AiStatus           = AiStatus.Idle,
    val batteryPct: Int              = 100,
    val lastGesture: GestureEvent?   = null,
    val hudOpacity: Float            = 0.9f,
    val isAnalyzing: Boolean         = false
)
