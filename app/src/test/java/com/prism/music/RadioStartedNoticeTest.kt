package com.prism.music

import com.prism.music.playback.QueueKind
import com.prism.music.playback.QueueSource
import com.prism.music.playback.RadioNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Reddit (1.8.8): pressing Radio gave only a small vibration. The Radio buttons now say "Radio started", once the
 * radio's first songs are in the queue: never for a radio that doesn't get going, nor for another queue.
 */
class RadioStartedNoticeTest {
    private fun radio(seed: String) = QueueSource(QueueKind.RADIO, "$seed Radio", seedId = seed)

    @Test fun saidOnceWhenTheRadiosSongsAreIn() {
        val n = RadioNotice()
        val r = radio("a")
        n.asked(r)
        assertFalse("nothing found yet (no connection): nothing said", n.take(r, 0))
        assertTrue(n.take(r, 25))
        assertFalse("the next top-up says nothing", n.take(r, 25))
    }

    @Test fun neverForAnotherQueue() {
        val n = RadioNotice()
        val r = radio("a")
        n.asked(r)
        // The same song's radio again, but another queue (an equal source isn't the one asked for).
        assertFalse(n.take(radio("a"), 25))
        assertFalse("Autoplay after an album", n.take(QueueSource(QueueKind.OTHER, "Album"), 25))
        assertTrue("still waiting for its own", n.take(r, 25))
    }

    @Test fun notAskedNothingSaid() {
        assertFalse(RadioNotice().take(radio("a"), 25))
    }

    @Test fun aNewerRadioTakesOver() {
        val n = RadioNotice()
        val first = radio("a"); val second = radio("b")
        n.asked(first); n.asked(second)
        assertFalse(n.take(first, 25))
        assertTrue(n.take(second, 25))
    }

    /** The three Radio buttons ask for it (the player's button, the player's ⋯ menu, a song's menu); Quick picks and the TV don't. */
    @Test fun theRadioButtonsAskForIt() {
        fun src(p: String) = File("src/main/java/com/prism/music/$p").readText()
        assertTrue(src("ui/player/PlayerControls.kt").contains("pc.playRadio(s, announce = true)"))
        assertTrue(src("ui/player/PlayerOptions.kt").contains("c.player.playRadio(song, announce = true)"))
        assertTrue(src("ui/components/Components.kt").contains("c.player.playRadio(song, announce = true)"))
        assertEquals("said where the radio's songs join the queue", 1, Regex("""radioNotice\.take\(src, answer\.add\.size\)""").findAll(src("playback/PlaybackService.kt")).count())
        assertTrue(src("playback/PlaybackService.kt").contains("c.player.reportError(tr(R.string.radio_started))"))
    }
}
