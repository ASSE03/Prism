package com.prism.music

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.prism.music.data.model.ArtistRef
import com.prism.music.data.model.Song
import com.prism.music.data.prefs.AppSettings
import com.prism.music.ui.Routes
import com.prism.music.ui.theme.PrismTheme
import com.prism.music.ui.tour.COACH_CAPTION_TAG
import com.prism.music.ui.tour.COACH_NEXT_TAG
import com.prism.music.ui.tour.COACH_SKIP_TAG
import com.prism.music.ui.tour.Coach
import com.prism.music.ui.tour.CoachActions
import com.prism.music.ui.tour.CoachLayer
import com.prism.music.ui.tour.FIRST_STEPS_DONE_TAG
import com.prism.music.ui.tour.FIRST_STEPS_GUIDE_TAG
import com.prism.music.ui.tour.FirstSteps
import com.prism.music.ui.tour.FirstStepsClosing
import com.prism.music.ui.tour.TourAnchor
import com.prism.music.ui.tour.TourState
import com.prism.music.ui.tour.tourAnchor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * First steps (1.8.8, plan-tour.md §3 B): the five steps walked by the app's state with the real coach over a stand-in
 * app (Quick pick, mini player, the player's Lyrics and heart, the Settings tab): play, open the player, lyrics, like,
 * Looks. Next at step 1 without playing puts the first Quick pick in the mini player, paused (D12); a song without lyrics
 * passes the Lyrics step over; Settings during the last step opens Looks; finishing shows the closing card, skipping
 * doesn't; either way it's done for good.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp", application = android.app.Application::class)
class FirstStepsTest {
    @get:Rule val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val pick = Song(id = "pick0000001", title = "First pick", artists = listOf(ArtistRef("Someone", null)), durationSec = 200)
    private val other = Song(id = "other000001", title = "Another", artists = listOf(ArtistRef("Someone", null)), durationSec = 200)

    private fun TourState.copy(
        route: String? = this.route, playerOpen: Boolean = this.playerOpen, song: Song? = this.song, playing: Boolean = this.playing,
        liked: Set<String> = this.liked, lyricsOpen: Boolean = this.lyricsOpen, songHasLyrics: Boolean? = this.songHasLyrics,
    ) = TourState(route, playerOpen, song, playing, liked, lyricsOpen, songHasLyrics, settings)

    private var state by mutableStateOf(TourState(route = Routes.HOME))
    private var picksLoaded = 0
    private var closings = 0

    @Before fun fresh() {
        Coach.skip()
        context.getSharedPreferences("tour", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        FirstSteps.load(context, returning = false)
        FirstSteps.closing = false
    }

    @After fun clean() = Coach.skip()

    private val actions = CoachActions(
        go = { r -> state = state.copy(route = r) },
        setPlayerOpen = { open -> state = state.copy(playerOpen = open && state.song != null) },
    )

    /** D12's stand-in: the first Quick pick goes in the mini player, paused. */
    private val loadPick: () -> Unit = { picksLoaded++; state = state.copy(song = pick, playing = false) }

    @Composable private fun App() {
        PrismTheme(AppSettings(), artworkColor = null) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.align(Alignment.TopStart).padding(top = 120.dp)) {
                    Box(Modifier.padding(16.dp).size(160.dp, 56.dp).tourAnchor(TourAnchor.QUICK_PICK))
                }
                if (state.song != null && !state.playerOpen) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp).size(360.dp, 64.dp).tourAnchor(TourAnchor.MINI_PLAYER))
                if (state.playerOpen) {
                    Box(Modifier.align(Alignment.Center).size(48.dp).tourAnchor(TourAnchor.PLAYER_LIKE))
                    Box(Modifier.align(Alignment.BottomStart).padding(24.dp).size(48.dp).tourAnchor(TourAnchor.PLAYER_LYRICS))
                }
                Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(64.dp, 56.dp).tourAnchor(TourAnchor.SETTINGS_TAB))
                Coach.run?.let { CoachLayer(it, state, actions, reduceMotion = true) }
            }
        }
    }

    private fun begin() {
        FirstSteps.start(context, loadPick)
        compose.setContent { App() }
        settle()
    }

    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
    }

    private fun index() = Coach.run?.index

    /**
     * 1.8.8, seen on a fresh install: its bar has no Settings tab (Home, Library, Search, Replay), so the last step pointed
     * at nothing. Without the tab, Home's profile picture is where Settings opens: the step goes to Home first.
     */
    @Test fun withoutASettingsTabTheLastStepGoesHomeWhereThePictureOpensSettings() {
        val looks = FirstSteps.steps { }[FirstSteps.LOOKS_STEP]
        val noTab = AppSettings(navTabs = listOf(com.prism.music.data.prefs.NavTab.HOME, com.prism.music.data.prefs.NavTab.LIBRARY, com.prism.music.data.prefs.NavTab.SEARCH))
        var went: String? = null
        val acts = CoachActions(go = { went = it })
        looks.prep!!.invoke(acts, TourState(route = Routes.LIBRARY, settings = noTab))
        assertEquals(Routes.HOME, went)
        went = null
        looks.prep!!.invoke(acts, TourState(route = Routes.LIBRARY, settings = noTab.copy(navTabs = noTab.navTabs + com.prism.music.data.prefs.NavTab.SETTINGS)))
        assertEquals("with the tab, it stays where it is", null, went)
    }

    /**
     * 1.8.8 review: taken again from the Guide, step 1 pushed a second Home over the Guide (`go`); it switches to Home's
     * tab as its tab button does. The last step's way to Home (no Settings tab) too.
     */
    @Test fun stepsGoHomeAsTheTabDoesNotByPushingAnotherHome() {
        val steps = FirstSteps.steps { }
        val went = ArrayList<String>()
        val tabbed = ArrayList<String>()
        val acts = CoachActions(go = { went += it }, tab = { tabbed += it })
        steps[0].prep!!.invoke(acts, TourState(route = Routes.GUIDE))
        val noTab = AppSettings(navTabs = listOf(com.prism.music.data.prefs.NavTab.HOME, com.prism.music.data.prefs.NavTab.LIBRARY))
        steps[FirstSteps.LOOKS_STEP].prep!!.invoke(acts, TourState(route = Routes.GUIDE, settings = noTab))
        assertEquals(emptyList<String>(), went)
        assertEquals(listOf(Routes.HOME, Routes.HOME), tabbed)
    }

    /** 1.8.8 review: with "Hide explicit" Home's Quick picks leave an explicit first song out: the pick is the first shown. */
    @Test fun theQuickPickIsTheFirstOneShown() {
        val explicit = pick.copy(id = "explicit001", explicit = true)
        val picks = listOf(explicit, pick, other)
        assertEquals(pick, FirstSteps.firstShown(com.prism.music.data.prefs.ExplicitContent.HIDE, picks))
        assertEquals(explicit, FirstSteps.firstShown(com.prism.music.data.prefs.ExplicitContent.SHOW, picks))
        assertEquals("Skip lists them (it skips them when playing)", explicit, FirstSteps.firstShown(com.prism.music.data.prefs.ExplicitContent.SKIP, picks))
        assertNull(FirstSteps.firstShown(com.prism.music.data.prefs.ExplicitContent.HIDE, listOf(explicit)))
    }

    @Test fun theFiveStepsAreWalkedByDoingThem() {
        begin()
        assertEquals(FirstSteps.COUNT, Coach.run!!.steps.size)
        compose.onNodeWithText(tr(R.string.first_steps_play)).assertExists()
        assertEquals(0, index())
        // 1: a song plays.
        state = state.copy(song = other, playing = true); settle()
        assertEquals(1, index())
        compose.onNodeWithText(tr(R.string.first_steps_player)).assertExists()
        assertEquals("a song was there: nothing loaded in its place", 0, picksLoaded)
        // 2: the player opens.
        state = state.copy(playerOpen = true); settle()
        assertEquals(2, index())
        // 3: lyrics show.
        state = state.copy(songHasLyrics = true); settle()
        assertEquals("lyrics known but not shown yet", 2, index())
        state = state.copy(lyricsOpen = true); settle()
        assertEquals(3, index())
        compose.onNodeWithText(tr(R.string.first_steps_like)).assertExists()
        assertTrue("the heart is in the player, which stays open", state.playerOpen)
        // 4: liked.
        state = state.copy(liked = setOf(other.id)); settle()
        assertEquals(4, index())
        assertFalse("the Settings tab is outside the player: it closed", state.playerOpen)
        // 5: Settings opens Looks.
        assertEquals(FirstSteps.looksRoute, FirstSteps.redirect(Routes.SETTINGS))
        assertNull("only Settings itself is sent on", FirstSteps.redirect(Routes.LIBRARY))
        state = state.copy(route = FirstSteps.looksRoute); settle()
        assertNull("finished", Coach.run)
        assertTrue("the closing card", FirstSteps.closing)
        assertFalse(FirstSteps.pending)
        assertNull("nothing sent on once it's over", FirstSteps.redirect(Routes.SETTINGS))
    }

    @Test fun nextAtStepOneWithoutPlayingPutsTheFirstPickInTheMiniPlayerPaused() {
        begin()
        compose.mainClock.advanceTimeBy(8_200); compose.waitForIdle()
        compose.onNodeWithTag(COACH_NEXT_TAG).performClick()
        settle()
        assertEquals(1, index())
        assertEquals(1, picksLoaded)
        assertEquals(pick, state.song)
        assertFalse("paused", state.playing)
        // So step 2 points at something at once: no Next offered for a missing control.
        compose.mainClock.advanceTimeBy(2_500); compose.waitForIdle()
        compose.onNodeWithTag(COACH_NEXT_TAG).assertDoesNotExist()
        state = state.copy(playerOpen = true); settle()
        assertEquals(2, index())
    }

    @Test fun aSongWithoutLyricsPassesTheLyricsStepOver() {
        begin()
        state = state.copy(song = other, playing = true); settle()
        state = state.copy(playerOpen = true, songHasLyrics = false); settle()
        assertEquals("from the player straight to the heart", 3, index())
        compose.onNodeWithText(tr(R.string.first_steps_like)).assertExists()
        // And Back from the heart passes it over too.
        compose.runOnIdle { Coach.run!!.back() }; settle()
        assertEquals(1, index())
        assertFalse("step 2's place is outside the player", state.playerOpen)
    }

    @Test fun eachStepOutsideThePlayerClosesItFirst() {
        state = state.copy(song = other, playing = true, playerOpen = true, route = Routes.LIBRARY)
        begin()
        assertFalse(state.playerOpen)
        assertEquals("step 1 is on Home", Routes.HOME, state.route)
        assertEquals("a song already playing doesn't end step 1 by itself", 0, index())
    }

    @Test fun aSongAlreadyLikedDoesNotEndTheLikeStep() {
        state = state.copy(song = other, liked = setOf(other.id))
        begin()
        compose.runOnIdle { repeat(3) { Coach.run!!.next() } }; settle()
        assertEquals(3, index())
        compose.mainClock.advanceTimeBy(1_000); compose.waitForIdle()
        assertEquals(3, index())
        state = state.copy(liked = emptySet()); settle()
        state = state.copy(liked = setOf(other.id)); settle()
        assertEquals(4, index())
    }

    @Test fun skipEndsItForGoodWithoutTheClosingCard() {
        begin()
        compose.onNodeWithTag(COACH_SKIP_TAG).performClick()
        settle()
        assertNull(Coach.run)
        assertFalse(FirstSteps.closing)
        assertFalse(FirstSteps.pending)
        compose.onNodeWithTag(COACH_CAPTION_TAG).assertDoesNotExist()
        // Not again: a later start of Prism finds it done.
        FirstSteps.load(context, returning = true)
        assertFalse(FirstSteps.pending)
        FirstSteps.load(context, returning = false)
        assertFalse(FirstSteps.pending)
    }

    @Test fun theCaptionsAreShortSentences() {
        val steps = FirstSteps.steps {}
        assertEquals(
            listOf(TourAnchor.QUICK_PICK, TourAnchor.MINI_PLAYER, TourAnchor.PLAYER_LYRICS, TourAnchor.PLAYER_LIKE, TourAnchor.SETTINGS_TAB),
            steps.map { it.anchor },
        )
        for (s in steps) assertTrue("\"${s.caption}\": 12 words at most", s.caption.split(' ').size <= 12)
        assertEquals("Tap a song to play it.", steps[0].caption)
        assertEquals("Everything can look your way.", steps[4].caption)
    }

    @Test fun theClosingCardOpensTheGuideOrIsDone() {
        var guide = 0
        var done = 0
        compose.setContent { PrismTheme(AppSettings(), artworkColor = null) { FirstStepsClosing(onGuide = { guide++ }, onDone = { done++ }) } }
        compose.onNodeWithText(tr(R.string.first_steps_closing)).assertExists()
        compose.onNodeWithTag(FIRST_STEPS_GUIDE_TAG).performClick()
        compose.onNodeWithTag(FIRST_STEPS_DONE_TAG).performClick()
        assertEquals(1, guide)
        assertEquals(1, done)
        assertEquals("\"Open the Guide\" opens the Guide", com.prism.music.ui.Routes.GUIDE, FirstSteps.guideRoute)
    }

    @Test fun filledRoutesNameTheirPage() {
        assertEquals("settings/looks", com.prism.music.ui.filledRoute("settings/{section}?find={find}") { mapOf("section" to "looks")[it] })
        assertEquals("settings/playback?find=Song", com.prism.music.ui.filledRoute("settings/{section}?find={find}") { mapOf("section" to "playback", "find" to "Song")[it] })
        assertEquals("home", com.prism.music.ui.filledRoute("home") { null })
        assertEquals(FirstSteps.looksRoute, "settings/looks")
    }
}
