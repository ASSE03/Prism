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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import com.prism.music.R
import com.prism.music.data.model.BrowseItem
import com.prism.music.tr
import com.prism.music.ui.theme.PrismIcons

/** Prism TV's top-level places, in the side navigation's order. */
enum class TvSection(val label: () -> String, val icon: () -> ImageVector) {
    HOME({ tr(R.string.home) }, { PrismIcons.Home }),
    SEARCH({ tr(R.string.search) }, { PrismIcons.Search }),
    LIBRARY({ tr(R.string.library) }, { PrismIcons.Library }),
    SETTINGS({ tr(R.string.settings) }, { PrismIcons.Settings }),
}

/** A page opened over the top-level places (Back closes it). [key] files its scroll and focus. */
sealed interface TvRoute {
    val key: String

    data object Player : TvRoute { override val key = "player" }
    data object Lyrics : TvRoute { override val key = "lyrics" }
    data object Video : TvRoute { override val key = "video" }
    /** An album or a playlist. */
    data class Collection(val item: BrowseItem) : TvRoute { override val key = "collection:${item.id}" }
    data class Artist(val id: String) : TvRoute { override val key = "artist:$id" }
    /** Pairing a phone: to sign in with it ([signIn]), or as a remote. */
    data class Pair(val signIn: Boolean, val firstRun: Boolean = false) : TvRoute { override val key = "pair:$signIn" }
    data object Sing : TvRoute { override val key = "sing" }
    /** The first run's welcome. */
    data object Welcome : TvRoute { override val key = "welcome" }
}

/** Moving between Prism TV's pages: [go] opens one over the others, [back] closes the top one. */
class TvNav(private val stack: SnapshotStateList<TvRoute>, private val memory: TvFocusMemory, private val sectionKey: () -> String) {
    val top: TvRoute? get() = stack.lastOrNull()

    fun go(route: TvRoute) {
        if (stack.lastOrNull() == route) return
        memory.opening(route.key)
        stack += route
    }

    /** Closes the top page; false when none is open (Back then belongs to the places). */
    fun back(): Boolean {
        if (stack.isEmpty()) return false
        stack.removeAt(stack.lastIndex)
        memory.opening(stack.lastOrNull()?.key ?: sectionKey())
        return true
    }

    /** Closes every page: back to the places. */
    fun clear() {
        while (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        memory.opening(sectionKey())
    }

    /** The first run is over (signed in with the phone, or not): its pages go, Home shows. */
    var firstRunDone: () -> Unit = {}
}

/**
 * Prism TV's shell (1.8.0): the side navigation (it opens wide while it has focus) and the place picked in it, with
 * pages (the player, an album…) opened over them. Back closes a page, then goes to Home from another place; Back on
 * Home leaves Prism, and the music plays on. Each page keeps its scroll and the item that had focus.
 */
@Composable
fun TvRoot() {
    TvTheme {
        var section by rememberSaveable { mutableStateOf(TvSection.HOME) }
        val stack = remember { mutableStateListOf<TvRoute>() }
        val memory = remember { TvFocusMemory() }
        val sectionKey = "section:${section.name}"
        val nav = remember(stack, memory) { TvNav(stack, memory) { "section:${section.name}" } }
        // First run (plan §2): welcome, then sign in with the phone or carry on without an account.
        val c = com.prism.music.ui.theme.LocalContainer.current
        nav.firstRunDone = { c.settings.setOnboarded(true); nav.clear() }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            val s = c.settings.current
            if (!s.onboarded && !s.isLoggedIn) nav.go(TvRoute.Welcome)
        }
        BackHandler(enabled = stack.isNotEmpty() || section != TvSection.HOME) {
            if (!nav.back()) { section = TvSection.HOME; memory.opening("section:${TvSection.HOME.name}") }
        }
        val holder = rememberSaveableStateHolder()
        val top = stack.lastOrNull()
        val pick: (TvSection) -> Unit = { section = it; memory.opening("section:${it.name}") }
        CompositionLocalProvider(LocalTvFocusMemory provides memory) { Box(Modifier.fillMaxSize()) {
            // The side navigation stays put while the places change beside it (focus moves from it into the new place);
            // each place keeps its own scroll and focus.
            if (top == null) TvNavFrame(section, pick, focusNav = false) {
                holder.SaveableStateProvider(sectionKey) {
                    CompositionLocalProvider(LocalTvScreen provides sectionKey) { TvPlace(section, nav, pick) }
                }
            }
            else holder.SaveableStateProvider(top.key) {
                CompositionLocalProvider(LocalTvScreen provides top.key) {
                    when (top) {
                        TvRoute.Player -> TvPlayerPage(nav)
                        TvRoute.Lyrics -> TvLyricsPage(nav)
                        TvRoute.Video -> TvVideoPage(nav)
                        is TvRoute.Collection -> TvCollectionPage(top.item, nav)
                        is TvRoute.Artist -> TvArtistPage(top.id, nav)
                        is TvRoute.Pair -> if (top.firstRun) TvPairPage(top.signIn, nav) { nav.firstRunDone() } else TvPairPage(top.signIn, nav)
                        TvRoute.Sing -> TvSingPage(nav)
                        TvRoute.Welcome -> TvWelcomePage(nav)
                    }
                }
            }
            // Over everything, when the room has gone quiet while music plays.
            AmbientHost(onTv = true)
        } }
    }
}

/**
 * The side navigation beside a top-level place's [content]. [focusNav]: focus opens on the picked place's item (while
 * a place has nothing to focus yet); otherwise the place's own first item takes it.
 */
@Composable
fun TvNavFrame(section: TvSection, onSection: (TvSection) -> Unit, focusNav: Boolean, content: @Composable () -> Unit) {
    // Entering the navigation (Left from a place) lands on the place being shown.
    val current = remember { androidx.compose.ui.focus.FocusRequester() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavigationDrawer(
            drawerContent = {
                // Its own focus key: moving through it never counts as focus inside a place.
                CompositionLocalProvider(LocalTvScreen provides NAV_SCREEN) {
                    Column(
                        Modifier.fillMaxHeight().padding(vertical = TV_SAFE_V, horizontal = 12.dp).selectableGroup()
                            .focusRestorerTo(current).testTag(TAG_NAV),
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    ) {
                        for (s in TvSection.entries) {
                            NavigationDrawerItem(
                                selected = section == s,
                                onClick = { onSection(s) },
                                leadingContent = { Icon(s.icon(), contentDescription = null) },
                                modifier = (if (s == section) Modifier.focusRequester(current) else Modifier)
                                    .tvItem("$TAG_NAV/${s.name}", entry = focusNav && s == section),
                            ) { Text(s.label()) }
                        }
                    }
                }
            },
            content = content,
        )
    }
}

/** One top-level place. */
@Composable
private fun TvPlace(section: TvSection, nav: TvNav, onSection: (TvSection) -> Unit) {
    Box(Modifier.fillMaxSize().testTag("$TAG_PLACE/${section.name}")) {
        when (section) {
            TvSection.HOME -> TvHomePlace(nav) { onSection(TvSection.SEARCH) }
            TvSection.SEARCH -> TvSearchPlace(nav)
            TvSection.LIBRARY -> TvLibraryPlace(nav)

            TvSection.SETTINGS -> TvSettingsPlace(nav)
        }
    }
}

private val TV_SAFE_V = 27.dp
/** The side navigation's own focus key. */
private const val NAV_SCREEN = "nav"

/** Focus coming into this group goes to [target] (the navigation's current place), whatever was focused in it before. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
private fun Modifier.focusRestorerTo(target: androidx.compose.ui.focus.FocusRequester): Modifier =
    this.focusProperties { onEnter = { target.requestFocus() } }.focusGroup()
/** Test tags (TV in-app tests): the side navigation, its items ("tv_nav/HOME"), and the shown place ("tv_place/HOME"). */
const val TAG_NAV = "tv_nav"
const val TAG_PLACE = "tv_place"
