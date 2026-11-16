package com.prism.music

import android.content.ContextWrapper
import android.content.Intent
import com.prism.music.data.prefs.EqSettings
import com.prism.music.playback.AudioEffects
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * #519: Prism's platform effects (equalizer, bass boost, loudness) are asked of Android only once the session has a
 * track on it, and only when the settings ask for them. Made on a session with nothing playing yet, as they were when
 * the service started, Android kept three of them in its register for good whenever Prism's process ended before
 * anything played (measured on the A16, `EqEffect519AppTest`), until the register was full and every effect was refused.
 *
 * Plain JUnit: Android's effect classes are the build's stand-ins here (they make nothing), so what's checked is what
 * Prism asks for and when, through [AudioEffects.trace].
 */
class AudioEffectsTrackTest {
    private val executor = Executors.newSingleThreadExecutor()
    private val events = Collections.synchronizedList(mutableListOf<String>())
    private val context = object : ContextWrapper(null) {
        override fun sendBroadcast(intent: Intent?) {}
        override fun getPackageName() = "com.prism.music.test"
    }

    private fun effects(announce: Boolean = true) = AudioEffects(context, executor, announce).also { e -> e.trace = { events += it } }

    /** Waits until the effects thread has done everything handed to it so far. */
    private fun settle() { executor.submit {}.get(5, TimeUnit.SECONDS) }

    private fun made() = events.filter { it.startsWith("make") }

    @After fun stop() { executor.shutdownNow() }

    @Test fun nothingIsMadeOnASessionWithNoTrack() {
        val e = effects()
        e.attach(42)
        e.apply(EqSettings(enabled = true, bands = List(10) { 3f }, bassBoost = 500, loudnessGainMb = 300, preampDb = -2f))
        settle()
        assertEquals("asked of Android before any track: $events", emptyList<String>(), made())
        assertTrue("other engines told about a session with nothing on it: $events", events.none { it.startsWith("open") })
    }

    @Test fun theEffectsAreMadeOnceTheTrackIs() {
        val e = effects()
        e.attach(42)
        e.apply(EqSettings(enabled = true, bassBoost = 500))
        e.trackReady()
        settle()
        assertEquals("open 42", events.first())
        // The equalizer (DynamicsProcessing, or the platform Equalizer on the build's stand-in SDK level), bass, loudness;
        // no platform virtualizer any more (it was only ever made to be switched off).
        val kinds = made().map { it.split(' ')[1] }
        assertTrue("no equalizer made: $events", kinds.any { it == "dynamics" || it == "equalizer" })
        assertTrue("no bass boost made: $events", "bass" in kinds)
        assertTrue("no loudness made: $events", "loudness" in kinds)
        assertTrue("a virtualizer made: $events", "virtualizer" !in kinds)
        assertTrue("made on another session: $events", made().all { it.endsWith(" 42") })
        // Further changes use the ones made: nothing new is asked for.
        val before = made().size
        repeat(5) { i -> e.apply(EqSettings(enabled = true, preampDb = -i.toFloat())) }
        e.trackReady()
        settle()
        assertEquals("made again: $events", before, made().size)
    }

    @Test fun withTheEqualizerOffNoEffectIsMade() {
        val e = effects()
        e.attach(42)
        e.apply(EqSettings(enabled = false))
        e.trackReady()
        settle()
        assertEquals("asked of Android with the equalizer off: $events", emptyList<String>(), made())
        // Switched on later, while playing: made then.
        e.apply(EqSettings(enabled = true))
        settle()
        assertTrue("not made when switched on: $events", made().isNotEmpty())
    }

    @Test fun aTrackReportedBeforeTheSessionCountsForIt() {
        // Media3 hands the session to listeners a moment after it's made; the track can come first.
        val e = effects()
        e.apply(EqSettings(enabled = true))
        e.trackReady()
        settle()
        assertEquals(emptyList<String>(), made())
        e.attach(42)
        settle()
        assertTrue("not made on the session once known: $events", made().isNotEmpty() && made().all { it.endsWith(" 42") })
    }

    @Test fun aNewSessionWaitsForItsOwnTrack() {
        val e = effects()
        e.apply(EqSettings(enabled = true))
        e.attach(42)
        e.trackReady()
        settle()
        events.clear()
        e.attach(43)
        e.apply(EqSettings(enabled = true, preampDb = -3f))
        settle()
        assertEquals("the old session not closed: $events", listOf("close 42"), events.toList())
        e.trackReady()
        settle()
        assertTrue("not made on the new session: $events", made().isNotEmpty() && made().all { it.endsWith(" 43") })
    }

    @Test fun crossfadesTailIsNotAnnounced() {
        val e = effects(announce = false)
        e.apply(EqSettings(enabled = true))
        e.attach(7)
        e.trackReady()
        e.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        assertTrue("the tail's session announced: $events", events.none { it.startsWith("open") || it.startsWith("close") })
        assertTrue(made().isNotEmpty())
    }
}
