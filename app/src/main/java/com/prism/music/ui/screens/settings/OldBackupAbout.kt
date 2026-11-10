package com.prism.music.ui.screens.settings

import com.prism.music.R
import com.prism.music.tr
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.prism.music.ui.theme.LocalAppSettings
import com.prism.music.ui.theme.LocalContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.prism.music.ui.screens.Chevron
import com.prism.music.ui.screens.Group
import com.prism.music.ui.screens.Item
import com.prism.music.ui.screens.Toggle
import com.prism.music.ui.screens.UpdateBanner

/**
 * Playback diagnostics: a log of what the audio did (the AudioTrack, underruns, devices, Prism's own processing and
 * background work), kept on the phone, for chasing stutters. Shared only when the listener shares it.
 */
@Composable
internal fun DiagnosticsGroup() {
    val c = LocalContainer.current
    val s = LocalAppSettings.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    Group(tr(R.string.diagnostics)) {
        Toggle(
            tr(R.string.playback_diagnostics), tr(R.string.a_log_for_tracking_down_stutters), s.audioDiagnostics,
            info = tr(R.string.keeps_a_log_on_this_phone),
        ) { c.settings.setAudioDiagnostics(it) }
        fun share() {
            scope.launch {
                val file = withContext(Dispatchers.IO) { com.prism.music.playback.AudioDiagnostics.export(context, com.prism.music.playback.AudioDiagnostics.about(c)) }
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.share", file)
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    .putExtra(android.content.Intent.EXTRA_SUBJECT, tr(R.string.prism_playback_diagnostics))
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                send.clipData = android.content.ClipData.newRawUri(null, uri)
                context.startActivity(android.content.Intent.createChooser(send, tr(R.string.share_diagnostics)))
            }
        }
        // A crash kept on the phone (CrashLog): there to share whether or not diagnostics are on.
        var crashAt by remember { mutableStateOf(com.prism.music.CrashLog.lastAt(context)) }
        crashAt?.let { at ->
            Item(
                tr(R.string.last_crash), tr(R.string.tap_to_share_its_details, java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(at))),
                onClick = { share() },
                info = tr(R.string.prism_closed_unexpectedly_what_went_wrong),
            ) { Chevron() }
            Item(tr(R.string.forget_crashes), null, onClick = { com.prism.music.CrashLog.clear(context); crashAt = null })
        }
        if (s.audioDiagnostics) {
            Item(tr(R.string.share_diagnostics), tr(R.string.the_log_and_audio_settings_as), onClick = { share() })
            Item(tr(R.string.clear_diagnostics), tr(R.string.starts_the_log_again), onClick = {
                scope.launch(Dispatchers.IO) { com.prism.music.playback.AudioDiagnostics.clear { com.prism.music.playback.AudioDiagnostics.about(c) } }
                android.widget.Toast.makeText(context, tr(R.string.diagnostics_cleared), android.widget.Toast.LENGTH_SHORT).show()
            })
        }
    }
}

/** Prism also checks on its own from Home; tapping this asks GitHub right now. */
@Composable
internal fun CheckForUpdates() {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    Item(
        tr(R.string.check_for_updates),
        when {
            checking -> tr(R.string.asking_github)
            result != null -> result
            else -> tr(R.string.prism_also_checks_on_its_own)
        },
        onClick = {
            if (!checking) scope.launch {
                checking = true
                result = runCatching { c.updates.checkNow() }.fold(
                    { v -> if (v != null) tr(R.string.prism_is_out_tap_below_to, v) else tr(R.string.you_re_up_to_date) },
                    { tr(R.string.couldn_t_reach_github_try_again) },
                )
                checking = false
            }
        },
    ) {
        if (checking) com.prism.music.ui.components.PrismSpinner(Modifier.size(20.dp), stroke = 2.dp)
    }
    // The update itself, installed from here (#201): the same card as Home's, which needs Home to be a tab.
    UpdateBanner(inSettings = true)
}
