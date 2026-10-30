package com.aventure.messcores

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Symboles des quatre couleurs, dans l'ordre de [HuitScoring.suit] : pique, cœur, carreau, trèfle. */
private val SUIT_SYMBOLS = listOf("♠", "♥", "♦", "♣")

/** Cartes par ligne dans la main affichée. */
private const val CARDS_PER_ROW = 5

private val CardWhite = Color(0xFFFAFAFA)
private val CardBlocked = Color(0xFFBDBDBD)
private val CardBlack = Color(0xFF212121)
private val CardRed = Color(0xFFC62828)

/** Noms des valeurs (A, 2…10, V, D, R) et des couleurs, selon la langue de l'appli. */
private class HuitLabels(ranks: String, suits: String) {
    private val rankList = ranks.split(',')
    private val suitList = suits.split(',')

    fun rank(card: Int): String = rankList.getOrElse(HuitScoring.rank(card) - 1) { "" }

    fun suitName(suit: Int): String = suitList.getOrElse(suit) { "" }

    fun symbol(suit: Int): String = SUIT_SYMBOLS.getOrElse(suit) { "" }

    /** Texte écrit sur la carte : sa valeur et le symbole de sa couleur. */
    fun face(card: Int): String = rank(card) + symbol(HuitScoring.suit(card))

    /** Symbole et nom d'une couleur : « ♥ cœur ». */
    fun suitLabel(suit: Int): String = symbol(suit) + " " + suitName(suit)
}

private fun suitColor(suit: Int): Color = if (suit == 1 || suit == 2) CardRed else CardBlack

/**
 * L'écran du Huit américain jouable : il n'y a pas de feuille de scores derrière, tout s'affiche ici (voir [HuitSection]).
 * « Retour » et la fin de partie quittent la partie ([onBack]).
 */
@Composable
internal fun HuitScreen(viewModel: ScoreViewModel, onBack: () -> Unit) {
    val rules = viewModel.gameRules
    if (rules.id != HUIT_ID) {
        // Aucune partie de Huit américain ouverte (partie fermée entre-temps) : retour.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    KeepScreenOn()
    val round = remember(viewModel, viewModel.players.size) { viewModel.huitRound() }
    val scrollState = rememberScrollState()
    // Un autre joueur, une nouvelle manche ou la main qu'on vient de dévoiler : on remonte en haut de la page.
    LaunchedEffect(round.current, round.revealed, round.roundOver, round.roundNumber) { scrollState.animateScrollTo(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.common_back), color = Color.White)
        }
        Text(
            text = stringResource(R.string.category_cards),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            modifier = Modifier.semantics { heading() }
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = rules.name,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(10.dp))
            RulesButton(rules = rules)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HuitSection(viewModel = viewModel, round = round, onLeave = onBack)
        }
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
    val labels = HuitLabels(stringResource(R.string.huit_ranks), stringResource(R.string.huit_suits))
    val over = viewModel.isGameOver()
    val totals = viewModel.totals

    // Une manche qui vient de finir est inscrite tout de suite : quitter ensuite ne la fait pas perdre.
    fun settleIfOver() {
        if (round.roundOver) round.settle(viewModel)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.dice_sb_scores_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            players.forEachIndexed { index, name ->
                val mine = !over && !round.roundOver && index == round.current
                Text(
                    text = stringResource(R.string.dice_pig_score_line, name, totals.getOrElse(index) { 0 }),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                    color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    if (over) {
        // La partie est finie (ou reprise déjà finie) : le vainqueur, les points de la dernière manche s'il y en a une, puis la sortie.
        GameOverBanner(players = players, winners = viewModel.winners())
        if (round.roundOver) RoundResult(round = round, players = players, canContinue = false, onNext = {})
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
        RoundResult(
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
        Card(
            colors = CardDefaults.cardColors(containerColor = cardSurface()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.dice_bunco_turn, name), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.huit_cover_hint, name), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { round.reveal() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.huit_show_hand))
                }
            }
        }
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
private fun TableCard(round: HuitRound, labels: HuitLabels, players: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.huit_top_card),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CardFace(card = round.top, labels = labels, playable = true, modifier = Modifier.width(64.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.huit_asked_suit, labels.suitLabel(round.askedSuit)),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (suitColor(round.askedSuit) == CardRed) CardRed else MaterialTheme.colorScheme.onSurface
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
}

/** La main du joueur qui joue : ses cartes (les jouables sont touchables), le choix de la couleur après un 8, piocher ou passer. */
@Composable
private fun HandCard(
    round: HuitRound,
    labels: HuitLabels,
    name: String,
    onPlay: (card: Int, suit: Int) -> Unit,
    onDraw: () -> Unit,
    onPass: () -> Unit
) {
    // Le 8 que le joueur vient de toucher et dont il doit encore choisir la couleur (-1 : aucun).
    var pendingEight by rememberSaveable(round.current, round.roundNumber) { mutableIntStateOf(-1) }
    val hand = round.currentHand.sortedWith(compareBy({ HuitScoring.suit(it) }, { HuitScoring.rank(it) }))
    val choosing = pendingEight in hand

    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.dice_bunco_turn, name),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.huit_hand_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            hand.chunked(CARDS_PER_ROW).forEach { line ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    line.forEach { card ->
                        val playable = round.isPlayable(card)
                        CardFace(
                            card = card,
                            labels = labels,
                            playable = playable,
                            selected = card == pendingEight,
                            modifier = Modifier.weight(1f),
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
                    // Dernière ligne incomplète : on garde la largeur des cartes.
                    repeat(CARDS_PER_ROW - line.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
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
                                    color = if (suitColor(suit) == CardRed) CardRed else MaterialTheme.colorScheme.onSurface
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
}

/** La fin de la manche : qui a gagné, les points de chacun (déjà inscrits au tableau) et la manche suivante. */
@Composable
private fun RoundResult(round: HuitRound, players: List<String>, canContinue: Boolean, onNext: () -> Unit) {
    val winnerName = players.getOrElse(round.winner) { "" }
    val points = round.roundPoints()
    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(if (round.blocked) R.string.huit_round_blocked else R.string.huit_round_won, winnerName),
                style = MaterialTheme.typography.titleMedium
            )
            players.forEachIndexed { index, name ->
                Text(
                    text = stringResource(R.string.huit_points_line, name, points.getOrElse(index) { 0 }),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (index == round.winner) FontWeight.Bold else FontWeight.Normal
                )
            }
            if (canContinue) {
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.huit_next_round))
                }
            }
        }
    }
}

/**
 * Une carte à jouer : fond clair, valeur et couleur en noir ou en rouge. [playable] faux : estompée et non touchable ;
 * [onClick] null : simple affichage.
 */
@Composable
private fun CardFace(
    card: Int,
    labels: HuitLabels,
    playable: Boolean,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val suit = HuitScoring.suit(card)
    val face = labels.face(card)
    val name = stringResource(R.string.huit_card_desc, labels.rank(card), labels.suitName(suit))
    val description = if (playable) name else stringResource(R.string.huit_card_blocked, name)
    val shape = RoundedCornerShape(8.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(0.7f)
            .clip(shape)
            .background(if (playable) CardWhite else CardBlocked, shape)
            .border(
                width = if (selected) 4.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray,
                shape = shape
            )
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { contentDescription = description }
    ) {
        Text(
            text = face,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (playable) suitColor(suit) else suitColor(suit).copy(alpha = 0.55f)
        )
    }
}
