package com.prism.music.data

import com.prism.music.R
import com.prism.music.tr
import java.io.File

/**
 * #535: the caches Prism keeps, and emptying them all at once (TV Settings → Storage → Clear cache). Only what Prism
 * fetches again by itself: songs kept from streaming, pictures, recently played animated covers and their loops, and
 * pictures made for sharing. Never downloads (Smart Downloads' picks included), saved animated covers, likes,
 * history, settings or the sign-in: those live elsewhere ([com.prism.music.AppContainer.downloadCache], `filesDir`,
 * the database, DataStore) and no part here reaches them.
 */
class AppCaches(private val parts: List<Part>) {
    /** One cache: what it holds now, and how to empty it (through its own owner, never by deleting its folder under it). */
    class Part(val name: String, val bytes: () -> Long, val clear: () -> Unit)

    /** What the caches hold now, in bytes. */
    fun bytes(): Long = parts.sumOf { runCatching { it.bytes() }.getOrDefault(0L).coerceAtLeast(0L) }

    /** Empties every cache (one that fails doesn't stop the rest); the bytes freed. */
    fun clear(): Long {
        val before = bytes()
        parts.forEach { runCatching { it.clear() } }
        return (before - bytes()).coerceAtLeast(0L)
    }

    companion object {
        fun dirBytes(dir: File): Long = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        fun emptyDir(dir: File) { dir.listFiles()?.forEach { it.deleteRecursively() } }

        /** "812 MB", "1.4 GB". */
        fun size(bytes: Long): String =
            if (bytes < 1_073_741_824L) tr(R.string.t_0f_mb).format(bytes / 1_048_576.0) else tr(R.string.t_1f_gb).format(bytes / 1_073_741_824.0)
    }
}

/**
 * #535: the caches' room on a TV box. Boxes like the Onn 4K and Chromecast with Google TV have 8 GB in all, and the
 * phone's 5 GB song cache filled them (the only way out was wiping Prism's data). On a TV the song cache takes half of
 * what is free above [KEEP_FREE], at most [SONG_MAX] and never under [SONG_MIN]; the other caches have smaller fixed
 * rooms. A phone keeps its own rooms.
 */
object TvCacheRoom {
    private const val MB = 1_048_576L
    const val SONG_MAX = 1024 * MB
    const val SONG_MIN = 128 * MB
    /** What the song cache always leaves free on the box, for the system and other apps. */
    const val KEEP_FREE = 1024 * MB
    /** Recently played animated covers and loops together (a phone: 400 MB); TV's player doesn't show them. */
    const val CANVAS = 128 * MB
    /** Pictures (Coil's disk cache; a phone: 2% of the storage, up to 250 MB). */
    const val IMAGES = 96 * MB

    /**
     * The song cache's room, from what the box has [freeBytes] and what the cache already holds [usedBytes] (the two
     * together don't change as the cache trims, so neither does the room).
     */
    fun songCache(freeBytes: Long, usedBytes: Long): Long =
        ((freeBytes + usedBytes - KEEP_FREE) / 2).coerceIn(SONG_MIN, SONG_MAX)

    /** The free space, read at most every [everyMs] (the evictor asks at every piece of a song it saves). */
    class FreeSpace(private val everyMs: Long = 10_000, private val now: () -> Long = System::currentTimeMillis, private val read: () -> Long) {
        @Volatile private var at = Long.MIN_VALUE
        @Volatile private var last = 0L
        operator fun invoke(): Long {
            val t = now()
            if (at == Long.MIN_VALUE || t - at >= everyMs) { last = runCatching(read).getOrDefault(last); at = t }
            return last
        }
    }
}
