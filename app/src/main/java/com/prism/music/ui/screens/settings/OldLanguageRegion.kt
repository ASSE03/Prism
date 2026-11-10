package com.prism.music.ui.screens.settings

import com.prism.music.R
import com.prism.music.tr
import com.prism.music.ui.components.PrismTextButton
import com.prism.music.ui.theme.PrismIcons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.prism.music.ui.theme.LocalAppSettings
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.screens.Chevron
import com.prism.music.ui.screens.Group
import com.prism.music.ui.screens.Item

private fun countryName(code: String): String =
    java.util.Locale("", code).displayCountry.takeIf { it.isNotBlank() && it != code } ?: code

/**
 * The country YouTube Music is asked as. Where it isn't offered, picking one where it is (the US, say) brings
 * Home, charts and search back; songs then play as long as YouTube itself is available there.
 */
@Composable
internal fun ContentCountrySetting() {
    val c = LocalContainer.current
    val s = LocalAppSettings.current
    var picking by remember { mutableStateOf(false) }
    val phone = java.util.Locale.getDefault().country
    Group(tr(R.string.region)) {
        Item(
            tr(R.string.content_country),
            if (s.contentCountry.isBlank()) tr(R.string.automatic_3, if (phone.isBlank()) tr(R.string.united_states) else countryName(phone)) else countryName(s.contentCountry),
            info = tr(R.string.home_charts_and_search_follow_this),
            onClick = { picking = true },
        ) { Chevron() }
    }
    if (picking) {
        val countries = remember {
            java.util.Locale.getISOCountries().map { it to countryName(it) }.sortedBy { it.second }
        }
        val pick: (String) -> Unit = { code -> c.settings.setContentCountry(code); picking = false }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(tr(R.string.content_country)) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.height(420.dp)) {
                    item { CountryRow(tr(R.string.automatic_follow_the_phone), s.contentCountry.isBlank()) { pick("") } }
                    item { CountryRow(tr(R.string.united_states), s.contentCountry == "US") { pick("US") } }
                    items(countries.size) { i ->
                        val (code, name) = countries[i]
                        CountryRow(name, s.contentCountry == code) { pick(code) }
                    }
                }
            },
            confirmButton = { PrismTextButton(onClick = { picking = false }) { Text(tr(R.string.cancel)) } },
        )
    }
}

@Composable
private fun CountryRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        if (selected) Icon(PrismIcons.Check, null, tint = MaterialTheme.colorScheme.primary)
    }
}
