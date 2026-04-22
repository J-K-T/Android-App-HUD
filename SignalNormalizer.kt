package com.ringhud.gesture

import com.ringhud.ble.BleConstants
import com.ringhud.ble.ImuFrame

/**
 * Converts raw [ImuFrame] → [NormFrame]:
 *  1. Scales integer readings to SI units.
 *  2. Estimates gravity with an exponential moving average (α = 0.9).
 *  3. Subtracts gravity estimate to isolate linear acceleration.
 *
 * Not thread-safe — call only from a single coroutine.
 */
class SignalNormalizer {

    // EMA gravity estimate (initialised to ≈ 1g downward)
    private var gravX = 0f
    private var gravY = 0f
    private var gravZ = 9.81f

    fun normalize(raw: ImuFrame): NormFrame {
        val ax = raw.accelX * BleConstants.ACCEL_SCALE
        val ay = raw.accelY * BleConstants.ACCEL_SCALE
        val az = raw.accelZ * BleConstants.ACCEL_SCALE

        // Exponential moving average — ALPHA=0.9 gives ~10 frame lag at 50 Hz
        gravX = ALPHA * gravX + (1f - ALPHA) * ax
        gravY = ALPHA * gravY + (1f - ALPHA) * ay
        gravZ = ALPHA * gravZ + (1f - ALPHA) * az

        return NormFrame(
            ax = ax - gravX,
            ay = ay - gravY,
            az = az - gravZ,
            gx = raw.gyroX * BleConstants.GYRO_SCALE,
            gy = raw.gyroY * BleConstants.GYRO_SCALE,
            gz = raw.gyroZ * BleConstants.GYRO_SCALE,
            timestampMs = raw.timestampMs
        )
    }

    fun reset() {
        gravX = 0f; gravY = 0f; gravZ = 9.81f
    }

    companion object {
        private const val ALPHA = 0.9f
    }
}
