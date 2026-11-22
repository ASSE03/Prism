package com.prism.music

import androidx.media3.datasource.cache.SimpleCache
import androidx.test.core.app.ApplicationProvider
import com.prism.music.data.db.SongEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * #535: "Clear cache" (TV Settings → Storage) empties the caches Prism fetches again by itself (songs kept from
 * streaming, pictures, recently played loops, pictures made for sharing) and leaves downloads, saved loops, likes,
 * history and settings as they were. Before it, the only way out of a full TV box was wiping all of Prism's data.
 */
@RunWith(RobolectricTestRunner::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ClearCache535Test {
    private fun SimpleCache.put(key: String, len: Long) {
        val hole = startReadWrite(key, 0, len)
        val f = startFile(key, 0, len)
        f.writeBytes(ByteArray(len.toInt()) { 7 })
        commitFile(f, len)
        releaseHoleSpan(hole)
    }

    @Test fun clearingTakesTheCachesAndLeavesTheRest() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<PrismApp>()
        val c = app.container

        // What stays: a download, a saved loop, a liked song, a setting.
        c.downloadCache.put("downloaded-song", 64 * 1024)
        val savedLoop = File(app.filesDir, "canvas_clips/savedVid.720.136.0-8000.mp4").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(4096)) }
        c.db.songs().upsert(SongEntity("liked1", "A liked song", "Artist", null, null, null, 200, null, liked = true, likedAt = 1))
        val normalize = !c.settings.current.normalize
        c.settings.setNormalize(normalize)
        withTimeout(10_000) { c.settings.flow.first { it.normalize == normalize } }

        // What goes: a song kept from streaming, a picture, a recently played loop, a shared picture.
        c.playerCache.put("streamed-song", 200 * 1024)
        val images = coil3.SingletonImageLoader.get(app).diskCache!!
        images.openEditor("https://example.com/cover.jpg")!!.let { e ->
            images.fileSystem.write(e.data) { write(ByteArray(32 * 1024)) }
            e.commit()
        }
        val recentLoop = File(app.cacheDir, "canvas_clips/recentVid.360.18.0-8000.mp4").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(16 * 1024)) }
        val shared = File(app.cacheDir, "share/card.png").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(8 * 1024)) }

        val before = c.caches.bytes()
        assertTrue("the caches hold what was put in them: $before", before >= (200 + 32 + 16 + 8) * 1024L)

        val freed = c.caches.clear()

        assertEquals("everything they held is freed", before, freed)
        assertEquals(0L, c.caches.bytes())
        assertTrue("the streamed song is gone", c.playerCache.keys.isEmpty())
        assertEquals(0L, images.size)
        assertFalse(recentLoop.exists())
        assertFalse(shared.exists())

        assertTrue("the download stays", "downloaded-song" in c.downloadCache.keys)
        assertEquals(64 * 1024L, c.downloadCache.cacheSpace)
        assertTrue("the saved loop stays", savedLoop.isFile && savedLoop.length() == 4096L)
        assertEquals(true, c.db.songs().get("liked1")?.liked)
        assertEquals(normalize, c.settings.current.normalize)
    }
}
