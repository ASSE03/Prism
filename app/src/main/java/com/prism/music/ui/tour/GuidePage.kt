package com.prism.music.ui.tour

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prism.music.R
import com.prism.music.tr
import com.prism.music.ui.LocalNavigator
import com.prism.music.ui.Routes
import com.prism.music.ui.components.PrismButton
import com.prism.music.ui.components.PrismTextButton
import com.prism.music.ui.components.SearchField
import com.prism.music.ui.screens.settings.LinkRow
import com.prism.music.ui.screens.settings.SettingsPageId
import com.prism.music.ui.screens.settings.SettingsPageScaffold
import com.prism.music.ui.screens.settings.settingsCard
import com.prism.music.ui.theme.LocalUi
import com.prism.music.ui.theme.PrismTokens

/*
 * The Guide (1.8.8, plan-tour.md §3 D, D6): where the tour's cards live now. Settings → Backup & about → Guide opens a
 * page of the eight topics ([TourTopic]) with a search line and, at the top, "Take First steps again"; a topic shows its
 * cards, newest first, and the four most used topics a "Show me how": a short coach tour done for real ([GuideTours]).
 * No card was rewritten: they are the tour's own ([Tour.steps]). TV keeps its own welcome: no "Show me how" there.
 */

object GuideTags {
    const val SEARCH = "guide:search"
    const val TOPIC = "guide:topic"
    const val NOTHING = "guide:nothing"
    const val FIRST_STEPS = "guide:firstSteps"
    const val SHOW_ME_HOW = "guide:showMeHow"
    const val CARD = "guide:card"
}

/** The topics whose name, line or cards hold [query] (all of them for a blank one), in the Guide's order. */
fun guideTopics(query: String): List<TourTopic> {
    val q = query.trim()
    if (q.isEmpty()) return TourTopic.entries
    return TourTopic.entries.filter { t ->
        (listOf(t.title, t.line) + t.cards.flatMap { listOf(it.title, it.body) + it.points }).any { it.contains(q, ignoreCase = true) }
    }
}

/**
 * The Guide's "Show me how" tours (plan-tour.md §3 D, D13, the mockup): short coach tours of one or two steps for the
 * four most used topics. Their steps are First steps' own, done for real.
 */
object GuideTours {
    // What was there as the step began, so music already playing or a song already liked isn't taken as done (as First
    // steps does): a step made anew for each tour, with its own.
    private val play: CoachStep get() {
        var startSong: String? = null
        var startPlaying = false
        return CoachStep(
            TourAnchor.QUICK_PICK, tr(R.string.first_steps_play),
            done = { s ->
                if (!s.playing) startPlaying = false // paused since: playing again counts
                s.playing && s.song != null && (s.song.id != startSong || !startPlaying)
            },
            prep = { s -> startSong = s.song?.id; startPlaying = s.playing; setPlayerOpen(false); tab(Routes.HOME) },
        )
    }
    private val player get() = CoachStep(
        TourAnchor.MINI_PLAYER, tr(R.string.first_steps_player), done = { it.playerOpen },
        prep = { setPlayerOpen(false) },
    )
    private val lyrics get() = CoachStep(
        TourAnchor.PLAYER_LYRICS, tr(R.string.first_steps_lyrics), done = { it.lyricsOpen },
        // A song without lyrics: the step is passed over.
        passOver = { it.playerOpen && it.songHasLyrics == false },
        prep = { setPlayerOpen(true) },
    )
    private val like: CoachStep get() {
        var likedSong: String? = null
        var likedBefore = false
        return CoachStep(
            TourAnchor.PLAYER_LIKE, tr(R.string.first_steps_like),
            done = { s ->
                if (!s.songLiked) likedBefore = false // unliked since: liking it again counts
                s.songLiked && (!likedBefore || s.song?.id != likedSong)
            },
            prep = { s -> likedSong = s.song?.id; likedBefore = s.songLiked; setPlayerOpen(true) },
        )
    }
    private val libraryTab get() = CoachStep(
        TourAnchor.LIBRARY_TAB, tr(R.string.guide_how_library), done = { it.route == Routes.LIBRARY },
        prep = { setPlayerOpen(false) },
    )
    private val looks get() = CoachStep(
        TourAnchor.SETTINGS_TAB, tr(R.string.first_steps_looks), done = { isSettings(it.route) },
        // No Settings tab: Home's profile picture carries the mark (1.8.8, as First steps).
        prep = { s -> setPlayerOpen(false); if (!FirstSteps.settingsTabShown(s) && s.route != Routes.HOME) tab(Routes.HOME) },
    )

    /** Settings, its first page or any page of it. */
    internal fun isSettings(route: String?) = route == Routes.SETTINGS || route?.startsWith("settings/") == true

    /** The topics that have one. */
    val topics: Set<TourTopic> = setOf(TourTopic.PLAYING, TourTopic.LYRICS, TourTopic.LIBRARY, TourTopic.LOOKS)

    /** [topic]'s steps, none for a topic without a tour. */
    fun of(topic: TourTopic): List<CoachStep> = when (topic) {
        TourTopic.PLAYING -> listOf(play, player)
        TourTopic.LYRICS -> listOf(lyrics)
        // D13: the heart, then the Library tab.
        TourTopic.LIBRARY -> listOf(like, libraryTab)
        TourTopic.LOOKS -> listOf(looks)
        else -> emptyList()
    }

    /** Where a finished tour leaves the person: Looks' tour opens Looks itself, as First steps' last step does. */
    fun after(topic: TourTopic): String? = if (topic == TourTopic.LOOKS) Routes.settings(SettingsPageId.LOOKS.key) else null

    /** Runs [topic]'s tour; [go] opens where it leaves off. */
    fun start(topic: TourTopic, go: (String) -> Unit): CoachRun? =
        Coach.start(of(topic)) { end -> if (end == CoachEnd.FINISHED) after(topic)?.let(go) }
}

/**
 * Settings → Backup & about → Guide. [onFirstSteps] starts First steps again (part B's tour); without it the card isn't
 * shown.
 */
@Composable
fun GuideScreen(bottomPadding: Dp, onFirstSteps: (() -> Unit)? = null) {
    val nav = LocalNavigator.current
    GuideContent(bottomPadding, onFirstSteps) { nav.go(Routes.guideTopic(it.name.lowercase())) }
}

/** The Guide's page: First steps, the search line, the topics. */
@Composable
fun GuideContent(bottomPadding: Dp, onFirstSteps: (() -> Unit)?, onTopic: (TourTopic) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val topics = remember(query) { guideTopics(query) }
    SettingsPageScaffold(tr(R.string.guide), bottomPadding) {
        if (onFirstSteps != null) item("guide:first") {
            Offer(tr(R.string.guide_first_steps), tr(R.string.guide_first_steps_line), tr(R.string.guide_first_steps_again), GuideTags.FIRST_STEPS, onFirstSteps)
        }
        item("guide:search") {
            SearchField(query, tr(R.string.guide_search_hint), { query = it }, Modifier.padding(bottom = 14.dp).testTag(GuideTags.SEARCH))
        }
        if (topics.isEmpty()) item("guide:nothing") {
            Text(
                tr(R.string.guide_nothing_found), Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).testTag(GuideTags.NOTHING),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else group(null, key = "guide:topics") {
            for (t in topics) LinkRow(t.title, Modifier.testTag(GuideTags.TOPIC), value = t.line, icon = t.icon) { onTopic(t) }
        }
    }
}

/** One of the Guide's topics, by its name in lower case (an unknown one shows nothing). */
@Composable
fun GuideTopicScreen(bottomPadding: Dp, name: String?) {
    val topic = TourTopic.entries.firstOrNull { it.name.lowercase() == name } ?: return
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val tv = remember { com.prism.music.ui.tv.TvDevice.isTv(context) }
    GuideTopicContent(
        bottomPadding, topic,
        onShowMeHow = if (tv || topic !in GuideTours.topics) null else ({ GuideTours.start(topic) { nav.go(it) } }),
        onShowMe = { nav.go(it) },
    )
}

/** A topic's page: its "Show me how" ([onShowMeHow], none without), then its cards, newest first. */
@Composable
fun GuideTopicContent(bottomPadding: Dp, topic: TourTopic, onShowMeHow: (() -> Unit)?, onShowMe: (String) -> Unit) {
    val cards = remember(topic) { topic.cards }
    SettingsPageScaffold(topic.title, bottomPadding) {
        if (onShowMeHow != null) item("guide:how") {
            Offer(tr(R.string.guide_show_me_how), tr(R.string.guide_show_me_how_line), tr(R.string.start), GuideTags.SHOW_ME_HOW, onShowMeHow)
        }
        cards.forEachIndexed { i, card -> item("guide:card:$i") { GuideCard(card, onShowMe) } }
    }
}

/** A card with a title, a line and one button (First steps, Show me how). */
@Composable
private fun Offer(title: String, line: String, button: String, tag: String, onClick: () -> Unit) {
    val shape = LocalUi.current.shape(20.dp)
    Row(
        Modifier.padding(bottom = 14.dp).fillMaxWidth().settingsCard(shape, tag).padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        PrismButton(onClick = onClick) { Text(button) }
    }
}

/** One tour card as the Guide shows it: icon, title, the release it came in, its words and points, and Show me. */
@Composable
internal fun GuideCard(step: TourStep, onShowMe: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = LocalUi.current.shape(20.dp)
    Column(
        Modifier.padding(bottom = 12.dp).fillMaxWidth().settingsCard(shape, GuideTags.CARD).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(scheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(step.icon, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(step.title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, modifier = Modifier.semantics { heading() })
                Tour.versionNames[step.since]?.let {
                    Text(tr(R.string.guide_since, it), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
            }
        }
        Text(step.body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
        step.points.forEachIndexed { i, p ->
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 7.dp).size(7.dp).clip(CircleShape).background(PrismTokens.hue(i % 7)))
                Spacer(Modifier.width(12.dp))
                Text(p, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
        step.showMe?.let { route ->
            PrismTextButton(onClick = { onShowMe(route) }) { Text(tr(R.string.show_me)) }
        }
    }
}
