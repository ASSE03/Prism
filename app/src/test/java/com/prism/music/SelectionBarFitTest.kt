package com.prism.music

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import com.prism.music.data.model.Song
import com.prism.music.ui.components.LocalSongSelection
import com.prism.music.ui.components.SelectScope
import com.prism.music.ui.components.SelectionBar
import com.prism.music.ui.components.SelectionRemover
import com.prism.music.ui.components.SongSelection
import com.prism.music.ui.theme.LocalAppSettings
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.theme.PrismTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * #567: the Select many bar on a phone held upright showed three of its buttons; the rest sat in a sideways scroll that
 * nothing showed. At 360 dp every button is now on the bar or in the ⋯ menu at its end, and one from the menu works.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi", application = android.app.Application::class)
class SelectionBarFitTest {
    @get:Rule val compose = createComposeRule()
    private val app = RuntimeEnvironment.getApplication()
    private val made = lazy { AppContainer(app) }
    private val container by made

    @After fun clear() {
        runCatching { container.db.clearAllTables() }
        made.closeAfterTest()
    }

    private val removed = ArrayList<List<Song>>()

    private fun show(): SongSelection {
        val songs = listOf(Song("a", "Alpha"), Song("b", "Beta"))
        val scope = SelectScope().apply {
            this.songs = songs
            remover = SelectionRemover("Mine", asks = false) { removed += it }
        }
        val selection = SongSelection()
        selection.start(songs[0], scope)
        selection.toggle(songs[1])
        compose.setContent {
            val s by container.settings.stored.collectAsState()
            CompositionLocalProvider(LocalContainer provides container, LocalSongSelection provides selection) {
                PrismTheme(s, artworkColor = null) {
                    CompositionLocalProvider(LocalAppSettings provides s) { SelectionBar(selection) }
                }
            }
        }
        compose.waitForIdle()
        return selection
    }

    private val labels get() = listOf(
        tr(R.string.play_next), tr(R.string.add_to_queue), tr(R.string.add_to_playlist), tr(R.string.add_a_tag),
        tr(R.string.like), tr(R.string.download), tr(R.string.remove_from_this_playlist),
    )

    private fun onBar(label: String): SemanticsNodeInteraction =
        compose.onNode(hasText(label) and hasClickAction() and hasAnyAncestor(hasTestTag("selectionBar")))

    private fun onBarShown(label: String) =
        compose.onAllNodes(hasText(label) and hasAnyAncestor(hasTestTag("selectionBar"))).fetchSemanticsNodes().isNotEmpty() &&
            onBar(label).isDisplayed()

    @Test fun everyButtonIsOnTheBarOrInItsMenu() {
        show()
        val onTheBar = labels.filter(::onBarShown)
        assertTrue("the first ones stay on the bar: $onTheBar", onTheBar.take(2) == labels.take(2))
        val rest = labels - onTheBar.toSet()
        assertTrue("at 360 dp not all seven fit", rest.isNotEmpty())
        // Whatever is on the bar is fully inside it (no half-cut button at the edge).
        val bar = compose.onNode(hasTestTag("selectionBar")).fetchSemanticsNode().boundsInRoot
        onTheBar.forEach {
            val b = onBar(it).fetchSemanticsNode().boundsInRoot
            assertTrue("$it inside the bar", b.left >= bar.left && b.right <= bar.right)
        }
        compose.onNode(hasTestTag(MORE)).performClick()
        compose.waitForIdle()
        rest.forEach { label ->
            assertTrue("$label in the ⋯ menu", compose.onAllNodes(hasText(label) and hasClickAction() and hasAnyAncestor(hasTestTag(MORE_MENU))).fetchSemanticsNodes().size == 1)
        }
    }

    @Test fun aButtonFromTheMenuRuns() {
        val selection = show()
        compose.onNode(hasTestTag(MORE)).performClick()
        compose.waitForIdle()
        compose.onNode(hasText(tr(R.string.remove_from_this_playlist)) and hasAnyAncestor(hasTestTag(MORE_MENU))).performClick()
        compose.waitForIdle()
        assertEquals(listOf("a", "b"), removed.single().map { it.id })
        assertTrue("the picking ends", !selection.selecting)
    }

    private companion object {
        // SelectionTags' values, spelled out so this test also builds on the code before #567.
        const val MORE = "selectionMore"
        const val MORE_MENU = "selectionMoreMenu"
    }
}
