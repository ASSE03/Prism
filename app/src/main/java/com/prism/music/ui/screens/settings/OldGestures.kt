package com.prism.music.ui.screens.settings

import com.prism.music.R
import com.prism.music.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.prism.music.ui.theme.LocalAppSettings
import com.prism.music.ui.theme.LocalContainer
import kotlin.math.roundToInt
import com.prism.music.ui.screens.Chips
import com.prism.music.ui.screens.Group
import com.prism.music.ui.screens.Label
import com.prism.music.ui.screens.label
import com.prism.music.ui.screens.PlayerGesturesGroup
import com.prism.music.ui.screens.SessionButtonsGroup
import com.prism.music.ui.screens.SliderSetting
import com.prism.music.ui.screens.Toggle

/**
 * Settings → Controls & gestures (1.7.6): what touches do, wherever they were before: the player's cover and the mini
 * player (Player), holding previous / next and haptics, the notification's and car's buttons, Search's keyboard.
 */
@Composable
internal fun ControlsPage() {
    val s = LocalAppSettings.current
    val prefs = LocalContainer.current.settings
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        // Swipes, double tap and long press (#79's seeking among them), then the buttons' own.
        PlayerGesturesGroup()
        Group(tr(R.string.buttons_and_touch)) {
            // 1.8.0: one slider, Off at its start (the switch only flipped this between 0 and 10 s). Same stored value.
            SliderSetting(
                tr(R.string.hold_previous_next_to_seek), s.holdSeekSeconds.toFloat(), 0f..20f,
                { if (it.roundToInt() == 0) tr(R.string.off) else "${it.roundToInt()} s" }, steps = 19,
                info = tr(R.string.a_tap_still_changes_songs),
            ) { prefs.setHoldSeekSeconds(it.roundToInt()) }
            Toggle(tr(R.string.haptics), tr(R.string.a_light_tap_on_buttons_and), s.haptics) { prefs.setHaptics(it) }
            Toggle(
                tr(R.string.swipe_up_for_the_queue), tr(R.string.on_the_player_s_lower_half), s.behavior.panelSwipes,
                info = tr(R.string.swipe_up_on_the_lower_half_all),
            ) { prefs.setPanelSwipes(it) }
        }
        Group(tr(R.string.screen)) {
            Toggle(
                tr(R.string.lock_to_portrait), tr(R.string.prism_stays_upright_when_the_phone), s.behavior.lockPortrait,
                info = tr(R.string.on_a_phone_prism_no_longer),
            ) { prefs.setLockPortrait(it) }
            Label(tr(R.string.picture_in_picture), info = tr(R.string.when_a_playing_music_video_floats))
            Chips(com.prism.music.data.prefs.PipMode.entries, s.behavior.pip, { it.label }) { prefs.setPipMode(it) }
        }
        SessionButtonsGroup()
    }
}
