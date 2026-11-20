package com.aventure.messcores

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * L'écran du Huit américain jouable : il n'y a pas de feuille de scores derrière, tout s'affiche ici.
 *
 * - Sur un seul téléphone, ou sur celui de l'organisateur : [HuitHostSection], où l'on se passe le téléphone.
 * - Sur un téléphone invité : [HuitGuestSection], qui affiche la main du joueur à sa place et envoie ses actions à l'organisateur.
 *
 * « Retour » et la fin de partie quittent la partie ([onBack]).
 */
@Composable
internal fun HuitScreen(
    viewModel: ScoreViewModel,
    session: SyncSession,
    online: HuitOnline,
    onBack: () -> Unit
) {
    if (viewModel.gameRules.id != HUIT_ID) {
        // Aucune partie de Huit américain ouverte (partie fermée entre-temps) : retour.
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
            HuitGuestSection(viewModel = viewModel, online = online)
        }
        return
    }
    val round = remember(viewModel, viewModel.players.size) { viewModel.huitRound() }
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
        HuitHostSection(viewModel = viewModel, round = round, online = online, onLeave = onBack)
    }
}

/**
 * Le Huit américain tour par tour (voir [HuitRound]) : l'appli distribue, vérifie chaque carte posée, fait piocher, compte les
 * points de la manche et les inscrit au tableau des scores (qu'on n'a pas besoin d'ouvrir : les scores sont sur cette page).
 * Les places que personne n'a prises sur un autre téléphone se jouent ici : le téléphone se passe de joueur en joueur et la
 * main n'apparaît qu'après « Voir ma main ».
 */
@Composable
internal fun HuitHostSection(viewModel: ScoreViewModel, round: HuitRound, online: HuitOnline, onLeave: () -> Unit) {
    val players = viewModel.players
    val labels = rememberFrenchLabels()
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
        if (round.roundOver) HuitRoundResult(table = table, players = players, canContinue = false, onNext = {})
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
        table = table,
        labels = labels,
        players = players,
        stockError = stockError,
        onStockClick = if (round.revealed && !remote && !round.roundOver) {
            {
                stockError = when {
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
        HuitRoundResult(
            table = table,
            players = players,
            canContinue = round.settled,
            onNext = { round.nextRound() }
        )
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

    HandCard(
        table = table,
        labels = labels,
        players = players,
        name = name,
        active = true,
        onPlay = { card, suit ->
            if (round.play(card, suit)) settleIfOver()
        },
        onPass = {
            if (round.pass()) settleIfOver()
        }
    )
}

/**
 * Le Huit américain sur un téléphone invité : l'invité choisit sa place à la table, voit sa main (et elle seule) en permanence
 * et joue quand c'est son tour ; l'organisateur vérifie et applique ses actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HuitGuestSection(viewModel: ScoreViewModel, online: HuitOnline) {
    val players = viewModel.players
    val labels = rememberFrenchLabels()
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
        if (table.roundOver) HuitRoundResult(table = table, players = players, canContinue = false, onNext = {})
        return
    }

    Text(
        text = stringResource(R.string.huit_round, table.roundNumber),
        style = MaterialTheme.typography.labelLarge,
        color = Color.White
    )

    val myTurn = seat >= 0 && table.current == seat && !table.roundOver
    TableCard(
        table = table,
        labels = labels,
        players = players,
        stockError = stockError,
        // La pioche n'est touchable que pour le joueur dont c'est le tour ; l'organisateur la vérifie de toute façon.
        onStockClick = if (myTurn) {
            {
                stockError = when {
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
        HuitRoundResult(table = table, players = players, canContinue = table.settled, onNext = { online.next() })
        return
    }
    if (seat < 0) {
        Text(stringResource(R.string.president_seat_hint), style = MaterialTheme.typography.bodyMedium, color = Color.White)
        return
    }

    HandCard(
        table = table,
        labels = labels,
        players = players,
        name = players.getOrElse(seat) { "" },
        active = myTurn,
        onPlay = { card, suit -> online.play(card, suit) },
        onPass = { online.pass() }
    )
}

/**
 * La table : dernière carte posée avec la pioche juste en face, couleur demandée (sur 2 lignes si besoin) et nombre de cartes de
 * chaque joueur (rien de secret). [onStockClick] null : la pioche n'est pas touchable (main cachée ou manche finie).
 */
@Composable
private fun TableCard(
    table: HuitTable,
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
            stockCount = table.stockSize,
            onStockClick = onStockClick,
            top = { FrenchCardFace(card = table.top, labels = labels, playable = true, modifier = it) },
            pile = { FrenchCardBack(it) }
        ) {
            Text(
                text = stringResource(R.string.huit_asked_suit, labels.suitLabel(table.askedSuit)),
                style = MaterialTheme.typography.titleMedium,
                color = if (frenchSuitColor(table.askedSuit) == CardRed) CardRed else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.huit_stock, table.stockSize),
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
                text = stringResource(R.string.dice_pig_score_line, name, table.handSizes.getOrNull(index) ?: 0),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * La main du joueur de la table : ses cartes (les jouables sont touchables quand c'est son tour [active]), le choix de la
 * couleur après un 8, piocher ou passer. Sur un téléphone invité, hors de son tour, la main reste visible mais rien n'est touchable.
 */
@Composable
private fun HandCard(
    table: HuitTable,
    labels: FrenchLabels,
    players: List<String>,
    name: String,
    active: Boolean,
    onPlay: (card: Int, suit: Int) -> Unit,
    onPass: () -> Unit
) {
    // Le 8 que le joueur vient de toucher et dont il doit encore choisir la couleur (-1 : aucun).
    var pendingEight by rememberSaveable(table.current, table.roundNumber) { mutableIntStateOf(-1) }
    val hand = table.hand
    val choosing = active && pendingEight in hand
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
            FrenchCardFace(
                card = card,
                labels = labels,
                playable = true,
                selected = active && card == pendingEight,
                modifier = cardModifier,
                onClick = if (active) {
                    {
                        if (!table.isPlayable(card)) {
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
            table.mustPass -> {
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
private fun HuitRoundResult(table: HuitTable, players: List<String>, canContinue: Boolean, onNext: () -> Unit) {
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
