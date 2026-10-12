package com.aventure.messcores

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Case du tableau : elle n'affiche que le score (dans la couleur du joueur).
 * Un clic appelle [onClick] : le tableau ouvre alors la fenêtre de saisie (voir [ScoreEntryDialog]).
 * Le sélecteur de règle de multiplication reste au-dessus de la case (si le jeu en a plusieurs).
 */
@Composable
internal fun ScoreCell(
    display: String? = null,
    cell: CellState,
    multipliers: List<ScoreMultiplier>,
    playerColor: Color,
    onClick: () -> Unit,
    onMultiplierPicked: (String) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (multipliers.size > 1) {
            var expanded by remember { mutableStateOf(false) }
            val current = multipliers.find { it.id == cell.multiplierId } ?: multipliers.first()
            Box {
                Text(
                    text = "${current.displayLabel()} ×${current.factor}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable(onClickLabel = stringResource(R.string.score_change_multiplier), role = Role.Button) { expanded = true }
                        .padding(vertical = 2.dp)
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    multipliers.forEach { multiplier ->
                        DropdownMenuItem(
                            text = { Text("${multiplier.displayLabel()} ×${multiplier.factor}") },
                            onClick = {
                                expanded = false
                                onMultiplierPicked(multiplier.id)
                            }
                        )
                    }
                }
            }
        }

        val base = cell.baseValue
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 4.dp)
                .height(48.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = stringResource(R.string.score_enter), role = Role.Button) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (display != null) {
                Text(
                    text = display,
                    color = playerTextColor(playerColor),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            } else if (base != null) {
                Text(
                    text = (if (cell.isNegative) "−" else "") + base,
                    color = playerTextColor(playerColor),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/** Nom de la catégorie : texte écrit par l'utilisateur (feuille personnalisée) ou texte de la feuille intégrée. */
@Composable
internal fun SheetRow.title(): String = labelText ?: stringResource(label)
