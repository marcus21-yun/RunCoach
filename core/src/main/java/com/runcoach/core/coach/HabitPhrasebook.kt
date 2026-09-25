package com.runcoach.core.coach

import kotlin.math.roundToInt

/** 러닝 중 음성 코치의 말투. */
enum class CoachTone {
    /** 걱정 많은 엄마 — 따뜻한 잔소리, 반말 */
    MOM,
    /** 무뚝뚝한 아빠 — 짧고 단호한 잔소리, 반말 */
    DAD,
    /** 기본 존댓말 */
    POLITE
}

/**
 * 러닝 중 안내 문구. 실시간 안내는 지연이 없어야 하므로 생성형 AI 대신 고정 문구를 쓴다.
 * 호흡 안내는 일반적인 2:2 리듬(두 걸음 들이쉬고 두 걸음 내쉬기) 권장이며 의학적 처방이 아니다.
 */
object HabitPhrasebook {

    fun text(cue: HabitCue, tone: CoachTone): String = when (tone) {
        CoachTone.MOM -> mom(cue)
        CoachTone.DAD -> dad(cue)
        CoachTone.POLITE -> polite(cue)
    }

    private fun mom(c: HabitCue): String = when (c.type) {
        CueType.SAFETY_SLOW -> "심박이 ${c.bpm}이야. 걸어도 괜찮으니까 속도 줄여. 무리하지 마, 엄마 걱정돼."
        CueType.BREATHING -> "숨 좀 고르자. 코로 들이쉬고, 들이쉬고. 입으로 내쉬고, 내쉬고. 옳지, 그렇게."
        CueType.BREATHING_REPEAT -> "아직 숨차지? 보폭 좀 줄이고, 내쉴 때 후우 길게. 천천히 가도 돼."
        CueType.WARMUP -> "자, 처음 5분은 천천히. 처음부터 뛰어나가면 금방 지쳐."
        CueType.PATTERN_AHEAD -> if (c.repeatCount >= 2)
            "또 여기다. 요즘 ${km(c.km)}킬로 지나면 자꾸 느려지더라. 이번엔 버텨보자!"
        else
            "지난번에 ${km(c.km)}킬로 지나서 느려졌지? 여기서부터 정신 바짝 차려."
        CueType.PACE_DROP -> "어머, 속도 떨어졌다. 팔 좀 힘차게 흔들어봐. 조금만 더 힘내!"
        CueType.TOO_FAST -> "너무 빨라! 처음부터 힘 빼면 끝까지 못 간다. 조금 천천히."
        CueType.UNSTEADY -> "빨랐다 느렸다 하지 말고 일정하게 가자. 그게 제일 덜 힘들어."
        CueType.POSTURE -> listOf(
            "어깨에 힘 빼. 또 올라갔다.",
            "고개 들고 앞을 봐. 땅만 보지 말고.",
            "발 끌지 말고 가볍게 톡톡."
        )[c.variant % 3]
        CueType.FINAL_PUSH -> "거의 다 왔어! 마지막 500미터, 엄마가 보고 있다!"
    }

    private fun dad(c: HabitCue): String = when (c.type) {
        CueType.SAFETY_SLOW -> "심박 ${c.bpm}이다. 걸어라. 오늘 무리할 필요 없다."
        CueType.BREATHING -> "호흡 맞춰라. 들이쉬고, 들이쉬고. 내쉬고, 내쉬고."
        CueType.BREATHING_REPEAT -> "아직 숨이 차구나. 보폭 줄이고 길게 내쉬어라."
        CueType.WARMUP -> "천천히 시작해라. 몸 풀리면 그때 가도 늦지 않다."
        CueType.PATTERN_AHEAD -> if (c.repeatCount >= 2)
            "${km(c.km)}킬로에서 처지는 게 벌써 ${c.repeatCount}번째다. 오늘은 버텨라."
        else
            "지난번엔 ${km(c.km)}킬로 지나서 처졌다. 여기서 버텨라."
        CueType.PACE_DROP -> "속도 떨어졌다. 팔 치고, 조금만 더 밀어붙여라."
        CueType.TOO_FAST -> "너무 빠르다. 욕심내지 말고 속도 줄여라."
        CueType.UNSTEADY -> "속도가 들쭉날쭉하다. 일정하게 가라."
        CueType.POSTURE -> listOf("어깨 내려라.", "고개 들어라.", "발 끌지 마라.")[c.variant % 3]
        CueType.FINAL_PUSH -> "다 왔다. 마지막 500미터, 끝까지 가라."
    }

    private fun polite(c: HabitCue): String = when (c.type) {
        CueType.SAFETY_SLOW -> "심박수가 ${c.bpm}입니다. 걸으셔도 괜찮아요. 속도를 줄여주세요."
        CueType.BREATHING -> "호흡을 맞춰볼게요. 들이쉬고, 들이쉬고. 내쉬고, 내쉬고."
        CueType.BREATHING_REPEAT -> "아직 숨이 차시죠? 보폭을 줄이고 길게 내쉬어 보세요."
        CueType.WARMUP -> "처음 5분은 천천히 시작해요."
        CueType.PATTERN_AHEAD -> if (c.repeatCount >= 2)
            "최근 ${c.repeatCount}번, ${km(c.km)}킬로 지점에서 느려지셨어요. 이번엔 여기서 힘내봐요."
        else
            "지난번 ${km(c.km)}킬로 지점에서 느려지셨어요. 여기서부터 집중해요."
        CueType.PACE_DROP -> "속도가 떨어졌어요. 팔을 조금 더 힘차게 흔들어 보세요."
        CueType.TOO_FAST -> "초반이 빨라요. 조금 천천히 가면 끝까지 편해요."
        CueType.UNSTEADY -> "속도가 들쭉날쭉해요. 일정하게 유지해 보세요."
        CueType.POSTURE -> listOf(
            "어깨 힘을 빼 주세요.",
            "고개를 들고 앞을 보세요.",
            "발을 가볍게 디뎌 보세요."
        )[c.variant % 3]
        CueType.FINAL_PUSH -> "마지막 500미터입니다. 끝까지 힘내세요."
    }

    private fun km(v: Float): String {
        val r = (v * 10f).roundToInt() / 10f
        return if (r % 1f == 0f) r.toInt().toString() else "%.1f".format(r)
    }
}
