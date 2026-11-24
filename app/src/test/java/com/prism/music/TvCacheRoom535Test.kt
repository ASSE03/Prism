package com.prism.music

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.core.app.ApplicationProvider
import com.prism.music.data.TvCacheRoom
import com.prism.music.download.SmartCacheEvictor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * #535: on a TV box (Onn 4K, Chromecast with Google TV: 8 GB in all) the song cache kept the phone's 5 GB and filled
 * the box. On a TV it now takes half of what is free above 1 GB, at most 1 GB, at least 128 MB where that much is
 * spare, and never into the 1 GB it leaves free.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TvCacheRoom535Test {
    private val mb = 1_048_576L

    @Test fun theSongCacheFitsTheBox() {
        // Plenty free (a box fresh from the shop, ~5 GB free): 1 GB, not the phone's 5 GB.
        assertEquals(1024 * mb, TvCacheRoom.songCache(freeBytes = 5 * 1024 * mb, usedBytes = 0))
        // 2 GB free, nothing kept yet: half of the 1 GB above what stays free.
        assertEquals(512 * mb, TvCacheRoom.songCache(freeBytes = 2048 * mb, usedBytes = 0))
        // The same box after the cache took 300 MB of it: the room doesn't move (free + kept is the same).
        assertEquals(512 * mb, TvCacheRoom.songCache(freeBytes = 1748 * mb, usedBytes = 300 * mb))
        // A little to spare: the smallest room, while it fits.
        assertEquals(128 * mb, TvCacheRoom.songCache(freeBytes = 1200 * mb, usedBytes = 0))
        // Less than the smallest room to spare: only what is spare, never into the 1 GB kept free.
        assertEquals(76 * mb, TvCacheRoom.songCache(freeBytes = 1100 * mb, usedBytes = 0))
        // Under 1 GB free (#535's box): no room at all (the songs still stream), and what the cache holds goes.
        assertEquals(0L, TvCacheRoom.songCache(freeBytes = 600 * mb, usedBytes = 0))
        assertEquals(0L, TvCacheRoom.songCache(freeBytes = 600 * mb, usedBytes = 128 * mb))
        assertEquals(0L, TvCacheRoom.songCache(freeBytes = 0, usedBytes = 0))
        // The free space not known yet: the smallest room.
        assertEquals(128 * mb, TvCacheRoom.songCache(freeBytes = -1, usedBytes = 0))
        // The other caches' rooms on a TV are smaller than a phone's (400 MB for Canvas, up to 250 MB for pictures).
        assertTrue(TvCacheRoom.CANVAS < 400 * mb && TvCacheRoom.IMAGES < 250 * mb)
    }

    @Test fun theFreeSpaceIsReadOnlyNowAndThen() {
        var now = 0L
        var reads = 0
        val free = TvCacheRoom.FreeSpace(everyMs = 10_000, now = { now }) { reads++; 42L }
        repeat(100) { assertEquals(42L, free()) }
        assertEquals(1, reads)
        now = 10_000
        free()
        assertEquals(2, reads)
    }

    /** `usableSpace` answers 0 when it can't read the space: that isn't a full box, the last good read stands. */
    @Test fun aFailedReadKeepsTheLastGoodOne() {
        var now = 0L
        var answer = 5 * 1024 * mb
        val free = TvCacheRoom.FreeSpace(everyMs = 10_000, now = { now }) { answer }
        assertEquals(5 * 1024 * mb, free())
        answer = 0
        now = 10_000
        assertEquals(5 * 1024 * mb, free())
        assertEquals(1024 * mb, TvCacheRoom.songCache(free(), 0))
        now = 20_000
        val failing = TvCacheRoom.FreeSpace(everyMs = 10_000, now = { now }) { error("no") }
        assertEquals("unknown before any good read", -1L, failing())
        assertEquals(TvCacheRoom.SONG_MIN, TvCacheRoom.songCache(failing(), 0))
        val zero = TvCacheRoom.FreeSpace(everyMs = 10_000, now = { now }) { 0L }
        assertEquals(-1L, zero())
    }

    @Test fun theEvictorKeepsToTheRoomWhateverTheSpaceSays() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "tv-room-${System.nanoTime()}")
        // Scaled down 1024 times: Smart Downloads' 5 GB ("5 MB" here), but the box can spare "300 MB" (300 KB).
        val total = 5 * 1024 * 1024L
        val room = 300 * 1024L
        var asked = -1L
        val evictor = SmartCacheEvictor({ total }, { false }, { total }, roomCap = { used -> asked = used; room })
        val cache = SimpleCache(dir, evictor, StandaloneDatabaseProvider(context))
        try {
            for (i in 0 until 10) {
                val len = 100 * 1024L
                val hole = cache.startReadWrite("song$i", 0, len)
                val f = cache.startFile("song$i", 0, len)
                f.writeBytes(ByteArray(len.toInt()))
                cache.commitFile(f, len)
                cache.releaseHoleSpan(hole)
            }
            assertTrue("within the box's room: ${cache.cacheSpace}", cache.cacheSpace <= room)
            assertTrue("the newest stay", cache.keys.contains("song9"))
            assertTrue("asked with what it holds", asked >= 0)
        } finally {
            cache.release()
            dir.deleteRecursively()
        }
    }

    @Test fun aPhoneKeepsItsRoom() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "phone-room-${System.nanoTime()}")
        val total = 5 * 1024 * 1024L
        val cache = SimpleCache(dir, SmartCacheEvictor({ total }, { false }, { total }), StandaloneDatabaseProvider(context))
        try {
            for (i in 0 until 10) {
                val len = 100 * 1024L
                val hole = cache.startReadWrite("song$i", 0, len)
                val f = cache.startFile("song$i", 0, len)
                f.writeBytes(ByteArray(len.toInt()))
                cache.commitFile(f, len)
                cache.releaseHoleSpan(hole)
            }
            assertEquals(1000 * 1024L, cache.cacheSpace)
        } finally {
            cache.release()
            dir.deleteRecursively()
        }
    }
}
