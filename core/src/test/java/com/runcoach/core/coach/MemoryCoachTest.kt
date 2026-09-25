package com.runcoach.core.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class MemoryCoachTest {

    private val now = 1_800_000_000_000L
    private fun daysAgo(d: Int) = now - TimeUnit.DAYS.toMillis(d.toLong())

    private fun run(
        days: Int = 3,
        km: Float = 5f,
        pace: Int? = 390,
        completed: Boolean = true,
        fatigue: String = "mid",
        effort: Effort? = null
    ) = RunSnapshot(daysAgo(days), km, pace, completed, fatigue, effort)

    private val goal5k = GoalSnapshot(5f, 390)

    @Test
    fun `no records starts with 3km`() {
        val plan = MemoryCoach.plan(emptyList(), null, now)
        assertEquals(CoachMode.START, plan.mode)
        assertEquals(3f, plan.targetKm)
        assertNull(plan.targetPaceSec)
    }

    @Test
    fun `easy feedback grows distance by 10 percent and keeps pace`() {
        val plan = MemoryCoach.plan(listOf(run(effort = Effort.EASY)), goal5k, now)
        assertEquals(CoachMode.BUILD, plan.mode)
        assertEquals(5.5f, plan.targetKm)
        assertEquals(390, plan.targetPaceSec)
        assertTrue(plan.reasons.first().contains("쉬웠어요"))
    }

    @Test
    fun `distance growth is capped at 1km`() {
        val plan = MemoryCoach.plan(listOf(run(km = 15f, effort = Effort.EASY)), GoalSnapshot(15f, 400), now)
        assertEquals(16f, plan.targetKm)
    }

    @Test
    fun `ok feedback grows distance by 5 percent`() {
        val plan = MemoryCoach.plan(listOf(run(effort = Effort.OK)), goal5k, now)
        assertEquals(CoachMode.BUILD, plan.mode)
        assertEquals(5.3f, plan.targetKm) // 5 + 0.25 → 5.25 → 반올림 5.3
    }

    @Test
    fun `hard but completed holds distance and slows pace`() {
        val plan = MemoryCoach.plan(listOf(run(effort = Effort.HARD)), goal5k, now)
        assertEquals(CoachMode.HOLD, plan.mode)
        assertEquals(5f, plan.targetKm)
        assertEquals(400, plan.targetPaceSec)
        assertEquals(0, plan.speedupPct)
        assertTrue(plan.reasons.first().contains("힘들었어요"))
    }

    @Test
    fun `hard and not completed eases distance`() {
        val plan = MemoryCoach.plan(listOf(run(km = 3.8f, completed = false, effort = Effort.HARD)), goal5k, now)
        assertEquals(CoachMode.EASE, plan.mode)
        assertEquals(4.5f, plan.targetKm)
    }

    @Test
    fun `user feedback overrides sensor fatigue`() {
        val plan = MemoryCoach.plan(listOf(run(fatigue = "high", effort = Effort.EASY)), goal5k, now)
        assertEquals(CoachMode.BUILD, plan.mode)
    }

    @Test
    fun `missing feedback falls back to fatigue and says so`() {
        val plan = MemoryCoach.plan(listOf(run(fatigue = "high")), goal5k, now)
        assertEquals(CoachMode.HOLD, plan.mode)
        assertTrue(plan.reasons.first().contains("체감 기록이 없어"))
    }

    @Test
    fun `two hard runs out of three holds even if last was ok`() {
        val runs = listOf(
            run(days = 2, effort = Effort.HARD),
            run(days = 5, effort = Effort.OK),
            run(days = 9, effort = Effort.HARD)
        )
        val plan = MemoryCoach.plan(runs, goal5k, now)
        assertEquals(CoachMode.HOLD, plan.mode)
        assertEquals(5f, plan.targetKm)
    }

    @Test
    fun `long break triggers return plan at 60 percent`() {
        val plan = MemoryCoach.plan(listOf(run(days = 20, effort = Effort.EASY)), goal5k, now)
        assertEquals(CoachMode.RETURN, plan.mode)
        assertEquals(3f, plan.targetKm)
        assertEquals(420, plan.targetPaceSec)
        assertTrue(plan.reasons.first().contains("20일"))
    }

    @Test
    fun `one week plus break eases 20 percent`() {
        val plan = MemoryCoach.plan(listOf(run(days = 10, effort = Effort.EASY)), goal5k, now)
        assertEquals(CoachMode.EASE, plan.mode)
        assertEquals(4f, plan.targetKm)
    }

    @Test
    fun `never goes below minimum distance`() {
        val plan = MemoryCoach.plan(listOf(run(days = 30, km = 1f, pace = null)), GoalSnapshot(2f, null), now)
        assertEquals(MemoryCoach.MIN_KM, plan.targetKm)
        assertNull(plan.targetPaceSec)
    }

    @Test
    fun `pace helpers round trip`() {
        assertEquals(390, paceToSecondsOrNull("6:30"))
        assertNull(paceToSecondsOrNull("--:--"))
        assertEquals("6:05", secondsToPace(365))
        assertEquals("--:--", secondsToPace(null))
    }
}
