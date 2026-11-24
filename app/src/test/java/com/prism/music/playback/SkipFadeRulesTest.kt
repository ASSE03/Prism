package com.prism.music.playback

import com.prism.music.data.prefs.CrossfadeCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 1.8.8 "Blend when I skip" (plan-mix §2.3.6 and the A16 trial's §2.3a): every rule of [SkipFadeRules] on a fake clock,
 * against two simulated players that play on with it. The main player takes [Sim.restartMs] to start again after a seek
 * (the A16 took 0.24 to 1 s); the head starts [Sim.headReadyAt] after the press.
 */
class SkipFadeRulesTest {
    /** Two players on one clock: positions in song ms, moving at the listener's [speed] (the main one at its nudged share). */
    private class Sim(
        val speed: Float = 1f,
        /** When the head starts playing (Long.MAX_VALUE: never). */
        val headReadyAt: Long = 200,
        val restartMs: Long = 350,
        /** Nudges change the main player's speed (false: a player that ignores them, so it never comes in step). */
        val nudgesWork: Boolean = true,
    ) {
        var now = 0L
        var mainIndex = 0
        var mainPos = 60_000.0
        var mainWants = true
        var mainRestartUntil = 0L
        var mainShare = 1f
        var mainVolume = 1f
        var headAlive = true
        var headPos = 0.0
        var headWants = true
        var headFailed = false
        var headVolume = 0f
        val mainVolumes = ArrayList<Float>()
        val headVolumes = ArrayList<Float>()
        var moves = 0
        var seeks = 0
        var plains = 0
        var thens = 0
        var lastEnd: SkipEnd? = null

        fun mainPlaying() = mainWants && now >= mainRestartUntil
        fun headPlaying() = headAlive && !headFailed && headWants && now >= headReadyAt

        fun read() = SkipNow(
            now, mainPlaying(), mainWants, mainIndex, mainPos.toLong(),
            headPlaying = headPlaying(), headPos = headPos.toLong(), headFailed = !headAlive || headFailed, speed = speed,
        )

        fun apply(s: SkipStep) {
            s.headPlay?.let { headWants = it }
            s.moveMs?.let { mainIndex = s.target; mainPos = it.toDouble(); mainRestartUntil = now + restartMs; moves++ }
            s.seekMs?.let { mainPos = it.toDouble(); mainRestartUntil = now + restartMs; seeks++ }
            if (s.plain) { mainIndex = s.target; mainPos = 0.0; mainRestartUntil = now + restartMs; plains++ }
            if (s.then) thens++
            s.nudge?.let { if (nudgesWork) mainShare = it }
            s.headVolume?.let { headVolume = it; headVolumes += it }
            s.mainVolume?.let { mainVolume = it; mainVolumes += it }
            s.end?.let { headAlive = false; lastEnd = it }
        }

        fun advance(ms: Long) {
            repeat(ms.toInt()) {
                if (mainPlaying()) mainPos += speed * mainShare
                if (headPlaying()) headPos += speed.toDouble()
                now++
            }
        }
    }

    private fun press(sim: Sim, warm: Boolean = true, curve: CrossfadeCurve = CrossfadeCurve.EQUAL_POWER): SkipFadeRules =
        SkipFadeRules { curve }.also { it.start(sim.now, target = 1, warm = warm) }

    /** Runs the rules against [sim] until the run is over (or [maxMs] of clock), with [each] before each step. */
    private fun run(rules: SkipFadeRules, sim: Sim, maxMs: Long = 12_000, each: (SkipFadeRules) -> Unit = {}): List<SkipStep> {
        val steps = ArrayList<SkipStep>()
        while (rules.active && sim.now < maxMs) {
            each(rules)
            if (!rules.active) break
            val s = rules.step(sim.read())
            sim.apply(s)
            steps += s
            sim.advance(s.waitMs.coerceIn(1, 20))
        }
        return steps
    }

    /** Back to normal: run over, main at full volume and the listener's speed, head gone. */
    private fun assertNormal(rules: SkipFadeRules, sim: Sim) {
        assertFalse("still under way (${rules.phase})", rules.active)
        assertEquals("main volume", 1f, sim.mainVolume)
        assertEquals("main speed share", 1f, sim.mainShare)
        assertFalse("head left alive", sim.headAlive)
        assertNotNull("how it ended", sim.lastEnd)
        for (v in sim.mainVolumes + sim.headVolumes) assertTrue("volume $v", v in 0f..1f)
    }

    // --- The run as it should go ---

    @Test fun aSkipFadesInTheChosenSongThenMovesTheMainPlayerAndHandsOverInStep() {
        val sim = Sim()
        val rules = press(sim)
        assertTrue(rules.pending)
        run(rules, sim)
        assertNormal(rules, sim)
        val e = sim.lastEnd!!
        assertTrue("clean: ${e.how}", e.clean)
        assertEquals("in step", e.how)
        assertEquals(1, sim.moves)
        assertEquals("no corrective seeks", 0, sim.seeks)
        assertEquals("on the chosen song", 1, sim.mainIndex)
        assertTrue("handed over within ${SkipFadeRules.IN_STEP_MS} ms (${e.offsetMs})", abs(e.offsetMs!!) <= SkipFadeRules.IN_STEP_MS)
        // Heard as soon as the head plays; the main player moves at the end of the 1.5 s fade.
        assertEquals(200L, e.heardMs)
        assertTrue("moved at ${e.movedMs}", e.movedMs!! in 1_700L..1_760L)
        assertTrue("all done in ${e.totalMs} ms", e.totalMs < 4_000)
    }

    @Test fun theFadeFollowsTheListenersCurveTheOldSongOnlyGoesDownAndTheNewOneOnlyUp() {
        for (curve in CrossfadeCurve.entries) {
            val sim = Sim()
            val rules = press(sim, curve = curve)
            val fading = ArrayList<Triple<Float, Float, Float>>()
            run(rules, sim) { r -> if (r.phase == SkipFadeRules.Phase.FADING) fading += Triple(r.progress, sim.headVolume, sim.mainVolume) }
            assertTrue(fading.size > 50)
            // Each step's volumes are the curve's at that step's progress.
            for ((p, i, o) in fading) {
                val (wantIn, wantOut) = curve.gains(p)
                assertEquals("$curve at $p: in", wantIn, i, 1e-4f)
                assertEquals("$curve at $p: out", wantOut, o, 1e-4f)
            }
            val ins = fading.map { it.second }
            val outs = fading.map { it.third }
            assertEquals("$curve: the new song only comes up", ins, ins.sorted())
            assertEquals("$curve: the old song only goes down", outs, outs.sortedDescending())
            assertTrue("$curve: it reached the end of the fade", fading.last().first > 0.97f)
        }
        // Straight: halfway the two are at half each.
        val sim = Sim()
        val rules = press(sim, curve = CrossfadeCurve.LINEAR)
        var mid: Pair<Float, Float>? = null
        run(rules, sim) { r -> if (mid == null && r.phase == SkipFadeRules.Phase.FADING && r.progress >= 0.5f) mid = sim.headVolume to sim.mainVolume }
        assertEquals(0.5f, mid!!.first, 0.02f)
        assertEquals(0.5f, mid!!.second, 0.02f)
    }

    @Test fun theMoveSendsTheMainPlayerAheadByTheLearnedLeadAtTheListenersSpeed() {
        val sim = Sim(speed = 1.25f)
        val rules = press(sim)
        var headAtMove = -1L
        var moveTo = -1L
        while (rules.active && sim.now < 12_000) {
            val r = sim.read()
            val s = rules.step(r)
            if (s.moveMs != null) { headAtMove = r.headPos; moveTo = s.moveMs!! }
            sim.apply(s)
            sim.advance(s.waitMs.coerceIn(1, 20))
        }
        assertEquals(headAtMove + (SkipFadeRules.START_LEAD_WARM_MS * 1.25f).toLong(), moveTo)
        assertNormal(rules, sim)
    }

    @Test fun aColdSongIsCaughtUpBySpeedNotBySeeksAndItsLeadIsLearnedApartFromWarmOnes() {
        val sim = Sim(restartMs = 800)
        val rules = press(sim, warm = false)
        val nudges = ArrayList<Float>()
        val steps = run(rules, sim)
        steps.mapNotNullTo(nudges) { it.nudge?.takeIf { n -> n != 1f } }
        assertNormal(rules, sim)
        assertEquals("in step", sim.lastEnd!!.how)
        assertEquals("no corrective seeks", 0, sim.seeks)
        assertTrue("caught up by speed ($nudges)", nudges.isNotEmpty())
        // The main player was behind: played faster, by at least 5 %, at most 20 %.
        for (n in nudges) assertTrue("nudge $n", n in 1.05f..1.20f)
        // What it fell behind by goes into the cold lead only.
        assertTrue("cold lead ${rules.leadColdMs}", rules.leadColdMs > SkipFadeRules.START_LEAD_COLD_MS)
        assertEquals(SkipFadeRules.START_LEAD_WARM_MS, rules.leadWarmMs)
        assertTrue("all normal well before 8 s (${sim.lastEnd!!.totalMs})", sim.lastEnd!!.totalMs < SkipFadeRules.LIMIT_MS)
        // The next cold skip starts closer.
        val again = Sim(restartMs = 800)
        rules.start(again.now, 1, warm = false)
        run(rules, again)
        assertTrue("second cold skip ${again.lastEnd}", again.lastEnd!!.totalMs <= sim.lastEnd!!.totalMs)
    }

    @Test fun nudgesAreSizedToTheGapAndTimedAtTheListenersSpeed() {
        assertEquals(0.05f, SkipFadeRules.nudgeRate(20), 1e-6f)
        assertEquals(0.10f, SkipFadeRules.nudgeRate(-250), 1e-6f)
        assertEquals(0.20f, SkipFadeRules.nudgeRate(1_400), 1e-6f)
        // 100 ms closed at 5 % of 1× in 2 s; at 1.25× in 1.6 s.
        assertEquals(2_000L, SkipFadeRules.nudgeMs(100, 0.05f, 1f))
        assertEquals(1_600L, SkipFadeRules.nudgeMs(-100, 0.05f, 1.25f))
    }

    @Test fun farOffItSeeksOnceThenNudges() {
        // The restart takes far longer than the lead: 2 s behind after the move.
        val sim = Sim(restartMs = 2_350)
        val rules = press(sim)
        run(rules, sim)
        assertEquals(1, sim.seeks)
        assertNormal(rules, sim)
    }

    @Test fun notInStepAfterTwoTriesItHandsOverSlowlyWithinAQuarterSecondElseWithADip() {
        // Nudges that don't take: 150 ms apart for good.
        val near = Sim(restartMs = 500, nudgesWork = false)
        val r1 = press(near)
        run(r1, near)
        assertNormal(r1, near)
        assertTrue(near.lastEnd!!.how, near.lastEnd!!.how.startsWith("slow hand-over"))
        assertFalse(near.lastEnd!!.clean)
        // 600 ms apart (under the seek's 1.5 s): a dip.
        val far = Sim(restartMs = 950, nudgesWork = false)
        val r2 = press(far)
        run(r2, far)
        assertNormal(r2, far)
        assertTrue(far.lastEnd!!.how, far.lastEnd!!.how.startsWith("dip"))
    }

    // --- Rules that keep it safe ---

    @Test fun theChosenSongNotPlayingIn4sIsAPlainSkip() {
        val sim = Sim(headReadyAt = Long.MAX_VALUE)
        val rules = press(sim)
        run(rules, sim)
        assertNormal(rules, sim)
        assertEquals(1, sim.plains)
        assertEquals(0, sim.moves)
        assertTrue(sim.lastEnd!!.how, sim.lastEnd!!.how.contains("4 s"))
        assertEquals(SkipFadeRules.LOAD_LIMIT_MS, sim.lastEnd!!.totalMs, 20.0)
        assertNull(sim.lastEnd!!.heardMs)
    }

    private fun assertEquals(expected: Long, actual: Long, slack: Double) =
        assertTrue("$actual, not $expected", abs(actual - expected) <= slack)

    @Test fun theChosenSongFailingIsAPlainSkipBeforeOrDuringTheFade() {
        for (failAt in listOf(100L, 900L)) {
            val sim = Sim()
            val rules = press(sim)
            run(rules, sim) { if (sim.now >= failAt) sim.headFailed = true }
            assertNormal(rules, sim)
            assertEquals("fails at $failAt", 1, sim.plains)
            assertEquals(1, sim.mainIndex)
        }
    }

    @Test fun pausedTheFadeHoldsAndBothResumeTogether() {
        val sim = Sim()
        val rules = press(sim)
        var heldAt = -1f
        run(rules, sim) { r ->
            if (r.phase == SkipFadeRules.Phase.FADING && r.progress >= 0.4f && sim.now < 5_000 && heldAt < 0) { sim.mainWants = false; heldAt = r.progress }
            if (heldAt >= 0 && sim.now in 3_000..3_010) {
                // A while later, still where it was: the head paused with the main player, the fade not moved on.
                assertFalse("head paused", sim.headWants)
                assertEquals(heldAt, r.progress, 0.02f)
                assertEquals(SkipFadeRules.Phase.FADING, r.phase)
            }
            if (sim.now >= 3_100 && !sim.mainWants) sim.mainWants = true
        }
        assertNormal(rules, sim)
        assertTrue(sim.lastEnd!!.how, sim.lastEnd!!.clean)
    }

    @Test fun aSecondSkipEndsTheRunAtOnceWithNothingStacked() {
        for (at in listOf(100L, 1_000L, 2_200L, 3_000L)) {
            val sim = Sim()
            val rules = press(sim)
            run(rules, sim) { r -> if (sim.now >= at && r.active) sim.apply(r.cut(sim.read())) }
            assertNormal(rules, sim)
            assertTrue(sim.lastEnd!!.how, sim.lastEnd!!.how.startsWith("a second skip"))
        }
    }

    @Test fun aSeekByTheListenerFinishesItAtOnceOnTheNewSong() {
        // In the fade: the main player to the chosen song where the head is, at full volume.
        val sim = Sim()
        val rules = press(sim)
        var headAt = -1L
        run(rules, sim) { r ->
            if (r.phase == SkipFadeRules.Phase.FADING && r.progress > 0.3f) {
                val now = sim.read(); headAt = now.headPos
                val s = r.seeked(now)
                assertEquals(headAt, s.moveMs)
                sim.apply(s)
            }
        }
        assertNormal(rules, sim)
        assertEquals(1, sim.mainIndex)
        // Catching up: the main player, already there, comes up at once.
        val sim2 = Sim(restartMs = 800)
        val r2 = press(sim2, warm = false)
        run(r2, sim2) { r -> if (r.phase == SkipFadeRules.Phase.CATCHING && sim2.now > 2_600) sim2.apply(r.seeked(sim2.read())) }
        assertNormal(r2, sim2)
        assertEquals(1, sim2.moves)
    }

    @Test fun eightSecondsAfterThePressAllIsNormalFromEveryState() {
        // Held (paused) in each state for good: whatever it is, by the limit the main player is on the chosen song, at full
        // volume and its own speed, and the head is gone.
        for (phase in listOf(SkipFadeRules.Phase.LOADING, SkipFadeRules.Phase.FADING, SkipFadeRules.Phase.CATCHING)) {
            val sim = Sim(restartMs = 800)
            val rules = press(sim, warm = false)
            run(rules, sim) { r -> if (r.phase == phase) sim.mainWants = false }
            assertNormal(rules, sim)
            assertTrue("$phase: over by ${sim.lastEnd!!.totalMs} ms", sim.lastEnd!!.totalMs <= SkipFadeRules.LIMIT_MS + 20)
            assertEquals("$phase: on the chosen song", 1, sim.mainIndex)
        }
        // Never in step (nudges that don't take, a gap the seek can't close): handed over before the limit.
        val stubborn = Sim(restartMs = 900, nudgesWork = false)
        val r = press(stubborn)
        run(r, stubborn)
        assertNormal(r, stubborn)
        assertTrue(stubborn.lastEnd!!.totalMs <= SkipFadeRules.LIMIT_MS)
        // Mid-nudge at the limit: the speed goes back too.
        val slow = Sim(restartMs = 2_000, nudgesWork = true)
        val r3 = press(slow)
        run(r3, slow) { if (slow.now > 3_000 && slow.mainShare != 1f) slow.mainWants = false }
        assertNormal(r3, slow)
        // Each phase, ended at the limit from outside the steps (finishNow), comes to the same.
        for (phase in SkipFadeRules.Phase.entries.filter { it != SkipFadeRules.Phase.IDLE }) {
            val sim = Sim(restartMs = 800)
            val rules = if (phase == SkipFadeRules.Phase.FADE_OUT) SkipFadeRules().also { it.fadeOut(0, 1) } else press(sim, warm = false)
            run(rules, sim) { x -> if (x.phase == phase) { sim.mainWants = false; sim.now = maxOf(sim.now, SkipFadeRules.LIMIT_MS) } }
            assertFalse("$phase", rules.active)
            assertEquals("$phase volume", 1f, sim.mainVolume)
            assertEquals("$phase speed", 1f, sim.mainShare)
            assertFalse("$phase head", sim.headAlive)
        }
    }

    @Test fun theShortFadeOutGoesDownOver200MsThenTheCut() {
        val sim = Sim()
        sim.headAlive = false
        val rules = SkipFadeRules()
        rules.fadeOut(sim.now, target = 1)
        assertTrue(rules.pending)
        run(rules, sim)
        assertEquals(1, sim.thens)
        assertEquals(1f, sim.mainVolume)
        assertFalse(rules.active)
        val down = sim.mainVolumes.dropLast(1)
        assertTrue(down.isNotEmpty())
        assertEquals(down, down.sortedDescending())
        assertTrue(sim.lastEnd!!.totalMs in SkipFadeRules.FADE_OUT_MS..SkipFadeRules.FADE_OUT_MS + 20)
        // A new queue's (no place in this one): nothing pending to count Next from.
        val q = SkipFadeRules().apply { fadeOut(0) }
        assertFalse(q.pending)
        assertTrue(q.active)
    }

    @Test fun pendingOnlyUntilTheMainPlayerMoves() {
        val sim = Sim()
        val rules = press(sim)
        val seen = LinkedHashMap<SkipFadeRules.Phase, Boolean>()
        run(rules, sim) { r -> seen[r.phase] = r.pending }
        assertEquals(true, seen[SkipFadeRules.Phase.LOADING])
        assertEquals(true, seen[SkipFadeRules.Phase.FADING])
        assertEquals(false, seen[SkipFadeRules.Phase.CATCHING])
        assertEquals(false, seen[SkipFadeRules.Phase.HANDOVER])
    }

    // --- What a skip does ---

    @Test fun whatASkipDoesInOrder() {
        val ok = SkipAsk(on = true)
        assertEquals(SkipChoice.BLEND, SkipFadeRules.choose(ok))
        // Off: today's skip, whatever else.
        assertEquals(SkipChoice.PLAIN, SkipFadeRules.choose(ok.copy(on = false)))
        assertEquals(SkipChoice.PLAIN, SkipFadeRules.choose(ok.copy(on = false, light = true, mixHeard = true)))
        // One under way: cut, never stacked (even with the switch just turned off).
        assertEquals(SkipChoice.CUT, SkipFadeRules.choose(ok.copy(underWay = true)))
        assertEquals(SkipChoice.CUT, SkipFadeRules.choose(ok.copy(underWay = true, on = false)))
        // An end-of-song blend heard: stopped, then a plain cut (not blended out of).
        assertEquals(SkipChoice.STOP_MIX_THEN_PLAIN, SkipFadeRules.choose(ok.copy(mixHeard = true)))
        assertEquals(SkipChoice.STOP_MIX_THEN_PLAIN, SkipFadeRules.choose(ok.copy(mixHeard = true, light = true)))
        // Paused, the same song, or not blended here (casting, Listen together, Sing, video, live, podcasts): plain.
        assertEquals(SkipChoice.PLAIN, SkipFadeRules.choose(ok.copy(playing = false)))
        assertEquals(SkipChoice.PLAIN, SkipFadeRules.choose(ok.copy(sameSong = true)))
        assertEquals(SkipChoice.PLAIN, SkipFadeRules.choose(ok.copy(allowed = false)))
        assertEquals(SkipChoice.PLAIN, SkipFadeRules.choose(ok.copy(allowed = false, light = true)))
        // Performance mode or battery saver: the short fade-out and the cut, no second player.
        assertEquals(SkipChoice.FADE_OUT, SkipFadeRules.choose(ok.copy(light = true)))
    }

    @Test fun theDiagnosticsLineSaysPressToHeardTheGapAndHowItEnded() {
        val e = SkipEnd("in step", true, heardMs = 302, movedMs = 1_620, offsetMs = 3, totalMs = 4_400)
        val line = SkipFade.skipLine("a", "b", SkipKind.NEXT, 350, 700, e, 310)
        assertTrue(line, line.startsWith("skip-blend a -> b (next): heard after 310 ms, moved at 1620 ms, 3 ms apart at the hand-over, ok: in step"))
        assertTrue(line, line.contains("all normal after 4400 ms"))
        val failed = SkipFade.skipLine("a", "b", SkipKind.JUMP, 350, 700, SkipEnd("not ready", false, null, null, null, 4_000), null)
        assertTrue(failed, failed.contains("heard after -") && failed.contains("not measured") && failed.contains("not clean"))
    }
}
