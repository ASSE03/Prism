package com.prism.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.prism.music.data.prefs.AppSettings
import com.prism.music.ui.theme.PrismTheme
import com.prism.music.ui.tour.COACH_CAPTION_TAG
import com.prism.music.ui.tour.COACH_NEXT_TAG
import com.prism.music.ui.tour.COACH_SKIP_TAG
import com.prism.music.ui.tour.COACH_TAG
import com.prism.music.ui.tour.Coach
import com.prism.music.ui.tour.CoachEnd
import com.prism.music.ui.tour.CoachLayer
import com.prism.music.ui.tour.CoachStep
import com.prism.music.ui.tour.TourAnchor
import com.prism.music.ui.tour.TourAnchors
import com.prism.music.ui.tour.TourState
import com.prism.music.ui.tour.captionTop
import com.prism.music.ui.tour.tourAnchor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The coach (1.8.8, plan-tour.md §3 A and §5): a step ends on the app's state, never on a tap alone; it never traps
 * anyone (Next after 2 s without the control, after 8 s without a touch; Skip from every step leaves nothing); a step
 * to be passed over is; taps outside the hole do nothing; the caption stays on screen at text size 2.0, right to left;
 * the anchors record nothing while no tour runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp", application = android.app.Application::class)
class CoachTest {
    @get:Rule val compose = createComposeRule()

    private var state by mutableStateOf(TourState())
    private var targetTaps = 0
    private var outsideTaps = 0

    @Before @After fun clean() = Coach.skip()

    private fun step(
        caption: String, anchor: TourAnchor? = TourAnchor.MINI_PLAYER,
        passOver: (TourState) -> Boolean = { false }, done: (TourState) -> Boolean = { false },
    ) = CoachStep(anchor, caption, done, passOver)

    /** A stand-in app: the mini player's anchor in the middle (or at [targetAt]), something else to tap up top. */
    @Composable private fun App(targetAt: Alignment = Alignment.Center) {
        PrismTheme(AppSettings(), artworkColor = null) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(targetAt).padding(vertical = 24.dp).size(120.dp, 56.dp).tourAnchor(TourAnchor.MINI_PLAYER).testTag("target").clickable { targetTaps++ })
                Box(Modifier.align(Alignment.TopStart).padding(top = 120.dp, start = 20.dp).size(80.dp, 48.dp).testTag("outside").clickable { outsideTaps++ })
                Coach.run?.let { CoachLayer(it, state, reduceMotion = true) }
            }
        }
    }

    private fun index() = Coach.run?.index

    @Test fun aStepEndsOnTheAppsStateNotOnATap() {
        Coach.start(listOf(step("Open the player.", done = { it.playerOpen }), step("Then this.", anchor = null)))
        compose.setContent { App() }
        compose.waitForIdle()
        compose.onNodeWithTag("target").performClick()
        compose.waitForIdle()
        assertEquals("the tap in the hole reaches the app", 1, targetTaps)
        assertEquals("but a tap alone doesn't end the step", 0, index())
        state = TourState(playerOpen = true)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertEquals(1, index())
    }

    @Test fun tapsOutsideTheHoleDoNothing() {
        Coach.start(listOf(step("Tap the player.")))
        compose.setContent { App() }
        compose.waitForIdle()
        compose.onNodeWithTag("outside").performClick()
        compose.waitForIdle()
        assertEquals(0, outsideTaps)
        Coach.skip()
        compose.waitForIdle()
        compose.onNodeWithTag("outside").performClick()
        compose.waitForIdle()
        assertEquals("with the tour gone, the app is the app again", 1, outsideTaps)
    }

    @Test fun aMissingControlOffersNextAfterTwoSeconds() {
        Coach.start(listOf(step("Your library.", anchor = TourAnchor.LIBRARY_TAB)))
        compose.mainClock.autoAdvance = false
        compose.setContent { App() }
        compose.mainClock.advanceTimeBy(1_800)
        compose.onNodeWithTag(COACH_NEXT_TAG).assertDoesNotExist()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag(COACH_NEXT_TAG).assertExists()
        compose.onNodeWithTag(COACH_NEXT_TAG).performClick()
        compose.mainClock.advanceTimeBy(100)
        assertNull("Next on the last step ends the tour", Coach.run)
    }

    @Test fun idlingOffersNextAfterEightSecondsFromTheLastTouch() {
        Coach.start(listOf(step("Tap the player.")))
        compose.mainClock.autoAdvance = false
        compose.setContent { App() }
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithTag(COACH_NEXT_TAG).assertDoesNotExist() // the control is there: no 2 s Next
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithTag("outside").performClick() // a touch, even one that does nothing, counts
        assertEquals(1, Coach.run!!.touches)
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(7_700)
        compose.onNodeWithTag(COACH_NEXT_TAG).assertDoesNotExist()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag(COACH_NEXT_TAG).assertExists()
    }

    @Test fun skipFromEveryStepLeavesNothingOnScreen() {
        compose.setContent { App() }
        for (k in 0 until 3) {
            var ended: CoachEnd? = null
            compose.runOnIdle { Coach.start(listOf(step("One."), step("Two.", anchor = null), step("Three.", anchor = TourAnchor.SETTINGS_TAB))) { ended = it } }
            compose.runOnIdle { repeat(k) { Coach.run!!.next() } }
            compose.waitForIdle()
            assertEquals(k, index())
            compose.onNodeWithTag(COACH_SKIP_TAG).performClick()
            compose.waitForIdle()
            assertNull("step ${k + 1}", Coach.run)
            assertEquals(CoachEnd.SKIPPED, ended)
            compose.onNodeWithTag(COACH_TAG).assertDoesNotExist()
            compose.onNodeWithTag(COACH_CAPTION_TAG).assertDoesNotExist()
            assertTrue(TourAnchors.recorded.isEmpty())
        }
    }

    @Test fun aStepToBePassedOverIsPassedOverBothWays() {
        state = TourState(playing = true, songHasLyrics = false)
        var ended: CoachEnd? = null
        Coach.start(
            listOf(
                step("Play a song.", anchor = null, done = { it.playing }),
                step("Lyrics follow the song.", anchor = TourAnchor.PLAYER_LYRICS, passOver = { it.songHasLyrics == false }, done = { it.lyricsOpen }),
                step("Like it.", anchor = null, done = { it.songLiked }),
            ),
        ) { ended = it }
        compose.setContent { App() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals("the song with no lyrics: the Lyrics step is left out", 2, index())
        compose.runOnIdle { Coach.run!!.back() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals("and Back passes over it too, to a step that stays though it's done", 0, index())
        compose.onNodeWithTag(COACH_NEXT_TAG).assertExists()
        assertNull(ended)
    }

    @Test fun theAnchorsRecordNothingWhileNoTourRuns() {
        compose.setContent { App() }
        compose.waitForIdle()
        assertTrue(TourAnchors.recorded.isEmpty())
        assertNull(TourAnchors.boundsOf(TourAnchor.MINI_PLAYER))
        compose.runOnIdle { Coach.start(listOf(step("Tap the player."))) }
        compose.waitForIdle()
        assertNotNull("a tour on: the control's place is known", TourAnchors.boundsOf(TourAnchor.MINI_PLAYER))
        compose.runOnIdle { Coach.skip() }
        compose.waitForIdle()
        assertTrue("and forgotten when it ends", TourAnchors.recorded.isEmpty())
    }

    @Test fun theCaptionStaysOnScreenAtTextSizeTwoRightToLeft() {
        var at by mutableStateOf(Alignment.BottomCenter)
        Coach.start(listOf(step("Like it, and it's in your Library. ".repeat(3).trim())))
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f), LocalLayoutDirection provides LayoutDirection.Rtl) { App(at) }
        }
        for (where in listOf(Alignment.BottomCenter, Alignment.TopCenter, Alignment.Center)) {
            at = where
            compose.waitForIdle()
            val root = compose.onRoot().getBoundsInRoot()
            val cap = compose.onNodeWithTag(COACH_CAPTION_TAG).getBoundsInRoot()
            val target = compose.onNodeWithTag("target").getBoundsInRoot()
            assertTrue("$where: caption $cap inside $root", cap.top >= root.top && cap.bottom <= root.bottom && cap.left >= root.left && cap.right <= root.right)
            if (where != Alignment.Center) assertTrue("$where: caption $cap clear of the control $target", cap.bottom <= target.top || cap.top >= target.bottom)
        }
    }

    /**
     * 1.8.8 review: First steps' last step, without a Settings tab, points at Home's profile picture in the top-end
     * corner, where Skip sat over it. Skip moves to the other top corner then, either direction of text.
     */
    @Test fun skipStaysClearOfAHoleInItsCorner() {
        var dir by mutableStateOf(LayoutDirection.Ltr)
        var at by mutableStateOf(Alignment.TopEnd)
        Coach.start(listOf(step("Your picture opens Settings.")))
        compose.setContent { CompositionLocalProvider(LocalLayoutDirection provides dir) { App(at) } }
        for (d in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            dir = d
            at = Alignment.TopEnd
            compose.waitForIdle()
            val skip = compose.onNodeWithTag(COACH_SKIP_TAG).getBoundsInRoot()
            val target = compose.onNodeWithTag("target").getBoundsInRoot()
            val apart = skip.right <= target.left || skip.left >= target.right || skip.bottom <= target.top || skip.top >= target.bottom
            assertTrue("$d: Skip $skip clear of the control $target", apart)
            val before = targetTaps
            compose.onNodeWithTag("target").performClick()
            compose.waitForIdle()
            assertEquals("$d: the control can be tapped", before + 1, targetTaps)
            // A hole elsewhere: Skip in its own corner, the top end.
            at = Alignment.Center
            compose.waitForIdle()
            val root = compose.onRoot().getBoundsInRoot()
            val s = compose.onNodeWithTag(COACH_SKIP_TAG).getBoundsInRoot()
            val atEnd = if (d == LayoutDirection.Ltr) s.left > root.right / 2 else s.right < root.right / 2
            assertTrue("$d: Skip $s at the top end", atEnd)
        }
        assertTrue(com.prism.music.ui.tour.skipAtStart(Rect(300f, 10f, 400f, 60f), 400f, androidx.compose.ui.geometry.Size(140f, 64f), rtl = false))
        assertTrue(com.prism.music.ui.tour.skipAtStart(Rect(0f, 10f, 100f, 60f), 400f, androidx.compose.ui.geometry.Size(140f, 64f), rtl = true))
        assertFalse(com.prism.music.ui.tour.skipAtStart(Rect(0f, 10f, 100f, 60f), 400f, androidx.compose.ui.geometry.Size(140f, 64f), rtl = false))
        assertFalse("lower down", com.prism.music.ui.tour.skipAtStart(Rect(300f, 100f, 400f, 160f), 400f, androidx.compose.ui.geometry.Size(140f, 64f), rtl = false))
        assertFalse(com.prism.music.ui.tour.skipAtStart(null, 400f, androidx.compose.ui.geometry.Size(140f, 64f), rtl = false))
    }

    /**
     * 1.8.8 review: the coach's Back was registered at its first step, and the player's own handlers (registered once a
     * step opens it) came later and won: Back closed the player's parts instead of going back a step. It's the coach's.
     */
    @Test fun backIsTheCoachsEvenOnceThePlayerOpensWithItsOwn() {
        var playerBacks = 0
        Coach.start(listOf(step("One.", anchor = null), step("Two.", anchor = null), step("Open the player.", anchor = null)))
        lateinit var dispatcher: androidx.activity.OnBackPressedDispatcher
        compose.setContent {
            dispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            PrismTheme(AppSettings(), artworkColor = null) {
                Box(Modifier.fillMaxSize()) {
                    if (state.playerOpen) androidx.activity.compose.BackHandler { playerBacks++ }
                    Coach.run?.let { CoachLayer(it, state, reduceMotion = true) }
                }
            }
        }
        compose.runOnIdle { Coach.run!!.next(); Coach.run!!.next() }
        compose.waitForIdle()
        assertEquals(2, index())
        state = TourState(playerOpen = true) // the player opens, and its Back is registered after the coach's
        compose.waitForIdle()
        compose.runOnUiThread { dispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals("the player's Back wasn't the one called", 0, playerBacks)
        assertEquals("the coach went back a step", 1, index())
        // With the tour gone, Back is the player's again.
        compose.runOnIdle { Coach.skip() }
        compose.waitForIdle()
        compose.runOnUiThread { dispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(1, playerBacks)
    }

    /**
     * 1.8.8 review: a step opens the player (Lyrics, the heart) while no song is in it. The player draws only with one,
     * so nothing showed, and the flag stayed: the next song threw the full player open. The tour's end puts it back.
     */
    @Test fun aPlayerOpenedWithNoSongIsClosedWhenTheTourEnds() {
        var open by mutableStateOf(false)
        var hasSong = false
        compose.setContent { com.prism.music.ui.tour.CoachPlayerReset(open, { hasSong }) { open = false } }
        open = true
        compose.waitForIdle()
        assertTrue("no tour ran: left alone", open)
        open = false
        compose.runOnIdle { Coach.start(listOf(step("Lyrics.", anchor = null))) }
        compose.waitForIdle()
        open = true // the step's prep
        compose.waitForIdle()
        assertTrue("open while the tour runs (a song may still come)", open)
        compose.runOnIdle { Coach.skip() }
        compose.waitForIdle()
        assertTrue("closed as the tour ends with no song", !open)
        // With a song in it, the player stays open after a tour.
        compose.runOnIdle { Coach.start(listOf(step("Like it.", anchor = null))) }
        compose.waitForIdle()
        open = true; hasSong = true
        compose.waitForIdle()
        compose.runOnIdle { Coach.skip() }
        compose.waitForIdle()
        assertTrue(open)
    }

    @Test fun captionPlacement() {
        val h = 2000
        // Hole near the top: below it. Near the bottom: above it. No hole: a little above the middle.
        assertEquals(416, captionTop(Rect(0f, 200f, 100f, 400f), 300, 150, 1950, 16, h))
        assertEquals(1584 - 300, captionTop(Rect(0f, 1600f, 100f, 1800f), 300, 150, 1950, 16, h))
        assertEquals(650, captionTop(null, 300, 150, 1950, 16, h))
        // Too tall for either side: pinned to the roomier side, never off the screen.
        val y = captionTop(Rect(0f, 900f, 100f, 1100f), 1200, 150, 1950, 16, h)
        assertTrue(y >= 0 && y + 1200 <= h)
        val tall = captionTop(Rect(0f, 900f, 100f, 1100f), 2500, 150, 1950, 16, h)
        assertEquals(0, tall)
    }
}
