package com.runcoach.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.runcoach.app.data.db.BriefingRecord
import com.runcoach.app.data.db.BriefingRecordDao
import com.runcoach.app.data.db.RunningRecord
import com.runcoach.app.data.db.RunningRecordDao
import com.runcoach.app.data.db.WeeklyGoal
import com.runcoach.app.data.db.WeeklyGoalDao
import com.runcoach.app.data.health.SamsungHealthImportManager
import com.runcoach.app.data.identity.OwnerIdProvider
import com.runcoach.app.domain.AiGoalAdvisor
import com.runcoach.app.domain.BriefingComposer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

@AndroidEntryPoint
class WearDataListenerService : WearableListenerService() {

    @Inject lateinit var recordDao: RunningRecordDao
    @Inject lateinit var goalDao: WeeklyGoalDao
    @Inject lateinit var briefingRecordDao: BriefingRecordDao
    @Inject lateinit var samsungHealthImportManager: SamsungHealthImportManager
    @Inject lateinit var ownerIdProvider: OwnerIdProvider
    @Inject lateinit var aiAdvisor: AiGoalAdvisor
    @Inject lateinit var briefingComposer: BriefingComposer

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val PATH_RUNNING_COMPLETE = "/running/complete"
        const val PATH_GOAL_REQUEST = "/goal/request"
        const val PATH_GOAL_RESPONSE = "/goal/response"
        const val PATH_RECORDS_REQUEST = "/records/request"
        const val PATH_RECORDS_RESPONSE = "/records/response"
        const val PATH_DETAIL_OPEN = "/detail/open"
        const val PATH_IMPORT_SAMSUNG_HEALTH = "/history/import_samsung_health"
        const val PATH_IMPORT_SAMSUNG_HEALTH_STATUS = "/history/import_samsung_health_status"
        const val PATH_OWNER_REQUEST = "/owner/request"
        const val PATH_OWNER_SYNC = "/owner/sync"

        const val CHANNEL_IMPORT = "runcoach_import"
        const val IMPORT_NOTIF_ID = 1002
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            PATH_RUNNING_COMPLETE -> handleRunningComplete(event)
            PATH_GOAL_REQUEST -> handleGoalRequest(event.sourceNodeId)
            PATH_RECORDS_REQUEST -> handleRecordsRequest(event.sourceNodeId)
            PATH_DETAIL_OPEN -> openDetailActivity()
            PATH_IMPORT_SAMSUNG_HEALTH -> showSamsungHealthImportNotification(event.sourceNodeId)
            PATH_OWNER_REQUEST -> sendOwnerSync(event.sourceNodeId)
        }
    }

    private fun handleRunningComplete(event: MessageEvent) {
        scope.launch {
            val json = JSONObject(String(event.data))
            val record = RunningRecord(
                date = json.getLong("date"),
                distanceKm = json.getDouble("distanceKm").toFloat(),
                targetKm = json.getDouble("targetKm").toFloat(),
                avgPace = json.getString("avgPace"),
                targetPace = json.optString("targetPace", "--:--"),
                avgHeartRate = json.getInt("avgHeartRate"),
                maxHeartRate = json.getInt("maxHeartRate"),
                durationSec = json.getInt("durationSec"),
                fatigueLevel = json.getString("fatigueLevel"),
                completed = json.getBoolean("completed"),
                source = "wear_os"
            )
            val recordId = recordDao.insert(record)

            briefingRecordDao.insert(
                BriefingRecord(
                    runRecordId = recordId,
                    briefingKey = "run-$recordId-post",
                    briefingType = "post_run_summary",
                    content = briefingComposer.composePostRunBriefing(record.copy(id = recordId)),
                    createdAt = System.currentTimeMillis(),
                    provider = "template"
                )
            )

            val currentGoal = goalDao.getCurrentGoalOnce()
            val suggestion = aiAdvisor.suggestNextGoal(record.copy(id = recordId), currentGoal)
            val nextGoal = WeeklyGoal(
                weekStart = System.currentTimeMillis(),
                targetKm = suggestion.targetKm,
                targetPace = suggestion.targetPace,
                speedupKm = suggestion.speedupKm,
                speedupPct = suggestion.speedupPct,
                hrAlertBpm = suggestion.hrAlertBpm,
                aiSuggested = true
            )
            goalDao.insert(nextGoal)

            val goalJson = JSONObject().apply {
                put("targetKm", nextGoal.targetKm)
                put("targetPace", nextGoal.targetPace)
                put("speedupKm", nextGoal.speedupKm)
                put("speedupPct", nextGoal.speedupPct)
                put("hrAlertBpm", nextGoal.hrAlertBpm)
                put("aiMessage", suggestion.message)
            }.toString()
            sendToWatch(event.sourceNodeId, PATH_GOAL_RESPONSE, goalJson)
        }
    }

    private fun handleGoalRequest(nodeId: String) {
        scope.launch {
            sendOwnerSync(nodeId)
            val goal = goalDao.getCurrentGoalOnce() ?: return@launch
            val json = JSONObject().apply {
                put("targetKm", goal.targetKm)
                put("targetPace", goal.targetPace)
                put("speedupKm", goal.speedupKm)
                put("speedupPct", goal.speedupPct)
                put("hrAlertBpm", goal.hrAlertBpm)
                put("aiMessage", "")
            }.toString()
            sendToWatch(nodeId, PATH_GOAL_RESPONSE, json)
        }
    }

    private fun handleRecordsRequest(nodeId: String) {
        scope.launch {
            sendOwnerSync(nodeId)
            val records = recordDao.getRecentRecords(5)
            val array = JSONArray()
            records.forEach { record ->
                array.put(
                    JSONObject().apply {
                        put("date", record.date)
                        put("distanceKm", record.distanceKm)
                        put("avgPace", record.avgPace)
                        put("completed", record.completed)
                    }
                )
            }
            sendToWatch(nodeId, PATH_RECORDS_RESPONSE, array.toString().toByteArray())
        }
    }

    private fun openDetailActivity() {
        val intent = Intent(this, Class.forName("com.runcoach.app.ui.MainActivity")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun showSamsungHealthImportNotification(nodeId: String) {
        samsungHealthImportManager.getAvailabilityMessage()?.let { message ->
            sendOwnerSync(nodeId)
            sendToWatch(nodeId, PATH_IMPORT_SAMSUNG_HEALTH_STATUS, message)
            return
        }

        createImportNotificationChannel()
        val manager = NotificationManagerCompat.from(this)
        if (!areNotificationsReady(manager)) {
            sendOwnerSync(nodeId)
            sendToWatch(
                nodeId,
                PATH_IMPORT_SAMSUNG_HEALTH_STATUS,
                "폰에서 알림 권한이 꺼져 있습니다. RunCoach 앱을 열고 알림을 허용해 주세요."
            )
            return
        }

        val intent = Intent(this, Class.forName("com.runcoach.app.ui.MainActivity")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_history_import", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            2001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_IMPORT)
            .setContentTitle("삼성헬스 기록 가져오기")
            .setContentText("워치 요청이 도착했습니다. RunCoach에서 과거 러닝 기록을 가져오세요.")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(0, "가져오기 열기", pendingIntent)
            .build()

        manager.notify(IMPORT_NOTIF_ID, notification)
        sendOwnerSync(nodeId)
        sendToWatch(
            nodeId,
            PATH_IMPORT_SAMSUNG_HEALTH_STATUS,
            "폰에서 요청을 받았습니다. 알림 또는 RunCoach 앱을 확인해 주세요."
        )
    }

    private fun areNotificationsReady(manager: NotificationManagerCompat): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun sendToWatch(nodeId: String, path: String, data: ByteArray) {
        Wearable.getMessageClient(this).sendMessage(nodeId, path, data)
    }

    private fun sendToWatch(nodeId: String, path: String, json: String) {
        sendToWatch(nodeId, path, json.toByteArray())
    }

    private fun sendOwnerSync(nodeId: String) {
        val payload = JSONObject()
            .put("ownerId", ownerIdProvider.getOrCreate())
            .toString()
        sendToWatch(nodeId, PATH_OWNER_SYNC, payload)
    }

    private fun createImportNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_IMPORT,
                "Samsung Health import",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
