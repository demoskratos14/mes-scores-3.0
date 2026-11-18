package com.aventure.messcores

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * L'écran d'Oh Hell jouable : il n'y a pas de feuille de scores derrière, tout s'affiche ici.
 *
 * - Sur un seul téléphone, ou sur celui de l'organisateur : [OhHellHostSection], où l'on se passe le téléphone.
 * - Sur un téléphone invité : [OhHellGuestSection], qui affiche la main du joueur à sa place et envoie ses actions à l'organisateur.
 *
 * « Retour » et la fin de partie quittent la partie ([onBack]) ; [onOpenShare] ouvre le partage entre téléphones.
 */
@Composable
internal fun OhHellScreen(
    viewModel: ScoreViewModel,
    session: SyncSession,
    online: OhHellOnline,
    onOpenShare: () -> Unit,
    onBack: () -> Unit
) {
    if (viewModel.gameRules.id != OH_HELL_ID) {
        // Aucune partie d'Oh Hell ouverte (partie fermée entre-temps) : retour.
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
            OhHellGuestSection(viewModel = viewModel, online = online)
        }
        return
    }
    val round = remember(viewModel, viewModel.players.size) { viewModel.ohHellRound() }
    // Les invités reçoivent leur vue de la table à chaque changement ; une manche finie est inscrite tout de suite.
    LaunchedEffect(round, online) {
        snapshotFlow { round.tableFor(-1) }.collect { online.publish() }
    }
    LaunchedEffect(round, round.roundOver) {
        if (round.roundOver) round.settle(viewModel)
    }
    // Un autre joueur, un pli complet, une nouvelle manche ou la main qu'on vient de dévoiler : on remonte en haut de la page.
    CardGameScreen(
        viewModel = viewModel,
        onBack = onBack,
        scrollKeys = listOf(round.current, round.revealed, round.roundOver, round.roundNumber, round.trickDone, round.phase),
        onOpenShare = onOpenShare,
        shareActive = session.isConnected
    ) {
        OhHellHostSection(viewModel = viewModel, round = round, online = online, onLeave = onBack)
    }
}

/** Les joueurs en tête (plusieurs en cas d'égalité) : le plus grand total gagne. */
private fun ohHellWinners(totals: List<Int>): List<Int> {
    val best = totals.maxOrNull() ?: return emptyList()
    return totals.indices.filter { totals[it] == best }
}

/**
 * Oh Hell tour par tour (voir [OhHellRound]) : l'appli distribue, retourne l'atout, fait annoncer chacun (le donneur en dernier, avec
 * son annonce interdite), vérifie chaque carte posée, désigne qui ramasse chaque pli, compte les points de la manche et les inscrit au
 * tableau des scores. Les places que personne n'a prises sur un autre téléphone se jouent ici : le téléphone se passe de joueur en
 * joueur et la main n'apparaît qu'après « Voir ma main ».
 */
@Composable
internal fun OhHellHostSection(viewModel: ScoreViewModel, round: OhHellRound, online: OhHellOnline, onLeave: () -> Unit) {
    val players = viewModel.players
    val labels = rememberFrenchLabels()
    val over = viewModel.isGameOver()
    val remote = online.isRemote(round.current)
    // La main n'est montrée que quand le joueur de ce téléphone l'a demandée ; celle d'un autre téléphone ne vient jamais ici.
    val table = round.tableFor(if (round.revealed && !remote) round.current else -1)

    // Une manche qui vient de finir est inscrite tout de suite : quitter ensuite ne la fait pas perdre.
    fun settleIfOver() {
        if (round.roundOver) round.settle(viewModel)
    }

    ScoresCard(viewModel = viewModel, current = if (over || round.roundOver) -1 else round.current)

    if (over) {
        // La partie est finie (dernière manche inscrite, ou partie reprise déjà finie) : le vainqueur, les points de la dernière manche, puis la sortie.
        GameOverBanner(players = players, winners = ohHellWinners(viewModel.totals))
        if (round.roundOver) OhHellRoundResult(table = table, players = players, canContinue = false, onNext = {})
        Button(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.dice_leave_game))
        }
        return
    }

    OhHellRoundHeader(table = table, labels = labels, players = players)

    if (round.roundOver) {
        OhHellRoundResult(table = table, players = players, canContinue = round.settled && !round.finished, onNext = { round.nextRound() })
        return
    }

    val name = players.getOrElse(round.current) { "" }

    if (round.phase == OhHellPhase.BIDDING) {
        OhHellBidsCard(table = table, players = players)
        when {
            // Ce joueur joue sur son téléphone : rien à faire ici que d'attendre son annonce.
            remote -> CardPanel {
                Text(stringResource(R.string.president_waiting_remote, name), style = MaterialTheme.typography.bodyMedium)
            }
            !round.revealed -> PassPhoneCard(name = name, onReveal = { round.reveal() })
            else -> OhHellHandCard(
                table = table,
                labels = labels,
                name = name,
                pickKey = round.current,
                active = true,
                onPlay = {},
                onBid = { value -> round.bid(value) }
            )
        }
        return
    }

    OhHellTableCard(table = table, labels = labels, players = players)

    when {
        round.trickDone -> {
            // Tout le monde voit le pli : celui qui le ramasse, puis le pli suivant.
            val winner = round.trickWinner
            CardPanel {
                Text(
                    text = stringResource(R.string.ohhell_trick_won, players.getOrElse(winner) { "" }),
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
        else -> OhHellHandCard(
            table = table,
            labels = labels,
            name = name,
            pickKey = round.current,
            active = true,
            onPlay = { card -> round.play(card) },
            onBid = {}
        )
    }
}

/**
 * Oh Hell sur un téléphone invité : l'invité choisit sa place à la table, voit sa main (et elle seule) en permanence, annonce et joue
 * quand c'est son tour ; l'organisateur vérifie et applique ses actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OhHellGuestSection(viewModel: ScoreViewModel, online: OhHellOnline) {
    val players = viewModel.players
    val labels = rememberFrenchLabels()
    val table = online.table
    val seat = online.mySeat

    ScoresCard(viewModel = viewModel, current = if (table == null || table.roundOver || table.finished) -1 else table.current)

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
        // La dernière manche est inscrite : le vainqueur (les totaux sont ceux du tableau des scores partagé) et les points de la manche.
        GameOverBanner(players = players, winners = ohHellWinners(viewModel.totals))
        if (table.roundOver) OhHellRoundResult(table = table, players = players, canContinue = false, onNext = {})
        return
    }

    OhHellRoundHeader(table = table, labels = labels, players = players)

    if (table.roundOver) {
        OhHellRoundResult(table = table, players = players, canContinue = table.settled, onNext = { online.next() })
        return
    }

    if (table.phase == OhHellPhase.BIDDING) {
        OhHellBidsCard(table = table, players = players)
    } else {
        OhHellTableCard(table = table, labels = labels, players = players)
    }
    if (seat < 0) {
        Text(stringResource(R.string.president_seat_hint), style = MaterialTheme.typography.bodyMedium, color = Color.White)
        return
    }

    val name = players.getOrElse(seat) { "" }

    if (table.phase == OhHellPhase.PLAYING && table.trickDone) {
        CardPanel {
            Text(
                text = stringResource(R.string.ohhell_trick_won, players.getOrElse(table.trickWinner) { "" }),
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

    OhHellHandCard(
        table = table,
        labels = labels,
        name = name,
        pickKey = seat,
        active = table.current == seat,
        // On prévient le joueur tout de suite d'une carte ou d'une annonce qui sera refusée ; l'organisateur les vérifie de toute façon.
        onPlay = { card -> if (table.isPlayable(card)) online.play(card) },
        onBid = { value -> if (table.isBidAllowed(value)) online.bid(value) }
    )
}

/** L'en-tête de la manche : son numéro, les cartes de chacun, l'atout (la carte retournée) et le donneur (rien de secret). */
@Composable
private fun OhHellRoundHeader(table: OhHellTable, labels: FrenchLabels, players: List<String>) {
    CardPanel {
        Text(
            text = stringResource(R.string.ohhell_round_info, table.roundNumber, table.totalRounds, table.cardCount),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FrenchCardFace(card = table.trump, labels = labels, playable = true, modifier = Modifier.width(56.dp))
            Text(
                text = stringResource(R.string.ohhell_trump, labels.suitLabel(table.trumpSuit)),
                style = MaterialTheme.typography.titleMedium,
                color = if (frenchSuitColor(table.trumpSuit) == CardRed) CardRed else MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = stringResource(R.string.ohhell_dealer, players.getOrElse(table.dealer) { "" }),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** Les annonces pendant le tour d'annonces : celles déjà faites, celui qui annonce en gras, et le total des annonces. */
@Composable
private fun OhHellBidsCard(table: OhHellTable, players: List<String>) {
    CardPanel(spacing = 4) {
        Text(
            text = stringResource(R.string.ohhell_bids_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        players.forEachIndexed { index, name ->
            val bid = table.bids.getOrElse(index) { -1 }
            Text(
                text = if (bid >= 0) stringResource(R.string.ohhell_bid_made, name, bid) else stringResource(R.string.ohhell_bid_pending, name),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (index == table.current) FontWeight.Bold else FontWeight.Normal
            )
        }
        Text(
            text = stringResource(R.string.ohhell_bids_total, table.bidsTotal, table.cardCount),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** La table pendant les plis : le pli en cours, puis l'annonce et les plis réalisés de chacun (rien de secret). */
@Composable
private fun OhHellTableCard(table: OhHellTable, labels: FrenchLabels, players: List<String>) {
    CardPanel {
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
            text = stringResource(R.string.ohhell_tally_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        players.forEachIndexed { index, name ->
            Text(
                text = stringResource(
                    R.string.ohhell_bid_line,
                    name,
                    table.bids.getOrElse(index) { 0 }.coerceAtLeast(0),
                    table.tricksWon.getOrElse(index) { 0 }
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (index == table.current && !table.trickDone) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

/**
 * La main du joueur de cette vue : pendant les annonces, il choisit combien de plis il fera puis confirme (la dernière annonce
 * permise est refusée par un message) ; ensuite il touche une carte à son tour (une carte refusée l'est par un message, aucune carte
 * n'est mise en avant). [active] : c'est à lui d'agir ; sinon sa main reste visible mais on n'y joue pas. [pickKey] : ce qui change
 * quand la main affichée change de joueur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OhHellHandCard(
    table: OhHellTable,
    labels: FrenchLabels,
    name: String,
    pickKey: Int,
    active: Boolean,
    onPlay: (card: Int) -> Unit,
    onBid: (value: Int) -> Unit
) {
    // L'annonce choisie (-1 : pas encore).
    var picked by rememberSaveable(pickKey, table.roundNumber, table.phase) { mutableIntStateOf(-1) }
    val bidding = table.phase == OhHellPhase.BIDDING
    val hand = table.hand
    // Vrai après le toucher d'une carte qu'on ne peut pas jouer ; remis à faux dès que la main, le pli ou le tour change.
    var notPlayable by remember(pickKey, table.roundNumber, table.phase, table.trick.size, hand.size) { mutableStateOf(false) }
    // Vrai après la confirmation d'une annonce interdite ; remis à faux dès qu'on en choisit une autre.
    var bidRefused by remember(pickKey, table.roundNumber, picked) { mutableStateOf(false) }

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
                    bidding || !active -> Unit
                    !table.isPlayable(card) -> notPlayable = true
                    else -> {
                        notPlayable = false
                        onPlay(card)
                    }
                }
            }
            FrenchCardFace(
                card = card,
                labels = labels,
                playable = true,
                modifier = cardModifier,
                onClick = tap
            )
        }
        if (notPlayable) CardErrorText(stringResource(R.string.ohhell_not_playable))

        if (bidding) {
            if (!active) {
                Text(stringResource(R.string.hearts_wait_turn), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(stringResource(R.string.ohhell_bid_hint), style = MaterialTheme.typography.bodyMedium)
                // De 0 au nombre de plis de la manche, 6 par ligne.
                CardRows((0..table.cardCount).toList(), perRow = 6) { value, chipModifier ->
                    FilterChip(
                        selected = picked == value,
                        onClick = { picked = value },
                        label = { Text(value.toString()) },
                        modifier = chipModifier
                    )
                }
                if (bidRefused) CardErrorText(stringResource(R.string.ohhell_bid_forbidden, picked, table.cardCount))
                Button(
                    onClick = { if (table.isBidAllowed(picked)) onBid(picked) else bidRefused = true },
                    enabled = picked >= 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (picked >= 0) stringResource(R.string.ohhell_bid_confirm, picked) else stringResource(R.string.ohhell_bid_choose))
                }
            }
        } else {
            if (!active) {
                Text(stringResource(R.string.hearts_wait_turn), style = MaterialTheme.typography.bodyMedium)
            }
            Text(stringResource(R.string.ohhell_hint), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** La fin de la manche : l'annonce, les plis réalisés et les points de chacun (déjà inscrits au tableau), puis la manche suivante. */
@Composable
private fun OhHellRoundResult(table: OhHellTable, players: List<String>, canContinue: Boolean, onNext: () -> Unit) {
    CardPanel {
        Text(text = stringResource(R.string.hearts_round_over), style = MaterialTheme.typography.titleMedium)
        players.forEachIndexed { index, name ->
            val bid = table.bids.getOrElse(index) { 0 }
            val won = table.tricksWon.getOrElse(index) { 0 }
            Text(
                text = stringResource(R.string.ohhell_result_line, name, bid, won, table.points.getOrElse(index) { 0 }),
                style = MaterialTheme.typography.bodyMedium,
                // En gras : ceux qui ont réussi leur annonce.
                fontWeight = if (bid == won) FontWeight.Bold else FontWeight.Normal
            )
        }
        if (canContinue) {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.huit_next_round))
            }
        }
    }
}
