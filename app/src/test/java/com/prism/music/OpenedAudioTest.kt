package com.prism.music

import android.content.Intent
import android.net.Uri
import com.prism.music.data.local.OpenedAudio
import com.prism.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Open with Prism" (Reddit, 1.8.5): an audio file opened from a file manager plays in Prism as a one-song queue, from
 * the file itself (no copy), named by its tags when it has them, else by its file name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class OpenedAudioTest {
    private val content = Uri.parse("content://com.example.files/document/primary%3AMusic%2Fsong.flac")

    @Test fun takesAudioFilesOpenedWithPrism() {
        assertTrue(OpenedAudio.accepts(Intent.ACTION_VIEW, content, "audio/flac"))
        assertTrue(OpenedAudio.accepts(Intent.ACTION_VIEW, content, "audio/mpeg"))
        assertTrue(OpenedAudio.accepts(Intent.ACTION_VIEW, content, "application/ogg"))
        assertTrue(OpenedAudio.accepts(Intent.ACTION_VIEW, content, "application/x-flac"))
        assertTrue(OpenedAudio.accepts(Intent.ACTION_VIEW, Uri.parse("file:///sdcard/Music/a.mp3"), "audio/mpeg"))
    }

    @Test fun leavesOtherIntentsAlone() {
        // A Look (a zip), a YouTube Music link, a prism:// link, a share: not audio files.
        assertFalse(OpenedAudio.accepts(Intent.ACTION_VIEW, content, "application/zip"))
        assertFalse(OpenedAudio.accepts(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/watch?v=abc"), null))
        assertFalse(OpenedAudio.accepts(Intent.ACTION_VIEW, Uri.parse("prism://player"), null))
        assertFalse(OpenedAudio.accepts(Intent.ACTION_SEND, content, "audio/mpeg"))
        assertFalse(OpenedAudio.accepts(Intent.ACTION_VIEW, null, "audio/mpeg"))
        assertFalse(OpenedAudio.accepts(Intent.ACTION_VIEW, Uri.parse("https://example.com/a.mp3"), "audio/mpeg"))
    }

    @Test fun songFromTags() {
        val s = OpenedAudio.song(content, displayName = "song.flac", title = "Real Title", artist = "A; B", album = "The Album", durationMs = 185_400)
        assertTrue(s.isLocal)
        assertEquals("Real Title", s.title)
        assertEquals(listOf("A", "B"), s.artists.map { it.name })
        assertEquals("The Album", s.album?.title)
        assertEquals(185, s.durationSec)
        // It plays from the file it came from.
        assertEquals(content, OpenedAudio.uriOf(s.id))
    }

    @Test fun untaggedFileIsNamedByItsFileName() {
        val s = OpenedAudio.song(content, displayName = "My Demo.mp3", title = "  ", artist = null, album = null, durationMs = null)
        assertEquals("My Demo", s.title)
        assertTrue(s.artists.isEmpty())
        assertNull(s.album)
        assertEquals(0, s.durationSec)
        // No name at all: the path's last part.
        val f = Uri.parse("file:///sdcard/Music/track01.ogg")
        assertEquals("track01", OpenedAudio.song(f, null, null, null, null, null).title)
    }

    @Test fun mediaStoreIdsAreNotOpenedFiles() {
        assertNull(OpenedAudio.uriOf(Song.LOCAL_PREFIX + "1234"))
        assertNull(OpenedAudio.uriOf("dQw4w9WgXcQ"))
    }
}
