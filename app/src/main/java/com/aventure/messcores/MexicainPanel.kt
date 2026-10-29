package com.aventure.messcores

import androidx.annotation.StringRes
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Le Mexicain jouable, tour par tour (voir [Round421], qui sert aussi au 421) : manche de départ (sans vie perdue)
 * pour désigner le premier joueur, puis chaque joueur lance ses deux dés à son tour, le premier fixant le nombre de
 * lancers de tous les autres. À la fin de la manche, le plus mauvais jeu perd une vie, comptée automatiquement ;
 * un joueur sans vie est éliminé, et le dernier joueur en jeu gagne.
 *
 * Le compteur de chaque joueur compte ses vies perdues ([DiceMexicain.LIVES] au plus).
 *
 * [canAct] : ce téléphone joue pour le joueur dont c'est le tour. [onChanged] : la manche a changé, à partager aux
 * autres téléphones. [onBack] : retour aux scores, à la fin de la partie.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MexicainSection(
    viewModel: ScoreViewModel,
    round: Round421,
    canAct: Boolean,
    onChanged: () -> Unit,
    onBack: () -> Unit,
    @StringRes backLabel: Int = R.string.dice_see_scores
) {
    val players = viewModel.players
    val lives = DiceMexicain.livesLeft(viewModel.totals)

    // La vie perdue est comptée une seule fois, par le téléphone qui a fait jouer le dernier joueur.
    fun settleRound() {
        val result = round.result() ?: return
        if (round.opening || result.draw) {
            // Manche de départ, ou égalité parfaite : personne ne perd de vie.
            round.recordTokens(0, true)
            return
        }
        viewModel.incrementCounter(result.loser, 1)
        round.recordTokens(1, true)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (round.phase != Phase421.DRAW && round.opening) {
                Text(
                    text = stringResource(R.string.dice_mex_opening_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Lives(players = players, lives = lives, current = if (round.phase == Phase421.PLAY) round.currentPlayer else -1)
            when (round.phase) {
                Phase421.DRAW -> DrawPart(viewModel, round, canAct, onChanged)
                Phase421.PLAY -> PlayPart(round, players, canAct, onChanged) {
                    if (round.keepHand()) settleRound()
                    onChanged()
                }
                Phase421.DONE -> DonePart(
                    viewModel = viewModel,
                    round = round,
                    canAct = canAct,
                    onReplay = {
                        round.result()?.let { result ->
                            if (round.tokens > 0) viewModel.incrementCounter(result.loser, -round.tokens)
                        }
                        round.replayRound()
                        onChanged()
                    },
                    onNext = {
                        val active = DiceMexicain.activePlayers(viewModel.totals)
                        if (round.opening) round.finishOpening(active) else round.nextRound(active)
                        onChanged()
                    },
                    onBack = onBack,
                    backLabel = backLabel
                )
            }
        }
    }
}

/** Le nom d'un jeu : Mexico, double de 4, ou les deux dés (le plus fort en premier). */
@Composable
private fun handName(dice: List<Int>): String = when {
    DiceMexicain.isMexico(dice) -> stringResource(R.string.dice_mex_name_mexico)
    DiceMexicain.isDouble(dice) -> stringResource(R.string.dice_mex_name_double, DiceMexicain.doubleValue(dice))
    dice.size == 2 -> stringResource(R.string.dice_mex_name_plain, maxOf(dice[0], dice[1]), minOf(dice[0], dice[1]))
    else -> ""
}

/** Les vies restantes de chacun, toujours visibles pendant les dés ; le joueur dont c'est le tour ([current]) est en gras. */
@Composable
private fun Lives(players: List<String>, lives: List<Int>, current: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(R.string.dice_mex_lives_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        players.forEachIndexed { index, name ->
            val mine = index == current
            Text(
                text = stringResource(R.string.dice_421_holding, name, lives.getOrElse(index) { 0 }),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** Avant la partie : on lance la manche de départ, qui désigne le premier joueur, ou on le choisit directement. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawPart(viewModel: ScoreViewModel, round: Round421, canAct: Boolean, onChanged: () -> Unit) {
    val players = viewModel.players
    Text(stringResource(R.string.dice_421_draw_title), style = MaterialTheme.typography.titleMedium)
    if (round.starter < 0) {
        Text(stringResource(R.string.dice_mex_draw_intro), style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = {
                round.startOpening(DiceMexicain.activePlayers(viewModel.totals))
                onChanged()
            },
            enabled = canAct,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.dice_421_draw_roll))
        }
        Text(stringResource(R.string.dice_421_draw_pick), style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            players.forEachIndexed { index, name ->
                FilterChip(
                    selected = false,
                    enabled = canAct,
                    onClick = {
                        round.chooseStarter(index)
                        onChanged()
                    },
                    label = { Text(name) }
                )
            }
        }
    } else {
        Text(
            text = stringResource(R.string.dice_421_draw_result, players.getOrElse(round.starter) { "" }),
            style = MaterialTheme.typography.titleMedium
        )
        Button(
            onClick = {
                round.begin(DiceMexicain.activePlayers(viewModel.totals))
                onChanged()
            },
            enabled = canAct,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.dice_421_begin))
        }
    }
}

/** Le tour d'un joueur : ses dés, le nombre de lancers de la manche, et « Garder ce jeu ». */
@Composable
private fun PlayPart(
    round: Round421,
    players: List<String>,
    canAct: Boolean,
    onChanged: () -> Unit,
    onKeep: () -> Unit
) {
    val turn = round.turn
    val current = round.currentPlayer
    val name = players.getOrElse(current) { "" }
    Text(
        text = stringResource(R.string.dice_421_turn, name, round.position + 1, round.order.size),
        style = MaterialTheme.typography.titleMedium
    )
    if (round.isStarterTurn) {
        Text(stringResource(R.string.dice_mex_starter_hint, name), style = MaterialTheme.typography.bodyMedium)
    } else {
        Text(
            text = stringResource(R.string.dice_421_limit, round.limit, players.getOrElse(round.order.first()) { "" }),
            style = MaterialTheme.typography.bodyMedium
        )
    }

    val animation = rememberDiceRollAnimation(turn)
    DiceTray(
        turn = turn,
        animation = animation,
        rollEnabled = turn.canRoll && canAct,
        interactive = canAct,
        showNewTurn = false,
        onRoll = {
            turn.roll()
            onChanged()
        },
        onToggleHold = {
            turn.toggleHold(it)
            onChanged()
        },
        onNewTurn = {}
    )

    // Les dés tournent encore : le nom du jeu attend qu'ils soient arrêtés.
    if (turn.hasRolled && !animation.rolling) {
        Text(text = handName(turn.values), style = MaterialTheme.typography.titleMedium)
        if ((turn.rollsLeft ?: 0) == 0) {
            Text(stringResource(R.string.dice_421_no_roll_left), style = MaterialTheme.typography.bodyMedium)
        }
    }
    Button(onClick = onKeep, enabled = canAct && round.canKeep && !animation.rolling, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.dice_421_keep))
    }

    HandsList(round, players)
}

/** La manche terminée : les jeux de chacun, qui perd une vie, puis la manche suivante ou la fin de partie. */
@Composable
private fun DonePart(
    viewModel: ScoreViewModel,
    round: Round421,
    canAct: Boolean,
    onReplay: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    @StringRes backLabel: Int
) {
    val players = viewModel.players
    val result = round.result()
    fun nameOf(player: Int): String = players.getOrElse(player) { "" }

    Text(stringResource(R.string.dice_421_round_over), style = MaterialTheme.typography.titleMedium)
    HandsList(round, players)
    if (result != null && round.opening) {
        // Manche de départ : personne ne perd de vie, le meilleur jeu commence la partie.
        Text(
            text = if (result.draw) {
                stringResource(R.string.dice_421_opening_draw)
            } else {
                stringResource(R.string.dice_421_opening_result, nameOf(result.winner))
            },
            style = MaterialTheme.typography.titleMedium
        )
        Button(onClick = onNext, enabled = canAct, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (result.draw) R.string.dice_421_replay_round else R.string.dice_421_opening_begin))
        }
        return
    }
    if (result != null && result.draw) {
        Text(
            text = stringResource(R.string.dice_mex_result_draw),
            style = MaterialTheme.typography.titleMedium
        )
    } else if (result != null) {
        Text(
            text = stringResource(R.string.dice_mex_result_best, nameOf(result.winner), handName(round.hands[result.winner])),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(R.string.dice_mex_result_loser, nameOf(result.loser)),
            style = MaterialTheme.typography.titleMedium
        )
        if (result.loser in players.indices && viewModel.totals.getOrElse(result.loser) { 0 } >= DiceMexicain.LIVES) {
            Text(
                text = stringResource(R.string.dice_mex_eliminated, nameOf(result.loser)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    val winner = DiceMexicain.winner(viewModel.totals)
    if (winner != null) {
        Text(
            text = stringResource(R.string.dice_mex_game_over, nameOf(winner)),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(backLabel))
        }
    } else {
        Button(onClick = onNext, enabled = canAct, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.dice_421_next_round))
        }
    }
    OutlinedButton(onClick = onReplay, enabled = canAct, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.dice_421_replay_round))
    }
}

/** Les jeux gardés par les joueurs de la manche, dans l'ordre où ils jouent. */
@Composable
private fun HandsList(round: Round421, players: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.dice_421_hands_title), style = MaterialTheme.typography.labelLarge)
        round.order.forEach { player ->
            val hand = round.hands.getOrElse(player) { emptyList() }
            val name = players.getOrElse(player) { "" }
            Text(
                text = if (hand.isEmpty()) {
                    stringResource(R.string.dice_421_hand_waiting, name)
                } else {
                    stringResource(
                        R.string.dice_421_hand,
                        name,
                        hand.joinToString(" "),
                        handName(hand),
                        round.rolls.getOrElse(player) { 0 }
                    )
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
