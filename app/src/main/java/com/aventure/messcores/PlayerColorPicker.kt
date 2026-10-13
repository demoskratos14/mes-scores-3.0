package com.aventure.messcores

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pastille de la couleur d'un joueur. En mode daltonien ([LocalColorBlindMode]), le code de la
 * couleur (A, B, C…) est écrit dedans ; il est masqué à TalkBack, qui lit déjà le nom du joueur.
 */
@Composable
internal fun PlayerColorDot(color: Color, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val code = if (LocalColorBlindMode.current) colorCode(color) else null
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        if (code != null) {
            Text(
                text = code,
                color = contentColorFor(color),
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.5f).sp,
                modifier = Modifier.clearAndSetSemantics {}
            )
        }
    }
}

/**
 * Choix de la couleur d'un joueur : toute la palette est proposée, mais les couleurs déjà
 * prises par un autre joueur ([takenColors]) sont grisées, barrées et non sélectionnables.
 * [currentColor] est null tant que la couleur est laissée au hasard (écran de départ).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerColorPicker(
    currentColor: Color?,
    takenColors: Set<Color>,
    onPick: (Color) -> Unit
) {
    val colorBlind = LocalColorBlindMode.current
    val selectedState = stringResource(R.string.score_color_selected)
    val takenState = stringResource(R.string.score_color_taken)
    val pickLabel = stringResource(R.string.score_color_pick)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PLAYER_COLOR_PALETTE.forEachIndexed { index, color ->
            val selected = color == currentColor
            val taken = !selected && color in takenColors
            val description = stringResource(R.string.score_color_option, index + 1)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .alpha(if (taken) 0.3f else 1f)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape)
                        else Modifier
                    )
                    .clickable(enabled = !taken, onClickLabel = pickLabel, role = Role.RadioButton) { onPick(color) }
                    .semantics {
                        contentDescription = description
                        if (selected) stateDescription = selectedState
                        else if (taken) stateDescription = takenState
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val code = if (colorBlind) colorCode(color) else null
                    if (code != null) {
                        Text(
                            text = code,
                            color = contentColorFor(color),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clearAndSetSemantics {}
                        )
                    }
                    if (selected || taken) {
                        Icon(
                            imageVector = if (selected) Icons.Filled.Check else Icons.Filled.Close,
                            contentDescription = null,
                            tint = contentColorFor(color),
                            modifier = Modifier.size(if (code != null) 14.dp else 24.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Fenêtre de choix de couleur. [onRandom] non null : un bouton « Aléatoire » remet la couleur
 * au hasard (écran de départ). [name] non null : le nom complet du joueur est affiché au-dessus.
 */
@Composable
internal fun PlayerColorDialog(
    title: String,
    name: String?,
    currentColor: Color?,
    takenColors: Set<Color>,
    onPick: (Color) -> Unit,
    onRandom: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) } },
        dismissButton = if (onRandom != null) {
            { TextButton(onClick = onRandom) { Text(stringResource(R.string.score_color_random)) } }
        } else null,
        title = { Text(title) },
        text = {
            Column {
                if (name != null) {
                    Text(name)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Text(
                    text = stringResource(R.string.score_color_title),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                PlayerColorPicker(currentColor = currentColor, takenColors = takenColors, onPick = onPick)
            }
        }
    )
}

/** Fenêtre d'un joueur en cours de partie : nom complet + choix de sa couleur (jamais celle d'un autre). */
@Composable
internal fun PlayerOptionsDialog(viewModel: ScoreViewModel, playerIndex: Int, onDismiss: () -> Unit) {
    PlayerColorDialog(
        title = stringResource(R.string.score_player),
        name = viewModel.players.getOrElse(playerIndex) { "" },
        currentColor = viewModel.playerColors.getOrNull(playerIndex),
        takenColors = viewModel.playerColors.filterIndexed { i, _ -> i != playerIndex }.toSet(),
        onPick = { viewModel.setPlayerColor(playerIndex, it) },
        onRandom = null,
        onDismiss = onDismiss
    )
}
