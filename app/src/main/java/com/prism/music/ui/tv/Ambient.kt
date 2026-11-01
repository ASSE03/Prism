package com.prism.music.ui.tv

import android.os.BatteryManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.prism.music.AppContainer
import com.prism.music.playback.CarLyricsCard
import com.prism.music.ui.player.rememberLyricsState
import com.prism.music.ui.theme.LocalContainer
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.sin

/**
 * When the listener last touched the screen or pressed a key ([touched] from the activities' onUserInteraction), for the
 * ambient screen to know the room has gone quiet.
 */
object Idle {
    @Volatile var lastInput: Long = System.currentTimeMillis()
        private set
    /** The ambient screen is showing: the next key only wakes it (it doesn't also press what's under it). */
    @Volatile var ambient: Boolean = false
        internal set
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun touched() {
        lastInput = System.currentTimeMillis()
        listeners.forEach { it() }
    }

    internal fun listen(l: () -> Unit) = listeners.add(l)
    internal fun stop(l: () -> Unit) = listeners.remove(l)
}

/** How often the ambient screen checks the time since the last input. */
private const val CHECK_MS = 5_000L

/** Whether the ambient screen should show now: music playing, [allowed], and [minutes] since the last input (0: never). */
fun ambientDue(playing: Boolean, allowed: Boolean, minutes: Int, idleMs: Long): Boolean =
    playing && allowed && minutes > 0 && idleMs >= minutes * 60_000L

/** The slow drift that keeps the ambient screen burn-in safe: a few dp, one loop every several minutes. */
fun ambientDrift(nowMs: Long): DpOffset {
    val t = nowMs / 1000.0
    return DpOffset((14 * sin(t / 420.0 * 2 * Math.PI)).dp, (10 * sin(t / 300.0 * 2 * Math.PI)).dp)
}

/**
 * The ambient screen over everything, when it's due: on a TV after [TvPrefs.ambientMinutes] with no remote while music
 * plays; on a phone in desk mode (#265: switched on, charging, playing). While it's allowed and music plays the screen
 * stays on (it's the ambient screen that shows, not the TV's screensaver over the lyrics); paused, the screen may sleep.
 * The first key or touch only brings back what was there.
 */
@Composable
fun AmbientHost(onTv: Boolean) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val view = LocalView.current
    val settings by c.settings.flow.collectAsState()
    val playing by c.player.isPlaying.collectAsState()
    // A phone asks about charging only while desk mode is on (off, this costs nothing).
    val desk = !onTv && settings.tv.deskMode
    val charging by produceState(false, desk) {
        while (desk) { value = isCharging(context); delay(CHECK_MS * 6) }
    }
    val allowed = onTv || (settings.tv.deskMode && charging)
    // The window's own flag (the player sets the view's for videos, #169): neither turns the other off.
    val window = (view.context as? android.app.Activity)?.window
    androidx.compose.runtime.DisposableEffect(allowed, playing, window) {
        val on = allowed && playing
        if (on) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { if (on) window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    var shown by remember { mutableStateOf(false) }
    var input by remember { mutableLongStateOf(Idle.lastInput) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val l: () -> Unit = { input = Idle.lastInput; shown = false }
        Idle.listen(l)
        onDispose { Idle.stop(l) }
    }
    LaunchedEffect(allowed, playing, settings.tv.ambientMinutes, input) {
        if (!allowed || !playing) { shown = false; return@LaunchedEffect }
        while (true) {
            if (ambientDue(true, true, settings.tv.ambientMinutes, System.currentTimeMillis() - Idle.lastInput)) { shown = true; break }
            delay(CHECK_MS)
        }
    }
    androidx.compose.runtime.SideEffect { Idle.ambient = shown }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { Idle.ambient = false } }
    if (shown) {
        val ambient: @Composable () -> Unit = {
            TvTheme(settings) {
                AmbientNow(c, Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { Idle.touched() } })
            }
        }
        // A phone: a window of its own over all of Prism (Back or a tap brings Prism back). A TV: drawn over the
        // screens, so the remote's keys keep going through the activity.
        if (onTv) ambient()
        else androidx.compose.ui.window.Dialog(
            onDismissRequest = { Idle.touched() },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            content = ambient,
        )
    }
}

/** What the ambient screen shows now: the song, the line being sung (when it has lyrics), the time, drifting. */
@Composable
fun AmbientNow(c: AppContainer, modifier: Modifier = Modifier) {
    val now = rememberTvNow(c)
    val song by c.player.currentSong.collectAsState()
    val clock by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(CHECK_MS * 2) } }
    val time = remember(clock / 60_000) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(clock)) }
    val accent = rememberTvAccent(now?.art)
    val line = song?.let { s ->
        val lyrics = rememberLyricsState(s)
        val lines = lyrics.lines
        val at by produceState(-1, lines) { while (true) { value = CarLyricsCard.lineAt(lines, c.player.lyricPosition); delay(500) } }
        lines.getOrNull(at)?.text?.takeIf { it.isNotBlank() }
    }
    val n = now ?: return
    TvAmbientScreen(n, line, time, modifier, drift = ambientDrift(clock), accent = accent)
}

private fun isCharging(context: android.content.Context): Boolean =
    context.getSystemService(BatteryManager::class.java)?.isCharging == true
