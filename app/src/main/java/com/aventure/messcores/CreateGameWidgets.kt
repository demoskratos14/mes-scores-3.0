package com.aventure.messcores

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType

@Composable
internal fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    // La ligne entière est l'interrupteur : TalkBack annonce le titre avec l'état.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Champ numérique : ne garde que les chiffres (6 au plus, pour rester dans un Int). */
@Composable
internal fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { c -> c.isDigit() }.take(6)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

internal fun GameVariant.labelRes(): Int = when (this) {
    GameVariant.NONE -> R.string.create_variant_classic
    GameVariant.SHEET -> R.string.create_variant_sheet
    GameVariant.BID -> R.string.create_variant_bid
    GameVariant.EXACT_TARGET -> R.string.create_variant_exact
    GameVariant.COUNTDOWN -> R.string.create_variant_countdown
    GameVariant.TAROT -> R.string.create_variant_tarot
}

/** Explication de la mécanique, ou null pour « classique » (le mode de score a déjà son texte). */
internal fun GameVariant.descriptionRes(): Int? = when (this) {
    GameVariant.NONE -> null
    GameVariant.SHEET -> R.string.create_variant_sheet_desc
    GameVariant.BID -> R.string.create_variant_bid_desc
    GameVariant.EXACT_TARGET -> R.string.create_variant_exact_desc
    GameVariant.COUNTDOWN -> R.string.create_variant_countdown_desc
    GameVariant.TAROT -> R.string.create_variant_tarot_desc
}
