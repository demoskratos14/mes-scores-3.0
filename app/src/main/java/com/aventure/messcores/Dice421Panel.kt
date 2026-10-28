package com.aventure.messcores

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Le 421 jouable, tour par tour (voir [Round421]) : manche de départ (sans jetons) pour désigner le premier joueur,
 * puis chaque joueur lance ses dés à son tour, le premier fixant le nombre de lancers de tous les autres, et à la fin de la manche les jetons du perdant
 * sont comptés automatiquement (charge, puis décharge).
 *
 * [canAct] : ce téléphone joue pour le joueur dont c'est le tour. [onChanged] : la manche a changé, à partager aux
 * autres téléphones. [onBack] : retour aux scores, à la fin de la partie.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Round421Section(
    viewModel: ScoreViewModel,
    round: Round421,
    canAct: Boolean,
    onChanged: () -> Unit,
    onBack: () -> Unit,
    @StringRes backLabel: Int = R.string.dice_see_scores
) {
    val players = viewModel.players
    val holdings = viewModel.totals
    val pot = Dice421.pot(holdings)

    // Les jetons du perdant sont comptés une seule fois, par le téléphone qui a fait jouer le dernier joueur.
    fun settleRound() {
        val result = round.result() ?: return
        if (round.opening || result.draw) {
            // Manche de départ, ou égalité parfaite : aucun jeton ne bouge.
            round.recordTokens(0, true)
            return
        }
        val settlement = Dice421.settle(result.points, result.winner, viewModel.totals)
        if (settlement.tokens > 0) {
            viewModel.incrementCounter(result.loser, settlement.tokens)
            if (!settlement.fromPot) viewModel.incrementCounter(result.winner, -settlement.tokens)
        }
        round.recordTokens(settlement.tokens, settlement.fromPot)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (round.phase != Phase421.DRAW) {
                Text(
                    text = when {
                        round.opening -> stringResource(R.string.dice_421_opening_title)
                        pot > 0 -> stringResource(R.string.dice_421_pot, pot)
                        else -> stringResource(R.string.dice_421_discharge)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Holdings(players = players, holdings = holdings, current = if (round.phase == Phase421.PLAY) round.currentPlayer else -1)
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
                            if (round.tokens > 0) {
                                viewModel.incrementCounter(result.loser, -round.tokens)
                                if (!round.tokensFromPot) viewModel.incrementCounter(result.winner, round.tokens)
                            }
                        }
                        round.replayRound()
                        onChanged()
                    },
                    onNext = {
                        val active = Dice421.activePlayers(viewModel.totals)
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

/** Les jetons de chacun, toujours visibles pendant les dés ; le joueur dont c'est le tour ([current]) est en gras. */
@Composable
private fun Holdings(players: List<String>, holdings: List<Int>, current: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(R.string.dice_421_holdings_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        players.forEachIndexed { index, name ->
            val mine = index == current
            Text(
                text = stringResource(R.string.dice_421_holding, name, holdings.getOrElse(index) { 0 }),
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
        Text(stringResource(R.string.dice_421_draw_intro), style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = {
                round.startOpening(Dice421.activePlayers(viewModel.totals))
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
                round.begin(Dice421.activePlayers(viewModel.totals))
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
        Text(pluralStringResource(R.plurals.dice_421_starter_hint, round.maxRolls, name, round.maxRolls), style = MaterialTheme.typography.bodyMedium)
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

    // Les dés tournent encore : la combinaison attend qu'ils soient arrêtés.
    if (turn.hasRolled && !animation.rolling) {
        val combo = Dice421.combo(turn.values)
        Text(
            text = pluralStringResource(R.plurals.dice_combo_tokens, combo.points, stringResource(combo.label), combo.points),
            style = MaterialTheme.typography.titleMedium
        )
        if ((turn.rollsLeft ?: 0) == 0) {
            Text(stringResource(R.string.dice_421_no_roll_left), style = MaterialTheme.typography.bodyMedium)
        }
    }
    Button(onClick = onKeep, enabled = canAct && round.canKeep && !animation.rolling, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.dice_421_keep))
    }

    HandsList(round, players)
}

/** La manche terminée : les jeux de chacun, qui perd et les jetons pris, puis la manche suivante ou la fin de partie. */
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
        // Manche de départ : pas de jetons, le meilleur jeu commence la partie.
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
            text = stringResource(R.string.dice_421_result_draw),
            style = MaterialTheme.typography.titleMedium
        )
    } else if (result != null) {
        val best = Dice421.combo(round.hands[result.winner])
        Text(
            text = stringResource(R.string.dice_421_result_best, nameOf(result.winner), stringResource(best.label), result.points),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = if (round.tokensFromPot) {
                stringResource(R.string.dice_421_result_pot, nameOf(result.loser), round.tokens)
            } else {
                stringResource(R.string.dice_421_result_discharge, nameOf(result.loser), round.tokens, nameOf(result.winner))
            },
            style = MaterialTheme.typography.titleMedium
        )
    }

    val gameLoser = Dice421.gameLoser(viewModel.totals)
    if (gameLoser != null) {
        Text(
            text = stringResource(R.string.dice_421_game_over, nameOf(gameLoser)),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
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
                    val combo = Dice421.combo(hand)
                    stringResource(
                        R.string.dice_421_hand,
                        name,
                        hand.joinToString(" "),
                        pluralStringResource(R.plurals.dice_combo_tokens, combo.points, stringResource(combo.label), combo.points),
                        round.rolls.getOrElse(player) { 0 }
                    )
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
