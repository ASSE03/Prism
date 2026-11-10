package com.prism.music.ui.screens.settings

import com.prism.music.R
import com.prism.music.tr
import com.prism.music.ui.components.tabTint
import com.prism.music.ui.components.PrismTextButton
import com.prism.music.ui.theme.PrismIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prism.music.data.prefs.CardSize
import com.prism.music.data.prefs.MiniPlayerStyle
import com.prism.music.data.prefs.MiniProgress
import com.prism.music.data.prefs.NavBarStyle
import com.prism.music.data.prefs.NavLabels
import com.prism.music.data.prefs.NavTab
import com.prism.music.data.prefs.TitleSize
import com.prism.music.data.prefs.UiDensity
import com.prism.music.ui.theme.LocalAppSettings
import com.prism.music.ui.theme.LocalContainer
import kotlin.math.roundToInt
import com.prism.music.ui.screens.Chips
import com.prism.music.ui.screens.ChoiceCards
import com.prism.music.ui.screens.Group
import com.prism.music.ui.screens.Label
import com.prism.music.ui.screens.label
import com.prism.music.ui.screens.PrismCheck
import com.prism.music.ui.screens.ResetPage
import com.prism.music.ui.screens.SettingsSection
import com.prism.music.ui.screens.SliderSetting
import com.prism.music.ui.screens.Toggle

// ---------------------------------------------------------------------------- Layout & navigation

internal fun androidx.compose.foundation.lazy.LazyListScope.layoutAndNavigation() {
    item(key = "layout") {
        Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
            NavBarGroup()
            TabsEditor()
            MiniPlayerGroup()
            PagesGroup()
            Box {
                val s = LocalAppSettings.current
                val prefs = LocalContainer.current.settings
                Group(tr(R.string.screen)) {
                    Toggle(
                        tr(R.string.hide_the_status_bar), tr(R.string.more_room_swipe_down_from_the), s.hideStatusBar,
                        info = tr(R.string.the_clock_battery_and_notification_icons),
                    ) { prefs.setHideStatusBar(it) }
                    // Reddit, 1.7.9.1: only while the player is up (the rest of Prism keeps it). Moot with it hidden everywhere.
                    if (!s.hideStatusBar) Toggle(
                        tr(R.string.hide_status_bar_on_the_player), tr(R.string.only_while_the_player_its_lyrics), s.hideStatusBarPlayer,
                    ) { prefs.setHideStatusBarPlayer(it) }
                }
            }
            ResetPage(SettingsSection.INTERFACE)
        }
    }
}

@Composable
internal fun NavBarGroup() {
    val s = LocalAppSettings.current
    val prefs = LocalContainer.current.settings
    Group(tr(R.string.navigation_bar)) {
        Label(tr(R.string.style))
        Chips(NavBarStyle.entries, s.navStyle, { it.label }) { prefs.setNavStyle(it) }
        // The Minimal bar has no labels to show.
        if (s.navStyle != NavBarStyle.MINIMAL) {
            Label(tr(R.string.labels), info = tr(R.string.never_shows_only_the_icons_words))
            Chips(NavLabels.entries, s.navLabels, { it.label }) { prefs.setNavLabels(it) }
        }
        Label(tr(R.string.height))
        Chips(com.prism.music.data.prefs.NavBarSize.entries, s.navBarSize, { it.label }) { prefs.setNavBarSize(it) }
        Label(tr(R.string.background), info = tr(R.string.see_through_lets_the_page_show))
        Chips(com.prism.music.data.prefs.NavBarFill.entries, s.navBarFill, { it.label }) { prefs.setNavBarFill(it) }
        // #264 (1.8.0): only where it means something, a tablet or wide window (phones: always the bottom).
        if (com.prism.music.ui.theme.LocalWindowLayout.current.rail) {
            Label(tr(R.string.tabs_on_a_tablet), info = tr(R.string.tabs_on_a_tablet_info))
            Chips(com.prism.music.data.prefs.TabBarPosition.entries, s.behavior.tabBarPosition, { it.label }) { prefs.setTabBarPosition(it) }
        }
        // 1.8.0: from Icons & shapes (it's how the tab bar marks where you are).
        Label(tr(R.string.the_tab_you_re_on))
        ChoiceCards(
            com.prism.music.data.prefs.TabTint.entries, s.shapes.tabTint, title = { it.label }, detail = { null }, columns = 3,
            onSelect = { prefs.setTabTint(it) },
        ) { t ->
            Box(Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
                Icon(com.prism.music.ui.theme.PrismIcons.HomeFilled, null, Modifier.size(26.dp).tabTint(t))
            }
        }
        Toggle(tr(R.string.shrink_while_scrolling), tr(R.string.slims_down_as_you_scroll), s.navHideOnScroll) { prefs.setNavHideOnScroll(it) }
        Toggle(
            tr(R.string.swipe_between_tabs), tr(R.string.swipe_sideways_on_a_tab_s), s.swipeTabs,
            info = tr(R.string.a_sideways_swipe_on_a_tab),
        ) { prefs.setSwipeTabs(it) }
    }
}

@Composable
internal fun MiniPlayerGroup() {
    val s = LocalAppSettings.current
    val prefs = LocalContainer.current.settings
    Group(tr(R.string.mini_player)) {
        Chips(MiniPlayerStyle.entries, s.miniPlayerStyle, { it.label }) { prefs.setMiniPlayerStyle(it) }
        if (s.navStyle == NavBarStyle.FLOATING) Toggle(
            tr(R.string.joined_to_the_tab_bar), tr(R.string.one_piece_of_glass_with_the), s.miniPlayerJoined,
            info = tr(R.string.the_mini_player_sits_on_top),
        ) { prefs.setMiniPlayerJoined(it) }
        Toggle(tr(R.string.previous_button), tr(R.string.swiping_right_goes_back_too), s.miniPrevButton) { prefs.setMiniPrevButton(it) }
        // Reddit (1.8.1): play / pause can go too, as Next can.
        Toggle(tr(R.string.play_pause_button), tr(R.string.the_player_and_your_phone_s_controls_still_have_it), s.view.miniPlayButton) { prefs.setMiniPlayButton(it) }
        Toggle(tr(R.string.next_button), tr(R.string.swiping_left_skips_too), s.miniSkipButtons) { prefs.setMiniSkipButtons(it) }
        Label(tr(R.string.progress))
        Chips(MiniProgress.entries, s.miniProgress, { it.label }) { prefs.setMiniProgress(it) }
        // #231 (1.7.9.2): its color, and how strongly Fill tints the card.
        if (s.miniProgress != MiniProgress.NONE) {
            Label(tr(R.string.progress_bar_color))
            Chips(com.prism.music.data.prefs.ScrubberColor.entries, s.view.miniProgressColor, { if (it == com.prism.music.data.prefs.ScrubberColor.WHITE) tr(R.string.plain) else it.label }) { prefs.setMiniProgressColor(it) }
        }
        if (s.miniProgress == MiniProgress.FILL) SliderSetting(
            tr(R.string.fill_strength), s.view.miniFillStrength, 0.1f..0.9f, { "${(it * 100).roundToInt()}%" },
        ) { prefs.setMiniFillStrength(it) }
    }
}

@Composable
internal fun PagesGroup() {
    val s = LocalAppSettings.current
    val prefs = LocalContainer.current.settings
    Group(tr(R.string.pages)) {
        Label(tr(R.string.spacing))
        Chips(UiDensity.entries, s.uiDensity, { it.label }) { prefs.setUiDensity(it) }
        Label(tr(R.string.page_titles))
        Chips(TitleSize.entries, s.titleSize, { it.label }) { prefs.setTitleSize(it) }
        Label(tr(R.string.album_playlist_cards))
        Chips(CardSize.entries, s.cardSize, { it.label }) { prefs.setCardSize(it) }
        Spacer(Modifier.height(6.dp))
    }
}

/** Which tabs the bar holds, in what order, and which one Prism opens on. */
@Composable
private fun TabsEditor() {
    val s = LocalAppSettings.current
    val prefs = LocalContainer.current.settings
    val tabs = s.navTabs
    var renaming by remember { mutableStateOf<NavTab?>(null) }
    renaming?.let { t -> RenameTabDialog(t, s.tabNames[t] ?: t.label, { renaming = null }) { prefs.setTabName(t, it); renaming = null } }
    Group(tr(R.string.tabs), info = tr(R.string.tick_up_to_tabs_and_hold, NavTab.MAX)) {
        // Settings opens from Home and Library, or is a tab itself: one of them always stays.
        fun allowed(next: List<NavTab>) = next.size in 2..NavTab.MAX && next.any { it == NavTab.HOME || it == NavTab.LIBRARY || it == NavTab.SETTINGS }
        @Composable
        fun TabRow(t: NavTab, i: Int, modifier: Modifier, grip: Modifier?, held: Boolean) {
            val on = t in tabs
            val name = s.tabNames[t]
            Row(modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PrismCheck(on, enabled = if (on) allowed(tabs - t) else allowed(tabs + t), label = t.label) { v ->
                    val next = if (v) tabs + t else tabs - t
                    if (allowed(next)) {
                        prefs.setNavTabs(next)
                        if (!v && s.startTab == t) prefs.setStartTab(next.first())
                    }
                }
                Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = tr(R.string.rename_2, t.label)) { renaming = t }.padding(vertical = 6.dp)) {
                    Text(name ?: t.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (name != null) Text(t.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (on && !held) {
                    IconButton(onClick = { prefs.setNavTabs(tabs.toMutableList().apply { add(i - 1, removeAt(i)) }) }, enabled = i > 0) { Icon(PrismIcons.ChevronUp, tr(R.string.move_up_2, t.label)) }
                    IconButton(onClick = { prefs.setNavTabs(tabs.toMutableList().apply { add(i + 1, removeAt(i)) }) }, enabled = i < tabs.lastIndex) { Icon(PrismIcons.ChevronDown, tr(R.string.move_down_2, t.label)) }
                }
                if (grip != null) com.prism.music.ui.components.DragGrip(grip.padding(horizontal = 4.dp), tr(R.string.drag, t.label))
            }
        }
        com.prism.music.ui.components.ReorderList(tabs, { it }, { prefs.setNavTabs(it) }) { t, i, held, mod -> TabRow(t, i, mod, Modifier.dragHandle(), held) }
        NavTab.entries.filter { it !in tabs }.forEach { t -> TabRow(t, -1, Modifier, null, false) }
        Label(tr(R.string.open_prism_on), info = tr(R.string.from_prism_s_next_start))
        Chips(tabs, if (s.startTab in tabs) s.startTab else tabs.first(), { s.tabNames[it] ?: it.label }) { prefs.setStartTab(it) }
        Spacer(Modifier.height(4.dp))
    }
}

/** A tab's own name (1.7.6); blank gives it back its own. */
@Composable
private fun RenameTabDialog(tab: NavTab, current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(R.string.rename_2, tab.label)) },
        text = {
            com.prism.music.ui.components.PrismTextField(
                text, { text = com.prism.music.data.prefs.cleanTabName(it) }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text(tab.label) },
            )
        },
        confirmButton = { PrismTextButton(onClick = { onSave(text) }) { Text(tr(R.string.save)) } },
        dismissButton = {
            Row {
                if (current != tab.label) PrismTextButton(onClick = { onSave("") }) { Text(tr(R.string.use_2, tab.label)) }
                PrismTextButton(onClick = onDismiss) { Text(tr(R.string.cancel)) }
            }
        },
    )
}
