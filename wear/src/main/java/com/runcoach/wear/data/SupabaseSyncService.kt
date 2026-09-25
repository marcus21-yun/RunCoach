package com.runcoach.wear.data

import android.util.Log
import com.runcoach.wear.BuildConfig
import com.runcoach.wear.data.model.CoachStyle
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ShareResult(
    val code: String,
    val payload: String
)

@Singleton
class SupabaseSyncService @Inject constructor(
    private val dataStore: WearDataStore
) {

    companion object {
        private const val TAG = "RunCoachWear"
    }

    fun isConfigured(): Boolean {
        val url = BuildConfig.SUPABASE_URL
        val key = BuildConfig.SUPABASE_ANON_KEY
        val ok = url.isNotBlank() && key.isNotBlank() && key.startsWith("eyJ")
        if (!ok) Log.w(TAG, "Supabase 미설정 url=$url keyPrefix=${key.take(10)}")
        return ok
    }

    fun syncHistory(cache: WearCache) {
        require(isConfigured()) { "Supabase 설정이 비어 있습니다." }
        val ownerId = dataStore.getOrCreateOwnerIdSync()

        if (cache.recentRecords.isNotEmpty()) {
            val records = JSONArray()
            cache.recentRecords.forEach { record ->
                val recordKey = record.id.ifBlank { "watch-${record.date}" }
                records.put(
                    JSONObject().apply {
                        put("owner_id", ownerId)
                        put("record_key", recordKey)
                        put("recorded_at", record.date)
                        put("distance_km", record.distanceKm)
                        put("target_km", record.distanceKm)
                        put("avg_pace", record.avgPace)
                        put("target_pace", record.avgPace)
                        put("avg_heart_rate", record.avgHeartRate)
                        put("max_heart_rate", record.maxHeartRate)
                        put("duration_sec", record.durationSec)
                        put("fatigue_level", record.fatigueLevel)
                        put("completed", record.completed)
                        put("source", record.source)
                        put("source_package", "wear_os")
                    }
                )
            }
            postJson("/rest/v1/run_records?on_conflict=record_key", records)
        }

        if (cache.recentBriefings.isNotEmpty()) {
            val briefings = JSONArray()
            cache.recentBriefings.forEach { briefing ->
                briefings.put(
                    JSONObject().apply {
                        put("owner_id", ownerId)
                        put("briefing_key", briefing.id)
                        put("record_key", briefing.recordId)
                        put("briefing_type", briefing.briefingType)
                        put("content", briefing.content)
                        put("created_at", briefing.createdAt)
                        put("provider", "watch")
                    }
                )
            }
            postJson("/rest/v1/briefing_records?on_conflict=briefing_key", briefings)
        }
    }

    // ── 코치 스타일 Supabase 동기화 ───────────────────────────────

    fun saveCoachStyle(style: CoachStyle) {
        if (!isConfigured()) return
        val ownerId = dataStore.getOrCreateOwnerIdSync()
        val body = JSONArray().put(
            JSONObject().apply {
                put("owner_id", ownerId)
                put("coach_style", style.id)
                put("updated_at", java.time.Instant.now().toString())
            }
        )
        runCatching {
            postJson("/rest/v1/user_coach_preference?on_conflict=owner_id", body)
        }.onFailure { Log.w(TAG, "코치 스타일 Supabase 저장 실패: ${it.message}") }
    }

    fun fetchCoachStyle(): CoachStyle? {
        if (!isConfigured()) return null
        val ownerId = dataStore.getOrCreateOwnerIdSync()
        return runCatching {
            val connection = openConnection(
                "/rest/v1/user_coach_preference?owner_id=eq.$ownerId&select=coach_style",
                "GET"
            )
            val status = connection.responseCode
            if (status !in 200..299) return@runCatching null
            val body = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            val array = JSONArray(body)
            if (array.length() == 0) return@runCatching null
            val styleId = array.getJSONObject(0).getString("coach_style")
            CoachStyle.fromId(styleId)
        }.getOrNull()
    }

    fun createShare(record: CachedRecord, briefing: BriefingRecord?): ShareResult {
        require(isConfigured()) { "Supabase 설정이 비어 있습니다." }

        val code = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
        val ownerId = dataStore.getOrCreateOwnerIdSync()
        val body = JSONObject().apply {
            put("owner_id", ownerId)
            put("share_token", code)
            put("share_url", "runcoach://share?code=$code")
            put("recorded_at", record.date)
            put("distance_km", record.distanceKm)
            put("avg_pace", record.avgPace)
            put("avg_heart_rate", record.avgHeartRate)
            put("duration_sec", record.durationSec)
            put("source", record.source)
            put("briefing", briefing?.content ?: "")
        }
        postJson("/rest/v1/shared_run_records?on_conflict=share_token", JSONArray().put(body))
        return ShareResult(
            code = code,
            payload = "runcoach://share?code=$code"
        )
    }

    private fun postJson(path: String, body: Any) {
        val connection = openConnection(path, "POST")
        connection.setRequestProperty("Prefer", "resolution=merge-duplicates")
        connection.doOutput = true
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(body.toString())
        }

        val status = connection.responseCode
        if (status !in 200..299) {
            val err = readError(connection)
            Log.e(TAG, "Supabase POST $path -> $status $err")
            throw IllegalStateException(err)
        }
        Log.d(TAG, "Supabase POST $path -> $status OK")
        connection.inputStream?.close()
        connection.disconnect()
    }

    private fun openConnection(path: String, method: String): HttpURLConnection {
        val baseUrl = BuildConfig.SUPABASE_URL.removeSuffix("/")
        val apiKey = BuildConfig.SUPABASE_ANON_KEY
        return (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("apikey", apiKey)
            if (apiKey.startsWith("eyJ")) {
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
        }
    }

    private fun readError(connection: HttpURLConnection): String {
        val text = runCatching {
            connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
        }.getOrNull()
        return "Supabase request failed (${connection.responseCode})${if (text.isNullOrBlank()) "" else ": $text"}"
    }
}
