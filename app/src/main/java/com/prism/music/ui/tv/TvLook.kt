package com.prism.music.ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import com.prism.music.ui.components.FrostedBackdrop
import com.prism.music.ui.components.rememberArtPalette
import com.prism.music.ui.components.rememberFrost
import com.prism.music.ui.player.ArtworkMeshBackdrop
import com.prism.music.ui.player.rememberArtworkMesh
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.theme.PrismTokens

/*
 * Prism TV's look (1.8.0, after the owner's TV): the phone's Aura × Spectrum on a TV. The frost of what's playing (or
 * played last) behind the places, the cover's own frost behind its album or artist page, the cover-made mesh behind
 * the player; frosted glass panels; and the split light as the one focus mark (a spectrum ring round what has focus).
 *
 * Made for a mid-range TV's GPU: every backdrop is drawn once into its own layer from a small picture blurred once in
 * software (cached per cover), never blurred live; nothing moves on its own while the remote is down.
 */

/** Soft corners, as the phone's ([PrismTokens.Radius]). */
object TvShapes {
    val card = RoundedCornerShape(PrismTokens.Radius.art)
    val panel = RoundedCornerShape(PrismTokens.Radius.large)
    val art = RoundedCornerShape(18.dp)
    /** A side panel's (the queue, a menu): soft on its leading edge, against the screen's edge on the other. */
    val panelStart = RoundedCornerShape(topStart = PrismTokens.Radius.large, bottomStart = PrismTokens.Radius.large)
}

/** The focus mark's width on cards and on buttons. */
val TV_RING = 3.dp
val TV_RING_THIN = 2.dp

/** The split light round what has focus (the phone's play ring, round any shape). */
@Composable
fun tvRing(): Brush = PrismTokens.spectrumRing()

/** The split light along a line (progress, the playing title, the current place's mark). */
@Composable
fun tvLight(): Brush = PrismTokens.spectrum(dark = true)

/** A focused card's or button's border: the split light round it. */
@Composable
fun tvFocusBorder(shape: Shape, width: androidx.compose.ui.unit.Dp = TV_RING): Border = Border(BorderStroke(width, tvRing()), shape = shape)

/** Frosted glass's fill on a TV (dark rooms: a light veil over the frost behind). */
val TvGlassFill = Color.White.copy(alpha = 0.10f)
/** Panels over a page (the queue, a menu, a setting's choices): darker glass, so what's on them reads. */
val TvPanelFill = Color(0xFF0C0C11).copy(alpha = 0.86f)

/** Frosted glass: [fill], with the phone's light top edge. */
fun Modifier.tvGlass(shape: Shape, fill: Color = TvGlassFill): Modifier = this
    .background(fill, shape)
    .border(0.7.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.02f))), shape)

/** The current place's mark in the side navigation: a short bar of the split light at its leading edge. */
fun Modifier.tvCurrentMark(on: Boolean, light: Brush): Modifier = if (!on) this else drawWithContent {
    drawContent()
    val h = size.height * 0.46f
    drawRoundRect(light, topLeft = Offset(4.dp.toPx(), (size.height - h) / 2), size = Size(4.dp.toPx(), h), cornerRadius = CornerRadius(2.dp.toPx()))
}

/**
 * The frost behind Prism TV's places (Home, Search, Library, Settings): the phone's Ambient frost, from the cover of
 * what's playing or played last. Drawn once into its own layer; a new song's cover replaces it.
 */
@Composable
fun TvAura(modifier: Modifier = Modifier) {
    val c = LocalContainer.current
    val current by c.player.currentSong.collectAsState()
    val recent by c.library.recent.collectAsState()
    TvAuraFrom(current?.thumbnail ?: recent.firstOrNull()?.thumbnail, modifier)
}

/** [TvAura] from the cover at [url] (the design pages draw it without the app around them). */
@Composable
fun TvAuraFrom(url: String?, modifier: Modifier = Modifier) {
    val frost = rememberFrost(url)
    val palette = rememberArtPalette(url)
    FrostedBackdrop(frost, palette, TV_AURA_HEIGHT, { 0f }, modifier.fillMaxSize().tvStill(), parallax = 0f, ambient = true)
}

/**
 * A backdrop that doesn't move: drawn into a layer of its own, whose pixels the TV keeps and puts back each frame (a
 * focus move redraws what moved, not the frost and its grain under it).
 */
fun Modifier.tvStill(): Modifier = graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }

/** How much of the screen's top the frost's picture covers before its colors run on down (a TV's whole height). */
private val TV_AURA_HEIGHT = 540.dp

/**
 * An album's, playlist's or artist's page: its own picture's frost behind it, held to the Aura's deep tone (the
 * picture itself sits beside the text on a TV, not above it, so the whole page is where text reads). Drawn once into
 * its own layer.
 */
@Composable
fun TvPictureBackdrop(url: String?, modifier: Modifier = Modifier) {
    TvAuraFrom(url, modifier)
}

/** The player's (and lyrics') backdrop: the phone's cover-made mesh, still, without a live blur. */
@Composable
fun TvMeshBackdrop(url: String?, modifier: Modifier = Modifier) {
    ArtworkMeshBackdrop(rememberArtworkMesh(url), modifier.fillMaxSize().tvStill(), liveBlur = false)
}
