package com.prism.music

import android.app.Activity
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prism.music.data.prefs.LockLayout
import com.prism.music.data.prefs.LockPaused
import com.prism.music.playback.QueueKind
import com.prism.music.playback.QueueSource
import com.prism.music.ui.lockscreen.LockLayoutKey
import com.prism.music.ui.lockscreen.LockScreenActivity
import com.prism.music.ui.lockscreen.LockTags
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * The lock screen player, driven with a real song playing (a generated tone, always muted). A test phone may have no lock,
 * so [LockScreenActivity.pretendLocked] stands in for the keyguard; showing over a real one is checked by hand.
 */
@RunWith(AndroidJUnit4::class)
class LockScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val shots = ScreenshotOnFailure()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val settings get() = Fixture.container.settings
    private var tone: android.net.Uri? = null

    private fun shell(cmd: String) = instrumentation.uiAutomation.executeShellCommand(cmd).close()

    @Before fun setUp() {
        Fixture.ready()
        shell("cmd media_session volume --stream 3 --set 0")
    }

    @After fun cleanUp() {
        LockScreenActivity.pretendLocked = false
        runCatching { instrumentation.runOnMainSync { LockScreenActivity.live?.finish(); Fixture.container.player.stop() } }
        tone?.let { runCatching { Fixture.context.contentResolver.delete(it, null, null) } }
        settings.setLockScreen(false)
        settings.setLockScreenLayout(LockLayout.COVER)
        settings.setLockScreenPaused(LockPaused.TEN_MINUTES)
        settings.setLockScreenHideSong(false)
        settings.setPerformanceMode(false)
        shell("cmd media_session volume --stream 3 --set 0")
    }

    private fun playTone(title: String) {
        val perm = if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE
        instrumentation.uiAutomation.grantRuntimePermission(Fixture.context.packageName, perm)
        tone = Fixture.tone(title, 120)
        val local = Fixture.container.localMusic
        runBlocking { local.scan() }
        val song = local.tracks.value.first { it.song.title == title }.song
        instrumentation.runOnMainSync { Fixture.container.player.playQueue(listOf(song), 0, QueueSource(QueueKind.OTHER, "Lock screen test")) }
    }

    private fun startLockScreen() {
        Fixture.context.startActivity(Intent(Fixture.context, LockScreenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun shown() = runCatching { compose.onAllNodes(hasTestTag(LockTags.ROOT), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)

    private fun layoutShown(): LockLayout? = runCatching {
        compose.onAllNodes(hasTestTag(LockTags.ROOT), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(LockLayoutKey)
    }.getOrNull()

    private fun tagged(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitUntilShown() = compose.waitUntil(20_000) { shown() }

    /** Waits for the activity to be gone, however it went. */
    private fun waitUntilClosed(timeoutMs: Long = 10_000) {
        val end = System.currentTimeMillis() + timeoutMs
        while (LockScreenActivity.live != null && System.currentTimeMillis() < end) Thread.sleep(100)
        assertEquals("the lock screen should have closed", null, LockScreenActivity.live)
    }

    @Test fun switchedOffItNeverShows() {
        assertFalse("off by default", settings.current.behavior.lockScreen)
        val monitor = instrumentation.addMonitor(LockScreenActivity::class.java.name, null, false)
        try {
            startLockScreen()
            val up: Activity? = monitor.waitForActivityWithTimeout(10_000)
            assertTrue("the activity didn't even start", up != null)
            val end = System.currentTimeMillis() + 5_000
            while (up?.isDestroyed == false && System.currentTimeMillis() < end) Thread.sleep(100)
            assertTrue("it stayed up with the setting off", up!!.isFinishing || up.isDestroyed)
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test fun onAnUnlockedPhoneItCloses() {
        // The lock delay: the phone turned on, but never locked.
        settings.setLockScreen(true)
        val monitor = instrumentation.addMonitor(LockScreenActivity::class.java.name, null, false)
        try {
            playTone("Prism lock delay tone")
            startLockScreen()
            val up = monitor.waitForActivityWithTimeout(10_000)
            val end = System.currentTimeMillis() + 5_000
            while (up?.isDestroyed == false && System.currentTimeMillis() < end) Thread.sleep(100)
            val locked = Fixture.context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
            if (!locked) assertTrue("it stayed up on an unlocked phone", up!!.isFinishing || up.isDestroyed)
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test fun showsWhatsPlayingAndNothingElseCanBeTouched() {
        LockScreenActivity.pretendLocked = true
        settings.setLockScreen(true)
        playTone("Prism lock tone")
        startLockScreen()
        waitUntilShown()
        for (layout in LockLayout.entries) {
            settings.setLockScreenLayout(layout)
            compose.waitUntil(5_000) { layoutShown() == layout }
            compose.waitUntil(5_000) { compose.onAllNodes(hasText("Prism lock tone", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            val touchable = SemanticsMatcher("takes a touch") { node ->
                listOf(SemanticsActions.OnClick, SemanticsActions.OnLongClick, SemanticsActions.SetProgress, SemanticsActions.ScrollBy, SemanticsActions.SetText)
                    .any { it in node.config }
            }
            val nodes = compose.onAllNodes(touchable, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue("$layout: nothing can be touched?", nodes.isNotEmpty())
            for (n in nodes) {
                val tag = n.config.getOrNull(SemanticsProperties.TestTag)
                assertTrue("$layout: something else can be touched: tag=$tag ${n.config}", tag in LockTags.touchable)
            }
            // Controls only: nothing leads anywhere else, however it might be worded.
            listOf(R.string.settings, R.string.search, R.string.library).forEach { id ->
                assertEquals(0, compose.onAllNodes(hasText(Fixture.context.getString(id)), useUnmergedTree = true).fetchSemanticsNodes().size)
            }
            Fixture.screenshot("lockscreen-${layout.name.lowercase()}")
        }
    }

    @Test fun playPauseAndSeekControlThePlayer() {
        LockScreenActivity.pretendLocked = true
        settings.setLockScreen(true)
        playTone("Prism lock control tone")
        startLockScreen()
        waitUntilShown()
        val player = Fixture.container.player
        compose.waitUntil(20_000) { player.isPlaying.value }

        compose.onAllNodes(hasTestTag(LockTags.PLAY), useUnmergedTree = true).onFirst().performClick()
        compose.waitUntil(5_000) { !player.isPlaying.value }
        compose.onAllNodes(hasTestTag(LockTags.PLAY), useUnmergedTree = true).onFirst().performClick()
        compose.waitUntil(5_000) { player.isPlaying.value }

        // The player only answers on the main thread.
        fun <T> onMain(read: () -> T): T {
            var value: T? = null
            instrumentation.runOnMainSync { value = read() }
            @Suppress("UNCHECKED_CAST")
            return value as T
        }
        compose.waitUntil(5_000) { onMain { player.duration } > 0 }
        compose.onAllNodes(hasTestTag(LockTags.SEEK), useUnmergedTree = true).onFirst()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        val half = onMain { player.duration } / 2
        compose.waitUntil(5_000) { abs(onMain { player.position } - half) < 6_000 }
    }

    @Test fun performanceModeIsQuiet() {
        LockScreenActivity.pretendLocked = true
        settings.setLockScreen(true)
        settings.setLockScreenLayout(LockLayout.FULL)
        playTone("Prism lock quiet tone")
        settings.setPerformanceMode(true)
        startLockScreen()
        waitUntilShown()
        compose.waitUntil(5_000) { layoutShown() == LockLayout.QUIET }
        settings.setPerformanceMode(false)
        compose.waitUntil(5_000) { layoutShown() == LockLayout.FULL }
    }

    @Test fun aHiddenSongShowsNoName() {
        LockScreenActivity.pretendLocked = true
        settings.setLockScreen(true)
        settings.setLockScreenHideSong(true)
        playTone("Prism secret tone")
        startLockScreen()
        waitUntilShown()
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodes(hasText("Prism secret tone", substring = true), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertFalse(tagged(LockTags.LIKE))
        assertTrue(tagged(LockTags.PLAY))
    }

    @Test fun swipingUpLeaves() {
        val secure = Fixture.context.getSystemService(android.app.KeyguardManager::class.java).isDeviceSecure
        org.junit.Assume.assumeFalse("needs the phone's own code", secure)
        LockScreenActivity.pretendLocked = true
        settings.setLockScreen(true)
        playTone("Prism lock swipe tone")
        startLockScreen()
        waitUntilShown()
        compose.onAllNodes(hasTestTag(LockTags.ROOT), useUnmergedTree = true).onFirst().performTouchInput { swipeUp(durationMillis = 200) }
        waitUntilClosed()
        assertTrue("music keeps playing", Fixture.container.player.isPlaying.value || Fixture.container.player.buffering.value)
    }

    @Test fun tappingTheCoverOpensThePlayer() {
        val secure = Fixture.context.getSystemService(android.app.KeyguardManager::class.java).isDeviceSecure
        org.junit.Assume.assumeFalse("needs the phone's own code", secure)
        LockScreenActivity.pretendLocked = true
        settings.setLockScreen(true)
        playTone("Prism lock cover tone")
        val monitor = instrumentation.addMonitor(PrismActivity::class.java.name, null, false)
        try {
            startLockScreen()
            waitUntilShown()
            compose.onAllNodes(hasTestTag(LockTags.COVER), useUnmergedTree = true).onFirst().performClick()
            waitUntilClosed()
            assertTrue("Prism didn't open", monitor.waitForActivityWithTimeout(15_000) != null)
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }
}
