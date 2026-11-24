package com.prism.music.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.prism.music.playback.CarState

/** Android's hidden "no request at all" value (ActivityInfo.SCREEN_ORIENTATION_UNSET). */
private const val ORIENTATION_UNSET = -2

/**
 * Settings → Controls & gestures → "Lock to portrait" (#190): the orientation the phone activity should ask for now, or
 * null to leave its request as it is.
 *
 * - Portrait while the switch is on, on a phone (a tablet keeps its own layouts), and not in a car.
 * - #582: in a car (Android Auto projecting, car mode, or Android Automotive) nothing is forced. One listener's Android
 *   Auto dropped after about five minutes with the switch on and stayed up with it off; the phone's screen is the car's
 *   business while it projects.
 * - Only Prism's own two values are replaced: anything else (a full-screen video's landscape) is left alone, and it
 *   puts back what it found when it closes.
 * - Nothing when the request already is the one wanted, so the activity is never asked twice for the same thing.
 */
internal fun portraitLockOrientation(lockPortrait: Boolean, tablet: Boolean, inCar: Boolean, current: Int): Int? {
    val ours = current == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT || current == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED ||
        current == ORIENTATION_UNSET
    if (!ours) return null
    val want = if (lockPortrait && !tablet && !inCar) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    return want.takeIf { it != current && !(it == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED && current == ORIENTATION_UNSET) }
}

/** The phone is in a car ([CarState]); only watched while [watch] (the switch is on), so nothing runs otherwise. */
@Composable
private fun rememberInCar(watch: Boolean): Boolean {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var driving by remember { mutableStateOf(false) }
    // Car mode also comes with the configuration (the activity handles uiMode itself): seen at once, without a look.
    val carMode = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_CAR
    DisposableEffect(watch) {
        if (!watch) {
            driving = false
            onDispose {}
        } else {
            lateinit var state: CarState
            state = CarState(context.applicationContext, scope) { driving = state.driving }
            state.start()
            onDispose { state.stop() }
        }
    }
    return carMode || driving
}

/** The phone activity's orientation for "Lock to portrait" ([portraitLockOrientation]). Not used on the TV. */
@Composable
fun PortraitLockEffect(lockPortrait: Boolean) {
    val view = LocalView.current
    val config = LocalConfiguration.current
    val tablet = config.smallestScreenWidthDp >= 600
    val inCar = rememberInCar(lockPortrait)
    // Also after each new configuration: a full-screen video closing puts back the portrait it found, maybe since a car
    // came; then the car's "nothing forced" is put back here.
    LaunchedEffect(lockPortrait, tablet, inCar, config) {
        val activity = view.context as? Activity ?: return@LaunchedEffect
        portraitLockOrientation(lockPortrait, tablet, inCar, activity.requestedOrientation)?.let { activity.requestedOrientation = it }
    }
}
