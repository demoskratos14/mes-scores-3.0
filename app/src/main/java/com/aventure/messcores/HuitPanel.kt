package com.aventure.messcores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * L'écran du Huit américain jouable : il n'y a pas de feuille de scores derrière, tout s'affiche ici (voir [HuitSection]).
 * « Retour » et la fin de partie quittent la partie ([onBack]).
 */
@Composable
internal fun HuitScreen(viewModel: ScoreViewModel, onBack: () -> Unit) {
    if (viewModel.gameRules.id != HUIT_ID) {
        // Aucune partie de Huit américain ouverte (partie fermée entre-temps) : retour.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val round = remember(viewModel, viewModel.players.size) { viewModel.huitRound() }
    // Un autre joueur, une nouvelle manche ou la main qu'on vient de dévoiler : on remonte en haut de la page.
    CardGameScreen(
        viewModel = viewModel,
        onBack = onBack,
        scrollKeys = listOf(round.current, round.revealed, round.roundOver, round.roundNumber)
    ) {
        HuitSection(viewModel = viewModel, round = round, onLeave = onBack)
    }
}

/**
 * Le Huit américain tour par tour (voir [HuitRound]) : l'appli distribue, vérifie chaque carte posée, fait piocher, compte les
 * points de la manche et les inscrit au tableau des scores (qu'on n'a pas besoin d'ouvrir : les scores sont sur cette page).
 * Le téléphone se passe de joueur en joueur : la main n'apparaît qu'après « Voir ma main ».
 */
@Composable
internal fun HuitSection(viewModel: ScoreViewModel, round: HuitRound, onLeave: () -> Unit) {
    val players = viewModel.players
    val labels = rememberFrenchLabels()
    val over = viewModel.isGameOver()

    // Une manche qui vient de finir est inscrite tout de suite : quitter ensuite ne la fait pas perdre.
    fun settleIfOver() {
        if (round.roundOver) round.settle(viewModel)
    }

    ScoresCard(viewModel = viewModel, current = if (over || round.roundOver) -1 else round.current)

    if (over) {
        // La partie est finie (ou reprise déjà finie) : le vainqueur, les points de la dernière manche s'il y en a une, puis la sortie.
        GameOverBanner(players = players, winners = viewModel.winners())
        if (round.roundOver) HuitRoundResult(round = round, players = players, canContinue = false, onNext = {})
        Button(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.dice_leave_game))
        }
        return
    }

    Text(
        text = stringResource(R.string.huit_round, round.roundNumber),
        style = MaterialTheme.typography.labelLarge,
        color = Color.White
    )

    TableCard(round = round, labels = labels, players = players)

    if (round.roundOver) {
        HuitRoundResult(
            round = round,
            players = players,
            canContinue = round.settled,
            onNext = { round.nextRound() }
        )
        return
    }

    val name = players.getOrElse(round.current) { "" }
    if (!round.revealed) {
        // Le téléphone change de mains : la main reste cachée jusqu'à ce que son joueur la demande.
        PassPhoneCard(name = name, onReveal = { round.reveal() })
        return
    }

    HandCard(
        round = round,
        labels = labels,
        name = name,
        onPlay = { card, suit ->
            if (round.play(card, suit)) settleIfOver()
        },
        onDraw = { round.draw() },
        onPass = {
            if (round.pass()) settleIfOver()
        }
    )
}

/** La table : dernière carte posée, couleur demandée, pioche et nombre de cartes de chaque joueur (rien de secret). */
@Composable
private fun TableCard(round: HuitRound, labels: FrenchLabels, players: List<String>) {
    CardPanel {
        Text(
            text = stringResource(R.string.huit_top_card),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FrenchCardFace(card = round.top, labels = labels, playable = true, modifier = Modifier.width(64.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.huit_asked_suit, labels.suitLabel(round.askedSuit)),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (frenchSuitColor(round.askedSuit) == CardRed) CardRed else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.huit_stock, round.stock.size),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Text(
            text = stringResource(R.string.huit_hands_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        players.forEachIndexed { index, name ->
            Text(
                text = stringResource(R.string.dice_pig_score_line, name, round.hands.getOrNull(index)?.size ?: 0),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/** La main du joueur qui joue : ses cartes (les jouables sont touchables), le choix de la couleur après un 8, piocher ou passer. */
@Composable
private fun HandCard(
    round: HuitRound,
    labels: FrenchLabels,
    name: String,
    onPlay: (card: Int, suit: Int) -> Unit,
    onDraw: () -> Unit,
    onPass: () -> Unit
) {
    // Le 8 que le joueur vient de toucher et dont il doit encore choisir la couleur (-1 : aucun).
    var pendingEight by rememberSaveable(round.current, round.roundNumber) { mutableIntStateOf(-1) }
    val hand = round.currentHand.sortedWith(compareBy({ HuitScoring.suit(it) }, { HuitScoring.rank(it) }))
    val choosing = pendingEight in hand

    CardPanel(spacing = 10) {
        Text(
            text = stringResource(R.string.dice_bunco_turn, name),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.huit_hand_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CardRows(hand) { card, cardModifier ->
            val playable = round.isPlayable(card)
            FrenchCardFace(
                card = card,
                labels = labels,
                playable = playable,
                selected = card == pendingEight,
                modifier = cardModifier,
                onClick = if (playable) {
                    {
                        if (HuitScoring.rank(card) == HuitScoring.EIGHT && hand.size > 1) {
                            pendingEight = if (pendingEight == card) -1 else card
                        } else {
                            pendingEight = -1
                            onPlay(card, -1)
                        }
                    }
                } else {
                    null
                }
            )
        }

        when {
            choosing -> {
                Text(stringResource(R.string.huit_pick_suit), style = MaterialTheme.typography.bodyMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (suit in 0 until HuitScoring.SUITS) {
                        val description = labels.suitName(suit)
                        OutlinedButton(
                            onClick = {
                                val card = pendingEight
                                pendingEight = -1
                                onPlay(card, suit)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = description }
                        ) {
                            Text(
                                text = labels.symbol(suit),
                                style = MaterialTheme.typography.titleLarge,
                                color = if (frenchSuitColor(suit) == CardRed) CardRed else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                TextButton(onClick = { pendingEight = -1 }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
            round.hasPlayable -> Text(stringResource(R.string.huit_hint), style = MaterialTheme.typography.bodyMedium)
            round.canDraw -> {
                Text(stringResource(R.string.huit_no_play), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onDraw, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.huit_draw))
                }
            }
            round.mustPass -> {
                Text(stringResource(R.string.huit_must_pass), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onPass, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.huit_pass))
                }
            }
        }
    }
}

/** La fin de la manche : qui a gagné, les points de chacun (déjà inscrits au tableau) et la manche suivante. */
@Composable
private fun HuitRoundResult(round: HuitRound, players: List<String>, canContinue: Boolean, onNext: () -> Unit) {
    val winnerName = players.getOrElse(round.winner) { "" }
    RoundResultCard(
        title = stringResource(if (round.blocked) R.string.huit_round_blocked else R.string.huit_round_won, winnerName),
        players = players,
        points = round.roundPoints(),
        winner = round.winner,
        canContinue = canContinue,
        onNext = onNext
    )
}
