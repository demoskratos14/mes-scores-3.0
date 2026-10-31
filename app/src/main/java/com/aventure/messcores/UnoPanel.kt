package com.aventure.messcores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

private val UnoRed = Color(0xFFD32F2F)
private val UnoYellow = Color(0xFFF9A825)
private val UnoGreen = Color(0xFF2E7D32)
private val UnoBlue = Color(0xFF1565C0)

/** Fond d'une couleur de carte (0 à 3), ou celui d'un joker (-1). */
private fun unoColor(color: Int): Color = when (color) {
    0 -> UnoRed
    1 -> UnoYellow
    2 -> UnoGreen
    3 -> UnoBlue
    else -> CardBlack
}

/** Couleur du texte écrit sur [unoColor] : sombre sur le jaune, clair sur le reste. */
private fun unoOnColor(color: Int): Color = if (color == 1) CardBlack else Color.White

/** Texte lisible sur le fond de l'appli pour annoncer une couleur demandée (le jaune est assombri). */
private fun unoInk(color: Int): Color = if (color == 1) Color(0xFF8D6E00) else unoColor(color)

/** Noms des cartes et des couleurs, selon la langue de l'appli. */
private class UnoLabels(faces: String, kinds: String, colors: String, private val template: String) {
    private val faceList = faces.split(',')
    private val kindList = kinds.split(',')
    private val colorList = colors.split(',')

    /** Texte écrit sur la carte : le chiffre ou le symbole de l'action. */
    fun face(card: Int): String = faceList.getOrElse(UnoScoring.kind(card)) { "" }

    fun kindName(card: Int): String = kindList.getOrElse(UnoScoring.kind(card)) { "" }

    fun colorName(color: Int): String = colorList.getOrElse(color) { "" }

    /** Nom complet de la carte, lu par TalkBack : « 5 rouge », « +2 bleu », « joker ». */
    fun description(card: Int): String =
        if (UnoScoring.isWild(card)) kindName(card) else String.format(Locale.getDefault(), template, kindName(card), colorName(UnoScoring.color(card)))
}

@Composable
private fun rememberUnoLabels(): UnoLabels = UnoLabels(
    faces = stringResource(R.string.uno_faces),
    kinds = stringResource(R.string.uno_kinds),
    colors = stringResource(R.string.uno_colors),
    template = stringResource(R.string.uno_card_desc)
)

/**
 * L'écran du Uno jouable : il n'y a pas de feuille de scores derrière, tout s'affiche ici (voir [UnoSection]).
 * « Retour » et la fin de partie quittent la partie ([onBack]).
 */
@Composable
internal fun UnoScreen(viewModel: ScoreViewModel, onBack: () -> Unit) {
    if (viewModel.gameRules.id != UNO_ID) {
        // Aucune partie de Uno ouverte (partie fermée entre-temps) : retour.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val round = remember(viewModel, viewModel.players.size) { viewModel.unoRound() }
    // Un autre joueur, une nouvelle manche ou la main qu'on vient de dévoiler : on remonte en haut de la page.
    CardGameScreen(
        viewModel = viewModel,
        onBack = onBack,
        scrollKeys = listOf(round.current, round.revealed, round.roundOver, round.roundNumber)
    ) {
        UnoSection(viewModel = viewModel, round = round, onLeave = onBack)
    }
}

/**
 * Le Uno tour par tour (voir [UnoRound]) : l'appli distribue, vérifie chaque carte posée, applique les cartes action, fait
 * piocher, annonce « UNO ! », compte les points de la manche et les inscrit au tableau des scores. Le téléphone se passe de
 * joueur en joueur : la main n'apparaît qu'après « Voir ma main ».
 */
@Composable
internal fun UnoSection(viewModel: ScoreViewModel, round: UnoRound, onLeave: () -> Unit) {
    val players = viewModel.players
    val labels = rememberUnoLabels()
    val over = viewModel.isGameOver()

    // Une manche qui vient de finir est inscrite tout de suite : quitter ensuite ne la fait pas perdre.
    fun settleIfOver() {
        if (round.roundOver) round.settle(viewModel)
    }

    ScoresCard(viewModel = viewModel, current = if (over || round.roundOver) -1 else round.current)

    if (over) {
        // La partie est finie (ou reprise déjà finie) : le vainqueur, les points de la dernière manche s'il y en a une, puis la sortie.
        GameOverBanner(players = players, winners = viewModel.winners())
        if (round.roundOver) UnoRoundResult(round = round, players = players, canContinue = false, onNext = {})
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

    UnoTableCard(round = round, labels = labels, players = players)

    if (round.roundOver) {
        UnoRoundResult(
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

    UnoHandCard(
        round = round,
        labels = labels,
        name = name,
        onPlay = { card, color ->
            if (round.play(card, color)) settleIfOver()
        },
        onDraw = { round.draw() },
        onPass = {
            if (round.pass()) settleIfOver()
        }
    )
}

/** Ce qu'a produit la dernière carte spéciale, en une phrase. */
@Composable
private fun eventText(event: UnoEvent, labels: UnoLabels, players: List<String>): String {
    val who = players.getOrElse(event.player) { "" }
    val card = labels.description(event.card)
    val victim = players.getOrElse(event.victim) { "" }
    return when (UnoScoring.kind(event.card)) {
        UnoScoring.SKIP -> stringResource(R.string.uno_event_skip, who, card, victim)
        UnoScoring.REVERSE ->
            if (event.victim >= 0) {
                stringResource(R.string.uno_event_skip, who, card, victim)
            } else {
                stringResource(R.string.uno_event_reverse, who, card)
            }
        UnoScoring.DRAW_TWO, UnoScoring.WILD_FOUR -> stringResource(R.string.uno_event_draw, who, card, victim, event.drawn)
        else -> stringResource(R.string.uno_event_wild, who, card)
    }
}

/** La table : dernière carte posée, couleur demandée, sens du jeu, pioche et nombre de cartes de chaque joueur (rien de secret). */
@Composable
private fun UnoTableCard(round: UnoRound, labels: UnoLabels, players: List<String>) {
    CardPanel {
        Text(
            text = stringResource(R.string.huit_top_card),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            UnoCardFace(card = round.top, labels = labels, playable = true, modifier = Modifier.width(64.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.uno_asked_color, labels.colorName(round.askedColor)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = unoInk(round.askedColor)
                )
                Text(
                    text = stringResource(if (round.direction > 0) R.string.uno_direction_forward else R.string.uno_direction_backward),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.huit_stock, round.stock.size),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        round.lastEvent?.let { event ->
            Text(
                text = eventText(event, labels, players),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = stringResource(R.string.huit_hands_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        players.forEachIndexed { index, name ->
            val line = stringResource(R.string.dice_pig_score_line, name, round.hands.getOrNull(index)?.size ?: 0)
            Text(
                text = if (round.hasUno(index)) line + " — " + stringResource(R.string.uno_call) else line,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (round.hasUno(index)) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

/**
 * La main du joueur qui joue : ses cartes (les jouables sont touchables), le choix de la couleur après un joker, piocher,
 * garder la carte piochée ou passer.
 */
@Composable
private fun UnoHandCard(
    round: UnoRound,
    labels: UnoLabels,
    name: String,
    onPlay: (card: Int, color: Int) -> Unit,
    onDraw: () -> Unit,
    onPass: () -> Unit
) {
    // Le joker que le joueur vient de toucher et dont il doit encore choisir la couleur (-1 : aucun).
    var pendingWild by rememberSaveable(round.current, round.roundNumber) { mutableIntStateOf(-1) }
    val hand = round.currentHand.sortedWith(
        compareBy({ if (UnoScoring.isWild(it)) UnoScoring.COLORS else UnoScoring.color(it) }, { UnoScoring.kind(it) })
    )
    val choosing = pendingWild in hand

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
            UnoCardFace(
                card = card,
                labels = labels,
                playable = playable,
                selected = card == pendingWild || card == round.drawnCard,
                modifier = cardModifier,
                onClick = if (playable) {
                    {
                        if (UnoScoring.isWild(card) && hand.size > 1) {
                            pendingWild = if (pendingWild == card) -1 else card
                        } else {
                            pendingWild = -1
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
                Text(stringResource(R.string.uno_pick_color), style = MaterialTheme.typography.bodyMedium)
                for (pair in listOf(0 to 1, 2 to 3)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (color in listOf(pair.first, pair.second)) {
                            Button(
                                onClick = {
                                    val card = pendingWild
                                    pendingWild = -1
                                    onPlay(card, color)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = unoColor(color),
                                    contentColor = unoOnColor(color)
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(labels.colorName(color), maxLines = 1)
                            }
                        }
                    }
                }
                TextButton(onClick = { pendingWild = -1 }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
            round.canKeep -> {
                Text(stringResource(R.string.uno_drawn_playable), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onPass, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.uno_keep))
                }
            }
            round.hasPlayable -> Text(stringResource(R.string.uno_hint), style = MaterialTheme.typography.bodyMedium)
            round.canDraw -> {
                Text(stringResource(R.string.uno_no_play), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onDraw, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.huit_draw))
                }
            }
            round.mustPass -> {
                Text(
                    text = stringResource(if (round.drawnCard >= 0) R.string.uno_drawn_blocked else R.string.huit_must_pass),
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = onPass, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.huit_pass))
                }
            }
        }
    }
}

/** La fin de la manche : qui a gagné, les points de chacun (déjà inscrits au tableau) et la manche suivante. */
@Composable
private fun UnoRoundResult(round: UnoRound, players: List<String>, canContinue: Boolean, onNext: () -> Unit) {
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

/**
 * Une carte de Uno : fond de la couleur de la carte (noir pour un joker), chiffre ou symbole au centre. [playable] faux :
 * grisée et non touchable ; [onClick] null : simple affichage.
 */
@Composable
private fun UnoCardFace(
    card: Int,
    labels: UnoLabels,
    playable: Boolean,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val color = UnoScoring.color(card)
    val name = labels.description(card)
    PlayingCard(
        text = labels.face(card),
        description = if (playable) name else stringResource(R.string.huit_card_blocked, name),
        background = if (playable) unoColor(color) else CardBlocked,
        textColor = if (playable) unoOnColor(color) else unoColor(color).copy(alpha = 0.7f),
        modifier = modifier,
        selected = selected,
        onClick = onClick
    )
}
