package com.aventure.messcores

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Points d'une donne avec leur signe (+ ou −), ou « 0 ». */
internal fun signedPoints(value: Int): String = when {
    value > 0 -> "+$value"
    value < 0 -> "−${-value}"
    else -> "0"
}

/** Rangée de choix exclusifs (une seule puce choisie), défilante si elle ne tient pas à l'écran. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContractChoiceRow(options: List<String>, selected: Int?, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { index, label ->
            FilterChip(
                selected = selected == index,
                onClick = { onSelect(index) },
                label = { Text(label) }
            )
        }
    }
}

/** Rangée d'options à cocher indépendamment : chaque paire donne le libellé et l'état de la puce. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContractToggleRow(options: List<Pair<String, Boolean>>, onToggle: (Int) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { index, (label, checked) ->
            FilterChip(
                selected = checked,
                onClick = { onToggle(index) },
                label = { Text(label) }
            )
        }
    }
}
