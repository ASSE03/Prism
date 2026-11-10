package com.prism.music.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.prism.music.R
import com.prism.music.data.prefs.AppSettings
import com.prism.music.tr
import com.prism.music.ui.screens.Needs
import com.prism.music.ui.screens.SettingEntry
import com.prism.music.ui.screens.SettingNeeds
import com.prism.music.ui.screens.SettingsSection

private val page = SettingsPageId.GESTURES_BUTTONS

/**
 * Settings → Gestures & buttons (1.8.4's map, plan §2.3). This file is the only place for the page: its composable,
 * its lines in Settings' search ([gesturesButtonsIndex]), its "Reset this page" keys ([gesturesButtonsLookKeys]), its
 * line on the root ([gesturesButtonsValue]) and the old group names it answers to ([gesturesButtonsOldGroups]).
 *
 * Interim body: the old Controls & gestures page as it was ([ControlsPage] in `OldGestures.kt`), inside the kit's
 * scaffold, until the page's rows are rebuilt with the kit.
 */
@Composable
fun GesturesButtonsPage(bottomPadding: Dp, find: String? = null, showBack: Boolean = true, onSearch: (() -> Unit)? = null) {
    InterimPage(page, bottomPadding, find, showBack, onSearch) { ControlsPage() }
}

/** The root's line for Gestures & buttons. */
fun gesturesButtonsValue(s: AppSettings, dark: Boolean): String = SettingsSection.CONTROLS.summary(s, dark)

/** The look settings "Reset this page" puts back: none yet (the old page had no reset). */
val gesturesButtonsLookKeys: List<String> = emptyList()

/** Old group titles → this page's group keys. Empty while the old groups are drawn as they were. */
val gesturesButtonsOldGroups: Map<String, String> = emptyMap()

/** The page's lines in Settings' search (each names the group it is drawn in today). */
val gesturesButtonsIndex: List<SettingEntry> by lazy {
    with(SettingNeeds) {
        listOf(
            SettingEntry(page, tr(R.string.notification_and_car), tr(R.string.notification_and_car), tr(R.string.notification_lock_screen_android_auto_car)),
            SettingEntry(page, tr(R.string.gestures), tr(R.string.gestures), tr(R.string.swipe_double_tap_long_press_cover)),
            SettingEntry(page, tr(R.string.gestures), tr(R.string.double_tap), tr(R.string.seek_skip_seconds_fast_forward_rewind)),
            SettingEntry(page, tr(R.string.gestures), tr(R.string.long_press), tr(R.string.hold_cover)),
            SettingEntry(page, tr(R.string.gestures), tr(R.string.pull_down), tr(R.string.mini_player_stop)),
            SettingEntry(page, tr(R.string.gestures), tr(R.string.swipe_the_mini_player_up), tr(R.string.mini_player_swipe_up_open_player)),
            SettingEntry(page, tr(R.string.buttons_and_touch), tr(R.string.hold_previous_next_to_seek), tr(R.string.fast_forward_rewind) + " " + tr(R.string.each_step)),
            SettingEntry(page, tr(R.string.buttons_and_touch), tr(R.string.haptics), tr(R.string.vibration_vibrate)),
            SettingEntry(page, tr(R.string.buttons_and_touch), tr(R.string.swipe_up_for_the_queue), tr(R.string.gesture_one_hand_queue_lyrics_swipe)),
            SettingEntry(page, tr(R.string.gestures), tr(R.string.gestures_anywhere_on_the_player), tr(R.string.gestures_everywhere_keywords)),
            SettingEntry(page, tr(R.string.gestures), tr(R.string.gestures_under_the_lyrics), tr(R.string.gestures_everywhere_keywords)),
            SettingEntry(page, tr(R.string.screen), tr(R.string.lock_to_portrait), tr(R.string.rotation_orientation_landscape_upright)),
            SettingEntry(page, tr(R.string.screen), tr(R.string.picture_in_picture), tr(R.string.pip_video_float_window_leave)),
        )
    }
}
