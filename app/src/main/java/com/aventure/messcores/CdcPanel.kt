package com.aventure.messcores

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Le Cul de chouette jouable, tour par tour (voir [CdcRound]) : chaque joueur lance les trois dés à son tour, la
 * combinaison rapporte ou fait perdre des points, et le score de chacun se calcule seul dans le tableau des scores
 * (qu'on n'a pas besoin d'ouvrir : les scores sont sur cette page). La partie s'arrête à 343 points.
 *
 * [canAct] : ce téléphone joue pour le joueur dont c'est le tour. [onChanged] : la partie a changé, à partager aux
 * autres téléphones.
 */
@Composable
internal fun CdcSection(
    viewModel: ScoreViewModel,
    round: CdcRound,
    canAct: Boolean,
    onChanged: () -> Unit
) {
    val players = viewModel.players
    // Les dés tournent encore : ce qu'ils rapportent (points, fin de partie) attend qu'ils soient arrêtés.
    val animation = rememberDiceRollAnimation(round.turn)
    val over = viewModel.isGameOver() && !animation.rolling
    val roll = round.lastRoll

    // Un lancer réglé tout de suite est déjà inscrit au tableau : il reste caché tant que les dés tournent.
    val hidden = if (animation.rolling && round.phase == CdcPhase.PLAY && !round.needsClaim) roll?.points ?: 0 else 0
    val totals = viewModel.totals.mapIndexed { index, total -> if (index == round.current) total - hidden else total }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Scores(
                players = players,
                totals = totals,
                target = viewModel.gameRules.endCondition.scoreThreshold,
                current = if (over) -1 else round.current
            )
            if (!over) {
                Text(
                    text = stringResource(R.string.dice_bunco_turn, players.getOrElse(round.current) { "" }),
                    style = MaterialTheme.typography.titleMedium
                )
                if (!round.turn.hasRolled) {
                    Text(stringResource(R.string.dice_cdc_hint), style = MaterialTheme.typography.bodyMedium)
                }
            }
            DiceTray(
                turn = round.turn,
                animation = animation,
                rollEnabled = round.canRoll && canAct && !over,
                interactive = canAct,
                showNewTurn = false,
                onRoll = {
                    // Un lancer réglé tout de suite est inscrit une seule fois, par le téléphone qui a lancé.
                    if (round.roll()) round.settle(viewModel)
                    onChanged()
                },
                onToggleHold = {},
                onNewTurn = {}
            )

            // Les dés tournent encore : ce qu'ils rapportent attend qu'ils soient arrêtés.
            if (roll != null && !animation.rolling) {
                Text(text = rollText(roll), style = MaterialTheme.typography.titleMedium)
                if (round.phase == CdcPhase.CLAIM) {
                    ClaimPart(
                        players = players,
                        suite = roll.kind == CdcKind.SUITE,
                        canAct = canAct,
                        onClaim = { claimant ->
                            round.settle(viewModel, claimant)
                            onChanged()
                        }
                    )
                } else if (!over) {
                    Button(
                        onClick = {
                            round.endTurn()
                            onChanged()
                        },
                        enabled = canAct && round.turnOver,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.dice_bunco_next_player))
                    }
                }
            }
        }
    }
}

/** Le score de chacun, avec l'objectif ; le joueur dont c'est le tour ([current]) est en gras. */
@Composable
private fun Scores(players: List<String>, totals: List<Int>, target: Int?, current: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = if (target != null) {
                stringResource(R.string.dice_pig_scores_title, target)
            } else {
                stringResource(R.string.dice_pig_scores_title_open)
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        players.forEachIndexed { index, name ->
            val mine = index == current
            Text(
                text = stringResource(R.string.dice_pig_score_line, name, totals.getOrElse(index) { 0 }),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** Chouette-velute ou suite : qui a crié le premier (il marque) ou qui a réagi en dernier (il perd) ; ou personne. */
@Composable
private fun ClaimPart(players: List<String>, suite: Boolean, canAct: Boolean, onClaim: (Int?) -> Unit) {
    Text(
        text = stringResource(if (suite) R.string.dice_cdc_claim_suite else R.string.dice_cdc_claim_velute),
        style = MaterialTheme.typography.bodyMedium
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        players.forEachIndexed { index, name ->
            Button(onClick = { onClaim(index) }, enabled = canAct) { Text(name) }
        }
        OutlinedButton(onClick = { onClaim(null) }, enabled = canAct) {
            Text(stringResource(R.string.dice_cdc_nobody))
        }
    }
}

/** Ce que rapporte un lancer, en toutes lettres. */
@Composable
private fun rollText(roll: CdcRoll): String = when (roll.kind) {
    CdcKind.CUL_DE_CHOUETTE -> stringResource(R.string.dice_cdc_result_cul, roll.value, roll.points)
    CdcKind.CHOUETTE_VELUTE -> stringResource(R.string.dice_cdc_result_chouette_velute, roll.points)
    CdcKind.VELUTE -> stringResource(R.string.dice_cdc_result_velute, roll.value, roll.points)
    CdcKind.CHOUETTE -> stringResource(R.string.dice_cdc_result_chouette, roll.value, roll.points)
    CdcKind.SUITE -> stringResource(R.string.dice_cdc_result_suite, roll.points)
    CdcKind.NEANT -> stringResource(R.string.dice_cdc_result_neant)
}
