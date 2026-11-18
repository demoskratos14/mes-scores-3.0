package com.aventure.messcores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Le poker avec cartes (voir [PokerSettings.dealCards]) : cartes communes et cartes privées, passage du téléphone entre les
// joueurs d'un même téléphone, abattage automatique et écran d'un téléphone invité (voir [PokerOnline]).

private val WINNER_GREEN = Color(0xFF2E7D32)

/** Nom de la main de valeur [score] (« Paire », « Full »…), dans la langue de l'appli. */
@Composable
private fun pokerHandName(score: Int): String {
    val names = stringResource(R.string.poker_hand_names).split(',')
    return names.getOrElse(if (PokerHands.isRoyal(score)) PokerHands.CATEGORIES else PokerHands.category(score)) { "" }
}

/**
 * Une rangée de cartes : les [cards] face visible, puis des dos de carte jusqu'à [slots] cartes en tout (les cartes communes
 * pas encore retournées).
 */
@Composable
internal fun PokerCardRow(cards: List<Int>, cardWidth: Dp, slots: Int = cards.size) {
    val labels = rememberFrenchLabels()
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until maxOf(slots, cards.size)) {
            val card = cards.getOrNull(i)
            if (card != null) {
                FrenchCardFace(card = card, labels = labels, playable = true, modifier = Modifier.width(cardWidth))
            } else {
                FrenchCardBack(modifier = Modifier.width(cardWidth))
            }
        }
    }
}

/** Les cartes privées d'un joueur, et ce qu'elles font avec les cartes communes déjà retournées (dès le flop). */
@Composable
internal fun PokerHoleCardsPanel(cards: List<Int>, board: List<Int>) {
    if (cards.size < 2) return
    CardPanel(spacing = 8) {
        Text(
            text = stringResource(R.string.poker_your_cards),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
        PokerCardRow(cards = cards, cardWidth = 72.dp)
        if (board.size >= 3) {
            Text(
                text = stringResource(R.string.poker_your_hand, pokerHandName(PokerHands.score(cards + board))),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/** Les mains dévoilées à l'abattage : les cartes de chaque joueur encore dans la main et le nom de sa main. */
@Composable
internal fun PokerRevealPanel(game: PokerGame, players: List<String>) {
    val seats = PokerEngine.revealedSeats(game)
    if (seats.isEmpty()) return
    CardPanel(spacing = 10) {
        Text(
            text = stringResource(R.string.poker_reveal_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
        seats.forEach { seat ->
            val cards = game.holeCards.getOrNull(seat).orEmpty()
            val score = PokerEngine.handScore(game, seat)
            val won = game.phase == PokerPhase.HAND_OVER && game.winnings.getOrElse(seat) { 0 } > 0
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PokerCardRow(cards = cards, cardWidth = 38.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = players.getOrElse(seat) { "" },
                        fontWeight = if (won) FontWeight.Bold else FontWeight.Normal,
                        color = if (won) WINNER_GREEN else MaterialTheme.colorScheme.onSurface
                    )
                    if (score != null) {
                        Text(
                            text = pokerHandName(score),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** L'abattage quand l'appli a distribué les cartes : les mains sont dévoilées, un appui règle tous les pots. */
@Composable
internal fun PokerAutoShowdownPanel(game: PokerGame, players: List<String>, onResolve: () -> Unit) {
    PokerRevealPanel(game, players)
    CardPanel(spacing = 10) {
        Text(
            text = stringResource(R.string.poker_showdown_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(R.string.poker_showdown_auto_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onResolve, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.poker_resolve))
        }
    }
}

/**
 * Le tour de parole d'un joueur quand l'appli a distribué les cartes. Si sa place se joue sur un autre téléphone, on attend
 * son action. Sinon le téléphone se passe : ses cartes (et les boutons) n'apparaissent qu'après « Voir mes cartes », et
 * disparaissent dès qu'il a joué.
 */
@Composable
internal fun PokerDealtTurnPanel(viewModel: ScoreViewModel, game: PokerGame, players: List<String>, online: PokerOnline) {
    // Chaque action (donc chaque nouveau joueur à parler) remet la couverture.
    var shown by remember(game.handNumber, game.street, game.log.size) { mutableStateOf(false) }
    val options = PokerEngine.options(game) ?: return
    val seat = options.player
    val name = players.getOrElse(seat) { "" }

    if (online.isRemote(seat)) {
        CardPanel {
            Text(
                text = stringResource(R.string.poker_waiting_for, name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        return
    }
    if (!shown) {
        CardPanel(spacing = 10) {
            Text(
                text = stringResource(R.string.poker_pass_to, name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            Button(onClick = { shown = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.poker_show_cards))
            }
        }
        return
    }
    PokerHoleCardsPanel(cards = game.holeCards.getOrNull(seat).orEmpty(), board = PokerEngine.visibleBoard(game))
    PokerActionPanel(game, players, viewModel.pokerSession.actions())
}

// ---------- Téléphone invité ----------

/**
 * L'écran d'un téléphone invité : la table envoyée par l'organisateur ([PokerOnline.table]), vue de sa place. Il choisit sa
 * place, voit ses cartes (jamais celles des autres) et envoie ses actions à l'organisateur à son tour.
 */
@Composable
internal fun PokerGuestScreen(viewModel: ScoreViewModel, online: PokerOnline) {
    KeepScreenOn()
    val game = online.table
    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(16.dp)) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (game == null) {
                CardPanel {
                    Text(text = stringResource(R.string.poker_guest_waiting), style = MaterialTheme.typography.titleMedium)
                }
            } else {
                PokerGuestContent(viewModel = viewModel, online = online, game = game)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PokerGuestContent(viewModel: ScoreViewModel, online: PokerOnline, game: PokerGame) {
    val players = viewModel.players
    val seat = online.mySeat
    val seated = seat in game.seats.indices
    val board = PokerEngine.visibleBoard(game)

    CardPanel(spacing = 2) {
        Text(
            text = viewModel.gameRules.name,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(R.string.poker_hand_number, game.handNumber),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(
                R.string.poker_blinds_now,
                PokerBlinds.smallBlindAt(game.settings, game.handLevel),
                PokerBlinds.bigBlindAt(game.settings, game.handLevel)
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }

    if (!seated) {
        CardPanel(spacing = 8) {
            Text(
                text = stringResource(R.string.poker_claim_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = stringResource(R.string.poker_claim_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                game.seats.indices.filter { !game.seats[it].out && it !in online.taken }.forEach { index ->
                    FilterChip(
                        selected = false,
                        onClick = { online.claim(index) },
                        label = { Text(players.getOrElse(index) { "" }) }
                    )
                }
            }
        }
    }

    PokerTable(
        game = game,
        players = players,
        colors = viewModel.playerColors,
        board = board,
        onSeatClick = {}
    )

    if (seated) {
        val me = game.seats[seat]
        if (me.out) {
            CardPanel {
                Text(text = stringResource(R.string.poker_status_out), style = MaterialTheme.typography.titleMedium)
            }
        } else if (!me.folded) {
            PokerHoleCardsPanel(cards = game.holeCards.getOrNull(seat).orEmpty(), board = board)
        }
    }

    when (game.phase) {
        PokerPhase.BETTING -> {
            val options = PokerEngine.options(game)
            if (options != null) {
                if (seated && options.player == seat) {
                    PokerActionPanel(game, players, online.actions())
                } else {
                    CardPanel {
                        Text(
                            text = stringResource(R.string.poker_to_act, players.getOrElse(options.player) { "" }),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                }
            }
        }
        PokerPhase.SHOWDOWN -> {
            if (seated) {
                PokerAutoShowdownPanel(game, players) { online.showdown() }
            } else {
                PokerRevealPanel(game, players)
            }
        }
        PokerPhase.HAND_OVER -> {
            PokerRevealPanel(game, players)
            PokerGuestHandOver(game, players, online, seated)
        }
    }

    if (game.log.isNotEmpty()) PokerLog(game, players)

    if (seated) {
        OutlinedButton(onClick = { online.claim(-1) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.poker_leave_seat))
        }
    }
}

/** La fin de main sur un téléphone invité : qui a gagné quoi, le champion, puis la main suivante. */
@Composable
private fun PokerGuestHandOver(game: PokerGame, players: List<String>, online: PokerOnline, seated: Boolean) {
    val champion = game.champion
    CardPanel(spacing = 8) {
        game.winnings.forEachIndexed { index, won ->
            if (won > 0) {
                Text(
                    text = pluralStringResource(R.plurals.poker_hand_won, won, players.getOrElse(index) { "" }, won),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (champion != null) {
            Text(
                text = stringResource(R.string.poker_champion, players.getOrElse(champion) { "" }),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        } else if (seated) {
            Button(onClick = { online.nextHand() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.poker_next_hand))
            }
        }
    }
}
