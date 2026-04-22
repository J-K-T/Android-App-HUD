package com.ringhud.gesture

import kotlin.math.abs

/**
 * Rule-based gesture classifier.  Runs in < 1 ms per window on the Moto G Play's
 * Snapdragon 460 — no TFLite or ML runtime needed.
 *
 * Tuning constants are exposed so you can adjust thresholds for your ring model.
 */
object PatternMatcher {

    // ── Thresholds (m/s²) ────────────────────────────────────────────────────
    const val TAP_MAGNITUDE_THRESHOLD  = 12f   // peak linear acceleration for a tap
    const val HOLD_VARIANCE_THRESHOLD  = 0.4f  // low variance = user holding still
    const val SWIPE_AXIS_THRESHOLD     = 6f    // minimum magnitude for swipe axis
    const val SWIPE_DOMINANCE_RATIO    = 2.5f  // dominant axis must be this much stronger

    const val DOUBLE_TAP_WINDOW_MS     = 400L
    const val HOLD_MIN_DURATION_MS     = 600L

    // ── State for double-tap detection ───────────────────────────────────────
    private var lastTapMs = 0L

    fun classify(window: List<NormFrame>): GestureEvent? {
        if (window.size < 5) return null

        // ── 1. Compute per-frame jerk (delta magnitude) ──────────────────────
        val jerk = window.zipWithNext().map { (a, b) ->
            val dax = b.ax - a.ax; val day = b.ay - a.ay; val daz = b.az - a.az
            kotlin.math.sqrt(dax * dax + day * day + daz * daz)
        }
        val peakJerk = jerk.max()

        // ── 2. Tap / double-tap ──────────────────────────────────────────────
        if (peakJerk > TAP_MAGNITUDE_THRESHOLD) {
            val now = System.currentTimeMillis()
            return if (now - lastTapMs < DOUBLE_TAP_WINDOW_MS) {
                lastTapMs = 0L  // reset so triple-tap doesn't fire again
                GestureEvent.DoubleTap
            } else {
                lastTapMs = now
                GestureEvent.Tap
            }
        }

        // ── 3. Swipe (dominant-axis sustained motion) ────────────────────────
        if (window.size >= 10) {
            val recent = window.takeLast(10)
            val meanX = recent.map { abs(it.ax) }.average().toFloat()
            val meanY = recent.map { abs(it.ay) }.average().toFloat()
            val meanZ = recent.map { abs(it.az) }.average().toFloat()
            val maxAxis = maxOf(meanX, meanY, meanZ)

            if (maxAxis > SWIPE_AXIS_THRESHOLD) {
                val secondMax = listOf(meanX, meanY, meanZ)
                    .sorted().let { it[1] }
                if (maxAxis / (secondMax + 0.01f) > SWIPE_DOMINANCE_RATIO) {
                    return when {
                        maxAxis == meanX -> if (recent.last().ax > 0) GestureEvent.Swipe(GestureEvent.Swipe.Direction.RIGHT)
                                           else                        GestureEvent.Swipe(GestureEvent.Swipe.Direction.LEFT)
                        maxAxis == meanY -> if (recent.last().ay > 0) GestureEvent.Swipe(GestureEvent.Swipe.Direction.DOWN)
                                           else                        GestureEvent.Swipe(GestureEvent.Swipe.Direction.UP)
                        else -> null
                    }
                }
            }
        }

        // ── 4. Hold (low variance across full window) ────────────────────────
        if (window.size == WindowBuffer.WINDOW_SIZE) {
            val variance = window.map { it.accelMagnitude }.let { mags ->
                val mean = mags.average().toFloat()
                mags.map { (it - mean) * (it - mean) }.average().toFloat()
            }
            val span = window.last().timestampMs - window.first().timestampMs
            if (variance < HOLD_VARIANCE_THRESHOLD && span >= HOLD_MIN_DURATION_MS) {
                return GestureEvent.Hold
            }
        }

        return null
    }

    fun resetState() { lastTapMs = 0L }
}
