package com.prism.music

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.prism.music.data.prefs.CrossfadeCurve
import com.prism.music.playback.MixEngine
import com.prism.music.playback.MixMoves
import com.prism.music.playback.MixSide
import com.prism.music.playback.MixState
import com.prism.music.playback.Move
import com.prism.music.playback.MovePlace
import com.prism.music.playback.Tempo
import com.prism.music.playback.Transition
import com.prism.music.playback.TransitionFx
import com.prism.music.playback.TransitionSound
import com.prism.music.playback.TransitionStyle
import com.prism.music.playback.Transitions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Duration
import kotlin.random.Random

/**
 * 1.8.8 Automix in the engine (plan-mix.md §3.3.4, B5): with Automix on, the plan is the move [MixMoves] picks, placed as
 * it places it; the volumes follow the move's law; each player's [TransitionFx] is armed with its song's effects at the
 * hand-over and cleared when the blend ends, is skipped or stopped; a move that can't be set up is today's crossfade
 * (never silence); DJ mode keeps today's sound unless Automix is on; the diagnostics line names the move; a saved
 * transition with an effect plays as made. Against fake players that play on with the clock, as [MixPlanTest]'s.
 */
@RunWith(RobolectricTestRunner::class)
class MixAutomixTest {
    private val context = ApplicationProvider.getApplicationContext<PrismApp>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val state = MixState()
    private val prefs = context.getSharedPreferences("mix_tempo", Context.MODE_PRIVATE)
    private val keys = context.getSharedPreferences("song_key", Context.MODE_PRIVATE)

    private fun item(id: String, ms: Long = DUR) = MediaItem.Builder().setMediaId(id).setUri("https://example.invalid/$id")
        .setMediaMetadata(MediaMetadata.Builder().setDurationMs(ms).build()).build()

    private fun now() = SystemClock.elapsedRealtime()

    private data class Seek(val fromIndex: Int, val fromMs: Long, val toIndex: Int, val toMs: Long)

    /** A player that plays on with the clock (as [MixPlanTest]'s). */
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
                "setVolume" -> { volume = args!![0] as Float; null }
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
    /** Each second player's own effects processor (null: made without one). */
    private val tailFxs = ArrayList<TransitionFx?>()
    private val mainFx = TransitionFx().apply { configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT)); flush() }
    private var automixOn = true
    private var mixOn = true
    private var withMainFx = true
    private var harmonic: (String, String) -> Double? = { _, _ -> null }
    private val saved = HashMap<Triple<String, String, String>, Transition>()
    private var shapeOf: (String) -> com.prism.music.playback.SongShape? = { null }
    private lateinit var engine: MixEngine

    /** A song [DUR] long at [bpm], its music 0.2–195 s, first beat at 0.5 s, its last beats on the same grid (as MixMovesTest's). */
    private fun tempo(bpm: Double, fadeOutMs: Long = 400, quietIntroMs: Long = 0): Tempo {
        val beat = 60_000.0 / bpm
        val outroBeat = 500 + Math.floor((LAST_SOUND - 500) / beat) * beat
        return Tempo(bpm, 8.0, 200, LAST_SOUND, 500, outroBeat.toLong(), fadeOutMs, quietIntroMs)
    }

    private fun scan(id: String, t: Tempo) { prefs.edit().putString(id, t.encode()).commit() }

    /** Where Automix puts [move] for the scans of "ending" into "next" (what the engine must play). */
    private fun placeOf(move: Move): MovePlace {
        val a = context.container.mixTempo.cached("ending")
        val b = context.container.mixTempo.cached("next")
        return MixMoves.place(move, MixSide(a, null, null, DUR), MixSide(b, null, null, DUR))!!
    }

    private fun build(startMs: Long = 165_000) {
        main.items = listOf(item("ending"), item("next"), item("third"))
        main.moveTo(0, startMs)
        main.seeks.clear()
        engine = MixEngine(
            main = main.player,
            scope = scope,
            tempo = context.container.mixTempo,
            mixMs = { if (mixOn) 6_000L else null },
            auto = { false },
            beatMatch = { false },
            harmonic = harmonic,
            allowed = { _, _ -> true },
            scanFromPhone = {},
            buildTail = { Fake("tail ${tails.size}", Player.STATE_IDLE).also { tails += it; tailFxs += null }.player },
            state = state,
            curve = { CrossfadeCurve.LINEAR },
            playlist = { "PL1" },
            custom = { p, a, b -> saved[Triple(p, a, b)] },
            automix = { automixOn },
            mainFx = if (withMainFx) mainFx else null,
            buildFxTail = { _, fx -> Fake("tail ${tails.size}", Player.STATE_IDLE).also { tails += it; tailFxs += fx }.player },
            shapeOf = { shapeOf(it) },
        )
        engine.start()
    }

    /** 1.8.8 review. Fails on the old code: the shapes (a file read each) were read on the engine's loop, the main thread. */
    @Test fun theSongsShapesAreReadOffTheMainThreadBeforeThePlan() {
        scan("ending", tempo(120.0)); scan("next", tempo(124.0))
        val reads = java.util.Collections.synchronizedList(ArrayList<Pair<String, Boolean>>())
        shapeOf = { id -> reads += id to (Looper.myLooper() == Looper.getMainLooper()); null }
        build(startMs = 130_000)
        runUntil("the hand-over", maxMs = 90_000) { handOver != null }
        assertTrue("both read: $reads", reads.map { it.first }.containsAll(listOf("ending", "next")))
        assertTrue("read on the main thread: $reads", reads.none { it.second })
        // Kept: a song with none is looked at again only after a while, not on every look of the plan (four a second).
        assertTrue(reads.toString(), reads.size <= 4)
        runToTheEnd()
        assertEquals("next", main.mediaId)
    }

    @Before fun clear() {
        prefs.edit().clear().commit()
        keys.edit().clear().commit()
        ShadowLog.clear()
    }

    @After fun stop() { if (::engine.isInitialized) engine.release(); scope.cancel() }

    private fun run(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun runUntil(what: String, maxMs: Long = 45_000, done: () -> Boolean) {
        var waited = 0L
        while (!done()) {
            assertTrue("never: $what", waited < maxMs)
            run(10); waited += 10
        }
    }

    private val handOver get() = main.seeks.firstOrNull { it.fromIndex == 0 && it.toIndex != 0 }

    private fun mixLog(): String = ShadowLog.getLogsForTag("PrismMix").joinToString("\n") { it.msg }

    /** What leaves the main player's processor after a seek's flush, against what went in. */
    private fun mainSoundAfterASeek(): Pair<ByteArray, ByteArray> {
        mainFx.flush(AudioProcessor.StreamMetadata.Builder().setPositionOffsetUs(0).setPeriodUid("third").build())
        val r = Random(9)
        val b = ByteBuffer.allocateDirect(48_000 * 2 * 4).order(ByteOrder.nativeOrder())
        repeat(48_000 * 2) { b.putFloat((r.nextDouble() * 1.6 - 0.8).toFloat()) }
        b.flip()
        val copy = ByteArray(b.remaining()).also { b.duplicate().get(it) }
        mainFx.queueInput(b)
        val o = mainFx.output
        return copy to ByteArray(o.remaining()).also { o.get(it) }
    }

    private fun runToTheEnd() {
        runUntil("the blend is done") { tails.isNotEmpty() && tails.all { it.released } && !engine.blending }
    }

    @Test fun withAutomixTheMoveIsPickedPlacedAndPlayed() {
        scan("ending", tempo(120.0)); scan("next", tempo(124.0))
        build()
        val place = placeOf(Move.BASS_SWAP)
        runUntil("the hand-over") { handOver != null }
        val h = handOver!!
        assertTrue("on the move's phrase: ${h.fromMs} vs ${place.startMs}", h.fromMs in place.startMs - 150..place.startMs + 250)
        assertEquals("the next song from its first beat", place.inMs, h.toMs)
        assertEquals("at the ending song's tempo", place.ratio, main.speed, 1e-4f)
        // Its effects armed on both players, each in its own song's time.
        val (out, into) = MixMoves.scripts(place)
        assertEquals(1, tailFxs.size)
        assertEquals(out.ramps, tailFxs.single()!!.asked!!.ramps)
        assertEquals(into.ramps, mainFx.asked!!.ramps)
        assertEquals(listOf(Move.BASS_SWAP), engine.recentMoves)
        assertTrue(mixLog(), mixLog().contains("Automix: bass swap"))
        // The volumes are the move's (both held near full while the bass cut works), not the listener's curve.
        runUntil("40 % through") { (main.position() - place.inMs) / place.ratio / place.lengthMs >= 0.4f }
        val p = (main.position() - place.inMs) / place.ratio / place.lengthMs
        val (incoming, outgoing) = MixMoves.gains(Move.BASS_SWAP, p, place.pivot)
        assertEquals(incoming, main.volume, 0.06f)
        assertEquals(outgoing, tails.single().volume, 0.06f)
        assertTrue("not the straight curve", tails.single().volume > 0.85f)
        runToTheEnd()
        assertEquals("next", main.mediaId)
        assertEquals(1f, main.volume)
        assertNull("the main player's effects cleared", mainFx.asked)
        assertFalse(engine.mainFxArmed)
    }

    @Test fun withAutomixOffItIsTodaysBlend() {
        automixOn = false
        scan("ending", tempo(120.0)); scan("next", tempo(124.0))
        build()
        runUntil("the hand-over") { handOver != null }
        // Crossfade's own 6 s against the end of the music, the next song from its first sound.
        assertTrue("at ${handOver!!.fromMs}", handOver!!.fromMs in 188_850..189_250)
        assertEquals(200L, handOver!!.toMs)
        assertEquals("no effects processor made for it", listOf<TransitionFx?>(null), tailFxs)
        assertNull(mainFx.asked)
        assertTrue(engine.recentMoves.isEmpty())
        assertFalse(mixLog().contains("Automix"))
        runToTheEnd()
        assertEquals(1f, main.volume)
    }

    @Test fun anEchoArmsOnlyTheEndingSong() {
        // Tempos too far apart to match, an ending that doesn't fade: the echo.
        scan("ending", tempo(120.0)); scan("next", tempo(90.0))
        build()
        val place = placeOf(Move.ECHO_OUT)
        runUntil("the ending song's effects armed") { tailFxs.firstOrNull()?.asked != null }
        assertTrue("armed before the catch's beat (${main.position()} ms)", main.position() < place.pivotMs - place.beatMs)
        assertEquals(MixMoves.scripts(place).first.ramps, tailFxs.single()!!.asked!!.ramps)
        runUntil("the hand-over") { handOver != null }
        assertTrue("at the catch: ${handOver!!.fromMs} vs ${place.startMs}", handOver!!.fromMs in place.startMs - 150..place.startMs + 250)
        assertNull("the next song starts clean", mainFx.asked)
        assertTrue(mixLog().contains("Automix: echo out"))
        runToTheEnd()
        assertEquals("next", main.mediaId)
        assertEquals(1f, main.volume)
    }

    @Test fun aSkipMidMoveLeavesNothingArmedAndTheSoundUntouched() {
        scan("ending", tempo(120.0)); scan("next", tempo(124.0))
        build()
        runUntil("the hand-over") { handOver != null }
        assertNotNull(mainFx.asked)
        run(1_500)
        // The listener skips to the third song mid-move.
        main.moveTo(2, 0)
        runUntil("the skip's fade is over") { tails.all { it.released } && !engine.blending }
        assertNull("no script left on the main player", mainFx.asked)
        assertNull("nor on the ending song's", tailFxs.single()!!.asked)
        assertEquals(1f, main.volume)
        // The skip's seek: what plays next comes through the main player's processor as it came.
        val (input, output) = mainSoundAfterASeek()
        assertArrayEquals(input, output)
        assertFalse(mainFx.running)
    }

    @Test fun aStopBeforeTheHandOverClearsBoth() {
        scan("ending", tempo(120.0)); scan("next", tempo(124.0))
        build()
        runUntil("the ending song's effects armed") { tailFxs.firstOrNull()?.asked != null }
        // A seek away from the blend, before it starts.
        main.moveTo(0, 100_000)
        runUntil("the second player let go") { tails.all { it.released } }
        assertNull(tailFxs.single()!!.asked)
        assertNull(mainFx.asked)
        assertNull("no hand-over", handOver)
        val (input, output) = mainSoundAfterASeek()
        assertArrayEquals(input, output)
    }

    @Test fun withoutTheMainPlayersEffectsTheMoveIsABlend() {
        withMainFx = false
        scan("ending", tempo(120.0)); scan("next", tempo(124.0))
        build()
        runUntil("the hand-over") { handOver != null }
        val place = placeOf(Move.BLEND)
        assertTrue("at the blend's place: ${handOver!!.fromMs} vs ${place.startMs}", handOver!!.fromMs in place.startMs - 150..place.startMs + 250)
        assertEquals("no effects made", listOf<TransitionFx?>(null), tailFxs)
        assertEquals(listOf(Move.BLEND), engine.recentMoves)
        assertTrue(mixLog(), mixLog().contains("Automix: blend (fell back from bass swap: no effects on this player)"))
        runToTheEnd()
        assertEquals("next", main.mediaId)
        assertEquals(1f, main.volume)
    }

    @Test fun aMoveStartedLateIsTodaysCrossfadeNotSilence() {
        scan("ending", tempo(120.0)); scan("next", tempo(124.0))
        val place = placeOf(Move.BASS_SWAP)
        // Arrived too close to it to get ready: it starts late, so its effects (placed for its start) don't play.
        build(startMs = place.startMs - 3_000)
        runUntil("the hand-over") { handOver != null }
        assertEquals("no effects made", listOf<TransitionFx?>(null), tailFxs)
        assertNull(mainFx.asked)
        assertTrue(mixLog(), mixLog().contains("fell back from bass swap: started late"))
        // Today's volume law (the listener's curve: straight), half-way through what's left.
        val from = handOver!!.fromMs
        val len = place.startMs + place.lengthMs - from
        runUntil("half-way") { (main.position() - place.inMs) / place.ratio >= len / 2 }
        assertEquals(0.5f, main.volume, 0.08f)
        assertEquals(0.5f, tails.single().volume, 0.08f)
        runToTheEnd()
        assertEquals("next", main.mediaId)
        assertEquals(1f, main.volume)
    }

    @Test fun songsNotScannedYetBlend() {
        build(startMs = 175_000)
        runUntil("the hand-over") { handOver != null }
        // Nothing known of either: the blend, at Auto's length for an unscanned song, against the file's end.
        assertTrue("at ${handOver!!.fromMs}", handOver!!.fromMs in 193_850..194_250)
        assertEquals(listOf<TransitionFx?>(null), tailFxs)
        assertEquals(listOf(Move.BLEND), engine.recentMoves)
        runToTheEnd()
        assertEquals(1f, main.volume)
    }

    @Test fun djModeKeepsTodaysSoundWithoutAutomix() {
        automixOn = false
        // DJ mode: keys that sit together get its long blend.
        harmonic = { _, _ -> 0.0 }
        scan("ending", tempo(120.0)); scan("next", tempo(124.0))
        build()
        runUntil("the hand-over") { handOver != null }
        assertTrue("DJ mode's 10 s: ${handOver!!.fromMs}", handOver!!.fromMs in 184_850..185_250)
        assertEquals(200L, handOver!!.toMs)
        assertEquals(listOf<TransitionFx?>(null), tailFxs)
        assertTrue(engine.recentMoves.isEmpty())
    }

    @Test fun djModeWithAutomixUsesTheMoves() {
        harmonic = { _, _ -> 0.0 }
        scan("ending", tempo(120.0)); scan("next", tempo(124.0))
        build()
        val place = placeOf(Move.BASS_SWAP)
        runUntil("the hand-over") { handOver != null }
        assertTrue("the move's place, not DJ mode's 10 s: ${handOver!!.fromMs}", handOver!!.fromMs in place.startMs - 150..place.startMs + 250)
        assertEquals(place.inMs, handOver!!.toMs)
        assertEquals(listOf(Move.BASS_SWAP), engine.recentMoves)
        assertNotNull(mainFx.asked)
    }

    @Test fun moveNamesForTheDiagnosticsLine() {
        assertEquals(listOf("bass swap", "filter out", "echo out", "blend", "cut"), Move.entries.map { MixEngine.moveName(it) })
    }

    // --- Mix it yourself: a saved transition with an effect plays as made (B5 turns EFFECTS_PLAY on) ---

    @Test fun aSavedBassSwapPlaysAsMadeWithCrossfadeOff() {
        mixOn = false
        automixOn = false
        scan("ending", tempo(120.0))
        val t = Transition(170_000, 20_000, 8_000, TransitionStyle.BASS_SWAP)
        saved[Triple("PL1", "ending", "next")] = t
        build(startMs = 150_000)
        val place = TransitionSound.place(t.style, Transitions.fit(t, DUR, DUR), 500.0)
        val (out, into) = TransitionSound.scripts(t.style, place)
        assertTrue(out.ramps.isNotEmpty() && into.ramps.isNotEmpty())
        runUntil("the hand-over") { handOver != null }
        assertEquals(20_000L, handOver!!.toMs)
        assertEquals(out.ramps, tailFxs.single()!!.asked!!.ramps)
        assertEquals(into.ramps, mainFx.asked!!.ramps)
        runUntil("30 % through") { main.position() >= 20_000 + 2_400 }
        val p = (main.position() - 20_000f) / 8_000
        val (incoming, outgoing) = TransitionSound.gains(t.style, p, place.pivot, CrossfadeCurve.LINEAR)
        assertEquals(incoming, main.volume, 0.06f)
        assertEquals(outgoing, tails.single().volume, 0.06f)
        runToTheEnd()
        assertEquals("next", main.mediaId)
        assertEquals(1f, main.volume)
        assertNull(mainFx.asked)
        // Not one of Automix's own: the no-three-in-a-row rule doesn't count it.
        assertTrue(engine.recentMoves.isEmpty())
    }

    companion object {
        private const val DUR = 200_000L
        private const val LAST_SOUND = 195_000L
    }
}
