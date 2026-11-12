package com.aventure.messcores

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
 * L'écran de Flip 7 jouable : il n'y a pas de feuille de scores derrière, tout s'affiche ici (voir [Flip7Section]).
 * « Retour » et la fin de partie quittent la partie ([onBack]).
 */
@Composable
internal fun Flip7Screen(viewModel: ScoreViewModel, onBack: () -> Unit) {
    if (viewModel.gameRules.id != FLIP7_ID) {
        // Aucune partie de Flip 7 ouverte (partie fermée entre-temps) : retour.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val round = remember(viewModel, viewModel.players.size) { viewModel.flip7Round() }
    // Une nouvelle manche ou la fin de la manche : on remonte en haut de la page (le reste du temps la page reste où elle est).
    CardGameScreen(
        viewModel = viewModel,
        onBack = onBack,
        scrollKeys = listOf(round.roundNumber, round.roundOver)
    ) {
        Flip7Section(viewModel = viewModel, round = round, onLeave = onBack)
    }
}

/**
 * Flip 7 tour par tour (voir [Flip7Round]) : l'appli distribue, pioche, applique les cartes Action, détecte les doublons et le
 * Flip 7, compte les points de la manche et les inscrit au tableau des scores. Rien n'est caché : on se passe le tour.
 */
@Composable
internal fun Flip7Section(viewModel: ScoreViewModel, round: Flip7Round, onLeave: () -> Unit) {
    val players = viewModel.players
    val over = viewModel.isGameOver()

    // Une manche qui vient de finir est inscrite tout de suite : quitter ensuite ne la fait pas perdre.
    fun act(action: () -> Unit) {
        action()
        if (round.roundOver) round.settle(viewModel)
    }

    if (over) {
        // La partie est finie (ou reprise déjà finie) : le vainqueur, la dernière manche s'il y en a une, puis la sortie.
        GameOverBanner(players = players, winners = viewModel.winners())
        if (round.roundOver) Flip7RoundResult(round = round, players = players, canContinue = false, onNext = {})
        Flip7Players(viewModel = viewModel, round = round, onStay = {})
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
    Flip7StockCard(round = round, players = players, onHit = { act { round.hit() } })
    if (round.roundOver) {
        Flip7RoundResult(
            round = round,
            players = players,
            canContinue = round.settled,
            onNext = { round.nextRound() }
        )
    } else {
        Flip7PendingCard(round = round, players = players, onChoose = { target -> act { round.chooseTarget(target) } })
    }
    Flip7Players(viewModel = viewModel, round = round, onStay = { act { round.stay() } })
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
 */
@Composable
private fun Flip7PendingCard(round: Flip7Round, players: List<String>, onChoose: (Int) -> Unit) {
    val pending = round.pending ?: return
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
        pending.targets.forEach { target ->
            Button(onClick = { onChoose(target) }, modifier = Modifier.fillMaxWidth()) {
                Text(players.getOrElse(target) { "" })
            }
        }
    }
}

/** La phrase qui raconte le dernier événement de la table ; rien s'il n'y en a pas encore. */
@Composable
private fun Flip7EventText(event: Flip7Event?, round: Flip7Round, players: List<String>) {
    if (event == null) return
    val player = players.getOrElse(event.player) { "" }
    val other = players.getOrElse(event.other) { "" }
    val card = if (event.card >= 0) flip7CardName(event.card) else ""
    val text = when (event.kind) {
        Flip7EventKind.DRAW -> stringResource(R.string.flip7_event_draw, player, card)
        Flip7EventKind.BUST -> stringResource(R.string.flip7_event_bust, player, card)
        Flip7EventKind.SAVED -> stringResource(R.string.flip7_event_saved, player, card)
        Flip7EventKind.FLIP7 -> stringResource(R.string.flip7_event_flip7, player, Flip7Cards.FLIP7_BONUS)
        Flip7EventKind.STAY -> stringResource(R.string.flip7_event_stay, player, round.pointsOf(event.player))
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
private fun Flip7Players(viewModel: ScoreViewModel, round: Flip7Round, onStay: () -> Unit) {
    val totals = viewModel.totals
    // Le joueur dont c'est le tour est toujours le premier (sous la pioche) ; ceux qui ont joué passent en bas de la file.
    val count = viewModel.players.size
    val start = if (round.current in 0 until count && !round.roundOver) round.current else 0
    List(count) { (start + it) % count }.forEach { index ->
        val name = viewModel.players[index]
        val status = round.status.getOrElse(index) { Flip7Status.ACTIVE }
        val mine = index == round.current && !round.roundOver
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
                text = stringResource(R.string.flip7_player_points, totals.getOrElse(index) { 0 }, round.pointsOf(index)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val extras = round.extras.getOrElse(index) { emptyList() }
            val numbers = round.numbers.getOrElse(index) { emptyList() }
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
            if (mine && round.canAct) {
                OutlinedButton(onClick = onStay, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.flip7_stay, round.pointsOf(index)))
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
private fun Flip7StockCard(round: Flip7Round, players: List<String>, onHit: () -> Unit) {
    val count = round.stock.size
    val description = pluralStringResource(R.plurals.card_stock_desc, count, count)
    val hitLabel = stringResource(R.string.flip7_hit)
    CardPanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Flip7CardBack(
                Modifier
                    .width(88.dp)
                    .then(if (round.canAct) Modifier.clickable(onClickLabel = hitLabel, role = Role.Button, onClick = onHit) else Modifier)
                    .semantics { contentDescription = description }
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = stringResource(R.string.huit_stock, count), style = MaterialTheme.typography.labelLarge)
                if (round.canAct) {
                    Text(
                        text = stringResource(R.string.flip7_tap_stock, players.getOrElse(round.current) { "" }),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                Flip7EventText(event = round.lastEvent, round = round, players = players)
            }
        }
    }
}

/** La fin de la manche : qui a fait un Flip 7 s'il y en a un, les points de chacun (déjà inscrits au tableau) et la manche suivante. */
@Composable
private fun Flip7RoundResult(
    round: Flip7Round,
    players: List<String>,
    canContinue: Boolean,
    onNext: () -> Unit
) {
    val title = if (round.flip7Player >= 0) {
        stringResource(R.string.flip7_round_flip7, players.getOrElse(round.flip7Player) { "" }, Flip7Cards.FLIP7_BONUS)
    } else {
        stringResource(R.string.flip7_round_over)
    }
    RoundResultCard(
        title = title,
        players = players,
        points = round.roundPoints(),
        winner = round.winner,
        canContinue = canContinue,
        onNext = onNext
    )
}
