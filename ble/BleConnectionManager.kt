package com.ringhud.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BleConnectionManager"

@Singleton
@SuppressLint("MissingPermission")
class BleConnectionManager @Inject constructor(
    private val context: Context
) {
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val scope       = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler     = Handler(Looper.getMainLooper())
    private val gattClient  = GattClient(context)

    private val _state = MutableStateFlow<BleState>(BleState.Idle)
    val state: StateFlow<BleState> = _state.asStateFlow()

    /** Live IMU frames; empty until a ring connects. */
    val frames: Flow<ImuFrame> = gattClient.frames

    private var retryDelay = BleConstants.RETRY_DELAY_BASE_MS
    private var scanning   = false

    // ── Public API ──────────────────────────────────────────────────────────

    fun startScanning() {
        if (adapter == null || !adapter.isEnabled) {
            _state.value = BleState.Error("Bluetooth is off")
            return
        }
        doScan()
    }

    fun stopScanning() {
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        scanning = false
    }

    fun disconnect() {
        stopScanning()
        gattClient.disconnect()
        _state.value = BleState.Idle
        retryDelay   = BleConstants.RETRY_DELAY_BASE_MS
    }

    // ── Scanning ────────────────────────────────────────────────────────────

    private fun doScan() {
        if (scanning) return
        scanning = true
        _state.value = BleState.Scanning

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED) // duty-cycled for G Play
            .build()
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BleConstants.RING_SERVICE_UUID))
            .build()

        adapter?.bluetoothLeScanner?.startScan(listOf(filter), settings, scanCallback)
        Log.d(TAG, "BLE scan started")

        // Stop scan after timeout to prevent battery drain
        handler.postDelayed({
            if (scanning && _state.value is BleState.Scanning) {
                stopScanning()
                scheduleRetry("No ring found within scan window")
            }
        }, BleConstants.SCAN_TIMEOUT_MS)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            stopScanning()
            connect(result)
        }

        override fun onScanFailed(errorCode: Int) {
            _state.value = BleState.Error("Scan failed: errorCode=$errorCode")
            scheduleRetry("Scan failed")
        }
    }

    // ── Connection ──────────────────────────────────────────────────────────

    private fun connect(result: ScanResult) {
        val name = result.device.name ?: "BLE Ring"
        Log.d(TAG, "Connecting to $name (${result.device.address})")
        _state.value = BleState.Connecting

        gattClient.connect(result.device)

        // Observe frame flow to transition to Streaming state
        scope.launch {
            gattClient.frames
                .catch { e ->
                    Log.e(TAG, "Frame stream error: ${e.message}")
                    _state.value = BleState.Error(e.message ?: "Stream error")
                    scheduleRetry("Frame stream dropped")
                }
                .collect { _ ->
                    if (_state.value !is BleState.Streaming) {
                        _state.value = BleState.Streaming
                        retryDelay = BleConstants.RETRY_DELAY_BASE_MS // reset on success
                        Log.d(TAG, "Streaming from ring")
                    }
                }
            // Flow ended = disconnected
            if (_state.value is BleState.Streaming || _state.value is BleState.Connected) {
                scheduleRetry("Ring disconnected")
            }
        }
    }

    // ── Retry backoff ────────────────────────────────────────────────────────

    private fun scheduleRetry(reason: String) {
        Log.d(TAG, "$reason — retrying in ${retryDelay}ms")
        _state.value = BleState.Error(reason, retryDelay)
        handler.postDelayed({
            retryDelay = minOf(retryDelay * 2, BleConstants.RETRY_DELAY_MAX_MS)
            doScan()
        }, retryDelay)
    }
}
