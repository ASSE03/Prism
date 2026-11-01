package com.prism.music

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prism.music.TvFixture.awaitFocusOn
import com.prism.music.data.db.LyricsEntity
import com.prism.music.playback.QueueKind
import com.prism.music.playback.QueueSource
import com.prism.music.ui.tv.TAG_HOME
import com.prism.music.ui.tv.TAG_LYRICS
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The TV lyrics' frames while lines are sung (1.8.0, the owner: "they should flow smoother"): not a pass or fail, a
 * measurement, so it runs only when asked (`-e tvperf 1`). Two silent songs with lyrics saved for them, one timed word
 * by word, one line by line; the lyrics page open and playing, `dumpsys gfxinfo` over [SECONDS] for each, in the
 * default look (Prism) and in Glow. The numbers go to logcat (PrismTvPerf).
 */
@TvTest
@RunWith(AndroidJUnit4::class)
class TvLyricsFramesProbe {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val tones = ArrayList<android.net.Uri>()
    private val player get() = Fixture.container.player

    @Before fun setUp() {
        assumeTrue("asked for (-e tvperf 1)", InstrumentationRegistry.getArguments().getString("tvperf") != null)
        TvFixture.assumeTv()
        Fixture.ready()
    }

    @After fun cleanUp() {
        runCatching { instrumentation.runOnMainSync { player.stop() } }
        tones.forEach { runCatching { Fixture.context.contentResolver.delete(it, null, null) } }
    }

    private fun shell(cmd: String): String =
        instrumentation.uiAutomation.executeShellCommand(cmd).use { java.io.FileInputStream(it.fileDescriptor).readBytes().toString(Charsets.UTF_8) }

    private fun measure(label: String): String {
        val pkg = Fixture.context.packageName
        shell("dumpsys gfxinfo $pkg reset")
        Thread.sleep(SECONDS * 1000L)
        val g = shell("dumpsys gfxinfo $pkg")
        fun num(name: String) = Regex("$name\\s*:\\s*([\\d.]+)ms").find(g)?.groupValues?.get(1) ?: "?"
        val frames = Regex("Total frames rendered:\\s*(\\d+)").find(g)?.groupValues?.get(1)?.toInt() ?: 0
        val janky = Regex("Janky frames:\\s*(\\d+)\\s*\\(([\\d.]+)%\\)").find(g)?.let { "${it.groupValues[1]} (${it.groupValues[2]}%)" } ?: "?"
        return "%-22s frames %4d  (%.1f fps)  p50 %s  p90 %s  p95 %s  janky %s".format(label, frames, frames / SECONDS.toFloat(), num("50th percentile"), num("90th percentile"), num("95th percentile"), janky)
    }

    @Test fun framesWhileLinesAreSung() {
        val perm = if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE
        instrumentation.uiAutomation.grantRuntimePermission(Fixture.context.packageName, perm)
        tones += Fixture.tone("Prism TV frames words", 180)
        tones += Fixture.tone("Prism TV frames lines", 180)
        val local = Fixture.container.localMusic
        runBlocking { local.scan() }
        val words = local.tracks.value.first { it.song.title == "Prism TV frames words" }.song
        val lines = local.tracks.value.first { it.song.title == "Prism TV frames lines" }.song
        // Made-up lines of five words, a line every 3 s, each word 0.5 s (QQ's word format, as stored).
        val qrc = (0 until 50).joinToString("\n") { i ->
            val t = 1_000L + i * 3_000L
            "[$t,2800]" + (0 until 5).joinToString("") { w -> "Word${w + 1} (${t + w * 500},500)" }
        }
        val lrc = (0 until 50).joinToString("\n") { i -> "[%02d:%02d.00]Line number ${i + 1} of the tone".format((1 + i * 3) / 60, (1 + i * 3) % 60) }
        runBlocking {
            Fixture.container.db.lyrics().put(LyricsEntity(words.id, "LRCLIB", true, "WORDS-QRC\n$qrc", System.currentTimeMillis()))
            Fixture.container.db.lyrics().put(LyricsEntity(lines.id, "LRCLIB", true, lrc, System.currentTimeMillis()))
        }
        instrumentation.runOnMainSync { player.playQueue(listOf(words, lines), 0, QueueSource(QueueKind.OTHER, "TV frames probe")) }
        val end0 = System.currentTimeMillis() + 20_000
        while (!player.isPlaying.value && System.currentTimeMillis() < end0) Thread.sleep(100)
        // Paused while the page is opened (the test framework waits for a still screen), then playing while measured.
        instrumentation.runOnMainSync { player.setPlaying(false) }
        TvFixture.launch()
        compose.awaitFocusOn(hasTestTag("$TAG_HOME/player"), 20_000)
        TvFixture.right()
        compose.awaitFocusOn(hasTestTag("$TAG_HOME/lyrics"))
        TvFixture.ok()
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Word1", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.awaitFocusOn(hasTestTag("$TAG_LYRICS/lines"), 10_000)
        val out = ArrayList<String>()
        fun run(label: String, song: Int) {
            instrumentation.runOnMainSync { player.skipTo(song); player.seekTo(4_000); player.setPlaying(true) }
            Thread.sleep(2_000)
            out += measure(label)
        }
        val settings = Fixture.container.settings
        for (style in listOf(com.prism.music.data.prefs.LyricsStyle.PRISM, com.prism.music.data.prefs.LyricsStyle.GLOW)) {
            settings.setLyricsStyle(style)
            Thread.sleep(1_000)
            run("${style.name} words", 0)
            run("${style.name} lines", 1)
        }
        instrumentation.runOnMainSync { player.setPlaying(false) }
        settings.setLyricsStyle(com.prism.music.data.prefs.LyricsStyle.PRISM)
        val report = "${android.os.Build.MODEL} (API ${android.os.Build.VERSION.SDK_INT})\n" + out.joinToString("\n")
        report.lines().forEach { android.util.Log.i("PrismTvPerf", it) }
    }

    private companion object { const val SECONDS = 15 }
}
