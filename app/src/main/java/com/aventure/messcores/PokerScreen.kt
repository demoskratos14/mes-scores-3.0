package com.aventure.messcores

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/** Nombre d'actions récentes montrées sous la table. */
private const val LOG_LINES = 6

private val FELT_GREEN = Color(0xFF1B5E20)
private val FELT_EDGE = Color(0xFF0B3D12)
private val WIN_GREEN = Color(0xFF2E7D32)

/**
 * Feuille de poker : les joueurs sont assis autour d'une table ronde (bouton, petite et grosse blind
 * désignés), et les enchères se jouent tour par tour : suivre, passer, relancer (avec la relance
 * minimum), tapis. Le pot et les pots annexes sont calculés ; à l'abattage on désigne le gagnant de chaque pot.
 */
@Composable
fun PokerScreen(viewModel: ScoreViewModel, historyRepository: GameHistoryRepository) {
    // Partie fermée pendant la transition de retour : rien à afficher.
    val game = viewModel.poker ?: return

    KeepScreenOn()
    val context = LocalContext.current
    val players = viewModel.players

    // Horloge : se rafraîchit chaque demi-seconde tant que le minuteur de blinds tourne.
    val running = viewModel.pokerClockRunning
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        now = System.currentTimeMillis()
        while (running) {
            delay(500)
            now = System.currentTimeMillis()
        }
    }
    val elapsed = viewModel.pokerElapsedMs(now)
    val level = PokerBlinds.levelAt(game.settings, elapsed)

    var optionsIndex by remember { mutableStateOf<Int?>(null) }
    var justSaved by remember { mutableStateOf(false) }
    LaunchedEffect(justSaved) {
        if (justSaved) {
            delay(2000)
            justSaved = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(16.dp)) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PokerHeader(
                title = viewModel.gameRules.name,
                game = game,
                level = level,
                elapsed = elapsed,
                running = running,
                onToggleClock = { viewModel.togglePokerClock() },
                onNextLevel = { viewModel.pokerSkipToNextLevel() }
            )

            PokerTable(
                game = game,
                players = players,
                colors = viewModel.playerColors,
                onSeatClick = { optionsIndex = it }
            )

            when (game.phase) {
                PokerPhase.BETTING -> PokerActionPanel(viewModel, game, players)
                PokerPhase.SHOWDOWN -> PokerShowdownPanel(viewModel, game, players)
                PokerPhase.HAND_OVER -> PokerHandOverPanel(viewModel, game, players)
            }

            if (game.log.isNotEmpty()) PokerLog(game, players)
        }

        Spacer(modifier = Modifier.height(12.dp))
        ScoreActionBar(
            justSaved = justSaved,
            onSave = {
                viewModel.saveToJournal(historyRepository)
                justSaved = true
            },
            onShare = { ResultText.share(context, viewModel.shareText(context)) },
            onUndo = { viewModel.undo() },
            canUndo = viewModel.canUndo,
            onRedo = { viewModel.redo() },
            canRedo = viewModel.canRedo
        )
    }

    optionsIndex?.let { index ->
        PlayerOptionsDialog(viewModel, index) { optionsIndex = null }
    }
}

// ---------- En-tête : blinds, minuteur ----------

@Composable
private fun PokerHeader(
    title: String,
    game: PokerGame,
    level: Int,
    elapsed: Long,
    running: Boolean,
    onToggleClock: () -> Unit,
    onNextLevel: () -> Unit
) {
    val settings = game.settings
    val handSmall = PokerBlinds.smallBlindAt(settings, game.handLevel)
    val handBig = PokerBlinds.bigBlindAt(settings, game.handLevel)
    val handAnte = PokerBlinds.anteAt(settings, game.handLevel)
    val remaining = PokerBlinds.remainingMs(settings, elapsed)

    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = stringResource(R.string.poker_hand_number, game.handNumber),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (remaining != null) {
                    IconButton(onClick = onToggleClock) {
                        Icon(
                            imageVector = if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(
                                if (running) R.string.poker_clock_pause else R.string.poker_clock_resume
                            )
                        )
                    }
                    IconButton(onClick = onNextLevel) {
                        Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.poker_next_level))
                    }
                }
            }

            Text(
                text = if (handAnte > 0) {
                    stringResource(R.string.poker_blinds_now_ante, handSmall, handBig, handAnte)
                } else {
                    stringResource(R.string.poker_blinds_now, handSmall, handBig)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = when {
                    remaining == null -> stringResource(R.string.poker_level_fixed, level)
                    running -> stringResource(R.string.poker_level_clock, level, formatBlindClock(remaining))
                    else -> stringResource(R.string.poker_level_clock_paused, level, formatBlindClock(remaining))
                },
                style = MaterialTheme.typography.bodyMedium
            )

            if (remaining != null) {
                val nextSmall = PokerBlinds.smallBlindAt(settings, level + 1)
                val nextBig = PokerBlinds.bigBlindAt(settings, level + 1)
                val nextAnte = PokerBlinds.anteAt(settings, level + 1)
                Text(
                    text = if (nextAnte > 0) {
                        stringResource(R.string.poker_next_blinds_ante, nextSmall, nextBig, nextAnte)
                    } else {
                        stringResource(R.string.poker_next_blinds, nextSmall, nextBig)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Le niveau a monté pendant la main : les nouvelles blinds s'appliquent à la prochaine.
            if (level > game.handLevel) {
                val upSmall = PokerBlinds.smallBlindAt(settings, level)
                val upBig = PokerBlinds.bigBlindAt(settings, level)
                Text(
                    text = stringResource(R.string.poker_level_up, upSmall, upBig),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
    }
}

// ---------- La table ----------

@Composable
private fun PokerTable(
    game: PokerGame,
    players: List<String>,
    colors: List<Color>,
    onSeatClick: (Int) -> Unit
) {
    val n = game.seats.size
    val seatWidth = if (n > 8) 78.dp else 90.dp
    val potChips = if (game.phase == PokerPhase.HAND_OVER) game.winnings.sum() else game.pot

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().aspectRatio(0.92f)) {
        val radiusX = (maxWidth - seatWidth) / 2
        val radiusY = (maxHeight - SEAT_HEIGHT) / 2
        val feltShape = RoundedCornerShape(percent = 50)

        // La table.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.72f)
                .fillMaxHeight(0.72f)
                .clip(feltShape)
                .background(FELT_GREEN)
                .border(4.dp, FELT_EDGE, feltShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.poker_pot, formatChips(potChips)),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(streetLabel(game)),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Les joueurs, dans l'ordre de la table, dans le sens des aiguilles d'une montre depuis le bas.
        for (i in 0 until n) {
            val angle = Math.PI / 2 + 2 * Math.PI * i / n
            val dx = radiusX * cos(angle).toFloat()
            val dy = radiusY * sin(angle).toFloat()
            Box(modifier = Modifier.align(Alignment.Center).offset(x = dx, y = dy)) {
                PokerSeatCard(
                    name = players.getOrElse(i) { "" },
                    color = colors.getOrElse(i) { Color.Gray },
                    seat = game.seats[i],
                    game = game,
                    index = i,
                    width = seatWidth,
                    onClick = { onSeatClick(i) }
                )
            }
        }
    }
}

private val SEAT_HEIGHT = 74.dp

@Composable
private fun PokerSeatCard(
    name: String,
    color: Color,
    seat: PokerSeat,
    game: PokerGame,
    index: Int,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val isTurn = game.phase == PokerPhase.BETTING && game.toAct == index
    val isDealer = !seat.out && game.dealer == index
    val isSmall = seat.inHand && game.smallBlindSeat == index
    val isBig = seat.inHand && game.bigBlindSeat == index
    val won = if (game.phase == PokerPhase.HAND_OVER) game.winnings.getOrElse(index) { 0 } else 0

    val statusText = when {
        seat.out -> stringResource(R.string.poker_status_out)
        seat.folded -> stringResource(R.string.poker_status_folded)
        won > 0 -> stringResource(R.string.poker_won_short, formatChips(won))
        seat.allIn -> stringResource(R.string.poker_status_allin)
        seat.roundBet > 0 -> stringResource(R.string.poker_bet_short, formatChips(seat.roundBet))
        else -> ""
    }

    // Lu par TalkBack : tout ce qu'on voit sur la place du joueur.
    val parts = mutableListOf(name, stringResource(R.string.poker_desc_chips, seat.stack))
    if (statusText.isNotEmpty()) parts.add(statusText)
    if (isDealer) parts.add(stringResource(R.string.poker_dealer))
    if (isSmall) parts.add(stringResource(R.string.poker_small_blind))
    if (isBig) parts.add(stringResource(R.string.poker_big_blind))
    if (isTurn) parts.add(stringResource(R.string.poker_desc_turn))
    val description = parts.joinToString(", ")
    val optionsLabel = stringResource(R.string.score_player_options)

    val alpha = when {
        seat.out -> 0.4f
        seat.folded -> 0.55f
        else -> 1f
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        border = if (isTurn) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .width(width)
            .alpha(alpha)
            .clickable(onClickLabel = optionsLabel, role = Role.Button) { onClick() }
            .semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerColorDot(color = color, size = 14.dp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = formatChips(seat.stack),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1
            )
            Text(
                text = statusText.ifEmpty { " " },
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (won > 0) FontWeight.Bold else FontWeight.Normal,
                color = if (won > 0) WIN_GREEN else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(16.dp)) {
                if (isDealer) SeatBadge(stringResource(R.string.poker_badge_dealer), MaterialTheme.colorScheme.primary)
                if (isSmall) SeatBadge(stringResource(R.string.poker_badge_small), MaterialTheme.colorScheme.secondary)
                if (isBig) SeatBadge(stringResource(R.string.poker_badge_big), MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun SeatBadge(label: String, background: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = contentColorFor(background),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

// ---------- Enchères ----------

@Composable
private fun PokerActionPanel(viewModel: ScoreViewModel, game: PokerGame, players: List<String>) {
    val options = PokerEngine.options(game) ?: return
    val seat = game.seats[options.player]
    val name = players.getOrElse(options.player) { "" }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.poker_to_act, name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.pokerFold() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.poker_action_fold))
                }
                Button(
                    onClick = { if (options.canCheck) viewModel.pokerCheck() else viewModel.pokerCall() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = when {
                            options.canCheck -> stringResource(R.string.poker_action_check)
                            options.callAmount >= seat.stack ->
                                stringResource(R.string.poker_action_call_allin, options.callAmount)
                            else -> stringResource(R.string.poker_action_call, options.callAmount)
                        },
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (options.canRaise) {
                RaiseSection(viewModel, game, options, seat)
            }
        }
    }
}

@Composable
private fun RaiseSection(viewModel: ScoreViewModel, game: PokerGame, options: PokerOptions, seat: PokerSeat) {
    // Le champ repart du minimum à chaque nouveau tour de parole.
    var amountText by remember(game.handNumber, game.street, game.currentBet, options.player) {
        mutableStateOf(options.minRaiseTo.toString())
    }
    val amount = amountText.toIntOrNull()
    val valid = amount != null && amount >= options.minRaiseTo && amount <= options.maxRaiseTo

    // Mise « de la taille du pot » : on suit d'abord, puis on relance du pot ainsi obtenu.
    val potAfterCall = game.pot.toLong() + options.callAmount
    fun potTarget(numerator: Int, denominator: Int): Int =
        (game.currentBet + potAfterCall * numerator / denominator).toInt()
            .coerceIn(options.minRaiseTo, options.maxRaiseTo)

    OutlinedTextField(
        value = amountText,
        onValueChange = { amountText = it.filter { c -> c.isDigit() }.take(9) },
        label = {
            Text(
                stringResource(
                    if (options.isBet) R.string.poker_raise_label_bet else R.string.poker_raise_label_raise
                )
            )
        },
        singleLine = true,
        isError = !valid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = {
            Text(
                if (valid && amount != null) {
                    stringResource(R.string.poker_raise_helper, amount - game.currentBet, options.minRaiseTo)
                } else {
                    stringResource(R.string.poker_raise_range, options.minRaiseTo, options.maxRaiseTo)
                }
            )
        },
        modifier = Modifier.fillMaxWidth()
    )

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        QuickAmount(stringResource(R.string.poker_quick_min)) { amountText = options.minRaiseTo.toString() }
        QuickAmount(stringResource(R.string.poker_quick_half)) { amountText = potTarget(1, 2).toString() }
        QuickAmount(stringResource(R.string.poker_quick_three_quarters)) { amountText = potTarget(3, 4).toString() }
        QuickAmount(stringResource(R.string.poker_quick_pot)) { amountText = potTarget(1, 1).toString() }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { if (amount != null) viewModel.pokerRaiseTo(amount) },
            enabled = valid,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = if (valid && amount != null) {
                    stringResource(
                        if (options.isBet) R.string.poker_action_bet_to else R.string.poker_action_raise_to,
                        amount
                    )
                } else {
                    stringResource(if (options.isBet) R.string.poker_action_bet else R.string.poker_action_raise)
                },
                textAlign = TextAlign.Center
            )
        }
        OutlinedButton(
            onClick = { viewModel.pokerAllIn() },
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(R.string.poker_action_allin, seat.stack),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RowScope.QuickAmount(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        modifier = Modifier.weight(1f)
    ) {
        Text(label, fontSize = 12.sp, maxLines = 1)
    }
}

// ---------- Abattage ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PokerShowdownPanel(viewModel: ScoreViewModel, game: PokerGame, players: List<String>) {
    val pots = remember(game) { PokerEngine.pots(game) }
    // Gagnants choisis pour chaque pot (index du pot -> joueurs) ; réinitialisé à chaque main.
    val choices = remember(game.handNumber) { mutableStateMapOf<Int, Set<Int>>() }
    val needsChoice = pots.indices.filter { !PokerEngine.isAutomatic(pots[it]) }
    val complete = needsChoice.all { !choices[it].isNullOrEmpty() }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.poker_showdown_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            if (needsChoice.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.poker_showdown_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            pots.forEachIndexed { index, pot ->
                val title = if (index == 0) {
                    stringResource(R.string.poker_pot_main, formatChips(pot.amount))
                } else {
                    stringResource(R.string.poker_pot_side, index, formatChips(pot.amount))
                }
                if (PokerEngine.isAutomatic(pot)) {
                    val owner = players.getOrElse(pot.eligible.firstOrNull() ?: -1) { "" }
                    Text(stringResource(R.string.poker_pot_auto, title, owner))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pot.eligible.forEach { player ->
                                val selected = choices[index].orEmpty()
                                FilterChip(
                                    selected = player in selected,
                                    onClick = {
                                        choices[index] = if (player in selected) selected - player else selected + player
                                    },
                                    label = { Text(players.getOrElse(player) { "" }) }
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { viewModel.pokerResolve(pots.indices.map { choices[it]?.toList().orEmpty() }) },
                enabled = complete,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.poker_showdown_validate))
            }
        }
    }
}

// ---------- Fin de main ----------

@Composable
private fun PokerHandOverPanel(viewModel: ScoreViewModel, game: PokerGame, players: List<String>) {
    val champion = game.champion
    // Éliminés à cette main : sortis du tournoi alors qu'ils étaient encore dans la main.
    val knockedOut = game.seats.indices.filter { game.seats[it].out && game.seats[it].inHand }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            game.winnings.forEachIndexed { index, won ->
                if (won > 0) {
                    Text(
                        text = stringResource(R.string.poker_hand_won, players.getOrElse(index) { "" }, won),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            knockedOut.forEach { index ->
                Text(stringResource(R.string.poker_player_eliminated, players.getOrElse(index) { "" }))
            }
            if (champion != null) {
                Text(
                    text = stringResource(R.string.poker_champion, players.getOrElse(champion) { "" }),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Button(onClick = { viewModel.pokerNextHand() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.poker_next_hand))
                }
            }
        }
    }
}

// ---------- Dernières actions ----------

@Composable
private fun PokerLog(game: PokerGame, players: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.poker_log_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() }
            )
            game.log.takeLast(LOG_LINES).reversed().forEach { entry ->
                val name = players.getOrElse(entry.player) { "" }
                val line = when {
                    entry.type == PokerActionType.FOLD -> stringResource(R.string.poker_log_fold, name)
                    entry.type == PokerActionType.CHECK -> stringResource(R.string.poker_log_check, name)
                    entry.allIn -> stringResource(R.string.poker_log_allin, name, entry.amount)
                    entry.type == PokerActionType.CALL -> stringResource(R.string.poker_log_call, name, entry.amount)
                    entry.type == PokerActionType.BET -> stringResource(R.string.poker_log_bet, name, entry.amount)
                    else -> stringResource(R.string.poker_log_raise, name, entry.amount)
                }
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ---------- Utilitaires ----------

private fun streetLabel(game: PokerGame): Int = when (game.phase) {
    PokerPhase.SHOWDOWN -> R.string.poker_street_showdown
    PokerPhase.HAND_OVER -> R.string.poker_street_over
    PokerPhase.BETTING -> when (game.street) {
        PokerStreet.PREFLOP -> R.string.poker_street_preflop
        PokerStreet.FLOP -> R.string.poker_street_flop
        PokerStreet.TURN -> R.string.poker_street_turn
        PokerStreet.RIVER -> R.string.poker_street_river
    }
}

/** Nombre de jetons avec séparateur de milliers selon la langue de l'appli. */
private fun formatChips(value: Int): String = NumberFormat.getIntegerInstance().format(value)

/** Temps restant au format m:ss (ou h:mm:ss), arrondi à la seconde supérieure. */
private fun formatBlindClock(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) + 999L) / 1000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
