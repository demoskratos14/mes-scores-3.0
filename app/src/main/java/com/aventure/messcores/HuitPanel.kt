package com.aventure.messcores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    // Le texte (ressource) refusant la pioche touchée ; effacé dès que la main ou le tour change.
    var stockError by remember(round.current, round.roundNumber, round.currentHand.size) { mutableStateOf<Int?>(null) }

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

    // La pioche est à côté de la dernière carte posée : touchable seulement quand la main est dévoilée.
    TableCard(
        round = round,
        labels = labels,
        players = players,
        stockError = stockError,
        onStockClick = if (round.revealed && !round.roundOver) {
            {
                stockError = when {
                    round.hasPlayable -> R.string.card_draw_forbidden
                    round.canDraw -> {
                        round.draw()
                        null
                    }
                    else -> R.string.huit_must_pass
                }
            }
        } else {
            null
        }
    )

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
        onPass = {
            if (round.pass()) settleIfOver()
        }
    )
}

/**
 * La table : dernière carte posée avec la pioche juste en face, couleur demandée (sur 2 lignes si besoin) et nombre de cartes de
 * chaque joueur (rien de secret). [onStockClick] null : la pioche n'est pas touchable (main cachée ou manche finie).
 */
@Composable
private fun TableCard(
    round: HuitRound,
    labels: FrenchLabels,
    players: List<String>,
    stockError: Int?,
    onStockClick: (() -> Unit)?
) {
    CardPanel {
        Text(
            text = stringResource(R.string.huit_top_card),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CardTableRow(
            stockCount = round.stock.size,
            onStockClick = onStockClick,
            top = { FrenchCardFace(card = round.top, labels = labels, playable = true, modifier = it) },
            pile = { FrenchCardBack(it) }
        ) {
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
        stockError?.let { CardErrorText(stringResource(it)) }
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
    onPass: () -> Unit
) {
    // Le 8 que le joueur vient de toucher et dont il doit encore choisir la couleur (-1 : aucun).
    var pendingEight by rememberSaveable(round.current, round.roundNumber) { mutableIntStateOf(-1) }
    val hand = round.currentHand.sortedWith(compareBy({ HuitScoring.suit(it) }, { HuitScoring.rank(it) }))
    val choosing = pendingEight in hand
    // Le texte (ressource) refusant la dernière carte ou pioche touchée ; effacé dès que la main ou le tour change.
    var error by remember(round.current, round.roundNumber, hand.size) { mutableStateOf<Int?>(null) }

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
            // Toutes les cartes ont le même aspect (aucune aide) : une carte refusée l'est par un message.
            FrenchCardFace(
                card = card,
                labels = labels,
                playable = true,
                selected = card == pendingEight,
                modifier = cardModifier,
                onClick = {
                    if (!round.isPlayable(card)) {
                        error = R.string.huit_not_playable
                    } else {
                        error = null
                        if (HuitScoring.rank(card) == HuitScoring.EIGHT && hand.size > 1) {
                            pendingEight = if (pendingEight == card) -1 else card
                        } else {
                            pendingEight = -1
                            onPlay(card, -1)
                        }
                    }
                }
            )
        }

        error?.let { CardErrorText(stringResource(it)) }

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
            round.mustPass -> {
                Text(stringResource(R.string.huit_must_pass), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onPass, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.huit_pass))
                }
            }
            else -> Text(stringResource(R.string.huit_hint), style = MaterialTheme.typography.bodyMedium)
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
