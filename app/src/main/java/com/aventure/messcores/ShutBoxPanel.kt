package com.aventure.messcores

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/**
 * Le Shut the Box jouable, tour par tour (voir [ShutBoxRound]) : chaque joueur lance les dés, rabat des clapets dont la somme
 * est égale au total, et recommence jusqu'à ne plus pouvoir (ou jusqu'à fermer la boîte). L'appli repère les combinaisons
 * possibles, accepte le seul dé quand les clapets 7, 8 et 9 sont rabattus, compte les clapets restés ouverts, les inscrit
 * au tableau des scores (qu'on n'a pas besoin d'ouvrir : les scores sont sur cette page) et passe la main. La partie compte
 * [ShutBoxScoring.ROUNDS] manches ; le plus petit total gagne.
 *
 * [canAct] : ce téléphone joue pour le joueur dont c'est le tour. [onChanged] : la partie a changé, à partager aux
 * autres téléphones.
 */
@Composable
internal fun ShutBoxSection(
    viewModel: ScoreViewModel,
    round: ShutBoxRound,
    canAct: Boolean,
    onChanged: () -> Unit
) {
    val players = viewModel.players
    // Les dés tournent encore : ce qu'ils permettent de rabattre attend qu'ils soient arrêtés.
    val animation = rememberDiceRollAnimation(round.turn)
    val over = viewModel.isGameOver()
    val totals = viewModel.totals
    val rounds = viewModel.gameRules.endCondition.roundCount ?: ShutBoxScoring.ROUNDS
    val nextRow = viewModel.scores.indexOfFirst { it.getOrNull(round.current)?.baseValue == null }
    val roundNumber = ((if (nextRow < 0) viewModel.scores.size else nextRow) + 1).coerceAtMost(rounds)

    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.dice_sb_scores_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                players.forEachIndexed { index, name ->
                    val mine = !over && index == round.current
                    Text(
                        text = stringResource(R.string.dice_pig_score_line, name, totals.getOrElse(index) { 0 }),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                        color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (over) return@Column

            Text(
                text = stringResource(R.string.dice_sb_round, roundNumber, rounds),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.dice_bunco_turn, players.getOrElse(round.current) { "" }),
                style = MaterialTheme.typography.titleMedium
            )

            // Les dés ont fini de tourner : le total, les clapets possibles et la fin du tour peuvent s'afficher.
            val settled = round.turn.hasRolled && !animation.rolling
            Flaps(
                round = round,
                enabled = canAct && settled && !round.turnOver,
                onToggle = {
                    round.toggle(it)
                    onChanged()
                }
            )

            if (!round.turn.hasRolled && !round.boxShut) {
                Text(stringResource(R.string.dice_sb_hint), style = MaterialTheme.typography.bodyMedium)
            }
            if (round.canChooseOneDie || round.oneDie) {
                OneDieChoice(
                    oneDie = round.oneDie,
                    enabled = canAct && round.canChooseOneDie,
                    onChange = {
                        round.setOneDie(it)
                        onChanged()
                    }
                )
            }

            DiceTray(
                turn = round.turn,
                animation = animation,
                rollEnabled = round.canRoll && canAct,
                interactive = canAct,
                showNewTurn = false,
                // Un seul dé : le second est estompé, il ne compte pas.
                locked = if (round.oneDie) setOf(1) else emptySet(),
                showHoldHint = false,
                onRoll = {
                    round.roll()
                    onChanged()
                },
                onToggleHold = {},
                onNewTurn = {}
            )

            if (settled) {
                ResultPart(
                    round = round,
                    canAct = canAct,
                    onConfirm = {
                        round.confirm()
                        onChanged()
                    },
                    onEndTurn = {
                        round.endTurn(viewModel)
                        onChanged()
                    }
                )
            } else if (round.boxShut) {
                // La boîte vient d'être fermée avec la dernière combinaison : il n'y a plus de dés à attendre.
                EndPart(round = round, canAct = canAct, onEndTurn = {
                    round.endTurn(viewModel)
                    onChanged()
                })
            }
        }
    }
}

/** Les neuf clapets : ouverts (bois clair), choisis (cerclés) ou rabattus (sombres et barrés). */
@Composable
private fun Flaps(round: ShutBoxRound, enabled: Boolean, onToggle: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (flap in 1..ShutBoxScoring.FLAPS) {
            val bit = ShutBoxScoring.bit(flap)
            val isOpen = round.openMask and bit != 0
            val isSelected = round.selected and bit != 0
            val description = stringResource(
                when {
                    !isOpen -> R.string.dice_sb_flap_shut
                    isSelected -> R.string.dice_sb_flap_selected
                    else -> R.string.dice_sb_flap_open
                },
                flap
            )
            val shape = RoundedCornerShape(8.dp)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(0.7f)
                    .clip(shape)
                    .background(if (isOpen) Color(0xFFD7B98E) else Color(0xFF424242), shape)
                    .border(
                        width = if (isSelected) 4.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                        shape = shape
                    )
                    .then(
                        if (enabled && isOpen) Modifier.clickable(role = Role.Button) { onToggle(flap) } else Modifier
                    )
                    .semantics { contentDescription = description }
            ) {
                Text(
                    text = flap.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isOpen) Color(0xFF3E2723) else Color(0xFF9E9E9E),
                    textDecoration = if (isOpen) TextDecoration.None else TextDecoration.LineThrough
                )
            }
        }
    }
}

/** Case à cocher « Lancer un seul dé », proposée quand les clapets 7, 8 et 9 sont rabattus. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OneDieChoice(oneDie: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(
        selected = oneDie,
        onClick = { onChange(!oneDie) },
        enabled = enabled,
        label = { Text(stringResource(R.string.dice_sb_one_die)) }
    )
}

/** Le total des dés, puis « Rabattre » tant qu'une combinaison est possible, ou la fin du tour. */
@Composable
private fun ResultPart(round: ShutBoxRound, canAct: Boolean, onConfirm: () -> Unit, onEndTurn: () -> Unit) {
    val total = round.total ?: return
    Text(
        text = stringResource(R.string.dice_sb_total, total),
        style = MaterialTheme.typography.titleMedium
    )
    if (round.turnOver) {
        EndPart(round = round, canAct = canAct, onEndTurn = onEndTurn)
        return
    }
    val sum = round.selectionSum
    Text(
        text = if (round.selected == 0) {
            stringResource(R.string.dice_sb_pick, total)
        } else {
            stringResource(R.string.dice_sb_selection, ShutBoxScoring.flaps(round.selected).joinToString(" + "), sum)
        },
        style = MaterialTheme.typography.bodyMedium
    )
    Button(onClick = onConfirm, enabled = canAct && round.canConfirm, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.dice_sb_close))
    }
}

/** Le tour est fini (boîte fermée ou aucune combinaison possible) : ce qu'il rapporte et « Terminer le tour ». */
@Composable
private fun EndPart(round: ShutBoxRound, canAct: Boolean, onEndTurn: () -> Unit) {
    Text(
        text = if (round.boxShut) stringResource(R.string.dice_sb_shut) else stringResource(R.string.dice_sb_stuck, round.score),
        style = MaterialTheme.typography.titleMedium,
        color = if (round.boxShut) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    )
    Button(onClick = onEndTurn, enabled = canAct, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.dice_sb_end_turn, round.score))
    }
}
