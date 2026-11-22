package com.prism.music

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.prism.music.data.prefs.CrossfadeCurve
import com.prism.music.playback.MixEngine
import com.prism.music.playback.MixMoves
import com.prism.music.playback.MixState
import com.prism.music.playback.Transition
import com.prism.music.playback.TransitionSound
import com.prism.music.playback.TransitionStyle
import com.prism.music.playback.Transitions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.lang.reflect.Proxy
import java.time.Duration
import kotlin.math.abs

/**
 * 1.8.8 Mix it yourself (plan-mix.md §4.4, C2): the crossfade engine plays the listener's own transition for a pair of a
 * playlist's songs before anything automatic, with Crossfade off too, but never where blending isn't allowed; the
 * other way round is another pair; the caps that protect every blend still hold. Against fake players that play on
 * with the clock, as [MixBlendTest]'s.
 */
@RunWith(RobolectricTestRunner::class)
class MixPlanTest {
    private val context = ApplicationProvider.getApplicationContext<PrismApp>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val state = MixState()
    private val prefs = context.getSharedPreferences("mix_tempo", Context.MODE_PRIVATE)

    private fun item(id: String, ms: Long = 200_000L) = MediaItem.Builder().setMediaId(id).setUri("https://example.invalid/$id")
        .setMediaMetadata(MediaMetadata.Builder().setDurationMs(ms).build()).build()

    private fun now() = SystemClock.elapsedRealtime()

    private data class Seek(val fromIndex: Int, val fromMs: Long, val toIndex: Int, val toMs: Long)

    /** A player that plays on with the clock (as [MixBlendTest]'s). */
    private inner class Fake(val label: String, startState: Int) {
        var items = listOf<MediaItem>()
        var index = 0
        private var base = 0L
        private var since = now()
        var playWhenReady = true
            set(v) { hold(); field = v }
        var state = startState
            set(v) { hold(); field = v }
        var speed = 1f
            set(v) { hold(); field = v }
        var volume = 1f
        var released = false
        val volumes = ArrayList<Pair<Long, Float>>()
        val seeks = ArrayList<Seek>()

        fun duration(): Long = items.getOrNull(index)?.mediaMetadata?.durationMs ?: C.TIME_UNSET
        private fun running() = playWhenReady && state == Player.STATE_READY && !released
        fun position(): Long {
            val p = if (running()) base + ((now() - since) * speed).toLong() else base
            val d = duration()
            return if (d == C.TIME_UNSET) p else minOf(p, d)
        }
        private fun hold() { base = position(); since = now() }
        private fun ended() = state == Player.STATE_READY && duration() != C.TIME_UNSET && position() >= duration()
        fun playbackState() = when { released -> Player.STATE_IDLE; ended() -> Player.STATE_ENDED; else -> state }
        fun isPlaying() = running() && !ended()
        fun moveTo(i: Int, ms: Long) { seeks += Seek(index, position(), i, ms); index = i; base = ms; since = now() }
        val mediaId: String? get() = items.getOrNull(index)?.mediaId

        val player: ExoPlayer = Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java)) { self, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(self)
                "equals" -> self === args?.get(0)
                "toString" -> label
                "isPlaying" -> isPlaying()
                "getPlayWhenReady" -> playWhenReady
                "setPlayWhenReady" -> { playWhenReady = args!![0] as Boolean; null }
                "getPlaybackState" -> playbackState()
                "getCurrentMediaItem" -> items.getOrNull(index)
                "getCurrentMediaItemIndex" -> index
                "getMediaItemCount" -> items.size
                "getDuration" -> duration()
                "getCurrentPosition" -> position()
                "getNextMediaItemIndex" -> if (index + 1 < items.size) index + 1 else C.INDEX_UNSET
                "getMediaItemAt" -> items[args!![0] as Int]
                "getVolume" -> volume
                "setVolume" -> { volume = args!![0] as Float; volumes += now() to volume; null }
                "getPlaybackParameters" -> PlaybackParameters(speed)
                "setPlaybackParameters" -> { speed = (args!![0] as PlaybackParameters).speed; null }
                "getRepeatMode" -> Player.REPEAT_MODE_OFF
                "seekTo" -> {
                    if (args!!.size == 2) moveTo(args[0] as Int, args[1] as Long) else moveTo(index, args[0] as Long)
                    null
                }
                "setMediaItem" -> { items = listOf(args!![0] as MediaItem); index = 0; null }
                "prepare" -> { state = Player.STATE_READY; null }
                "release" -> { playWhenReady = false; released = true; null }
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    java.lang.Float.TYPE -> 0f
                    else -> null
                }
            }
        } as ExoPlayer
    }

    private val main = Fake("main", Player.STATE_READY)
    private val tails = ArrayList<Fake>()
    /** Crossfade off unless a test turns it on: a saved pair plays anyway. */
    private var mixOn = false
    private var allow = true
    private var playlist: String? = "PL1"
    private val saved = HashMap<Triple<String, String, String>, Transition>()
    private val played = ArrayList<Triple<String, String, String>>()
    private lateinit var engine: MixEngine

    private fun build(nextMs: Long = 200_000L) {
        main.items = listOf(item("ending"), item("next", nextMs), item("third"))
        main.moveTo(0, 150_000)
        main.seeks.clear()
        engine = MixEngine(
            main = main.player,
            scope = scope,
            tempo = context.container.mixTempo,
            mixMs = { if (mixOn) 6_000L else null },
            auto = { false },
            beatMatch = { false },
            allowed = { _, _ -> allow },
            scanFromPhone = {},
            buildTail = { Fake("tail ${tails.size}", Player.STATE_IDLE).also { tails += it }.player },
            state = state,
            curve = { CrossfadeCurve.LINEAR },
            playlist = { playlist },
            custom = { p, a, b -> saved[Triple(p, a, b)] },
            customPlayed = { p, a, b -> played += Triple(p, a, b) },
        )
        engine.start()
    }

    @Before fun clear() { prefs.edit().clear().commit() }

    @After fun stop() { if (::engine.isInitialized) engine.release(); scope.cancel() }

    private fun run(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun runUntil(what: String, maxMs: Long = 40_000, done: () -> Boolean) {
        var waited = 0L
        while (!done()) {
            assertTrue("never: $what", waited < maxMs)
            run(10); waited += 10
        }
    }

    private val handOver get() = main.seeks.firstOrNull { it.fromIndex == 0 && it.toIndex != 0 }

    @Test fun aSavedPairIsUsedWithCrossfadeOff() {
        saved[Triple("PL1", "ending", "next")] = Transition(170_000, 20_000, 8_000, TransitionStyle.STRAIGHT)
        build()
        // Its mark is up well before, at the saved spot, with its length.
        runUntil("the mark") { state.mark.value != null }
        assertEquals(170_000L, state.mark.value!!.startMs)
        assertEquals(8_000L, state.mark.value!!.lengthMs)
        runUntil("the hand-over") { handOver != null }
        val h = handOver!!
        assertEquals(1, h.toIndex)
        assertEquals("the second song comes in where it was set", 20_000L, h.toMs)
        assertTrue("handed over at ${h.fromMs} ms", h.fromMs in 169_850..170_250)
        assertEquals(listOf(Triple("PL1", "ending", "next")), played)
        // Half-way: the style's own law (Straight: one goes down as the other comes up).
        runUntil("half-way") { main.position() >= 24_000 }
        assertEquals(0.5f, main.volume, 0.08f)
        assertEquals(0.5f, tails.single().volume, 0.08f)
        runUntil("the blend is done") { tails.all { it.released } && !engine.blending }
        assertEquals("next", main.mediaId)
        assertEquals(1f, main.volume)
    }

    /** 1.8.8 review. Fails on the old code: with its plan gone at the skip, the next step took it for Mix turned off and let the ending song go at once. */
    @Test fun aSkipDuringASavedPairWithCrossfadeOffStillFades() {
        saved[Triple("PL1", "ending", "next")] = Transition(170_000, 20_000, 8_000, TransitionStyle.STRAIGHT)
        build()
        runUntil("the hand-over") { handOver != null }
        runUntil("into the blend") { main.position() >= 23_000 }
        val tail = tails.single()
        val tailWas = tail.volume
        assertTrue(tailWas in 0.2f..0.9f)
        val at = now()
        main.moveTo(2, 0)
        run(80)
        assertFalse("cut off at once", tail.released)
        assertTrue("fading: ${tail.volume}", tail.volume > 0f && tail.volume < tailWas)
        run(250)
        assertTrue(tail.released)
        val out = tail.volumes.filter { it.first >= at }.map { it.second }
        assertEquals(0f, out.last())
        assertTrue(out.size > 3)
        assertEquals("third", main.mediaId)
        assertEquals(1f, main.volume)
        assertFalse(engine.blending)
    }

    @Test fun theReversedOrderIsntTheSavedPair() {
        saved[Triple("PL1", "next", "ending")] = Transition(170_000, 20_000, 8_000)
        build()
        run(25_000)
        assertNull("no mark", state.mark.value)
        assertNull("no blend: the song plays on", handOver)
        assertTrue(tails.isEmpty())
    }

    @Test fun anotherPlaylistsPairIsntUsed() {
        saved[Triple("PL2", "ending", "next")] = Transition(170_000, 20_000, 8_000)
        build()
        run(25_000)
        assertNull(handOver)
        assertTrue(tails.isEmpty())
    }

    @Test fun neverWhereBlendingIsntAllowed() {
        saved[Triple("PL1", "ending", "next")] = Transition(170_000, 20_000, 8_000)
        allow = false
        build()
        run(25_000)
        assertNull(state.mark.value)
        assertNull(handOver)
        assertTrue(tails.isEmpty())
    }

    @Test fun theSavedPairWinsOverCrossfade() {
        mixOn = true
        saved[Triple("PL1", "ending", "next")] = Transition(165_000, 0, 10_000)
        build()
        runUntil("the hand-over") { handOver != null }
        assertTrue("at the saved spot, not Crossfade's 194 s: ${handOver!!.fromMs}", handOver!!.fromMs in 164_850..165_250)
        assertEquals(10_000L, state.mark.value?.lengthMs ?: 10_000L)
    }

    @Test fun aTooLongOverlapIsCapped() {
        // The second song is 60 s long: at most a third of it (20 s) overlaps.
        saved[Triple("PL1", "ending", "next")] = Transition(170_000, 0, 30_000)
        build(nextMs = 60_000)
        runUntil("the mark") { state.mark.value != null }
        assertEquals(20_000L, state.mark.value!!.lengthMs)
        runUntil("the hand-over") { handOver != null }
        runUntil("the blend is done") { tails.isNotEmpty() && tails.all { it.released } && !engine.blending }
        assertEquals("next", main.mediaId)
        assertTrue("it lasted about 20 s, not 30: ${main.position()}", main.position() in 19_000..23_000)
    }

    @Test fun aZeroLengthIsACutAtTheSavedSpotWithoutASecondPlayer() {
        saved[Triple("PL1", "ending", "next")] = Transition(160_000, 30_000, 0)
        build()
        runUntil("the cut") { handOver != null }
        val h = handOver!!
        assertTrue("cut at ${h.fromMs} ms", h.fromMs in 159_950..160_300)
        assertEquals(30_000L, h.toMs)
        assertTrue("no second player for a cut", tails.isEmpty())
        assertEquals(1f, main.volume)
        assertEquals(listOf(Triple("PL1", "ending", "next")), played)
    }

    @Test fun withNoPlaylistNothingIsAsked() {
        saved[Triple("PL1", "ending", "next")] = Transition(170_000, 20_000, 8_000)
        playlist = null
        build()
        run(25_000)
        assertNull(handOver)
    }

    // --- The shared law (C4): the engine's and the editor's Listen are the same function ---

    @Test fun curvesPlayAsThemselves() {
        for (s in TransitionStyle.entries.filter { it.curve != null }) for (p in listOf(0f, 0.3f, 0.5f, 0.9f, 1f))
            assertEquals(s.curve!!.gains(p), TransitionSound.gains(s, p, listener = CrossfadeCurve.LINEAR))
    }

    @Test fun sinceB5TheEffectMovesPlayAsMade() {
        assertTrue("effects are wired in (B5)", TransitionSound.EFFECTS_PLAY)
        val listener = CrossfadeCurve.S_CURVE
        // Automatic is the listener's own curve, with no effects.
        for (p in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) assertEquals(listener.gains(p), TransitionSound.gains(TransitionStyle.AUTOMATIC, p, 0.5f, listener))
        val auto = TransitionSound.scripts(TransitionStyle.AUTOMATIC,
            TransitionSound.place(TransitionStyle.AUTOMATIC, Transitions.fit(Transition(170_000, 0, 8_000), 200_000, 200_000), 500.0))
        assertTrue(auto.first.ramps.isEmpty() && auto.second.ramps.isEmpty())
        // The three effect moves: their own volume law and their own effects, Automix's (as judged by ear).
        for (s in listOf(TransitionStyle.BASS_SWAP, TransitionStyle.FILTER_OUT, TransitionStyle.ECHO_OUT)) {
            assertTrue(s.name, TransitionSound.playsAsMade(s))
            for (p in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) assertEquals(s.name, MixMoves.gains(s.move!!, p, 0.5f, listener), TransitionSound.gains(s, p, 0.5f, listener))
            val place = TransitionSound.place(s, Transitions.fit(Transition(170_000, 0, 8_000, s), 200_000, 200_000), 500.0)
            val (a, b) = TransitionSound.scripts(s, place)
            val (ma, mb) = MixMoves.scripts(place)
            assertTrue("${s.name}: the ending song has effects", a.ramps.isNotEmpty())
            assertEquals(s.name, ma.ramps, a.ramps)
            assertEquals(s.name, mb.ramps, b.ramps)
        }
    }

    @Test fun fitKeepsThePointsInTheirSongsAndSaysWhenShortened() {
        val ok = Transitions.fit(Transition(170_000, 5_000, 8_000), 200_000, 200_000)
        assertEquals(listOf(170_000L, 5_000L, 8_000L), listOf(ok.startMs, ok.inMs, ok.lengthMs))
        assertFalse(ok.shortened)
        // A third of the shorter song.
        assertEquals(15_000L, Transitions.fit(Transition(170_000, 0, 30_000), 200_000, 45_000).lengthMs)
        // Not past the first song's end.
        val late = Transitions.fit(Transition(195_000, 0, 10_000), 200_000, 200_000)
        assertEquals(5_000L, late.lengthMs)
        assertTrue(late.shortened)
        // Never longer than the editor's 30 s, whatever was stored.
        assertEquals(30_000L, Transitions.fit(Transition(100_000, 0, 90_000), 400_000, 400_000).lengthMs)
        // A cut stays a cut.
        assertTrue(Transitions.fit(Transition(170_000, 0, 0), 200_000, 200_000).let { it.cut && !it.shortened })
        assertTrue(abs(Transitions.fit(Transition(-5, -5, 1_000), 200_000, null).startMs) == 0L)
    }
}
