package com.ringhud.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parses the ring's characteristic payload into a typed [ImuFrame].
 * Uses a pre-allocated ByteBuffer to avoid per-frame heap pressure at 50 Hz.
 */
object FrameParser {

    private val buf = ByteBuffer.allocate(BleConstants.FRAME_SIZE)
        .order(ByteOrder.LITTLE_ENDIAN)

    /** Returns null if the byte array is too short or malformed. */
    fun parse(bytes: ByteArray): ImuFrame? {
        if (bytes.size < BleConstants.FRAME_SIZE) return null
        synchronized(buf) {
            buf.clear()
            buf.put(bytes, 0, BleConstants.FRAME_SIZE)
            buf.flip()
            return ImuFrame(
                seq    = buf.short.toInt() and 0xFFFF,
                accelX = buf.short,
                accelY = buf.short,
                accelZ = buf.short,
                gyroX  = buf.short,
                gyroY  = buf.short,
                gyroZ  = buf.short
            )
        }
    }
}
