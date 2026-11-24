package com.prism.music

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prism.music.ui.tour.COACH_CAPTION_TAG
import com.prism.music.ui.tour.COACH_NEXT_TAG
import com.prism.music.ui.tour.COACH_SKIP_TAG
import com.prism.music.ui.tour.Coach
import com.prism.music.ui.tour.FIRST_STEPS_CLOSING_TAG
import com.prism.music.ui.tour.FIRST_STEPS_DONE_TAG
import com.prism.music.ui.tour.FirstSteps
import com.prism.music.ui.tour.TourAnchor
import com.prism.music.ui.tour.TourAnchors
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * First steps in the app (1.8.8, plan-tour.md §5): a new install is walked through the five steps by really doing them
 * (a Quick pick, the mini player, Lyrics, the heart, Settings → Looks) to the closing card; Skip at step 1 ends it for
 * good with nothing else up; Next at step 1 puts the first Quick pick in the mini player, paused (D12); at text size 2.0
 * the caption is on screen. Needs YouTube's Quick picks (network). Right to left is CoachTest's (Robolectric).
 */
@RunWith(AndroidJUnit4::class)
class FirstStepsAppTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val shots = ScreenshotOnFailure()

    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private fun shell(cmd: String) = automation.executeShellCommand(cmd).close()

    @After fun normal() {
        shell("settings put system font_scale 1.0")
        Coach.skip()
    }

    /** Set up as a listener would be, then First steps marked to come, as a new install's first Home has it. */
    private fun newInstall() {
        Fixture.ready()
        Fixture.context.getSharedPreferences("tour", android.content.Context.MODE_PRIVATE).edit()
            .putInt(FirstSteps.PREF, FirstSteps.DUE).commit()
    }

    private fun shows(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun awaitCaption(id: Int, ms: Long = 20_000) = compose.waitUntil(ms) { shows(tr(id)) }

    private fun boundsNow(anchor: TourAnchor): androidx.compose.ui.geometry.Rect? {
        var r: androidx.compose.ui.geometry.Rect? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { r = TourAnchors.boundsOf(anchor) }
        return r
    }

    /**
     * A tap on [anchor] where the coach's hole is (taps there reach the app). It never waits for the screen to settle:
     * with music playing it doesn't (the player's motion), so the place is read on the main thread and the tap goes in
     * through UiAutomator, at window pixels (the activity fills the screen).
     */
    private fun tap(anchor: TourAnchor, ms: Long = 15_000) {
        val until = System.currentTimeMillis() + ms
        // Drawn, and still for half a second (Home still loading above it moves it down).
        var b = boundsNow(anchor)
        var stillSince = System.currentTimeMillis()
        while (true) {
            Thread.sleep(100)
            val now = boundsNow(anchor)
            if (now == null || now != b) { b = now; stillSince = System.currentTimeMillis() }
            else if (System.currentTimeMillis() - stillSince >= 500) break
            assertTrue("$anchor never drawn and still", System.currentTimeMillis() < until)
        }
        b!!
        androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .click(b.center.x.toInt(), b.center.y.toInt())
    }

    /** [what] read on the main thread until true, never waiting for the screen to settle (it doesn't while music plays). */
    private fun until(ms: Long, why: String, what: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (true) {
            var ok = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync { ok = what() }
            if (ok) return
            assertTrue("never: $why", System.currentTimeMillis() < end)
            Thread.sleep(100)
        }
    }

    /** The coach's caption now (null when no tour runs). */
    private fun caption(): String? = Coach.run?.step?.caption

    private fun awaitStep(id: Int, ms: Long = 20_000) = until(ms, tr(id)) { caption() == tr(id) }

    /** The music paused, so the screen can settle for the closing checks. */
    private fun pause() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { if (Fixture.container.player.isPlaying.value) Fixture.container.player.togglePlay() }
        until(10_000, "paused") { !Fixture.container.player.isPlaying.value }
    }

    /** Live: step 1 is done only once the song really plays, and the recordings hold no audio (left out of normal runs). */
    @Live
    @Test fun aNewInstallIsWalkedThroughTheFiveStepsByDoingThem() {
        newInstall()
        Fixture.launch()
        awaitCaption(R.string.first_steps_play, 30_000)
        val started = System.currentTimeMillis()
        Fixture.screenshot("firststeps-1")
        // Live, Home's Quick picks can take a while to come.
        tap(TourAnchor.QUICK_PICK, 60_000)
        // From here music plays and the screen never settles: the steps are read from the coach itself.
        awaitStep(R.string.first_steps_player, 30_000)
        Fixture.screenshot("firststeps-2")
        tap(TourAnchor.MINI_PLAYER)
        until(20_000, "lyrics or like") { caption() == tr(R.string.first_steps_lyrics) || caption() == tr(R.string.first_steps_like) }
        if (Coach.run?.step?.caption == tr(R.string.first_steps_lyrics)) {
            Fixture.screenshot("firststeps-3")
            tap(TourAnchor.PLAYER_LYRICS)
        }
        awaitStep(R.string.first_steps_like)
        Fixture.screenshot("firststeps-4")
        tap(TourAnchor.PLAYER_LIKE)
        awaitStep(R.string.first_steps_looks)
        Fixture.screenshot("firststeps-5")
        tap(TourAnchor.SETTINGS_TAB)
        until(15_000, "the tour over") { Coach.run == null }
        android.util.Log.i("FirstStepsAppTest", "five steps in ${System.currentTimeMillis() - started} ms")
        pause()
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag(FIRST_STEPS_CLOSING_TAG)).fetchSemanticsNodes().isNotEmpty() }
        Fixture.screenshot("firststeps-closing")
        assertTrue(shows(tr(R.string.first_steps_closing)))
        compose.onNodeWithTag(FIRST_STEPS_DONE_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(FIRST_STEPS_CLOSING_TAG).assertDoesNotExist()
        assertFalse(FirstSteps.pending)
    }

    @Test fun skipAtStepOneEndsItForGood() {
        newInstall()
        Fixture.launch()
        awaitCaption(R.string.first_steps_play, 30_000)
        compose.onNodeWithTag(COACH_SKIP_TAG).performClick()
        compose.waitForIdle()
        assertNull(Coach.run)
        assertFalse(FirstSteps.pending)
        compose.onNodeWithTag(COACH_CAPTION_TAG).assertDoesNotExist()
        compose.onNodeWithTag(FIRST_STEPS_CLOSING_TAG).assertDoesNotExist()
        // Nothing else comes up over Home after it: no pager, no question.
        Thread.sleep(2_500)
        assertFalse(shows(tr(R.string.skip_tour)))
    }

    @Test fun nextAtStepOnePutsTheFirstPickInTheMiniPlayerPaused() {
        newInstall()
        Fixture.launch()
        awaitCaption(R.string.first_steps_play, 30_000)
        compose.waitUntil(12_000) { compose.onAllNodes(hasTestTag(COACH_NEXT_TAG)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(COACH_NEXT_TAG).performClick()
        awaitCaption(R.string.first_steps_player)
        compose.waitUntil(10_000) { Fixture.container.player.currentSong.value != null }
        assertNotNull(Fixture.container.player.currentSong.value)
        assertFalse("paused", Fixture.container.player.isPlaying.value)
        compose.waitUntil(5_000) { compose.runOnIdle { TourAnchors.boundsOf(TourAnchor.MINI_PLAYER) } != null }
    }

    @Test fun theCaptionIsOnScreenAtTextSizeTwo() {
        shell("settings put system font_scale 2.0")
        Thread.sleep(1_500)
        newInstall()
        Fixture.launch()
        awaitCaption(R.string.first_steps_play, 30_000)
        compose.onNodeWithTag(COACH_CAPTION_TAG).assertIsDisplayed()
        compose.onNodeWithTag(COACH_SKIP_TAG).assertIsDisplayed()
        Fixture.screenshot("firststeps-bigtext")
    }
}
