package com.aventure.messcores

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Nom et couleur de chaque rangée : rouge, jaune, vert, bleu (comme les dés de la table, voir [DiceSetups]).
private val ROW_NAMES = listOf(
    R.string.sheet_qwixx_red,
    R.string.sheet_qwixx_yellow,
    R.string.sheet_qwixx_green,
    R.string.sheet_qwixx_blue
)
private val ROW_COLORS = listOf(Color(0xFFEF5350), Color(0xFFFFEE58), Color(0xFF66BB6A), Color(0xFF42A5F5))

private const val LOCK_SYMBOL = "🔒"
private const val CROSS_SYMBOL = "✕"

/**
 * Le Qwixx jouable, tour par tour (voir [QwixxRound]) : le joueur actif lance les dés, chacun coche (ou non) la somme des dés blancs
 * sur son plateau, puis le joueur actif coche (ou non) un dé blanc + un dé de couleur. L'appli ne propose que les croix permises,
 * pose les cadenas, compte les pénalités et les points, et inscrit tout dans le tableau des scores à la fin de la partie.
 *
 * [canAct] : ce téléphone joue pour le joueur dont c'est le tour. [canActFor] : ce téléphone joue pour ce joueur (ses croix sur les
 * dés blancs). [onChanged] : la partie a changé, à partager aux autres téléphones.
 */
@Composable
internal fun QwixxSection(
    viewModel: ScoreViewModel,
    round: QwixxRound,
    canAct: Boolean,
    canActFor: (Int) -> Boolean,
    onChanged: () -> Unit
) {
    // Partie déjà terminée (reprise d'une partie finie) : le bandeau de fin de partie suffit.
    if (!round.over && viewModel.isGameOver()) return

    val players = viewModel.players
    // Les dés tournent encore : les croix possibles attendent qu'ils soient arrêtés.
    val animation = rememberDiceRollAnimation(round.turn)
    val rolling = animation.rolling
    val step = round.step
    val activeName = players.getOrElse(round.current) { "" }
    val showChoices = round.turn.hasRolled && !rolling

    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step == QwixxStep.OVER) {
                Text(stringResource(R.string.dice_qwixx_game_over), style = MaterialTheme.typography.titleMedium)
            } else {
                Text(
                    text = stringResource(R.string.dice_bunco_turn, activeName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                val closedDice = (0 until QwixxScoring.ROWS).filter { round.isRowClosed(it) }.map { 2 + it }.toSet()
                DiceTray(
                    turn = round.turn,
                    animation = animation,
                    rollEnabled = step == QwixxStep.ROLL && canAct,
                    interactive = canAct,
                    showNewTurn = false,
                    locked = closedDice,
                    showHoldHint = false,
                    onRoll = {
                        round.roll()
                        onChanged()
                    },
                    onToggleHold = {},
                    onNewTurn = {}
                )
                if (step == QwixxStep.ROLL) {
                    Text(stringResource(R.string.dice_qwixx_roll_hint), style = MaterialTheme.typography.bodyMedium)
                }
                if (showChoices && (step == QwixxStep.WHITE)) {
                    Text(
                        text = stringResource(R.string.dice_qwixx_white_prompt, round.whiteSum),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (showChoices && (step == QwixxStep.COLOR || step == QwixxStep.END)) {
                    ColorPart(
                        round = round,
                        activeName = activeName,
                        enabled = canAct,
                        onChanged = onChanged
                    )
                }
                if (showChoices && step == QwixxStep.END) {
                    if (round.penaltyPending) {
                        Text(
                            text = stringResource(R.string.dice_qwixx_penalty_note, activeName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Button(
                        onClick = {
                            // Fin de partie : les points de chacun sont inscrits une seule fois, par le téléphone qui passe la main.
                            if (round.endTurn()) round.writeTo(viewModel)
                            onChanged()
                        },
                        enabled = canAct,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.dice_bunco_next_player))
                    }
                }
            }
        }
    }

    players.forEachIndexed { index, name ->
        PlayerCard(
            name = name,
            index = index,
            round = round,
            step = step,
            showChoices = showChoices,
            enabled = canActFor(index),
            onChanged = onChanged
        )
    }
}

/** Le choix du joueur actif avec un dé blanc et un dé de couleur : les croix possibles, ou ce qu'il a choisi (avec « Annuler »). */
@Composable
private fun ColorPart(round: QwixxRound, activeName: String, enabled: Boolean, onChanged: () -> Unit) {
    val choice = round.colorChoice
    when {
        choice in 0 until QwixxScoring.ROWS -> ChoiceLine(
            text = stringResource(R.string.dice_qwixx_marked, stringResource(ROW_NAMES[choice]), round.colorNumber),
            enabled = enabled,
            onCancel = {
                round.cancelColor()
                onChanged()
            }
        )
        choice == QwixxScoring.PASS -> ChoiceLine(
            text = stringResource(R.string.dice_qwixx_passed),
            enabled = enabled,
            onCancel = {
                round.cancelColor()
                onChanged()
            }
        )
        else -> {
            val options = round.colorOptions()
            if (options.isEmpty()) {
                Text(stringResource(R.string.dice_qwixx_color_none), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    text = stringResource(R.string.dice_qwixx_color_prompt, activeName),
                    style = MaterialTheme.typography.bodyMedium
                )
                options.groupBy { it.row }.forEach { (row, moves) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        moves.forEach { move ->
                            Button(
                                onClick = {
                                    round.markColor(move.row, move.number)
                                    onChanged()
                                },
                                enabled = enabled,
                                colors = ButtonDefaults.buttonColors(containerColor = ROW_COLORS[row], contentColor = Color.Black),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.dice_qwixx_pick, stringResource(ROW_NAMES[row]), move.number), maxLines = 1)
                            }
                        }
                        if (moves.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
                OutlinedButton(
                    onClick = {
                        round.passColor()
                        onChanged()
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.dice_qwixx_pass))
                }
            }
        }
    }
}

/** Ce qu'un joueur a choisi, avec un bouton pour le défaire. */
@Composable
private fun ChoiceLine(text: String, enabled: Boolean, onCancel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (enabled) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.dice_qwixx_cancel)) }
        }
    }
}

/** Le plateau d'un joueur (ses quatre rangées), son score, ses pénalités, et son choix sur les dés blancs. */
@Composable
private fun PlayerCard(
    name: String,
    index: Int,
    round: QwixxRound,
    step: QwixxStep,
    showChoices: Boolean,
    enabled: Boolean,
    onChanged: () -> Unit
) {
    val mine = index == round.current && step != QwixxStep.OVER
    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.dice_pig_score_line, name, round.scoreOf(index)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                    color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.dice_qwixx_penalties, round.penalties[index], QwixxScoring.MAX_PENALTIES),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (round.penalties[index] > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            for (row in 0 until QwixxScoring.ROWS) {
                RowView(row = row, mask = round.boards[index][row], closed = round.isRowClosed(row), crosses = round.crossesOf(index, row))
            }
            if (showChoices && (step == QwixxStep.WHITE || step == QwixxStep.COLOR || step == QwixxStep.END)) {
                WhiteChoice(index = index, round = round, step = step, enabled = enabled, onChanged = onChanged)
            }
        }
    }
}

/** Le choix d'un joueur sur la somme des dés blancs : les rangées où il peut cocher, ou ce qu'il a choisi (avec « Annuler »). */
@Composable
private fun WhiteChoice(index: Int, round: QwixxRound, step: QwixxStep, enabled: Boolean, onChanged: () -> Unit) {
    val choice = round.whiteChoices.getOrElse(index) { QwixxScoring.NO_CHOICE }
    // Le choix peut se défaire jusqu'à ce que le joueur actif ait fait le sien avec un dé de couleur.
    val canCancel = enabled && round.colorChoice == QwixxScoring.NO_CHOICE
    when {
        choice in 0 until QwixxScoring.ROWS -> ChoiceLine(
            text = stringResource(R.string.dice_qwixx_marked, stringResource(ROW_NAMES[choice]), round.whiteSum),
            enabled = canCancel,
            onCancel = {
                round.cancelWhite(index)
                onChanged()
            }
        )
        choice == QwixxScoring.PASS -> ChoiceLine(
            text = stringResource(R.string.dice_qwixx_passed),
            enabled = canCancel,
            onCancel = {
                round.cancelWhite(index)
                onChanged()
            }
        )
        else -> {
            val options = round.whiteOptions(index)
            if (options.isEmpty()) {
                Text(stringResource(R.string.dice_qwixx_no_move), style = MaterialTheme.typography.bodySmall)
            } else if (step == QwixxStep.WHITE) {
                Text(
                    text = stringResource(R.string.dice_qwixx_white_pick, round.whiteSum),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    options.forEach { row ->
                        Button(
                            onClick = {
                                round.markWhite(index, row)
                                onChanged()
                            },
                            enabled = enabled,
                            colors = ButtonDefaults.buttonColors(containerColor = ROW_COLORS[row], contentColor = Color.Black),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(ROW_NAMES[row]), maxLines = 1)
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        round.passWhite(index)
                        onChanged()
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.dice_qwixx_pass))
                }
            }
        }
    }
}

/** Une rangée du plateau : ses nombres dans l'ordre où on les coche, puis le cadenas. */
@Composable
private fun RowView(row: Int, mask: Int, closed: Boolean, crosses: Int) {
    val color = ROW_COLORS[row]
    val description = stringResource(R.string.dice_qwixx_row_desc, stringResource(ROW_NAMES[row]), crosses)
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (closed) 0.5f else 1f)
            .semantics { contentDescription = description }
    ) {
        QwixxScoring.numbersOf(row).forEach { number ->
            val marked = QwixxScoring.isMarked(mask, number)
            Cell(
                text = if (marked) CROSS_SYMBOL else number.toString(),
                background = if (marked) color else color.copy(alpha = 0.25f),
                textColor = if (marked) Color.Black else MaterialTheme.colorScheme.onSurface,
                bold = marked,
                modifier = Modifier.weight(1f)
            )
        }
        val locked = QwixxScoring.isLocked(mask)
        Cell(
            text = LOCK_SYMBOL,
            background = if (locked) color else color.copy(alpha = 0.25f),
            textColor = if (locked) Color.Black else MaterialTheme.colorScheme.onSurface,
            bold = locked,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun Cell(text: String, background: Color, textColor: Color, bold: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(0.8f)
            .clip(shape)
            .background(background, shape)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}
