package com.prism.music

import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.prism.music.data.prefs.CrossfadeCurve
import com.prism.music.playback.MixPreview
import com.prism.music.playback.PreviewPlan
import com.prism.music.playback.Transition
import com.prism.music.playback.TransitionSound
import com.prism.music.playback.TransitionStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.time.Duration

/**
 * 1.8.8 Mix it yourself (plan-mix.md §4.3, C4): the editor's Listen. Its volumes are the very law the engine plays a
 * saved transition with ([TransitionSound.gains]); its two players play the first song from 6 s before the transition,
 * bring the second in at the saved spot, and are both let go at the end, leaving nothing behind.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MixPreviewTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @After fun stop() { scope.cancel() }

    private val t = Transition(170_000, 20_000, 8_000, TransitionStyle.GENTLE)
    private val plan = PreviewPlan.of(t, 200_000, 200_000, 0.0, CrossfadeCurve.LINEAR)

    @Test fun theSharedLaw() {
        assertEquals(164_000L, plan.fromMs)
        assertEquals(20_000L + 8_000 + 6_000, plan.untilMs)
        assertEquals(0f to 1f, plan.volumes(169_999))
        for (ms in listOf(170_000L, 172_000L, 174_000L, 177_000L))
            assertEquals(TransitionSound.gains(TransitionStyle.GENTLE, (ms - 170_000) / 8_000f, plan.place.pivot, CrossfadeCurve.LINEAR), plan.volumes(ms))
        assertEquals(1f to 0f, plan.volumes(178_000))
        // Automatic and the moves (before B5): the listener's own curve, as the engine.
        val auto = PreviewPlan.of(t.copy(style = TransitionStyle.AUTOMATIC), 200_000, 200_000, 0.0, CrossfadeCurve.LINEAR)
        assertEquals(0.5f to 0.5f, auto.volumes(174_000))
        // A cut: the second song at full at once.
        val cut = PreviewPlan.of(t.copy(lengthMs = 0), 200_000, 200_000, 0.0, CrossfadeCurve.LINEAR)
        assertEquals(1f to 0f, cut.volumes(170_000))
        assertTrue(cut.secondPlays(170_000) && !cut.secondPlays(169_999))
    }

    private fun now() = SystemClock.elapsedRealtime()

    /** A player that plays on with the clock once it's asked to. */
    private class Fake(val label: String) {
        var item: MediaItem? = null
        var base = 0L
        var since = SystemClock.elapsedRealtime()
        var playWhenReady = false
            set(v) { base = position(); since = SystemClock.elapsedRealtime(); field = v }
        var ready = false
        var volume = 1f
        var released = false
        var startedAt = -1L
        fun duration() = item?.mediaMetadata?.durationMs ?: C.TIME_UNSET
        fun running() = playWhenReady && ready && !released
        fun position(): Long = minOf(if (running()) base + (SystemClock.elapsedRealtime() - since) else base, duration())
        val player: ExoPlayer = Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java)) { self, m, args ->
            when (m.name) {
                "hashCode" -> System.identityHashCode(self)
                "equals" -> self === args?.get(0)
                "toString" -> label
                "isPlaying" -> running() && position() < duration()
                "getPlayWhenReady" -> playWhenReady
                "setPlayWhenReady" -> { playWhenReady = args!![0] as Boolean; if (playWhenReady && startedAt < 0) startedAt = position(); null }
                "getPlaybackState" -> when { released -> Player.STATE_IDLE; ready && position() >= duration() -> Player.STATE_ENDED; ready -> Player.STATE_READY; else -> Player.STATE_IDLE }
                "getCurrentPosition" -> position()
                "getDuration" -> duration()
                "getVolume" -> volume
                "setVolume" -> { volume = args!![0] as Float; null }
                "setMediaItem" -> { item = args!![0] as MediaItem; base = if (args.size > 1) args[1] as Long else 0L; since = SystemClock.elapsedRealtime(); null }
                "prepare" -> { ready = true; null }
                "release" -> { released = true; null }
                else -> when (m.returnType) {
                    java.lang.Boolean.TYPE -> false; java.lang.Integer.TYPE -> 0; java.lang.Long.TYPE -> 0L; java.lang.Float.TYPE -> 0f
                    else -> null
                }
            }
        } as ExoPlayer
    }

    private fun item(id: String) = MediaItem.Builder().setMediaId(id).setUri("https://example.invalid/$id")
        .setMediaMetadata(MediaMetadata.Builder().setDurationMs(200_000).build()).build()

    private fun run(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test fun listenPlaysAcrossTheSavedSpotAndLetsBothPlayersGo() {
        val made = ArrayList<Fake>()
        val preview = MixPreview(scope) { it, _ -> Fake(it.mediaId).also { f -> made += f }.player }
        assertTrue(preview.play(item("ending"), item("next"), plan))
        val (a, b) = made
        assertEquals("the first song from 6 s before", 164_000L, a.base)
        assertFalse("the second waits", b.playWhenReady)
        run(100)
        assertEquals(MixPreview.Phase.PLAYING, preview.phase.value)
        // Up to the spot only the first is heard.
        run(5_800)
        assertFalse(b.playWhenReady)
        assertEquals(0f, b.volume)
        // Over it, the second comes in where it was set to, and the two cross.
        run(4_100)
        assertTrue(b.playWhenReady)
        assertTrue("the second from its spot: ${b.startedAt}", b.startedAt in 20_000L..20_100L)
        assertTrue("half-way: ${b.volume} / ${a.volume}", b.volume in 0.3f..0.7f && a.volume in 0.3f..0.7f)
        // After the overlap the first goes; 6 s on, all of it ends.
        run(4_500)
        assertTrue(a.released)
        assertEquals(1f, b.volume)
        run(5_000)
        assertFalse("the second plays on to 6 s past the overlap", b.released)
        run(5_000)
        assertTrue(b.released)
        assertEquals(MixPreview.Phase.IDLE, preview.phase.value)
    }

    @Test fun stopLetsGoAtOnce() {
        val made = ArrayList<Fake>()
        val preview = MixPreview(scope) { it, _ -> Fake(it.mediaId).also { f -> made += f }.player }
        preview.play(item("ending"), item("next"), plan)
        run(2_000)
        preview.stop()
        assertTrue(made.all { it.released })
        assertEquals(MixPreview.Phase.IDLE, preview.phase.value)
        // No player (no playback service): nothing plays, and it says so.
        assertFalse(MixPreview(scope) { _, _ -> null }.play(item("a"), item("b"), plan))
    }
}
