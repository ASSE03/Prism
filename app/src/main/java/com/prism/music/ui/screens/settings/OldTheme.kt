package com.prism.music.ui.screens.settings

import com.prism.music.R
import com.prism.music.tr
import com.prism.music.trp
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.prism.music.data.prefs.ThemeMode
import com.prism.music.ui.LocalNavigator
import com.prism.music.ui.Routes
import com.prism.music.ui.theme.screensOnly
import com.prism.music.ui.theme.LocalAppSettings
import com.prism.music.ui.theme.LocalContainer
import kotlin.math.roundToInt
import com.prism.music.ui.screens.Chevron
import com.prism.music.ui.screens.Chips
import com.prism.music.ui.screens.ChoiceCards
import com.prism.music.ui.screens.GlassChoice
import com.prism.music.ui.screens.GlassSample
import com.prism.music.ui.screens.Group
import com.prism.music.ui.screens.Item
import com.prism.music.ui.screens.Label
import com.prism.music.ui.screens.label
import com.prism.music.ui.screens.LOOK_PREVIEW_KEY
import com.prism.music.ui.screens.LookChoices
import com.prism.music.ui.screens.LookStrip
import com.prism.music.ui.screens.NavRow
import com.prism.music.ui.screens.Note
import com.prism.music.ui.screens.ScreenChange
import com.prism.music.ui.screens.SettingsSection
import com.prism.music.ui.screens.SliderSetting
import com.prism.music.ui.screens.Toggle

/** Customize Prism's pages, as its page lists them: the look as a whole, then each part of Prism. */
private val lookWholes = listOf(SettingsSection.LOOKS, SettingsSection.COLOR, SettingsSection.TEXT, SettingsSection.ICONS, SettingsSection.GLASS)

private val lookParts = listOf(SettingsSection.INTERFACE, SettingsSection.HOME, SettingsSection.PLAYER, SettingsSection.LYRICS_LOOK)

// ---------------------------------------------------------------------------- Appearance

internal fun androidx.compose.foundation.lazy.LazyListScope.appearance() {
    // The live preview, once, at the top (it scrolls with the page).
    item(key = LOOK_PREVIEW_KEY) { LookStrip() }
    item(key = "appearance") {
        Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Box {
                val s = LocalAppSettings.current
                val prefs = LocalContainer.current.settings
                Group(tr(R.string.theme)) {
                    Chips(ThemeMode.entries, s.themeMode, { it.label }) { prefs.setThemeMode(it) }
                    Toggle(
                        tr(R.string.pure_black), tr(R.string.amoled_friendly_dark_theme), s.pureBlack,
                        info = tr(R.string.in_dark_mode_pages_sit_on),
                    ) { prefs.setPureBlack(it) }
                }
            }
            // Every look page is here and only here (1.7.6): the look as a whole, then each part of Prism.
            Box {
                val s = LocalAppSettings.current
                val nav = LocalNavigator.current
                val dark = com.prism.music.ui.theme.LocalIsDark.current
                Group(tr(R.string.the_whole_look)) {
                    lookWholes.forEach { sec -> NavRow(sec.icon, sec.title, sec.summary(s, dark)) { nav.go(Routes.settings(sec.key)) } }
                }
            }
            Box {
                val s = LocalAppSettings.current
                val nav = LocalNavigator.current
                val dark = com.prism.music.ui.theme.LocalIsDark.current
                Group(tr(R.string.each_part)) {
                    lookParts.forEach { sec -> NavRow(sec.icon, sec.title, sec.summary(s, dark)) { nav.go(Routes.settings(sec.key)) } }
                }
            }
            Box { MotionGroup() }
            // "Customize this" (1.7.3): tap the mini player, tab bar, player or a Home section to change it there.
            Box {
                val c = LocalContainer.current
                val on by c.customizing.collectAsState()
                val context = androidx.compose.ui.platform.LocalContext.current
                Group(tr(R.string.customize_this)) {
                    Toggle(
                        tr(R.string.customize_by_tapping), tr(R.string.tap_the_mini_player_tab_bar), on,
                        info = tr(R.string.while_it_s_on_those_parts),
                    ) {
                        c.customizing.value = it
                        if (it) android.widget.Toast.makeText(context, tr(R.string.tap_anything_outlined_to_change_it), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            ResetLook()
        }
    }
}

/** Appearance → Glass & backgrounds (1.7.2: its own page). */
@Composable
internal fun GlassSettings() {
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Box {
                val s = LocalAppSettings.current
                val nav = LocalNavigator.current
                Group(tr(R.string.backgrounds)) {
                    val screens = s.backdrops.screensOnly
                    val count = screens.size
                    Item(
                        tr(R.string.screen_backgrounds),
                        if (count == 0) tr(R.string.designs_or_your_own_photo)
                        else screens.values.map { com.prism.music.ui.theme.Backdrop.parse(it).first.label }.distinct().joinToString(" · ") + com.prism.music.trp(R.plurals.on_n_screens, count),
                        onClick = { nav.go(Routes.BACKGROUNDS) },
                    ) { Chevron() }
                }
            }
            Box {
                val s = LocalAppSettings.current
                val prefs = LocalContainer.current.settings
                Group(tr(R.string.glass)) {
                    // 1.8.0: one choice over liquidGlass + glassKind + frostedGlass (LookChoices).
                    Label(tr(R.string.glass), info = tr(R.string.glass_kinds_what_differs))
                    // #252 (1.8.2): each as a real piece of that glass, over the cover playing.
                    val choice = LookChoices.glass(s)
                    ChoiceCards(GlassChoice.entries, choice, { it.label }, { null }, onSelect = { LookChoices.setGlass(prefs, it) }) { GlassSample(it) }
                    val look = s.view.glass
                    if (choice != GlassChoice.OFF) {
                        // Both need Android 12's blur: below it the bars are solid, so there's nothing for them to change.
                        if (android.os.Build.VERSION.SDK_INT < 31) Note(tr(R.string.glass_needs_android_12))
                        else {
                            // Not on Clear: it has next to no blur to set.
                            if (choice != GlassChoice.LIQUID_CLEAR) SliderSetting(
                                tr(R.string.glass_blur), look.blur, com.prism.music.data.prefs.GlassLook.BLUR_RANGE, { "${(com.prism.music.ui.theme.glassSnap(it) * 100).roundToInt()}%" },
                                info = tr(R.string.glass_blur_info),
                            ) { prefs.setGlassBlur(com.prism.music.ui.theme.glassSnap(it)) }
                            SliderSetting(
                                tr(R.string.glass_frost), look.frost, com.prism.music.data.prefs.GlassLook.FROST_RANGE, { "${(com.prism.music.ui.theme.glassSnap(it) * 100).roundToInt()}%" },
                                info = tr(R.string.glass_frost_info),
                            ) { prefs.setGlassFrost(com.prism.music.ui.theme.glassSnap(it)) }
                        }
                    }
                    // #326: only what costs next to nothing, for now.
                    Toggle(
                        tr(R.string.glass_on_more_of_the_screen), tr(R.string.glass_on_more_of_the_screen_detail), look.more,
                        info = tr(R.string.glass_on_more_of_the_screen_info),
                    ) { prefs.setGlassMore(it) }
                    Toggle(tr(R.string.frosted_pages), tr(R.string.pages_frosted_with_their_picture), s.frostedPages, info = tr(R.string.artist_album_and_playlist_pages_sit)) { prefs.setFrostedPages(it) }
                }
            }
            // Reduce motion and how screens change are on the Customize Prism page itself (1.8.0: MotionGroup).
    }
}

/**
 * Motion (1.8.0, on the Customize Prism page: it changes the whole app, and Reduce motion is an accessibility choice):
 * Reduce motion, and how screens change (fades + #208's slide as one choice).
 */
@Composable
private fun MotionGroup() {
    val s = LocalAppSettings.current
    val prefs = LocalContainer.current.settings
    Group(tr(R.string.motion)) {
        Toggle(tr(R.string.reduce_motion), tr(R.string.calmer_quicker_animations), s.reduceMotion, info = tr(R.string.no_drifting_backgrounds_no_artwork_bounce)) { prefs.setReduceMotion(it) }
        Label(tr(R.string.changing_screens), info = tr(R.string.changing_screens_info))
        Chips(ScreenChange.entries, LookChoices.screenChange(s), { it.label }) { LookChoices.setScreenChange(prefs, it) }
    }
}
