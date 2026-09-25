package com.runcoach.wear.listener

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.runcoach.wear.alarm.AlarmTriggerManager
import com.runcoach.wear.alarm.AlarmType
import com.runcoach.wear.data.WearDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@AndroidEntryPoint
class PhoneMessageListenerService : WearableListenerService() {

    @Inject lateinit var dataStore: WearDataStore
    @Inject lateinit var alarmManager: AlarmTriggerManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val PATH_GOAL_RESPONSE = "/goal/response"
        const val PATH_RECORDS_RESPONSE = "/records/response"
        const val PATH_WEEKLY_ALARM = "/alarm/weekly"
        const val PATH_IMPORT_STATUS = "/history/import_samsung_health_status"
        const val PATH_OWNER_SYNC = "/owner/sync"

        const val ACTION_ALARM_RECEIVED = "com.runcoach.wear.ALARM_RECEIVED"
        const val EXTRA_ALARM_TYPE = "alarm_type"
        const val EXTRA_ALARM_TITLE = "alarm_title"
        const val EXTRA_ALARM_MSG = "alarm_msg"

        const val NOTIF_CHANNEL = "weekly_run"
        const val NOTIF_ID = 3001
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            PATH_GOAL_RESPONSE -> scope.launch {
                dataStore.saveGoalFromPhone(String(event.data))
            }

            PATH_RECORDS_RESPONSE -> scope.launch {
                dataStore.saveRecordsFromPhone(String(event.data))
            }

            PATH_IMPORT_STATUS -> scope.launch {
                dataStore.savePhoneImportStatus(String(event.data))
            }

            PATH_OWNER_SYNC -> scope.launch {
                val ownerId = JSONObject(String(event.data)).optString("ownerId")
                dataStore.saveOwnerId(ownerId)
            }

            PATH_WEEKLY_ALARM -> handleWeeklyAlarm()
        }
    }

    private fun handleWeeklyAlarm() {
        alarmManager.vibrate(AlarmType.DISTANCE)
        showWeeklyNotification()
        sendBroadcast(Intent(ACTION_ALARM_RECEIVED).apply {
            putExtra(EXTRA_ALARM_TYPE, "weekly")
            putExtra(EXTRA_ALARM_TITLE, "오늘 러닝")
            putExtra(EXTRA_ALARM_MSG, "워치에서 오늘 목표를 확인해 주세요.")
        })
    }

    private fun showWeeklyNotification() {
        createNotificationChannel()
        val notif = NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("오늘 러닝 알림")
            .setContentText("이번 주 기록을 확인하고 오늘 목표를 채워보세요.")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notif)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL,
            "주간 러닝 알림",
            NotificationManager.IMPORTANCE_HIGH
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
