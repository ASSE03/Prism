package com.prism.music.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prism.music.ui.screens.LocalSettingsFind
import com.prism.music.ui.screens.PerformancePaused
import com.prism.music.ui.theme.LocalAppSettings

/**
 * A page of the map before its rows are rebuilt with the kit (1.8.4, between the root and the pages): the kit's
 * [SettingsPageScaffold] (the page's title, back, the search button) around the groups the old pages drew, exactly as
 * they drew them ([content], top to bottom), so that every setting stays reachable while the pages are rebuilt one at
 * a time.
 *
 * The old groups keep their own way of being found: [find] is the old group's title, which scrolls into view and
 * glows ([LocalSettingsFind]), and a fold that holds it opens. They sit in one entry of the page's list, so a group far
 * down is there to scroll to. A page of the look says first when Performance mode is pausing its effects, as before.
 *
 * A rebuilt page calls [SettingsPageScaffold] itself and no longer this.
 */
@Composable
internal fun InterimPage(
    page: SettingsPageId,
    bottomPadding: Dp,
    find: String?,
    showBack: Boolean,
    onSearch: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val paused = LocalAppSettings.current.performanceMode && page.group == SettingsRootGroup.MAKE_IT_YOURS
    CompositionLocalProvider(LocalSettingsFind provides find) {
        SettingsPageScaffold(page.title, bottomPadding, showBack = showBack, onSearch = onSearch) {
            item("old") {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    if (paused) PerformancePaused()
                    content()
                }
            }
        }
    }
}

/**
 * What an old page added to its lazy list ([build]: `looksPage()`, `colorPage()`, `appearance()`,
 * `layoutAndNavigation()`), drawn here one under the other inside an [InterimPage]. Those builders only add single
 * items, each a composable that asks nothing of the list.
 */
@Composable
internal fun OldItems(build: LazyListScope.() -> Unit) {
    val items = OldItemList().apply(build).items
    items.forEachIndexed { i, (itemKey, content) -> key(itemKey ?: i) { OldItemScope.content() } }
}

private class OldItemList : LazyListScope {
    val items = ArrayList<Pair<Any?, @Composable LazyItemScope.() -> Unit>>()

    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        items += key to content
    }

    override fun items(count: Int, key: ((index: Int) -> Any)?, contentType: (index: Int) -> Any?, itemContent: @Composable LazyItemScope.(index: Int) -> Unit) {
        repeat(count) { i -> items += key?.invoke(i) to { itemContent(i) } }
    }

    @Deprecated("Sticky headers aren't used by the old settings pages")
    @androidx.compose.foundation.ExperimentalFoundationApi
    override fun stickyHeader(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        items += key to content
    }
}

/** The items aren't in a lazy list of their own here: "the parent's size" is the room they're given. */
private object OldItemScope : LazyItemScope {
    override fun Modifier.fillParentMaxSize(fraction: Float): Modifier = fillMaxSize(fraction)
    override fun Modifier.fillParentMaxWidth(fraction: Float): Modifier = fillMaxWidth(fraction)
    override fun Modifier.fillParentMaxHeight(fraction: Float): Modifier = fillMaxHeight(fraction)
}
