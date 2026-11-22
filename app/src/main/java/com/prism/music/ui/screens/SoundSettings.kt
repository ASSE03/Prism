package com.prism.music.ui.screens

import com.prism.music.R
import com.prism.music.tr
import com.prism.music.ui.components.PrismSlider
import com.prism.music.ui.components.PrismSwitch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import com.prism.music.data.prefs.CrossfadeCurve
import com.prism.music.data.prefs.NORMALIZE_TARGETS
import kotlin.math.roundToInt
import com.prism.music.ui.theme.LocalAppSettings
import com.prism.music.ui.theme.LocalContainer

@Composable
internal fun CurveSample(curve: CrossfadeCurve) {
    val incoming = MaterialTheme.colorScheme.primary
    val outgoing = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(44.dp)) {
        val steps = 40
        fun path(pick: (Pair<Float, Float>) -> Float) = androidx.compose.ui.graphics.Path().apply {
            for (i in 0..steps) {
                val p = i.toFloat() / steps
                val x = p * size.width
                val y = size.height - pick(curve.gains(p)) * (size.height - 4f) - 2f
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawPath(path { it.second }, outgoing, style = stroke)
        drawPath(path { it.first }, incoming, style = stroke)
    }
}

/**
 * The two volumes across a change of song for each Crossfade choice (1.8.8: its pick sheet's example cards), at [p]
 * (0 … 1 of the card's width): the next song's, then the ending one's. Off: one stops, a gap, the next starts. Automatic:
 * a long blend. Fixed length: a shorter one, always the same. Automix: both held up together, then the ending song let
 * go on a beat (a bass swap, the move it uses most).
 */
internal fun crossfadeGains(x: Crossfade, p: Float): Pair<Float, Float> {
    fun ramp(from: Float, to: Float) = ((p - from) / (to - from)).coerceIn(0f, 1f)
    return when (x) {
        Crossfade.OFF -> (if (p >= 0.54f) 1f else 0f) to (if (p <= 0.46f) 1f else 0f)
        Crossfade.AUTO -> ramp(0.2f, 0.8f) to 1f - ramp(0.2f, 0.8f)
        Crossfade.FIXED -> ramp(0.35f, 0.65f) to 1f - ramp(0.35f, 0.65f)
        Crossfade.AUTOMIX -> (0.85f * ramp(0.25f, 0.35f) + 0.15f * ramp(0.6f, 0.62f)) to (if (p < 0.6f) 1f else 1f - ramp(0.6f, 0.75f))
    }
}

/** A Crossfade choice's example card: [crossfadeGains] drawn as [CurveSample] draws a curve, the beats marked under Automix's. */
@Composable
internal fun CrossfadeSample(x: Crossfade) {
    val incoming = MaterialTheme.colorScheme.primary
    val outgoing = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val beats = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(44.dp)) {
        val steps = 80
        fun path(pick: (Pair<Float, Float>) -> Float) = androidx.compose.ui.graphics.Path().apply {
            for (i in 0..steps) {
                val p = i.toFloat() / steps
                val x0 = p * size.width
                val y = size.height - pick(crossfadeGains(x, p)) * (size.height - 4f) - 2f
                if (i == 0) moveTo(x0, y) else lineTo(x0, y)
            }
        }
        if (x == Crossfade.AUTOMIX) for (b in 1..9) {
            val bx = size.width * b / 10f
            drawLine(beats, androidx.compose.ui.geometry.Offset(bx, size.height - 6.dp.toPx()), androidx.compose.ui.geometry.Offset(bx, size.height), 1.dp.toPx())
        }
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawPath(path { it.second }, outgoing, style = stroke)
        drawPath(path { it.first }, incoming, style = stroke)
    }
}

/** What a Crossfade choice's card says under its example. */
internal fun crossfadeDetail(x: Crossfade): String = tr(
    when (x) {
        Crossfade.OFF -> R.string.crossfade_off_detail
        Crossfade.AUTO -> R.string.crossfade_auto_detail
        Crossfade.FIXED -> R.string.crossfade_fixed_detail
        Crossfade.AUTOMIX -> R.string.crossfade_automix_detail
    },
)

/** "1×", "0.85×", "1.25×". */
fun speedText(speed: Float): String = "%.2f".format(speed).trimEnd('0').trimEnd('.') + "×"

/** "As recorded", "+3", "−2" (semitones). */
fun pitchText(semitones: Int): String = when {
    semitones == 0 -> tr(R.string.as_recorded)
    semitones > 0 -> "+$semitones"
    else -> "−${-semitones}"
}