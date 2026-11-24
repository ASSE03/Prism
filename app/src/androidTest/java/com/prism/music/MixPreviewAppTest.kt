package com.prism.music

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prism.music.data.model.ArtistRef
import com.prism.music.data.model.Song
import com.prism.music.data.prefs.CrossfadeCurve
import com.prism.music.playback.MixPreview
import com.prism.music.playback.PlaybackServices
import com.prism.music.playback.PreviewPlan
import com.prism.music.playback.QueueKind
import com.prism.music.playback.QueueSource
import com.prism.music.playback.Transition
import com.prism.music.playback.TransitionStyle
import com.prism.music.playback.toMediaItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * 1.8.8 Mix it yourself (plan-mix.md §4.4, C4): the editor's Listen on the playback service's real players. It plays
 * through (6 s, a 4 s overlap, 6 s) and lets both players go; Stop lets go at once; the queue's player is never touched
 * (paused before, it is where it was after). Silent tones written here; nothing reaches the network.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class MixPreviewAppTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val c get() = Fixture.container
    private val dir get() = java.io.File(Fixture.context.filesDir, "mix-preview-test")

    @After fun cleanUp() {
        runCatching { instrumentation.runOnMainSync { c.player.stop() } }
        dir.deleteRecursively()
    }

    private fun wav(file: java.io.File, seconds: Int) {
        val rate = 8_000
        val bytes = rate * seconds * 2
        val b = java.nio.ByteBuffer.allocate(44 + bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + bytes).put("WAVE".toByteArray()).put("fmt ".toByteArray())
        b.putInt(16).putShort(1.toShort()).putShort(1.toShort()).putInt(rate).putInt(rate * 2).putShort(2.toShort()).putShort(16.toShort())
        b.put("data".toByteArray()).putInt(bytes)
        file.writeBytes(b.array())
    }

    private fun <T> waitFor(ms: Long, what: () -> T?): T? {
        val until = SystemClock.elapsedRealtime() + ms
        while (SystemClock.elapsedRealtime() < until) { what()?.let { return it }; Thread.sleep(25) }
        return null
    }

    private fun <T> onMain(read: () -> T): T {
        var out: T? = null
        instrumentation.runOnMainSync { out = read() }
        @Suppress("UNCHECKED_CAST")
        return out as T
    }

    @Test fun listenPlaysThroughAndStopsLeavingTheQueueWhereItWas() {
        Fixture.ready()
        dir.mkdirs()
        val songs = (1..3).map { i ->
            val f = java.io.File(dir, "preview-$i.wav").also { wav(it, 40) }
            Song(Song.LOCAL_PREFIX + android.net.Uri.fromFile(f), "Preview tone $i", listOf(ArtistRef("Test Artist", null)), durationSec = 40)
        }
        Fixture.launch()
        waitFor(20_000) { PlaybackServices.playerForTest } ?: error("Prism's playback service didn't start")
        instrumentation.runOnMainSync { c.player.playQueue(listOf(songs[2]), 0, QueueSource(QueueKind.PLAYLIST, "Elsewhere")) }
        assertTrue(waitFor(20_000) { (onMain { c.player.position } > 1_000).takeIf { it } } == true)
        instrumentation.runOnMainSync { c.player.seekTo(10_000); c.player.setPlaying(false) }
        Thread.sleep(500)
        val queueAt = onMain { c.player.position }

        val preview = onMain { MixPreview(c.scope) { item, fx -> c.player.previewPlayer(item, fx) } }
        val plan = PreviewPlan.of(Transition(25_000, 8_000, 4_000, TransitionStyle.STRAIGHT), 40_000, 40_000, 0.0, CrossfadeCurve.EQUAL_POWER)
        assertTrue(onMain { preview.play(songs[0].toMediaItem(), songs[1].toMediaItem(), plan) })
        assertTrue("Listen plays", waitFor(10_000) { (preview.phase.value == MixPreview.Phase.PLAYING).takeIf { it } } == true)
        val started = SystemClock.elapsedRealtime()
        assertTrue("and ends by itself", waitFor(30_000) { (preview.phase.value == MixPreview.Phase.IDLE).takeIf { it } } == true)
        val took = SystemClock.elapsedRealtime() - started
        assertTrue("6 s + 4 s + 6 s: $took ms", took in 14_000..20_000)

        // Stop lets go at once.
        assertTrue(onMain { preview.play(songs[0].toMediaItem(), songs[1].toMediaItem(), plan) })
        assertTrue(waitFor(10_000) { (preview.phase.value == MixPreview.Phase.PLAYING).takeIf { it } } == true)
        instrumentation.runOnMainSync { preview.stop() }
        assertEquals(MixPreview.Phase.IDLE, preview.phase.value)

        // The queue's song is where it was, still paused, and plays on from there.
        assertTrue(!c.player.isPlaying.value)
        assertTrue("queue at ${onMain { c.player.position }} vs $queueAt", abs(onMain { c.player.position } - queueAt) < 500)
        instrumentation.runOnMainSync { c.player.setPlaying(true) }
        assertTrue(waitFor(5_000) { c.player.isPlaying.value.takeIf { it } } == true)
        assertEquals(songs[2].id, c.player.currentSong.value?.id)
    }
}
