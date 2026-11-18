package com.aventure.messcores

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Le nom du contrat, avec sa valeur (« Pas de plis (−2 par pli) »). */
@StringRes
internal fun BarbuContract.labelRes(): Int = when (this) {
    BarbuContract.NO_TRICKS -> R.string.barbu_contract_notricks
    BarbuContract.NO_HEARTS -> R.string.barbu_contract_nohearts
    BarbuContract.NO_QUEENS -> R.string.barbu_contract_noqueens
    BarbuContract.NO_KING -> R.string.barbu_contract_noking
    BarbuContract.NO_LAST -> R.string.barbu_contract_nolast
    BarbuContract.SALAD -> R.string.barbu_contract_salad
    BarbuContract.DOMINO -> R.string.barbu_contract_domino
}

/**
 * L'écran de Barbu jouable : il n'y a pas de feuille de scores derrière, tout s'affiche ici.
 *
 * - Sur un seul téléphone, ou sur celui de l'organisateur : [BarbuHostSection], où l'on se passe le téléphone.
 * - Sur un téléphone invité : [BarbuGuestSection], qui affiche la main du joueur à sa place et envoie ses actions à l'organisateur.
 *
 * « Retour » et la fin de partie quittent la partie ([onBack]) ; [onOpenShare] ouvre le partage entre téléphones.
 */
@Composable
internal fun BarbuScreen(
    viewModel: ScoreViewModel,
    session: SyncSession,
    online: BarbuOnline,
    onOpenShare: () -> Unit,
    onBack: () -> Unit
) {
    if (viewModel.gameRules.id != BARBU_ID) {
        // Aucune partie de Barbu ouverte (partie fermée entre-temps) : retour.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val guest = session.role == SyncRole.GUEST && session.isConnected && session.kind == SyncKind.SCORE
    if (guest) {
        val table = online.table
        CardGameScreen(
            viewModel = viewModel,
            onBack = onBack,
            scrollKeys = listOf(table?.current, table?.phase, table?.roundNumber, table?.roundOver, table?.trickDone, online.mySeat),
            onOpenShare = onOpenShare,
            shareActive = true
        ) {
            BarbuGuestSection(viewModel = viewModel, online = online)
        }
        return
    }
    val round = remember(viewModel, viewModel.players.size) { viewModel.barbuRound() }
    // Les invités reçoivent leur vue de la table à chaque changement ; une donne finie est inscrite tout de suite.
    LaunchedEffect(round, online) {
        snapshotFlow { round.tableFor(-1) }.collect { online.publish() }
    }
    LaunchedEffect(round, round.roundOver) {
        if (round.roundOver) round.settle(viewModel)
    }
    // Un autre joueur, un pli complet, une nouvelle donne ou la main qu'on vient de dévoiler : on remonte en haut de la page.
    CardGameScreen(
        viewModel = viewModel,
        onBack = onBack,
        scrollKeys = listOf(round.current, round.revealed, round.roundOver, round.roundNumber, round.trickDone, round.phase),
        onOpenShare = onOpenShare,
        shareActive = session.isConnected
    ) {
        BarbuHostSection(viewModel = viewModel, round = round, online = online, onLeave = onBack)
    }
}

/** Les joueurs en tête du total (le plus élevé gagne) : ils gagnent la partie finie. */
private fun leaders(viewModel: ScoreViewModel): List<Int> {
    val ranks = viewModel.ranksFor(viewModel.totals)
    return viewModel.players.indices.filter { ranks.getOrElse(it) { 0 } == 1 }
}

/**
 * Barbu tour par tour (voir [BarbuRound]) : l'appli distribue, le donneur annonce son contrat, l'appli vérifie chaque carte posée,
 * désigne qui ramasse chaque pli (ou qui se vide au Domino), compte les points de la donne et les inscrit au tableau des scores.
 * Les places que personne n'a prises sur un autre téléphone se jouent ici : le téléphone se passe de joueur en joueur et la main
 * n'apparaît qu'après « Voir ma main ».
 */
@Composable
internal fun BarbuHostSection(viewModel: ScoreViewModel, round: BarbuRound, online: BarbuOnline, onLeave: () -> Unit) {
    val players = viewModel.players
    val labels = rememberFrenchLabels()
    val remote = online.isRemote(round.current)
    // La main n'est montrée que quand le joueur de ce téléphone l'a demandée ; celle d'un autre téléphone ne vient jamais ici.
    val table = round.tableFor(if (round.revealed && !remote) round.current else -1)

    // Une donne qui vient de finir est inscrite tout de suite : quitter ensuite ne la fait pas perdre.
    fun settleIfOver() {
        if (round.roundOver) round.settle(viewModel)
    }

    ScoresCard(viewModel = viewModel, current = if (round.finished || round.roundOver) -1 else round.current)

    if (round.finished) {
        // La partie est finie (ou reprise déjà finie) : le vainqueur, les points de la dernière donne s'il y en a une, puis la sortie.
        GameOverBanner(players = players, winners = leaders(viewModel))
        if (round.roundOver) BarbuRoundResult(table = table, players = players, canContinue = false, onNext = {})
        Button(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.dice_leave_game))
        }
        return
    }

    BarbuHeader(table = table, players = players)

    if (round.roundOver) {
        BarbuRoundResult(table = table, players = players, canContinue = round.settled, onNext = { round.nextRound() })
        return
    }

    val name = players.getOrElse(round.current) { "" }

    if (round.phase == BarbuPhase.CHOOSING) {
        when {
            remote -> CardPanel {
                Text(stringResource(R.string.president_waiting_remote, name), style = MaterialTheme.typography.bodyMedium)
            }
            !round.revealed -> PassPhoneCard(name = name, onReveal = { round.reveal() })
            else -> BarbuChooseCard(
                table = table,
                labels = labels,
                name = name,
                players = players,
                onChoose = { chosen -> round.chooseContract(chosen) }
            )
        }
        return
    }

    BarbuTableCard(table = table, labels = labels, players = players)

    when {
        round.trickDone -> {
            // Tout le monde voit le pli : celui qui le ramasse, puis le pli suivant.
            val winner = round.trickWinner
            CardPanel {
                Text(
                    text = stringResource(R.string.barbu_trick_won, players.getOrElse(winner) { "" }),
                    style = MaterialTheme.typography.titleMedium
                )
                if (online.isRemote(winner)) {
                    // Le gagnant joue sur son téléphone : c'est lui qui empoche le pli.
                    Text(
                        stringResource(R.string.president_waiting_remote, players.getOrElse(winner) { "" }),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Button(
                        onClick = {
                            if (round.collectTrick()) settleIfOver()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.hearts_continue))
                    }
                }
            }
        }
        remote -> CardPanel {
            // Ce joueur joue sur son téléphone : rien à faire ici que d'attendre.
            Text(stringResource(R.string.president_waiting_remote, name), style = MaterialTheme.typography.bodyMedium)
        }
        !round.revealed -> PassPhoneCard(name = name, onReveal = { round.reveal() })
        else -> BarbuHandCard(
            table = table,
            labels = labels,
            name = name,
            pickKey = round.current,
            active = true,
            onPlay = { card ->
                round.play(card)
                settleIfOver()
            }
        )
    }
}

/**
 * Barbu sur un téléphone invité : l'invité choisit sa place à la table, voit sa main (et elle seule) en permanence, annonce son
 * contrat quand il est donneur et joue quand c'est son tour ; l'organisateur vérifie et applique ses actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BarbuGuestSection(viewModel: ScoreViewModel, online: BarbuOnline) {
    val players = viewModel.players
    val labels = rememberFrenchLabels()
    val table = online.table
    val seat = online.mySeat

    ScoresCard(viewModel = viewModel, current = if (table == null || table.roundOver) -1 else table.current)

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

    if (table.finished) {
        val ranks = viewModel.ranksFor(viewModel.totals)
        GameOverBanner(players = players, winners = players.indices.filter { ranks.getOrElse(it) { 0 } == 1 })
        if (table.roundOver) BarbuRoundResult(table = table, players = players, canContinue = false, onNext = {})
        return
    }

    BarbuHeader(table = table, players = players)

    if (table.roundOver) {
        BarbuRoundResult(table = table, players = players, canContinue = table.settled, onNext = { online.next() })
        return
    }
    if (seat < 0) {
        if (table.phase == BarbuPhase.PLAYING) BarbuTableCard(table = table, labels = labels, players = players)
        Text(stringResource(R.string.president_seat_hint), style = MaterialTheme.typography.bodyMedium, color = Color.White)
        return
    }

    val name = players.getOrElse(seat) { "" }
    if (table.phase == BarbuPhase.CHOOSING) {
        if (table.declarer == seat) {
            BarbuChooseCard(table = table, labels = labels, name = name, players = players, onChoose = { chosen -> online.choose(chosen) })
        } else {
            CardPanel {
                Text(
                    stringResource(R.string.president_waiting_remote, players.getOrElse(table.declarer) { "" }),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        return
    }

    BarbuTableCard(table = table, labels = labels, players = players)

    if (table.trickDone) {
        CardPanel {
            Text(
                text = stringResource(R.string.barbu_trick_won, players.getOrElse(table.trickWinner) { "" }),
                style = MaterialTheme.typography.titleMedium
            )
            if (table.trickWinner == seat) {
                Button(onClick = { online.collect() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.hearts_continue))
                }
            } else {
                Text(
                    stringResource(R.string.president_waiting_remote, players.getOrElse(table.trickWinner) { "" }),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        return
    }

    BarbuHandCard(
        table = table,
        labels = labels,
        name = name,
        pickKey = seat,
        active = table.current == seat,
        // On prévient le joueur tout de suite d'une carte qui sera refusée ; l'organisateur la vérifie de toute façon.
        onPlay = { card -> if (table.isPlayable(card)) online.play(card) }
    )
}

/** Le numéro de la donne, son donneur et son contrat (quand il est annoncé). */
@Composable
private fun BarbuHeader(table: BarbuTable, players: List<String>) {
    Text(
        text = stringResource(R.string.barbu_deal, table.roundNumber, BarbuScoring.totalDeals(table.playerCount)),
        style = MaterialTheme.typography.labelLarge,
        color = Color.White
    )
    val contract = table.contract
    Text(
        text = if (contract != null) {
            stringResource(R.string.barbu_contract_now, players.getOrElse(table.declarer) { "" }, stringResource(contract.labelRes()))
        } else {
            stringResource(R.string.barbu_declarer, players.getOrElse(table.declarer) { "" })
        },
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White
    )
}

/** La table : le pli en cours et les plis ramassés (contrats en plis), ou les cartes posées et l'ordre d'arrivée (Domino). Rien de secret. */
@Composable
private fun BarbuTableCard(table: BarbuTable, labels: FrenchLabels, players: List<String>) {
    CardPanel {
        if (table.contract == BarbuContract.DOMINO) {
            Text(
                text = stringResource(R.string.barbu_domino_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (table.dominoBase == 0) {
                Text(stringResource(R.string.barbu_domino_empty), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    text = stringResource(
                        R.string.barbu_domino_base,
                        labels.rank(BarbuScoring.cardOf(FrenchCards.SPADE, table.dominoBase))
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                for (suit in 0 until FrenchCards.SUITS) {
                    val low = table.dominoLow.getOrElse(suit) { 0 }
                    val high = table.dominoHigh.getOrElse(suit) { 0 }
                    val suitName = labels.suitLabel(suit)
                    val text = when {
                        low == 0 -> stringResource(R.string.barbu_domino_suit_closed, suitName)
                        low == high -> stringResource(R.string.barbu_domino_suit_single, suitName, labels.rank(BarbuScoring.cardOf(suit, low)))
                        else -> stringResource(
                            R.string.barbu_domino_suit_line,
                            suitName,
                            labels.rank(BarbuScoring.cardOf(suit, low)),
                            labels.rank(BarbuScoring.cardOf(suit, high))
                        )
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (frenchSuitColor(suit) == CardRed) CardRed else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (table.skipped.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.barbu_domino_skipped,
                        table.skipped.joinToString(stringResource(R.string.result_and)) { players.getOrElse(it) { "" } }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (table.finishOrder.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.barbu_domino_order),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                table.finishOrder.forEachIndexed { place, player ->
                    Text(
                        text = stringResource(R.string.barbu_domino_place, place + 1, players.getOrElse(player) { "" }),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Text(
                text = stringResource(R.string.barbu_hand_sizes_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            players.forEachIndexed { index, name ->
                Text(
                    text = stringResource(R.string.dice_pig_score_line, name, table.handSizes.getOrElse(index) { 0 }),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            Text(
                text = stringResource(R.string.hearts_trick_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (table.trick.isEmpty()) {
                Text(stringResource(R.string.hearts_trick_empty), style = MaterialTheme.typography.bodyMedium)
            }
            table.trick.forEach { play ->
                Text(
                    text = stringResource(R.string.hearts_trick_line, players.getOrElse(play.player) { "" }, labels.face(play.card)),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (frenchSuitColor(FrenchCards.suit(play.card)) == CardRed) CardRed else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (table.trickDone && play.player == table.trickWinner) FontWeight.Bold else FontWeight.Normal
                )
            }
            Text(
                text = stringResource(R.string.barbu_tricks_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            players.forEachIndexed { index, name ->
                Text(
                    text = stringResource(R.string.dice_pig_score_line, name, table.tricksWon.getOrElse(index) { 0 }),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/** Le donneur voit sa main et annonce un contrat parmi ceux qu'il n'a pas encore joués. */
@Composable
private fun BarbuChooseCard(
    table: BarbuTable,
    labels: FrenchLabels,
    name: String,
    players: List<String>,
    onChoose: (BarbuContract) -> Unit
) {
    val available = table.available(table.declarer)
    CardPanel(spacing = 10) {
        Text(text = stringResource(R.string.dice_bunco_turn, name), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.huit_hand_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CardRows(table.hand) { card, cardModifier ->
            FrenchCardFace(card = card, labels = labels, playable = true, modifier = cardModifier)
        }
        Text(stringResource(R.string.barbu_choose_hint, name), style = MaterialTheme.typography.bodyMedium)
        available.forEach { contract ->
            Button(onClick = { onChoose(contract) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(contract.labelRes()))
            }
        }
        Text(
            text = stringResource(R.string.barbu_remaining_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        players.forEachIndexed { index, player ->
            val left = BarbuContract.entries.count { !table.hasUsed(index, it) }
            Text(
                text = pluralStringResource(R.plurals.barbu_remaining_line, left, player, left),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * La main du joueur de cette vue : il touche une carte jouable à son tour. [active] : c'est à lui d'agir ; sinon sa main reste visible
 * mais on n'y joue pas. [pickKey] : ce qui change quand la main affichée change de joueur.
 */
@Composable
private fun BarbuHandCard(
    table: BarbuTable,
    labels: FrenchLabels,
    name: String,
    pickKey: Int,
    active: Boolean,
    onPlay: (card: Int) -> Unit
) {
    val hand = table.hand
    val domino = table.contract == BarbuContract.DOMINO
    // Vrai après le toucher d'une carte qu'on ne peut pas jouer ; remis à faux dès que la main, le pli ou le tour change.
    var notPlayable by remember(pickKey, table.roundNumber, table.trick.size, hand.size, table.dominoBase) { mutableStateOf(false) }

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
            val tap: () -> Unit = {
                when {
                    !active -> Unit
                    !table.isPlayable(card) -> notPlayable = true
                    else -> {
                        notPlayable = false
                        onPlay(card)
                    }
                }
            }
            FrenchCardFace(card = card, labels = labels, playable = true, modifier = cardModifier, onClick = tap)
        }
        if (notPlayable) {
            CardErrorText(
                stringResource(
                    when {
                        !domino -> R.string.barbu_not_playable
                        table.dominoBase == 0 -> R.string.barbu_not_startable
                        else -> R.string.barbu_not_playable_domino
                    }
                )
            )
        }
        if (!active) {
            Text(stringResource(R.string.hearts_wait_turn), style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = stringResource(
                when {
                    !domino -> R.string.barbu_hint_tricks
                    table.dominoBase == 0 -> R.string.barbu_hint_domino_first
                    else -> R.string.barbu_hint_domino
                }
            ),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** La fin de la donne : les points de chacun (déjà inscrits au tableau) et la donne suivante. */
@Composable
private fun BarbuRoundResult(table: BarbuTable, players: List<String>, canContinue: Boolean, onNext: () -> Unit) {
    val contract = table.contract
    CardPanel {
        Text(
            text = if (contract != null) {
                stringResource(R.string.barbu_round_over, stringResource(contract.labelRes()))
            } else {
                stringResource(R.string.hearts_round_over)
            },
            style = MaterialTheme.typography.titleMedium
        )
        players.forEachIndexed { index, name ->
            Text(
                text = stringResource(R.string.dice_pig_score_line, name, table.points.getOrElse(index) { 0 }),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (canContinue) {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.huit_next_round))
            }
        }
    }
}
