package com.prism.music.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.prism.music.data.model.Song
import com.prism.music.playback.BlendNow
import com.prism.music.ui.theme.LocalAppSettings
import com.prism.music.ui.theme.LocalContainer

/*
 * 1.8.8 (plan-mix §2.4): what the player shows while songs blend, an end-of-song crossfade or a skip-blend ("Blend when I
 * skip"). During a skip-blend the main player is still on the song heard, so the player shows the chosen one at once
 * ([skipShownId]); with "Cover and title blend too" the cover and the title of the song before lie over the new ones and
 * fade out with the sound ([blendingFrom]). The Canvas loop comes and goes with it too ([canvasBlendAlpha]).
 */

/**
 * The song the player shows in place of [playingId] while a skip-blend fades into it: the blend's song, when it blends out
 * of the one the main player is still on (an end-of-song blend has already moved the main player: nothing to show then).
 */
fun skipShownId(playingId: String, blend: BlendNow?): String? =
    blend?.takeIf { it.fromId == playingId && it.toId != playingId }?.toId

/** How much of the song before still shows over the new one at [progress] (0 … 1): fading over the blend, or with Reduce motion, all of it until halfway. */
fun blendOverAlpha(progress: Float, reduceMotion: Boolean): Float =
    if (reduceMotion) (if (progress < 0.5f) 1f else 0f) else (1f - progress).coerceIn(0f, 1f)

/** A Canvas loop's share of its alpha for [songId] while a blend runs: coming up with the song blended into, going with the one blended out of. */
fun canvasBlendAlpha(blend: BlendNow?, songId: String): Float = when {
    blend == null -> 1f
    blend.toId == songId -> blend.progress.coerceIn(0f, 1f)
    blend.fromId == songId -> (1f - blend.progress).coerceIn(0f, 1f)
    else -> 1f
}

/**
 * "Cover and title blend too": the song a blend into [songId] comes from, with how much of it shows now (to be read in a
 * layer, so the player isn't recomposed as the blend moves). Null with the switch off or no blend into [songId].
 */
@Composable
fun blendingFrom(songId: String): Pair<Song, () -> Float>? {
    val c = LocalContainer.current
    val settings = LocalAppSettings.current
    if (!settings.view.blendCovers) return null
    val blend = c.mixState.blend.collectAsState()
    val fromId by remember(songId) { derivedStateOf { blend.value?.takeIf { it.toId == songId }?.fromId } }
    val id = fromId ?: return null
    val from = remember(id) { c.player.queue.value.firstOrNull { it.song.id == id }?.song } ?: return null
    val reduce = settings.reduceMotion
    return from to { blendOverAlpha(blend.value?.takeIf { it.toId == songId }?.progress ?: 1f, reduce) }
}

/** [content] (the song before's cover) laid over what's under it at [over]'s alpha. */
@Composable
fun BlendOver(over: () -> Float, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.graphicsLayer { alpha = over() }, content = content)
}

/**
 * The title and artist: with a blend under way ([over] given), [old] (the song before's, untouchable) over [now], the one
 * fading out as the other comes in. Without one, [now] alone, drawn as it always was.
 */
@Composable
fun TitleBlend(
    over: (() -> Float)?,
    centred: Boolean,
    modifier: Modifier = Modifier,
    old: @Composable ColumnScope.() -> Unit,
    now: @Composable ColumnScope.() -> Unit,
) {
    val align = if (centred) Alignment.CenterHorizontally else Alignment.Start
    if (over == null) {
        Column(modifier, horizontalAlignment = align, content = now)
        return
    }
    Box(modifier, contentAlignment = if (centred) Alignment.Center else Alignment.CenterStart) {
        Column(Modifier.fillMaxWidth().graphicsLayer { alpha = over() }.clearAndSetSemantics {}, horizontalAlignment = align, content = old)
        Column(Modifier.fillMaxWidth().graphicsLayer { alpha = 1f - over() }, horizontalAlignment = align, content = now)
    }
}
