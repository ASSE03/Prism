package com.prism.music.ui.screens.settings

import com.prism.music.R
import com.prism.music.tr
import com.prism.music.ui.components.PrismTextButton
import com.prism.music.ui.theme.PrismIcons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.prism.music.ui.LocalNavigator
import com.prism.music.ui.Routes
import com.prism.music.ui.theme.LocalAppSettings
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.theme.LocalUi
import kotlinx.coroutines.launch
import com.prism.music.ui.screens.Chevron
import com.prism.music.ui.screens.ChoiceCards
import com.prism.music.ui.screens.Group
import com.prism.music.ui.screens.Item
import com.prism.music.ui.screens.Label
import com.prism.music.ui.screens.label
import com.prism.music.ui.screens.ListeningHistoryGroup
import com.prism.music.ui.screens.Note
import com.prism.music.ui.screens.SettingsSection
import com.prism.music.ui.screens.SliderSetting
import com.prism.music.ui.screens.StoryTypeSample
import com.prism.music.ui.screens.Toggle
import com.prism.music.ui.screens.worded

@Composable
internal fun RecommendationsPage() {
    val c = LocalContainer.current
    val s = LocalAppSettings.current
    val prefs = c.settings
    val nav = LocalNavigator.current
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Group(tr(R.string.made_for_you)) {
            Item(tr(R.string.your_artists), tr(R.string.they_shape_your_mixes_and_release), onClick = { nav.go(Routes.PICK_ARTISTS) }) { Chevron() }
            Toggle(tr(R.string.new_release_notifications), tr(R.string.when_a_starred_artist_puts_out), s.releaseNotifications) { prefs.setReleaseNotifications(it) }
            Item(tr(R.string.make_my_mixes_again), tr(R.string.fresh_daily_mixes_now), onClick = {
                c.scope.launch { c.forYou.refresh(force = true) }
                android.widget.Toast.makeText(context, tr(R.string.making_your_mixes), android.widget.Toast.LENGTH_SHORT).show()
            })
            Item(tr(R.string.exclusions), tr(R.string.what_s_kept_out_of_your), info = tr(R.string.artists_not_to_play_and_playlists), onClick = { nav.go(Routes.EXCLUSIONS) }) { Chevron() }
            // Suggestions skipped at once stay out of mixes and radio for a while (1.7.9): this lets them all back.
            Item(tr(R.string.forget_skipped_suggestions), tr(R.string.let_every_song_be_suggested_again), info = tr(R.string.forget_skipped_suggestions_info), onClick = {
                c.recFeedback.clear()
                android.widget.Toast.makeText(context, tr(R.string.suggestions_start_fresh), android.widget.Toast.LENGTH_SHORT).show()
            })
        }
        // 1.8.2: each slider goes by what the phone really knows, and says so behind its ⓘ.
        Group(tr(R.string.tune_radios), info = tr(R.string.tune_radios_info)) { RadioTuneSliders() }
        Group(tr(R.string.liked_songs)) {
            Toggle(
                tr(R.string.a_song_and_its_video_as), tr(R.string.liked_both_they_show_once_as), s.mergeLikedVideos,
                info = tr(R.string.when_you_ve_liked_a_song),
            ) { prefs.setMergeLikedVideos(it) }
        }
        // 1.8.1: histories from Spotify, YouTube Music (Takeout) and Last.fm.
        val imported by c.imports.summaries.collectAsState(emptyList())
        val teach by c.imports.teach.collectAsState()
        val counted by c.imports.counted.collectAsState()
        Group(tr(R.string.imp_title), info = tr(R.string.imp_group_info)) {
            Item(tr(R.string.imp_title), tr(R.string.imp_row_detail), onClick = { nav.go(Routes.BRING_HISTORY) }) { Chevron() }
            if (imported.isNotEmpty()) Toggle(
                tr(R.string.imp_shape_mixes), tr(R.string.imp_shape_mixes_detail), teach, info = tr(R.string.imp_shape_mixes_info),
            ) { c.imports.setTeach(it) }
        }
        // 1.8.1: pause history (#296) and when a song counts as played (#263).
        ListeningHistoryGroup()
        // 1.8.0: the Last.fm link here is gone (its page is in Devices & connections; Settings search finds it).
        Group(tr(R.string.replay_2)) {
            Toggle(tr(R.string.show_time_in_hours), if (s.replayHours) tr(R.string.like_20h_4m) else tr(R.string.like_1_204_min), s.replayHours) { prefs.setReplayHours(it) }
            Toggle(tr(R.string.use_theme_colors), tr(R.string.your_accent_instead_of_prism_s), s.replayThemeColors) { prefs.setReplayThemeColors(it) }
            // #287: the stories' type, each shown as a slide would set it.
            Label(tr(R.string.replay_story_font), info = tr(R.string.replay_story_font_info))
            ChoiceCards(
                listOf(false, true), s.view.replayStoryFont,
                { if (it) tr(R.string.replay_story_font_own) else tr(R.string.replay_story_font_poster) }, { null },
                onSelect = { prefs.setReplayStoryFont(it) },
            ) { StoryTypeSample(it) }
            Toggle(tr(R.string.rewind_notification), tr(R.string.on_december_1_when_your_year), s.rewindNotification) { prefs.setRewindNotification(it) }
            // Prism's plays always count; each imported source only when switched on (off by default: Replay as before).
            // 1.8.1 (owner): every source is listed; one not brought in (or a live one that's off) is greyed, with the way to it.
            CountHistoryFrom(imported, counted)
        }
        // Region moved to the Language page (1.8.0).
    }
}

/**
 * Tune a radio (1.8.2): three sliders over every radio and autoplay, each with both its ends named. In the middle (where
 * they start) a radio plays as it comes. Halfway to an end is a real step: see [com.prism.music.data.foryou.RadioTuner].
 */
@Composable
private fun RadioTuneSliders() {
    val c = LocalContainer.current
    val tune = LocalAppSettings.current.behavior.radioTune
    fun word(v: Float, low: String, high: String) = when {
        kotlin.math.abs(v) < 0.05f -> tr(R.string.tune_as_it_comes)
        v < 0 -> worded(low, -v)
        else -> worded(high, v)
    }
    // Close to the middle is the middle: "as it comes" has to be easy to get back to.
    fun snap(v: Float) = if (kotlin.math.abs(v) < 0.05f) 0f else v
    SliderSetting(
        tr(R.string.tune_familiar_new), tune.newness, -1f..1f, { word(it, tr(R.string.tune_familiar), tr(R.string.tune_new)) }, steps = 7,
        info = tr(R.string.tune_familiar_new_info),
    ) { c.settings.setRadioTune(tune.copy(newness = snap(it))) }
    SliderSetting(
        tr(R.string.tune_hits_deep), tune.depth, -1f..1f, { word(it, tr(R.string.tune_hits), tr(R.string.tune_deep)) }, steps = 7,
        info = tr(R.string.tune_hits_deep_info),
    ) { c.settings.setRadioTune(tune.copy(depth = snap(it))) }
    SliderSetting(
        tr(R.string.tune_calm_energetic), tune.energy, -1f..1f, { word(it, tr(R.string.tune_calm), tr(R.string.tune_energetic)) }, steps = 7,
        info = tr(R.string.tune_calm_energetic_info),
    ) { c.settings.setRadioTune(tune.copy(energy = snap(it))) }
    // How much the last one has to go by: a figure, not an explanation.
    if (tune.energy != 0f) {
        val measured = remember { c.mixTempo.measuredCount() }
        Note(tr(R.string.tune_measured_songs, measured))
    }
}

/**
 * "Count history from" (1.8.1): Prism's plays (always), then every source in order. One with plays here (or a live one
 * that's on) has its switch; the others are greyed with "Bring it in" (Bring your history) or "Turn on" (the live ones'
 * own switches), and never count.
 */
@Composable
private fun CountHistoryFrom(imported: List<com.prism.music.data.db.ImportSummary>, counted: Set<com.prism.music.data.imports.ImportSource>) {
    val c = LocalContainer.current
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val ytLive by c.imports.youtubeLive.on.collectAsState()
    // Spotify's connection is only built when it's connected (most never connect; see Spotify.pageOpened).
    val spotifyConnected = remember {
        com.prism.music.data.spotify.SpotifyStore.connectedIn(
            context.getSharedPreferences(com.prism.music.data.spotify.SpotifyStore.AUTH_PREFS, android.content.Context.MODE_PRIVATE),
        )
    }
    val spotifyLive = if (spotifyConnected) c.spotify.options.collectAsState().value.recent else false
    val liveOn = buildSet {
        if (ytLive) add(com.prism.music.data.imports.ImportSource.YOUTUBE_LIVE)
        if (spotifyLive) add(com.prism.music.data.imports.ImportSource.SPOTIFY_LIVE)
    }
    val count = java.text.NumberFormat.getIntegerInstance()
    Label(tr(R.string.imp_count_history_from), info = tr(R.string.imp_count_history_info))
    Item(tr(R.string.imp_prism_plays), tr(R.string.imp_always_counted), onClick = {}) {
        Icon(PrismIcons.Check, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    com.prism.music.data.imports.CountSources.rows(imported, liveOn, counted).forEach { row ->
        val source = row.source
        if (row.state == com.prism.music.data.imports.CountSources.State.COUNTABLE) {
            val detail = if (row.plays > 0) tr(R.string.imp_n_plays, count.format(row.plays)) else tr(R.string.imp_live_no_plays)
            Toggle(source.label, detail, row.counted) { c.imports.setCounted(source, it) }
        } else {
            val go = {
                if (source == com.prism.music.data.imports.ImportSource.SPOTIFY_LIVE) nav.go(Routes.settings(SettingsSection.SPOTIFY.key))
                else nav.go(Routes.BRING_HISTORY)
            }
            MissingSource(source.label, if (source.isLive) tr(R.string.off) else tr(R.string.imp_not_brought_in), if (source.isLive) tr(R.string.imp_turn_on) else tr(R.string.imp_bring_it_in), go)
        }
    }
}

/** A source with nothing to count yet: its name greyed, and the way to bring it in. */
@Composable
private fun MissingSource(title: String, detail: String, action: String, onClick: () -> Unit) {
    val ui = com.prism.music.ui.theme.LocalUi.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 8.dp, top = ui.gap(8.dp), bottom = ui.gap(8.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).alpha(0.5f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        com.prism.music.ui.components.PrismTextButton(onClick = onClick) { Text(action) }
    }
}
