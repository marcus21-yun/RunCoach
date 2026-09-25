package com.runcoach.app.data.supabase

import com.runcoach.app.BuildConfig
import com.runcoach.app.data.db.BriefingRecord
import com.runcoach.app.data.db.RunningRecord
import com.runcoach.app.data.identity.OwnerIdProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.text.Charsets.UTF_8

@Singleton
class SupabaseSyncService @Inject constructor(
    private val ownerIdProvider: OwnerIdProvider
) {

    fun isConfigured(): Boolean =
        BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    suspend fun syncRecords(records: List<RunningRecord>, briefingMap: Map<Long, BriefingRecord?>): SyncResult {
        if (!isConfigured()) {
            return SyncResult(false, "SUPABASE_URL 또는 SUPABASE_ANON_KEY가 비어 있습니다.")
        }

        val runs = JSONArray()
        val briefings = JSONArray()
        val ownerId = ownerIdProvider.getOrCreate()

        records.forEach { record ->
            val recordKey = record.externalId ?: "local-${record.id}"
            runs.put(
                JSONObject().apply {
                    put("owner_id", ownerId)
                    put("record_key", recordKey)
                    put("recorded_at", record.date)
                    put("distance_km", record.distanceKm.toDouble())
                    put("target_km", record.targetKm.toDouble())
                    put("avg_pace", record.avgPace)
                    put("target_pace", record.targetPace)
                    put("avg_heart_rate", record.avgHeartRate)
                    put("max_heart_rate", record.maxHeartRate)
                    put("duration_sec", record.durationSec)
                    put("fatigue_level", record.fatigueLevel)
                    put("completed", record.completed)
                    put("source", record.source)
                    put("source_package", record.sourcePackage)
                }
            )

            briefingMap[record.id]?.let { briefing ->
                briefings.put(
                    JSONObject().apply {
                        put("owner_id", ownerId)
                        put("briefing_key", briefing.briefingKey)
                        put("record_key", recordKey)
                        put("briefing_type", briefing.briefingType)
                        put("content", briefing.content)
                        put("created_at", briefing.createdAt)
                        put("provider", briefing.provider)
                    }
                )
            }
        }

        postJsonArray("run_records", runs, "record_key")
        if (briefings.length() > 0) {
            postJsonArray("briefing_records", briefings, "briefing_key")
        }

        return SyncResult(true, "${records.size}개의 러닝 기록을 Supabase에 업로드했습니다.")
    }

    suspend fun createShare(record: RunningRecord, briefing: BriefingRecord?): ShareResult {
        if (!isConfigured()) {
            return ShareResult(null, "Supabase 설정이 필요합니다.")
        }

        val shareToken = UUID.randomUUID().toString().replace("-", "").take(12)
        val shareUrl = "runcoach://share?code=$shareToken"
        val ownerId = ownerIdProvider.getOrCreate()
        val payload = JSONObject().apply {
            put("owner_id", ownerId)
            put("share_token", shareToken)
            put("share_url", shareUrl)
            put("recorded_at", record.date)
            put("distance_km", record.distanceKm.toDouble())
            put("avg_pace", record.avgPace)
            put("avg_heart_rate", record.avgHeartRate)
            put("duration_sec", record.durationSec)
            put("source", record.source)
            put("briefing", briefing?.content ?: "")
        }

        postJsonArray("shared_run_records", JSONArray().put(payload), "share_token")
        return ShareResult(
            share = SharedRunShare(
                code = shareToken,
                qrPayload = shareUrl,
                briefing = briefing?.content.orEmpty(),
                distanceKm = record.distanceKm,
                avgPace = record.avgPace
            ),
            message = "QR 공유 코드를 만들었습니다."
        )
    }

    suspend fun fetchSharedRun(code: String): SharedRunComparison? {
        if (!isConfigured() || code.isBlank()) return null

        val queryCode = URLEncoder.encode(code, UTF_8.name())
        val connection = createConnection(
            path = "shared_run_records?select=share_token,recorded_at,distance_km,avg_pace,avg_heart_rate,duration_sec,source,briefing&share_token=eq.$queryCode&limit=1",
            method = "GET"
        )

        val responseBody = connection.inputStream.bufferedReader().use(BufferedReader::readText)
        val array = JSONArray(responseBody)
        if (array.length() == 0) return null

        val item = array.getJSONObject(0)
        return SharedRunComparison(
            shareCode = item.getString("share_token"),
            recordedAt = item.getLong("recorded_at"),
            distanceKm = item.getDouble("distance_km").toFloat(),
            avgPace = item.getString("avg_pace"),
            avgHeartRate = item.optInt("avg_heart_rate"),
            durationSec = item.optInt("duration_sec"),
            source = item.optString("source"),
            briefing = item.optString("briefing")
        )
    }

    private fun postJsonArray(table: String, payload: JSONArray, conflictKey: String) {
        val connection = createConnection("$table?on_conflict=$conflictKey", "POST").apply {
            setRequestProperty("Prefer", "resolution=merge-duplicates")
            doOutput = true
        }

        OutputStreamWriter(connection.outputStream, UTF_8).use { writer ->
            writer.write(payload.toString())
        }

        val status = connection.responseCode
        if (status !in 200..299) {
            val error = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
            throw IllegalStateException("Supabase request failed: $status ${error.orEmpty()}")
        }
    }

    private fun createConnection(path: String, method: String): HttpURLConnection {
        val normalizedUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val apiKey = BuildConfig.SUPABASE_ANON_KEY
        return (URL("$normalizedUrl/rest/v1/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("apikey", apiKey)
            if (apiKey.startsWith("eyJ")) {
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
    }
}

data class SyncResult(
    val success: Boolean,
    val message: String
)

data class SharedRunShare(
    val code: String,
    val qrPayload: String,
    val briefing: String,
    val distanceKm: Float,
    val avgPace: String
)

data class ShareResult(
    val share: SharedRunShare?,
    val message: String
)

data class SharedRunComparison(
    val shareCode: String,
    val recordedAt: Long,
    val distanceKm: Float,
    val avgPace: String,
    val avgHeartRate: Int,
    val durationSec: Int,
    val source: String,
    val briefing: String
)
