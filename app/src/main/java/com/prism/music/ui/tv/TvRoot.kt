package com.prism.music.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import com.prism.music.R
import com.prism.music.tr
import com.prism.music.ui.theme.PrismIcons

/** Prism TV's top-level places, in the side navigation's order. */
enum class TvSection(val label: () -> String, val icon: () -> ImageVector) {
    HOME({ tr(R.string.home) }, { PrismIcons.Home }),
    SEARCH({ tr(R.string.search) }, { PrismIcons.Search }),
    LIBRARY({ tr(R.string.library) }, { PrismIcons.Library }),
    SETTINGS({ tr(R.string.settings) }, { PrismIcons.Settings }),
}

/**
 * Prism TV's shell (1.8.0): the side navigation (it opens wide while it has focus) and the place picked in it. Back
 * from any other place goes to Home; Back on Home leaves Prism, and the music plays on.
 */
@Composable
fun TvRoot() {
    TvTheme {
        var section by rememberSaveable { mutableStateOf(TvSection.HOME) }
        BackHandler(enabled = section != TvSection.HOME) { section = TvSection.HOME }
        TvNavFrame(section, { section = it }, focusNav = true) { TvPlace(section) }
    }
}

/**
 * The side navigation beside a top-level place's [content]. [focusNav]: focus opens on the picked place's item (while
 * a place has nothing to focus yet); otherwise the place's own first item takes it.
 */
@Composable
fun TvNavFrame(section: TvSection, onSection: (TvSection) -> Unit, focusNav: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavigationDrawer(
            drawerContent = {
                Column(
                    Modifier.fillMaxHeight().padding(vertical = TV_SAFE_V, horizontal = 12.dp).selectableGroup().testTag(TAG_NAV),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                ) {
                    for (s in TvSection.entries) {
                        NavigationDrawerItem(
                            selected = section == s,
                            onClick = { onSection(s) },
                            leadingContent = { Icon(s.icon(), contentDescription = null) },
                            modifier = (if (focusNav && s == section) Modifier.entryFocus() else Modifier).testTag("$TAG_NAV/${s.name}"),
                        ) { Text(s.label()) }
                    }
                }
            },
            content = content,
        )
    }
}

/** One top-level place. Slice 1 (foundations): each is its title only; the screens come in the next slices. */
@Composable
private fun TvPlace(section: TvSection) {
    Box(Modifier.fillMaxSize().padding(TvSafe).testTag("$TAG_PLACE/${section.name}")) {
        Text(section.label(), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
    }
}

private val TV_SAFE_V = 27.dp
/** Test tags (TV in-app tests): the side navigation, its items ("tv_nav/HOME"), and the shown place ("tv_place/HOME"). */
const val TAG_NAV = "tv_nav"
const val TAG_PLACE = "tv_place"
