package com.prism.music

import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.prism.music.data.model.Song
import com.prism.music.playback.HistoryShuffleOrder
import com.prism.music.playback.QueueKind
import com.prism.music.playback.QueueSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 1.8.8 review, on the real [com.prism.music.playback.PlayerConnection] with a real player: with "Blend when I skip" on, a
 * new queue starts only after the song playing has faded out (200 ms). What's asked right after it (TvCast's
 * `playQueue(...)` then `afterPending { seekTo(pos) }`) waits for it, in order, instead of running on the old queue.
 */
@RunWith(RobolectricTestRunner::class)
class PlayerConnectionQueueHoldTest {
    private val app get() = ApplicationProvider.getApplicationContext<PrismApp>()
    private val c get() = app.container
    private lateinit var player: ExoPlayer

    private fun songs(n: Int, prefix: String) = (0 until n).map { Song("$prefix$it", "Song $it") }
    private val playlist = QueueSource(QueueKind.PLAYLIST, "A playlist", originId = "PLtest")

    @Before fun setUp() {
        runBlocking { c.settings.editNow { it.clear() } }
        runBlocking { withTimeout(10_000) { c.settings.flow.first { !it.behavior.keepShuffle } } }
        player = ExoPlayer.Builder(app).build()
        player.setShuffleOrder(HistoryShuffleOrder(IntArray(0)) { player.currentMediaItemIndex })
        c.player.testPlayer = player
    }

    @After fun tearDown() {
        c.player.testQueueFade = null
        c.player.testPlayer = null
        player.release()
    }

    /** Fails on the old code: the command ran at once, on the song fading out. */
    @Test fun commandsAfterAPostponedNewQueueWaitForIt() {
        c.player.playQueue(songs(3, "old"), 0, playlist)
        assertEquals("old0", player.currentMediaItem?.mediaId)
        var waiting: (() -> Unit)? = null
        c.player.testQueueFade = { waiting = it }
        c.player.playQueue(songs(3, "new"), 2, playlist)
        val seen = ArrayList<String?>()
        c.player.afterPending { seen += "first: " + player.currentMediaItem?.mediaId }
        c.player.afterPending { seen += "second: " + player.currentMediaItem?.mediaId }
        assertTrue("ran before the new queue started: $seen", seen.isEmpty())
        // The fade-out is over: the new queue, then what was asked after it, in order.
        waiting!!()
        assertEquals(listOf("first: new2", "second: new2"), seen)
        // Nothing is held any more.
        c.player.afterPending { seen += "after" }
        assertEquals("after", seen.last())
    }

    @Test fun aNewQueueOvertakingOneStillWaitingLetsWhatCameBetweenRunFirst() {
        c.player.playQueue(songs(3, "old"), 0, playlist)
        var waiting: (() -> Unit)? = null
        c.player.testQueueFade = { waiting = it }
        c.player.playQueue(songs(3, "a"), 1, playlist)
        val seen = ArrayList<String?>()
        c.player.afterPending { seen += player.currentMediaItem?.mediaId }
        // A second new queue before the first's fade-out is over (the service then starts it at once, the first dropped).
        c.player.testQueueFade = { it() }
        c.player.playQueue(songs(3, "b"), 0, playlist)
        assertEquals("what was asked between ran, before the newest queue", listOf<String?>("old0"), seen)
        assertEquals("b0", player.currentMediaItem?.mediaId)
        c.player.afterPending { seen += player.currentMediaItem?.mediaId }
        assertEquals(listOf<String?>("old0", "b0"), seen)
        assertTrue(waiting != null)
    }
}
