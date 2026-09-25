package com.runcoach.core.coach

import java.time.DayOfWeek
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 선수들의 훈련 패턴과 나의 패턴을 나란히 놓은 비교 1줄.
 * 선수 쪽 문장은 공개 연구에서 확인한 일반 경향이며, 따라야 할 목표치가 아니라 "생각해볼 거울"이다.
 */
data class AthleteComparison(
    val topic: String,
    val athlete: String,
    val me: String,
    /** 선수 패턴과 차이가 커서 생각해볼 만한 항목 */
    val worthThinking: Boolean
)

/**
 * 멘탈 코치가 던지는 대화 주제.
 * @param askOthers 친구·가족·러닝 크루에게 그대로 물어볼 수 있는 질문 (다른 사람과 비교하며 이야기하기)
 * @param askMyself 다음 러닝 전에 스스로 떠올려볼 질문
 */
data class ConversationTopic(
    val id: String,
    val observation: String,
    val askOthers: String,
    val askMyself: String
)

data class Reflection(
    val comparisons: List<AthleteComparison>,
    val topic: ConversationTopic
)

/**
 * 러닝 후 멘탈 코치.
 *
 * 목표 달성을 재촉하지 않는다. 내 기록에서 드러난 습관을 실제 선수들의 훈련 패턴과
 * 나란히 보여주고, 다른 사람과 이야기해볼 질문 하나를 던져 스스로 러닝 패턴을 돌아보게 한다.
 * 관찰 문장은 저장된 기록에서 계산한 사실만 담고, 데이터가 부족하면 비교하지 않는다.
 *
 * 선수 패턴 근거:
 * - 강도 분배: 엘리트 지구력 선수는 훈련의 약 80%를 낮은 강도로 수행 (Seiler 2010 등 관찰 연구)
 * - 페이스 배분: 세계 기록급 마라톤은 대부분 고른 페이스 또는 약한 네거티브 스플릿
 * - 규칙성: 일반 성인 신체활동 권장량은 주 150분 이상 중강도 (WHO)
 */
object ReflectionCoach {

    const val WHO_WEEKLY_MINUTES = 150

    fun reflect(p: RunPatternProfile): Reflection {
        val comparisons = buildList {
            p.easyShare?.takeIf { p.runsWithHr >= 3 }?.let { share ->
                val pct = (share * 100).roundToInt()
                add(
                    AthleteComparison(
                        topic = "강도 분배",
                        athlete = "엘리트 선수는 훈련의 약 80%를 편한 강도로 달려요.",
                        me = "최근 ${p.runsWithHr}번 중 ${pct}%가 편한 심박이었어요.",
                        worthThinking = share < 0.6f
                    )
                )
            }
            p.latestSecondHalfChange?.let { change ->
                val pct = (abs(change) * 100).roundToInt()
                val me = when (RunPatternAnalyzer.pacingStyle(change)) {
                    PacingStyle.POSITIVE -> "지난 러닝은 후반이 ${pct}% 느려졌어요."
                    PacingStyle.NEGATIVE -> "지난 러닝은 후반이 ${pct}% 빨라졌어요."
                    PacingStyle.EVEN -> "지난 러닝은 전·후반이 고르게 유지됐어요."
                }
                add(
                    AthleteComparison(
                        topic = "페이스 배분",
                        athlete = "세계 기록 마라톤은 대부분 고른 페이스나 후반이 살짝 빠른 흐름이에요.",
                        me = me,
                        worthThinking = RunPatternAnalyzer.pacingStyle(change) == PacingStyle.POSITIVE
                    )
                )
            }
            if (p.runsLast28Days > 0) {
                add(
                    AthleteComparison(
                        topic = "꾸준함",
                        athlete = "선수의 핵심은 양보다 규칙성이에요. 일반 권장은 주 150분 이상이에요.",
                        me = "최근 4주 중 ${p.activeWeeks}주 달렸고, 주 평균 ${p.weeklyMinutes}분이에요.",
                        worthThinking = p.activeWeeks < 3
                    )
                )
            }
        }.sortedByDescending { it.worthThinking }

        return Reflection(comparisons, pickTopic(p))
    }

    /** 가장 생각해볼 만한 습관 하나를 골라 대화 주제로 만든다. */
    private fun pickTopic(p: RunPatternProfile): ConversationTopic {
        val slowdown = p.slowdown
        if (slowdown != null && slowdown.repeatCount >= 2) {
            val km = fmtKm(slowdown.km)
            return ConversationTopic(
                id = "slowdown_repeat",
                observation = "최근 ${slowdown.repeatCount}번, ${km}km 지나서 속도가 떨어졌어요.",
                askOthers = "“나는 늘 ${km}km쯤에서 무너져. 너는 언제 제일 그만두고 싶어져? 그때 무슨 생각해?”",
                askMyself = "${km}km쯤에서 머릿속에 무슨 말이 떠오르는지 다음에 한번 들어봐요."
            )
        }
        if (p.pacedRuns >= 2 && p.positiveSplitCount >= 2) {
            return ConversationTopic(
                id = "positive_split",
                observation = "최근 ${p.pacedRuns}번 중 ${p.positiveSplitCount}번 후반에 느려졌어요.",
                askOthers = "“선수들은 후반을 더 빠르게 달린대. 너는 처음에 천천히 가는 편이야, 빨리 가는 편이야?”",
                askMyself = "처음 1km를 일부러 늦게 가면 마지막이 어떻게 느껴질지 궁금하지 않나요?"
            )
        }
        val easy = p.easyShare
        if (easy != null && p.runsWithHr >= 3 && easy < 0.6f) {
            return ConversationTopic(
                id = "too_hard",
                observation = "최근 러닝 대부분이 숨찬 강도였어요.",
                askOthers = "“선수들은 80%를 편하게 달린대. 너는 달리면서 대화할 수 있는 속도야?”",
                askMyself = "‘편하게 달리면 운동이 안 된다’는 생각이 나에게 있는지 떠올려봐요."
            )
        }
        if (p.runsLast28Days > 0 && p.activeWeeks < 3) {
            return ConversationTopic(
                id = "irregular",
                observation = "최근 4주 중 ${p.activeWeeks}주만 달렸어요.",
                askOthers = "“요즘 달리기를 빼먹게 되는 이유가 뭐야? 너는 어떻게 꾸준히 해?”",
                askMyself = "달리지 못한 날, 나를 멈추게 한 건 시간이었나요, 마음이었나요?"
            )
        }
        val day = p.favoriteDay
        if (day != null && p.runsLast28Days >= 3 && p.favoriteDayShare >= 0.5f) {
            return ConversationTopic(
                id = "ritual_day",
                observation = "달리기의 ${(p.favoriteDayShare * 100).roundToInt()}%가 ${dayName(day)}이에요. 나만의 루틴이 생겼어요.",
                askOthers = "“나는 ${dayName(day)}마다 달려. 너만의 달리는 요일이나 의식이 있어?”",
                askMyself = "${dayName(day)} 러닝이 나에게 어떤 의미가 됐는지 한 문장으로 말해봐요."
            )
        }
        return ConversationTopic(
            id = "why_run",
            observation = if (p.runsLast28Days == 0) "아직 비교할 기록이 충분하지 않아요." else "좋은 흐름이에요.",
            askOthers = "“너는 왜 달려? 처음 달리기 시작한 날 기억나?”",
            askMyself = "내가 달리기를 시작한 이유를 누군가에게 말해본 적 있나요?"
        )
    }

    private fun dayName(d: DayOfWeek) = when (d) {
        DayOfWeek.MONDAY -> "월요일"
        DayOfWeek.TUESDAY -> "화요일"
        DayOfWeek.WEDNESDAY -> "수요일"
        DayOfWeek.THURSDAY -> "목요일"
        DayOfWeek.FRIDAY -> "금요일"
        DayOfWeek.SATURDAY -> "토요일"
        DayOfWeek.SUNDAY -> "일요일"
    }

    private fun fmtKm(v: Float): String {
        val r = (v * 10f).roundToInt() / 10f
        return if (r % 1f == 0f) r.toInt().toString() else "%.1f".format(r)
    }
}
