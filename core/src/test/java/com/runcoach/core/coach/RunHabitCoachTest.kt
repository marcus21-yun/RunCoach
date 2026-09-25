package com.runcoach.core.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunHabitCoachTest {

    /** 1초 간격 샘플을 흘려보내고 나온 안내를 (초, 종류)로 모은다. */
    private fun simulate(
        coach: RunHabitCoach,
        seconds: IntRange,
        sample: (Int) -> HabitSample
    ): List<Pair<Int, CueType>> = seconds.mapNotNull { t -> coach.onSample(sample(t))?.let { t to it.type } }

    private fun steady(t: Int, pace: Int = 400, bpm: Int = 130) =
        HabitSample(t, t / pace.toFloat(), pace, bpm)

    @Test
    fun `warmup cue once at start`() {
        val cues = simulate(RunHabitCoach(), 0..60) { steady(it) }
        assertEquals(listOf(5 to CueType.WARMUP), cues)
    }

    @Test
    fun `breathing guidance when heart rate approaches 150`() {
        val coach = RunHabitCoach()
        simulate(coach, 0..100) { steady(it) }
        val cues = simulate(coach, 101..200) { steady(it, bpm = 146) }
        assertEquals(CueType.BREATHING, cues.first().second)
        assertEquals(101, cues.first().first)
        assertTrue(cues.any { it.second == CueType.BREATHING_REPEAT && it.first == 161 })
    }

    @Test
    fun `never pushes speed while breathing hard`() {
        val coach = RunHabitCoach(targetPaceSec = 400)
        simulate(coach, 0..300) { steady(it) }
        // 심박 147 + 페이스 크게 느려짐
        val cues = simulate(coach, 301..600) { steady(it, pace = 480, bpm = 147) }
        assertFalse(cues.any { it.second == CueType.PACE_DROP })
    }

    @Test
    fun `pace drop nag when comfortable but slowing`() {
        val coach = RunHabitCoach(targetPaceSec = 400)
        simulate(coach, 0..300) { steady(it) }
        val cues = simulate(coach, 301..420) { steady(it, pace = 450) }
        assertTrue(cues.any { it.second == CueType.PACE_DROP })
    }

    @Test
    fun `safety cue overrides everything`() {
        val coach = RunHabitCoach()
        coach.onSample(steady(5)) // warmup
        val cue = coach.onSample(steady(6, bpm = 170))
        assertEquals(CueType.SAFETY_SLOW, cue?.type)
        assertEquals(170, cue?.bpm)
    }

    @Test
    fun `warns before the usual slowdown point`() {
        val coach = RunHabitCoach(pattern = SlowdownPattern(km = 2f, repeatCount = 2))
        val cues = simulate(coach, 0..900) { steady(it) } // 400초/km → 1.8km는 720초
        val hit = cues.first { it.second == CueType.PATTERN_AHEAD }
        assertEquals(720, hit.first)
    }

    @Test
    fun `posture nag after long silence`() {
        val cues = simulate(RunHabitCoach(), 0..600) { steady(it) }
        assertTrue(cues.any { it.second == CueType.POSTURE && it.first == 485 })
    }

    @Test
    fun `min gap between cues`() {
        val coach = RunHabitCoach()
        coach.onSample(steady(5))
        assertNull(coach.onSample(steady(10, bpm = 146)))
        assertEquals(CueType.BREATHING, coach.onSample(steady(25, bpm = 146))?.type)
    }

    @Test
    fun `mom and dad tones differ and mention numbers`() {
        val cue = HabitCue(CueType.PATTERN_AHEAD, km = 3f, repeatCount = 3)
        val mom = HabitPhrasebook.text(cue, CoachTone.MOM)
        val dad = HabitPhrasebook.text(cue, CoachTone.DAD)
        assertTrue(mom.contains("3킬로"))
        assertTrue(dad.contains("3번째"))
        assertTrue(mom != dad)
        CueType.entries.forEach { type ->
            CoachTone.entries.forEach { tone ->
                assertTrue(HabitPhrasebook.text(HabitCue(type, bpm = 150), tone).isNotBlank())
            }
        }
    }
}
