package com.ringhud.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

private const val TAG = "GattClient"

/**
 * Wraps BluetoothGatt into a coroutine-friendly Flow of [ImuFrame].
 * Call [connect] to start; call [disconnect] to clean up.
 */
@SuppressLint("MissingPermission")
class GattClient(private val context: Context) {

    private var gatt: BluetoothGatt? = null
    private val frameChannel = Channel<ImuFrame>(Channel.BUFFERED)

    /** Emits parsed IMU frames as the ring streams them. */
    val frames: Flow<ImuFrame> = frameChannel.receiveAsFlow()

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "GATT connected — discovering services")
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "GATT disconnected (status=$status)")
                frameChannel.close()
                g.close()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Service discovery failed: $status")
                return
            }
            val imuChar = g.getService(BleConstants.RING_SERVICE_UUID)
                ?.getCharacteristic(BleConstants.IMU_CHAR_UUID)
            if (imuChar == null) {
                Log.w(TAG, "IMU characteristic not found — check BleConstants UUIDs")
                return
            }
            enableNotifications(g, imuChar)
            // Request shorter connection interval for lower latency on G Play
            g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
            Log.d(TAG, "Notifications enabled; streaming started")
        }

        // API 33+ callback with value parameter
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) = handleFrame(value)

        // Legacy callback (API < 33)
        @Suppress("DEPRECATION")
        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) = handleFrame(characteristic.value)
    }

    fun connect(device: BluetoothDevice) {
        Log.d(TAG, "Connecting to ${device.address}")
        // autoConnect=false avoids the 30 s hang on the G Play's BLE stack
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }

    // ── private helpers ─────────────────────────────────────────────────────

    private fun handleFrame(bytes: ByteArray) {
        FrameParser.parse(bytes)?.let { frame ->
            frameChannel.trySend(frame)
        }
    }

    private fun enableNotifications(g: BluetoothGatt, char: BluetoothGattCharacteristic) {
        g.setCharacteristicNotification(char, true)
        val descriptor = char.getDescriptor(BleConstants.CCCD_UUID) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            g.writeDescriptor(descriptor)
        }
    }
}
