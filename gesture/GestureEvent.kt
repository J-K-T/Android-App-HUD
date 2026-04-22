package com.ringhud.gesture

import kotlin.math.sqrt

/** Normalised IMU sample — gravity removed, units: m/s² and rad/s. */
data class NormFrame(
    val ax: Float, val ay: Float, val az: Float,
    val gx: Float, val gy: Float, val gz: Float,
    val timestampMs: Long
) {
    val accelMagnitude: Float get() = sqrt(ax * ax + ay * ay + az * az)
    val gyroMagnitude: Float  get() = sqrt(gx * gx + gy * gy + gz * gz)
}

/** Recognised hand gestures emitted to the HUD layer. */
sealed class GestureEvent {
    object Tap          : GestureEvent()
    object DoubleTap    : GestureEvent()
    object Hold         : GestureEvent()
    object HoldRelease  : GestureEvent()
    data class Swipe(val direction: Direction) : GestureEvent() {
        enum class Direction { LEFT, RIGHT, UP, DOWN }
    }
}
