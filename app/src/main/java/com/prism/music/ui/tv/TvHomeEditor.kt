package com.prism.music.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.prism.music.R
import com.prism.music.data.model.Shelf
import com.prism.music.data.prefs.HomeSectionConfig
import com.prism.music.data.prefs.HomeSections
import com.prism.music.tr
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.theme.PrismIcons
import kotlinx.coroutines.launch

/** One row of Home in the editor: its name and whether it shows. */
data class TvHomeEditorRow(val key: String, val title: String, val shown: Boolean)

/**
 * Customize Home (TV, 1.8.0): Home's rows top to bottom, as they'll be. The D-pad moves between them; OK shows or hides
 * one; Move beside it (or OK held) picks it up, Up and Down carry it, OK (or Back) puts it down. "Back to default" is
 * Home as it comes. Each change is kept at once.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TvHomeEditorScreen(
    rows: List<TvHomeEditorRow>,
    moving: String?,
    onToggle: (String) -> Unit,
    onPickUp: (String) -> Unit,
    onStep: (String, Int) -> Unit,
    onPutDown: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val list = rememberLazyListState()
    // The row focus opens on: the first as the page opened (not whichever is first after a move, which would take
    // focus from the row being carried).
    val firstKey = remember { rows.firstOrNull()?.key }
    Column(modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 27.dp).testTag(TAG_HOME_EDITOR)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr(R.string.customize_home), style = MaterialTheme.typography.headlineMedium, color = scheme.onBackground)
                Spacer(Modifier.height(6.dp))
                val moved = rows.firstOrNull { it.key == moving }
                Text(
                    if (moved != null) tr(R.string.tv_home_moving, moved.title) else tr(R.string.tv_home_editor_how),
                    style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant,
                    modifier = Modifier.testTag("$TAG_HOME_EDITOR/how"),
                )
            }
            Spacer(Modifier.width(24.dp))
            TvButton(tr(R.string.tv_home_back_to_default), onReset, Modifier.tvItem("$TAG_HOME_EDITOR/reset"), icon = PrismIcons.Undo)
        }
        Spacer(Modifier.height(20.dp))
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().rememberedFocus(),
            state = list,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 27.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(rows, key = { it.key }) { r ->
                val isMoving = r.key == moving
                val bring = remember { BringIntoViewRequester() }
                val scope = rememberCoroutineScope()
                // A row being carried stays in view as it goes past the list's edge.
                LaunchedEffect(isMoving, rows.indexOf(r)) { if (isMoving) runCatching { bring.bringIntoView() } }
                Row(
                    Modifier.fillMaxWidth().bringIntoViewRequester(bring).onPreviewKeyEvent { e ->
                        if (!isMoving || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionUp -> { onStep(r.key, -1); scope.launch { runCatching { bring.bringIntoView() } }; true }
                            Key.DirectionDown -> { onStep(r.key, 1); scope.launch { runCatching { bring.bringIntoView() } }; true }
                            // Carried, it stays itself: Left and Right don't wander off to the other button.
                            Key.DirectionLeft, Key.DirectionRight -> true
                            else -> false
                        }
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val click = { if (isMoving) onPutDown() else onToggle(r.key) }
                    val hold = { if (isMoving) onPutDown() else onPickUp(r.key) }
                    var held by remember { mutableStateOf(false) }
                    ListItem(
                        selected = isMoving,
                        onClick = click,
                        onLongClick = hold,
                        // OK pressed or held is read here, by how long the key has been down (a remote's repeats, or one
                        // key-down and its key-up apart), so a hold never also counts as a press when it's let go.
                        modifier = Modifier.weight(1f).onPreviewKeyEvent { e ->
                            if (e.key != Key.DirectionCenter && e.key != Key.Enter && e.key != Key.NumPadEnter) return@onPreviewKeyEvent false
                            val n = e.nativeKeyEvent
                            val long = n.eventTime - n.downTime >= HOLD_MS
                            when (e.type) {
                                KeyEventType.KeyDown -> if (long && !held) { held = true; hold() }
                                KeyEventType.KeyUp -> when {
                                    held -> held = false
                                    long -> hold()
                                    else -> click()
                                }
                            }
                            true
                        }.tvItem("$TAG_HOME_EDITOR/row/${r.key}", entry = r.key == firstKey),
                        leadingContent = { Icon(if (isMoving) PrismIcons.SwapVertical else if (r.shown) PrismIcons.Visible else PrismIcons.Hidden, null, Modifier.size(22.dp)) },
                        headlineContent = { Text(r.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(tr(if (r.shown) R.string.shown else R.string.hidden), maxLines = 1) },
                        trailingContent = { Switch(checked = r.shown, onCheckedChange = null) },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent, selectedContainerColor = TvGlassFill, selectedContentColor = scheme.onSurface,
                            focusedContainerColor = scheme.onSurface, focusedContentColor = scheme.surface,
                            focusedSelectedContainerColor = scheme.onSurface, focusedSelectedContentColor = scheme.surface,
                        ),
                        // As the settings' rows: Prism's soft corners, the split light round the focused one.
                        shape = ListItemDefaults.shape(TvShapes.card),
                        border = ListItemDefaults.border(focusedBorder = tvFocusBorder(TvShapes.card, TV_RING_THIN)),
                    )
                    Spacer(Modifier.width(8.dp))
                    TvIconButton(
                        if (isMoving) PrismIcons.Check else PrismIcons.SwapVertical,
                        if (isMoving) tr(R.string.done) else tr(R.string.tv_home_move, r.title),
                        { if (isMoving) onPutDown() else onPickUp(r.key) },
                        Modifier.tvItem("$TAG_HOME_EDITOR/move/${r.key}"),
                        on = isMoving,
                    )
                }
            }
        }
    }
}

/** Customize Home's page: the TV's layout ([com.prism.music.data.prefs.TvPrefs.homeLayout]) and the phone's ([TvHomeLayout]'s file). */
@Composable
fun TvHomeEditorPage(nav: TvNav) {
    val c = LocalContainer.current
    val settings by c.settings.flow.collectAsState()
    val account = settings.accountEmail.ifBlank { settings.accountName }.takeIf { settings.isLoggedIn }
    val owner = settings.accountName.takeIf { it.isNotBlank() }
    // YouTube's shelves on Home now (this run's, else the copy kept on the TV): the rows there are to lay out.
    val shelves by produceState(TvHomeShelves.of(account, System.currentTimeMillis()).first, account) {
        if (value.isEmpty()) c.homeCache.read()?.let { value = it }
    }
    val titles = TvHomeTitles(tr(R.string.quick_picks), tr(R.string.section_speed_dial), tr(R.string.recently_played))
    val natural = remember(shelves, owner) { tvHomeNatural(shelves, owner, titles) }
    // The editor's own copies while it's open (the TV's layout: order and the hero; the phone's: which rows show), so
    // quick presses never act on a layout still being written.
    var localTv by remember { mutableStateOf<List<HomeSectionConfig>?>(null) }
    var localPhone by remember { mutableStateOf<List<HomeSectionConfig>?>(null) }
    val tvLayout = localTv ?: settings.tv.homeLayout
    val phoneLayout = localPhone ?: settings.homeLayout
    val layout = TvHomeLayout.editable(tvLayout, natural, phoneLayout)
    var moving by remember { mutableStateOf<String?>(null) }
    fun keepTv(next: List<HomeSectionConfig>) { localTv = TvHomeLayout.saved(next); c.settings.setTvHomeLayout(localTv!!) }
    fun keepPhone(next: List<HomeSectionConfig>) { localPhone = next; c.settings.setHomeLayout(next) }
    BackHandler(enabled = moving != null) { moving = null }
    TvHomeEditorScreen(
        layout.map { TvHomeEditorRow(it.key, it.title, it.visible) },
        moving,
        onToggle = { key ->
            val row = layout.first { it.key == key }
            // The hero is the TV's own; the other rows are shown or hidden in the phone's Home layout too.
            if (key == TvHomeLayout.HERO) keepTv(TvHomeLayout.toggled(layout, key))
            else {
                keepPhone(layoutWithShown(phoneLayout, key, row.title, !row.visible))
                // The order as it is now is the TV's from here on (the phone's layout may name the row in another place).
                keepTv(layout)
            }
        },
        onPickUp = { moving = it },
        onStep = { key, by -> keepTv(TvHomeLayout.moved(layout, key, by)) },
        onPutDown = { moving = null },
        onReset = {
            moving = null
            localTv = emptyList()
            c.settings.setTvHomeLayout(emptyList())
            keepPhone(TvHomeLayout.allShown(phoneLayout))
        },
    )
}

/** Every row Home can have now, in Home's own order, titled: the fixed rows, then [shelves]' YouTube rows. */
internal fun tvHomeNatural(shelves: List<Shelf>, owner: String?, titles: TvHomeTitles): List<HomeSectionConfig> {
    val youtube = tvHomeRows(shelves, owner, emptyList(), emptyList(), titles).filter { it.key.startsWith(HomeSections.YT_PREFIX) }
    return TvHomeLayout.natural(youtube.map { HomeSectionConfig(it.key, it.title) }) { key ->
        when (key) {
            TvHomeLayout.HERO -> tr(R.string.now_playing)
            KEY_PICKS -> titles.quickPicks
            KEY_DIAL -> titles.speedDial
            KEY_RECENT -> titles.recent
            else -> key
        }
    }
}

const val TAG_HOME_EDITOR = "tv_home_editor"

/** How long OK is held to pick a row up (Android's own long press). */
private const val HOLD_MS = 500L
