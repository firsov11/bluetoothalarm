package com.firsov.bluetoothalarm.ble

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BleForegroundService : LifecycleService() {

    @Inject lateinit var bleManager: BleManager
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    companion object {
        const val CHANNEL_ID = "ble_service_channel"
        const val NOTIF_ID = 101
        const val ACTION_ALARM_OFF = "ACTION_ALARM_OFF"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initVibrator()

        // Запускаем бесконечный цикл поиска
        bleManager.startScan()

        lifecycleScope.launch {
            // Следим за статусом для уведомлений и тревоги
            bleManager.status.collect { status ->
                updateNotification("Статус: $status")
                if (status == "ALARM") startAlarm() else stopAlarm()
            }
        }

        // Логика АВТО-ОТКРЫТИЯ в фоне (опционально, если ViewModel не активна)
        lifecycleScope.launch {
            bleManager.rssi.collect { rssi ->
                // Если мы очень близко и байк закрыт
                if (rssi > -60 && bleManager.status.value == "LOCKED") {
                    // Здесь можно добавить проверку флага "Авто" из SharedPreferences/DataStore
                    // bleManager.sendCommand("UNLOCK")
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ALARM_OFF) {
            bleManager.sendCommand("ALARM_OFF")
        }
        val notification = buildNotification("Защита активна")
        startForeground(NOTIF_ID, notification)
        return super.onStartCommand(intent, flags, startId)
    }

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun startAlarm() {
        if (ringtone?.isPlaying != true) {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = RingtoneManager.getRingtone(this, uri)
            ringtone?.play()
        }
        val pattern = longArrayOf(0, 500, 500)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun stopAlarm() {
        ringtone?.stop()
        vibrator?.cancel()
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, BleForegroundService::class.java).apply { action = ACTION_ALARM_OFF }
        val pStopIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BikeLock Guard")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "ОТБОЙ", pStopIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "BikeLock", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

