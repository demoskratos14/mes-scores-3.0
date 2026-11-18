package com.aventure.messcores

import android.content.res.Resources
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * L'écran de Tarot jouable : il n'y a pas de feuille de scores à saisir, tout s'affiche ici (les manches s'inscrivent toutes seules
 * dans l'historique, comme la saisie de la feuille de Tarot).
 *
 * - Sur un seul téléphone, ou sur celui de l'organisateur : [TarotHostSection], où l'on se passe le téléphone.
 * - Sur un téléphone invité : [TarotGuestSection], qui affiche la main du joueur à sa place et envoie ses actions à l'organisateur.
 *
 * « Retour » quitte la partie ([onBack]) ; [onOpenShare] ouvre le partage entre téléphones.
 */
@Composable
internal fun TarotScreen(
    viewModel: ScoreViewModel,
    session: SyncSession,
    online: TarotOnline,
    onOpenShare: () -> Unit,
    onBack: () -> Unit
) {
    if (viewModel.gameRules.id != TAROT_ID) {
        // Aucune partie de Tarot ouverte (partie fermée entre-temps) : retour.
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
            TarotGuestSection(viewModel = viewModel, online = online)
        }
        return
    }
    val round = remember(viewModel, viewModel.players.size) { viewModel.tarotRound() }
    val resources = LocalResources.current
    // Le libellé de la manche dans l'historique dépend de la langue : seul l'écran la connaît.
    SideEffect {
        round.labeler = { outcome, attackers -> tarotLabel(resources, viewModel.players, outcome, attackers) }
    }
    // Les invités reçoivent leur vue de la table à chaque changement ; une manche finie est inscrite tout de suite.
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
        scrollKeys = listOf(round.current, round.revealed, round.roundOver, round.roundNumber, round.trickDone, round.phase, round.dealer),
        onOpenShare = onOpenShare,
        shareActive = session.isConnected
    ) {
        TarotHostSection(viewModel = viewModel, round = round, online = online, onLeave = onBack)
    }
}

/** Le libellé d'une manche de Tarot dans l'historique : les attaquants, le contrat, les bouts, les points et la réussite. */
internal fun tarotLabel(resources: Resources, players: List<String>, outcome: TarotOutcome, attackers: Set<Int>): String {
    val team = attackers.joinToString(" + ") { players.getOrElse(it) { "" } }
    val bid = resources.getString(
        when (outcome.contract) {
            TarotBid.PETITE -> R.string.tarot_bid_petite
            TarotBid.GARDE -> R.string.tarot_bid_garde
            TarotBid.GARDE_SANS -> R.string.tarot_bid_garde_sans
            else -> R.string.tarot_bid_garde_contre
        }
    )
    val slam = when {
        outcome.slamSucceeded -> resources.getString(R.string.tarot_label_slam)
        outcome.defenseSlam -> resources.getString(R.string.tarot_label_defense_slam)
        else -> ""
    }
    return resources.getString(
        R.string.tarot_label,
        team,
        bid,
        resources.getQuantityString(R.plurals.tarot_bouts, outcome.bouts, outcome.bouts),
        outcome.points,
        resources.getString(if (outcome.success) R.string.tarot_label_success else R.string.tarot_label_failed),
        slam
    )
}

/** Ce que le joueur de cette vue peut faire ; l'organisateur l'applique lui-même, l'invité l'envoie à l'organisateur. */
private class TarotActions(
    val bid: (Int) -> Unit,
    val call: (Int) -> Unit,
    val discard: (List<Int>, Boolean) -> Unit,
    val handful: () -> Unit,
    val play: (Int) -> Unit,
    val collect: () -> Unit
)

/**
 * Tarot tour par tour (voir [TarotRound]) : l'appli distribue, fait parler chacun, montre le chien, vérifie chaque carte posée,
 * désigne qui ramasse chaque pli, compte les bouts et les points (petit au bout, poignées et chelem compris) et inscrit la manche
 * dans l'historique. Les places que personne n'a prises sur un autre téléphone se jouent ici : le téléphone se passe de joueur en
 * joueur et la main n'apparaît qu'après « Voir ma main ».
 */
@Composable
internal fun TarotHostSection(viewModel: ScoreViewModel, round: TarotRound, online: TarotOnline, onLeave: () -> Unit) {
    val players = viewModel.players
    val namer = rememberTarotNamer()
    val over = viewModel.isGameOver()
    val remote = online.isRemote(round.current)
    // La main n'est montrée que quand le joueur de ce téléphone l'a demandée ; celle d'un autre téléphone ne vient jamais ici.
    val handShown = round.revealed && !remote
    val table = round.tableFor(if (handShown) round.current else -1)

    // Une manche qui vient de finir est inscrite tout de suite : quitter ensuite ne la fait pas perdre.
    fun settleIfOver() {
        if (round.roundOver) round.settle(viewModel)
    }

    ScoresCard(viewModel = viewModel, current = if (over || round.roundOver) -1 else round.current)

    if (over) {
        // La partie est finie (ou reprise déjà finie) : le vainqueur, le résultat de la dernière manche s'il y en a un, puis la sortie.
        GameOverBanner(players = players, winners = viewModel.winners())
        if (round.roundOver) TarotRoundResult(table = table, players = players, canContinue = false, onNext = {})
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

    if (round.roundOver) {
        TarotRoundResult(table = table, players = players, canContinue = round.settled, onNext = { round.nextRound() })
        return
    }

    val name = players.getOrElse(round.current) { "" }
    val actions = TarotActions(
        bid = { level -> round.bidFor(round.current, level) },
        call = { card -> round.callFor(round.current, card) },
        discard = { cards, slam -> round.discardFor(round.current, cards, slam) },
        handful = { round.handfulFor(round.current) },
        play = { card -> round.playFor(round.current, card) },
        collect = { if (round.collect()) settleIfOver() }
    )
    // Quand le pli est complet, personne n'a besoin de sa main : le gagnant l'empoche.
    val needsHand = !round.trickDone
    TarotPlaySection(
        table = table,
        seat = if (handShown) round.current else -1,
        players = players,
        namer = namer,
        actions = actions,
        coverName = if (needsHand && !remote && !handShown) name else null,
        onReveal = { round.reveal() },
        waitingName = if (needsHand && remote) name else null,
        collectHere = round.trickDone && !online.isRemote(round.trickWinner)
    )
}

/**
 * Tarot sur un téléphone invité : l'invité choisit sa place à la table, voit sa main (et elle seule) en permanence, parle, appelle,
 * écarte et joue quand c'est son tour ; l'organisateur vérifie et applique ses actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TarotGuestSection(viewModel: ScoreViewModel, online: TarotOnline) {
    val players = viewModel.players
    val namer = rememberTarotNamer()
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

    Text(
        text = stringResource(R.string.huit_round, table.roundNumber),
        style = MaterialTheme.typography.labelLarge,
        color = Color.White
    )

    if (table.roundOver) {
        TarotRoundResult(table = table, players = players, canContinue = table.settled, onNext = { online.next() })
        return
    }

    val actions = TarotActions(
        bid = { level -> online.bid(level) },
        call = { card -> online.call(card) },
        discard = { cards, slam -> online.discard(cards, slam) },
        handful = { online.handful() },
        // On prévient le joueur tout de suite d'une carte qui sera refusée ; l'organisateur la vérifie de toute façon.
        play = { card -> if (table.isPlayable(card)) online.play(card) },
        collect = { online.collect() }
    )
    TarotPlaySection(
        table = table,
        seat = seat,
        players = players,
        namer = namer,
        actions = actions,
        coverName = null,
        onReveal = {},
        waitingName = null,
        collectHere = table.trickDone && seat >= 0 && table.trickWinner == seat
    )
    if (seat < 0) {
        Text(stringResource(R.string.president_seat_hint), style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}

/**
 * La table et la main telles que les voit le joueur de cette vue : les infos publiques, l'étape de la donne (enchères, appel,
 * chien, pli en cours) et, si [seat] est une place, sa main. [coverName] non null : le téléphone passe à ce joueur, qui doit
 * d'abord demander à voir sa main ([onReveal]). [waitingName] non null : ce joueur joue sur un autre téléphone. [collectHere] : ce
 * téléphone empoche le pli complet.
 */
@Composable
private fun TarotPlaySection(
    table: TarotTable,
    seat: Int,
    players: List<String>,
    namer: TarotNamer,
    actions: TarotActions,
    coverName: String?,
    onReveal: () -> Unit,
    waitingName: String?,
    collectHere: Boolean
) {
    TarotInfoCard(table = table, players = players, namer = namer)

    when (table.phase) {
        TarotPhase.BIDDING -> TarotBidCard(
            table = table,
            players = players,
            canBid = seat >= 0 && table.current == seat,
            onBid = actions.bid
        )
        TarotPhase.CALLING -> {
            if (seat >= 0 && seat == table.taker && table.current == seat) {
                TarotCallCard(options = TarotRules.callOptions(table.hand), namer = namer, onCall = actions.call)
            } else {
                CardPanel {
                    Text(
                        text = stringResource(R.string.tarot_calling_wait, players.getOrElse(table.taker) { "" }),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        TarotPhase.DISCARD -> TarotDogCard(table = table, players = players, namer = namer)
        TarotPhase.PLAYING -> {
            TarotTrickCard(table = table, players = players, namer = namer)
            if (table.trickDone) {
                val winner = players.getOrElse(table.trickWinner) { "" }
                CardPanel {
                    Text(
                        text = stringResource(R.string.tarot_trick_won, winner),
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (collectHere) {
                        Button(onClick = actions.collect, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.hearts_continue))
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.president_waiting_remote, winner),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    when {
        waitingName != null -> CardPanel {
            // Ce joueur joue sur son téléphone : rien à faire ici que d'attendre.
            Text(stringResource(R.string.president_waiting_remote, waitingName), style = MaterialTheme.typography.bodyMedium)
        }
        coverName != null -> PassPhoneCard(name = coverName, onReveal = onReveal)
        seat >= 0 && table.hand.isNotEmpty() -> TarotHandCard(
            table = table,
            namer = namer,
            name = players.getOrElse(seat) { "" },
            seat = seat,
            onPlay = actions.play,
            onDiscard = actions.discard,
            onHandful = actions.handful
        )
    }
}

/** Le nom d'une enchère : passe, petite, garde, garde sans ou garde contre. */
@Composable
private fun bidName(level: Int): String = stringResource(
    when (level) {
        TarotBid.PASS -> R.string.tarot_bid_pass
        TarotBid.PETITE -> R.string.tarot_bid_petite
        TarotBid.GARDE -> R.string.tarot_bid_garde
        TarotBid.GARDE_SANS -> R.string.tarot_bid_garde_sans
        else -> R.string.tarot_bid_garde_contre
    }
)

/** Le nom d'un niveau de poignée : aucune, simple, double ou triple. */
@Composable
private fun handfulName(level: Int): String = stringResource(
    when (level) {
        1 -> R.string.tarot_handful_single
        2 -> R.string.tarot_handful_double
        3 -> R.string.tarot_handful_triple
        else -> R.string.tarot_handful_none
    }
)

/** Ce que tout le monde sait : le contrat et son preneur, la carte appelée, le chelem, les poignées montrées et les atouts écartés. */
@Composable
private fun TarotInfoCard(table: TarotTable, players: List<String>, namer: TarotNamer) {
    if (table.taker < 0 && table.handfuls.isEmpty() && table.discardedTrumps.isEmpty()) return
    CardPanel(spacing = 6) {
        if (table.taker >= 0) {
            Text(
                text = stringResource(R.string.tarot_contract_line, bidName(table.contract), players.getOrElse(table.taker) { "" }),
                style = MaterialTheme.typography.titleMedium
            )
            if (table.slamAnnounced) {
                Text(stringResource(R.string.tarot_slam_line), style = MaterialTheme.typography.bodyMedium)
            }
            if (table.calledCard >= 0) {
                Text(
                    text = stringResource(R.string.tarot_called_line, namer.name(table.calledCard)),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (table.partner >= 0) {
                        stringResource(R.string.tarot_partner_known, players.getOrElse(table.partner) { "" })
                    } else {
                        stringResource(R.string.tarot_partner_unknown)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (table.phase == TarotPhase.PLAYING) {
                Text(
                    text = stringResource(R.string.tarot_tricks_line, table.tricksPlayed, TarotRules.handSize(table.playerCount)),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        table.handfuls.forEach { handful ->
            Text(
                text = stringResource(R.string.tarot_handful_line, players.getOrElse(handful.player) { "" }, handfulName(handful.level)),
                style = MaterialTheme.typography.bodyMedium
            )
            CardRows(handful.trumps, perRow = 8) { card, cardModifier -> TarotCardFace(card = card, namer = namer, modifier = cardModifier) }
        }
        if (table.discardedTrumps.isNotEmpty()) {
            Text(
                text = stringResource(R.string.tarot_discarded_trumps, players.getOrElse(table.taker) { "" }),
                style = MaterialTheme.typography.bodyMedium
            )
            CardRows(table.discardedTrumps, perRow = 8) { card, cardModifier -> TarotCardFace(card = card, namer = namer, modifier = cardModifier) }
        }
    }
}

/** Les enchères : ce que chacun a annoncé, et, si [canBid], les boutons pour parler (passe ou plus haut que la dernière enchère). */
@Composable
private fun TarotBidCard(table: TarotTable, players: List<String>, canBid: Boolean, onBid: (Int) -> Unit) {
    CardPanel {
        Text(
            text = stringResource(R.string.tarot_bidding_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.tarot_dealer, players.getOrElse(table.dealer) { "" }),
            style = MaterialTheme.typography.bodyMedium
        )
        if (table.passedOut) {
            Text(stringResource(R.string.tarot_passed_out), style = MaterialTheme.typography.bodyMedium)
        }
        players.forEachIndexed { index, name ->
            val bid = table.bids.getOrElse(index) { -1 }
            Text(
                text = stringResource(
                    R.string.tarot_line,
                    name,
                    if (bid < 0) stringResource(R.string.tarot_bid_waiting) else bidName(bid)
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (index == table.current) FontWeight.Bold else FontWeight.Normal
            )
        }
        if (canBid) {
            Text(stringResource(R.string.tarot_bid_pick), style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (level in TarotBid.PASS..TarotBid.GARDE_CONTRE) {
                    Button(
                        onClick = { onBid(level) },
                        enabled = level == TarotBid.PASS || level > table.highestBid
                    ) {
                        Text(bidName(level))
                    }
                }
            }
        }
    }
}

/** Le preneur à 5 joueurs choisit la carte qu'il appelle : son propriétaire sera son partenaire. */
@Composable
private fun TarotCallCard(options: List<Int>, namer: TarotNamer, onCall: (Int) -> Unit) {
    var chosen by remember { mutableIntStateOf(-1) }
    CardPanel(spacing = 10) {
        Text(
            text = stringResource(R.string.tarot_call_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(stringResource(R.string.tarot_call_pick), style = MaterialTheme.typography.bodyMedium)
        CardRows(options, perRow = 4) { card, cardModifier ->
            TarotCardFace(
                card = card,
                namer = namer,
                modifier = cardModifier,
                selected = card == chosen,
                onClick = { chosen = card }
            )
        }
        Button(onClick = { onCall(chosen) }, enabled = chosen >= 0, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.tarot_call_confirm))
        }
    }
}

/** Le chien : montré à tous en petite et en garde, caché (et expliqué) en garde sans et en garde contre. */
@Composable
private fun TarotDogCard(table: TarotTable, players: List<String>, namer: TarotNamer) {
    val takerName = players.getOrElse(table.taker) { "" }
    CardPanel {
        Text(
            text = stringResource(R.string.tarot_dog_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (table.contract == TarotBid.GARDE_SANS || table.contract == TarotBid.GARDE_CONTRE) {
            Text(
                text = stringResource(if (table.contract == TarotBid.GARDE_SANS) R.string.tarot_dog_sans else R.string.tarot_dog_contre),
                style = MaterialTheme.typography.bodyMedium
            )
            CardRows(List(TarotRules.dogSize(table.playerCount)) { it }, perRow = 6) { _, cardModifier -> TarotCardBack(cardModifier) }
        } else {
            Text(
                text = stringResource(R.string.tarot_dog_shown, takerName, table.discardCount),
                style = MaterialTheme.typography.bodyMedium
            )
            CardRows(table.dog, perRow = 6) { card, cardModifier -> TarotCardFace(card = card, namer = namer, modifier = cardModifier) }
        }
    }
}

/** Le pli en cours : les cartes posées, avec le nom de celui qui les a jouées (le gagnant en gras quand le pli est complet). */
@Composable
private fun TarotTrickCard(table: TarotTable, players: List<String>, namer: TarotNamer) {
    CardPanel {
        Text(
            text = stringResource(R.string.tarot_trick_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (table.trick.isEmpty()) {
            Text(stringResource(R.string.hearts_trick_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                table.trick.forEach { play ->
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        TarotCardFace(card = play.card, namer = namer, modifier = Modifier.fillMaxWidth())
                        Text(
                            text = players.getOrElse(play.player) { "" },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (table.trickDone && play.player == table.trickWinner) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
                repeat(table.playerCount - table.trick.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * La main du joueur de cette vue. Une carte se touche pour la voir en grand ; à son tour, la toucher une deuxième fois (ou « Jouer »)
 * la pose. Le preneur qui écarte touche les cartes à écarter puis confirme ; il peut annoncer un chelem. Avant sa première carte,
 * un joueur qui a assez d'atouts peut montrer une poignée.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TarotHandCard(
    table: TarotTable,
    namer: TarotNamer,
    name: String,
    seat: Int,
    onPlay: (Int) -> Unit,
    onDiscard: (List<Int>, Boolean) -> Unit,
    onHandful: () -> Unit
) {
    val hand = table.hand
    val myTurn = table.current == seat && !table.trickDone && !table.roundOver
    val discarding = myTurn && table.phase == TarotPhase.DISCARD && table.taker == seat
    val playing = myTurn && table.phase == TarotPhase.PLAYING
    val needed = table.discardCount
    var preview by remember(seat, table.roundNumber, table.phase, hand.size) { mutableIntStateOf(-1) }
    var picked by remember(seat, table.roundNumber, table.phase) { mutableStateOf(listOf<Int>()) }
    var slam by remember(seat, table.roundNumber, table.phase) { mutableStateOf(false) }
    var refused by remember(seat, table.roundNumber, table.phase) { mutableStateOf(false) }

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
        if (discarding) {
            Text(
                text = if (needed > 0) stringResource(R.string.tarot_discard_pick, needed) else stringResource(R.string.tarot_discard_none),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (!discarding && preview in hand) {
            // La carte choisie en grand, avec le bouton pour la poser quand c'est possible.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TarotCardFace(card = preview, namer = namer, modifier = Modifier.width(96.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = namer.name(preview), style = MaterialTheme.typography.titleSmall)
                    if (playing) {
                        Button(onClick = { onPlay(preview) }, enabled = table.isPlayable(preview)) {
                            Text(stringResource(R.string.tarot_play_selected))
                        }
                    }
                }
            }
        }
        if (playing && preview in hand && !table.isPlayable(preview)) {
            CardErrorText(stringResource(R.string.tarot_not_playable))
        }
        CardRows(hand, perRow = if (hand.size > 18) 5 else 4) { card, cardModifier ->
            TarotCardFace(
                card = card,
                namer = namer,
                modifier = cardModifier,
                selected = if (discarding) card in picked else card == preview,
                onClick = {
                    when {
                        discarding -> {
                            refused = false
                            picked = if (card in picked) {
                                picked - card
                            } else if (picked.size < needed) {
                                picked + card
                            } else {
                                picked
                            }
                        }
                        playing && preview == card && table.isPlayable(card) -> onPlay(card)
                        else -> preview = card
                    }
                }
            )
        }

        if (discarding) {
            Text(
                text = stringResource(R.string.tarot_discard_count, picked.size, needed),
                style = MaterialTheme.typography.bodyMedium
            )
            FilterChip(
                selected = slam,
                onClick = { slam = !slam },
                label = { Text(stringResource(R.string.tarot_slam_toggle)) }
            )
            if (refused) CardErrorText(stringResource(R.string.tarot_discard_refused))
            Button(
                onClick = {
                    if (TarotRules.isValidDiscard(picked, hand, needed)) onDiscard(picked, slam) else refused = true
                },
                enabled = picked.size == needed,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (needed > 0) R.string.tarot_discard_confirm else R.string.tarot_start_play))
            }
        } else if (playing) {
            if (table.canDeclareHandful(seat)) {
                OutlinedButton(onClick = onHandful, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.tarot_handful_button, handfulName(table.handfulLevelOf())))
                }
            }
            Text(stringResource(R.string.tarot_play_hint), style = MaterialTheme.typography.bodyMedium)
        } else if (!myTurn && !table.trickDone) {
            Text(stringResource(R.string.hearts_wait_turn), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Un score avec son signe : « +30 », « −30 » ou « 0 ». */
private fun tarotSigned(value: Int): String = when {
    value > 0 -> "+$value"
    value < 0 -> "−${-value}"
    else -> "0"
}

/** La fin de la donne : le contrat réussi ou chuté, les points faits, les primes et le score de chacun (déjà inscrit à l'historique). */
@Composable
private fun TarotRoundResult(table: TarotTable, players: List<String>, canContinue: Boolean, onNext: () -> Unit) {
    val outcome = table.outcome ?: return
    CardPanel {
        Text(
            text = if (outcome.success) {
                pluralStringResource(R.plurals.tarot_contract_success, outcome.difference, outcome.difference)
            } else {
                pluralStringResource(R.plurals.tarot_contract_failed, -outcome.difference, -outcome.difference)
            },
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(
                R.string.tarot_result_points,
                players.getOrElse(outcome.taker) { "" },
                outcome.points,
                pluralStringResource(R.plurals.tarot_bouts, outcome.bouts, outcome.bouts),
                outcome.required
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        if (outcome.partner >= 0) {
            Text(
                text = stringResource(R.string.tarot_partner_known, players.getOrElse(outcome.partner) { "" }),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (outcome.petitAuBout != 0) {
            Text(
                text = stringResource(
                    R.string.tarot_result_petit,
                    stringResource(if (outcome.petitAuBout == 1) R.string.tarot_taker else R.string.tarot_defense)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (outcome.handful > 0) {
            Text(
                text = stringResource(R.string.tarot_result_handful, tarotSigned(TarotRules.handfulPoints(outcome.handful) + outcome.extraHandful)),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        val slamText = when {
            outcome.slamSucceeded && outcome.slamAnnounced -> stringResource(R.string.tarot_hint_announced_ok)
            outcome.slamSucceeded -> stringResource(R.string.tarot_hint_unannounced_ok)
            outcome.defenseSlam -> stringResource(R.string.tarot_hint_defense, DEFENSE_SLAM_PRIZE)
            outcome.slamAnnounced -> stringResource(R.string.tarot_hint_announced_failed)
            else -> null
        }
        if (slamText != null) Text(slamText, style = MaterialTheme.typography.bodyMedium)
        players.forEachIndexed { index, name ->
            val attacker = index == outcome.taker || index == outcome.partner
            Text(
                text = stringResource(R.string.tarot_delta_line, name, tarotSigned(outcome.deltas.getOrElse(index) { 0 })),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (attacker) FontWeight.Bold else FontWeight.Normal
            )
        }
        if (canContinue) {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.huit_next_round))
            }
        }
    }
}
