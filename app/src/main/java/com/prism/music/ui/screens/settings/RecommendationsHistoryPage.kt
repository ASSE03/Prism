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

private val page = SettingsPageId.RECOMMENDATIONS_HISTORY

/**
 * Settings → Recommendations & history (1.8.4's map, plan §2.3). This file is the only place for the page: its
 * composable, its lines in Settings' search ([recommendationsHistoryIndex]), its "Reset this page" keys
 * ([recommendationsHistoryLookKeys]), its line on the root ([recommendationsHistoryValue]) and the old group names it
 * answers to ([recommendationsHistoryOldGroups]).
 *
 * Interim body: the old Recommendations & Replay page as it was ([RecommendationsPage] in `OldRecommendations.kt`,
 * with the groups of `HistorySettings.kt`), inside the kit's scaffold, until the page's rows are rebuilt with the kit.
 * Its Liked songs group ("A song and its video as one", Home & Library's by the ledger) is still here.
 */
@Composable
fun RecommendationsHistoryPage(bottomPadding: Dp, find: String? = null, showBack: Boolean = true, onSearch: (() -> Unit)? = null) {
    InterimPage(page, bottomPadding, find, showBack, onSearch) { RecommendationsPage() }
}

/** The root's line for Recommendations & history: only what's on the page itself, and "History paused" while it is. */
fun recommendationsHistoryValue(s: AppSettings, dark: Boolean): String = SettingsSection.LIBRARY.summary(s, dark)

/** The look settings "Reset this page" puts back: none (recommendations and history aren't part of a look). */
val recommendationsHistoryLookKeys: List<String> = emptyList()

/** Old group titles → this page's group keys. Empty while the old groups are drawn as they were. */
val recommendationsHistoryOldGroups: Map<String, String> = emptyMap()

/** The page's lines in Settings' search (each names the group it is drawn in today). */
val recommendationsHistoryIndex: List<SettingEntry> by lazy {
    with(SettingNeeds) {
        listOf(
            // GitHub requests in 1.7.5 (#111 #115 #116).
            SettingEntry(page, tr(R.string.liked_songs), tr(R.string.a_song_and_its_video_as), tr(R.string.merge_liked_music_video_duplicate_same)),
            SettingEntry(page, tr(R.string.made_for_you), tr(R.string.your_artists), tr(R.string.onboarding_pick_artists_favourites_starred_recom)),
            SettingEntry(page, tr(R.string.made_for_you), tr(R.string.new_release_notifications), tr(R.string.release_radar_new_music_alerts)),
            SettingEntry(page, tr(R.string.made_for_you), tr(R.string.make_my_mixes_again), tr(R.string.daily_mix_refresh_recommendations)),
            SettingEntry(page, tr(R.string.tune_radios), tr(R.string.tune_familiar_new), tr(R.string.tune_radios) + " " + tr(R.string.autoplay)),
            SettingEntry(page, tr(R.string.tune_radios), tr(R.string.tune_hits_deep), tr(R.string.tune_radios) + " " + tr(R.string.autoplay)),
            SettingEntry(page, tr(R.string.tune_radios), tr(R.string.tune_calm_energetic), tr(R.string.tune_radios) + " " + tr(R.string.autoplay)),
            SettingEntry(page, tr(R.string.replay_2), tr(R.string.show_time_in_hours), tr(R.string.stats_minutes)),
            SettingEntry(page, tr(R.string.replay_2), tr(R.string.use_theme_colors), tr(R.string.colors_stats)),
            SettingEntry(page, tr(R.string.replay_2), tr(R.string.rewind_notification), tr(R.string.rewind_wrapped_year_in_review_december)),
            SettingEntry(page, tr(R.string.replay_2), tr(R.string.replay_story_font), tr(R.string.replay_story_font_keywords)),
            // 1.8.1: History (#296, #263).
            SettingEntry(page, tr(R.string.hist_title), tr(R.string.hist_pause), tr(R.string.hist_pause_keywords)),
            SettingEntry(page, tr(R.string.hist_title), tr(R.string.hist_count_after), tr(R.string.hist_count_keywords)),
            SettingEntry(page, tr(R.string.hist_title), tr(R.string.hist_count_account), tr(R.string.hist_count_keywords),
                needs = Needs(tr(R.string.hist_needs_account)) { it.isLoggedIn },
            ),
            // 1.8.1: Bring your history.
            SettingEntry(page, tr(R.string.imp_title), tr(R.string.imp_title), tr(R.string.imp_keywords)),
            SettingEntry(page, tr(R.string.imp_title), tr(R.string.imp_shape_mixes), tr(R.string.imp_shape_mixes_keywords)),
            // On the Bring your history page (opened from this group): the fresh-download reminder.
            SettingEntry(page, tr(R.string.imp_title), tr(R.string.imp_remind), tr(R.string.imp_remind_keywords)),
            // And YouTube Music (live): the account's History page every few hours (signed in).
            SettingEntry(page, tr(R.string.imp_title), tr(R.string.ytlive_title), tr(R.string.ytlive_keywords),
                needs = Needs(tr(R.string.ytlive_sign_in)) { it.isLoggedIn },
            ),
            SettingEntry(page, tr(R.string.replay_2), tr(R.string.imp_count_history_from), tr(R.string.imp_count_keywords)),
            SettingEntry(page, tr(R.string.replay_2), tr(R.string.imp_prism_plays)),
            SettingEntry(page, tr(R.string.made_for_you), tr(R.string.exclusions), tr(R.string.taste_profile_exclude_artist_playlist_don)),
            SettingEntry(page, tr(R.string.made_for_you), tr(R.string.forget_skipped_suggestions), tr(R.string.skipped_suggestions_keywords)),
        )
    }
}
