package com.prism.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #547: the player's mesh jumped when a sheet opened over it (snapped back to rest) and again when it closed (its loop
 * started again from the top). Held, it stays where it is, and it goes on from there.
 */
class MeshDriftHoldTest {
    private val ms = 1_000_000L
    private val loop = 16_000

    /** Runs frames 16 ms apart from [fromMs] for [forMs]; the time of the last frame. */
    private fun DriftPhase.run(fromMs: Long, forMs: Long): Long {
        var t = fromMs
        while (t <= fromMs + forMs) { advance(t * ms, loop); t += 16 }
        return t - 16
    }

    @Test fun itMovesWhileLetGo() {
        val d = DriftPhase()
        d.run(0, 4_000)
        // A quarter of a 16 s loop.
        assertEquals((Math.PI / 2).toFloat(), d.value, 0.01f)
    }

    @Test fun coveringKeepsItWhereItIs() {
        val d = DriftPhase()
        d.run(0, 3_000)
        val before = d.value
        assertNotEquals(0f, before)
        d.hold()
        assertEquals("not back at rest when covered", before, d.value, 0f)
    }

    @Test fun uncoveringGoesOnFromTheSamePlace() {
        val d = DriftPhase()
        val last = d.run(0, 3_000)
        val held = d.value
        d.hold()
        // Ten seconds under a sheet: none of it counts.
        val back = last + 10_000
        d.advance(back * ms, loop)
        assertEquals("the first frame after the sheet is where it was held", held, d.value, 0f)
        d.advance((back + 16) * ms, loop)
        val step = d.value - held
        assertTrue("then one frame's move, not a jump: $step", step > 0f && step < 0.01f)
    }

    @Test fun itWrapsRoundTheLoop() {
        val d = DriftPhase()
        d.run(0, 20_000)
        assertTrue(d.value >= 0f && d.value < (2 * Math.PI).toFloat())
        assertEquals((Math.PI / 2).toFloat(), d.value, 0.01f)
    }
}
