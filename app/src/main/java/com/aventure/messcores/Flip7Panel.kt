package com.aventure.messcores

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Cartes par ligne devant un joueur : sept nombres différents font un Flip 7 ; un éventuel doublon (qui fait sauter) passe à la ligne. */
private const val FLIP7_CARDS_PER_ROW = 7

/**
 * L'écran de Flip 7 jouable : il n'y a pas de feuille de scores derrière, tout s'affiche ici.
 *
 * - Sur un seul téléphone, ou sur celui de l'organisateur : [Flip7HostSection], où l'on se passe le téléphone.
 * - Sur un téléphone invité : [Flip7GuestSection], qui affiche la table et envoie les actions de l'invité à l'organisateur.
 *
 * « Retour » et la fin de partie quittent la partie ([onBack]) ; [onOpenShare] ouvre le partage entre téléphones.
 */
@Composable
internal fun Flip7Screen(
    viewModel: ScoreViewModel,
    session: SyncSession,
    online: Flip7Online,
    onOpenShare: () -> Unit,
    onBack: () -> Unit
) {
    if (viewModel.gameRules.id != FLIP7_ID) {
        // Aucune partie de Flip 7 ouverte (partie fermée entre-temps) : retour.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val guest = session.role == SyncRole.GUEST && session.isConnected && session.kind == SyncKind.SCORE
    if (guest) {
        val table = online.table
        CardGameScreen(
            viewModel = viewModel,
            onBack = onBack,
            scrollKeys = listOf(table?.roundNumber, table?.roundOver),
            onOpenShare = onOpenShare,
            shareActive = true
        ) {
            Flip7GuestSection(viewModel = viewModel, online = online)
        }
        return
    }
    val round = remember(viewModel, viewModel.players.size) { viewModel.flip7Round() }
    // Les invités reçoivent la table à chaque changement ; une manche finie est inscrite tout de suite.
    LaunchedEffect(round, online) {
        snapshotFlow { round.tableFor() }.collect { online.publish() }
    }
    LaunchedEffect(round, round.roundOver) {
        if (round.roundOver) round.settle(viewModel)
    }
    // Une nouvelle manche ou la fin de la manche : on remonte en haut de la page (le reste du temps la page reste où elle est).
    CardGameScreen(
        viewModel = viewModel,
        onBack = onBack,
        scrollKeys = listOf(round.roundNumber, round.roundOver),
        onOpenShare = onOpenShare,
        shareActive = session.isConnected
    ) {
        Flip7HostSection(viewModel = viewModel, round = round, online = online, onLeave = onBack)
    }
}

/**
 * Flip 7 tour par tour (voir [Flip7Round]) : l'appli distribue, pioche, applique les cartes Action, détecte les doublons et le
 * Flip 7, compte les points de la manche et les inscrit au tableau des scores. Rien n'est caché : on se passe le tour. Les
 * joueurs d'un autre téléphone jouent chez eux : ici on ne touche ni la pioche ni « S'arrêter » à leur place.
 */
@Composable
internal fun Flip7HostSection(viewModel: ScoreViewModel, round: Flip7Round, online: Flip7Online, onLeave: () -> Unit) {
    val players = viewModel.players
    val over = viewModel.isGameOver()
    val table = round.tableFor()
    // Le joueur dont c'est le tour, et celui qui doit choisir une cible, jouent-ils sur ce téléphone ?
    val localTurn = table.current >= 0 && !online.isRemote(table.current)
    val pendingSource = table.pending?.source ?: -1
    val localChoice = pendingSource >= 0 && !online.isRemote(pendingSource)

    // Une manche qui vient de finir est inscrite tout de suite : quitter ensuite ne la fait pas perdre.
    fun act(action: () -> Unit) {
        action()
        if (round.roundOver) round.settle(viewModel)
    }

    if (over) {
        // La partie est finie (ou reprise déjà finie) : le vainqueur, la dernière manche s'il y en a une, puis la sortie.
        GameOverBanner(players = players, winners = viewModel.winners())
        if (round.roundOver) Flip7RoundResult(table = table, players = players, canContinue = false, onNext = {})
        Flip7Players(viewModel = viewModel, table = table, onStay = null)
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

    // La pioche est tout en haut : c'est elle qu'on touche pour piocher.
    Flip7StockCard(table = table, players = players, onHit = if (localTurn) ({ act { round.hit() } }) else null)
    if (round.roundOver) {
        Flip7RoundResult(
            table = table,
            players = players,
            canContinue = round.settled,
            onNext = { round.nextRound() }
        )
    } else {
        Flip7PendingCard(
            table = table,
            players = players,
            onChoose = if (localChoice) ({ target -> act { round.chooseTarget(target) } }) else null
        )
    }
    Flip7Players(viewModel = viewModel, table = table, onStay = if (localTurn) ({ act { round.stay() } }) else null)
}

/**
 * Flip 7 sur un téléphone invité : l'invité choisit sa place à la table, voit toute la table et joue quand c'est son tour
 * (piocher, s'arrêter, choisir la cible de sa carte Action) ; l'organisateur vérifie et applique ses actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Flip7GuestSection(viewModel: ScoreViewModel, online: Flip7Online) {
    val players = viewModel.players
    val table = online.table
    val seat = online.mySeat
    val over = viewModel.isGameOver()

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
        // La partie est finie : le vainqueur et la dernière manche ; l'organisateur n'en distribue plus.
        GameOverBanner(players = players, winners = viewModel.winners())
        if (table.roundOver) Flip7RoundResult(table = table, players = players, canContinue = false, onNext = {})
        Flip7Players(viewModel = viewModel, table = table, onStay = null)
        return
    }

    Text(
        text = stringResource(R.string.huit_round, table.roundNumber),
        style = MaterialTheme.typography.labelLarge,
        color = Color.White
    )

    // L'organisateur vérifie de toute façon que c'est bien à cette place de jouer.
    val myTurn = seat >= 0 && table.current == seat && !table.roundOver
    val myChoice = seat >= 0 && table.pending?.source == seat && !table.roundOver
    Flip7StockCard(table = table, players = players, onHit = if (myTurn) ({ online.hit() }) else null)
    if (table.roundOver) {
        Flip7RoundResult(table = table, players = players, canContinue = table.settled, onNext = { online.next() })
    } else {
        Flip7PendingCard(table = table, players = players, onChoose = if (myChoice) ({ target -> online.choose(target) }) else null)
    }
    if (seat < 0) {
        Text(stringResource(R.string.president_seat_hint), style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
    Flip7Players(viewModel = viewModel, table = table, onStay = if (myTurn) ({ online.stay() }) else null)
}

/** Le nom lisible (et lu par TalkBack) d'une carte de Flip 7. */
@Composable
private fun flip7CardName(card: Int): String = when {
    Flip7Cards.isNumber(card) -> stringResource(R.string.flip7_card_number, card)
    Flip7Cards.isBonus(card) -> stringResource(R.string.flip7_card_plus, Flip7Cards.bonusValue(card))
    card == Flip7Cards.TIMES_2 -> stringResource(R.string.flip7_card_times2)
    card == Flip7Cards.FREEZE -> stringResource(R.string.flip7_card_freeze)
    card == Flip7Cards.FLIP_THREE -> stringResource(R.string.flip7_card_flip_three)
    else -> stringResource(R.string.flip7_card_second_chance)
}

/** Une carte de Flip 7 : son image. [grayed] : grisée (joueur qui a sauté). */
@Composable
private fun Flip7CardFace(card: Int, modifier: Modifier = Modifier, grayed: Boolean = false) {
    ImageCard(
        image = flip7CardImage(card),
        description = flip7CardName(card),
        playable = !grayed,
        modifier = modifier,
        ratio = FLIP7_CARD_RATIO
    )
}

/**
 * Quand une carte Action vient d'être piochée : la carte et les joueurs sur qui elle peut s'appliquer (rien d'affiché sinon).
 * [onChoose] null : ce n'est pas à ce téléphone de choisir, les joueurs ne sont pas proposés.
 */
@Composable
private fun Flip7PendingCard(table: Flip7Table, players: List<String>, onChoose: ((Int) -> Unit)?) {
    val pending = table.pending ?: return
    CardPanel(spacing = 10) {
        val source = players.getOrElse(pending.source) { "" }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Flip7CardFace(card = pending.card, modifier = Modifier.width(64.dp))
            Text(
                text = stringResource(
                    when (pending.choice) {
                        Flip7Choice.FREEZE -> R.string.flip7_choose_freeze
                        Flip7Choice.FLIP_THREE -> R.string.flip7_choose_flip_three
                        Flip7Choice.GIVE_SECOND_CHANCE -> R.string.flip7_choose_give
                    },
                    source
                ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        // Seul celui qui a pioché la carte choisit (sur son téléphone, ou sur celui de l'organisateur) ; les autres regardent.
        if (onChoose != null) {
            pending.targets.forEach { target ->
                Button(onClick = { onChoose(target) }, modifier = Modifier.fillMaxWidth()) {
                    Text(players.getOrElse(target) { "" })
                }
            }
        }
    }
}

/** La phrase qui raconte le dernier événement de la table ; rien s'il n'y en a pas encore. */
@Composable
private fun Flip7EventText(event: Flip7Event?, table: Flip7Table, players: List<String>) {
    if (event == null) return
    val player = players.getOrElse(event.player) { "" }
    val other = players.getOrElse(event.other) { "" }
    val card = if (event.card >= 0) flip7CardName(event.card) else ""
    val text = when (event.kind) {
        Flip7EventKind.DRAW -> stringResource(R.string.flip7_event_draw, player, card)
        Flip7EventKind.BUST -> stringResource(R.string.flip7_event_bust, player, card)
        Flip7EventKind.SAVED -> stringResource(R.string.flip7_event_saved, player, card)
        Flip7EventKind.FLIP7 -> stringResource(R.string.flip7_event_flip7, player, Flip7Cards.FLIP7_BONUS)
        Flip7EventKind.STAY -> stringResource(R.string.flip7_event_stay, player, table.points.getOrElse(event.player) { 0 })
        Flip7EventKind.FREEZE ->
            if (event.player == event.other) stringResource(R.string.flip7_event_freeze_self, player) else stringResource(R.string.flip7_event_freeze, player, other)
        Flip7EventKind.FLIP_THREE ->
            if (event.player == event.other) stringResource(R.string.flip7_event_flip_three_self, player) else stringResource(R.string.flip7_event_flip_three, player, other)
        Flip7EventKind.SECOND_CHANCE_GIVEN -> stringResource(R.string.flip7_event_second_chance_given, player, other)
        Flip7EventKind.SECOND_CHANCE_LOST -> stringResource(R.string.flip7_event_second_chance_lost, player)
        Flip7EventKind.DECK_EMPTY -> stringResource(R.string.flip7_event_deck_empty)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}

/** La table de chaque joueur : son état, ses points, ses cartes Action et Bonus puis ses cartes Nombre (tout est visible). */
@Composable
private fun Flip7Players(viewModel: ScoreViewModel, table: Flip7Table, onStay: (() -> Unit)?) {
    val totals = viewModel.totals
    // Le joueur dont c'est le tour est toujours le premier (sous la pioche) ; ceux qui ont joué passent en bas de la file.
    val count = viewModel.players.size
    val start = if (table.current in 0 until count && !table.roundOver) table.current else 0
    List(count) { (start + it) % count }.forEach { index ->
        val name = viewModel.players[index]
        val status = table.status.getOrElse(index) { Flip7Status.ACTIVE }
        val mine = index == table.current && !table.roundOver
        val busted = status == Flip7Status.BUSTED
        CardPanel(spacing = 6) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                    color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(
                        when (status) {
                            Flip7Status.ACTIVE -> R.string.flip7_status_active
                            Flip7Status.STAYED -> R.string.flip7_status_stayed
                            Flip7Status.FROZEN -> R.string.flip7_status_frozen
                            Flip7Status.BUSTED -> R.string.flip7_status_busted
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (busted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.flip7_player_points, totals.getOrElse(index) { 0 }, table.points.getOrElse(index) { 0 }),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val extras = table.extras.getOrElse(index) { emptyList() }
            val numbers = table.numbers.getOrElse(index) { emptyList() }
            if (extras.isNotEmpty()) {
                CardRows(extras, perRow = FLIP7_CARDS_PER_ROW) { card, cardModifier ->
                    Flip7CardFace(card = card, modifier = cardModifier, grayed = busted)
                }
            }
            if (numbers.isNotEmpty()) {
                CardRows(numbers, perRow = FLIP7_CARDS_PER_ROW) { card, cardModifier ->
                    Flip7CardFace(card = card, modifier = cardModifier, grayed = busted)
                }
            }
            if (mine && table.canAct && onStay != null) {
                OutlinedButton(onClick = onStay, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.flip7_stay, table.points.getOrElse(index) { 0 }))
                }
            }
        }
    }
}

/**
 * La pioche, tout en haut : on la touche pour piocher (il n'y a pas de bouton). À côté : le nombre de cartes qui restent, à qui
 * c'est de jouer et ce qui vient de se passer sur la table.
 */
@Composable
private fun Flip7StockCard(table: Flip7Table, players: List<String>, onHit: (() -> Unit)?) {
    val count = table.stockSize
    val description = pluralStringResource(R.plurals.card_stock_desc, count, count)
    val hitLabel = stringResource(R.string.flip7_hit)
    CardPanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Flip7CardBack(
                Modifier
                    .width(88.dp)
                    .then(if (table.canAct && onHit != null) Modifier.clickable(onClickLabel = hitLabel, role = Role.Button, onClick = onHit) else Modifier)
                    .semantics { contentDescription = description }
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = stringResource(R.string.huit_stock, count), style = MaterialTheme.typography.labelLarge)
                if (table.canAct) {
                    // Touche la pioche seulement si le joueur joue sur ce téléphone ; sinon on dit juste à qui c'est de jouer.
                    Text(
                        text = stringResource(
                            if (onHit != null) R.string.flip7_tap_stock else R.string.president_turn_of,
                            players.getOrElse(table.current) { "" }
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                Flip7EventText(event = table.lastEvent, table = table, players = players)
            }
        }
    }
}

/** La fin de la manche : qui a fait un Flip 7 s'il y en a un, les points de chacun (déjà inscrits au tableau) et la manche suivante. */
@Composable
private fun Flip7RoundResult(
    table: Flip7Table,
    players: List<String>,
    canContinue: Boolean,
    onNext: () -> Unit
) {
    val title = if (table.flip7Player >= 0) {
        stringResource(R.string.flip7_round_flip7, players.getOrElse(table.flip7Player) { "" }, Flip7Cards.FLIP7_BONUS)
    } else {
        stringResource(R.string.flip7_round_over)
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
