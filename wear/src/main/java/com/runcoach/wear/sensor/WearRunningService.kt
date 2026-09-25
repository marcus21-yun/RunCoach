package com.runcoach.wear.sensor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.LocationAvailability
import androidx.health.services.client.data.WarmUpConfig
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.Wearable
import com.runcoach.core.HrZone
import com.runcoach.core.getHrZone
import com.runcoach.wear.data.CachedGoal
import com.runcoach.wear.data.CachedRecord
import com.runcoach.wear.data.WearDataStore
import com.runcoach.wear.data.model.CoachStyle
import com.runcoach.wear.tts.BriefingTtsManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

data class SensorData(
    val distanceKm: Float = 0f,
    val pacePerKm: String = "--:--",
    val paceSeconds: Int = 0,
    val heartRate: Int = 0,
    val elapsedSec: Int = 0,
    val calorie: Int = 0
)

data class RunResult(
    val distanceKm: Float,
    val avgPace: String,
    val avgHeartRate: Int,
    val maxHeartRate: Int,
    val durationSec: Int,
    val fatigueLevel: String,
    val completed: Boolean
)

@AndroidEntryPoint
class WearRunningService : LifecycleService() {

    @Inject lateinit var dataStore: WearDataStore
    @Inject lateinit var repository: RunningRepository

    private val heartRateSamples = mutableListOf<Int>()
    private var startTimeMs = 0L

    // 실시간 HR 코칭
    private var hrCoachingTts: BriefingTtsManager? = null
    private var lastHrZone: HrZone = HrZone.SAFE
    private var lastCoachingTimeMs: Long = 0L
    private val coachingCooldownMs = 30_000L  // 30초 쿨다운

    companion object {
        private const val TAG = "RunCoachWear"
        const val CHANNEL_ID = "running_service"
        const val NOTIF_ID = 2001
        const val ACTION_START = "com.runcoach.wear.START_RUNNING"
        const val ACTION_STOP = "com.runcoach.wear.STOP_RUNNING"
    }

    private val exerciseCallback = object : ExerciseUpdateCallback {
        override fun onRegistered() = Unit

        override fun onRegistrationFailed(throwable: Throwable) {
            Log.e(TAG, "exercise callback registration failed", throwable)
        }

        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val metrics = update.latestMetrics
            val hr = metrics.getData(DataType.HEART_RATE_BPM)
                .lastOrNull()?.value?.toInt() ?: 0
            if (hr > 0) {
                heartRateSamples.add(hr)
            }

            // 누적 거리는 DISTANCE_TOTAL(.total) 사용 — DISTANCE(델타)의 마지막 샘플은 누적값이 아님
            val distanceM = (metrics.getData(DataType.DISTANCE_TOTAL)?.total ?: 0.0)
                .takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
            val speedMps = (metrics.getData(DataType.SPEED)
                .lastOrNull()?.value ?: 0.0)
                .takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
            val calorie = metrics.getData(DataType.CALORIES_TOTAL)?.total?.toInt() ?: 0
            val elapsedSec = ((System.currentTimeMillis() - startTimeMs) / 1000).toInt()
            Log.d(
                TAG,
                "sensorUpdate hr=$hr distanceM=$distanceM speedMps=$speedMps elapsedSec=$elapsedSec calorie=$calorie"
            )

            repository.updateSensorData(
                SensorData(
                    distanceKm = (distanceM / 1000).toFloat(),
                    pacePerKm = speedToPace(speedMps),
                    paceSeconds = speedToPaceSeconds(speedMps),
                    heartRate = hr,
                    elapsedSec = elapsedSec,
                    calorie = calorie
                )
            )

            checkAndCoachHrZone(hr)
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit

        override fun onAvailabilityChanged(
            dataType: DataType<*, *>,
            availability: Availability
        ) {
            Log.d(TAG, "availabilityChanged $dataType -> $availability")
            if (dataType == DataType.LOCATION && availability is LocationAvailability) {
                // 위성 신호를 확보(ACQUIRED_*)했을 때만 GPS 준비 완료
                val acquired = availability == LocationAvailability.ACQUIRED_TETHERED ||
                    availability == LocationAvailability.ACQUIRED_UNTETHERED
                repository.updateGpsReady(acquired)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        hrCoachingTts = BriefingTtsManager(this)
    }

    override fun onDestroy() {
        hrCoachingTts?.release()
        hrCoachingTts = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "onStartCommand action=${intent?.action}")
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        startTimeMs = System.currentTimeMillis()
        heartRateSamples.clear()
        repository.reset()
        Log.d(TAG, "startTracking")
        startForeground(NOTIF_ID, buildNotification("러닝 측정 중"))

        lifecycleScope.launch {
            val exerciseClient = HealthServices.getClient(this@WearRunningService).exerciseClient

            val config = ExerciseConfig.builder(ExerciseType.RUNNING)
                .setDataTypes(
                    setOf(
                        DataType.HEART_RATE_BPM,
                        DataType.LOCATION,
                        DataType.DISTANCE_TOTAL,
                        DataType.SPEED,
                        DataType.CALORIES_TOTAL
                    )
                )
                .setIsAutoPauseAndResumeEnabled(false)
                .setIsGpsEnabled(true)
                .build()

            runCatching {
                exerciseClient.setUpdateCallback(exerciseCallback)
                // GPS·심박 워밍업 — 위성/센서 신호를 운동 시작 전에 미리 확보(첫 거리/페이스 지연 단축)
                runCatching {
                    exerciseClient.prepareExerciseAsync(
                        WarmUpConfig(
                            ExerciseType.RUNNING,
                            setOf(DataType.HEART_RATE_BPM, DataType.LOCATION)
                        )
                    ).await()
                    Log.d(TAG, "exercise warmup requested")
                }.onFailure { Log.w(TAG, "warmup failed", it) }
                exerciseClient.startExerciseAsync(config).await()
            }.onSuccess {
                Log.d(TAG, "exercise started")
            }.onFailure {
                Log.e(TAG, "exercise start failed", it)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun stopTracking() {
        lifecycleScope.launch {
            val exerciseClient = HealthServices.getClient(this@WearRunningService).exerciseClient
            runCatching { exerciseClient.endExerciseAsync().await() }
                .onFailure { Log.w(TAG, "exercise end failed", it) }

            val current = repository.sensorData.value
            val goal = dataStore.getCurrentGoalSync()
            val avgHr = if (heartRateSamples.isEmpty()) 0 else heartRateSamples.average().toInt()
            val maxHr = heartRateSamples.maxOrNull() ?: 0
            val fatigue = calcFatigue(avgHr, current.elapsedSec)
            val safeDistanceKm = current.distanceKm.takeIf { it.isFinite() && it >= 0f } ?: 0f
            val safeElapsedSec = current.elapsedSec.coerceAtLeast(0)
            val completed = goal?.let { safeDistanceKm >= it.targetKm } ?: false

            val result = RunResult(
                distanceKm = safeDistanceKm,
                avgPace = current.pacePerKm.ifBlank { "--:--" },
                avgHeartRate = avgHr,
                maxHeartRate = maxHr,
                durationSec = safeElapsedSec,
                fatigueLevel = fatigue,
                completed = completed
            )
            Log.d(TAG, "stopTracking result distance=${result.distanceKm}, duration=${result.durationSec}")
            repository.updateResult(result)

            val finishedAt = System.currentTimeMillis()
            dataStore.saveRunRecord(
                CachedRecord(
                    id = finishedAt.toString(),
                    date = finishedAt,
                    distanceKm = result.distanceKm,
                    avgPace = result.avgPace,
                    avgHeartRate = result.avgHeartRate,
                    maxHeartRate = result.maxHeartRate,
                    durationSec = result.durationSec,
                    fatigueLevel = result.fatigueLevel,
                    completed = result.completed,
                    source = "watch"
                )
            )
            Log.d(TAG, "record saved id=$finishedAt")

            sendResultToPhone(result, goal)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun sendResultToPhone(result: RunResult, goal: CachedGoal?) {
        lifecycleScope.launch {
            val json = JSONObject().apply {
                put("date", System.currentTimeMillis())
                put("distanceKm", result.distanceKm)
                put("targetKm", goal?.targetKm ?: 0f)
                put("avgPace", result.avgPace)
                put("avgHeartRate", result.avgHeartRate)
                put("maxHeartRate", result.maxHeartRate)
                put("durationSec", result.durationSec)
                put("fatigueLevel", result.fatigueLevel)
                put("completed", result.completed)
            }.toString()

            Wearable.getNodeClient(this@WearRunningService)
                .connectedNodes
                .addOnSuccessListener { nodes ->
                    Log.d(TAG, "connectedNodes=${nodes.size}")
                    nodes.firstOrNull()?.let { node ->
                        Wearable.getMessageClient(this@WearRunningService)
                            .sendMessage(node.id, "/running/complete", json.toByteArray())
                        Log.d(TAG, "result sent to phone")
                    }
                }
        }
    }

    private fun checkAndCoachHrZone(bpm: Int) {
        val zone = getHrZone(bpm)
        val now = System.currentTimeMillis()
        val cooldownPassed = (now - lastCoachingTimeMs) >= coachingCooldownMs

        // 존이 악화됐거나(SAFE→CAUTION, CAUTION→DANGER), 쿨다운 후 DANGER 지속 시 재코칭
        val shouldCoach = when {
            zone == HrZone.DANGER && lastHrZone != HrZone.DANGER -> true
            zone == HrZone.CAUTION && lastHrZone == HrZone.SAFE -> true
            zone == HrZone.DANGER && cooldownPassed -> true
            else -> false
        }

        if (shouldCoach) {
            val message = when (zone) {
                HrZone.DANGER -> "심박수가 너무 높습니다. 잠깐 걷기로 전환하세요!"
                HrZone.CAUTION -> "조금 힘드시죠? 페이스를 약간 줄이세요."
                HrZone.SAFE -> return
            }
            Log.d(TAG, "hrCoach zone=$zone bpm=$bpm message=$message")
            hrCoachingTts?.speak(message, CoachStyle.ANNOUNCER)
            lastCoachingTimeMs = now
        }

        lastHrZone = zone
    }

    private fun speedToPace(speedMps: Double): String {
        if (speedMps <= 0) return "--:--"
        val secPerKm = (1000.0 / speedMps).toInt()
        return "%d:%02d".format(secPerKm / 60, secPerKm % 60)
    }

    private fun speedToPaceSeconds(speedMps: Double): Int {
        if (speedMps <= 0) return Int.MAX_VALUE
        return (1000.0 / speedMps).toInt()
    }

    private fun calcFatigue(avgHr: Int, elapsedSec: Int): String {
        val hrScore = when {
            avgHr > 170 -> 3
            avgHr > 155 -> 2
            else -> 1
        }
        val timeScore = when {
            elapsedSec > 2400 -> 2
            elapsedSec > 1200 -> 1
            else -> 0
        }
        return when (hrScore + timeScore) {
            in 4..5 -> "high"
            in 2..3 -> "mid"
            else -> "low"
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Run tracking",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RunCoach")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)
}
