package com.prism.music.playback

import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.prism.music.PrismApp
import com.prism.music.container
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

/**
 * 1.8.8 "Blend when I skip": [SkipFade] driven from [MixEngine]'s loop (Crossfade off, as it works either way), against
 * fake players that play on with the clock: the head takes a moment to load, the main player a moment to start again after
 * a seek. Every way through ends with the main player on the chosen song at full volume and its own speed, and no second
 * player alive; with the switch off nothing is made at all.
 */
@RunWith(RobolectricTestRunner::class)
class SkipFadeTest {
    private val context = ApplicationProvider.getApplicationContext<PrismApp>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val state = MixState()

    private fun item(id: String) = MediaItem.Builder().setMediaId(id).setUri("https://example.invalid/$id")
        .setMediaMetadata(MediaMetadata.Builder().setDurationMs(200_000L).build()).build()

    private fun now() = SystemClock.elapsedRealtime()

    /** A queue of [n] songs, in order (what the next and previous are worked out on). */
    private class Line(private val n: Int) : Timeline() {
        override fun getWindowCount() = n
        override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window =
            window.set(windowIndex, MediaItem.EMPTY, null, 0, 0, 0, true, false, null, 0, C.TIME_UNSET, windowIndex, windowIndex, 0)
        override fun getPeriodCount() = n
        override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period = period.set(periodIndex, periodIndex, periodIndex, C.TIME_UNSET, 0)
        override fun getIndexOfPeriod(uid: Any) = (uid as? Int) ?: C.INDEX_UNSET
        override fun getUidOfPeriod(periodIndex: Int): Any = periodIndex
    }

    /** A player that plays on with the clock once ready: [loadMs] after prepare(), [restartMs] after a seek. */
    private inner class Fake(val label: String, private val loadMs: Long = 0, private val restartMs: Long = 0) {
        var items = listOf<MediaItem>()
        var index = 0
        private var base = 0L
        private var since = now()
        private var readyAt = 0L
        var playWhenReady = true
            set(v) { hold(); field = v }
        var prepared = false
        var failed = false
        var speed = 1f
            set(v) { hold(); field = v }
        var volume = 1f
        var released = false
        val volumes = ArrayList<Float>()
        val seeks = ArrayList<Pair<Int, Long>>()

        private fun ready() = prepared && !failed && !released && now() >= readyAt
        private fun running() = playWhenReady && ready()
        fun position(): Long = if (running()) base + ((now() - maxOf(since, readyAt)) * speed).toLong().coerceAtLeast(0) else base
        private fun hold() { base = position(); since = now() }
        fun state() = when {
            released || failed || !prepared -> Player.STATE_IDLE
            !ready() -> Player.STATE_BUFFERING
            else -> Player.STATE_READY
        }
        fun isPlaying() = running()
        /** Volumes and moves in the order they came ("vol 1.0", "seek 2"). */
        val events = ArrayList<String>()
        fun moveTo(i: Int, ms: Long) {
            seeks += i to ms
            events += "seek $i"
            index = i; base = ms; since = now(); readyAt = now() + restartMs
        }
        val mediaId: String? get() = items.getOrNull(index)?.mediaId

        val player: ExoPlayer = Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java)) { self, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(self)
                "equals" -> self === args?.get(0)
                "toString" -> label
                "isPlaying" -> isPlaying()
                "getPlayWhenReady" -> playWhenReady
                "setPlayWhenReady" -> { playWhenReady = args!![0] as Boolean; null }
                "getPlaybackState" -> state()
                "getCurrentMediaItem" -> items.getOrNull(index)
                "getCurrentMediaItemIndex" -> index
                "getMediaItemCount" -> items.size
                "getCurrentPosition" -> position()
                "getCurrentTimeline" -> Line(items.size)
                "getNextMediaItemIndex" -> if (index + 1 < items.size) index + 1 else C.INDEX_UNSET
                "getMediaItemAt" -> items[args!![0] as Int]
                "getVolume" -> volume
                "setVolume" -> { volume = args!![0] as Float; volumes += volume; events += "vol $volume"; null }
                "getPlaybackParameters" -> PlaybackParameters(speed)
                "setPlaybackParameters" -> { speed = (args!![0] as PlaybackParameters).speed; null }
                "getRepeatMode" -> Player.REPEAT_MODE_OFF
                "seekTo" -> { if (args!!.size == 2) moveTo(args[0] as Int, args[1] as Long) else moveTo(index, args[0] as Long); null }
                "seekToDefaultPosition" -> { if (args?.size == 1) moveTo(args[0] as Int, 0) else moveTo(index, 0); null }
                "setMediaItem" -> { items = listOf(args!![0] as MediaItem); index = 0; null }
                "prepare" -> { prepared = true; readyAt = now() + loadMs; since = now(); null }
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

    private val main = Fake("main", restartMs = 350)
    private val heads = ArrayList<Fake>()
    private var on = true
    private var light = false
    private var allowed = true
    private var headLoadMs = 200L
    private var settled = 0
    private lateinit var skip: SkipFade
    private lateinit var engine: MixEngine

    @Before
    fun start() {
        main.items = listOf(item("a"), item("b"), item("c"), item("d"))
        main.prepared = true
        main.moveTo(0, 60_000)
        run(400)
        main.seeks.clear()
        skip = SkipFade(
            main = main.player,
            buildHead = { Fake("head ${heads.size}", loadMs = headLoadMs).also { heads += it }.player },
            firstSoundMs = { 0L },
            on = { on },
            light = { light },
            allowed = { _, _ -> allowed },
            warm = { _, isNext -> isNext },
            state = state,
            onSettled = { settled++ },
            note = {},
        )
        engine = MixEngine(
            main = main.player, scope = scope, tempo = context.container.mixTempo,
            // Crossfade off: a skip-blend works all the same.
            mixMs = { null }, auto = { false }, beatMatch = { false },
            allowed = { _, _ -> true }, scanFromPhone = {}, buildTail = { error("no end-of-song blend here") },
            state = state, skip = skip,
        )
        engine.start()
    }

    @After
    fun stop() {
        engine.release()
        scope.cancel()
    }

    private fun run(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun runUntil(what: String, maxMs: Long = 12_000, done: () -> Boolean) {
        var waited = 0L
        while (!done()) {
            assertTrue("never: $what", waited < maxMs)
            run(10); waited += 10
        }
    }

    private fun assertAllBack(on: String) {
        assertFalse("still under way", skip.active)
        assertFalse("a head left", skip.headAlive)
        for (h in heads) assertTrue("${h.label} left alive", h.released)
        assertEquals("main volume", 1f, main.volume)
        assertEquals("main speed", 1f, main.speed)
        assertEquals("on the chosen song", on, main.mediaId)
        assertNull("the player still shown a blend", state.blend.value)
        for (v in main.volumes + heads.flatMap { it.volumes }) assertTrue("volume $v", v in 0f..1f)
    }

    @Test fun offASkipIsTodaysAndNothingIsMade() {
        on = false
        assertFalse(skip.press(SkipKind.NEXT, 1))
        assertTrue(heads.isEmpty())
        assertFalse(skip.active)
        var ran = false
        skip.fadeOutThen { ran = true }
        assertTrue("a new queue at once", ran)
        assertTrue(main.volumes.isEmpty())
        run(2_000)
        assertTrue(heads.isEmpty())
        assertEquals("a", main.mediaId)
    }

    @Test fun aNextBlendsIntoTheChosenSongWithCrossfadeOffAndEndsWithOnePlayer() {
        assertTrue(skip.press(SkipKind.NEXT, 1))
        // The song heard plays on; the player is shown where it's going at once.
        assertEquals("a", main.mediaId)
        assertEquals(BlendNow("a", "b", 0f), state.blend.value)
        assertEquals(1, heads.size)
        assertEquals("b", heads.single().mediaId)
        runUntil("the fade under way") { heads.single().volume in 0.3f..0.7f }
        assertEquals("still on the old song mid-fade", "a", main.mediaId)
        // Equal power: the old song still near full while the new one is at a third.
        assertTrue("main at ${main.volume}", main.volume in 0.7f..0.97f)
        runUntil("over") { !skip.active }
        assertAllBack("b")
        // Moved once, by a seek into the chosen song (a skip: the old song counts as skipped), ahead of the start.
        assertEquals(1, main.seeks.size)
        assertEquals(1, main.seeks.single().first)
        assertTrue("moved to ${main.seeks.single().second} ms", main.seeks.single().second > 1_500)
        assertEquals(1, settled)
    }

    @Test fun aSecondNextBeforeTheMoveGoesToTheSongAfterTheChosenOne() {
        assertTrue(skip.press(SkipKind.NEXT, 1))
        run(600)
        // The session works Next out from the song heard ("a"): it's counted from the chosen one instead.
        assertTrue(skip.press(SkipKind.NEXT, 1))
        assertAllBack("c")
        assertEquals(listOf(2 to 0L), main.seeks)
    }

    @Test fun aSkipWhilePausedIsTodays() {
        main.playWhenReady = false
        assertFalse(skip.press(SkipKind.NEXT, 1))
        assertTrue(heads.isEmpty())
    }

    @Test fun theSameSongOrOneNotBlendedHereIsTodays() {
        assertFalse(skip.press(SkipKind.PREVIOUS, 0))
        allowed = false
        assertFalse(skip.press(SkipKind.NEXT, 1))
        assertFalse(skip.press(SkipKind.NEXT, C.INDEX_UNSET))
        assertTrue(heads.isEmpty())
    }

    @Test fun aTapInTheQueueBlendsToo() {
        assertTrue(skip.press(SkipKind.JUMP, 3))
        runUntil("over") { !skip.active }
        assertAllBack("d")
    }

    @Test fun aSeekByTheListenerFinishesItButTheRunsOwnSeeksDont() {
        assertTrue(skip.press(SkipKind.NEXT, 1))
        runUntil("the move") { main.seeks.isNotEmpty() }
        // Its own move, told back as a discontinuity: still going.
        skip.discontinuity(Player.DISCONTINUITY_REASON_SEEK)
        assertTrue(skip.active)
        run(200)
        // The listener's: finished at once.
        skip.discontinuity(Player.DISCONTINUITY_REASON_SEEK)
        assertAllBack("b")
    }

    @Test fun aSeekInTheFadeFinishesItOnTheNewSongWhereTheHeadIs() {
        assertTrue(skip.press(SkipKind.NEXT, 1))
        runUntil("the fade under way") { heads.single().volume > 0.3f }
        skip.discontinuity(Player.DISCONTINUITY_REASON_SEEK)
        assertAllBack("b")
        assertTrue(main.seeks.single().second > 0)
    }

    @Test fun aSongThatDoesntLoadIn4sIsAPlainSkip() {
        headLoadMs = 60_000
        assertTrue(skip.press(SkipKind.NEXT, 1))
        run(3_900)
        assertEquals("a", main.mediaId)
        runUntil("over", maxMs = 600) { !skip.active }
        assertAllBack("b")
        assertEquals(listOf(1 to 0L), main.seeks)
    }

    @Test fun performanceModeFadesOutOver200MsThenCutsWithNoSecondPlayer() {
        light = true
        assertTrue(skip.press(SkipKind.NEXT, 1))
        assertTrue(heads.isEmpty())
        run(100)
        assertTrue("going down: ${main.volume}", main.volume < 0.7f)
        assertEquals("a", main.mediaId)
        runUntil("over", maxMs = 400) { !skip.active }
        assertAllBack("b")
        assertTrue(heads.isEmpty())
    }

    @Test fun aNewQueueFadesOutOver200MsFirst() {
        var at = -1L
        val from = now()
        skip.fadeOutThen { at = now() - from; main.moveTo(2, 0) }
        assertEquals(-1L, at)
        runUntil("the new queue", maxMs = 400) { at >= 0 }
        assertTrue("after $at ms", at in SkipFadeRules.FADE_OUT_MS..SkipFadeRules.FADE_OUT_MS + 60)
        assertTrue("faded first", main.volumes.dropLast(1).isNotEmpty() && main.volumes.dropLast(1).all { it < 1f })
        assertEquals(1f, main.volume)
        assertFalse(skip.active)
    }

    @Test fun anEndOfSongBlendHeardIsLeftToTheEngineAndTheSkipIsPlain() {
        // 1.8.8 review: the blend isn't aborted here (a hard cut): the plain skip goes through and the engine's own skip
        // fade takes it down, as with the switch off (MixBlendTest.withBlendWhenISkipOnASkipMidBlendStillFades).
        var dropped = 0
        skip.mixHeard = { true }
        skip.dropMix = { dropped++ }
        assertFalse(skip.press(SkipKind.NEXT, 1))
        assertEquals(0, dropped)
        assertTrue(heads.isEmpty())
        assertFalse(skip.active)
    }

    @Test fun castingStartingOrTheServiceGoingEndsItAtOnce() {
        assertTrue(skip.press(SkipKind.NEXT, 1))
        runUntil("the fade under way") { heads.single().volume > 0.2f }
        engine.stopBlend()
        assertAllBack("b")
        run(500)
        assertTrue(skip.press(SkipKind.NEXT, 2))
        run(300)
        engine.release()
        assertFalse(skip.active)
        assertFalse(skip.headAlive)
        assertEquals(1f, main.volume)
    }

    // 1.8.8 review (a second read of SkipFade): the four points below.

    @Test fun aSecondSkipInTheFadeMovesBeforeTheVolumeComesBack() {
        assertTrue(skip.press(SkipKind.NEXT, 1))
        runUntil("the fade under way") { heads.single().volume in 0.3f..0.7f }
        main.events.clear()
        assertTrue(skip.press(SkipKind.NEXT, 1))
        // Up to full only once on the newest choice: the song left is never heard at full again.
        assertEquals("seek 2", main.events.first())
        assertTrue(main.events.indexOf("vol 1.0") > 0)
        assertAllBack("c")
    }

    @Test fun aNewQueueInTheFadeComesBeforeTheVolumeDoes() {
        assertTrue(skip.press(SkipKind.NEXT, 1))
        runUntil("the fade under way") { heads.single().volume in 0.3f..0.7f }
        main.events.clear()
        var ran = false
        skip.fadeOutThen { ran = true; main.moveTo(3, 0) }
        assertTrue(ran)
        assertEquals("seek 3", main.events.first())
        assertAllBack("d")
    }

    @Test fun aNextToNowhereWhileCatchingUpLeavesTheRunBe() {
        assertTrue(skip.press(SkipKind.NEXT, 1))
        runUntil("the move") { main.seeks.isNotEmpty() }
        // Next on the last song does nothing today: here it doesn't cut the hand-over short either.
        assertTrue(skip.press(SkipKind.NEXT, C.INDEX_UNSET))
        assertTrue("still under way", skip.active)
        runUntil("over") { !skip.active }
        assertAllBack("b")
    }

    @Test fun aNewQueueThatFailsStillLeavesAllNormal() {
        skip.fadeOutThen { error("the new queue failed") }
        run(100)
        assertTrue("fading out: ${main.volume}", main.volume < 1f)
        // A skip cuts the fade-out short: the new queue runs, fails, and is only noted.
        val r = runCatching { skip.press(SkipKind.NEXT, 1) }
        assertTrue("nothing thrown at the skip: ${r.exceptionOrNull()}", r.isSuccess)
        assertFalse(skip.active)
        assertEquals(1f, main.volume)
        assertEquals(1, settled)
    }

    @Test fun aStepThatFailsStillPlaysTheNewQueue() {
        var boom = false
        val s = SkipFade(
            main = main.player, buildHead = { error("no head") }, firstSoundMs = { 0L }, on = { true }, light = { false },
            allowed = { _, _ -> true }, warm = { _, _ -> false }, speed = { if (boom) error("boom") else 1f }, note = {},
        )
        var ran = false
        s.fadeOutThen { ran = true }
        assertTrue(s.active)
        boom = true
        s.tick()
        assertTrue("the new queue the listener asked for", ran)
        assertFalse(s.active)
        assertEquals(1f, main.volume)
    }

    /** 1.8.8 review. Fails on the old code: the move after an error was only logged, and the old song played on. */
    @Test fun aStepThatFailsBeforeTheMoveStillLandsOnTheChosenSong() {
        for (inFade in listOf(false, true)) {
            main.moveTo(0, 60_000)
            run(400)
            var boom = false
            val made = ArrayList<Fake>()
            val s = SkipFade(
                main = main.player, buildHead = { Fake("head", loadMs = 200).also { made += it }.player }, firstSoundMs = { 0L },
                on = { true }, light = { false }, allowed = { _, _ -> true }, warm = { _, _ -> false },
                speed = { if (boom) error("boom") else 1f }, note = {},
            )
            assertTrue(s.press(SkipKind.NEXT, 1))
            if (inFade) {
                // Into the fade: the head plays, both heard.
                var waited = 0L
                while (made.single().volume < 0.2f) { assertTrue("never faded", waited < 3_000); run(10); waited += 10; s.tick() }
            }
            assertEquals("a", main.mediaId)
            boom = true
            s.tick()
            assertFalse("inFade=$inFade", s.active)
            assertEquals("inFade=$inFade: on the chosen song", "b", main.mediaId)
            assertEquals(1f, main.volume)
            assertTrue(made.single().released)
        }
    }

    @Test fun eightSecondsAfterThePressAllIsNormalEvenPaused() {
        assertTrue(skip.press(SkipKind.NEXT, 1))
        runUntil("the fade under way") { heads.single().volume > 0.2f }
        main.playWhenReady = false
        run(SkipFadeRules.LIMIT_MS)
        assertAllBack("b")
    }
}
