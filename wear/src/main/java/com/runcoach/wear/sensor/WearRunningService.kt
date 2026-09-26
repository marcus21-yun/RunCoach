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
import com.runcoach.core.coach.LoadGuard
import com.runcoach.core.coach.HabitPhrasebook
import com.runcoach.core.coach.HabitSample
import com.runcoach.core.coach.RunHabitCoach
import com.runcoach.core.coach.RunPatternAnalyzer
import com.runcoach.core.coach.paceToSecondsOrNull
import com.runcoach.wear.BuildConfig
import com.runcoach.wear.data.BriefingRepository
import com.runcoach.wear.data.CachedGoal
import com.runcoach.wear.data.CachedRecord
import com.runcoach.wear.data.WearDataStore
import com.runcoach.wear.data.toPatternRun
import com.runcoach.wear.data.api.OpenAiApiService
import com.runcoach.wear.data.model.CoachStyle
import com.runcoach.wear.data.model.habitTone
import com.runcoach.wear.tts.BriefingTtsManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
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

    // 러닝 중 습관 코치 (호흡·페이스·자세 잔소리)
    private var coachTts: BriefingTtsManager? = null
    private var habitCoach: RunHabitCoach? = null
    private var coachStyle: CoachStyle = CoachStyle.MOM

    // 1km 구간 기록 — 다음 러닝에서 "지난번 처졌던 지점"을 알려주는 근거
    private val splitsSec = mutableListOf<Int>()
    private var lastSplitElapsedSec = 0

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

            recordSplit(distanceM / 1000.0, elapsedSec)
            coachHabit(
                HabitSample(
                    elapsedSec = elapsedSec,
                    distanceKm = (distanceM / 1000).toFloat(),
                    paceSec = speedToPaceSeconds(speedMps).takeIf { it != Int.MAX_VALUE },
                    bpm = hr
                )
            )
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
        coachTts = BriefingTtsManager(this)
    }

    override fun onDestroy() {
        coachTts?.release()
        coachTts = null
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
        splitsSec.clear()
        lastSplitElapsedSec = 0
        habitCoach = null
        repository.reset()
        Log.d(TAG, "startTracking")
        startForeground(NOTIF_ID, buildNotification("러닝 측정 중"))

        lifecycleScope.launch {
            prepareHabitCoach()
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
                    source = "watch",
                    splitsSec = splitsSec.toList(),
                    slowdownKm = RunPatternAnalyzer.slowdownKm(splitsSec)
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

    /** 최근 기록의 반복 패턴과 선택한 코치 말투로 이번 러닝의 습관 코치를 준비한다. */
    private suspend fun prepareHabitCoach() {
        val goal = dataStore.getCurrentGoalSync()
        val records = dataStore.getRecordsSync()
        val pattern = RunPatternAnalyzer.slowdownPattern(records.map { it.slowdownKm })
        // 나이를 입력했으면 추정 최대심박 기준, 아니면 기본 150/160
        val load = LoadGuard.assess(records.map { it.toPatternRun() }, dataStore.getCachedData().first().userAge)
        coachStyle = runCatching {
            BriefingRepository(this, OpenAiApiService(apiKey = BuildConfig.OPENAI_API_KEY)).loadCoachStyle()
        }.getOrDefault(CoachStyle.MOM)
        habitCoach = RunHabitCoach(
            config = LoadGuard.habitConfig(load),
            targetPaceSec = paceToSecondsOrNull(goal?.targetPace),
            targetKm = goal?.targetKm,
            pattern = pattern
        )
        Log.d(TAG, "habitCoach ready style=${coachStyle.id} pattern=$pattern breath=${load.breathingBpm} walk=${load.walkBpm}")
    }

    private fun coachHabit(sample: HabitSample) {
        val cue = habitCoach?.onSample(sample) ?: return
        val message = HabitPhrasebook.text(cue, coachStyle.habitTone)
        Log.d(TAG, "habitCue ${cue.type} message=$message")
        repository.updateCoachMessage(message)
        coachTts?.speak(message, coachStyle)
    }

    /** km 경계를 넘을 때마다 해당 구간 소요 시간을 기록한다. */
    private fun recordSplit(distanceKm: Double, elapsedSec: Int) {
        val completedKm = distanceKm.toInt()
        val crossed = completedKm - splitsSec.size
        if (crossed <= 0) return
        // GPS 튐으로 한 번에 여러 km를 넘으면 경과 시간을 균등하게 나눈다
        val each = (elapsedSec - lastSplitElapsedSec) / crossed
        repeat(crossed) { splitsSec.add(each) }
        lastSplitElapsedSec = elapsedSec
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
