package com.ringhud.ble

import java.util.UUID

/**
 * Update RING_SERVICE_UUID and IMU_CHAR_UUID to match your ring's GATT profile.
 *
 * Common rings:
 *  - Colmi R02/R06: service 0x180D, IMU char 0x2A37  (HR sensor repurposed)
 *  - RingConn G1:   service 6E40FFF0-…, notify char 6E40FFF1-…
 *  - Generic Nordic: service 6E400001-B5A3-F393-E0A9-E50E24DCCA9E (NUS)
 *
 * Use nRF Connect (Android app) to discover the exact UUIDs for your hardware.
 */
object BleConstants {

    // ── ✏️  EDIT THESE for your ring ────────────────────────────────────────
    val RING_SERVICE_UUID: UUID = UUID.fromString("6E40FFF0-B5A3-F393-E0A9-E50E24DCCA9E")
    val IMU_CHAR_UUID: UUID     = UUID.fromString("6E40FFF1-B5A3-F393-E0A9-E50E24DCCA9E")
    val BATTERY_CHAR_UUID: UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
    // ────────────────────────────────────────────────────────────────────────

    /** Standard Client Characteristic Configuration Descriptor */
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    // IMU scale factors — ±2g accelerometer, ±500°/s gyroscope (16-bit signed)
    const val ACCEL_SCALE = 9.81f / 16384f          // → m/s²
    const val GYRO_SCALE  = (Math.PI / 180 / 65.5).toFloat() // → rad/s

    // Frame layout (14 bytes: seq[2] + ax[2] + ay[2] + az[2] + gx[2] + gy[2] + gz[2])
    const val FRAME_SIZE = 14

    // Scan / reconnect tuning (balanced mode for Moto G Play battery life)
    const val SCAN_TIMEOUT_MS     = 10_000L
    const val RETRY_DELAY_BASE_MS = 1_000L
    const val RETRY_DELAY_MAX_MS  = 8_000L
}
