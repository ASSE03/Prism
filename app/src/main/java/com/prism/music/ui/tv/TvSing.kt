package com.prism.music.ui.tv

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.tv.material3.MaterialTheme
import com.prism.music.AppContainer
import com.prism.music.R
import com.prism.music.playback.CarLyricsCard
import com.prism.music.tr
import com.prism.music.ui.player.rememberLyricClock
import com.prism.music.ui.player.rememberLyricsState
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.theme.PrismIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Sing on a TV (1.8.0, plan §2): karaoke night, only on TVs fast enough. The speed check separates the playing song's
 * voice for a while and works out whether the whole song would be ready before it's played through; the answer is kept.
 */
object TvSing {
    enum class State { UNCHECKED, CHECKING, READY, SLOW, UNSUPPORTED;
        fun label(): String = when (this) {
            UNCHECKED -> tr(R.string.tv_sing_check)
            CHECKING -> tr(R.string.tv_sing_checking)
            READY -> tr(R.string.tv_sing_ready)
            SLOW -> tr(R.string.tv_sing_too_slow)
            UNSUPPORTED -> tr(R.string.tv_sing_no_cpu)
        }
    }

    private val _state = MutableStateFlow(State.UNCHECKED)
    val state: StateFlow<State> = _state

    /** How long the check watches the separation. */
    private const val CHECK_MS = 40_000L
    /** The separation has to beat the song by this much (it also shares the TV with playback). */
    private const val MARGIN = 0.85

    private fun prefs(context: Context) = context.getSharedPreferences("tv_sing", Context.MODE_PRIVATE)

    /** The kept answer of an earlier check (called as the settings open). */
    fun load(context: Context) {
        if (_state.value != State.UNCHECKED) return
        _state.value = prefs(context).getString("state", null)?.let { s -> State.entries.firstOrNull { it.name == s } }?.takeIf { it != State.CHECKING } ?: State.UNCHECKED
    }

    /** Whether separating at [fraction] done after [elapsedMs] would have the whole song ready before [songMs] plays. */
    fun fastEnough(fraction: Float, elapsedMs: Long, songMs: Long): Boolean =
        fraction > 0f && songMs > 0 && elapsedMs / fraction <= songMs * MARGIN

    /** Checks this TV with the playing song; what to tell the listener. */
    suspend fun check(c: AppContainer): String {
        if (_state.value == State.CHECKING) return State.CHECKING.label()
        if (!c.sing.supported) return keep(c, State.UNSUPPORTED)
        val song = c.player.currentSong.value?.takeIf { c.sing.canSing(it) } ?: return tr(R.string.tv_sing_checking)
        if (!c.sing.engineReady() && !c.sing.downloadEngine()) return tr(R.string.sing_s_engine_isn_t_downloaded)
        val result = checking(c.sing.on.value, c.sing::setOn) {
            c.sing.prepare(listOf(song))
            val start = System.currentTimeMillis()
            var fraction = 0f
            while (System.currentTimeMillis() - start < CHECK_MS) {
                fraction = if (c.sing.store.complete(song.id)) 1f else c.sing.progress.value[song.id] ?: fraction
                if (fraction >= 1f) break
                delay(1_000)
            }
            if (fastEnough(fraction, System.currentTimeMillis() - start, song.durationSec * 1000L)) State.READY else State.SLOW
        }
        return keep(c, result)
    }

    /**
     * The check's [run] with Sing on, then Sing as it was ([wasOn]) and the state as it was if it's cut short (the
     * listener left, the TV closed Prism): never left on, nor stuck at "checking".
     */
    internal suspend fun checking(wasOn: Boolean, setOn: (Boolean) -> Unit, run: suspend () -> State): State {
        val before = _state.value
        _state.value = State.CHECKING
        var done = false
        try {
            setOn(true)
            return run().also { done = true }
        } finally {
            if (!wasOn) setOn(false)
            if (!done) _state.value = before
        }
    }

    private fun keep(c: AppContainer, s: State): String {
        _state.value = s
        prefs(c.app).edit().putString("state", s.name).apply()
        return s.label()
    }
}

/**
 * Sing (TV): the lyrics large with the voice taken out; Left and Right move the key. Leaving the page switches Sing
 * off again (the song plays on as it is).
 */
@Composable
fun TvSingPage(nav: TvNav) {
    val c = LocalContainer.current
    val song by c.player.currentSong.collectAsState()
    val now = rememberTvNow(c)
    val semitones by c.sing.semitones.collectAsState()
    val s = song
    if (s == null || now == null) { TvStarting { nav.back() }; return }
    DisposableEffect(Unit) {
        c.sing.setOn(true)
        c.sing.prepare(c.player.queue.value.map { it.song })
        onDispose { c.sing.setOn(false) }
    }
    val state = rememberLyricsState(s)
    val clock = rememberLyricClock(now.playing)
    val accent = rememberTvAccent(now.art)
    val lines = state.lines
    val current by remember(lines) { derivedStateOf { CarLyricsCard.lineAt(lines, clock.value).coerceAtLeast(0) } }
    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                Key.DirectionLeft -> { c.sing.setSemitones(semitones - 1); true }
                Key.DirectionRight -> { c.sing.setSemitones(semitones + 1); true }
                else -> false
            }
        },
    ) {
        TvLyricsPlace(now, lines, current, clock, state.loading, state.unavailable, state.source?.label, accent, onPlayPause = { c.player.togglePlay() }) { ms -> c.player.seekToLyric(ms) }
        Row(Modifier.align(Alignment.TopEnd).padding(TvSafe), horizontalArrangement = Arrangement.spacedBy(androidx.compose.ui.unit.Dp(10f)), verticalAlignment = Alignment.CenterVertically) {
            val scheme = MaterialTheme.colorScheme
            TvChip(tr(R.string.tv_vocals_out), PrismIcons.Mic, scheme.primary, scheme.onPrimary)
            TvChip(tr(R.string.tv_key_line, if (semitones > 0) "+$semitones" else "$semitones"), null, scheme.surfaceVariant, scheme.onSurface)
        }
    }
}
