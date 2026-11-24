package com.prism.music.ui.tour

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prism.music.R
import com.prism.music.data.model.Song
import com.prism.music.tr
import com.prism.music.ui.Routes
import com.prism.music.ui.components.PrismButton
import com.prism.music.ui.components.PrismTextButton
import com.prism.music.ui.screens.settings.SettingsPageId
import com.prism.music.ui.theme.PrismIcons

/*
 * First steps (1.8.8, plan-tour.md §3 B): a new install's tour, five things done for real on the coach ([Coach]), about a
 * minute: play a song, open the player, its lyrics, like it, Looks. Then one closing card, "That's the start…", with Open
 * the Guide and Done. Shown once, after the first Home, to a new install only (D9); done whether finished or skipped.
 * While it's pending the Home offer cards and the Holiday Mode question wait (D11, [offersWait], [holidayWaits]).
 */
object FirstSteps {
    const val COUNT = 5
    /** The step on the Settings tab, which opens Looks ([redirect]). */
    const val LOOKS_STEP = 4

    /** Where the last step is done: Settings → Looks. */
    val looksRoute: String get() = Routes.settings(SettingsPageId.LOOKS.key)

    /** The closing card's "Open the Guide" (plan §3 D). */
    val guideRoute: String get() = Routes.GUIDE

    /** In the "tour" prefs file, beside the What's new pager's "seen_version". */
    internal const val PREF = "first_steps"
    internal const val DUE = 1
    internal const val DONE = 2

    private fun prefs(context: Context) = context.getSharedPreferences("tour", Context.MODE_PRIVATE)

    /** First steps is still to come (a new install that hasn't finished or skipped it). Snapshot state. */
    var pending by mutableStateOf(false)
        private set

    /** The closing card is up (First steps was finished, not skipped). Kept here so a rotation keeps it. */
    var closing by mutableStateOf(false)

    /** The first Quick pick on Home, told by Home while a coach tour runs (D12). */
    var quickPick: Song? = null
        internal set

    /** The First steps tour running, if it is. */
    private var current: CoachRun? = null

    /**
     * At each start of the activity. [returning]: Prism was set up before this run started. A new install (not
     * returning, never shown the old tour) is marked due once, and the What's new pager marked seen at this version: a
     * new install never gets the history, only later releases' What's new (D9).
     */
    fun load(context: Context, returning: Boolean) {
        val p = prefs(context)
        if (!p.contains(PREF) && !returning && Tour.seenVersion(context) == 0) {
            p.edit().putInt(PREF, DUE).apply()
            Tour.markSeen(context)
        }
        pending = p.getInt(PREF, 0) == DUE
    }

    /** Finished or skipped: never again. */
    fun markDone(context: Context) {
        prefs(context).edit().putInt(PREF, DONE).apply()
        pending = false
    }

    /** The two Home offer cards (Performance mode, Smart Downloads) wait while First steps is to come. */
    fun offersWait(pending: Boolean = this.pending): Boolean = pending

    /**
     * The Holiday Mode question waits while First steps is to come or its closing card is up, and through the whole of
     * a new install's first session ([newInstallSession]): it comes the next day it applies.
     */
    fun holidayWaits(pending: Boolean = this.pending, closing: Boolean = this.closing, newInstallSession: Boolean): Boolean =
        pending || closing || newInstallSession

    /**
     * Starts First steps on the coach. [loadPick] puts the first Quick pick in the mini player, paused (D12). Ends marked
     * done; finished, the closing card comes up ([closing]).
     */
    fun start(context: Context, loadPick: () -> Unit): CoachRun? {
        val app = context.applicationContext
        closing = false
        val run = Coach.start(steps { loadPick() }) { end ->
            current = null
            markDone(app)
            if (end == CoachEnd.FINISHED) closing = true
        }
        current = run
        return run
    }

    /** Settings tapped during the last step opens Looks (the step is "Everything can look your way"): where to go, or null. */
    fun redirect(route: String?): String? {
        val run = current ?: return null
        if (Coach.run !== run || run.index != LOOKS_STEP) return null
        return if (route == Routes.SETTINGS) looksRoute else null
    }

    /** The five steps. [loadPick] (D12): called by a step that needs a song when none is in the player. */
    fun steps(loadPick: (TourState) -> Unit): List<CoachStep> {
        // What was there as a step began, so a song already playing or liked (First steps taken again) isn't taken as done.
        var startSong: String? = null
        var startPlaying = false
        var likedSong: String? = null
        var likedBefore = false
        return listOf(
            CoachStep(
                TourAnchor.QUICK_PICK, tr(R.string.first_steps_play),
                done = { s ->
                    if (!s.playing) startPlaying = false // paused since: playing again counts
                    s.playing && s.song != null && (s.song.id != startSong || !startPlaying)
                },
                prep = { s ->
                    startSong = s.song?.id; startPlaying = s.playing
                    setPlayerOpen(false)
                    // As Home's tab button does: taken again from the Guide, no second Home on top of it.
                    if (s.route != Routes.HOME) tab(Routes.HOME)
                },
            ),
            CoachStep(
                TourAnchor.MINI_PLAYER, tr(R.string.first_steps_player),
                done = { it.playerOpen },
                prep = { s -> setPlayerOpen(false); if (s.song == null) loadPick(s) },
            ),
            CoachStep(
                TourAnchor.PLAYER_LYRICS, tr(R.string.first_steps_lyrics),
                done = { it.lyricsOpen },
                passOver = { it.songHasLyrics == false },
                prep = { s -> if (s.song == null) loadPick(s); setPlayerOpen(true) },
            ),
            CoachStep(
                TourAnchor.PLAYER_LIKE, tr(R.string.first_steps_like),
                done = { s ->
                    if (!s.songLiked) likedBefore = false // unliked since: liking it again counts
                    s.songLiked && (!likedBefore || s.song?.id != likedSong)
                },
                prep = { s ->
                    likedSong = s.song?.id; likedBefore = s.songLiked
                    if (s.song == null) loadPick(s)
                    setPlayerOpen(true)
                },
            ),
            CoachStep(
                TourAnchor.SETTINGS_TAB, tr(R.string.first_steps_looks),
                done = { it.route == looksRoute },
                // No Settings tab in the bar: Home's profile picture opens Settings, and carries this step's mark then.
                prep = { s -> setPlayerOpen(false); if (!settingsTabShown(s) && s.route != Routes.HOME) tab(Routes.HOME) },
            ),
        )
    }

    /** The bar shows a Settings tab; without one, Home's profile picture is [TourAnchor.SETTINGS_TAB]. */
    fun settingsTabShown(s: TourState): Boolean = com.prism.music.data.prefs.NavTab.SETTINGS in s.settings.navTabs

    /** The first of [picks] Home actually shows: "Hide explicit" leaves explicit songs off its rows. */
    fun firstShown(mode: com.prism.music.data.prefs.ExplicitContent, picks: List<Song>): Song? =
        com.prism.music.data.ExplicitFilter.shown(mode, picks).firstOrNull()

    /** Home tells the first Quick pick while a coach tour runs (one line there). */
    @Composable
    fun QuickPick(song: Song?) {
        SideEffect { quickPick = song }
    }
}

const val FIRST_STEPS_CLOSING_TAG = "firstSteps:closing"
const val FIRST_STEPS_GUIDE_TAG = "firstSteps:guide"
const val FIRST_STEPS_DONE_TAG = "firstSteps:done"

/** The closing card after First steps: "That's the start. The Guide has the rest…", Open the Guide, Done. */
@Composable
fun FirstStepsClosing(onGuide: () -> Unit, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    val text = tr(R.string.first_steps_closing)
    Box(
        Modifier.fillMaxSize().background(Color(0xBD09080E))
            // Taps on the dimmed app behind do nothing while the card is up (taken here, without merging the card's words).
            .pointerInput(Unit) { detectTapGestures {} }
            .testTag(FIRST_STEPS_CLOSING_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp).widthIn(max = 380.dp).fillMaxWidth().shadow(16.dp, shape).clip(shape)
                .background(scheme.surfaceContainerHigh).padding(horizontal = 20.dp, vertical = 20.dp)
                .semantics { paneTitle = text },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(PrismIcons.Spatial, null, tint = scheme.primary)
            }
            Text(text, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, textAlign = TextAlign.Center)
            PrismButton(onClick = onGuide, modifier = Modifier.fillMaxWidth().testTag(FIRST_STEPS_GUIDE_TAG)) { Text(tr(R.string.first_steps_open_guide)) }
            PrismTextButton(onClick = onDone, modifier = Modifier.fillMaxWidth().testTag(FIRST_STEPS_DONE_TAG)) { Text(tr(R.string.done), color = scheme.onSurfaceVariant) }
        }
    }
}
