package com.runcoach.wear.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.runcoach.core.coach.PatternRun
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class CachedRecord(
    val id: String,
    val date: Long,
    val distanceKm: Float,
    val avgPace: String,
    val avgHeartRate: Int,
    val maxHeartRate: Int = 0,
    val durationSec: Int = 0,
    val fatigueLevel: String = "low",
    val completed: Boolean,
    val source: String = "watch",
    /** 사용자가 고른 체감 난이도 (Effort.id). 미응답이면 null. */
    val effort: String? = null,
    /** 1km 구간별 소요 시간(초) */
    val splitsSec: List<Int> = emptyList(),
    /** 초반보다 느려지기 시작한 km 지점 (RunPatternAnalyzer.slowdownKm) */
    val slowdownKm: Float? = null
)

data class CachedGoal(
    val targetKm: Float,
    val targetPace: String,
    val speedupKm: Float,
    val speedupPct: Int,
    val hrAlertBpm: Int
)

data class BriefingRecord(
    val id: String,
    val recordId: String,
    val createdAt: Long,
    val briefingType: String,
    val content: String
)

data class WearCache(
    val currentGoal: CachedGoal? = null,
    val nextGoal: CachedGoal? = null,
    val lastRecord: CachedRecord? = null,
    val recentRecords: List<CachedRecord> = emptyList(),
    val recentBriefings: List<BriefingRecord> = emptyList(),
    val aiMessage: String? = null,
    val ownerId: String? = null,
    val phoneImportStatus: String? = null,
    val lastShareCode: String? = null,
    val lastSharePayload: String? = null,
    /** 기억하는 코치가 다음 목표를 제안한 근거 (폰 목표 수신 시 비움) */
    val coachReasons: List<String> = emptyList(),
    /** 사용자가 입력한 나이. 회복 간격·심박 기준에만 쓰고, 미입력이면 null */
    val userAge: Int? = null
)

/** 패턴 분석·주간 누적 가드용 변환 */
fun CachedRecord.toPatternRun() = PatternRun(
    finishedAt = date,
    distanceKm = distanceKm,
    durationSec = durationSec,
    avgHeartRate = avgHeartRate,
    splitsSec = splitsSec
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("wear_cache")
private val Context.metaStore: DataStore<Preferences> by preferencesDataStore("wear_meta")

@Singleton
class WearDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val KEY_CURRENT_GOAL = stringPreferencesKey("current_goal")
        val KEY_NEXT_GOAL = stringPreferencesKey("next_goal")
        val KEY_RECENT_RECORDS = stringPreferencesKey("recent_records")
        val KEY_RECENT_BRIEFINGS = stringPreferencesKey("recent_briefings")
        val KEY_AI_MESSAGE = stringPreferencesKey("ai_message")
        val KEY_OWNER_ID = stringPreferencesKey("owner_id")
        val KEY_PHONE_IMPORT_STATUS = stringPreferencesKey("phone_import_status")
        val KEY_START_DATE = longPreferencesKey("start_date")
        val KEY_LAST_SHARE_CODE = stringPreferencesKey("last_share_code")
        val KEY_LAST_SHARE_PAYLOAD = stringPreferencesKey("last_share_payload")
        val KEY_COACH_REASONS = stringPreferencesKey("coach_reasons")
        val KEY_USER_AGE = intPreferencesKey("user_age")

        private const val MAX_RECORDS = 20
        private const val MAX_BRIEFINGS = 20
    }

    fun getCachedData(): Flow<WearCache> = context.dataStore.data.map { prefs ->
        val currentGoal = prefs[KEY_CURRENT_GOAL]?.let(::parseGoal)
        val nextGoal = prefs[KEY_NEXT_GOAL]?.let(::parseGoal)
        val records = prefs[KEY_RECENT_RECORDS]?.let(::parseRecords) ?: emptyList()
        val briefings = prefs[KEY_RECENT_BRIEFINGS]?.let(::parseBriefings) ?: emptyList()
        WearCache(
            currentGoal = currentGoal,
            nextGoal = nextGoal,
            lastRecord = records.firstOrNull(),
            recentRecords = records,
            recentBriefings = briefings,
            aiMessage = prefs[KEY_AI_MESSAGE],
            ownerId = prefs[KEY_OWNER_ID],
            phoneImportStatus = prefs[KEY_PHONE_IMPORT_STATUS],
            lastShareCode = prefs[KEY_LAST_SHARE_CODE],
            lastSharePayload = prefs[KEY_LAST_SHARE_PAYLOAD],
            coachReasons = prefs[KEY_COACH_REASONS]?.let(::parseStrings).orEmpty(),
            userAge = prefs[KEY_USER_AGE]
        )
    }

    fun getStartDateFlow(): Flow<Long> = context.metaStore.data.map { prefs ->
        prefs[KEY_START_DATE] ?: System.currentTimeMillis()
    }

    suspend fun saveGoalFromPhone(json: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NEXT_GOAL] = json
            prefs.remove(KEY_COACH_REASONS)
            runCatching {
                val obj = JSONObject(json)
                if (obj.has("aiMessage")) {
                    prefs[KEY_AI_MESSAGE] = obj.getString("aiMessage")
                }
            }
        }
    }

    suspend fun saveRecordsFromPhone(json: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RECENT_RECORDS] = json
        }
    }

    suspend fun saveRunRecord(record: CachedRecord) {
        val updated = getRecordsSync()
            .filterNot { it.id == record.id }
            .toMutableList()
            .apply { add(0, record) }
            .take(MAX_RECORDS)

        context.dataStore.edit { prefs ->
            prefs[KEY_RECENT_RECORDS] = recordsToJson(updated)
        }
    }

    /** 나이 저장. null이면 삭제(미입력) */
    suspend fun saveUserAge(age: Int?) {
        context.dataStore.edit { prefs ->
            if (age == null) prefs.remove(KEY_USER_AGE) else prefs[KEY_USER_AGE] = age
        }
    }

    /** 러닝 후 체감 난이도를 해당 기록에 저장한다. */
    suspend fun saveRunFeedback(recordId: String, effortId: String) {
        context.dataStore.edit { prefs ->
            val records = prefs[KEY_RECENT_RECORDS]?.let(::parseRecords).orEmpty()
            if (records.none { it.id == recordId }) return@edit
            prefs[KEY_RECENT_RECORDS] = recordsToJson(
                records.map { if (it.id == recordId) it.copy(effort = effortId) else it }
            )
        }
    }

    /** 워치에서 계산한 다음 목표와 근거를 저장한다 (폰 없이 동작). */
    suspend fun saveCoachGoal(goal: CachedGoal, headline: String, reasons: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NEXT_GOAL] = JSONObject().apply {
                put("targetKm", goal.targetKm.toDouble())
                put("targetPace", goal.targetPace)
                put("speedupKm", goal.speedupKm.toDouble())
                put("speedupPct", goal.speedupPct)
                put("hrAlertBpm", goal.hrAlertBpm)
                put("aiMessage", headline)
            }.toString()
            prefs[KEY_AI_MESSAGE] = headline
            prefs[KEY_COACH_REASONS] = JSONArray(reasons).toString()
        }
    }

    suspend fun saveBriefing(recordId: String, briefingType: String, content: String) {
        val item = BriefingRecord(
            id = "${recordId}_$briefingType",
            recordId = recordId,
            createdAt = System.currentTimeMillis(),
            briefingType = briefingType,
            content = content
        )
        val updated = getBriefingsSync()
            .filterNot { it.id == item.id }
            .toMutableList()
            .apply { add(0, item) }
            .take(MAX_BRIEFINGS)

        context.dataStore.edit { prefs ->
            prefs[KEY_RECENT_BRIEFINGS] = briefingsToJson(updated)
        }
    }

    suspend fun saveShareInfo(code: String, payload: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_SHARE_CODE] = code
            prefs[KEY_LAST_SHARE_PAYLOAD] = payload
        }
    }

    suspend fun savePhoneImportStatus(message: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PHONE_IMPORT_STATUS] = message
        }
    }

    suspend fun saveOwnerId(ownerId: String) {
        if (ownerId.isBlank()) return
        context.dataStore.edit { prefs ->
            prefs[KEY_OWNER_ID] = ownerId
        }
    }

    suspend fun clearPhoneImportStatus() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_PHONE_IMPORT_STATUS)
        }
    }

    suspend fun acceptNextGoal() {
        context.dataStore.edit { prefs ->
            prefs[KEY_NEXT_GOAL]?.let { prefs[KEY_CURRENT_GOAL] = it }
        }
    }

    fun getStartDate(): Long = runBlocking {
        context.metaStore.data.first()[KEY_START_DATE] ?: System.currentTimeMillis()
    }

    suspend fun getCurrentGoal(): CachedGoal? =
        context.dataStore.data.firstOrNull()?.get(KEY_CURRENT_GOAL)?.let(::parseGoal)

    suspend fun getLastRecordAsync(): CachedRecord? =
        context.dataStore.data.firstOrNull()?.get(KEY_RECENT_RECORDS)?.let(::parseRecords)?.firstOrNull()

    fun getOrCreateOwnerIdSync(): String = runBlocking {
        val existing = context.dataStore.data.first()[KEY_OWNER_ID]
        if (!existing.isNullOrBlank()) {
            existing
        } else {
            val created = UUID.randomUUID().toString()
            context.dataStore.edit { prefs ->
                prefs[KEY_OWNER_ID] = created
            }
            created
        }
    }

    suspend fun initStartDateIfNeeded() {
        val existing = context.metaStore.data.first()[KEY_START_DATE]
        if (existing == null) {
            context.metaStore.edit { it[KEY_START_DATE] = System.currentTimeMillis() }
        }
    }

    fun requestGoalFromPhone() {
        // No-op in watch-only mode. Existing phone sync remains optional.
    }

    fun getCurrentGoalSync(): CachedGoal? = runBlocking {
        context.dataStore.data.first()[KEY_CURRENT_GOAL]?.let(::parseGoal)
    }

    fun getLastRecord(): CachedRecord? = runBlocking {
        getRecordsSync().firstOrNull()
    }

    fun getBriefingsSync(): List<BriefingRecord> = runBlocking {
        context.dataStore.data.first()[KEY_RECENT_BRIEFINGS]
            ?.let(::parseBriefings)
            .orEmpty()
    }

    fun getRecordsSync(): List<CachedRecord> = runBlocking {
        context.dataStore.data.first()[KEY_RECENT_RECORDS]
            ?.let(::parseRecords)
            .orEmpty()
    }

    private fun parseGoal(json: String): CachedGoal? = runCatching {
        val obj = JSONObject(json)
        CachedGoal(
            targetKm = obj.getDouble("targetKm").toFloat(),
            targetPace = obj.getString("targetPace"),
            speedupKm = obj.getDouble("speedupKm").toFloat(),
            speedupPct = obj.getInt("speedupPct"),
            hrAlertBpm = obj.getInt("hrAlertBpm")
        )
    }.getOrNull()

    private fun parseRecords(json: String): List<CachedRecord> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            val distanceKm = obj.getDouble("distanceKm").toFloat()
            CachedRecord(
                id = obj.optString("id", obj.optLong("date").toString()),
                date = obj.getLong("date"),
                distanceKm = distanceKm.takeIf { it.isFinite() && it >= 0f } ?: 0f,
                avgPace = obj.getString("avgPace"),
                avgHeartRate = obj.optInt("avgHeartRate", 0),
                maxHeartRate = obj.optInt("maxHeartRate", 0),
                durationSec = obj.optInt("durationSec", 0),
                fatigueLevel = obj.optString("fatigueLevel", "low"),
                completed = obj.getBoolean("completed"),
                source = obj.optString("source", "watch"),
                effort = obj.optString("effort").takeIf { it.isNotBlank() },
                splitsSec = obj.optJSONArray("splitsSec")?.let { arr ->
                    (0 until arr.length()).map { arr.getInt(it) }
                }.orEmpty(),
                slowdownKm = if (obj.has("slowdownKm")) obj.getDouble("slowdownKm").toFloat() else null
            )
        }
    }.getOrDefault(emptyList())

    private fun parseStrings(json: String): List<String> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())

    private fun parseBriefings(json: String): List<BriefingRecord> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            BriefingRecord(
                id = obj.getString("id"),
                recordId = obj.getString("recordId"),
                createdAt = obj.getLong("createdAt"),
                briefingType = obj.getString("briefingType"),
                content = obj.getString("content")
            )
        }
    }.getOrDefault(emptyList())

    private fun recordsToJson(records: List<CachedRecord>): String {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject().apply {
                    put("id", record.id)
                    put("date", record.date)
                    put("distanceKm", record.distanceKm)
                    put("avgPace", record.avgPace)
                    put("avgHeartRate", record.avgHeartRate)
                    put("maxHeartRate", record.maxHeartRate)
                    put("durationSec", record.durationSec)
                    put("fatigueLevel", record.fatigueLevel)
                    put("completed", record.completed)
                    put("source", record.source)
                    record.effort?.let { put("effort", it) }
                    if (record.splitsSec.isNotEmpty()) put("splitsSec", JSONArray(record.splitsSec))
                    record.slowdownKm?.let { put("slowdownKm", it.toDouble()) }
                }
            )
        }
        return array.toString()
    }

    private fun briefingsToJson(briefings: List<BriefingRecord>): String {
        val array = JSONArray()
        briefings.forEach { briefing ->
            array.put(
                JSONObject().apply {
                    put("id", briefing.id)
                    put("recordId", briefing.recordId)
                    put("createdAt", briefing.createdAt)
                    put("briefingType", briefing.briefingType)
                    put("content", briefing.content)
                }
            )
        }
        return array.toString()
    }
}
