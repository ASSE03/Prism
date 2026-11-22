package com.prism.music

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prism.music.data.innertube.SearchFilter
import com.prism.music.data.model.Song
import com.prism.music.data.model.SongItem
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Automix's listening round (plan-mix.md §3.4), the phone's half: the owner's ten pairs found by search and fetched whole
 * with Prism's own stream resolver, the files as they are (no decoding here), into /sdcard/prism-tests/listening/ with a
 * `songs.tsv` naming each video id used. The PC half (`MixListeningRender`, a unit test) decodes, scans and renders the
 * moves. Not part of the suite (live; plays nothing). Run it with
 * `-Pandroid.testInstrumentationRunnerArguments.class=com.prism.music.MixListeningFetch` `record=true` and
 * `notAnnotation=org.junit.Ignore`, then `adb pull /sdcard/prism-tests/listening`.
 */
@RunWith(AndroidJUnit4::class)
@Live
class MixListeningFetch {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun fetchTheTenPairs() {
        Fixture.ready()
        val c = Fixture.container
        shell("mkdir -p $OUT")
        val lines = StringBuilder("n\tside\ttitle\tartist\tvideoId\tfoundTitle\tfoundArtist\tmime\tbytes\tfile\n")
        for ((n, pair) in PAIRS.withIndex()) for ((side, want) in listOf("a" to pair.first, "b" to pair.second)) {
            val (title, artist) = want
            val tag = "%02d%s".format(n + 1, side)
            val result = runCatching {
                val song = runBlocking { find(c, title, artist) } ?: error("not found")
                val s = c.streams.resolve(song.id)
                val ext = if (s.mimeType.contains("webm")) "webm" else "m4a"
                val file = "$tag-${song.id}.$ext"
                val bytes = c.http.newCall(Request.Builder().url(s.audioUrl).apply {
                    if (s.contentLength > 0) header("Range", "bytes=0-${s.contentLength - 1}")
                }.build()).execute().use { r ->
                    check(r.isSuccessful) { "HTTP ${r.code}" }
                    r.body.bytes()
                }
                write("$OUT/$file", bytes)
                android.util.Log.i(TAG, "$tag $title / $artist -> ${song.id} (${song.title} / ${song.artistText}), ${s.mimeType} ${s.bitrate} kbps, ${bytes.size} bytes")
                listOf(song.id, song.title, song.artistText, s.mimeType, bytes.size.toString(), file)
            }.getOrElse { e ->
                android.util.Log.w(TAG, "$tag $title / $artist failed: $e")
                listOf("", "", "", "", "0", "FAILED: ${e.message}")
            }
            lines.append(listOf("${n + 1}", side, title, artist).plus(result).joinToString("\t")).append('\n')
        }
        write("$OUT/songs.tsv", lines.toString().encodeToByteArray())
    }

    /** The search's best song for [title] by [artist]: one whose title and artist both match, else the first song. */
    private suspend fun find(c: AppContainer, title: String, artist: String): Song? {
        val found = c.ytm.search("$title $artist", SearchFilter.SONGS).items.filterIsInstance<SongItem>().map { it.song }
        fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        val whole = norm(title.substringBefore(" (feat"))
        val base = norm(title.substringBefore(" ("))
        val a = norm(artist)
        return found.firstOrNull { norm(it.title).startsWith(whole) && norm(it.artistText).contains(a) }
            ?: found.firstOrNull { norm(it.title).startsWith(base) && norm(it.artistText).contains(a) }
            ?: found.firstOrNull { norm(it.title).contains(base) }
            ?: found.firstOrNull()
    }

    private fun shell(cmd: String) = instrumentation.uiAutomation.executeShellCommand(cmd).close()

    /** To /sdcard through the test's shell (Prism's own files are cleared after the test), as [RecordedNetwork] does. */
    private fun write(path: String, bytes: ByteArray) {
        val (out, input) = instrumentation.uiAutomation.executeShellCommandRw("sh -c cat>$path")
        android.os.ParcelFileDescriptor.AutoCloseOutputStream(input).use { it.write(bytes) }
        android.os.ParcelFileDescriptor.AutoCloseInputStream(out).use { it.readBytes() }
    }

    companion object {
        private const val TAG = "PrismListen"
        const val OUT = "/sdcard/prism-tests/listening"

        /** plan-mix.md §3.4: the owner's ten pairs (ending song, next song), each as (title, artist). */
        val PAIRS = listOf(
            ("Man Of The Year" to "Dance Gavin Dance") to ("Kool-Aid" to "Bring Me The Horizon"),
            ("Objects in the Mirror" to "Mac Miller") to ("Coming Up The Driveway" to "Cuco"),
            ("I'm Low On Gas And You Need A Jacket" to "Pierce The Veil") to ("You Be Tails, I'll Be Sonic" to "A Day To Remember"),
            ("Doomed" to "Bring Me The Horizon") to ("Evaporate" to "Dance Gavin Dance"),
            ("Para Ti" to "Cuco") to ("Tommy's Party" to "Peach Pit"),
            ("The Isnis" to "Chelsea Grin") to ("Hive Mind (feat. Denzel Curry)" to "Knocked Loose"),
            ("Simple Man" to "Lynyrd Skynyrd") to ("Wonderwall" to "Oasis"),
            ("Buttons" to "Mac Miller") to ("Sentimental (Live at Hangar 30)" to "SYML"),
            ("My Life For Hire" to "A Day To Remember") to ("The Reality" to "Memphis May Fire"),
            ("Avalanche" to "Bring Me The Horizon") to ("Blue Dream" to "Dance Gavin Dance"),
        )
    }
}
