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
import com.prism.music.playback.sing.Separation
import com.prism.music.playback.sing.SingPace
import com.prism.music.playback.sing.words
import com.prism.music.tr
import com.prism.music.ui.player.rememberLyricClock
import com.prism.music.ui.player.rememberLyricsState
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.theme.PrismIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Sing on a TV (1.8.0, plan §2): karaoke night. The speed check times Sing's full and light separation on this TV with
 * the playing song (1.8.x); a TV that keeps up with one of them gets it, one too slow, or whose processor Sing's engine
 * doesn't run on, gets the quick way (what's in the middle of the mix taken out) rather than nothing. The answer is kept.
 */
object TvSing {
    /** [SLOW] and [UNSUPPORTED]: Sing the quick way. */
    enum class State { UNCHECKED, CHECKING, READY, SLOW, UNSUPPORTED;
        fun label(): String = when (this) {
            UNCHECKED -> tr(R.string.tv_sing_check)
            CHECKING -> tr(R.string.tv_sing_checking)
            READY -> tr(R.string.tv_sing_ready)
            SLOW -> tr(R.string.tv_sing_too_slow)
            UNSUPPORTED -> tr(R.string.tv_sing_no_cpu)
        }

        /**
         * Sing is in the player's ⋯: always but while the check runs. Unchecked, the first use times this TV as it goes
         * (and the quick way stands in meanwhile); the check only says ahead what it'll do.
         */
        val sings: Boolean get() = this != CHECKING
    }

    private val _state = MutableStateFlow(State.UNCHECKED)
    val state: StateFlow<State> = _state

    private fun prefs(context: Context) = context.getSharedPreferences("tv_sing", Context.MODE_PRIVATE)

    /** The kept answer of an earlier check (called as the settings open); no check needed where only the quick way runs. */
    fun load(context: Context) {
        if (_state.value != State.UNCHECKED) return
        _state.value = prefs(context).getString("state", null)?.let { s -> State.entries.firstOrNull { it.name == s } }?.takeIf { it != State.CHECKING }
            ?: if (com.prism.music.data.ModelStore.Model.singRuntime() == null) State.UNSUPPORTED else State.UNCHECKED
    }

    /** What the check's speeds (seconds of song a second; null: not timed) say this TV can do. */
    fun verdict(full: Double?, light: Double?): State? = when {
        full == null && light == null -> null
        SingPace.choose(true, full, light) == Separation.QUICK -> State.SLOW
        else -> State.READY
    }

    /** Checks this TV with the playing song; what to tell the listener. */
    suspend fun check(c: AppContainer): String {
        if (_state.value == State.CHECKING) return State.CHECKING.label()
        if (!c.sing.fullEngine) return keep(c, State.UNSUPPORTED)
        // Nothing playing to time it with: said so (it used to say "checking" and do nothing).
        val song = c.player.currentSong.value?.takeIf { c.sing.canSing(it) } ?: return tr(R.string.tv_sing_play_first)
        if (!c.sing.engineReady() && !c.sing.downloadEngine()) return tr(R.string.sing_s_engine_isn_t_downloaded)
        var speeds: Pair<Double?, Double?> = null to null
        var problem: String? = null
        val before = _state.value
        val result = checking(c.sing.on.value, c.sing::setOn) {
            runCatching { speeds = c.sing.timeWays(song) }.onFailure { problem = it.message }
            verdict(speeds.first, speeds.second) ?: State.UNCHECKED
        }
        // Nothing timed (the song couldn't be had): the answer stays as it was.
        if (result == State.UNCHECKED) { _state.value = before; return problem ?: tr(R.string.couldn_t_get_this_song_ready) }
        val best = listOfNotNull(speeds.first, speeds.second).maxOrNull()
        return keep(c, result) + (best?.let { "\n" + tr(R.string.tv_sing_measured, "%.1f".format(it)) } ?: "")
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
        // The playing song and the next (the player keeps it one ahead from there, as on the phone).
        val queue = c.player.queue.value.map { it.song }
        val at = queue.indexOfFirst { it.id == s.id }
        c.sing.prepare(listOf(s) + queue.drop(at + 1).take(1), c.player.position)
        // Leaving the page, the song in hand is still finished for a while (Sing's grace), not thrown away.
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
            // What Sing has for the song, honestly: taking the voice out (how far), the quick way standing in while the
            // song's own separation goes (how far), or the voice out. Re-read as the separation moves along.
            c.sing.progress.collectAsState().value
            c.sing.method.collectAsState().value
            TvChip(c.sing.status(s.id).words(), PrismIcons.Mic, scheme.primary, scheme.onPrimary)
            TvChip(tr(R.string.tv_key_line, if (semitones > 0) "+$semitones" else "$semitones"), null, scheme.surfaceVariant, scheme.onSurface)
        }
    }
}
