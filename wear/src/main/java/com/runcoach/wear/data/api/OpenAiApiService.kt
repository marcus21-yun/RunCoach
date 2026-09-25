package com.runcoach.wear.data.api

import com.runcoach.wear.data.model.CoachStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class OpenAiApiService(private val apiKey: String) {

    companion object {
        private const val API_URL = "https://api.openai.com/v1/chat/completions"
        private const val MODEL = "gpt-4o-mini"
        private const val MAX_TOKENS = 500
        private const val TIMEOUT_MS = 15_000
    }

    suspend fun generatePreBriefing(
        coachStyle: CoachStyle,
        lastKm: Float,
        lastPace: String,
        completed: Boolean,
        fatigue: String,
        targetKm: Float,
        targetPace: String,
        weeksLeft: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        val userPrompt = """
            아래 러닝 데이터를 바탕으로 러닝 전 브리핑 멘트를 작성해줘.
            - 지난주 거리: ${lastKm}km
            - 지난주 페이스: $lastPace
            - 지난주 완주 여부: ${if (completed) "완주" else "미완주"}
            - 지난주 피로도: $fatigue
            - 오늘 목표 거리: ${targetKm}km
            - 오늘 목표 페이스: $targetPace
            - 하프마라톤까지 남은 주: ${weeksLeft}주

            조건: 전략 중심으로, 숫자 나열 금지, 300자 이내, 음성으로 읽을 텍스트이므로 특수문자 사용 금지.
        """.trimIndent()

        callApi(coachStyle.systemPrompt, userPrompt)
    }

    suspend fun generatePostBriefing(
        coachStyle: CoachStyle,
        actualKm: Float,
        targetKm: Float,
        completed: Boolean,
        avgPace: String,
        targetPace: String,
        avgHr: Int,
        maxHr: Int,
        fatigue: String,
        diffKm: Float,
        nextKm: Float
    ): Result<String> = withContext(Dispatchers.IO) {
        val userPrompt = """
            아래 오늘 러닝 결과를 바탕으로 운동 후 피드백 멘트를 작성해줘.
            - 실제 거리: ${actualKm}km / 목표: ${targetKm}km
            - 완주 여부: ${if (completed) "완주" else "미완주"}
            - 평균 페이스: $avgPace / 목표: $targetPace
            - 평균 심박: ${avgHr}bpm / 최고 심박: ${maxHr}bpm
            - 피로도: $fatigue
            - 지난주 대비 거리 변화: ${if (diffKm >= 0) "+${diffKm}" else "$diffKm"}km
            - 다음 주 AI 제안 거리: ${nextKm}km

            조건: 잘된 점 1가지와 개선할 점 1가지 반드시 포함, 다음 주 목표 자연스럽게 언급, 400자 이내, 특수문자 사용 금지.
        """.trimIndent()

        callApi(coachStyle.systemPrompt, userPrompt)
    }

    private fun callApi(systemPrompt: String, userPrompt: String): Result<String> {
        return try {
            val requestBody = JSONObject().apply {
                put("model", MODEL)
                put("max_tokens", MAX_TOKENS)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userPrompt)
                    })
                })
            }

            val url = URL(API_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
            }

            conn.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode != 200) {
                return Result.failure(Exception("API 오류: ${conn.responseCode}"))
            }

            val response = BufferedReader(InputStreamReader(conn.inputStream)).use {
                it.readText()
            }

            val content = JSONObject(response)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()

            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
