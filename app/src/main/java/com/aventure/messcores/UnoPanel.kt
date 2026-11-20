package com.aventure.messcores

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.snapshotFlow
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
 * L'écran du Uno jouable : il n'y a pas de feuille de scores derrière, tout s'affiche ici.
 *
 * - Sur un seul téléphone, ou sur celui de l'organisateur : [UnoHostSection], où l'on se passe le téléphone.
 * - Sur un téléphone invité : [UnoGuestSection], qui affiche la main du joueur à sa place et envoie ses actions à l'organisateur.
 *
 * « Retour » et la fin de partie quittent la partie ([onBack]).
 */
@Composable
internal fun UnoScreen(
    viewModel: ScoreViewModel,
    session: SyncSession,
    online: UnoOnline,
    onBack: () -> Unit
) {
    if (viewModel.gameRules.id != UNO_ID) {
        // Aucune partie de Uno ouverte (partie fermée entre-temps) : retour.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val guest = session.role == SyncRole.GUEST && session.isConnected && session.kind == SyncKind.SCORE
    if (guest) {
        val table = online.table
        CardGameScreen(
            viewModel = viewModel,
            onBack = onBack,
            scrollKeys = listOf(table?.current, table?.roundOver, table?.roundNumber, online.mySeat)
        ) {
            UnoGuestSection(viewModel = viewModel, online = online)
        }
        return
    }
    val round = remember(viewModel, viewModel.players.size) { viewModel.unoRound() }
    // Les invités reçoivent leur vue de la table à chaque changement ; une manche finie est inscrite tout de suite.
    LaunchedEffect(round, online) {
        snapshotFlow { round.tableFor(-1) }.collect { online.publish() }
    }
    LaunchedEffect(round, round.roundOver) {
        if (round.roundOver) round.settle(viewModel)
    }
    // Un autre joueur, une nouvelle manche ou la main qu'on vient de dévoiler : on remonte en haut de la page.
    CardGameScreen(
        viewModel = viewModel,
        onBack = onBack,
        scrollKeys = listOf(round.current, round.revealed, round.roundOver, round.roundNumber)
    ) {
        UnoHostSection(viewModel = viewModel, round = round, online = online, onLeave = onBack)
    }
}

/**
 * Le Uno tour par tour (voir [UnoRound]) : l'appli distribue, vérifie chaque carte posée, applique les cartes action, fait
 * piocher, annonce « UNO ! », compte les points de la manche et les inscrit au tableau des scores. Les places que personne n'a
 * prises sur un autre téléphone se jouent ici : le téléphone se passe de joueur en joueur et la main n'apparaît qu'après
 * « Voir ma main ».
 */
@Composable
internal fun UnoHostSection(viewModel: ScoreViewModel, round: UnoRound, online: UnoOnline, onLeave: () -> Unit) {
    val players = viewModel.players
    val labels = rememberUnoLabels()
    val over = viewModel.isGameOver()
    val remote = online.isRemote(round.current)
    // La main n'est montrée que quand le joueur de ce téléphone l'a demandée ; celle d'un autre téléphone ne vient jamais ici.
    val table = round.tableFor(if (round.revealed && !remote) round.current else -1)
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
        if (round.roundOver) UnoRoundResult(table = table, players = players, canContinue = false, onNext = {})
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
    UnoTableCard(
        table = table,
        labels = labels,
        players = players,
        stockError = stockError,
        onStockClick = if (round.revealed && !remote && !round.roundOver) {
            {
                stockError = when {
                    table.drawnCard >= 0 -> R.string.uno_already_drawn
                    table.hasPlayable -> R.string.card_draw_forbidden
                    table.canDraw -> {
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
        UnoRoundResult(table = table, players = players, canContinue = round.settled, onNext = { round.nextRound() })
        return
    }

    val name = players.getOrElse(round.current) { "" }
    if (remote) {
        // Ce joueur joue sur son téléphone : rien à faire ici que d'attendre.
        CardPanel {
            Text(stringResource(R.string.president_waiting_remote, name), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    if (!round.revealed) {
        // Le téléphone change de mains : la main reste cachée jusqu'à ce que son joueur la demande.
        PassPhoneCard(name = name, onReveal = { round.reveal() })
        return
    }

    UnoHandCard(
        table = table,
        labels = labels,
        players = players,
        name = name,
        active = true,
        onPlay = { card, color ->
            if (round.play(card, color)) settleIfOver()
        },
        onPass = {
            if (round.pass()) settleIfOver()
        }
    )
}

/**
 * Le Uno sur un téléphone invité : l'invité choisit sa place à la table, voit sa main (et elle seule) en permanence et joue
 * quand c'est son tour ; l'organisateur vérifie et applique ses actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UnoGuestSection(viewModel: ScoreViewModel, online: UnoOnline) {
    val players = viewModel.players
    val labels = rememberUnoLabels()
    val table = online.table
    val seat = online.mySeat
    val over = viewModel.isGameOver()
    var stockError by remember(table?.current, table?.roundNumber, table?.hand?.size) { mutableStateOf<Int?>(null) }

    ScoresCard(viewModel = viewModel, current = if (table == null || over || table.roundOver) -1 else table.current)

    CardPanel {
        Text(stringResource(R.string.president_seat_title), style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            players.forEachIndexed { index, name ->
                FilterChip(
                    selected = index == seat,
                    // Une place jouée sur un autre téléphone ne se prend pas ; toucher la sienne la libère.
                    enabled = index == seat || index !in online.taken,
                    onClick = { online.claim(if (index == seat) -1 else index) },
                    label = { Text(name) }
                )
            }
        }
    }

    if (table == null) {
        Text(stringResource(R.string.president_guest_wait), style = MaterialTheme.typography.bodyMedium, color = Color.White)
        return
    }

    if (over) {
        // La partie est finie : le vainqueur et les points de la dernière manche ; l'organisateur n'en distribue plus.
        GameOverBanner(players = players, winners = viewModel.winners())
        if (table.roundOver) UnoRoundResult(table = table, players = players, canContinue = false, onNext = {})
        return
    }

    Text(
        text = stringResource(R.string.huit_round, table.roundNumber),
        style = MaterialTheme.typography.labelLarge,
        color = Color.White
    )

    val myTurn = seat >= 0 && table.current == seat && !table.roundOver
    UnoTableCard(
        table = table,
        labels = labels,
        players = players,
        stockError = stockError,
        // La pioche n'est touchable que pour le joueur dont c'est le tour ; l'organisateur la vérifie de toute façon.
        onStockClick = if (myTurn) {
            {
                stockError = when {
                    table.drawnCard >= 0 -> R.string.uno_already_drawn
                    table.hasPlayable -> R.string.card_draw_forbidden
                    table.canDraw -> {
                        online.draw()
                        null
                    }
                    else -> R.string.huit_must_pass
                }
            }
        } else {
            null
        }
    )

    if (table.roundOver) {
        UnoRoundResult(table = table, players = players, canContinue = table.settled, onNext = { online.next() })
        return
    }
    if (seat < 0) {
        Text(stringResource(R.string.president_seat_hint), style = MaterialTheme.typography.bodyMedium, color = Color.White)
        return
    }

    UnoHandCard(
        table = table,
        labels = labels,
        players = players,
        name = players.getOrElse(seat) { "" },
        active = myTurn,
        onPlay = { card, color -> online.play(card, color) },
        onPass = { online.pass() }
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

/**
 * La table : dernière carte posée avec la pioche juste en face, couleur demandée (sur 2 lignes si besoin), sens du jeu et nombre
 * de cartes de chaque joueur (rien de secret). [onStockClick] null : la pioche n'est pas touchable (main cachée ou manche finie).
 */
@Composable
private fun UnoTableCard(
    table: UnoTable,
    labels: UnoLabels,
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
            stockCount = table.stockSize,
            onStockClick = onStockClick,
            top = { UnoCardFace(card = table.top, labels = labels, playable = true, modifier = it) },
            pile = { UnoCardBack(it) }
        ) {
            Text(
                text = stringResource(R.string.uno_asked_color, labels.colorName(table.askedColor)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = unoInk(table.askedColor)
            )
            Text(
                text = stringResource(if (table.direction > 0) R.string.uno_direction_forward else R.string.uno_direction_backward),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.huit_stock, table.stockSize),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        stockError?.let { CardErrorText(stringResource(it)) }
        table.lastEvent?.let { event ->
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
            val line = stringResource(R.string.dice_pig_score_line, name, table.handSizes.getOrNull(index) ?: 0)
            Text(
                text = if (table.hasUno(index)) line + " — " + stringResource(R.string.uno_call) else line,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (table.hasUno(index)) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

/**
 * La main d'un joueur : ses cartes, le choix de la couleur après un joker, garder la carte piochée ou passer. [active] : c'est
 * son tour, les cartes jouables sont touchables ; sinon (invité qui attend) la main est seulement affichée.
 */
@Composable
private fun UnoHandCard(
    table: UnoTable,
    labels: UnoLabels,
    players: List<String>,
    name: String,
    active: Boolean,
    onPlay: (card: Int, color: Int) -> Unit,
    onPass: () -> Unit
) {
    // Le joker que le joueur vient de toucher et dont il doit encore choisir la couleur (-1 : aucun).
    var pendingWild by rememberSaveable(table.current, table.roundNumber) { mutableIntStateOf(-1) }
    val hand = table.hand
    val choosing = active && pendingWild in hand
    // Le texte (ressource) refusant la dernière carte ou pioche touchée ; effacé dès que la main ou le tour change.
    var error by remember(table.current, table.roundNumber, hand.size) { mutableStateOf<Int?>(null) }

    CardPanel(spacing = 10) {
        if (active) {
            Text(
                text = stringResource(R.string.dice_bunco_turn, name),
                style = MaterialTheme.typography.titleMedium
            )
        }
        Text(
            text = stringResource(R.string.huit_hand_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CardRows(hand) { card, cardModifier ->
            // Toutes les cartes ont le même aspect (aucune aide) : une carte refusée l'est par un message.
            UnoCardFace(
                card = card,
                labels = labels,
                playable = true,
                selected = active && (card == pendingWild || card == table.drawnCard),
                modifier = cardModifier,
                onClick = if (active) {
                    {
                        if (!table.isPlayable(card)) {
                            // Après une pioche, seule la carte piochée peut être posée.
                            error = if (table.drawnCard >= 0 && card != table.drawnCard) R.string.uno_drawn_only else R.string.uno_not_playable
                        } else {
                            error = null
                            if (UnoScoring.isWild(card) && hand.size > 1) {
                                pendingWild = if (pendingWild == card) -1 else card
                            } else {
                                pendingWild = -1
                                onPlay(card, -1)
                            }
                        }
                    }
                } else {
                    null
                }
            )
        }

        error?.let { CardErrorText(stringResource(it)) }

        when {
            !active -> Text(
                text = stringResource(R.string.president_turn_of, players.getOrElse(table.current) { "" }),
                style = MaterialTheme.typography.bodyMedium
            )
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
            table.drawnCard >= 0 -> {
                // Après la pioche : on pose la carte piochée si on le peut, sinon on passe (sans dire si elle est jouable).
                Text(stringResource(R.string.uno_already_drawn), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onPass, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.huit_pass))
                }
            }
            table.mustPass -> {
                Text(stringResource(R.string.huit_must_pass), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onPass, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.huit_pass))
                }
            }
            else -> Text(stringResource(R.string.uno_hint), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** La fin de la manche : qui a gagné, les points de chacun (déjà inscrits au tableau) et la manche suivante. */
@Composable
private fun UnoRoundResult(table: UnoTable, players: List<String>, canContinue: Boolean, onNext: () -> Unit) {
    val winnerName = players.getOrElse(table.winner) { "" }
    RoundResultCard(
        title = stringResource(if (table.blocked) R.string.huit_round_blocked else R.string.huit_round_won, winnerName),
        players = players,
        points = table.points,
        winner = table.winner,
        canContinue = canContinue,
        onNext = onNext
    )
}

/**
 * Une carte de Uno dessinée comme sur un vrai jeu (voir [UnoCardImage]). [playable] faux :
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
    val name = labels.description(card)
    UnoCardImage(
        card = card,
        description = if (playable) name else stringResource(R.string.huit_card_blocked, name),
        playable = playable,
        modifier = modifier,
        selected = selected,
        onClick = onClick
    )
}
