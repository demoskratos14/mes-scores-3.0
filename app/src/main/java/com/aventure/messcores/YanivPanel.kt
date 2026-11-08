package com.aventure.messcores

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * L'écran de Yaniv jouable : il n'y a pas de feuille de scores derrière, tout s'affiche ici (voir [YanivSection]).
 * « Retour » et la fin de partie quittent la partie ([onBack]).
 */
@Composable
internal fun YanivScreen(viewModel: ScoreViewModel, onBack: () -> Unit) {
    if (viewModel.gameRules.id != YANIV_ID) {
        // Aucune partie de Yaniv ouverte (partie fermée entre-temps) : retour.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val round = remember(viewModel, viewModel.players.size) { viewModel.yanivRound() }
    // Un autre joueur, une nouvelle manche, la main qu'on vient de dévoiler ou la pioche après la défausse : on remonte en haut de la page.
    CardGameScreen(
        viewModel = viewModel,
        onBack = onBack,
        scrollKeys = listOf(round.current, round.revealed, round.roundOver, round.roundNumber, round.phase)
    ) {
        YanivSection(viewModel = viewModel, round = round, onLeave = onBack)
    }
}

/**
 * Yaniv tour par tour (voir [YanivRound]) : l'appli distribue, vérifie chaque défausse, compte les points de la manche (« Yaniv »
 * réussi ou « Assaf ») et les inscrit au tableau des scores. Le téléphone se passe de joueur en joueur : la main n'apparaît
 * qu'après « Voir ma main ».
 */
@Composable
internal fun YanivSection(viewModel: ScoreViewModel, round: YanivRound, onLeave: () -> Unit) {
    val players = viewModel.players
    val labels = rememberFrenchLabels()
    val over = viewModel.isGameOver()
    // Le texte (ressource) refusant la dernière sélection ou carte touchée ; effacé dès que la main, la phase ou le tour change.
    var error by remember(round.current, round.roundNumber, round.phase, round.hands) { mutableStateOf<Int?>(null) }

    // Une manche qui vient de finir est inscrite tout de suite : quitter ensuite ne la fait pas perdre.
    fun settleIfOver() {
        if (round.roundOver) round.settle(viewModel)
    }

    ScoresCard(viewModel = viewModel, current = if (over || round.roundOver) -1 else round.current)

    if (over) {
        // La partie est finie (ou reprise déjà finie) : le vainqueur, le résultat de la dernière manche s'il y en a une, puis la sortie.
        GameOverBanner(players = players, winners = viewModel.winners())
        if (round.roundOver) YanivRoundResult(round = round, players = players, labels = labels, canContinue = false, onNext = {})
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

    // On ne touche la table (pioche, dernière défausse) que pour piocher : main dévoilée et défausse déjà faite.
    val canPick = round.revealed && !round.roundOver && round.phase == YanivPhase.DRAW
    TableCard(
        round = round,
        labels = labels,
        players = players,
        error = if (round.phase == YanivPhase.DRAW) error else null,
        onStockClick = if (canPick) {
            {
                error = if (round.drawFromStock()) null else R.string.yaniv_stock_empty
            }
        } else {
            null
        },
        onDiscardClick = if (canPick) {
            { card ->
                error = if (round.takeFromDiscard(card)) null else R.string.yaniv_not_drawable
            }
        } else {
            null
        }
    )

    if (round.roundOver) {
        YanivRoundResult(
            round = round,
            players = players,
            labels = labels,
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
        error = error,
        onError = { error = it },
        onDiscard = { cards ->
            error = if (round.discard(cards)) null else R.string.yaniv_not_valid
        },
        onYaniv = {
            if (round.callYaniv()) settleIfOver()
        }
    )
}

/** Une carte du jeu de 54 cartes : l'image d'une carte, ou celle d'un joker. */
@Composable
private fun YanivCardFace(
    card: Int,
    labels: FrenchLabels,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    if (YanivScoring.isJoker(card)) {
        ImageCard(
            image = yanivCardImage(card),
            description = stringResource(R.string.yaniv_joker),
            playable = true,
            modifier = modifier,
            selected = selected,
            onClick = onClick
        )
    } else {
        FrenchCardFace(card = card, labels = labels, playable = true, modifier = modifier, selected = selected, onClick = onClick)
    }
}

/**
 * La table : la dernière défausse (celle du joueur précédent), la pioche et le nombre de cartes de chaque joueur (rien de secret).
 * [onStockClick] et [onDiscardClick] null : la table n'est pas touchable (main cachée, défausse pas encore faite ou manche finie).
 */
@Composable
private fun TableCard(
    round: YanivRound,
    labels: FrenchLabels,
    players: List<String>,
    error: Int?,
    onStockClick: (() -> Unit)?,
    onDiscardClick: ((Int) -> Unit)?
) {
    val stockDescription = pluralStringResource(R.plurals.card_stock_desc, round.stock.size, round.stock.size)
    CardPanel {
        Text(
            text = stringResource(R.string.yaniv_discard_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CardRows(round.lastDiscard) { card, cardModifier ->
            YanivCardFace(
                card = card,
                labels = labels,
                modifier = cardModifier,
                onClick = onDiscardClick?.let { click -> { click(card) } }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FrenchCardBack(
                Modifier
                    .width(64.dp)
                    .then(if (onStockClick != null) Modifier.clickable(role = Role.Button, onClick = onStockClick) else Modifier)
                    .semantics { contentDescription = stockDescription }
            )
            Text(
                text = stringResource(R.string.huit_stock, round.stock.size),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        error?.let { CardErrorText(stringResource(it)) }
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

/**
 * La main du joueur qui joue : on touche les cartes à défausser (une carte, une paire, un brelan ou une suite), puis « Défausser » ;
 * ensuite il pioche sur la table. Avec 5 points ou moins, il peut annoncer « Yaniv » à la place.
 */
@Composable
private fun HandCard(
    round: YanivRound,
    labels: FrenchLabels,
    name: String,
    error: Int?,
    onError: (Int?) -> Unit,
    onDiscard: (List<Int>) -> Unit,
    onYaniv: () -> Unit
) {
    val hand = round.currentHand.sortedBy { YanivScoring.sortKey(it) }
    val discarding = round.phase == YanivPhase.DISCARD
    // Les cartes choisies pour la défausse ; remises à zéro dès que la main ou le tour change.
    var selection by remember(round.current, round.roundNumber, round.hands) { mutableStateOf(emptySet<Int>()) }

    CardPanel(spacing = 10) {
        Text(
            text = stringResource(R.string.dice_bunco_turn, name),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.yaniv_hand_points, round.currentPoints),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CardRows(hand) { card, cardModifier ->
            YanivCardFace(
                card = card,
                labels = labels,
                modifier = cardModifier,
                selected = card in selection,
                onClick = if (discarding) {
                    {
                        onError(null)
                        selection = if (card in selection) selection - card else selection + card
                    }
                } else {
                    null
                }
            )
        }

        if (!discarding) {
            // Défausse faite : les cartes défaussées restent visibles, il reste à piocher.
            Text(
                text = stringResource(R.string.yaniv_discarded_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CardRows(round.pending) { card, cardModifier ->
                YanivCardFace(card = card, labels = labels, modifier = cardModifier)
            }
            Text(stringResource(R.string.yaniv_draw_hint), style = MaterialTheme.typography.bodyMedium)
            return@CardPanel
        }

        Text(stringResource(R.string.yaniv_hint), style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = {
                if (selection.isEmpty()) {
                    onError(R.string.yaniv_select_first)
                } else {
                    // Les cartes dans l'ordre de la main : le moteur range lui-même une suite.
                    onDiscard(hand.filter { it in selection })
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.yaniv_discard))
        }
        if (round.canCallYaniv) {
            OutlinedButton(onClick = onYaniv, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.yaniv_call, round.currentPoints))
            }
        }
        // Sélection refusée : le message est sous les boutons ; celui d'une pioche refusée est sous la table.
        error?.let { CardErrorText(stringResource(it)) }
    }
}

/** La fin de la manche : « Yaniv » ou « Assaf », les mains de chacun, les points (déjà inscrits au tableau) et la manche suivante. */
@Composable
private fun YanivRoundResult(
    round: YanivRound,
    players: List<String>,
    labels: FrenchLabels,
    canContinue: Boolean,
    onNext: () -> Unit
) {
    val callerName = players.getOrElse(round.caller) { "" }
    val title = if (round.assafBy >= 0) {
        stringResource(R.string.yaniv_round_assaf, callerName, players.getOrElse(round.assafBy) { "" })
    } else {
        stringResource(R.string.yaniv_round_won, callerName, YanivScoring.handPoints(round.hands.getOrElse(round.caller) { emptyList() }))
    }
    // joinToString n'est pas inline : le texte du joker est lu ici, hors de sa lambda.
    val jokerFace = stringResource(R.string.yaniv_joker_face)
    CardPanel {
        Text(
            text = stringResource(R.string.yaniv_hands_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        players.forEachIndexed { index, name ->
            val hand = round.hands.getOrElse(index) { emptyList() }.sortedBy { YanivScoring.sortKey(it) }
            val text = hand.joinToString(" ") { card -> if (YanivScoring.isJoker(card)) jokerFace else labels.face(card) }
            Text(
                text = stringResource(R.string.yaniv_hand_line, name, text, YanivScoring.handPoints(hand)),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
    RoundResultCard(
        title = title,
        players = players,
        points = round.roundPoints(),
        winner = round.winner,
        canContinue = canContinue,
        onNext = onNext
    )
}
