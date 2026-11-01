package com.prism.music

import androidx.datastore.preferences.core.stringPreferencesKey
import com.prism.music.data.model.Shelf
import com.prism.music.data.model.SongItem
import com.prism.music.data.prefs.AppSettings
import com.prism.music.data.prefs.HomeSectionConfig
import com.prism.music.data.prefs.HomeSections
import com.prism.music.data.prefs.SettingsRepository
import com.prism.music.ui.tv.KEY_DIAL
import com.prism.music.ui.tv.KEY_PICKS
import com.prism.music.ui.tv.KEY_RECENT
import com.prism.music.ui.tv.TvHomeLayout
import com.prism.music.ui.tv.TvHomeLayout.HERO
import com.prism.music.ui.tv.TvHomeRow
import com.prism.music.ui.tv.TvHomeTitles
import com.prism.music.ui.tv.tvHomeRows
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prism TV's Customize Home (1.8.0, the owner: "You should be able to customize the homescreen"): no layout is Home as
 * it comes (or follows the phone's order), the TV's layout orders rows and the hero, the phone's says which rows show,
 * and new shelves land where Home would put them.
 */
class TvHomeLayoutTest {
    private fun row(key: String) = TvHomeRow(key, key, emptyList())
    private fun yt(title: String) = HomeSections.shelfKey(title)
    private fun cfg(key: String, visible: Boolean = true) = HomeSectionConfig(key, key, visible)
    private val today = listOf(row(KEY_PICKS), row(KEY_DIAL), row(KEY_RECENT), row(yt("Mixed for you")), row(yt("New releases")))

    @Test fun noLayoutIsHomeAsItComes() {
        val a = TvHomeLayout.arrange(emptyList(), today)
        assertEquals(today, a.rows)
        assertEquals(0, a.heroAt)
        assertEquals(TvHomeLayout.FIXED, listOf(HERO, KEY_PICKS, KEY_DIAL, KEY_RECENT))
        assertTrue(AppSettings().tv.homeLayout.isEmpty())
    }

    @Test fun theRowsShareThePhonesKeys() {
        assertEquals(HomeSections.QUICK_PICKS, KEY_PICKS)
        assertEquals(HomeSections.SPEED_DIAL, KEY_DIAL)
        assertEquals(HomeSections.RECENT, KEY_RECENT)
        // A YouTube shelf is named as the phone's Home names it.
        assertEquals(yt("Mixed for you"), HomeSections.shelfKey("Mixed For You "))
        val song = com.prism.music.data.model.Song("s1", "Song 1", listOf(com.prism.music.data.model.ArtistRef("Artist", "a1")), durationSec = 200)
        val shelves = listOf(Shelf("Mixed for you", items = listOf(SongItem(song))))
        val rows = tvHomeRows(shelves, null, emptyList(), emptyList(), TvHomeTitles("Quick picks", "Speed dial", "Recently played"))
        assertEquals(listOf("yt:shelf:mixed for you"), rows.map { it.key })
    }

    @Test fun noTvLayoutFollowsThePhonesOrderWhereItNamesTheRows() {
        // The phone's layout as a fresh install has it: the TV's Home as it was.
        assertEquals(today, TvHomeLayout.arrange(emptyList(), today, HomeSections.initial).rows)
        // A phone layout with "Mixed for you" before Quick picks: they swap places, the rest stay.
        val phone = listOf(cfg(yt("Mixed for you")), cfg(KEY_PICKS))
        assertEquals(listOf(yt("Mixed for you"), KEY_DIAL, KEY_RECENT, KEY_PICKS, yt("New releases")), TvHomeLayout.arrange(emptyList(), today, phone).rows.map { it.key })
    }

    @Test fun aLayoutOrdersRowsAndTheHeroAndThePhoneSaysWhichShow() {
        // The TV's order: Recently played first, then the hero. Quick picks hidden in the phone's layout (shared).
        val stored = listOf(cfg(KEY_RECENT), cfg(HERO), cfg(KEY_PICKS), cfg(KEY_DIAL), cfg(yt("Mixed for you")), cfg(yt("New releases")))
        val phone = listOf(cfg(KEY_PICKS, visible = false), HomeSectionConfig(HomeSections.DOWNLOADS, "Downloads", visible = false))
        val a = TvHomeLayout.arrange(stored, today, phone)
        assertEquals(listOf(KEY_RECENT, KEY_DIAL, yt("Mixed for you"), yt("New releases")), a.rows.map { it.key })
        assertEquals(1, a.heroAt)
        // A shared row's "hidden" in the TV's own layout means nothing: the phone's layout decides.
        assertEquals(today, TvHomeLayout.arrange(listOf(cfg(HERO), cfg(KEY_PICKS, visible = false)), today).rows)
        // The hero hidden (the TV's own): -1.
        assertEquals(-1, TvHomeLayout.arrange(listOf(cfg(HERO, visible = false)), today).heroAt)
    }
    @Test fun aNewShelfLandsAfterTheRowItFollowsOnHome() {
        // The layout was made before "New releases" came: it lands after "Mixed for you", which was moved to the top.
        val stored = listOf(cfg(yt("Mixed for you")), cfg(HERO), cfg(KEY_PICKS), cfg(KEY_DIAL), cfg(KEY_RECENT))
        val a = TvHomeLayout.arrange(stored, today)
        assertEquals(listOf(yt("Mixed for you"), yt("New releases"), KEY_PICKS, KEY_DIAL, KEY_RECENT), a.rows.map { it.key })
        assertEquals(2, a.heroAt)
        // A row Home doesn't have now (no Speed dial yet, a shelf gone) is simply not there.
        val few = TvHomeLayout.arrange(stored + cfg(yt("Gone")), listOf(row(KEY_PICKS), row(yt("Mixed for you"))))
        assertEquals(listOf(yt("Mixed for you"), KEY_PICKS), few.rows.map { it.key })
        // An old layout without the hero gets it on top, as Home has it.
        assertEquals(0, TvHomeLayout.arrange(listOf(cfg(KEY_RECENT), cfg(KEY_PICKS)), today).heroAt)
    }

    @Test fun theEditorListsEveryRowAsHomeHasIt() {
        val natural = TvHomeLayout.natural(listOf(cfg(yt("Mixed for you")))) { it.uppercase() }
        assertEquals(listOf(HERO, KEY_PICKS, KEY_DIAL, KEY_RECENT, yt("Mixed for you")), natural.map { it.key })
        assertEquals(KEY_PICKS.uppercase(), natural[1].title)
        // No TV layout: Home's own order, shown as the phone's layout says.
        val phone = listOf(cfg(KEY_DIAL, visible = false), HomeSectionConfig(HomeSections.DOWNLOADS, "Downloads", visible = false))
        val fresh = TvHomeLayout.editable(emptyList(), natural, phone)
        assertEquals(natural.map { it.key }, fresh.map { it.key })
        assertEquals(listOf(true, true, false, true, true), fresh.map { it.visible })
        // With one: its order; rows it doesn't have after the row before them on Home (Quick picks and Speed dial after
        // the hero, "Mixed for you" after Recently played); rows Home hasn't now aren't listed.
        val stored = listOf(cfg(yt("Gone")), cfg(KEY_RECENT), cfg(HERO, visible = false))
        val editable = TvHomeLayout.editable(stored, natural, phone)
        assertEquals(listOf(KEY_RECENT, yt("Mixed for you"), HERO, KEY_PICKS, KEY_DIAL), editable.map { it.key })
        assertEquals(listOf(true, true, false, true, false), editable.map { it.visible })
        assertEquals(editable, TvHomeLayout.saved(editable + editable.first()))
        // Two of the TV's rows hidden (the phone-only Downloads isn't one); Back to default shows them, Downloads stays.
        assertEquals(2, TvHomeLayout.hiddenCount(stored, phone))
        val shown = TvHomeLayout.allShown(phone)
        assertTrue(!com.prism.music.ui.tv.hiddenIn(shown, KEY_DIAL))
        assertTrue(com.prism.music.ui.tv.hiddenIn(shown, HomeSections.DOWNLOADS))
    }
    @Test fun movingStopsAtEitherEnd() {
        val list = listOf(cfg("a"), cfg("b"), cfg("c"))
        assertEquals(listOf("b", "a", "c"), TvHomeLayout.moved(list, "b", -1).map { it.key })
        assertEquals(listOf("a", "c", "b"), TvHomeLayout.moved(list, "b", 1).map { it.key })
        assertEquals(list, TvHomeLayout.moved(list, "a", -1))
        assertEquals(list, TvHomeLayout.moved(list, "c", 5).let { TvHomeLayout.moved(it, "c", 0) })
        assertEquals(listOf("b", "c", "a"), TvHomeLayout.moved(list, "a", 9).map { it.key })
        assertEquals(list, TvHomeLayout.moved(list, "nope", 1))
        assertEquals(listOf(true, false, true), TvHomeLayout.toggled(list, "b").map { it.visible })
    }
}

/** Home drawn as laid out: the hero hidden or lower down, and Customize Home at the end. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi", application = android.app.Application::class)
class TvHomeArrangedScreenTest {
    @get:org.junit.Rule val compose = androidx.compose.ui.test.junit4.createComposeRule()
    private val now = com.prism.music.ui.tv.TvNow("Glass Horizon", "Artist", null, null, 0.3f, "1:00", "3:20", playing = true)
    private val shelves = listOf(
        com.prism.music.ui.tv.TvShelf("Row A", listOf(com.prism.music.ui.tv.TvItem("a", "Song A", "Artist", null))),
        com.prism.music.ui.tv.TvShelf("Row B", listOf(com.prism.music.ui.tv.TvItem("b", "Song B", "Artist", null))),
    )

    private fun show(heroAt: Int) = compose.setContent {
        com.prism.music.ui.tv.TvTheme(AppSettings()) {
            // A focus memory of its own (the default one is shared by every test in this JVM).
            androidx.compose.runtime.CompositionLocalProvider(
                com.prism.music.ui.tv.LocalTvScreen provides "section:HOME",
                com.prism.music.ui.tv.LocalTvFocusMemory provides androidx.compose.runtime.remember { com.prism.music.ui.tv.TvFocusMemory() },
            ) {
                com.prism.music.ui.tv.TvHomeScreen("Good evening", now, shelves, {}, {}, {}, { _, _ -> }, heroAt = heroAt, onCustomize = {})
            }
        }
    }

    private fun exists(tag: String) = compose.onAllNodes(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun focusedOn(tag: String) =
        compose.onAllNodes(androidx.compose.ui.test.hasTestTag(tag) and androidx.compose.ui.test.isFocused(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test fun theHeroHiddenWhilePlayingOpensOnTheFirstCard() {
        show(heroAt = -1)
        compose.waitUntil(5_000) { focusedOn("tv_home/row0/0") }
        assertTrue(!exists("tv_home/hero"))
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("Good evening"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun theHeroBelowARowStillLeavesFocusOnTop() {
        show(heroAt = 1)
        compose.waitUntil(5_000) { focusedOn("tv_home/row0/0") }
        assertTrue(exists("tv_home/hero"))
    }
}

/** The layout as the settings store keeps it. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34], application = android.app.Application::class)
class TvHomeLayoutStoreTest {
    @Test fun theLayoutIsKeptReadAndPutBack(): Unit = runBlocking {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val settings = SettingsRepository(app, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val key = stringPreferencesKey("tv_home_layout")
        try {
            val layout = listOf(HomeSectionConfig(KEY_RECENT, "Recently played"), HomeSectionConfig(HERO, "Now playing", visible = false))
            settings.setTvHomeLayout(layout)
            withTimeout(5_000) { settings.flow.first { it.tv.homeLayout == layout } }
            // Written by hand with only the keys and titles (no visible): read as shown, nothing to migrate.
            settings.editNow { it[key] = """[{"key":"$KEY_DIAL","title":"Speed dial"}]""" }
            withTimeout(5_000) { settings.flow.first { it.tv.homeLayout == listOf(HomeSectionConfig(KEY_DIAL, "Speed dial", visible = true)) } }
            // Unreadable: Home as it comes.
            settings.editNow { it[key] = "not json" }
            withTimeout(5_000) { settings.flow.first { it.tv.homeLayout.isEmpty() } }
            // Back to default takes the key away.
            settings.setTvHomeLayout(layout)
            withTimeout(5_000) { settings.flow.first { it.tv.homeLayout.isNotEmpty() } }
            settings.setTvHomeLayout(emptyList())
            withTimeout(5_000) { settings.flow.first { it.tv.homeLayout.isEmpty() } }
        } finally {
            settings.editNow { it.remove(key) }
        }
    }
}
