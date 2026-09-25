package com.runcoach.wear.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.runcoach.wear.data.api.OpenAiApiService
import com.runcoach.wear.data.model.CoachStyle
import kotlinx.coroutines.flow.first

private val Context.briefingDataStore by preferencesDataStore(name = "briefing_cache")

class BriefingRepository(
    private val context: Context,
    private val apiService: OpenAiApiService
) {

    companion object {
        private val KEY_PRE_TEXT = stringPreferencesKey("briefing_pre_text")
        private val KEY_PRE_TS = longPreferencesKey("briefing_pre_timestamp")
        private val KEY_POST_TEXT = stringPreferencesKey("briefing_post_text")
        private val KEY_POST_TS = longPreferencesKey("briefing_post_timestamp")
        private val KEY_COACH_STYLE = stringPreferencesKey("briefing_coach_style")

        // 캐시 유효 시간: 7일 (주간 러닝 주기에 맞춤)
        private const val CACHE_VALID_MS = 7 * 24 * 60 * 60 * 1000L
    }

    // ── 코치 스타일 저장/불러오기 ──────────────────────────────────

    suspend fun saveCoachStyle(style: CoachStyle) {
        context.briefingDataStore.edit { it[KEY_COACH_STYLE] = style.id }
    }

    suspend fun loadCoachStyle(): CoachStyle {
        val prefs = context.briefingDataStore.data.first()
        return CoachStyle.fromId(prefs[KEY_COACH_STYLE] ?: CoachStyle.MOM.id)
    }

    // ── 사전 브리핑 ───────────────────────────────────────────────

    suspend fun getPreBriefing(
        lastKm: Float,
        lastPace: String,
        completed: Boolean,
        fatigue: String,
        targetKm: Float,
        targetPace: String,
        weeksLeft: Int
    ): String {
        val coachStyle = loadCoachStyle()

        // API 호출
        val apiResult = apiService.generatePreBriefing(
            coachStyle, lastKm, lastPace, completed, fatigue,
            targetKm, targetPace, weeksLeft
        )

        return if (apiResult.isSuccess) {
            val text = apiResult.getOrThrow()
            cachePreBriefing(text)
            text
        } else {
            // API 실패 → 캐시 시도
            loadCachedPreBriefing()
                ?: buildPreTemplate(targetKm, targetPace) // 캐시도 없으면 기본 템플릿
        }
    }

    suspend fun getPostBriefing(
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
    ): String {
        val coachStyle = loadCoachStyle()

        val apiResult = apiService.generatePostBriefing(
            coachStyle, actualKm, targetKm, completed,
            avgPace, targetPace, avgHr, maxHr, fatigue, diffKm, nextKm
        )

        return if (apiResult.isSuccess) {
            val text = apiResult.getOrThrow()
            cachePostBriefing(text)
            text
        } else {
            loadCachedPostBriefing()
                ?: buildPostTemplate(actualKm, completed)
        }
    }

    // ── 캐시 저장/불러오기 ────────────────────────────────────────

    private suspend fun cachePreBriefing(text: String) {
        context.briefingDataStore.edit {
            it[KEY_PRE_TEXT] = text
            it[KEY_PRE_TS] = System.currentTimeMillis()
        }
    }

    private suspend fun cachePostBriefing(text: String) {
        context.briefingDataStore.edit {
            it[KEY_POST_TEXT] = text
            it[KEY_POST_TS] = System.currentTimeMillis()
        }
    }

    private suspend fun loadCachedPreBriefing(): String? {
        val prefs = context.briefingDataStore.data.first()
        val ts = prefs[KEY_PRE_TS] ?: return null
        if (System.currentTimeMillis() - ts > CACHE_VALID_MS) return null
        return prefs[KEY_PRE_TEXT]
    }

    private suspend fun loadCachedPostBriefing(): String? {
        val prefs = context.briefingDataStore.data.first()
        val ts = prefs[KEY_POST_TS] ?: return null
        if (System.currentTimeMillis() - ts > CACHE_VALID_MS) return null
        return prefs[KEY_POST_TEXT]
    }

    // ── 기본 템플릿 (오프라인 + 캐시 모두 없을 때) ───────────────

    private fun buildPreTemplate(targetKm: Float, targetPace: String): String =
        "오늘 목표는 ${targetKm}킬로미터, 페이스는 ${targetPace}입니다. " +
        "처음 2킬로미터는 천천히 몸을 풀어주세요. 화이팅!"

    private fun buildPostTemplate(actualKm: Float, completed: Boolean): String =
        if (completed)
            "${actualKm}킬로미터 완주하셨습니다! 정말 수고하셨어요. 다음 주도 기대할게요."
        else
            "오늘은 ${actualKm}킬로미터 달리셨어요. 괜찮아요, 다음 주에 다시 도전해봐요."
}
