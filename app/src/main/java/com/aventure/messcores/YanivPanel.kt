package com.aventure.messcores

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
 * L'écran de Yaniv jouable : il n'y a pas de feuille de scores derrière, tout s'affiche ici.
 *
 * - Sur un seul téléphone, ou sur celui de l'organisateur : [YanivHostSection], où l'on se passe le téléphone.
 * - Sur un téléphone invité : [YanivGuestSection], qui affiche la main du joueur à sa place et envoie ses actions à l'organisateur.
 *
 * « Retour » et la fin de partie quittent la partie ([onBack]) ; [onOpenShare] ouvre le partage entre téléphones.
 */
@Composable
internal fun YanivScreen(
    viewModel: ScoreViewModel,
    session: SyncSession,
    online: YanivOnline,
    onOpenShare: () -> Unit,
    onBack: () -> Unit
) {
    if (viewModel.gameRules.id != YANIV_ID) {
        // Aucune partie de Yaniv ouverte (partie fermée entre-temps) : retour.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val guest = session.role == SyncRole.GUEST && session.isConnected && session.kind == SyncKind.SCORE
    if (guest) {
        val table = online.table
        CardGameScreen(
            viewModel = viewModel,
            onBack = onBack,
            scrollKeys = listOf(table?.current, table?.roundOver, table?.roundNumber, table?.drawing, online.mySeat),
            onOpenShare = onOpenShare,
            shareActive = true
        ) {
            YanivGuestSection(viewModel = viewModel, online = online)
        }
        return
    }
    val round = remember(viewModel, viewModel.players.size) { viewModel.yanivRound() }
    // Les invités reçoivent leur vue de la table à chaque changement ; une manche finie est inscrite tout de suite.
    LaunchedEffect(round, online) {
        snapshotFlow { round.tableFor(-1) }.collect { online.publish() }
    }
    LaunchedEffect(round, round.roundOver) {
        if (round.roundOver) round.settle(viewModel)
    }
    // Un autre joueur, une nouvelle manche, la main qu'on vient de dévoiler ou la pioche après la défausse : on remonte en haut de la page.
    CardGameScreen(
        viewModel = viewModel,
        onBack = onBack,
        scrollKeys = listOf(round.current, round.revealed, round.roundOver, round.roundNumber, round.phase),
        onOpenShare = onOpenShare,
        shareActive = session.isConnected
    ) {
        YanivHostSection(viewModel = viewModel, round = round, online = online, onLeave = onBack)
    }
}

/**
 * Yaniv tour par tour (voir [YanivRound]) : l'appli distribue, vérifie chaque défausse, compte les points de la manche (« Yaniv »
 * réussi ou « Assaf ») et les inscrit au tableau des scores. Les places que personne n'a prises sur un autre téléphone se
 * jouent ici : le téléphone se passe de joueur en joueur et la main n'apparaît qu'après « Voir ma main ».
 */
@Composable
internal fun YanivHostSection(viewModel: ScoreViewModel, round: YanivRound, online: YanivOnline, onLeave: () -> Unit) {
    val players = viewModel.players
    val labels = rememberFrenchLabels()
    val over = viewModel.isGameOver()
    val remote = online.isRemote(round.current)
    // La main n'est montrée que quand le joueur de ce téléphone l'a demandée ; celle d'un autre téléphone ne vient jamais ici.
    val table = round.tableFor(if (round.revealed && !remote) round.current else -1)
    // Le texte (ressource) refusant la pioche touchée ; effacé dès que la main, la phase ou le tour change.
    var tableError by remember(round.current, round.roundNumber, round.phase, round.hands) { mutableStateOf<Int?>(null) }

    // Une manche qui vient de finir est inscrite tout de suite : quitter ensuite ne la fait pas perdre.
    fun settleIfOver() {
        if (round.roundOver) round.settle(viewModel)
    }

    ScoresCard(viewModel = viewModel, current = if (over || round.roundOver) -1 else round.current)

    if (over) {
        // La partie est finie (ou reprise déjà finie) : le vainqueur, le résultat de la dernière manche s'il y en a une, puis la sortie.
        GameOverBanner(players = players, winners = viewModel.winners())
        if (round.roundOver) YanivRoundResult(table = table, players = players, labels = labels, canContinue = false, onNext = {})
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

    // On ne touche la table (pioche, dernière défausse) que pour piocher : main dévoilée sur ce téléphone et défausse déjà faite.
    val canPick = round.revealed && !remote && !round.roundOver && round.phase == YanivPhase.DRAW
    YanivTableCard(
        table = table,
        labels = labels,
        players = players,
        error = if (round.phase == YanivPhase.DRAW) tableError else null,
        onStockClick = if (canPick) {
            {
                tableError = if (round.drawFromStock()) null else R.string.yaniv_stock_empty
            }
        } else {
            null
        },
        onDiscardClick = if (canPick) {
            { card ->
                tableError = if (round.takeFromDiscard(card)) null else R.string.yaniv_not_drawable
            }
        } else {
            null
        }
    )

    if (round.roundOver) {
        YanivRoundResult(
            table = table,
            players = players,
            labels = labels,
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

    YanivHandCard(
        table = table,
        labels = labels,
        players = players,
        name = name,
        active = true,
        onDiscard = { cards, onRefused ->
            if (!round.discard(cards)) onRefused()
        },
        onYaniv = {
            if (round.callYaniv()) settleIfOver()
        }
    )
}

/**
 * Le Yaniv sur un téléphone invité : l'invité choisit sa place à la table, voit sa main (et elle seule) en permanence et joue
 * quand c'est son tour ; l'organisateur vérifie et applique ses actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun YanivGuestSection(viewModel: ScoreViewModel, online: YanivOnline) {
    val players = viewModel.players
    val labels = rememberFrenchLabels()
    val table = online.table
    val seat = online.mySeat
    val over = viewModel.isGameOver()
    var tableError by remember(table?.current, table?.roundNumber, table?.drawing, table?.hand) { mutableStateOf<Int?>(null) }

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
        if (table.roundOver) YanivRoundResult(table = table, players = players, labels = labels, canContinue = false, onNext = {})
        return
    }

    Text(
        text = stringResource(R.string.huit_round, table.roundNumber),
        style = MaterialTheme.typography.labelLarge,
        color = Color.White
    )

    val myTurn = seat >= 0 && table.current == seat && !table.roundOver
    // La table n'est touchable que pour le joueur dont c'est le tour, une fois sa défausse faite ; l'organisateur vérifie de toute façon.
    val canPick = myTurn && table.drawing
    YanivTableCard(
        table = table,
        labels = labels,
        players = players,
        error = if (table.drawing) tableError else null,
        onStockClick = if (canPick) {
            {
                if (table.canDrawFromStock) {
                    tableError = null
                    online.drawFromStock()
                } else {
                    tableError = R.string.yaniv_stock_empty
                }
            }
        } else {
            null
        },
        onDiscardClick = if (canPick) {
            { card ->
                if (card in table.drawable) {
                    tableError = null
                    online.takeFromDiscard(card)
                } else {
                    tableError = R.string.yaniv_not_drawable
                }
            }
        } else {
            null
        }
    )

    if (table.roundOver) {
        YanivRoundResult(table = table, players = players, labels = labels, canContinue = table.settled, onNext = { online.next() })
        return
    }
    if (seat < 0) {
        Text(stringResource(R.string.president_seat_hint), style = MaterialTheme.typography.bodyMedium, color = Color.White)
        return
    }

    YanivHandCard(
        table = table,
        labels = labels,
        players = players,
        name = players.getOrElse(seat) { "" },
        active = myTurn,
        onDiscard = { cards, onRefused ->
            if (YanivScoring.isValidDiscard(cards)) online.discard(cards) else onRefused()
        },
        onYaniv = { online.callYaniv() }
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
private fun YanivTableCard(
    table: YanivTable,
    labels: FrenchLabels,
    players: List<String>,
    error: Int?,
    onStockClick: (() -> Unit)?,
    onDiscardClick: ((Int) -> Unit)?
) {
    val stockDescription = pluralStringResource(R.plurals.card_stock_desc, table.stockSize, table.stockSize)
    CardPanel {
        Text(
            text = stringResource(R.string.yaniv_discard_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CardRows(table.lastDiscard) { card, cardModifier ->
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
                text = stringResource(R.string.huit_stock, table.stockSize),
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
                text = stringResource(R.string.dice_pig_score_line, name, table.handSizes.getOrNull(index) ?: 0),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * La main du joueur : si [active] (c'est son tour), on touche les cartes à défausser (une carte, une paire, un brelan ou une
 * suite), puis « Défausser » ; ensuite il pioche sur la table. Avec 5 points ou moins, il peut annoncer « Yaniv » à la place.
 * [onDiscard] reçoit les cartes et un rappel à appeler si la défausse est refusée.
 */
@Composable
private fun YanivHandCard(
    table: YanivTable,
    labels: FrenchLabels,
    players: List<String>,
    name: String,
    active: Boolean,
    onDiscard: (cards: List<Int>, onRefused: () -> Unit) -> Unit,
    onYaniv: () -> Unit
) {
    val hand = table.hand
    val discarding = active && !table.drawing
    // Les cartes choisies pour la défausse ; remises à zéro dès que la main ou le tour change.
    var selection by remember(table.current, table.roundNumber, table.hand) { mutableStateOf(emptySet<Int>()) }
    // Le texte (ressource) refusant la dernière sélection ; effacé dès qu'on touche une carte, ou que la main ou le tour change.
    var error by remember(table.current, table.roundNumber, table.hand) { mutableStateOf<Int?>(null) }

    CardPanel(spacing = 10) {
        if (active) {
            Text(
                text = stringResource(R.string.dice_bunco_turn, name),
                style = MaterialTheme.typography.titleMedium
            )
        }
        Text(
            text = stringResource(R.string.yaniv_hand_points, table.handPoints),
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
                        error = null
                        selection = if (card in selection) selection - card else selection + card
                    }
                } else {
                    null
                }
            )
        }

        if (!active) {
            Text(
                text = stringResource(R.string.president_turn_of, players.getOrElse(table.current) { "" }),
                style = MaterialTheme.typography.bodyMedium
            )
            return@CardPanel
        }

        if (table.drawing) {
            // Défausse faite : les cartes défaussées restent visibles, il reste à piocher.
            Text(
                text = stringResource(R.string.yaniv_discarded_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CardRows(table.pending) { card, cardModifier ->
                YanivCardFace(card = card, labels = labels, modifier = cardModifier)
            }
            Text(stringResource(R.string.yaniv_draw_hint), style = MaterialTheme.typography.bodyMedium)
            return@CardPanel
        }

        Text(stringResource(R.string.yaniv_hint), style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = {
                if (selection.isEmpty()) {
                    error = R.string.yaniv_select_first
                } else {
                    // Les cartes dans l'ordre de la main : le moteur range lui-même une suite.
                    onDiscard(hand.filter { it in selection }) { error = R.string.yaniv_not_valid }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.yaniv_discard))
        }
        if (table.canCallYaniv) {
            OutlinedButton(onClick = onYaniv, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.yaniv_call, table.handPoints))
            }
        }
        error?.let { CardErrorText(stringResource(it)) }
    }
}

/** La fin de la manche : « Yaniv » ou « Assaf », les mains de chacun, les points (déjà inscrits au tableau) et la manche suivante. */
@Composable
private fun YanivRoundResult(
    table: YanivTable,
    players: List<String>,
    labels: FrenchLabels,
    canContinue: Boolean,
    onNext: () -> Unit
) {
    val callerName = players.getOrElse(table.caller) { "" }
    val title = if (table.assafBy >= 0) {
        stringResource(R.string.yaniv_round_assaf, callerName, players.getOrElse(table.assafBy) { "" })
    } else {
        stringResource(R.string.yaniv_round_won, callerName, YanivScoring.handPoints(table.finalHands.getOrElse(table.caller) { emptyList() }))
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
            val hand = table.finalHands.getOrElse(index) { emptyList() }
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
        points = table.points,
        winner = table.winner,
        canContinue = canContinue,
        onNext = onNext
    )
}
