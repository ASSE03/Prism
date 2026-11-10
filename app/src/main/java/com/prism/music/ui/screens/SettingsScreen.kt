package com.prism.music.ui.screens

import com.prism.music.R
import com.prism.music.tr
import com.prism.music.trp
import com.prism.music.ui.components.PrismOutlinedButton
import com.prism.music.ui.components.PrismTextButton
import com.prism.music.ui.theme.PrismIcons
import android.webkit.CookieManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.prism.music.data.prefs.AppSettings
import com.prism.music.data.prefs.ColorSource
import com.prism.music.data.prefs.NavTab
import com.prism.music.download.formatGb
import com.prism.music.ui.LocalNavigator
import com.prism.music.ui.Routes
import com.prism.music.ui.components.SubPage
import com.prism.music.ui.components.featurePane
import com.prism.music.ui.theme.screensOnly
import com.prism.music.ui.theme.LocalAppSettings
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.theme.LocalUi
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.prism.music.ui.screens.settings.appearance
import com.prism.music.ui.screens.settings.AppIconSettings
import com.prism.music.ui.screens.settings.CheckForUpdates
import com.prism.music.ui.screens.settings.ContentCountrySetting
import com.prism.music.ui.screens.settings.ControlsPage
import com.prism.music.ui.screens.settings.DevicesPage
import com.prism.music.ui.screens.settings.DiagnosticsGroup
import com.prism.music.ui.screens.settings.DiscordGroup
import com.prism.music.ui.screens.settings.DownloadSettings
import com.prism.music.ui.screens.settings.GlassSettings
import com.prism.music.ui.screens.settings.HomeLibraryLook
import com.prism.music.ui.screens.settings.IconSettings
import com.prism.music.ui.screens.settings.LastFmGroup
import com.prism.music.ui.screens.settings.layoutAndNavigation
import com.prism.music.ui.screens.settings.LyricsLookGroup
import com.prism.music.ui.screens.settings.LyricsLookPage
import com.prism.music.ui.screens.settings.LyricsSettings
import com.prism.music.ui.screens.settings.MiniPlayerGroup
import com.prism.music.ui.screens.settings.NavBarGroup
import com.prism.music.ui.screens.settings.PagesGroup
import com.prism.music.ui.screens.settings.PlayerCoverGroup
import com.prism.music.ui.screens.settings.PlayerLookGroup
import com.prism.music.ui.screens.settings.PlayerSettings
import com.prism.music.ui.screens.settings.RecommendationsPage
import com.prism.music.ui.screens.settings.ShapeSettings
import com.prism.music.ui.screens.settings.SoundPage
import com.prism.music.ui.screens.settings.TypeSettings

/**
 * The settings pages (1.7.6's reorganization, owner): the hub lists what Prism does (playback, lyrics, controls, your
 * music, devices); everything about how Prism looks lives under one entry, Customize Prism ([APPEARANCE]), whose page
 * lists the look pages. Keys stay as they were, so links to a page (the tour, the player's ⋯, prism:// links) still work.
 */
enum class SettingsSection(val key: String, val title: String, val icon: ImageVector) {
    APPEARANCE("appearance", tr(R.string.customize_prism), PrismIcons.Palette),
    SOUND("sound", tr(R.string.playback_sound), PrismIcons.Equalizer),
    LYRICS("lyrics", tr(R.string.lyrics_sing), PrismIcons.Lyrics),
    CONTROLS("controls", tr(R.string.controls_gestures), PrismIcons.Touch),
    LIBRARY("library", tr(R.string.recommendations_replay), PrismIcons.Library),
    DOWNLOADS("downloads", tr(R.string.downloads_storage_data), PrismIcons.DownloadCircle),
    DEVICES("devices", tr(R.string.devices_connections), PrismIcons.Car),
    LANGUAGE("language", tr(R.string.language), PrismIcons.Globe),
    ABOUT("about", tr(R.string.backup_about), PrismIcons.Info),
    /** Not on the hub: Devices & connections' own pages. */
    CONNECT("connect", tr(R.string.speakers_tvs_browser), PrismIcons.Cast),
    DISCORD("discord", tr(R.string.discord), PrismIcons.Groups),
    LASTFM("lastfm", "Last.fm", PrismIcons.Replay),
    /** 1.8.1: Spotify through the listener's own developer app. */
    SPOTIFY("spotify", tr(R.string.spotify), PrismIcons.Library),
    /** Not on the hub: opened from About. */
    LICENSES("licenses", tr(R.string.open_source_licenses), PrismIcons.Info),
    /** Not on the hub: Customize Prism's pages. */
    LOOKS("looks", tr(R.string.looks), PrismIcons.Looks),
    COLOR("color", tr(R.string.color_2), PrismIcons.Palette),
    TEXT("text", tr(R.string.text), PrismIcons.Signature),
    ICONS("icons", tr(R.string.icons_shapes), PrismIcons.Category),
    GLASS("glass", tr(R.string.glass_backgrounds), PrismIcons.Photo),
    INTERFACE("interface", tr(R.string.tabs_mini_player), PrismIcons.Customize),
    HOME("home", tr(R.string.home_library), PrismIcons.Home),
    PLAYER("player", tr(R.string.player), PrismIcons.PlayCircle),
    LYRICS_LOOK("lyrics_look", tr(R.string.lyrics), PrismIcons.Lyrics);

    fun summary(s: AppSettings, dark: Boolean = true): String = when (this) {
        APPEARANCE -> tr(R.string.looks_colors_fonts_the_player_and)
        INTERFACE -> listOf(tr(R.string.nav_bar_x, s.navStyle.label), com.prism.music.trp(R.plurals.n_tabs, s.navTabs.size), tr(R.string.mini_player_x, s.miniPlayerStyle.label)).joinToString(" · ")
        PLAYER -> listOfNotNull(tr(R.string.player_layout_x, s.playerLayout.label), s.playerBackground.label, if (s.canvasEnabled) tr(R.string.animated_covers) else null).joinToString(" · ")
        SOUND -> listOfNotNull(if (s.lossless) tr(R.string.lossless) else tr(R.string.audio_quality_x, s.audioQuality.label), if (s.mix) tr(R.string.crossfade) else null, if (s.normalize) tr(R.string.normalized) else null, if (s.spatial) tr(R.string.prism_spatial) else null, if (s.eq.enabled) "EQ ${com.prism.music.playback.EqPresets.label(s.eq.preset)}" else null).joinToString(" · ")
        LYRICS -> listOfNotNull(tr(R.string.first, s.lyricsSources.first().label), if (s.lyricsTranslate) tr(R.string.translated) else null, tr(R.string.sing)).joinToString(" · ")
        LYRICS_LOOK -> tr(R.string.style_2, s.lyricsStyle.label, s.lyricsPosition.label)
        CONTROLS -> tr(R.string.swipes_and_taps_on_the_player)
        DOWNLOADS -> listOfNotNull(tr(R.string.smart_downloads_2, formatGb(s.offlineGb)), if (s.downloadWifiOnly) tr(R.string.wi_fi_only) else tr(R.string.any_network), if (s.dataSaver) tr(R.string.data_saver) else null).joinToString(" · ")
        DEVICES -> tr(R.string.android_auto_speakers_tvs_discord_last)
        CONNECT -> listOfNotNull(tr(R.string.speakers_tvs), if (s.webRemote) tr(R.string.browser_remote_on) else null, if (s.handoffAvailable) tr(R.string.available_for_handoff) else null).joinToString(" · ")
        HOME -> tr(R.string.home_s_sections_and_top_library)
        // Only what's on the page itself (1.8.4: it showed the content country, which is on Language).
        LIBRARY -> listOfNotNull(tr(R.string.your_artists_new_releases), if (com.prism.music.data.HistoryPause.paused(s.behavior)) tr(R.string.hist_paused_title) else null, tr(R.string.replay_2)).joinToString(" · ")
        DISCORD -> if (s.discordPresence) tr(R.string.showing_what_you_play_on_your) else tr(R.string.show_what_you_re_playing_on)
        LASTFM -> tr(R.string.scrobble_what_you_play_to_your)
        SPOTIFY -> tr(R.string.spot_summary)
        LANGUAGE -> java.util.Locale.getDefault().let { it.getDisplayName(it) }.replaceFirstChar { it.titlecase() }
        ABOUT -> tr(R.string.prism_backup_updates_diagnostics, com.prism.music.BuildConfig.VERSION_NAME)
        LICENSES -> tr(R.string.gpl_3_0_or_later)
        COLOR -> listOfNotNull(
            // The light's name (White shows as Black in light mode).
            if (s.lightStyle == com.prism.music.data.prefs.LightStyle.WHITE) tr(R.string.light_2, whiteName(dark))
            else com.prism.music.ui.theme.SpectrumPresets.label(s.spectrumPreset).let { if (it == com.prism.music.ui.theme.SpectrumPresets.COVER_LABEL) tr(R.string.light_from_the_cover) else tr(R.string.light_2, it) },
            when (s.colorSource) { ColorSource.CUSTOM -> tr(R.string.accent_x, com.prism.music.ui.theme.ColorMath.toHex(s.accent)); ColorSource.ARTWORK -> tr(R.string.accent_from_the_cover); ColorSource.DYNAMIC -> tr(R.string.accent_from_the_wallpaper) },
        ).joinToString(" · ")
        TEXT -> listOfNotNull(tr(R.string.font_x, s.fontChoice.label), if (s.textScale != 1f) tr(R.string.size_2, (s.textScale * 100).roundToInt()) else null).joinToString(" · ")
        ICONS -> tr(R.string.icon_pack_and_weight_app_icon)
        GLASS -> listOfNotNull(when { s.liquidGlass -> tr(R.string.liquid_glass); s.frostedGlass -> tr(R.string.frosted_glass); else -> tr(R.string.solid_bars) }, if (s.backdrops.screensOnly.isEmpty()) null else tr(R.string.backgrounds), if (s.reduceMotion) tr(R.string.reduced_motion) else null).joinToString(" · ")
        LOOKS -> tr(R.string.save_switch_and_share_whole_looks)
    }

    /** The look settings on this page, for its "Reset this page" (empty: the page has none, or its own reset). */
    val lookKeys: List<String> get() = when (this) {
        TEXT -> listOf("font_choice", "lyrics_font", "text_scale", "type_weight", "type_spacing", "title_font")
        ICONS -> listOf(
            "corners", "corner_scale", "cover_shape_songs", "cover_shape_albums", "cover_shape_artists", "cover_shape_player",
            "button_shape", "play_outlined",
        )
        // 1.8.0: Reduce motion is on the Customize Prism page now (MotionGroup), so this page's reset leaves it.
        GLASS -> listOf("liquid_glass", "glass_kind", "frosted_glass", "frosted_pages", "backdrops", "backdrop_dim", "backdrop_motion", "glass_blur", "glass_frost", "glass_more")
        INTERFACE -> listOf(
            // 1.8.0: the tab you're on's tint is with the tab bar now (from Icons & shapes).
            "tab_icon_tint",
            "nav_style", "nav_labels", "nav_hide_on_scroll", "nav_tabs", "start_tab", "mini_player_style", "mini_skip_buttons",
            "mini_prev_button", "mini_play_button", "mini_progress", "mini_progress_color", "mini_fill_strength", "ui_density", "title_size", "card_size", "hide_status_bar", "hide_status_bar_player",
            "tab_names", "nav_bar_size", "nav_bar_fill", "mini_player_joined",
        )
        PLAYER -> listOf(
            "player_background", "player_backdrop_show", "transport_style", "scrubber_style", "cover_size", "player_art_corners",
            "full_bleed_artwork", "video_full_bleed", "art_shrink_on_pause", "show_playing_from", "player_shuffle_repeat", "hide_volume_bar",
            "show_output_device", "immersive_art", "immersive_lyrics", "immersive_controls", "player_handle", "player_nav_band", "play_ring",
            // 1.8.0: what the page now holds (What's on the player, the progress bar, the crossfade's marks from Sound).
            "player_hidden_parts", "player_buttons", "player_buttons_pill", "player_times", "scrubber_color", "scrubber_thickness",
            "scrubber_thumb", "cover_flight", "crossfade_mark", "blend_covers", "video_controls_hide", "mesh_speed", "mesh_intensity",
            "player_blur", "crossfade_shape", "player_gradient_depth",
            "cover_flight_back", "video_buttons_hide", "bleed_roomy", "queue_lengths", "queue_played_moves", "queue_tidy",
        )
        LYRICS_LOOK -> listOf(
            "lyrics_align", "lyrics_scale", "lyrics_blur", "lyrics_progress_line", "lyrics_style", "lyrics_blur_amount", "lyrics_spacing",
            "lyrics_others_alpha", "lyrics_sung_color", "lyrics_sung_custom", "lyrics_other_color", "lyrics_other_custom", "lyrics_weight",
            "lyrics_backing_size", "lyrics_shadow", "lyrics_uppercase", "lyrics_ahead", "lyrics_position", "lyrics_extra_size", "lyrics_extra_color",
            "lyrics_extra_italic", "lyrics_extra_place", "lyrics_background", "immersive_background", "lyrics_bg_color", "lyrics_bg_color2", "lyrics_everywhere",
            "lyrics_strip_align", "lyrics_strip_two_lines", "lyrics_gap_mark",
            // 1.8.0 lyrics look
            "lyrics_motion", "lyrics_word_by_word", "lyrics_card", "lyrics_halo", "lyrics_bold_lines",
        )
        else -> emptyList()
    }

    /**
     * The look pages: they say when Performance mode is pausing their effects. The live preview (three phones) is on
     * Customize Prism's own page only, scrolling with it: pinned on every look page it took a third of the screen.
     */
    val isLook: Boolean get() = this in setOf(APPEARANCE, LOOKS, COLOR, TEXT, ICONS, GLASS, INTERFACE, HOME, PLAYER, LYRICS_LOOK)

    companion object { fun of(key: String?) = entries.firstOrNull { it.key == key } }
}

private val hubGroups = listOf(
    tr(R.string.make_it_yours) to listOf(SettingsSection.APPEARANCE),
    tr(R.string.listening) to listOf(SettingsSection.SOUND, SettingsSection.LYRICS, SettingsSection.CONTROLS),
    tr(R.string.your_music) to listOf(SettingsSection.LIBRARY, SettingsSection.DOWNLOADS),
    tr(R.string.devices) to listOf(SettingsSection.DEVICES),
    "" to listOf(SettingsSection.LANGUAGE, SettingsSection.ABOUT),
)

/**
 * Settings: the account, then the categories; [section] shows one category's page. On a wide window the hub lists
 * the categories down the side with the picked one's page beside it.
 */
@Composable
fun SettingsScreen(bottomPadding: Dp, section: SettingsSection? = null, find: String? = null) {
    // The switches show what the listener chose, also while Performance mode draws Prism lighter.
    StoredSettings { SettingsHub(bottomPadding, section, find) }
}

/** [content] with the settings as stored, not as Performance mode draws them (pages that change settings). */
@Composable
fun StoredSettings(content: @Composable () -> Unit) {
    val stored by LocalContainer.current.settings.stored.collectAsState()
    androidx.compose.runtime.CompositionLocalProvider(LocalAppSettings provides stored, content = content)
}

@Composable
private fun SettingsHub(bottomPadding: Dp, section: SettingsSection?, find: String?) {
    if (section != null || !com.prism.music.ui.theme.LocalWindowLayout.current.listDetail) {
        SettingsPage(bottomPadding, section, find)
        return
    }
    var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(SettingsSection.APPEARANCE.key) }
    var openFind by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(400.dp).fillMaxHeight()) {
            SettingsPage(bottomPadding, null, null, selected = SettingsSection.of(open)) { picked, group -> open = picked.key; openFind = group }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            androidx.compose.runtime.key(open, openFind) {
                SettingsPage(bottomPadding, SettingsSection.of(open) ?: SettingsSection.APPEARANCE, openFind, showBack = false)
            }
        }
    }
}

/**
 * One settings page: the hub ([section] null) or a category. [onOpen], when given, opens a category beside the hub
 * (wide windows) instead of as its own page; [selected] marks the open one.
 */
@Composable
private fun SettingsPage(
    bottomPadding: Dp,
    section: SettingsSection?,
    find: String?,
    selected: SettingsSection? = null,
    showBack: Boolean = true,
    onOpen: ((SettingsSection, String?) -> Unit)? = null,
) {
    val c = LocalContainer.current
    val s = LocalAppSettings.current
    val nav = LocalNavigator.current
    // As a tab, the hub is a root page and has nothing to go back to.
    val isTab = (section == null && NavTab.SETTINGS in s.navTabs) || !showBack
    // Search (#73), on the hub: matching settings in place of the categories. A result on the hub itself (the account)
    // is found here; one on a page opens that page at its group.
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var foundHere by remember { mutableStateOf<String?>(null) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val results = remember(query) { if (query.isBlank()) emptyList() else SettingsIndex.search(query) }
    // The hub only: its search is ready before the first letter, without the main thread reading the whole index (#355).
    if (section == null) androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { SettingsIndex.prepare() }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalSettingsFind provides (find ?: foundHere)) {
    SubPage(
        title = section?.title ?: tr(R.string.settings),
        bottomPadding = bottomPadding,
        horizontalPadding = 16.dp,
        spacing = 22.dp,
        showBack = !isTab,
        // Only Color keeps its preview pinned under the bar: there each choice changes it, and it's one slim row.
        pinned = if (section == SettingsSection.COLOR) ({ ColorPreviewBar(pinned = true) }) else null,
        pinnedKey = if (section == SettingsSection.COLOR) COLOR_PREVIEW_KEY else null,
    ) {
        // Pages of the effects Performance mode pauses say so first, with the way back.
        if (s.performanceMode && section?.isLook == true) item(key = "paused") { PerformancePaused() }
        when (section) {
            null -> {
                item(key = "find") {
                    com.prism.music.ui.components.SearchField(query, tr(R.string.search_settings), { query = it; foundHere = null })
                }
                if (query.isNotBlank()) {
                    item(key = "results") {
                        if (results.isEmpty()) Text(
                            tr(R.string.no_settings_match, query.trim()),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) else Group("") {
                            results.take(30).forEach { e ->
                                // Keyed: a result still there after the next letter keeps its row, so its text isn't laid out again.
                                androidx.compose.runtime.key(e.section, e.group, e.title) {
                                    // Hidden until another switch is on: the result says so, and opens at that switch's group.
                                    val hidden = e.needs?.takeIf { !it.on(s) }
                                    NavRow(
                                        e.section?.icon ?: PrismIcons.Settings, e.shown,
                                        listOf(e.page, e.group).filter { it != e.title }.joinToString(" › "),
                                        hint = hidden?.hint,
                                    ) {
                                        // The keyboard goes with the search: left up, the page's first Back only closed it.
                                        keyboard?.hide()
                                        val opens = e.opens
                                        // A page outside Settings' own (Customize Home) opens as itself, beside the hub or not.
                                        if (e.route != null) nav.go(e.route)
                                        else if (e.section == null || opens == null) { query = ""; foundHere = e.group }
                                        else if (onOpen != null) onOpen(e.section, e.group)
                                        else nav.go(opens)
                                    }
                                }
                            }
                        }
                    }
                    return@SubPage
                }
                item { AccountCard() }
                item(key = "performance") { PerformanceGroup() }
                // Every section's icon sits on its own color of the split light, red at the top to violet at the foot.
                val hubCount = hubGroups.sumOf { it.second.size }
                var hubIndex = 0
                hubGroups.forEach { (title, sections) ->
                    val first = hubIndex
                    hubIndex += sections.size
                    item {
                        Group(title) {
                            sections.forEachIndexed { i, sec ->
                                val hue = com.prism.music.ui.theme.PrismTokens.spectrumAt((first + i) / (hubCount - 1).coerceAtLeast(1).toFloat())
                                NavRow(sec.icon, sec.title, sec.summary(s, com.prism.music.ui.theme.LocalIsDark.current), selected = sec == selected, hue = hue) {
                                    if (onOpen != null) onOpen(sec, null) else nav.go(Routes.settings(sec.key))
                                }
                            }
                        }
                    }
                }
            }
            SettingsSection.APPEARANCE -> appearance()
            SettingsSection.LOOKS -> looksPage()
            SettingsSection.COLOR -> colorPage()
            SettingsSection.TEXT -> item(key = "text") { Column(verticalArrangement = Arrangement.spacedBy(22.dp)) { TypeSettings(); ResetPage(section) } }
            SettingsSection.ICONS -> item(key = "icons") {
                Column(verticalArrangement = Arrangement.spacedBy(22.dp)) { IconSettings(); AppIconSettings(); ShapeSettings(); HomeWidgetsGroup(); ResetPage(section) }
            }
            SettingsSection.GLASS -> item(key = "glass") { Column(verticalArrangement = Arrangement.spacedBy(22.dp)) { GlassSettings(); ResetPage(section) } }
            SettingsSection.INTERFACE -> layoutAndNavigation()
            SettingsSection.PLAYER -> item { Column(verticalArrangement = Arrangement.spacedBy(22.dp)) { PlayerSettings(); ResetPage(section) } }
            SettingsSection.SOUND -> item { SoundPage() }
            SettingsSection.LYRICS -> item { LyricsSettings() }
            SettingsSection.LYRICS_LOOK -> item { Column(verticalArrangement = Arrangement.spacedBy(22.dp)) { LyricsLookPage(); ResetPage(section) } }
            SettingsSection.CONTROLS -> item { ControlsPage() }
            SettingsSection.DOWNLOADS -> item { DownloadSettings() }
            SettingsSection.DEVICES -> item { DevicesPage() }
            SettingsSection.CONNECT -> item { ConnectSettings() }
            SettingsSection.HOME -> item { HomeLibraryLook() }
            SettingsSection.LIBRARY -> item { RecommendationsPage() }
            SettingsSection.DISCORD -> item { DiscordGroup() }
            SettingsSection.LASTFM -> item { LastFmGroup() }
            SettingsSection.SPOTIFY -> item { SpotifyPage() }
            // 1.8.0: Region (the country Prism Music is asked as) beside the language, from Recommendations & Replay.
            SettingsSection.LANGUAGE -> item { Column(verticalArrangement = Arrangement.spacedBy(22.dp)) { LanguageSettings(); ContentCountrySetting() } }
            SettingsSection.ABOUT -> item {
                Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                BackupGroup()
                Group(tr(R.string.about_3)) {
                    val uri = androidx.compose.ui.platform.LocalUriHandler.current
                    Item(tr(R.string.prism, com.prism.music.BuildConfig.VERSION_NAME), tr(R.string.unofficial_not_affiliated_with_google_or), onClick = { runCatching { uri.openUri(SOURCE_URL) } }) {
                        Icon(PrismIcons.OpenInNew, tr(R.string.open_on_github), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Item(tr(R.string.your_data_stays_here), tr(R.string.prism_has_no_server), info = tr(R.string.your_history_replay_and_settings_live), onClick = {})
                    CheckForUpdates()
                    Item(tr(R.string.take_the_tour), tr(R.string.a_minute_s_walk_through_prism), onClick = { c.tourRequests.tryEmit(Unit) }) { Chevron() }
                    Item(tr(R.string.open_source_licenses), tr(R.string.gpl_3_0_or_later_and), onClick = { nav.go(Routes.settings(SettingsSection.LICENSES.key)) }) { Chevron() }
                    // Debug builds only, so it's left out of Settings search (named title: SettingsIndexTest skips it).
                    if (com.prism.music.BuildConfig.DEBUG) Item(
                        title = tr(R.string.design_gallery), subtitle = tr(R.string.every_component_of_the_redesign_in), onClick = { nav.go(Routes.DESIGN_GALLERY) },
                    ) { Chevron() }
                }
                // 1.8.0: only for sending a bug report, so folded away.
                MoreOptions(listOf(tr(R.string.diagnostics))) { DiagnosticsGroup() }
                }
            }
            SettingsSection.LICENSES -> item { LicensesPage() }
        }
    }
    }
}

/** What Performance mode pauses, in plain words (its ⓘ and the pages' notice). */
internal val PERFORMANCE_PAUSES =
    tr(R.string.blur_and_frosted_glass_the_frost_all)

/**
 * Performance mode (1.7.6): one switch for slower phones, Prism drawn about as lightly as 1.6 was. Nothing is removed and
 * no choice is changed: the effects that cost the most on every frame pause until it's off again.
 */
@Composable
private fun PerformanceGroup() {
    val c = LocalContainer.current
    val s = LocalAppSettings.current
    Group(tr(R.string.performance)) {
        Toggle(
            tr(R.string.performance_mode), if (s.performanceMode) tr(R.string.on_prism_is_drawn_lightly) else tr(R.string.for_slower_phones_lighter_fewer_effects),
            s.performanceMode, icon = PrismIcons.Speed,
            info = tr(R.string.pauses_every_feature_still_works_and, PERFORMANCE_PAUSES.lowercase()),
        ) { c.settings.setPerformanceMode(it) }
    }
}

/** At the top of a page whose effects Performance mode is pausing. */
@Composable
internal fun PerformancePaused() {
    val c = LocalContainer.current
    Group("") {
        Item(
            tr(R.string.performance_mode_is_on), tr(R.string.some_of_these_effects_are_paused),
            info = tr(R.string.paused_you_can_still_change_them, PERFORMANCE_PAUSES.lowercase()),
            icon = PrismIcons.Speed, onClick = { c.settings.setPerformanceMode(false) },
        ) { PrismTextButton(onClick = { c.settings.setPerformanceMode(false) }) { Text(tr(R.string.turn_off)) } }
    }
}

@Composable
internal fun Chevron() = Icon(PrismIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)

// ---------------------------------------------------------------------------- Account

@Composable
private fun AccountCard() {
    val c = LocalContainer.current
    val s = LocalAppSettings.current
    val nav = LocalNavigator.current
    val ui = LocalUi.current
    val scheme = MaterialTheme.colorScheme
    // A pane of frosted glass on a frosted page, the accent's gradient otherwise.
    val frosted = com.prism.music.ui.components.LocalFrosted.current
    val ink = com.prism.music.ui.components.featureInk()
    var switching by remember { mutableStateOf(false) }
    if (switching) AccountSwitcher { switching = false }
    val profile by c.account.profile.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var confirmSignOut by remember { mutableStateOf(false) }
    fun signOut() {
        c.settings.logout()
        // The account's library kept for offline goes with it (copies in Prism stay: they're the Prism Account's).
        c.library.forgetAccount()
        CookieManager.getInstance().removeAllCookies(null)
    }
    // With a Prism Account, signing out can keep the YouTube Music library in Prism first.
    if (confirmSignOut) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmSignOut = false },
        title = { Text(tr(R.string.sign_out_of_youtube_music)) },
        text = { Text(tr(R.string.keep_a_copy_of_its_playlists)) },
        confirmButton = {
            PrismTextButton({
                confirmSignOut = false
                c.scope.launch {
                    val r = runCatching { c.account.bringOver() }.getOrNull()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        signOut()
                        android.widget.Toast.makeText(context, if (r != null) tr(R.string.signed_out_playlists_and_albums_kept, trp(R.plurals.n_playlists, r.playlists), trp(R.plurals.n_albums, r.albums)) else tr(R.string.signed_out), android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }) { Text(tr(R.string.keep_a_copy)) }
        },
        dismissButton = { PrismTextButton({ confirmSignOut = false; signOut() }) { Text(tr(R.string.just_sign_out)) } },
    )
    Column(Modifier.fillMaxWidth().featurePane(ui.card)) {
    // The Prism Account: who the listener is in Prism, on this phone.
    Row(
        Modifier.fillMaxWidth().clickable { nav.go(Routes.ACCOUNT) }.padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (profile != null || s.isLoggedIn) com.prism.music.ui.screens.ProfilePicture(Modifier.size(56.dp))
        else Box(Modifier.size(56.dp).clip(CircleShape).background(if (frosted) com.prism.music.ui.components.frostFill(1.6f) else scheme.surface.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            Icon(PrismIcons.Add, null, tint = if (frosted) scheme.primary else ink)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                profile?.name ?: tr(R.string.make_a_prism_account),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium), color = ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (profile != null) tr(R.string.prism_account_on_this_phone) else tr(R.string.your_name_and_picture_kept_on),
                style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.75f),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(PrismIcons.ChevronRight, null, tint = ink)
    }
    // YouTube Music: optional, for the account's library, history and recommendations.
    Row(
        Modifier.fillMaxWidth()
            .then(if (s.isLoggedIn) Modifier else Modifier.clickable { nav.go(Routes.LOGIN) })
            .padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(if (frosted) com.prism.music.ui.components.frostFill(1.6f) else scheme.surface.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            if (s.isLoggedIn && s.accountAvatar.isNotBlank()) AsyncImage(s.accountAvatar, null, Modifier.fillMaxSize())
            else Icon(PrismIcons.Login, null, Modifier.size(18.dp), tint = if (frosted) scheme.primary else ink)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (s.isLoggedIn) tr(R.string.youtube_music, s.accountName.ifBlank { tr(R.string.signed_in) }) else tr(R.string.sign_in_to_youtube_music),
                style = MaterialTheme.typography.bodyMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (s.isLoggedIn) s.accountEmail.ifBlank { tr(R.string.signed_in_2) } else tr(R.string.optional_your_library_history_and_recommendation),
                style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (s.isLoggedIn) PrismOutlinedButton(onClick = { if (profile != null) confirmSignOut = true else signOut() }) {
            Icon(PrismIcons.Logout, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(tr(R.string.sign_out))
        }
        else Icon(PrismIcons.ChevronRight, null, tint = ink)
    }
    // Another channel of the same Google account (brand accounts), or another Google account altogether.
    if (s.isLoggedIn) Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 8.dp)) {
        PrismTextButton(onClick = { switching = true }) {
            Icon(PrismIcons.Person, null, Modifier.size(18.dp), tint = ink); Spacer(Modifier.width(6.dp)); Text(tr(R.string.switch_account), color = ink)
        }
        PrismTextButton(onClick = { nav.go(Routes.LOGIN_ADD) }) {
            Icon(PrismIcons.Add, null, Modifier.size(18.dp), tint = ink); Spacer(Modifier.width(6.dp)); Text(tr(R.string.add_a_google_account), color = ink)
        }
    }
    // #372, #373, #377: YouTube's own answers say it isn't taking Prism's requests as this account.
    val accountSeen by c.innerTube.seen.collectAsState()
    if (s.isLoggedIn && accountSeen?.signedIn == false) Text(
        tr(R.string.youtube_no_longer_takes_this_sign_in), Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        style = MaterialTheme.typography.bodySmall, color = ink,
    )
    }
}

/**
 * Every channel Prism can use on this sign-in: each Google account's own and its brand accounts (one email can have
 * several YouTube channels). Picking one switches the library, likes and recommendations to it.
 */
@Composable
private fun AccountSwitcher(onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val s = LocalAppSettings.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val accounts by androidx.compose.runtime.produceState<List<com.prism.music.data.model.YtAccount>?>(null) {
        value = runCatching { c.ytm.accounts() }.getOrDefault(emptyList())
    }
    com.prism.music.ui.components.ActionSheet(s.accountAvatar.ifBlank { null }, onDismiss) { close ->
        Text(
            tr(R.string.accounts_and_channels), Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
        )
        val list = accounts
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { com.prism.music.ui.components.PrismSpinner(Modifier.size(30.dp)) }
            list.isEmpty() -> Text(
                tr(R.string.youtube_didn_t_list_any_other),
                Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium,
            )
            else -> list.groupBy { it.email }.forEach { (email, channels) ->
                if (email != null) Text(
                    email, Modifier.padding(start = 24.dp, top = 14.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                channels.forEach { a ->
                    val current = a.authUser == s.authUser && a.dataSyncId.substringBefore("||") == s.dataSyncId.substringBefore("||")
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !current) {
                            close {
                                c.scope.launch {
                                    c.library.switchAccount(a)
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, tr(R.string.switched_to, a.name), android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }.padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(com.prism.music.ui.components.glassFill(2f)), contentAlignment = Alignment.Center) {
                            if (a.avatar != null) AsyncImage(a.avatar, null, Modifier.fillMaxSize()) else Icon(PrismIcons.Person, null)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            a.byline?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                        }
                        if (current) Icon(PrismIcons.Check, tr(R.string.in_use), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Each font's name, set in that font, with the typeface it is. */
/**
 * [text] on up to [maxLines] lines in [style], made smaller a step at a time (down to about half) until every line ends
 * between words or after a hyphen: a word too wide for the room is otherwise split mid-word. Judged from its own layout,
 * not measured ahead (BoxWithConstraints can't sit in the font cards' row, which is sized by its contents).
 */
@Composable
internal fun WholeWords(text: String, style: TextStyle, color: Color, maxLines: Int = 2, modifier: Modifier = Modifier) {
    var fitted by remember(text, style) { mutableStateOf(style) }
    Text(
        text, modifier, style = fitted, color = color, maxLines = maxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        onTextLayout = { l ->
            val whole = !l.hasVisualOverflow && (0 until l.lineCount - 1).all { i -> text.getOrNull(l.getLineEnd(i) - 1).let { it == ' ' || it == '-' } }
            if (!whole && fitted.fontSize.value > style.fontSize.value * 0.5f) fitted = fitted.copy(fontSize = fitted.fontSize * 0.9f)
        },
    )
}

/** "Reset this page" (1.7.3): this page's look settings back to how Prism comes, after asking. */
@Composable
internal fun ResetPage(section: SettingsSection) {
    val c = LocalContainer.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var ask by remember { mutableStateOf(false) }
    PrismTextButton(onClick = { ask = true }, Modifier.fillMaxWidth()) {
        Icon(PrismIcons.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(tr(R.string.reset_this_page))
    }
    if (ask) androidx.compose.material3.AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text(tr(R.string.reset_2, section.title)) },
        text = { Text(tr(R.string.this_page_s_look_goes_back)) },
        confirmButton = {
            PrismTextButton(onClick = {
                ask = false
                c.settings.resetLookKeys(section.lookKeys)
                // The icon style lives apart from the settings.
                if (section == SettingsSection.ICONS) {
                    com.prism.music.ui.theme.IconStyle.save(context, com.prism.music.ui.theme.IconWeight.REGULAR, true)
                    com.prism.music.ui.theme.IconStyle.savePack(context, com.prism.music.ui.theme.IconPack.PRISM)
                    com.prism.music.ui.theme.IconStyle.saveLikeStar(context, false)
                    com.prism.music.ui.theme.IconStyle.saveFill(context, com.prism.music.ui.theme.IconFill.OUTLINE)
                    c.looks.iconsChanged()
                }
            }) { Text(tr(R.string.reset)) }
        },
        dismissButton = { PrismTextButton(onClick = { ask = false }) { Text(tr(R.string.cancel)) } },
    )
}

/**
 * "Customize this" (1.7.3): the settings for the element tapped, in a sheet, the same groups as on their pages, with a
 * way to the whole page. A Home section also gets its own style.
 */
@Composable
fun CustomizeSheet(target: com.prism.music.ui.components.CustomizeTarget, key: String?, onLeave: () -> Unit = {}, onDismiss: () -> Unit) {
    StoredSettings { CustomizeSheetContent(target, key, onLeave, onDismiss) }
}

@Composable
private fun CustomizeSheetContent(target: com.prism.music.ui.components.CustomizeTarget, key: String?, onLeave: () -> Unit, onDismiss: () -> Unit) {
    val nav = LocalNavigator.current
    val c = LocalContainer.current
    val s = LocalAppSettings.current
    val page = when (target) {
        com.prism.music.ui.components.CustomizeTarget.MINI_PLAYER, com.prism.music.ui.components.CustomizeTarget.TAB_BAR -> SettingsSection.INTERFACE
        com.prism.music.ui.components.CustomizeTarget.PLAYER -> SettingsSection.PLAYER
        com.prism.music.ui.components.CustomizeTarget.HOME_SECTION -> SettingsSection.HOME
    }
    val section = key?.let { k -> s.homeLayout.firstOrNull { it.key == k } }
    com.prism.music.ui.components.ActionSheet(null, onDismiss) { close ->
        Text(
            section?.title ?: target.title, Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
        )
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            when (target) {
                com.prism.music.ui.components.CustomizeTarget.MINI_PLAYER -> MiniPlayerGroup()
                com.prism.music.ui.components.CustomizeTarget.TAB_BAR -> NavBarGroup()
                com.prism.music.ui.components.CustomizeTarget.PLAYER -> { PlayerLayoutGroup(); PlayerLookGroup(); PlayerCoverGroup(); PlayerButtonsGroup(); PlayerVisualizerGroup(); LyricsStyleGroup(); LyricsLookGroup(tr(R.string.lyrics)) }
                com.prism.music.ui.components.CustomizeTarget.HOME_SECTION -> {
                    if (section != null) Group(tr(R.string.this_section)) {
                        Label(tr(R.string.style))
                        Chips(com.prism.music.data.prefs.SectionStyle.entries, section.style, { it.label }) { st ->
                            c.settings.setHomeLayout(s.homeLayout.map { if (it.key == section.key) it.copy(style = st) else it })
                        }
                        Item(tr(R.string.hide_this_section), tr(R.string.bring_it_back_from_customize_home), onClick = {
                            c.settings.setHomeLayout(s.homeLayout.map { if (it.key == section.key) it.copy(visible = false) else it })
                            close {}
                        })
                    }
                    PagesGroup()
                    ShapeSettings()
                }
            }
            // The Undo bar sits under the sheet: a change made here can be taken back here.
            val steps by c.looks.steps.collectAsState()
            val openedAt = remember { System.currentTimeMillis() }
            steps.firstOrNull()?.takeIf { it.at >= openedAt }?.let { step ->
                PrismTextButton({ c.looks.undo() }, Modifier.fillMaxWidth()) {
                    Icon(PrismIcons.Undo, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(tr(R.string.undo_2, step.label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            PrismTextButton({ close { onLeave(); nav.go(Routes.settings(page.key)) } }, Modifier.fillMaxWidth()) {
                Text(tr(R.string.all_settings, page.title)); Spacer(Modifier.width(6.dp)); Chevron()
            }
        }
    }
}
