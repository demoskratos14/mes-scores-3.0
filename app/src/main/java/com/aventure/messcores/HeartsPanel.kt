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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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

/**
 * L'écran de Cœurs jouable : il n'y a pas de feuille de scores derrière, tout s'affiche ici.
 *
 * - Sur un seul téléphone, ou sur celui de l'organisateur : [HeartsHostSection], où l'on se passe le téléphone.
 * - Sur un téléphone invité : [HeartsGuestSection], qui affiche la main du joueur à sa place et envoie ses actions à l'organisateur.
 *
 * « Retour » et la fin de partie quittent la partie ([onBack]).
 */
@Composable
internal fun HeartsScreen(
    viewModel: ScoreViewModel,
    session: SyncSession,
    online: HeartsOnline,
    onBack: () -> Unit
) {
    if (viewModel.gameRules.id != HEARTS_ID) {
        // Aucune partie de Cœurs ouverte (partie fermée entre-temps) : retour.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val guest = session.role == SyncRole.GUEST && session.isConnected && session.kind == SyncKind.SCORE
    if (guest) {
        val table = online.table
        CardGameScreen(
            viewModel = viewModel,
            onBack = onBack,
            scrollKeys = listOf(table?.current, table?.phase, table?.roundNumber, table?.roundOver, table?.trickDone, online.mySeat)
        ) {
            HeartsGuestSection(viewModel = viewModel, online = online)
        }
        return
    }
    val round = remember(viewModel, viewModel.players.size) { viewModel.heartsRound() }
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
        scrollKeys = listOf(round.current, round.revealed, round.roundOver, round.roundNumber, round.trickDone, round.phase)
    ) {
        HeartsHostSection(viewModel = viewModel, round = round, online = online, onLeave = onBack)
    }
}

/**
 * Cœurs tour par tour (voir [HeartsRound]) : l'appli distribue, fait passer 3 cartes, vérifie chaque carte posée, désigne qui
 * ramasse chaque pli, compte les points de la manche (grand chelem compris) et les inscrit au tableau des scores. Les places que
 * personne n'a prises sur un autre téléphone se jouent ici : le téléphone se passe de joueur en joueur et la main n'apparaît
 * qu'après « Voir ma main ».
 */
@Composable
internal fun HeartsHostSection(viewModel: ScoreViewModel, round: HeartsRound, online: HeartsOnline, onLeave: () -> Unit) {
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

    // À la passe, le téléphone va au premier joueur de ce téléphone qui n'a pas encore choisi, sans attendre ceux des autres.
    val localToPass = if (round.phase == HeartsPhase.PASSING && !round.roundOver) {
        (0 until round.playerCount).firstOrNull { !round.hasPassed(it) && !online.isRemote(it) } ?: -1
    } else {
        -1
    }
    LaunchedEffect(localToPass, round.current) {
        if (localToPass >= 0) round.focusPass(localToPass)
    }

    ScoresCard(viewModel = viewModel, current = if (over || round.roundOver) -1 else round.current)

    if (over) {
        // La partie est finie (ou reprise déjà finie) : le vainqueur, les points de la dernière manche s'il y en a une, puis la sortie.
        GameOverBanner(players = players, winners = viewModel.winners())
        if (round.roundOver) HeartsRoundResult(table = table, players = players, canContinue = false, onNext = {})
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
    if (round.passOffset == 0 && !round.roundOver) {
        Text(
            text = stringResource(R.string.hearts_no_pass),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
    }

    if (round.roundOver) {
        HeartsRoundResult(table = table, players = players, canContinue = round.settled, onNext = { round.nextRound() })
        return
    }

    val name = players.getOrElse(round.current) { "" }

    if (round.phase == HeartsPhase.PASSING) {
        when {
            // Tous les joueurs de ce téléphone ont choisi : il ne reste que ceux des autres téléphones.
            localToPass < 0 -> CardPanel {
                Text(stringResource(R.string.hearts_pass_wait), style = MaterialTheme.typography.bodyMedium)
            }
            // Le téléphone n'est pas encore arrivé à ce joueur (une image) : rien à montrer.
            round.current != localToPass -> Unit
            !round.revealed -> PassPhoneCard(name = name, onReveal = { round.reveal() })
            else -> HeartsHandCard(
                table = table,
                labels = labels,
                name = name,
                pickKey = round.current,
                active = true,
                targetName = players.getOrElse(table.passTarget(round.current)) { "" },
                onPlay = {},
                onPass = { cards -> round.confirmPass(cards) }
            )
        }
        return
    }

    HeartsTableCard(table = table, labels = labels, players = players)

    when {
        round.trickDone -> {
            // Tout le monde voit le pli : celui qui le ramasse, ce qu'il rapporte, puis le pli suivant.
            val winner = round.trickWinner
            CardPanel {
                Text(
                    text = stringResource(R.string.hearts_trick_won, players.getOrElse(winner) { "" }, round.trickPoints()),
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
        else -> HeartsHandCard(
            table = table,
            labels = labels,
            name = name,
            pickKey = round.current,
            active = true,
            targetName = "",
            onPlay = { card -> round.play(card) },
            onPass = {}
        )
    }
}

/**
 * Cœurs sur un téléphone invité : l'invité choisit sa place à la table, voit sa main (et elle seule) en permanence, choisit ses
 * cartes à passer quand il veut et joue quand c'est son tour ; l'organisateur vérifie et applique ses actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HeartsGuestSection(viewModel: ScoreViewModel, online: HeartsOnline) {
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

    Text(
        text = stringResource(R.string.huit_round, table.roundNumber),
        style = MaterialTheme.typography.labelLarge,
        color = Color.White
    )
    if (table.passOffset == 0 && !table.roundOver) {
        Text(stringResource(R.string.hearts_no_pass), style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }

    if (table.roundOver) {
        HeartsRoundResult(table = table, players = players, canContinue = table.settled, onNext = { online.next() })
        return
    }
    if (seat < 0) {
        if (table.phase == HeartsPhase.PLAYING) HeartsTableCard(table = table, labels = labels, players = players)
        Text(stringResource(R.string.president_seat_hint), style = MaterialTheme.typography.bodyMedium, color = Color.White)
        return
    }

    val name = players.getOrElse(seat) { "" }
    if (table.phase == HeartsPhase.PASSING) {
        if (table.passed.getOrElse(seat) { false }) {
            // Mes cartes sont parties : je garde ma main sous les yeux en attendant les autres.
            CardPanel {
                Text(stringResource(R.string.hearts_pass_wait), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            HeartsHandCard(
                table = table,
                labels = labels,
                name = name,
                pickKey = seat,
                active = true,
                targetName = players.getOrElse(table.passTarget(seat)) { "" },
                onPlay = {},
                onPass = { cards -> if (cards.size == HeartsScoring.PASS_COUNT && table.hand.containsAll(cards)) online.pass(cards) }
            )
        }
        return
    }

    HeartsTableCard(table = table, labels = labels, players = players)

    if (table.trickDone) {
        CardPanel {
            Text(
                text = stringResource(R.string.hearts_trick_won, players.getOrElse(table.trickWinner) { "" }, table.trickPoints()),
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

    HeartsHandCard(
        table = table,
        labels = labels,
        name = name,
        pickKey = seat,
        active = table.current == seat,
        targetName = "",
        // On prévient le joueur tout de suite d'une carte qui sera refusée ; l'organisateur la vérifie de toute façon.
        onPlay = { card -> if (table.isPlayable(card)) online.play(card) },
        onPass = {}
    )
}

/** La table : le pli en cours, les points ramassés par chacun et l'état des cœurs (rien de secret). */
@Composable
private fun HeartsTableCard(table: HeartsTable, labels: FrenchLabels, players: List<String>) {
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
            text = stringResource(if (table.heartsBroken) R.string.hearts_broken_yes else R.string.hearts_broken_no),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(R.string.hearts_taken_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        players.forEachIndexed { index, name ->
            Text(
                text = stringResource(R.string.dice_pig_score_line, name, table.points.getOrElse(index) { 0 }),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/** Ajoute ou retire [card] de la sélection [picked] (un bit par carte) ; on ne choisit pas plus de 3 cartes. */
private fun togglePick(picked: Long, card: Int, count: Int): Long {
    val bit = 1L shl card
    return when {
        (picked and bit) != 0L -> picked and bit.inv()
        count < HeartsScoring.PASS_COUNT -> picked or bit
        else -> picked
    }
}

/**
 * La main du joueur de cette vue : à la passe, il touche 3 cartes à passer à [targetName] puis confirme ; ensuite il touche une
 * carte jouable à son tour (les cartes qu'il vient de recevoir sont entourées). [active] : c'est à lui d'agir ; sinon sa main
 * reste visible mais on n'y joue pas. [pickKey] : ce qui change quand la main affichée change de joueur.
 */
@Composable
private fun HeartsHandCard(
    table: HeartsTable,
    labels: FrenchLabels,
    name: String,
    pickKey: Int,
    active: Boolean,
    targetName: String,
    onPlay: (card: Int) -> Unit,
    onPass: (cards: List<Int>) -> Unit
) {
    // Les cartes choisies pour la passe : un bit par carte.
    var picked by rememberSaveable(pickKey, table.roundNumber, table.phase) { mutableLongStateOf(0L) }
    val passing = table.phase == HeartsPhase.PASSING
    val hand = table.hand
    val count = java.lang.Long.bitCount(picked)
    val receivedNow = table.received.toSet()
    // Vrai après le toucher d'une carte qu'on ne peut pas jouer ; remis à faux dès que la main, le pli ou le tour change.
    var notPlayable by remember(pickKey, table.roundNumber, table.phase, table.trick.size, hand.size) { mutableStateOf(false) }

    CardPanel(spacing = 10) {
        Text(
            text = stringResource(R.string.dice_bunco_turn, name),
            style = MaterialTheme.typography.titleMedium
        )
        if (passing) {
            Text(stringResource(R.string.hearts_pass_pick, targetName), style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = stringResource(R.string.huit_hand_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CardRows(hand) { card, cardModifier ->
            val isPicked = (picked and (1L shl card)) != 0L
            // Toutes les cartes ont le même aspect (aucune aide) : une carte refusée l'est par un message.
            val tap: () -> Unit = {
                when {
                    passing -> picked = togglePick(picked, card, count)
                    !active -> Unit
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
                selected = if (passing) isPicked else card in receivedNow,
                modifier = cardModifier,
                onClick = tap
            )
        }
        if (notPlayable) CardErrorText(stringResource(R.string.hearts_not_playable))

        if (passing) {
            Text(
                text = stringResource(R.string.hearts_pass_count, count, HeartsScoring.PASS_COUNT),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = {
                    onPass(hand.filter { (picked and (1L shl it)) != 0L })
                },
                enabled = count == HeartsScoring.PASS_COUNT,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.hearts_pass_confirm))
            }
        } else {
            if (!active) {
                Text(stringResource(R.string.hearts_wait_turn), style = MaterialTheme.typography.bodyMedium)
            }
            val first = table.trick.isEmpty() && table.tricksPlayed == 0
            Text(
                text = stringResource(if (first) R.string.hearts_lead_first else R.string.hearts_hint),
                style = MaterialTheme.typography.bodyMedium
            )
            if (receivedNow.isNotEmpty()) {
                Text(stringResource(R.string.hearts_received_hint), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** La fin de la manche : les points de chacun (déjà inscrits au tableau, grand chelem compris) et la manche suivante. */
@Composable
private fun HeartsRoundResult(table: HeartsTable, players: List<String>, canContinue: Boolean, onNext: () -> Unit) {
    val shooter = table.moonShooter
    RoundResultCard(
        title = if (shooter >= 0) {
            stringResource(R.string.hearts_moon, players.getOrElse(shooter) { "" })
        } else {
            stringResource(R.string.hearts_round_over)
        },
        players = players,
        points = table.points,
        winner = shooter,
        canContinue = canContinue,
        onNext = onNext
    )
}
