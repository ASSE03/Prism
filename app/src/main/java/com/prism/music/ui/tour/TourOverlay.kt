package com.prism.music.ui.tour

import com.prism.music.R
import com.prism.music.tr
import com.prism.music.trp
import android.content.Context
import com.prism.music.ui.components.tickWhileShown
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prism.music.BuildConfig
import com.prism.music.data.Notifications
import com.prism.music.ui.components.PrismButton
import com.prism.music.ui.components.PrismOutlinedButton
import com.prism.music.ui.components.PrismTextButton
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.theme.PrismIcons
import com.prism.music.ui.theme.PrismTokens
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

/**
 * What What's new shows: an opening page, then [steps] (the newest release's cards), the last of them ending with the
 * line "And [more] more things since you last looked" when older releases were skipped (it opens the Guide).
 * [welcome]: someone from before tours ("Welcome to the new Prism"). [start] is the page it opens at (0, the opening;
 * after a "Show me", the card after the one it came from).
 */
class TourPlan(val welcome: Boolean, val steps: List<TourStep>, val more: Int = 0, val start: Int = 0) {
    val pages: Int get() = steps.size + 1

    /** The same, opening at [page]. */
    fun at(page: Int) = TourPlan(welcome, steps, more, page.coerceIn(0, pages - 1))
}

/**
 * What's new for someone who last saw the tour at [seen] (1.8.8, plan-tour.md §3 C, D7): the newest release's cards,
 * the cards of any older release since folded into [TourPlan.more]. Null when there's nothing new, and for a new
 * install ([seen] 0, not [returning]): that gets First steps, never the history. Someone from before tours ([seen] 0,
 * [returning]) gets the newest release's cards and the Guide line too, not every page.
 */
fun whatsNewPlan(seen: Int, returning: Boolean): TourPlan? {
    if (seen == 0 && !returning) return null
    val after = Tour.stepsAfter(seen)
    if (after.isEmpty()) return null
    val newest = after.maxOf { it.since }
    val cards = after.filter { it.since == newest && it.kind == TourStep.Kind.PLAIN }
    if (cards.isEmpty()) return null
    return TourPlan(welcome = seen == 0, steps = cards, more = after.size - cards.size)
}

/** What's new to show now, if any ([whatsNewPlan] for the tour last seen). */
fun pendingTour(context: Context, returning: Boolean): TourPlan? = whatsNewPlan(Tour.seenVersion(context), returning)

/**
 * After What's new's "Show me" (1.8.8): the cards to come back to, and the page the person was on. The chip "Back to
 * what's new" ([BackToWhatsNew]) is shown while this is set.
 */
object WhatsNewReturn {
    class Pending internal constructor(val plan: TourPlan, internal val from: String?, internal val run: CoachRun?)

    var pending by mutableStateOf<Pending?>(null)
        private set

    internal fun arm(plan: TourPlan, from: String?, run: CoachRun?) { pending = Pending(plan, from, run) }

    fun clear() { pending = null }
}

const val WHATS_NEW_BACK_TAG = "whatsNew:back"
const val WHATS_NEW_MORE_TAG = "whatsNew:more"
const val WHATS_NEW_SHOW_ME_TAG = "whatsNew:showMe"

/**
 * What's new's "Show me" for [step] on [page] of [plan]: the tour steps aside (seen, as before), the place opens with
 * the coach's hole on the control when the card names one ([TourStep.coach]; not on a TV), else as it always did, and
 * the chip back to the next card is armed. [here] is the page showing now (to return to).
 */
internal fun whatsNewShowMe(plan: TourPlan, page: Int, step: TourStep, here: String?, tv: Boolean, open: (String) -> Unit) {
    val route = step.showMe ?: return
    val coach = step.coach
    val run = if (coach != null && !tv) {
        Coach.start(
            listOf(CoachStep(coach.anchor, coach.caption, coach.done, prep = { setPlayerOpen(false); go(route) })),
            label = tr(R.string.whats_new),
        )
    } else null
    if (run == null) open(route)
    WhatsNewReturn.arm(plan.at(page + 1), here, run)
}

/**
 * The chip "Back to what's new", top left, after a "Show me": it puts back the page the person was on and opens What's
 * new at the next card ([onReturn]). It goes on its own when the person goes back to that page themselves. Nothing is
 * drawn (or watched) otherwise.
 */
@Composable
fun BackToWhatsNew(modifier: Modifier = Modifier, onReturn: (TourPlan) -> Unit) {
    val p = WhatsNewReturn.pending ?: return
    val nav = com.prism.music.ui.LocalNavigator.current.nav
    val entry by nav.currentBackStackEntryAsState()
    var left by remember(p) { mutableStateOf(false) }
    LaunchedEffect(p, entry?.id) {
        val id = entry?.id ?: return@LaunchedEffect
        if (id != p.from) left = true else if (left) WhatsNewReturn.clear()
    }
    val scheme = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 8.dp, start = 12.dp)
                .shadow(8.dp, CircleShape).clip(CircleShape).background(scheme.primaryContainer)
                .clickable {
                    WhatsNewReturn.clear()
                    p.run?.let { if (Coach.run === it) Coach.skip() }
                    if (p.from != null && nav.currentBackStack.value.any { it.id == p.from }) {
                        while (nav.currentBackStackEntry?.id != p.from && nav.popBackStack()) Unit
                    }
                    onReturn(p.plan)
                }
                .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp).testTag(WHATS_NEW_BACK_TAG),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(PrismIcons.ArrowBack, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(tr(R.string.back_to_whats_new), color = scheme.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * What's new: a page at a time over the app, swiped or stepped through with Next, skippable at any point. Finishing or
 * skipping it marks it seen for this version ([Tour.markSeen]). "Show me" opens the place (the coach's hole on the
 * control when the card names one) with a chip back to the next card; the "more things" line opens the Guide.
 */
@Composable
fun TourOverlay(plan: TourPlan, onOpen: (String) -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val pages = plan.pages
    val pager = rememberPagerState(initialPage = plan.start) { pages }
    val scope = rememberCoroutineScope()
    val nav = com.prism.music.ui.LocalNavigator.current
    val tv = remember { com.prism.music.ui.tv.TvDevice.isTv(context) }
    fun finish() { Tour.markSeen(context); onDone() }
    BackHandler { if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } else finish() }
    val shown by animateFloatAsState(1f, tween(320), label = "tourIn")

    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = shown }
            .background(Color(0xF20B0A10))
            // Taps on the dimmed app behind do nothing while the tour is up.
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        // The split light, faint, behind the cards.
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(PrismTokens.spectrumColors.map { (_, c) -> c.copy(alpha = 0.10f) }, radius = 1400f),
            ),
        )
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (pager.currentPage == 0) "" else tr(R.string.of_2, pager.currentPage, pages - 1),
                    color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
                PrismTextButton(onClick = ::finish) { Text(tr(R.string.skip_tour), color = Color.White.copy(alpha = 0.8f)) }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                Box(
                    Modifier.fillMaxSize().graphicsLayer { alpha = 1f - offset * 0.6f; val s = 1f - offset * 0.06f; scaleX = s; scaleY = s },
                    contentAlignment = Alignment.Center,
                ) {
                    if (page == 0) Opening(plan)
                    else StepPage(
                        plan.steps[page - 1], current = pager.settledPage == page,
                        more = if (page == pages - 1) plan.more else 0,
                        onShowMe = {
                            val step = plan.steps[page - 1]
                            val here = nav.nav.currentBackStackEntry?.id
                            finish()
                            whatsNewShowMe(plan, page, step, here, tv, onOpen)
                        },
                        onMore = { finish(); onOpen(com.prism.music.ui.Routes.GUIDE) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (pager.currentPage > 0) PrismTextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                        Text(tr(R.string.back), color = Color.White.copy(alpha = 0.8f))
                    }
                }
                Dots(pages, pager.currentPage)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    val last = pager.currentPage == pages - 1
                    PrismButton(onClick = { if (last) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                        Text(if (last) tr(R.string.done) else if (pager.currentPage == 0) tr(R.string.start) else tr(R.string.widget_next))
                    }
                }
            }
        }
    }
}

@Composable
private fun Opening(plan: TourPlan) {
    // A new install gets First steps, never this pager (plan-tour.md §3 B).
    val (title, body) = when {
        !plan.welcome -> tr(R.string.what_s_new_in_prism, BuildConfig.VERSION_NAME) to
            trp(R.plurals.n_things_to_know, plan.steps.size)
        else -> tr(R.string.welcome_to_the_new_prism) to tr(R.string.prism_is_redrawn_from_the_ground, BuildConfig.VERSION_NAME)
    }
    PageColumn {
        Halo(PrismIcons.Spatial, big = true)
        Spacer(Modifier.height(30.dp))
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(12.dp))
        Text(body, color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Text(tr(R.string.whats_new_in_the_guide), color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StepPage(step: TourStep, current: Boolean, more: Int, onShowMe: () -> Unit, onMore: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    PageColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Halo(step.icon, big = true)
            if (step.extras.isNotEmpty()) Column(Modifier.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                step.extras.forEach { Halo(it, big = false) }
            }
        }
        Spacer(Modifier.height(26.dp))
        Text(step.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(10.dp))
        Text(step.body, color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (step.points.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.07f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                step.points.forEachIndexed { i, p ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 7.dp).size(7.dp).clip(CircleShape).background(PrismTokens.hue(i % 7)))
                        Spacer(Modifier.width(12.dp))
                        Text(p, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (step.kind == TourStep.Kind.NOTIFICATIONS) {
            Spacer(Modifier.height(22.dp))
            var allowed by remember { mutableStateOf(Notifications.allowed(context)) }
            // After Android's prompt answers, look again (it shows over the app, and Prism resumes under it).
            val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
            LaunchedEffect(lifecycle) { lifecycle.tickWhileShown(700) { allowed = Notifications.allowed(context) } }
            if (allowed) Text(tr(R.string.notifications_are_on), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
            else if (Build.VERSION.SDK_INT >= 33) PrismOutlinedButton(onClick = {
                context.getSharedPreferences("prompts", Context.MODE_PRIVATE).edit().putBoolean("notifications_asked", true).apply()
                c.notificationPrompt?.invoke()
            }) { Text(tr(R.string.allow_notifications), color = Color.White) }
            else Text(tr(R.string.turn_them_on_in_android_s), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
        }
        if (step.showMe != null) {
            Spacer(Modifier.height(18.dp))
            PrismOutlinedButton(onClick = onShowMe, modifier = Modifier.testTag(WHATS_NEW_SHOW_ME_TAG)) { Text(tr(R.string.show_me), color = Color.White) }
        }
        // 1.8.7.6: the release's last card may end with one quiet line to support Prism (once per release).
        if (step.support) {
            Spacer(Modifier.height(18.dp))
            com.prism.music.ui.support.SupportWhatsNewLine(step.since, current)
        }
        // 1.8.8: the older releases skipped, folded into one line that opens the Guide (plan-tour.md D7).
        if (more > 0) {
            Spacer(Modifier.height(14.dp))
            PrismTextButton(onClick = onMore, modifier = Modifier.testTag(WHATS_NEW_MORE_TAG)) {
                Text(trp(R.plurals.n_more_things_since, more), color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
            }
        }
    }
}

/** A page's content: a phone's width, centred, scrolling if a small screen needs it. */
@Composable
private fun PageColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.widthIn(max = 460.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

/** An icon on frosted glass ringed with the split light. */
@Composable
private fun Halo(icon: ImageVector, big: Boolean) {
    val size = if (big) 112.dp else 46.dp
    Box(
        Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
            .border(if (big) 2.dp else 1.5.dp, PrismTokens.spectrumRing(), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(if (big) 48.dp else 22.dp))
    }
}

@Composable
private fun Dots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        // Many pages: a short run around the current one, so the dots never crowd the buttons.
        val shown = if (count <= 9) 0 until count else (current - 4).coerceIn(0, count - 9).let { it until it + 9 }
        for (i in shown) Box(
            Modifier.size(if (i == current) 8.dp else 6.dp).clip(CircleShape)
                .background(if (i == current) Color.White else Color.White.copy(alpha = 0.3f)),
        )
    }
}
