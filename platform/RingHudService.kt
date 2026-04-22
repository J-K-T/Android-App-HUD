package com.ringhud.platform

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.ringhud.MainActivity
import com.ringhud.R
import com.ringhud.ble.BleConnectionManager
import com.ringhud.ble.BleState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

private const val NOTIF_CHANNEL = "ring_hud_channel"
private const val NOTIF_ID      = 1001

@AndroidEntryPoint
class RingHudService : LifecycleService() {

    @Inject lateinit var bleManager: BleConnectionManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Scanning for ring…"))
        bleManager.startScanning()

        // Update notification with live BLE status
        bleManager.state.onEach { state ->
            val msg = when (state) {
                BleState.Idle       -> "Idle"
                BleState.Scanning   -> "Scanning for ring…"
                BleState.Connecting -> "Connecting…"
                is BleState.Connected -> "Connected to ${state.name}"
                BleState.Streaming  -> "● Streaming from ring"
                is BleState.Error   -> "Disconnected — retrying"
            }
            updateNotification(msg)
        }.launchIn(lifecycleScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY   // OS restarts us after low-memory kills
    }

    override fun onDestroy() {
        bleManager.disconnect()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    // ── Notification helpers ─────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL,
            "Ring HUD",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "BLE ring connection status" }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("Ring HUD")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(text))
    }
}
