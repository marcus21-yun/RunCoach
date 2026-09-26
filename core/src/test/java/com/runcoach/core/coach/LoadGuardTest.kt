package com.runcoach.core.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class LoadGuardTest {

    private val now = 1_800_000_000_000L
    private fun daysAgo(d: Int) = now - TimeUnit.DAYS.toMillis(d.toLong())
    private fun run(d: Int, km: Float) = PatternRun(daysAgo(d), km, (km * 400).toInt(), 140)

    /** 이전 3주 동안 주 2회 × 5km = 주 10km */
    private val base10 = listOf(9, 12, 16, 19, 23, 26).map { run(it, 5f) }

    @Test
    fun `level comes from weekly records not age`() {
        assertEquals(RunnerLevel.START, LoadGuard.assess(emptyList(), age = 25, now = now).level)
        assertEquals(RunnerLevel.BASE, LoadGuard.assess(base10, age = 25, now = now).level)
        assertEquals(RunnerLevel.BASE, LoadGuard.assess(base10, age = 62, now = now).level)
        val strong = (0 until 28 step 2).map { run(it, 5f) } // 주 약 17.5km
        assertEquals(RunnerLevel.STEADY, LoadGuard.assess(strong, age = null, now = now).level)
    }

    @Test
    fun `weekly cap is baseline plus 10 percent`() {
        val a = LoadGuard.assess(base10 + run(2, 5f), age = null, now = now)
        assertEquals(10f, a.baselineWeekKm)
        assertEquals(11f, a.weeklyCapKm)
        assertEquals(6f, a.remainingKm)
        assertEquals(LoadStatus.SAFE, a.status)
    }

    @Test
    fun `more than 30 percent jump is overload`() {
        val a = LoadGuard.assess(base10 + run(1, 7f) + run(3, 7f), age = null, now = now)
        assertEquals(LoadStatus.OVERLOAD, a.status)
        assertTrue(a.summary.contains("40%"))
    }

    @Test
    fun `no previous weeks means building`() {
        val a = LoadGuard.assess(listOf(run(1, 3f)), age = null, now = now)
        assertEquals(LoadStatus.BUILDING, a.status)
        assertEquals(6f, a.weeklyCapKm)
    }

    @Test
    fun `age sets heart rate thresholds and recovery, unknown keeps 150 and 160`() {
        assertEquals(150 to 160, LoadGuard.heartRateThresholds(null))
        assertEquals(152 to 168, LoadGuard.heartRateThresholds(25)) // 최대 190.5
        assertEquals(136 to 149, LoadGuard.heartRateThresholds(55)) // 최대 169.5
        assertEquals(1, LoadGuard.assess(base10, age = 35, now = now).restDaysAfterHard)
        assertEquals(2, LoadGuard.assess(base10, age = 55, now = now).restDaysAfterHard)
        assertEquals(AgeBand.AGE_40S, AgeBand.of(45))
    }

    @Test
    fun `memory coach respects remaining weekly allowance`() {
        val runs = listOf(RunSnapshot(daysAgo(1), 5f, 390, true, "low", Effort.EASY))
        val goal = GoalSnapshot(5f, 390)
        val load = LoadGuard.assess(base10 + run(1, 5f) + run(3, 3f), age = null, now = now)
        assertEquals(3f, load.remainingKm)
        val plan = MemoryCoach.plan(runs, goal, now, load)
        assertEquals(CoachMode.HOLD, plan.mode)
        assertEquals(3f, plan.targetKm)
        assertTrue(plan.reasons.last().contains("3km 남았어요"))
    }

    @Test
    fun `memory coach rests on overload`() {
        val runs = listOf(RunSnapshot(daysAgo(1), 7f, 390, true, "low", Effort.EASY))
        val load = LoadGuard.assess(base10 + run(1, 7f) + run(3, 7f), age = null, now = now)
        val plan = MemoryCoach.plan(runs, GoalSnapshot(7f, 390), now, load)
        assertEquals(CoachMode.REST, plan.mode)
        assertEquals(MemoryCoach.MIN_KM, plan.targetKm)
    }
}
