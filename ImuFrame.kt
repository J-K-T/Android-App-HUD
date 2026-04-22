package com.ringhud.ble

/** Raw sensor reading straight off the wire — not yet normalised. */
data class ImuFrame(
    val seq: Int,
    val accelX: Short, val accelY: Short, val accelZ: Short,
    val gyroX: Short,  val gyroY: Short,  val gyroZ: Short,
    val timestampMs: Long = System.currentTimeMillis()
)

/** Connection lifecycle modelled as a state machine. */
sealed class BleState {
    object Idle       : BleState()
    object Scanning   : BleState()
    object Connecting : BleState()
    data class Connected(val name: String, val address: String) : BleState()
    object Streaming  : BleState()
    data class Error(val message: String, val retryInMs: Long = 0L) : BleState()
}
