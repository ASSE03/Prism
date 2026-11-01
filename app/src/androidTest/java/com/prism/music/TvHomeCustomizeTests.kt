package com.prism.music

import android.view.KeyEvent
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prism.music.TvFixture.awaitFocus
import com.prism.music.TvFixture.awaitFocusOn
import com.prism.music.data.model.Shelf
import com.prism.music.data.model.SongItem
import com.prism.music.data.prefs.HomeSections
import com.prism.music.ui.tv.TAG_HOME
import com.prism.music.ui.tv.TAG_HOME_EDITOR
import com.prism.music.ui.tv.TAG_NAV
import com.prism.music.ui.tv.TAG_SETTINGS
import com.prism.music.ui.tv.TvHomeLayout
import com.prism.music.ui.tv.TvHomeShelves
import com.prism.music.ui.tv.TvSection
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prism TV's Customize Home (1.8.0, the owner: "You should be able to customize the homescreen"), with the remote
 * only: from the end of Home, a row hidden with OK (in the phone's Home layout too), one carried up with Move and one with OK held; Home then shows them
 * so; and from Settings, Back to default brings Home back as it came.
 */
@TvTest
@RunWith(AndroidJUnit4::class)
class TvHomeCustomizeTests {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val shots = ScreenshotOnFailure()
    private val settings get() = Fixture.container.settings
    private val a = HomeSections.shelfKey("Prism test A")
    private val b = HomeSections.shelfKey("Prism test B")
    private val c = HomeSections.shelfKey("Prism test C")

    @Before fun setUp() {
        TvFixture.assumeTv()
        Fixture.ready()
        // Home's shelves as YouTube would send them (fresh for this run, so nothing is asked of the network).
        val songs = Fixture.songs(6)
        TvHomeShelves.keep(
            null,
            listOf("A" to 0, "B" to 2, "C" to 4).map { (n, i) -> Shelf("Prism test $n", items = songs.subList(i, i + 2).map(::SongItem)) },
            System.currentTimeMillis(),
        )
    }

    private val inNav = SemanticsMatcher("in the side navigation") { n ->
        n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { null }?.startsWith("$TAG_NAV/") == true
    }

    private fun goTo(section: TvSection) {
        compose.awaitFocus(15_000)
        for (i in 0 until 6) {
            if (compose.onAllNodes(inNav and isFocused(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) break
            TvFixture.left(); Thread.sleep(300)
        }
        // The navigation opens on the place shown: up to its top (Home), then down to [section].
        repeat(TvSection.entries.size) { TvFixture.up(); Thread.sleep(150) }
        repeat(section.ordinal) { TvFixture.down(); Thread.sleep(200) }
        compose.awaitFocusOn(hasTestTag("$TAG_NAV/${section.name}"))
        TvFixture.ok()
    }

    private fun titleOf(r: Int): String? =
        compose.onAllNodes(hasTestTag("$TAG_HOME/title$r"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            ?.config?.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.joinToString { it.text }

    /**
     * Home's row titles, top to bottom. A TV screen holds about two rows and Home only draws what's on it, so the list
     * is scrolled to each further row in turn (no remote key, so focus stays where it is), then back to its top.
     */
    private fun homeTitles(): List<String> {
        val seen = sortedMapOf<Int, String>()
        val home = compose.onNode(hasTestTag(TAG_HOME))
        for (r in 0 until 8) {
            if (titleOf(r) == null) runCatching { home.performScrollToNode(hasTestTag("$TAG_HOME/title$r")) }
            titleOf(r)?.let { seen[r] = it } ?: break
        }
        runCatching { home.performScrollToIndex(0) }
        return seen.values.toList()
    }

    /** Waits until Home shows [titles] (polling: each look scrolls the list). */
    private fun awaitHomeTitles(titles: List<String>, ms: Long) {
        val end = System.currentTimeMillis() + ms
        var got = homeTitles()
        while (got != titles) {
            if (System.currentTimeMillis() > end) throw AssertionError("Home shows $got, not $titles")
            Thread.sleep(300)
            got = homeTitles()
        }
    }

    private fun keys() = settings.current.tv.homeLayout.map { it.key }

    private fun waitFor(ms: Long, what: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (!condition()) {
            if (System.currentTimeMillis() > end) throw AssertionError("not after $ms ms: $what (layout ${settings.current.tv.homeLayout})")
            Thread.sleep(100)
        }
    }

    private fun row(key: String) = hasTestTag("$TAG_HOME_EDITOR/row/$key")

    @Test fun aRowHiddenAndTwoMovedShowSoOnHome() {
        TvFixture.launch()
        compose.awaitFocusOn(hasTestTag("$TAG_HOME/row0/0"), 15_000)
        awaitHomeTitles(listOf("Prism test A", "Prism test B", "Prism test C"), 5_000)
        // Down past the rows: Customize Home, at Home's end.
        repeat(3) { TvFixture.down(); Thread.sleep(250) }
        compose.awaitFocusOn(hasTestTag("$TAG_HOME/customize"))
        TvFixture.ok()

        // The editor opens on its first row (the hero); Home's rows follow in Home's order.
        compose.awaitFocusOn(row(TvHomeLayout.HERO), 10_000)
        repeat(4) { TvFixture.down(); Thread.sleep(250) }
        compose.awaitFocusOn(row(a))
        // OK hides A.
        TvFixture.ok()
        waitFor(5_000, "A hidden") { com.prism.music.ui.tv.hiddenIn(settings.current.homeLayout, a) }

        // C, carried up twice with Move beside it, put down with OK.
        TvFixture.down(); Thread.sleep(250); TvFixture.down()
        compose.awaitFocusOn(row(c))
        TvFixture.right()
        compose.awaitFocusOn(hasTestTag("$TAG_HOME_EDITOR/move/$c"))
        TvFixture.ok(); Thread.sleep(300)
        TvFixture.up(); Thread.sleep(300); TvFixture.up()
        waitFor(5_000, "C above A") { keys().indexOf(c) in 0 until keys().indexOf(a) }
        // Carried, focus stayed on C.
        compose.awaitFocusOn(hasTestTag("$TAG_HOME_EDITOR/move/$c"))
        TvFixture.ok(); Thread.sleep(300)

        // B, picked up by holding OK, carried up once past A, put down with OK.
        TvFixture.left()
        compose.awaitFocusOn(row(c))
        TvFixture.down(); Thread.sleep(250); TvFixture.down()
        compose.awaitFocusOn(row(b))
        TvFixture.hold(KeyEvent.KEYCODE_DPAD_CENTER, 1_500); Thread.sleep(300)
        TvFixture.up()
        waitFor(5_000, "B above A") { keys().indexOf(b) in 0 until keys().indexOf(a) }
        compose.awaitFocusOn(row(b))
        TvFixture.ok(); Thread.sleep(300)
        // Put down: Up moves focus again, not B.
        TvFixture.up()
        compose.awaitFocusOn(row(c))
        assertEquals(listOf(TvHomeLayout.HERO, HomeSections.QUICK_PICKS, HomeSections.SPEED_DIAL, HomeSections.RECENT, c, b, a), keys())

        // Back: Home, as laid out (A gone, C before B).
        TvFixture.back()
        awaitHomeTitles(listOf("Prism test C", "Prism test B"), 10_000)
        compose.awaitFocus()
    }

    @Test fun settingsOpensTheEditorAndBackToDefaultPutsHomeBack() {
        // C moved to the top on the TV; A hidden (in the phone's Home layout, which the TV shares).
        settings.setTvHomeLayout(listOf(
            com.prism.music.data.prefs.HomeSectionConfig(c, "Prism test C"),
            com.prism.music.data.prefs.HomeSectionConfig(a, "Prism test A"),
        ))
        settings.setHomeLayout(com.prism.music.ui.tv.layoutWithShown(settings.current.homeLayout, a, "Prism test A", shown = false))
        waitFor(5_000, "the layouts kept") { keys().isNotEmpty() && com.prism.music.ui.tv.hiddenIn(settings.current.homeLayout, a) }
        TvFixture.launch()
        compose.awaitFocusOn(hasTestTag("$TAG_HOME/row0/0"), 15_000)
        awaitHomeTitles(listOf("Prism test C", "Prism test B"), 5_000)

        // Settings → Home (after Account, Language, Sound, Lyrics, Sing and Ambient screen) → Customize Home.
        goTo(TvSection.SETTINGS)
        compose.awaitFocusOn(hasTestTag("$TAG_SETTINGS/group/0"), 10_000)
        for (g in 1..6) { TvFixture.down(); compose.awaitFocusOn(hasTestTag("$TAG_SETTINGS/group/$g")) }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(com.prism.music.tr(R.string.customize_home)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        TvFixture.right()
        compose.awaitFocusOn(hasTestTag("$TAG_SETTINGS/setting/0"))
        TvFixture.ok()

        // The editor opens on its first row (the hero, then C as moved); Up: Back to default.
        compose.awaitFocusOn(row(TvHomeLayout.HERO), 10_000)
        TvFixture.up()
        compose.awaitFocusOn(hasTestTag("$TAG_HOME_EDITOR/reset"))
        TvFixture.ok()
        waitFor(5_000, "no layout kept, A shown") { keys().isEmpty() && !com.prism.music.ui.tv.hiddenIn(settings.current.homeLayout, a) }
        // The list is Home's own order again: A shown, right after Recently played (the list may run past the screen, so C
        fun top(key: String) = compose.onAllNodes(row(key), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top
        // isn't looked for).
        compose.waitUntil(5_000) { val tRecent = top(HomeSections.RECENT); val ta = top(a); tRecent != null && ta != null && tRecent < ta && (top(c) ?: Float.MAX_VALUE) > ta }
        compose.waitUntil(5_000) { compose.onAllNodes(row(a) and hasText(com.prism.music.tr(R.string.shown), substring = true)).fetchSemanticsNodes().isNotEmpty() }

        // Back to Settings, then Home: as it came.
        TvFixture.back()
        compose.awaitFocusOn(hasTestTag("$TAG_SETTINGS/setting/0"), 10_000)
        TvFixture.back()
        awaitHomeTitles(listOf("Prism test A", "Prism test B", "Prism test C"), 10_000)
    }
}
