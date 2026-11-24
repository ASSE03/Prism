package com.prism.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.prism.music.data.model.Song
import com.prism.music.ui.LocalNavigator
import com.prism.music.ui.Navigator
import com.prism.music.ui.Routes
import com.prism.music.ui.screens.settings.SettingsPageId
import com.prism.music.ui.theme.LocalAppSettings
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.theme.PrismTheme
import com.prism.music.ui.tour.GuideContent
import com.prism.music.ui.tour.GuideTags
import com.prism.music.ui.tour.GuideTopicContent
import com.prism.music.ui.tour.GuideTours
import com.prism.music.ui.tour.TourAnchor
import com.prism.music.ui.tour.TourState
import com.prism.music.ui.tour.TourTopic
import com.prism.music.ui.tour.guideTopics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Guide (1.8.8, plan-tour.md §3 D, §5): Settings → Backup & about → Guide is a page of the eight topics with a
 * search line and "Take First steps again" at the top; a topic shows its cards, newest first, and the four most used a
 * "Show me how" (short coach tours of one or two steps: Your library is the heart, then the Library tab).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h6000dp-xhdpi", application = android.app.Application::class)
class GuidePageTest {
    @get:Rule val compose = createComposeRule()
    private val container by lazy { AppContainer(RuntimeEnvironment.getApplication()) }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            val controller = rememberNavController()
            val nav = Navigator(controller, openPlayer = {})
            val s by container.settings.stored.collectAsState()
            CompositionLocalProvider(LocalContainer provides container, LocalNavigator provides nav) {
                PrismTheme(s, artworkColor = null) {
                    CompositionLocalProvider(LocalAppSettings provides s) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            NavHost(controller, "page") { composable("page") { content() } }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun count(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size
    private fun shown(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().size
    private fun top(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().first().boundsInRoot.top

    @Test fun theEightTopicsInOrderWithFirstStepsAtTheTop() {
        var firstSteps = 0
        val opened = ArrayList<TourTopic>()
        show { GuideContent(0.dp, onFirstSteps = { firstSteps++ }) { opened += it } }
        assertEquals(8, count(GuideTags.TOPIC))
        val names = listOf(
            R.string.guide_topic_playing, R.string.guide_topic_lyrics, R.string.guide_topic_library, R.string.guide_topic_finding,
            R.string.guide_topic_sound, R.string.guide_topic_looks, R.string.guide_topic_replay, R.string.guide_topic_devices,
        ).map { tr(it) }
        assertEquals(names, TourTopic.entries.map { it.title })
        // (A row's words may be merged into the row that carries the tag.)
        val inRow = hasTestTag(GuideTags.TOPIC) or hasAnyAncestor(hasTestTag(GuideTags.TOPIC))
        val tops = names.map { n -> compose.onAllNodes(hasText(n) and inRow, useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot.top }
        assertEquals("in the Guide's order", tops.sorted(), tops)
        // Each with its line.
        for (t in TourTopic.entries) assertEquals(t.line, 1, shown(t.line))
        // First steps, above the search line and the topics.
        assertEquals(1, count(GuideTags.FIRST_STEPS))
        assertTrue(top(tr(R.string.guide_first_steps)) < top(tr(R.string.guide_search_hint)))
        assertTrue(top(tr(R.string.guide_search_hint)) < tops.first())
        compose.onNodeWithText(tr(R.string.guide_first_steps_again)).performClick()
        assertEquals(1, firstSteps)
        compose.onNodeWithText(tr(R.string.guide_topic_lyrics)).performClick()
        assertEquals(listOf(TourTopic.LYRICS), opened)
    }

    /** Without First steps to call (part B not connected), the card isn't drawn: nothing that does nothing. */
    @Test fun noFirstStepsCardWithoutFirstSteps() {
        show { GuideContent(0.dp, onFirstSteps = null) {} }
        assertEquals(0, count(GuideTags.FIRST_STEPS))
        assertEquals(8, count(GuideTags.TOPIC))
    }

    @Test fun theSearchLineNarrowsTheTopics() {
        // By a topic's name, by words on its cards, and nothing for words in none.
        assertEquals(TourTopic.entries, guideTopics(" "))
        assertEquals(listOf(TourTopic.SOUND), guideTopics(tr(R.string.guide_topic_sound_line)))
        assertTrue(TourTopic.LOOKS in guideTopics(tr(R.string.tour_186_canvas_title)))
        assertTrue(TourTopic.REPLAY in guideTopics(tr(R.string.tour_185_rewind_title).uppercase()))
        assertEquals(emptyList<TourTopic>(), guideTopics("zqxwv"))

        show { GuideContent(0.dp, onFirstSteps = null) {} }
        val field = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(GuideTags.SEARCH)))
        field.performTextInput(tr(R.string.tour_186_canvas_title))
        compose.waitForIdle()
        assertEquals(guideTopics(tr(R.string.tour_186_canvas_title)).size, count(GuideTags.TOPIC))
        assertEquals(1, shown(tr(R.string.guide_topic_looks)))
        assertEquals(0, count(GuideTags.NOTHING))
        field.performTextReplacement("zqxwv")
        compose.waitForIdle()
        assertEquals(0, count(GuideTags.TOPIC))
        assertEquals(1, count(GuideTags.NOTHING))
        assertEquals(1, shown(tr(R.string.guide_nothing_found)))
    }

    /** A topic shows all its cards, newest first, each with the release it came in, and its Show me opens the place. */
    @Test fun aTopicShowsItsCardsNewestFirst() {
        val topic = TourTopic.LYRICS
        val opened = ArrayList<String>()
        var how = 0
        show { GuideTopicContent(0.dp, topic, onShowMeHow = { how++ }) { opened += it } }
        val cards = topic.cards
        assertTrue(cards.size >= 4)
        assertEquals(cards.size, count(GuideTags.CARD))
        // Newest first: 1.8.7's card above 1.7's "Lyrics".
        assertTrue(cards.first().since > cards.last().since)
        assertTrue(top(cards.first().body) < top(cards.last().body))
        assertTrue(shown(tr(R.string.guide_since, "1.7")) >= 1)
        // Its points are there, word for word.
        for (p in cards.flatMap { it.points }) assertTrue(p, shown(p) >= 1)
        // "Show me how", above the cards.
        assertEquals(1, count(GuideTags.SHOW_ME_HOW))
        assertTrue(top(tr(R.string.guide_show_me_how)) < top(cards.first().body))
        compose.onNodeWithText(tr(R.string.start)).performClick()
        assertEquals(1, how)
        // A card's Show me opens its place.
        val withShowMe = cards.filter { it.showMe != null }
        assertEquals(withShowMe.size, shown(tr(R.string.show_me)))
        compose.onAllNodes(hasText(tr(R.string.show_me))).fetchSemanticsNodes().size.let { assertTrue(it > 0) }
        compose.onAllNodes(hasText(tr(R.string.show_me)))[0].performClick()
        assertEquals(listOf(withShowMe.first().showMe), opened)
    }

    /** A topic without a tour (or on a TV) has no "Show me how". */
    @Test fun noShowMeHowWithoutATour() {
        show { GuideTopicContent(0.dp, TourTopic.SOUND, onShowMeHow = null) {} }
        assertEquals(0, count(GuideTags.SHOW_ME_HOW))
        assertEquals(TourTopic.SOUND.cards.size, count(GuideTags.CARD))
    }

    /**
     * 1.8.8 review: "Show me how" with music already playing, or the song already liked, ended its step at once. As in
     * First steps, what was there as the step began doesn't count: pausing and playing (or unliking and liking) does,
     * and so does another song. And its way to Home is Home's tab, not a second Home over the Guide.
     */
    @Test fun showMeHowStepsDoNotEndThemselves() {
        val song = Song(id = "tst00000001", title = "Test song 1", artists = emptyList(), durationSec = 181)
        val another = Song(id = "tst00000002", title = "Test song 2", artists = emptyList(), durationSec = 181)
        val went = ArrayList<String>()
        val tabbed = ArrayList<String>()
        val acts = com.prism.music.ui.tour.CoachActions(go = { went += it }, tab = { tabbed += it })
        val (play, _) = GuideTours.of(TourTopic.PLAYING)
        val playing = TourState(route = Routes.GUIDE, song = song, playing = true)
        play.prep!!.invoke(acts, playing)
        assertFalse("already playing as it began", play.done(playing))
        assertTrue("another song", play.done(TourState(song = another, playing = true)))
        assertFalse(play.done(TourState(song = song)))
        assertTrue("paused, then played", play.done(playing))
        val (like, _) = GuideTours.of(TourTopic.LIBRARY)
        val liked = TourState(song = song, liked = setOf(song.id))
        like.prep!!.invoke(acts, liked)
        assertFalse("already liked as it began", like.done(liked))
        assertFalse(like.done(TourState(song = song)))
        assertTrue("unliked, then liked", like.done(liked))
        // A fresh tour starts fresh.
        val again = GuideTours.of(TourTopic.LIBRARY).first()
        again.prep!!.invoke(acts, TourState(song = song))
        assertTrue(again.done(liked))
        // Looks' tour without a Settings tab goes Home by the tab too.
        val noTab = com.prism.music.data.prefs.AppSettings(navTabs = listOf(com.prism.music.data.prefs.NavTab.HOME, com.prism.music.data.prefs.NavTab.LIBRARY))
        GuideTours.of(TourTopic.LOOKS).single().prep!!.invoke(acts, TourState(route = Routes.GUIDE, settings = noTab))
        assertEquals(emptyList<String>(), went)
        assertEquals(listOf(Routes.HOME, Routes.HOME), tabbed)
    }

    /** The four tours (plan D13 and the mockup): their steps, the controls they point at, and when each is done. */
    @Test fun theShowMeHowTours() {
        assertEquals(setOf(TourTopic.PLAYING, TourTopic.LYRICS, TourTopic.LIBRARY, TourTopic.LOOKS), GuideTours.topics)
        for (t in TourTopic.entries) assertEquals(t.name, t in GuideTours.topics, GuideTours.of(t).isNotEmpty())
        assertEquals(listOf(TourAnchor.QUICK_PICK, TourAnchor.MINI_PLAYER), GuideTours.of(TourTopic.PLAYING).map { it.anchor })
        assertEquals(listOf(TourAnchor.PLAYER_LYRICS), GuideTours.of(TourTopic.LYRICS).map { it.anchor })
        assertEquals(listOf(TourAnchor.PLAYER_LIKE, TourAnchor.LIBRARY_TAB), GuideTours.of(TourTopic.LIBRARY).map { it.anchor })
        assertEquals(listOf(TourAnchor.SETTINGS_TAB), GuideTours.of(TourTopic.LOOKS).map { it.anchor })
        // Captions: one sentence, 12 words at most.
        for (t in GuideTours.topics) for (s in GuideTours.of(t)) assertTrue(s.caption, s.caption.split(' ').size <= 12 && s.caption.endsWith("."))

        val song = Song(id = "tst00000001", title = "Test song 1", artists = emptyList(), durationSec = 181)
        val (play, player) = GuideTours.of(TourTopic.PLAYING)
        assertFalse(play.done(TourState(song = song)))
        assertTrue(play.done(TourState(song = song, playing = true)))
        assertTrue(player.done(TourState(playerOpen = true)))
        val lyrics = GuideTours.of(TourTopic.LYRICS).single()
        assertTrue(lyrics.done(TourState(playerOpen = true, lyricsOpen = true)))
        assertTrue("a song without lyrics is passed over", lyrics.passOver(TourState(playerOpen = true, songHasLyrics = false)))
        assertFalse(lyrics.passOver(TourState(playerOpen = true, songHasLyrics = null)))
        val (like, library) = GuideTours.of(TourTopic.LIBRARY)
        assertFalse(like.done(TourState(song = song)))
        assertTrue(like.done(TourState(song = song, liked = setOf(song.id))))
        assertTrue(library.done(TourState(route = Routes.LIBRARY)))
        assertFalse(library.done(TourState(route = Routes.HOME)))
        val looks = GuideTours.of(TourTopic.LOOKS).single()
        assertTrue(looks.done(TourState(route = Routes.SETTINGS)) && looks.done(TourState(route = "settings/{section}?find={find}")))
        assertFalse(looks.done(TourState(route = Routes.GUIDE)))
        // Looks' tour leaves the person on Looks; the others where they are.
        assertEquals(Routes.settings(SettingsPageId.LOOKS.key), GuideTours.after(TourTopic.LOOKS))
        assertNull(GuideTours.after(TourTopic.LIBRARY))
    }
}
