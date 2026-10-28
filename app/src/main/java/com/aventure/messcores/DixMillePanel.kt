package com.aventure.messcores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Le Dix mille jouable, tour par tour (voir [DixMilleRound]) : chaque joueur lance les 6 dés, choisit ceux qui rapportent
 * des points, puis relance les autres ou garde ses points. L'appli compte les points, repère le Farkle, applique la règle
 * d'ouverture, passe la main et inscrit les points gardés dans le tableau des scores (qu'on n'a pas besoin d'ouvrir :
 * les scores sont sur cette page). La partie s'arrête à 10 000 points, après un dernier tour pour les autres.
 *
 * [canAct] : ce téléphone joue pour le joueur dont c'est le tour. [onChanged] : la partie a changé, à partager aux
 * autres téléphones.
 */
@Composable
internal fun DixMilleSection(
    viewModel: ScoreViewModel,
    round: DixMilleRound,
    canAct: Boolean,
    onChanged: () -> Unit
) {
    val players = viewModel.players
    // Les dés tournent encore : ce qu'ils rapportent attend qu'ils soient arrêtés.
    val animation = rememberDiceRollAnimation(round.turn)
    val over = viewModel.isGameOver()
    val totals = viewModel.totals
    val opened = totals.getOrElse(round.current) { 0 } > 0
    val target = viewModel.gameRules.endCondition.scoreThreshold ?: DixMilleScoring.TARGET
    val leader = totals.indices.maxByOrNull { totals[it] }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.dice_pig_scores_title, target),
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

            // Quelqu'un a atteint l'objectif : les joueurs qui n'ont pas encore joué dans la manche ont un dernier tour.
            if (leader != null && totals[leader] >= target) {
                Text(
                    text = stringResource(R.string.dice_dm_last_turn, players.getOrElse(leader) { "" }, target),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = stringResource(R.string.dice_bunco_turn, players.getOrElse(round.current) { "" }),
                style = MaterialTheme.typography.titleMedium
            )
            if (!round.turn.hasRolled) {
                Text(stringResource(R.string.dice_dm_hint, DixMilleScoring.OPENING), style = MaterialTheme.typography.bodyMedium)
            }

            DiceTray(
                turn = round.turn,
                animation = animation,
                rollEnabled = round.canRoll && canAct,
                interactive = canAct && !round.farkle,
                showNewTurn = false,
                locked = round.locked,
                showHoldHint = false,
                onRoll = {
                    round.roll()
                    onChanged()
                },
                onToggleHold = {
                    round.toggle(it)
                    onChanged()
                },
                onNewTurn = {}
            )

            // Les dés tournent encore : ce qu'ils rapportent attend qu'ils soient arrêtés.
            if (round.turn.hasRolled && !animation.rolling) {
                if (round.hotDice) {
                    Text(stringResource(R.string.dice_dm_hot), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                if (round.farkle) {
                    Text(
                        text = stringResource(R.string.dice_dm_farkle),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(
                        onClick = {
                            round.pass(viewModel)
                            onChanged()
                        },
                        enabled = canAct,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.dice_pig_pass))
                    }
                } else {
                    ChoicePart(
                        round = round,
                        opened = opened,
                        canAct = canAct,
                        onKeep = {
                            round.keep(viewModel, opened)
                            onChanged()
                        }
                    )
                }
            }
        }
    }
}

/** Les points du tour, ceux des dés choisis, et « Garder » quand on peut s'arrêter. */
@Composable
private fun ChoicePart(round: DixMilleRound, opened: Boolean, canAct: Boolean, onKeep: () -> Unit) {
    if (round.turnPoints > 0) {
        Text(stringResource(R.string.dice_dm_turn_points, round.turnPoints), style = MaterialTheme.typography.bodyMedium)
    }
    val points = round.selectionPoints
    when {
        points != null -> {
            Text(
                text = stringResource(R.string.dice_dm_selection, points, round.pending),
                style = MaterialTheme.typography.titleMedium
            )
            if (!opened && round.pending < DixMilleScoring.OPENING) {
                Text(stringResource(R.string.dice_dm_open_needed, DixMilleScoring.OPENING), style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onKeep, enabled = canAct && round.canKeep(opened), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.dice_pig_keep, round.pending))
            }
        }
        round.selected.isNotEmpty() -> Text(stringResource(R.string.dice_dm_invalid), style = MaterialTheme.typography.bodyMedium)
        else -> Text(stringResource(R.string.dice_dm_select_prompt), style = MaterialTheme.typography.bodyMedium)
    }
}
