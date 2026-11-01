package com.prism.music.ui.tv

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.prism.music.R
import com.prism.music.data.lyrics.LyricLine
import com.prism.music.data.prefs.AppSettings
import com.prism.music.data.prefs.FontChoice
import com.prism.music.data.prefs.LyricsAlign
import com.prism.music.data.prefs.LyricsGapMark
import com.prism.music.tr
import com.prism.music.ui.components.Artwork
import com.prism.music.ui.player.RestingLine
import com.prism.music.ui.player.SungLine
import com.prism.music.ui.player.SweptLine
import com.prism.music.ui.player.drawNote
import com.prism.music.ui.player.extraStyle
import com.prism.music.ui.player.rememberLyricsLook
import com.prism.music.ui.theme.AppFonts
import com.prism.music.ui.theme.LocalAppSettings
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.theme.LocalLyricsFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.abs

/*
 * Lyrics on the TV (1.8.0, after the owner asked for karaoke-style lyrics): the phone's lyrics page on a big screen. The
 * lines scroll as one list, the sung line gliding into place (only the list's offset moves); the sung line is drawn by
 * the phone's own SungLine with the lyrics' weight-axis typeface, so "Grows bolder" swells word by word; background
 * vocals, the romanized line and the translation sit under each line as the listener has them; instrumental breaks
 * have their mark; the Lyrics look's Glow, Shadow and Bold card come along (Glow's blur only on Android 12 and later,
 * as the phone).
 */

/** The lyrics' typeface for the weight axis (Settings' lyrics font, else the app's), or null for one without it. */
fun tvLyricsAxisChoice(s: AppSettings): FontChoice? =
    if (s.lyricsFont.isEmpty()) s.fontChoice else FontChoice.entries.firstOrNull { it.name == s.lyricsFont }

@Composable
private fun rememberTvLyricsAxis(s: AppSettings): FontFamily? {
    val choice = tvLyricsAxisChoice(s)
    return remember(choice) { choice?.let(AppFonts::axis) }
}

/** The lines' brightness by how far they are from the sung one (then the look's own brightness for others). */
private val FALLOFF = floatArrayOf(1f, 0.85f, 0.65f, 0.5f, 0.4f)

/** How long the sung line takes to glide into place. */
private const val GLIDE_MS = 450

/** Where the sung line settles: this share of the screen down. */
private const val SETTLE_AT = 0.3f

/**
 * Lyrics (TV): the song's lyrics large across the screen, the cover and title small in the corner, the progress at the
 * foot. [lyrics]' sung line ([TvLyrics.current]) settles a third of the way down; a line [picked] with the remote is
 * lit and comes there instead. [roman] (line for line, null where none) and [translations] go under each line;
 * [romanInstead]: the romanized line is sung in the line's place.
 */
@Composable
fun TvLyricsScreen(
    now: TvNow,
    lyrics: TvLyrics,
    clock: State<Long>,
    modifier: Modifier = Modifier,
    picked: Int? = null,
    accent: Color? = null,
    roman: List<LyricLine?>? = null,
    romanInstead: Boolean = false,
    translations: List<String?>? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val settings = LocalAppSettings.current
    val look = rememberLyricsLook(settings, now.art)
    val axis = rememberTvLyricsAxis(settings)
    val scale = settings.tv.lyricsScale * look.sizeScale
    val centred = settings.lyricsAlign == LyricsAlign.CENTER
    val onCard = look.card != null
    val style = TextStyle(
        fontSize = 40.sp * scale, lineHeight = 52.sp * scale, fontWeight = FontWeight(look.weight),
        fontFamily = LocalLyricsFont.current ?: MaterialTheme.typography.displaySmall.fontFamily,
        textAlign = if (centred) TextAlign.Center else TextAlign.Start,
        textDirection = androidx.compose.ui.text.style.TextDirection.Content,
    )
    val lines = lyrics.lines
    val target = picked ?: lyrics.current
    val list = rememberLazyListState(initialFirstVisibleItemIndex = target.coerceIn(0, (lines.size - 1).coerceAtLeast(0)))
    // The line comes to the settle point: gliding when it's on screen (the next line, mostly), at once from further.
    LaunchedEffect(target, lines) {
        if (target !in lines.indices) return@LaunchedEffect
        snapshotFlow { list.layoutInfo.viewportSize.height }.first { it > 0 }
        val shown = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
        if (shown != null) list.animateScrollBy(shown.offset.toFloat(), tween(GLIDE_MS, easing = FastOutSlowInEasing))
        else list.scrollToItem(target)
    }
    Box(modifier.fillMaxSize().testTag(TAG_LYRICS)) {
        TvMeshBackdrop(now.art)
        // Bold card: the lines on the cover's own color, as the phone's lyrics page.
        look.card?.let { card -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(card, lerp(card, Color.Black, 0.35f))))) }
        Row(Modifier.padding(TvSafe), verticalAlignment = Alignment.CenterVertically) {
            Artwork(now.art, Modifier.size(56.dp), shape = RoundedCornerShape(8.dp), size = 160)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(now.title, style = MaterialTheme.typography.titleMedium, color = scheme.onBackground, maxLines = 1)
                Text(now.artist, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1)
            }
        }
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(start = 96.dp, end = 160.dp, top = 96.dp, bottom = 88.dp)
                .tvItem("$TAG_LYRICS/lines", entry = true).focusable(),
        ) {
            val settle = maxHeight * SETTLE_AT
            LazyColumn(
                state = list,
                userScrollEnabled = false,
                // Lines fade out at the top and bottom edges (as on the phone), never cut across.
                modifier = Modifier.fillMaxSize().testTag("$TAG_LYRICS/list")
                    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.12f to Color.Black, 0.86f to Color.Black, 1f to Color.Transparent), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                    },
                contentPadding = PaddingValues(top = settle, bottom = maxHeight),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(lines, key = { i, _ -> i }) { i, line ->
                    val active = i == lyrics.current
                    val distance = if (lyrics.current < 0) 3 else abs(i - lyrics.current)
                    val alpha = when {
                        active -> 1f
                        i == picked -> 0.95f
                        else -> FALLOFF[distance.coerceAtMost(FALLOFF.lastIndex)] * look.othersAlpha / 0.55f
                    }.coerceIn(0f, 1f)
                    if (line.isGap) {
                        TvGapMark(
                            line, lines.getOrNull(i + 1)?.timeMs ?: line.endMs, clock, active, centred,
                            note = settings.lyricsGapMark == LyricsGapMark.NOTE, modifier = Modifier.testTag("$TAG_LYRICS/gap/$i"),
                        )
                        return@itemsIndexed
                    }
                    TvLyricLine(
                        line, i, active, i == picked, alpha, clock, style, look, axis, onCard,
                        roman?.getOrNull(i), romanInstead, translations?.getOrNull(i),
                    )
                }
            }
        }
        TvProgress(now.fraction, now.played, now.total, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(TvSafe))
    }
}

/** One line of the lyrics: sung or resting, with its background vocals, romanized line and translation under it. */
@Composable
private fun TvLyricLine(
    line: LyricLine,
    index: Int,
    active: Boolean,
    picked: Boolean,
    alpha: Float,
    clock: State<Long>,
    style: TextStyle,
    look: com.prism.music.ui.player.LyricsLook,
    axis: FontFamily?,
    onCard: Boolean,
    romanLine: LyricLine?,
    romanInstead: Boolean,
    translated: String?,
) {
    val shown = remember(line, romanLine, romanInstead, look.uppercase) { look.shown(if (romanInstead && romanLine != null) romanLine else line) }
    val extraInk = if (onCard) Color.Black else look.extraColor
    Column(
        Modifier.fillMaxWidth().testTag("$TAG_LYRICS/line/$index")
            .then(if (picked) Modifier.background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp * look.spacing),
    ) {
        val translation = @Composable {
            translated?.takeUnless { it.equals(line.text.trim(), ignoreCase = true) }?.let { t ->
                Text(t, style = look.extraStyle(style, 0.52f, 0.56f), color = extraInk.copy(alpha = if (active) 0.8f else alpha * 0.75f), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            }
        }
        val romanised = @Composable {
            if (!romanInstead && romanLine != null) {
                val r = look.shown(romanLine)
                val rs = look.extraStyle(style, 0.56f, 0.62f)
                if (active && r.isWordSynced) SweptLine(r, clock, rs, Modifier.fillMaxWidth().padding(vertical = 4.dp), litBrush = look.sung)
                else Text(r.text, style = rs, color = extraInk.copy(alpha = if (active) 0.85f else alpha * 0.8f), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            }
        }
        if (look.extraAbove) { romanised(); translation() }
        if (active) SungLine(shown, clock, style, look, axis, Modifier.fillMaxWidth(), onCard = onCard)
        else RestingLine(shown, style, look, alpha, Modifier.fillMaxWidth())
        if (!look.extraAbove) romanised()
        // Background vocals: smaller under the line, lit word by word in their own time (as on the phone).
        shown.backgroundLine?.let { bg ->
            val bs = style.copy(fontSize = style.fontSize * look.backingSize, lineHeight = style.lineHeight * look.backingSize * 1.07f, fontWeight = FontWeight.Normal, shadow = look.shadow)
            val m = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("$TAG_LYRICS/backing/$index")
            if (active) SweptLine(bg, clock, bs, m, litBrush = look.sung)
            else Text(bg.text, style = bs, color = look.others.copy(alpha = alpha * 0.75f), modifier = m)
        }
        if (!look.extraAbove) translation()
    }
}

/**
 * An instrumental break (TV): three dots (or a note, as Settings → Lyrics has it) lighting up as the break goes by,
 * larger while it's the one playing.
 */
@Composable
private fun TvGapMark(line: LyricLine, until: Long, clock: State<Long>, active: Boolean, centred: Boolean, note: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(if (active) 72.dp else 36.dp).padding(horizontal = 10.dp), contentAlignment = if (centred) Alignment.Center else Alignment.CenterStart) {
        Box(
            Modifier.size(width = if (note) 32.dp else 72.dp, height = if (note) 32.dp else 16.dp)
                .graphicsLayer { alpha = if (active) 1f else 0.45f }
                .drawBehind {
                    val span = (until - line.timeMs).coerceAtLeast(1)
                    val through = if (!active) 0f else ((clock.value - line.timeMs).toFloat() / span).coerceIn(0f, 1f)
                    if (note) {
                        drawNote(Color.White.copy(alpha = 0.35f))
                        clipRect(top = size.height * (1f - through)) { drawNote(Color.White) }
                        return@drawBehind
                    }
                    val r = size.height / 2
                    val stride = (size.width - 2 * r) / 2
                    repeat(3) { d ->
                        val lit = (through * 3 - d).coerceIn(0f, 1f)
                        drawCircle(Color.White.copy(alpha = 0.35f + 0.65f * lit), r, Offset(r + d * stride, size.height / 2))
                    }
                },
        )
    }
}

/**
 * The lyrics page's states around [TvLyricsScreen]: looking for lyrics, none found, or the lines. The lines take the
 * remote: ↑ ↓ pick a line (it lights up and comes into place), OK plays from the picked line (or pauses and plays with
 * none picked).
 */
@Composable
fun TvLyricsPlace(
    now: TvNow,
    lines: List<LyricLine>,
    current: Int,
    clock: State<Long>,
    loading: Boolean,
    unavailable: Boolean,
    source: String?,
    accent: Color?,
    onPlayPause: () -> Unit = {},
    roman: List<LyricLine?>? = null,
    romanInstead: Boolean = false,
    translations: List<String?>? = null,
    onSeek: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var picked by remember(lines) { mutableStateOf<Int?>(null) }
    if (lines.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TvMeshBackdrop(now.art)
            Text(
                tr(if (loading || !unavailable) R.string.looking_for_lyrics else R.string.no_lyrics_found_for_this_song),
                // The lines' own tag: when they come (or go, as the song changes), focus stays on the lyrics.
                Modifier.tvItem("$TAG_LYRICS/lines", entry = true).focusable(),
                style = MaterialTheme.typography.headlineMedium, color = scheme.onSurfaceVariant,
            )
        }
        return
    }
    // The clock is read only where words are drawn (not here, or the whole page would be redrawn every frame).
    TvLyricsScreen(
        now, TvLyrics(lines, current, 0L, source), clock, picked = picked, accent = accent,
        roman = roman, romanInstead = romanInstead, translations = translations,
        modifier = Modifier.onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                Key.DirectionUp -> { picked = ((picked ?: current) - 1).coerceAtLeast(0); true }
                Key.DirectionDown -> { picked = ((picked ?: current) + 1).coerceAtMost(lines.lastIndex); true }
                Key.DirectionCenter, Key.Enter -> {
                    val p = picked
                    if (p != null) { onSeek(lines[p].timeMs); picked = null } else onPlayPause()
                    true
                }
                else -> false
            }
        },
    )
}

/** How many times a song's translation is asked for while its lyrics stay open (Google can be busy). */
private const val TRANSLATE_TRIES = 3

/**
 * The lyrics' translation into the listener's language, line for line, when Settings → Lyrics has translations on and the
 * song isn't in that language already; null otherwise (or until it comes).
 */
@Composable
fun rememberTvTranslations(lines: List<LyricLine>): List<String?>? {
    val c = LocalContainer.current
    val s = LocalAppSettings.current
    val to = s.lyricsTranslateTo.ifBlank { com.prism.music.ui.player.defaultTranslateTarget() }
    val translation by produceState<com.prism.music.data.lyrics.LyricsTranslator.Translation?>(null, lines, s.lyricsTranslate, to) {
        value = null
        if (!s.lyricsTranslate || lines.isEmpty()) return@produceState
        val texts = lines.map { l -> if (l.isGap || (l.text.startsWith("[") && l.text.endsWith("]"))) "" else l.text }
        for (attempt in 0 until TRANSLATE_TRIES) {
            val got = runCatching { c.translator.translate(texts, to) }
            if (got.isSuccess) { value = got.getOrNull(); return@produceState }
            if (got.exceptionOrNull() is kotlinx.coroutines.CancellationException) return@produceState
            delay(20_000L shl attempt)
        }
    }
    return translation?.takeUnless { it.sameLanguage }?.translations
}
