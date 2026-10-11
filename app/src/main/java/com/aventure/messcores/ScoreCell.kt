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
import androidx.compose.runtime.saveable.rememberSaveable
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
 * Un clic ouvre une fenêtre de saisie (voir [ScoreInputDialog]) où l'on écrit le
 * score et, si les scores négatifs sont autorisés, choisit le signe. Le sélecteur de
 * règle de multiplication reste au-dessus de la case (si le jeu en a plusieurs).
 */
@Composable
internal fun ScoreCell(
    choices: List<Int>? = null,
    title: String? = null,
    hint: String? = null,
    zeroCrossesOut: Boolean = true,
    bidScoring: String? = null,
    bowlingFrame: Int? = null,
    display: String? = null,
    cell: CellState,
    allowNegative: Boolean,
    multipliers: List<ScoreMultiplier>,
    playerColor: Color,
    onValueEntered: (Int?, Boolean) -> Unit,
    onMultiplierPicked: (String) -> Unit
) {
    // rememberSaveable : la boîte de saisie reste ouverte après une rotation.
    var showDialog by rememberSaveable { mutableStateOf(false) }

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
                .clickable(onClickLabel = stringResource(R.string.score_enter), role = Role.Button) { showDialog = true },
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

    if (showDialog && choices != null) {
        ScoreChoiceDialog(
            title = title ?: stringResource(R.string.score_enter_title),
            options = choices,
            hint = hint,
            zeroCrossesOut = zeroCrossesOut,
            current = cell.baseValue,
            onPick = { value ->
                showDialog = false
                onValueEntered(value, false)
            },
            onDismiss = { showDialog = false }
        )
    } else if (showDialog && bowlingFrame != null) {
        BowlingFrameDialog(
            frame = bowlingFrame,
            title = title,
            onConfirm = { code ->
                showDialog = false
                onValueEntered(code, false)
            },
            onDismiss = { showDialog = false }
        )
    } else if (showDialog && bidScoring != null) {
        BidScoreDialog(
            title = title,
            kind = bidScoring,
            onConfirm = { score ->
                showDialog = false
                onValueEntered(score, false)
            },
            onDismiss = { showDialog = false }
        )
    } else if (showDialog) {
        ScoreInputDialog(
            title = title,
            initialValue = cell.baseValue,
            initialNegative = cell.isNegative,
            allowNegative = allowNegative,
            onConfirm = { value, negative ->
                showDialog = false
                onValueEntered(value, negative)
            },
            onDismiss = { showDialog = false }
        )
    }
}

/** Nom de la catégorie : texte écrit par l'utilisateur (feuille personnalisée) ou texte de la feuille intégrée. */
@Composable
internal fun SheetRow.title(): String = labelText ?: stringResource(label)
