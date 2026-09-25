package com.runcoach.core.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

class ReflectionCoachTest {

    private val zone = ZoneOffset.UTC
    // 2026-09-27 (일요일) 12:00 UTC
    private val now = LocalDate.of(2026, 9, 27).atTime(12, 0).toInstant(zone).toEpochMilli()
    private fun weeksAgo(w: Int) = now - TimeUnit.DAYS.toMillis(7L * w)

    @Test
    fun `slowdown km detects first slow split after baseline`() {
        assertEquals(2f, RunPatternAnalyzer.slowdownKm(listOf(400, 400, 440, 450)))
        assertNull(RunPatternAnalyzer.slowdownKm(listOf(400, 400, 410)))
        assertNull(RunPatternAnalyzer.slowdownKm(listOf(400, 400)))
    }

    @Test
    fun `slowdown pattern counts repeats near same point`() {
        val p = RunPatternAnalyzer.slowdownPattern(listOf(3f, null, 2.5f, 3f))
        assertEquals(SlowdownPattern(3f, 2), p)
    }

    @Test
    fun `pacing style from split halves`() {
        assertEquals(PacingStyle.POSITIVE, RunPatternAnalyzer.pacingStyle(RunPatternAnalyzer.secondHalfChange(listOf(400, 400, 430, 440))!!))
        assertEquals(PacingStyle.NEGATIVE, RunPatternAnalyzer.pacingStyle(RunPatternAnalyzer.secondHalfChange(listOf(410, 405, 400, 395))!!))
        assertEquals(PacingStyle.EVEN, RunPatternAnalyzer.pacingStyle(RunPatternAnalyzer.secondHalfChange(listOf(400, 402, 401, 403))!!))
    }

    @Test
    fun `repeated slowdown becomes conversation topic`() {
        val splits = listOf(400, 400, 400, 450, 460)
        val runs = (0..2).map { PatternRun(weeksAgo(it), 5f, 2100, 140, splits) }
        val r = ReflectionCoach.reflect(RunPatternAnalyzer.profile(runs, now = now, zone = zone))
        assertEquals("slowdown_repeat", r.topic.id)
        assertTrue(r.topic.observation.contains("3km"))
        assertTrue(r.topic.askOthers.isNotBlank())
    }

    @Test
    fun `hard intensity compared with 80 20 rule`() {
        val runs = (0..3).map { PatternRun(weeksAgo(it), 5f, 1800, 160) }
        val r = ReflectionCoach.reflect(RunPatternAnalyzer.profile(runs, now = now, zone = zone))
        assertEquals("too_hard", r.topic.id)
        val intensity = r.comparisons.first { it.topic == "강도 분배" }
        assertTrue(intensity.worthThinking)
        assertTrue(intensity.me.contains("0%"))
    }

    @Test
    fun `regular sunday runner gets ritual topic`() {
        val runs = (0..3).map { PatternRun(weeksAgo(it), 5f, 2400, 135, listOf(400, 400, 398, 396)) }
        val p = RunPatternAnalyzer.profile(runs, now = now, zone = zone)
        assertEquals(DayOfWeek.SUNDAY, p.favoriteDay)
        assertEquals(4, p.activeWeeks)
        val r = ReflectionCoach.reflect(p)
        assertEquals("ritual_day", r.topic.id)
        assertTrue(r.comparisons.none { it.worthThinking })
    }

    @Test
    fun `no data gives neutral topic and no comparisons`() {
        val r = ReflectionCoach.reflect(RunPatternAnalyzer.profile(emptyList(), now = now, zone = zone))
        assertEquals("why_run", r.topic.id)
        assertTrue(r.comparisons.isEmpty())
    }
}
