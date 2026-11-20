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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * L'écran du Président jouable : il n'y a pas de feuille de scores derrière, tout s'affiche ici.
 *
 * - Sur un seul téléphone, ou sur celui de l'organisateur : [PresidentHostSection], où l'on se passe le téléphone.
 * - Sur un téléphone invité : [PresidentGuestSection], qui affiche la main du joueur à sa place et envoie ses actions à l'organisateur.
 *
 * « Retour » et la fin de partie quittent la partie ([onBack]).
 */
@Composable
internal fun PresidentScreen(
    viewModel: ScoreViewModel,
    session: SyncSession,
    online: PresidentOnline,
    onBack: () -> Unit
) {
    if (viewModel.gameRules.id != PRESIDENT_ID) {
        // Aucune partie de Président ouverte (partie fermée entre-temps) : retour.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val guest = session.role == SyncRole.GUEST && session.isConnected && session.kind == SyncKind.SCORE
    if (guest) {
        val table = online.table
        CardGameScreen(
            viewModel = viewModel,
            onBack = onBack,
            scrollKeys = listOf(table?.current, table?.phase, table?.roundNumber, online.mySeat)
        ) {
            PresidentGuestSection(viewModel = viewModel, online = online)
        }
        return
    }
    val round = remember(viewModel, viewModel.players.size) { viewModel.presidentRound() }
    // Les invités reçoivent leur vue de la table à chaque changement ; une manche finie est inscrite tout de suite.
    LaunchedEffect(round, online) {
        snapshotFlow { round.tableFor(-1) }.collect { online.publish() }
    }
    LaunchedEffect(round, round.roundOver) {
        if (round.roundOver) round.settle(viewModel)
    }
    CardGameScreen(
        viewModel = viewModel,
        onBack = onBack,
        scrollKeys = listOf(round.current, round.revealed, round.phase, round.roundNumber)
    ) {
        PresidentHostSection(viewModel = viewModel, round = round, online = online, onLeave = onBack)
    }
}

/**
 * Le Président tour par tour (voir [PresidentRound]) : l'appli distribue, fait les échanges, vérifie chaque pose, classe les
 * joueurs et inscrit les points de la manche au tableau des scores. Les places que personne n'a prises sur un autre téléphone se
 * jouent ici : le téléphone se passe de joueur en joueur et la main n'apparaît qu'après « Voir ma main ».
 */
@Composable
internal fun PresidentHostSection(viewModel: ScoreViewModel, round: PresidentRound, online: PresidentOnline, onLeave: () -> Unit) {
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
        GameOverBanner(players = players, winners = viewModel.winners())
        if (round.roundOver) PresidentRoundResult(table = table, players = players, canContinue = false, onNext = {})
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

    PresidentTableCard(table = table, labels = labels, players = players)

    if (round.roundOver) {
        PresidentRoundResult(table = table, players = players, canContinue = round.settled, onNext = { round.nextRound() })
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

    PresidentHandCard(
        table = table,
        labels = labels,
        players = players,
        active = true,
        onPlay = { cards -> round.play(cards).also { if (it == PlayCheck.OK) settleIfOver() } },
        onPass = { round.pass() },
        onGive = { cards -> round.giveBack(cards) }
    )
}

/**
 * Le Président sur un téléphone invité : l'invité choisit sa place à la table, voit sa main (et elle seule) en permanence et
 * joue quand c'est son tour ; l'organisateur vérifie et applique ses actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PresidentGuestSection(viewModel: ScoreViewModel, online: PresidentOnline) {
    val players = viewModel.players
    val labels = rememberFrenchLabels()
    val table = online.table
    val seat = online.mySeat

    ScoresCard(viewModel = viewModel, current = if (table == null || table.phase == PresidentPhase.OVER) -1 else table.current)

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

    PresidentTableCard(table = table, labels = labels, players = players)

    if (table.phase == PresidentPhase.OVER) {
        PresidentRoundResult(table = table, players = players, canContinue = table.settled, onNext = { online.next() })
        return
    }
    if (seat < 0) {
        Text(stringResource(R.string.president_seat_hint), style = MaterialTheme.typography.bodyMedium, color = Color.White)
        return
    }

    PresidentHandCard(
        table = table,
        labels = labels,
        players = players,
        active = table.current == seat,
        onPlay = { cards ->
            // On prévient le joueur tout de suite d'une pose qui sera refusée ; l'organisateur la vérifie de toute façon.
            PresidentScoring.checkCards(table.hand, table.top, cards).also { if (it == PlayCheck.OK) online.play(cards) }
        },
        onPass = { online.pass() },
        onGive = { cards ->
            (cards.size == table.giveCount && table.hand.containsAll(cards)).also { if (it) online.give(cards) }
        }
    )
}

@StringRes
private fun roleName(role: PresidentRole): Int = when (role) {
    PresidentRole.PRESIDENT -> R.string.president_role_president
    PresidentRole.VICE_PRESIDENT -> R.string.president_role_vice
    PresidentRole.NEUTRAL -> R.string.president_role_neutral
    PresidentRole.VICE_TROU -> R.string.president_role_vice_trou
    PresidentRole.TROU -> R.string.president_role_trou
}

/** Les points d'une place avec leur signe : « +2 », « 0 », « −1 ». */
private fun signed(points: Int): String = when {
    points > 0 -> "+$points"
    points < 0 -> "−${-points}"
    else -> "0"
}

/**
 * La table : les cartes du pli en cours (qui les a posées), le joueur sauté, les échanges du début de manche et, pour chaque
 * joueur, le nombre de cartes qui lui restent. Rien de secret, sauf les cartes rendues, que seuls les deux joueurs concernés voient.
 */
@Composable
private fun PresidentTableCard(table: PresidentTable, labels: FrenchLabels, players: List<String>) {
    fun name(player: Int) = players.getOrElse(player) { "" }
    CardPanel {
        Text(
            text = stringResource(R.string.president_table_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (table.top.isEmpty()) {
            if (table.phase == PresidentPhase.PLAY) {
                Text(
                    text = stringResource(R.string.president_table_empty, name(table.current)),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            CardRows(table.top) { card, cardModifier ->
                FrenchCardFace(card = card, labels = labels, playable = true, modifier = cardModifier)
            }
            Text(
                text = stringResource(R.string.president_last_by, name(table.topPlayer)),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (table.skipped >= 0) {
            Text(
                text = stringResource(R.string.president_skipped, name(table.skipped)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
        if (table.exchanges.isNotEmpty() && !table.started) {
            Text(
                text = stringResource(R.string.president_exchange_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            table.exchanges.forEach { exchange ->
                Text(
                    text = stringResource(
                        R.string.president_exchange_line,
                        name(exchange.low),
                        exchange.toHigh.joinToString(" ") { labels.face(it) },
                        name(exchange.high)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = when {
                        !exchange.done -> stringResource(R.string.president_exchange_waiting, name(exchange.high))
                        exchange.toLow.isNotEmpty() -> stringResource(
                            R.string.president_exchange_back,
                            name(exchange.high),
                            exchange.toLow.joinToString(" ") { labels.face(it) }
                        )
                        else -> stringResource(R.string.president_exchange_back_hidden, name(exchange.high), name(exchange.low))
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Text(
            text = stringResource(R.string.huit_hands_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        players.forEachIndexed { index, playerName ->
            val place = table.placeOf(index)
            Text(
                text = if (place >= 0) {
                    stringResource(R.string.president_out_line, playerName, place + 1)
                } else {
                    stringResource(R.string.dice_pig_score_line, playerName, table.handSizes.getOrNull(index) ?: 0)
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * La main du joueur de [table] : on touche les cartes à poser (de même valeur), puis « Poser » ; ou on passe si le pli est ouvert.
 * Pendant les échanges, le Président (ou le vice) choisit les cartes à rendre. [active] faux : on voit sa main, mais ce n'est pas
 * son tour. [onPlay] et [onGive] renvoient si l'action est acceptée (sinon un message explique pourquoi).
 */
@Composable
private fun PresidentHandCard(
    table: PresidentTable,
    labels: FrenchLabels,
    players: List<String>,
    active: Boolean,
    onPlay: (List<Int>) -> PlayCheck,
    onPass: () -> Unit,
    onGive: (List<Int>) -> Boolean
) {
    val hand = table.hand
    val exchanging = table.phase == PresidentPhase.EXCHANGE
    val giving = active && exchanging && table.giveCount > 0
    val playing = active && table.phase == PresidentPhase.PLAY
    // Les cartes choisies ; remises à zéro dès que la main ou le tour change.
    var selection by remember(table.current, table.roundNumber, table.phase, hand) { mutableStateOf(emptySet<Int>()) }
    // Le texte (ressource) refusant la dernière action ; effacé dès que la sélection change.
    var error by remember(table.current, table.roundNumber, table.phase, hand, selection) { mutableStateOf<Int?>(null) }
    val currentName = players.getOrElse(table.current) { "" }

    CardPanel(spacing = 10) {
        if (active) {
            Text(text = stringResource(R.string.dice_bunco_turn, currentName), style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = stringResource(R.string.huit_hand_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CardRows(hand) { card, cardModifier ->
            // Toutes les cartes ont le même aspect (aucune aide) : une action refusée l'est par un message.
            FrenchCardFace(
                card = card,
                labels = labels,
                playable = true,
                selected = card in selection,
                modifier = cardModifier,
                onClick = if (giving || playing) {
                    { selection = if (card in selection) selection - card else selection + card }
                } else {
                    null
                }
            )
        }
        when {
            giving -> {
                val low = table.exchanges.firstOrNull { !it.done && it.high == table.current }?.low ?: -1
                Text(
                    text = stringResource(R.string.president_exchange_give, table.giveCount, players.getOrElse(low) { "" }),
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = {
                        if (!onGive(hand.filter { it in selection })) error = R.string.president_give_count
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.president_give))
                }
            }
            playing -> {
                Text(
                    text = stringResource(if (table.top.isEmpty()) R.string.president_hint_lead else R.string.president_hint_follow),
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = {
                        val result = onPlay(hand.filter { it in selection })
                        if (result != PlayCheck.OK) {
                            error = when (result) {
                                PlayCheck.EMPTY -> R.string.president_select_first
                                PlayCheck.WRONG_COUNT -> R.string.president_wrong_count
                                PlayCheck.TOO_WEAK -> R.string.president_too_weak
                                else -> R.string.president_not_set
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.president_play))
                }
                if (table.top.isNotEmpty()) {
                    OutlinedButton(onClick = onPass, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.president_pass))
                    }
                }
            }
            else -> Text(
                text = stringResource(
                    if (exchanging) R.string.president_exchange_waiting else R.string.president_turn_of,
                    currentName
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        error?.let { CardErrorText(stringResource(it)) }
    }
}

/** La fin de la manche : le classement avec le rôle et les points de chacun (déjà inscrits au tableau), puis la manche suivante. */
@Composable
private fun PresidentRoundResult(table: PresidentTable, players: List<String>, canContinue: Boolean, onNext: () -> Unit) {
    CardPanel {
        Text(text = stringResource(R.string.president_round_title), style = MaterialTheme.typography.titleMedium)
        table.order.forEachIndexed { place, player ->
            val role = table.roleOf(player) ?: PresidentRole.NEUTRAL
            Text(
                text = stringResource(
                    R.string.president_result_line,
                    place + 1,
                    players.getOrElse(player) { "" },
                    stringResource(roleName(role)),
                    signed(table.points.getOrElse(player) { 0 })
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (place == 0) FontWeight.Bold else FontWeight.Normal
            )
        }
        if (canContinue) {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.huit_next_round))
            }
        }
    }
}
