package com.prism.music.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.prism.music.R
import com.prism.music.data.model.Song
import com.prism.music.data.prefs.CrossfadeCurve
import com.prism.music.playback.MixPreview
import com.prism.music.playback.MusicKey
import com.prism.music.playback.PreviewPlan
import com.prism.music.playback.Tempo
import com.prism.music.playback.Transition
import com.prism.music.playback.TransitionSound
import com.prism.music.playback.TransitionStyle
import com.prism.music.playback.Transitions
import com.prism.music.playback.toMediaItem
import com.prism.music.tr
import com.prism.music.ui.components.PrismButton
import com.prism.music.ui.components.PrismTextButton
import com.prism.music.ui.screens.settings.PickRow
import com.prism.music.ui.screens.settings.PickSheet
import com.prism.music.ui.screens.settings.SliderRow
import com.prism.music.ui.screens.settings.SwitchRow
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.theme.PrismIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * Mix it yourself (1.8.8; plan-mix.md §4.1, §4.3): the listener's own transition between two songs that sit side by
 * side in a playlist. Two waveforms (the first song's last 90 s above, the second's first 90 s below) with a handle each:
 * where the first song leaves and where the second comes in; how long they overlap; how they cross; snap to beat; Listen
 * (its own two small players, [MixPreview]); Reset. Every change is saved at once (it is content, like a playlist's
 * order: no setting). Not offered on the TV.
 */
data class TransitionPair(val playlistId: String, val from: Song, val to: Song)

const val TRANSITION_OUT_TAG = "transitionOut"
const val TRANSITION_IN_TAG = "transitionIn"
const val TRANSITION_LENGTH_TAG = "transitionLength"
const val TRANSITION_LISTEN_TAG = "transitionListen"
const val TRANSITION_RESET_TAG = "transitionReset"
const val TRANSITION_SHORTENED_TAG = "transitionShortened"

/** The editor's rules, apart from the screen (tested on their own). */
object TransitionEditing {
    /** The first song's handle stays in its last 90 s, and a second before its end. */
    fun outRange(fromMs: Long): LongRange = (fromMs - Transitions.WINDOW_MS).coerceAtLeast(0)..(fromMs - 1_000).coerceAtLeast(0)

    /** The second song's handle stays in its first 90 s, and a second before its end. */
    fun inRange(toMs: Long): LongRange = 0L..minOf(Transitions.WINDOW_MS, (toMs - 1_000).coerceAtLeast(0))

    /** [ms] for the first song's handle: inside [outRange], on its nearest beat with [snap] (one inside the range). */
    fun placeOut(ms: Long, fromMs: Long, tempo: Tempo?, snap: Boolean): Long = place(ms, outRange(fromMs), tempo, snap, fromStart = false)

    /** [ms] for the second song's handle, the same way, on its beats counted from its first one. */
    fun placeIn(ms: Long, toMs: Long, tempo: Tempo?, snap: Boolean): Long = place(ms, inRange(toMs), tempo, snap, fromStart = true)

    private fun place(ms: Long, range: LongRange, tempo: Tempo?, snap: Boolean, fromStart: Boolean): Long {
        val c = ms.coerceIn(range)
        if (!snap || tempo == null || !tempo.steady) return c
        val beat = tempo.periodMs
        var b = Transitions.nearestBeat(tempo, c, fromStart).toDouble()
        if (b > range.last) b -= ceil((b - range.last) / beat) * beat
        if (b < range.first) b += ceil((range.first - b) / beat) * beat
        return if (b.toLong() in range) b.toLong() else c
    }

    /** Whether snapping can be on: both songs have a steady beat. */
    fun canSnap(a: Tempo?, b: Tempo?): Boolean = a?.steady == true && b?.steady == true

    /**
     * [points] bars of a song [songMs] long between [fromMs] and [toMs]: from its shape (a value a second, finer) when it
     * has one, else from its outline ([com.prism.music.data.SongWaves.POINTS] over the song); null when it has neither yet.
     */
    fun wave(energy: FloatArray?, outline: FloatArray?, songMs: Long, fromMs: Long, toMs: Long, points: Int): FloatArray? {
        if (songMs <= 0 || toMs <= fromMs || points <= 0) return null
        val src = energy?.takeIf { it.isNotEmpty() } ?: outline?.takeIf { it.isNotEmpty() } ?: return null
        val msPer = if (src === energy) 1_000.0 else songMs.toDouble() / src.size
        val out = FloatArray(points) { i ->
            val at = fromMs + (toMs - fromMs) * (i + 0.5) / points
            src[(at / msPer).toInt().coerceIn(0, src.size - 1)]
        }
        // The shape's energy is a power: its square root reads like the outline.
        if (src === energy) for (i in out.indices) out[i] = kotlin.math.sqrt(out[i].coerceAtLeast(0f))
        val top = out.maxOrNull()?.takeIf { it > 0f } ?: return out
        for (i in out.indices) out[i] /= top
        return out
    }

    /** "12.5 s" (one decimal, none when whole). */
    fun seconds(ms: Long): String {
        val tenths = (ms / 100.0).roundToLong()
        return if (tenths % 10 == 0L) "${tenths / 10} s" else "${tenths / 10}.${kotlin.math.abs(tenths % 10)} s"
    }
}

/** What the editor shows of one song: its scan and its waveform window. */
data class TransitionSide(val song: Song, val durationMs: Long, val tempo: Tempo?, val key: MusicKey?, val wave: FloatArray?)

/** The editor as a full-screen sheet over whatever opened it (a playlist, or the player). */
@Composable
fun TransitionEditor(pair: TransitionPair, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { TransitionEditorScreen(pair, onClose) }
    }
}

/** The editor wired to Prism: the store, the scans, the preview and the queue's own song. */
// Listen builds its own player (previewPlayer), part of Media3's unstable API.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun TransitionEditorScreen(pair: TransitionPair, onClose: () -> Unit) {
    val c = LocalContainer.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val fromMs = pair.from.durationSec * 1000L
    val toMs = pair.to.durationSec * 1000L
    var a by remember { mutableStateOf<TransitionSide?>(null) }
    var b by remember { mutableStateOf<TransitionSide?>(null) }
    var t by remember { mutableStateOf<Transition?>(null) }
    var savedRow by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(false) }
    var measuring by remember { mutableStateOf(false) }
    var needsData by remember { mutableStateOf(false) }
    var measureAsk by remember { mutableStateOf(0) }

    suspend fun side(s: Song, ms: Long, windowFrom: Long, windowTo: Long): TransitionSide = withContext(Dispatchers.IO) {
        val shape = c.waves.shape(s.id)
        TransitionSide(s, ms, c.mixTempo.cached(s.id), c.mixTempo.key(s.id),
            TransitionEditing.wave(shape?.energy, c.waves.get(s.id), ms, windowFrom, windowTo, WAVE_POINTS))
    }
    suspend fun readSides() {
        a = side(pair.from, fromMs, TransitionEditing.outRange(fromMs).first, fromMs)
        b = side(pair.to, toMs, 0, minOf(Transitions.WINDOW_MS, toMs))
    }
    LaunchedEffect(pair, measureAsk) {
        if (measureAsk == 0) {
            readSides()
            val row = c.transitions.get(pair.playlistId, pair.from.id, pair.to.id)
            savedRow = row != null
            t = row ?: Transitions.automatic(a?.tempo, b?.tempo, fromMs)
        }
        // Songs not measured yet are measured now: from the phone when their audio is here, else downloaded on Wi-Fi (or
        // on mobile data when the listener allows it, the same rule as every scan); otherwise asked first.
        val unmeasured = listOf(pair.from, pair.to).filter { !c.waves.has(it.id) || c.mixTempo.cached(it.id) == null }
        if (unmeasured.isEmpty()) return@LaunchedEffect
        val metered = context.getSystemService(android.net.ConnectivityManager::class.java)?.isActiveNetworkMetered != false
        val network = measureAsk > 0 || c.settings.current.mix.tempoOnCellular || !metered
        measuring = true
        for (s in unmeasured) runCatching { c.mixTempo.measure(s.id, network = network, edges = true, key = true, wave = true) }
        measuring = false
        readSides()
        needsData = !network && listOf(pair.from, pair.to).any { !c.waves.has(it.id) }
        if (!savedRow && !touched) t = Transitions.automatic(a?.tempo, b?.tempo, fromMs)
    }

    // Listen: the queue's song pauses while the editor plays, and comes back at its place when the editor closes.
    val preview = remember { MixPreview(c.scope) { item, fx -> c.player.previewPlayer(item, fx) } }
    var pausedQueue by remember { mutableStateOf(false) }
    val listening by preview.phase.collectAsState()
    DisposableEffect(Unit) {
        onDispose {
            preview.stop()
            if (pausedQueue) c.player.setPlaying(true)
        }
    }
    val listener = com.prism.music.ui.theme.LocalAppSettings.current.sound.crossfadeCurve

    fun save(next: Transition) {
        t = next
        touched = true
        savedRow = true
        c.scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { runCatching { c.transitions.save(pair.playlistId, pair.from.id, pair.to.id, next) } }
    }

    TransitionEditorBody(
        pair = pair, a = a, b = b, t = t, savedRow = savedRow, measuring = measuring, needsData = needsData,
        listening = listening != MixPreview.Phase.IDLE, listener = listener,
        onChange = { t = it; touched = true }, onSave = ::save,
        onReset = {
            preview.stop()
            c.scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { runCatching { c.transitions.reset(pair.playlistId, pair.from.id, pair.to.id) } }
            savedRow = false
            touched = false
            t = Transitions.automatic(a?.tempo, b?.tempo, fromMs)
        },
        onListen = {
            val now = t
            if (listening != MixPreview.Phase.IDLE || now == null) preview.stop()
            else {
                if (c.player.isPlaying.value) { c.player.setPlaying(false); pausedQueue = true }
                val plan = PreviewPlan.of(now, fromMs, toMs.takeIf { it > 0 }, a?.tempo?.takeIf { it.steady }?.periodMs ?: 0.0, listener)
                if (!preview.play(pair.from.toMediaItem(), pair.to.toMediaItem(), plan))
                    android.widget.Toast.makeText(context, tr(R.string.transition_listen_failed), android.widget.Toast.LENGTH_SHORT).show()
            }
        },
        onMeasure = { measureAsk++ },
        onClose = onClose,
    )
}

/** The editor's screen itself, from plain values (the render and Compose tests draw it without Prism around it). */
@Composable
fun TransitionEditorBody(
    pair: TransitionPair,
    a: TransitionSide?,
    b: TransitionSide?,
    t: Transition?,
    savedRow: Boolean,
    measuring: Boolean,
    needsData: Boolean,
    listening: Boolean,
    listener: CrossfadeCurve,
    onChange: (Transition) -> Unit,
    onSave: (Transition) -> Unit,
    onReset: () -> Unit,
    onListen: () -> Unit,
    onMeasure: () -> Unit,
    onClose: () -> Unit,
) {
    val fromMs = pair.from.durationSec * 1000L
    val toMs = pair.to.durationSec * 1000L
    var styles by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(PrismIcons.ArrowBack, tr(R.string.back)) }
            Column(Modifier.weight(1f)) {
                Text(tr(R.string.transition_title), style = MaterialTheme.typography.titleLarge)
                Text(tr(R.string.transition_from_to, pair.from.title, pair.to.title), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val tr0 = t ?: return@Column
        val fit = Transitions.fit(tr0, fromMs, toMs.takeIf { it > 0 })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            if (measuring) Note(tr(R.string.transition_measuring))
            if (needsData) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr(R.string.transition_not_measured), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                PrismTextButton(onMeasure) { Text(tr(R.string.transition_measure_on_data)) }
            }
            // The first song: where it leaves.
            val outWindow = TransitionEditing.outRange(fromMs).first..fromMs
            SongHeader(pair.from, a)
            WaveHandle(
                wave = a?.wave, window = outWindow, handleMs = fit.startMs, shadeFrom = fit.startMs, shadeTo = fit.startMs + fit.lengthMs,
                beats = Transitions.beats(a?.tempo, outWindow.first, outWindow.last),
                label = tr(R.string.transition_where_leaves, pair.from.title),
                valueText = tr(R.string.transition_before_end, TransitionEditing.seconds(fromMs - fit.startMs)),
                tag = TRANSITION_OUT_TAG,
                onMove = { ms -> onChange(tr0.copy(outMs = TransitionEditing.placeOut(ms, fromMs, a?.tempo, tr0.snap))) },
                onSet = { ms -> onSave(tr0.copy(outMs = TransitionEditing.placeOut(ms, fromMs, a?.tempo, tr0.snap))) },
            )
            Spacer(Modifier.height(12.dp))
            // The second song: where it comes in.
            val inWindow = 0L..minOf(Transitions.WINDOW_MS, toMs.coerceAtLeast(1))
            SongHeader(pair.to, b)
            WaveHandle(
                wave = b?.wave, window = inWindow, handleMs = fit.inMs, shadeFrom = fit.inMs, shadeTo = fit.inMs + fit.lengthMs,
                beats = Transitions.beats(b?.tempo, inWindow.first, inWindow.last, fromStart = true),
                label = tr(R.string.transition_where_comes_in, pair.to.title),
                valueText = tr(R.string.transition_from_start, TransitionEditing.seconds(fit.inMs)),
                tag = TRANSITION_IN_TAG,
                onMove = { ms -> onChange(tr0.copy(inMs = TransitionEditing.placeIn(ms, toMs, b?.tempo, tr0.snap))) },
                onSet = { ms -> onSave(tr0.copy(inMs = TransitionEditing.placeIn(ms, toMs, b?.tempo, tr0.snap))) },
            )
            Spacer(Modifier.height(8.dp))
            SliderRow(
                tr(R.string.transition_overlap), (tr0.lengthMs / 1000f).coerceIn(0f, 30f), 0f..30f,
                { s -> if (s < 0.5f) tr(R.string.transition_cut) else tr(R.string.transition_seconds, s.toInt()) },
                Modifier.testTag(TRANSITION_LENGTH_TAG), steps = 29,
            ) { s -> onSave(tr0.copy(lengthMs = s.toLong() * 1000L)) }
            if (fit.shortened) Note(tr(R.string.transition_shortened, TransitionEditing.seconds(fit.lengthMs)), Modifier.testTag(TRANSITION_SHORTENED_TAG))
            PickRow(tr(R.string.how_they_cross), styleLabel(tr0.style)) { styles = true }
            val canSnap = TransitionEditing.canSnap(a?.tempo, b?.tempo)
            SwitchRow(tr(R.string.transition_snap), tr0.snap && canSnap, info = tr(R.string.transition_snap_info), enabled = canSnap) { on ->
                onSave(if (!on) tr0.copy(snap = false) else tr0.copy(
                    snap = true,
                    outMs = TransitionEditing.placeOut(tr0.outMs, fromMs, a?.tempo, true),
                    inMs = TransitionEditing.placeIn(tr0.inMs, toMs, b?.tempo, true),
                ))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PrismButton(onListen, Modifier.weight(1f).testTag(TRANSITION_LISTEN_TAG)) {
                Icon(if (listening) PrismIcons.Pause else PrismIcons.Play, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (listening) tr(R.string.stop) else tr(R.string.listen))
            }
            PrismTextButton(onReset, Modifier.testTag(TRANSITION_RESET_TAG), enabled = savedRow) { Text(tr(R.string.reset)) }
        }
    }
    if (styles) t?.let { cur ->
        PickSheet(
            tr(R.string.how_they_cross), TransitionStyle.offered, cur.style, ::styleLabel,
            onPick = { onSave(cur.copy(style = it)) }, onDismiss = { styles = false }, exampleScale = 1f,
        ) { s ->
            Column {
                StyleSample(s, listener)
                Spacer(Modifier.height(4.dp))
                Text(styleDetail(s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Note(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A song's name with its tempo and key, as Spotify's Mix shows them ("Not measured yet" until the scan has them). */
@Composable
private fun SongHeader(song: Song, side: TransitionSide?) {
    val tempo = side?.tempo?.takeIf { it.steady }
    val key = side?.key?.takeIf { it.sure }
    val facts = when {
        tempo != null && key != null -> tr(R.string.transition_bpm_key, tempo.bpm.toInt().toString(), "${key.name} (${key.camelot})")
        tempo != null -> tr(R.string.transition_bpm, tempo.bpm.toInt().toString())
        key != null -> "${key.name} (${key.camelot})"
        else -> tr(R.string.transition_not_measured)
    }
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp)) {
        Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(facts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A song's waveform over [window] with a handle at [handleMs], the overlap shaded and the beats ticked. Dragged along,
 * the handle follows ([onMove]) and is set where it's let go ([onSet]); a tap sets it there. For TalkBack it is a
 * slider ([label], [valueText]) whose value can be set straight away.
 */
@Composable
private fun WaveHandle(
    wave: FloatArray?, window: LongRange, handleMs: Long, shadeFrom: Long, shadeTo: Long, beats: List<Long>,
    label: String, valueText: String, tag: String, onMove: (Long) -> Unit, onSet: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val move by rememberUpdatedState(onMove)
    val set by rememberUpdatedState(onSet)
    var dragged by remember { mutableStateOf<Long?>(null) }
    val span = (window.last - window.first).coerceAtLeast(1)
    fun msAt(x: Float, width: Int): Long = window.first + (x / width.coerceAtLeast(1) * span).toLong()
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(72.dp).clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.45f))
            .testTag(tag)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = valueText
                progressBarRangeInfo = ProgressBarRangeInfo(handleMs.toFloat(), window.first.toFloat()..window.last.toFloat())
                setProgress { v -> set(v.toLong()); true }
            }
            .focusable()
            .pointerInput(window) { detectTapGestures { o -> set(msAt(o.x, size.width)) } }
            .pointerInput(window) {
                detectHorizontalDragGestures(
                    onDragEnd = { dragged?.let(set); dragged = null },
                    onDragCancel = { dragged?.let(set); dragged = null },
                ) { change, _ -> msAt(change.position.x, size.width).let { dragged = it; move(it) } }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            fun x(ms: Long) = ((ms - window.first).toFloat() / span * size.width).coerceIn(0f, size.width)
            // The overlap, shaded.
            if (shadeTo > shadeFrom) drawRect(scheme.primary.copy(alpha = 0.18f), Offset(x(shadeFrom), 0f), Size((x(shadeTo) - x(shadeFrom)).coerceAtLeast(2f), size.height))
            // The beats, ticked along the bottom.
            for (beat in beats) drawLine(scheme.onSurfaceVariant.copy(alpha = 0.35f), Offset(x(beat), size.height - 6.dp.toPx()), Offset(x(beat), size.height), 1f)
            // The waveform.
            val w = wave
            if (w != null && w.isNotEmpty()) {
                val bar = size.width / w.size
                for (i in w.indices) {
                    val h = (w[i] * (size.height - 16.dp.toPx())).coerceAtLeast(2f)
                    val cx = i * bar + bar / 2
                    val inShade = cx >= x(shadeFrom) && cx <= x(shadeTo)
                    drawRoundRect(
                        if (inShade) scheme.primary else scheme.onSurface.copy(alpha = 0.55f),
                        Offset(cx - bar * 0.35f, (size.height - h) / 2), Size(bar * 0.7f, h), CornerRadius(bar * 0.35f),
                    )
                }
            } else drawLine(scheme.onSurface.copy(alpha = 0.3f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx())
            // The handle.
            val hx = x(handleMs)
            drawLine(scheme.primary, Offset(hx, 0f), Offset(hx, size.height), 3.dp.toPx())
            drawCircle(scheme.primary, 7.dp.toPx(), Offset(hx, 9.dp.toPx()))
        }
    }
    Text(valueText, Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp), style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A style's two volumes over the transition, drawn from the same law that plays ([TransitionSound.gains]). */
@Composable
private fun StyleSample(style: TransitionStyle, listener: CrossfadeCurve) {
    val incoming = MaterialTheme.colorScheme.primary
    val outgoing = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    Canvas(Modifier.fillMaxWidth().height(44.dp)) {
        val steps = 40
        fun path(pick: (Pair<Float, Float>) -> Float) = androidx.compose.ui.graphics.Path().apply {
            for (i in 0..steps) {
                val p = i.toFloat() / steps
                val y = size.height - pick(TransitionSound.gains(style, p, 0.5f, listener)) * (size.height - 4f) - 2f
                if (i == 0) moveTo(p * size.width, y) else lineTo(p * size.width, y)
            }
        }
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawPath(path { it.second }, outgoing, style = stroke)
        drawPath(path { it.first }, incoming, style = stroke)
    }
}

/** A style's name: the curves' own names, Automix's moves by theirs. */
fun styleLabel(s: TransitionStyle): String = s.curve?.label ?: when (s) {
    TransitionStyle.BASS_SWAP -> tr(R.string.transition_bass_swap)
    TransitionStyle.FILTER_OUT -> tr(R.string.transition_filter_out)
    TransitionStyle.ECHO_OUT -> tr(R.string.transition_echo_out)
    else -> tr(R.string.automatic_2)
}

private fun styleDetail(s: TransitionStyle): String = s.curve?.detail ?: when (s) {
    TransitionStyle.BASS_SWAP -> tr(R.string.transition_bass_swap_detail)
    TransitionStyle.FILTER_OUT -> tr(R.string.transition_filter_out_detail)
    TransitionStyle.ECHO_OUT -> tr(R.string.transition_echo_out_detail)
    else -> tr(R.string.transition_auto_detail)
}

const val TRANSITION_ROW_MARK_TAG = "transitionRowMark"
const val TRANSITION_PLAYER_MARK_TAG = "transitionPlayerMark"

/** The small mark between two rows of a playlist that have their own transition; a tap opens it. */
@Composable
fun TransitionRowMark(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(20.dp).testTag(TRANSITION_ROW_MARK_TAG)
            .clickable(onClickLabel = tr(R.string.transition_edit)) { onClick() }
            .semantics { contentDescription = tr(R.string.transition_mark) }
            .padding(start = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PrismIcons.Blend, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(36.dp).height(1.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
    }
}

/** Bars across each waveform. */
private const val WAVE_POINTS = 90

