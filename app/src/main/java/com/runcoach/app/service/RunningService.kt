package com.runcoach.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@AndroidEntryPoint
class RunningService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 워치로 알람 전송 경로 상수
    companion object {
        const val PATH_ALARM = "/runcoach/alarm"
        const val CHANNEL_ID = "running_channel"

        // 알람 타입
        const val ALARM_DISTANCE = "distance"
        const val ALARM_HEART_RATE = "heartrate"
        const val ALARM_SPEEDUP = "speedup"
        const val ALARM_PACE = "pace"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification("러닝 중..."))
    }

    // 워치에 알람 명령 전송
    fun sendAlarmToWatch(alarmType: String, message: String) {
        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@RunningService)
                    .connectedNodes.await()
                val payload = "$alarmType|$message".toByteArray()
                nodes.forEach { node ->
                    Wearable.getMessageClient(this@RunningService)
                        .sendMessage(node.id, PATH_ALARM, payload).await()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "러닝 서비스", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RunCoach")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
