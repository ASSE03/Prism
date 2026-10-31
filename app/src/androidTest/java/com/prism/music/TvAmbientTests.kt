package com.prism.music

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prism.music.TvFixture.awaitFocusOn
import com.prism.music.playback.QueueKind
import com.prism.music.playback.QueueSource
import com.prism.music.ui.tv.TAG_AMBIENT
import com.prism.music.ui.tv.TAG_HOME
import com.prism.music.ui.tv.TAG_PLAYER
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prism TV, slice 7: after a quiet minute while music plays the ambient screen comes (not the TV's screensaver over the
 * lyrics); the first key only brings back what was there, it doesn't press the button under it.
 */
@TvTest
@RunWith(AndroidJUnit4::class)
class TvAmbientTests {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val shots = ScreenshotOnFailure()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var tone: android.net.Uri? = null

    @Before fun setUp() {
        TvFixture.assumeTv()
        Fixture.ready()
    }

    @After fun cleanUp() {
        runCatching { instrumentation.runOnMainSync { Fixture.container.player.stop() } }
        tone?.let { runCatching { Fixture.context.contentResolver.delete(it, null, null) } }
        Fixture.container.settings.setTvAmbientMinutes(3)
    }

    private fun shown() = compose.onAllNodes(hasTestTag(TAG_AMBIENT), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test fun aQuietMinuteBringsTheAmbientScreenAndAKeyOnlyWakesIt() {
        Fixture.container.settings.setTvAmbientMinutes(1)
        val perm = if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE
        instrumentation.uiAutomation.grantRuntimePermission(Fixture.context.packageName, perm)
        tone = Fixture.tone("Prism TV ambient tone", 150)
        val local = Fixture.container.localMusic
        runBlocking { local.scan() }
        val song = local.tracks.value.first { it.song.title == "Prism TV ambient tone" }.song
        val player = Fixture.container.player
        instrumentation.runOnMainSync { player.playQueue(listOf(song), 0, QueueSource(QueueKind.OTHER, "TV ambient test")) }
        TvFixture.launch()
        compose.awaitFocusOn(hasTestTag("$TAG_HOME/player"), 20_000)
        assertTrue("not before a quiet minute", !shown())
        compose.waitUntil(90_000) { shown() }
        // OK: back to Home, with the same button focused, and it wasn't pressed (the player didn't open).
        TvFixture.ok()
        compose.waitUntil(5_000) { !shown() }
        compose.awaitFocusOn(hasTestTag("$TAG_HOME/player"))
        Thread.sleep(500)
        assertTrue("the key opened the player", compose.onAllNodes(hasTestTag(TAG_PLAYER), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertTrue(player.isPlaying.value)
        // Paused, it doesn't come back.
        instrumentation.runOnMainSync { player.setPlaying(false) }
        Thread.sleep(70_000)
        assertTrue("came while paused", !shown())
    }
}
