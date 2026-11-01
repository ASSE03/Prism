package com.prism.music.ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.prism.music.ui.components.Artwork

/*
 * Prism TV's pieces (1.8.0): every TV screen is made of these, so focus looks and behaves the same everywhere. Focus
 * is drawn as Compose for TV does it: the focused thing grows a little and gets a light outline (seen from across a
 * room), no glow or blur (cheap on slow TVs).
 */

/**
 * A row of cards' padding: the screen's 48 dp margin inside the row (so the first card's focus growth isn't cut off
 * at the row's edge), and room above and below for it.
 */
val TvRowPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 10.dp, bottom = 10.dp)

/** How much a focused card grows. */
private const val FOCUS_SCALE = 1.08f
/** The focus outline. */
private val FOCUS_RING = 3.dp

@Composable
private fun focusBorder(shape: Shape) = Border(BorderStroke(FOCUS_RING, MaterialTheme.colorScheme.onSurface), shape = shape)

/** A row's title (Quick picks, Your mixes…), above its cards. */
@Composable
fun TvRowTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
}

/**
 * A song, album, playlist, mix or artist in a row: its picture (round for artists), with the title and a quiet second
 * line below. OK opens or plays it; holding OK is [onLongClick] (its menu).
 */
@Composable
fun TvCard(
    art: String?,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    width: Dp = 132.dp,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = if (round) CircleShape else RoundedCornerShape(10.dp)
    Column(Modifier.width(width)) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.fillMaxWidth().aspectRatio(1f),
            shape = CardDefaults.shape(shape),
            scale = CardDefaults.scale(focusedScale = FOCUS_SCALE),
            border = CardDefaults.border(focusedBorder = focusBorder(shape)),
            colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Artwork(art, Modifier.fillMaxSize(), shape = shape, size = 360)
        }
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A labeled button (Play, Shuffle, Sign in with your phone…); [primary] is the screen's main action, filled. */
@Composable
fun TvButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, primary: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier,
        scale = ButtonDefaults.scale(focusedScale = 1.05f),
        colors = if (primary) ButtonDefaults.colors(containerColor = scheme.primary, contentColor = scheme.onPrimary, focusedContainerColor = scheme.onSurface, focusedContentColor = scheme.surface)
        else ButtonDefaults.colors(containerColor = scheme.surfaceVariant, contentColor = scheme.onSurface, focusedContainerColor = scheme.onSurface, focusedContentColor = scheme.surface),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

/** A round icon button (the player's controls); [big] is play/pause. [label] is what TalkBack reads. */
@Composable
fun TvIconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, big: Boolean = false, on: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val size = if (big) 72.dp else 52.dp
    IconButton(
        onClick = onClick,
        modifier = modifier.size(size),
        scale = IconButtonDefaults.scale(focusedScale = 1.1f),
        colors = IconButtonDefaults.colors(
            containerColor = if (big) scheme.onSurface else Color.Transparent,
            contentColor = if (big) scheme.surface else if (on) scheme.primary else scheme.onSurface,
            focusedContainerColor = scheme.onSurface, focusedContentColor = scheme.surface,
        ),
    ) {
        Icon(icon, label, Modifier.size(if (big) 36.dp else 26.dp))
    }
}

/** One song in a list (an album, a playlist, the queue, search results): number or picture, title, artist, length. */
@Composable
fun TvSongRow(
    title: String,
    artist: String,
    length: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    number: Int? = null,
    art: String? = null,
    playing: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    ListItem(
        selected = playing,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        scale = ListItemDefaults.scale(focusedScale = 1.02f),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent, selectedContainerColor = scheme.primary.copy(alpha = 0.18f),
            focusedContainerColor = scheme.onSurface, focusedContentColor = scheme.surface,
        ),
        leadingContent = when {
            art != null -> { { Artwork(art, Modifier.size(44.dp), shape = RoundedCornerShape(6.dp), size = 120) } }
            number != null -> { { Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) { Text("$number", style = MaterialTheme.typography.titleSmall) } } }
            else -> null
        },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = length?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
    )
}

/** How far the song is: a thin line with the time played and the time left under it. */
@Composable
fun TvProgress(fraction: Float, played: String, total: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(scheme.onSurface.copy(alpha = 0.22f))) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(scheme.onSurface))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(played, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            Text(total, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        }
    }
}
