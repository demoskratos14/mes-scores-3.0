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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Le Bunco jouable, tour par tour (voir [BuncoRound]) : chaque joueur lance les 3 dés à son tour et relance tant
 * qu'il marque ; la manche s'arrête quand un joueur atteint 21 points, et les points de chacun sont alors
 * inscrits tout seuls dans le tableau des scores.
 *
 * [canAct] : ce téléphone joue pour le joueur dont c'est le tour. [onChanged] : la partie a changé, à partager aux
 * autres téléphones.
 */
@Composable
internal fun BuncoSection(
    viewModel: ScoreViewModel,
    round: BuncoRound,
    canAct: Boolean,
    onChanged: () -> Unit
) {
    // Partie déjà terminée (reprise d'une partie finie) : le bandeau de fin de partie suffit.
    if (round.phase == BuncoPhase.PLAY && viewModel.isGameOver()) return

    val players = viewModel.players
    // Les dés tournent encore : ce qu'ils rapportent (points, fin de manche) attend qu'ils soient arrêtés.
    val animation = rememberDiceRollAnimation(round.turn)

    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.dice_bunco_round, round.round, BuncoScoring.ROUNDS),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            // Le dernier lancer n'est pas compté tant que les dés tournent.
            val hidden = if (animation.rolling) round.lastRoll?.points ?: 0 else 0
            val shown = round.points.mapIndexed { index, value -> if (index == round.current) value - hidden else value }
            // Total de la partie : les manches déjà inscrites au tableau, plus la manche en cours tant qu'elle n'y est pas.
            val written = viewModel.totals
            // Un lancer qui finit la manche est déjà inscrit : il reste caché tant que les dés tournent.
            val totals = players.indices.map { index ->
                if (round.phase == BuncoPhase.PLAY) {
                    written.getOrElse(index) { 0 } + shown.getOrElse(index) { 0 }
                } else {
                    written.getOrElse(index) { 0 } - if (index == round.current) hidden else 0
                }
            }
            RoundPoints(
                players = players,
                points = shown,
                totals = totals,
                current = if (round.phase == BuncoPhase.PLAY) round.current else -1
            )
            if (round.phase == BuncoPhase.DONE && !animation.rolling) {
                DonePart(players = players, round = round, canAct = canAct, onNext = {
                    round.nextRound()
                    onChanged()
                })
            } else {
                PlayPart(
                    players = players,
                    round = round,
                    animation = animation,
                    canAct = canAct,
                    onRoll = {
                        // Fin de manche : les points de chacun sont inscrits une seule fois, par le téléphone qui a lancé.
                        if (round.roll()) round.writeTo(viewModel)
                        onChanged()
                    },
                    onEndTurn = {
                        round.endTurn()
                        onChanged()
                    }
                )
            }
        }
    }
}

/** Les points de chacun dans la manche, avec l'objectif ; le joueur dont c'est le tour ([current]) est en gras. */
@Composable
private fun RoundPoints(players: List<String>, points: List<Int>, totals: List<Int>, current: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(R.string.dice_bunco_scores_title, BuncoScoring.TARGET),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        players.forEachIndexed { index, name ->
            val mine = index == current
            Text(
                text = stringResource(R.string.dice_bunco_score_line_total, name, points.getOrElse(index) { 0 }, totals.getOrElse(index) { 0 }),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** Le tour d'un joueur : ses dés, ce que rapporte chaque lancer, et « Joueur suivant » quand un lancer ne rapporte rien. */
@Composable
private fun PlayPart(
    players: List<String>,
    round: BuncoRound,
    animation: DiceRollAnimation,
    canAct: Boolean,
    onRoll: () -> Unit,
    onEndTurn: () -> Unit
) {
    val turn = round.turn
    val name = players.getOrElse(round.current) { "" }
    Text(
        text = stringResource(R.string.dice_bunco_turn, name),
        style = MaterialTheme.typography.titleMedium
    )
    if (!turn.hasRolled) {
        Text(
            text = stringResource(R.string.dice_bunco_hint, round.round),
            style = MaterialTheme.typography.bodyMedium
        )
    }

    DiceTray(
        turn = turn,
        animation = animation,
        rollEnabled = round.canRoll && canAct,
        interactive = canAct,
        showNewTurn = false,
        onRoll = onRoll,
        onToggleHold = {},
        onNewTurn = {}
    )

    // Les dés tournent encore : ce qu'ils rapportent attend qu'ils soient arrêtés.
    val roll = round.lastRoll
    if (roll != null && !animation.rolling) {
        Text(
            text = rollText(roll, round.round, turn.values),
            style = MaterialTheme.typography.titleMedium
        )
        if (round.turnOver) {
            Button(onClick = onEndTurn, enabled = canAct, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.dice_bunco_next_player))
            }
        } else if (round.phase == BuncoPhase.PLAY) {
            Text(stringResource(R.string.dice_bunco_roll_again), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Ce que rapporte un lancer, en toutes lettres. */
@Composable
private fun rollText(roll: BuncoRoll, round: Int, dice: List<Int>): String = when (roll.kind) {
    BuncoKind.BUNCO -> stringResource(R.string.dice_bunco_result_bunco, round)
    BuncoKind.TRIPLE -> stringResource(R.string.dice_bunco_result_triple, dice.firstOrNull() ?: 0, roll.points)
    BuncoKind.MATCHES -> pluralStringResource(R.plurals.dice_bunco_result_match, roll.points, roll.points, round)
    BuncoKind.NOTHING -> stringResource(R.string.dice_bunco_result_none, round)
}

/** La manche terminée : qui la gagne, puis la manche suivante (ou la fin de la partie après la sixième). */
@Composable
private fun DonePart(players: List<String>, round: BuncoRound, canAct: Boolean, onNext: () -> Unit) {
    val name = players.getOrElse(round.winner) { "" }
    Text(stringResource(R.string.dice_bunco_round_over), style = MaterialTheme.typography.titleMedium)
    Text(
        text = if (round.lastRoll?.kind == BuncoKind.BUNCO) {
            stringResource(R.string.dice_bunco_winner_bunco, name)
        } else {
            stringResource(R.string.dice_bunco_winner, name, BuncoScoring.TARGET)
        },
        style = MaterialTheme.typography.bodyMedium
    )
    if (round.isLastRound) {
        // Le bandeau de fin de partie (avec le vainqueur) s'affiche en haut de l'écran.
        Text(stringResource(R.string.dice_bunco_game_over), style = MaterialTheme.typography.bodyMedium)
    } else {
        Button(onClick = onNext, enabled = canAct, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.dice_bunco_next_round, round.round + 1))
        }
    }
}
