package com.prism.music.ui.screens.settings

import com.prism.music.R
import com.prism.music.tr
import com.prism.music.ui.components.PrismTextButton
import com.prism.music.ui.theme.PrismIcons
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prism.music.ui.theme.LocalContainer

@Composable
internal fun ResetLook() {
    val prefs = LocalContainer.current.settings
    var ask by remember { mutableStateOf(false) }
    PrismTextButton(onClick = { ask = true }, Modifier.fillMaxWidth()) {
        Icon(PrismIcons.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(tr(R.string.reset_look_layout_to_defaults))
    }
    if (ask) androidx.compose.material3.AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text(tr(R.string.reset_look_layout)) },
        text = { Text(tr(R.string.font_text_size_corners_spacing_navigation)) },
        confirmButton = { PrismTextButton(onClick = { prefs.resetLook(); ask = false }) { Text(tr(R.string.reset)) } },
        dismissButton = { PrismTextButton(onClick = { ask = false }) { Text(tr(R.string.cancel)) } },
    )
}
