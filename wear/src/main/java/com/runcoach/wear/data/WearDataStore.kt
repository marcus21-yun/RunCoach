package com.runcoach.wear.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
    val source: String = "watch"
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
    val lastSharePayload: String? = null
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
            lastSharePayload = prefs[KEY_LAST_SHARE_PAYLOAD]
        )
    }

    fun getStartDateFlow(): Flow<Long> = context.metaStore.data.map { prefs ->
        prefs[KEY_START_DATE] ?: System.currentTimeMillis()
    }

    suspend fun saveGoalFromPhone(json: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NEXT_GOAL] = json
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
                source = obj.optString("source", "watch")
            )
        }
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
