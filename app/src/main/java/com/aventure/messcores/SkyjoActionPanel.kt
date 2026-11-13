package com.aventure.messcores

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Proportions des cartes du Skyjo Action (images découpées dans les photos du jeu). */
private const val SKYJO_RATIO = 192f / 298f

@DrawableRes
private fun skyjoNumberImage(card: Int): Int = when (card) {
    -2 -> R.drawable.skyjo_num_m2
    -1 -> R.drawable.skyjo_num_m1
    0 -> R.drawable.skyjo_num_0
    1 -> R.drawable.skyjo_num_1
    2 -> R.drawable.skyjo_num_2
    3 -> R.drawable.skyjo_num_3
    4 -> R.drawable.skyjo_num_4
    5 -> R.drawable.skyjo_num_5
    6 -> R.drawable.skyjo_num_6
    7 -> R.drawable.skyjo_num_7
    8 -> R.drawable.skyjo_num_8
    9 -> R.drawable.skyjo_num_9
    10 -> R.drawable.skyjo_num_10
    11 -> R.drawable.skyjo_num_11
    12 -> R.drawable.skyjo_num_12
    else -> R.drawable.skyjo_star
}

@DrawableRes
private fun skyjoActionImage(action: SkyjoAction): Int = when (action) {
    SkyjoAction.EXCHANGE -> R.drawable.skyjo_act_exchange
    SkyjoAction.SWAP_OWN -> R.drawable.skyjo_act_swap_own
    SkyjoAction.DRAW_THREE -> R.drawable.skyjo_act_draw3
    SkyjoAction.LOOK -> R.drawable.skyjo_act_look
    SkyjoAction.METEOR -> R.drawable.skyjo_act_meteor
    SkyjoAction.DOUBLE_MOVE -> R.drawable.skyjo_act_double
    SkyjoAction.PROTECTION -> R.drawable.skyjo_act_protect
    SkyjoAction.REACTIVATION -> R.drawable.skyjo_act_reactivate
    SkyjoAction.STEAL -> R.drawable.skyjo_act_steal
}

/** Texte lu par TalkBack pour une carte numérotée. */
@Composable
private fun skyjoCardLabel(card: Int): String =
    if (card == SkyjoActionScoring.STAR) stringResource(R.string.skyjoa_card_star) else stringResource(R.string.skyjoa_card_value, card)

/**
 * L'écran du Skyjo Action jouable : il n'y a pas de feuille de scores derrière, tout s'affiche ici (voir [SkyjoActionSection]).
 * « Retour » et la fin de partie quittent la partie ([onBack]).
 */
@Composable
internal fun SkyjoActionScreen(viewModel: ScoreViewModel, onBack: () -> Unit) {
    if (viewModel.gameRules.id != SKYJO_ACTION_ID) {
        // Aucune partie de Skyjo Action ouverte (partie fermée entre-temps) : retour.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val round = remember(viewModel, viewModel.players.size) { viewModel.skyjoActionRound() }
    CardGameScreen(
        viewModel = viewModel,
        onBack = onBack,
        scrollKeys = listOf(round.actor, round.roundOver, round.roundNumber, round.phase)
    ) {
        SkyjoActionSection(viewModel = viewModel, round = round, onLeave = onBack)
    }
}

/**
 * Skyjo Action tour par tour (voir [SkyjoActionRound]) : l'appli distribue, applique les cartes action, retire les lignes
 * identiques, compte les points de la manche et les inscrit au tableau des scores. Toutes les grilles sont visibles : seule la
 * carte « Regarder » montre un secret, au joueur seul.
 */
@Composable
internal fun SkyjoActionSection(viewModel: ScoreViewModel, round: SkyjoActionRound, onLeave: () -> Unit) {
    val players = viewModel.players
    val over = viewModel.isGameOver()
    val actionNames = stringResource(R.string.skyjoa_action_names).split(',')
    fun actionName(action: SkyjoAction): String = actionNames.getOrElse(action.ordinal) { "" }
    fun playerName(index: Int): String = players.getOrElse(index) { "" }

    var error by remember(round.phase, round.turnNumber, round.roundNumber) { mutableStateOf<Int?>(null) }
    var flipMode by remember(round.phase, round.turnNumber, round.roundNumber) { mutableStateOf(false) }
    var noneMode by remember(round.phase, round.turnNumber, round.roundNumber) { mutableStateOf(false) }
    var chosenThree by remember(round.phase, round.turnNumber, round.roundNumber) { mutableStateOf<Int?>(null) }
    var lookTarget by remember(round.phase, round.turnNumber, round.roundNumber) { mutableStateOf<Pair<Int, Int>?>(null) }

    // Une manche qui vient de finir est inscrite tout de suite : quitter ensuite ne la fait pas perdre.
    LaunchedEffect(round.roundOver, round.roundNumber) {
        if (round.roundOver) round.settle(viewModel)
    }

    ScoresCard(viewModel = viewModel, current = if (over || round.roundOver) -1 else round.actor)

    if (over) {
        GameOverBanner(players = players, winners = viewModel.winners())
        if (round.roundOver) SkyjoRoundResult(round = round, players = players, canContinue = false, onNext = {})
        SkyjoGrids(round = round, players = players, selectable = false, onCell = { _, _ -> }, onSteal = { _, _ -> })
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

    val phase = round.phase
    val actor = playerName(round.actor)

    // ----- Les consignes -----
    CardPanel {
        if (!round.roundOver) {
            Text(
                text = stringResource(R.string.dice_bunco_turn, actor),
                style = MaterialTheme.typography.titleMedium
            )
            val prompt = when (phase) {
                SkyjoPhase.CHOOSE -> stringResource(if (round.finalTurn) R.string.skyjoa_p_choose_final else R.string.skyjoa_p_choose, actor)
                SkyjoPhase.PLACE -> when {
                    flipMode -> stringResource(R.string.skyjoa_p_flip, actor)
                    round.heldFromDiscard -> stringResource(R.string.skyjoa_p_place_discard, actor)
                    else -> stringResource(R.string.skyjoa_p_place_stock, actor)
                }
                SkyjoPhase.STAR_BONUS -> stringResource(R.string.skyjoa_p_star, actor)
                SkyjoPhase.THREE -> when {
                    noneMode -> stringResource(R.string.skyjoa_p_flip, actor)
                    chosenThree != null -> stringResource(R.string.skyjoa_p_three_cell, actor)
                    else -> stringResource(R.string.skyjoa_p_three, actor)
                }
                SkyjoPhase.SWAP -> stringResource(if (round.firstPick == null) R.string.skyjoa_p_swap1 else R.string.skyjoa_p_swap2, actor)
                SkyjoPhase.EXCHANGE -> stringResource(if (round.firstPick == null) R.string.skyjoa_p_exch1 else R.string.skyjoa_p_exch2, actor)
                SkyjoPhase.LOOK -> stringResource(R.string.skyjoa_p_look, actor)
                SkyjoPhase.PEEK -> stringResource(R.string.skyjoa_p_peek_cover, actor)
                SkyjoPhase.METEOR -> stringResource(R.string.skyjoa_p_meteor, playerName(round.meteorQueue.firstOrNull() ?: 0))
                SkyjoPhase.REACTIVATE -> stringResource(R.string.skyjoa_p_react, actor)
                SkyjoPhase.STEAL -> stringResource(R.string.skyjoa_p_steal, actor)
                SkyjoPhase.ROUND_OVER -> ""
            }
            Text(text = prompt, style = MaterialTheme.typography.bodyMedium)
        }
        error?.let { CardErrorText(stringResource(it)) }
        if (round.events.isNotEmpty()) {
            Text(
                text = stringResource(R.string.skyjoa_events_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            round.events.forEach { event ->
                val who = playerName(event.player)
                val text = when (event.kind) {
                    SkyjoEventKind.ACTION_PLAYED -> stringResource(R.string.skyjoa_ev_played, who, event.action?.let { actionName(it) } ?: "")
                    SkyjoEventKind.BLOCKED -> stringResource(R.string.skyjoa_ev_blocked, who, event.action?.let { actionName(it) } ?: "")
                    SkyjoEventKind.LINE_REMOVED -> stringResource(R.string.skyjoa_ev_line, who)
                    SkyjoEventKind.EXTRA_TURN -> stringResource(R.string.skyjoa_ev_extra, who)
                    SkyjoEventKind.END_TRIGGERED -> stringResource(R.string.skyjoa_ev_end, who)
                    SkyjoEventKind.STOLE -> stringResource(R.string.skyjoa_ev_stole, who, playerName(event.other), event.action?.let { actionName(it) } ?: "")
                }
                Text(text = text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    // ----- La table : pioches, défausses et cartes action visibles -----
    val canChoose = phase == SkyjoPhase.CHOOSE
    val canBonus = phase == SkyjoPhase.STAR_BONUS
    CardPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TablePile(
                label = stringResource(R.string.skyjoa_stock, round.stock.size),
                modifier = Modifier.weight(1f)
            ) { m ->
                ImageCard(
                    image = R.drawable.skyjo_back,
                    description = stringResource(R.string.skyjoa_stock, round.stock.size),
                    playable = true,
                    modifier = m,
                    ratio = SKYJO_RATIO,
                    onClick = if (canChoose) {
                        { error = if (round.drawFromStock()) null else R.string.skyjoa_err_move }
                    } else {
                        null
                    }
                )
            }
            TablePile(label = stringResource(R.string.skyjoa_discard_title), modifier = Modifier.weight(1f)) { m ->
                val top = round.discard.lastOrNull()
                if (top == null) {
                    Box(modifier = m.aspectRatio(SKYJO_RATIO))
                } else {
                    ImageCard(
                        image = skyjoNumberImage(top),
                        description = stringResource(R.string.skyjoa_discard_desc, skyjoCardLabel(top)),
                        playable = true,
                        modifier = m,
                        ratio = SKYJO_RATIO,
                        onClick = if (canChoose) {
                            { error = if (round.takeFromDiscard()) null else R.string.skyjoa_err_move }
                        } else {
                            null
                        }
                    )
                }
            }
            TablePile(label = stringResource(R.string.skyjoa_action_stock, round.actionStock.size), modifier = Modifier.weight(1f)) { m ->
                ImageCard(
                    image = R.drawable.skyjo_act_back,
                    description = stringResource(R.string.skyjoa_action_stock, round.actionStock.size),
                    playable = true,
                    modifier = m,
                    ratio = SKYJO_RATIO,
                    onClick = if (canChoose || canBonus) {
                        {
                            val ok = if (canChoose) round.takeActionFromDeck() else round.takeBonusFromDeck()
                            error = if (ok) null else if (round.finalTurn) R.string.skyjoa_err_final else R.string.skyjoa_err_move
                        }
                    } else {
                        null
                    }
                )
            }
            TablePile(label = stringResource(R.string.skyjoa_action_discard), modifier = Modifier.weight(1f)) { m ->
                val top = round.actionDiscard.lastOrNull()
                if (top == null) {
                    Box(modifier = m.aspectRatio(SKYJO_RATIO))
                } else {
                    ImageCard(
                        image = skyjoActionImage(top),
                        description = actionName(top),
                        playable = true,
                        modifier = m,
                        ratio = SKYJO_RATIO
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.skyjoa_action_row),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            round.actionRow.forEachIndexed { index, action ->
                ImageCard(
                    image = skyjoActionImage(action),
                    description = actionName(action),
                    playable = true,
                    modifier = Modifier.weight(1f),
                    ratio = SKYJO_RATIO,
                    onClick = if (canChoose || canBonus) {
                        {
                            val ok = if (canChoose) round.takeActionFromRow(index) else round.takeBonusFromRow(index)
                            error = if (ok) null else if (round.finalTurn) R.string.skyjoa_err_final else R.string.skyjoa_err_move
                        }
                    } else {
                        null
                    }
                )
            }
            repeat(SkyjoActionScoring.ACTION_ROW - round.actionRow.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
        if (canBonus) {
            OutlinedButton(onClick = { round.skipBonus() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.skyjoa_btn_skip_star))
            }
        }
    }

    // ----- La carte en main, les trois cartes, la défausse action, les cartes regardées -----
    when (phase) {
        SkyjoPhase.PLACE -> {
            val card = round.held
            if (card != null) {
                CardPanel {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ImageCard(
                            image = skyjoNumberImage(card),
                            description = skyjoCardLabel(card),
                            playable = true,
                            modifier = Modifier.width(72.dp),
                            ratio = SKYJO_RATIO
                        )
                        if (!round.heldFromDiscard) {
                            Button(onClick = { flipMode = !flipMode }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(if (flipMode) R.string.skyjoa_btn_flip_cancel else R.string.skyjoa_btn_flip))
                            }
                        }
                    }
                }
            }
        }
        SkyjoPhase.THREE -> CardPanel {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                round.three.forEachIndexed { index, card ->
                    ImageCard(
                        image = skyjoNumberImage(card),
                        description = skyjoCardLabel(card),
                        playable = true,
                        modifier = Modifier.weight(1f),
                        ratio = SKYJO_RATIO,
                        selected = chosenThree == index,
                        onClick = {
                            noneMode = false
                            chosenThree = index
                        }
                    )
                }
                repeat(3 - round.three.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
            OutlinedButton(
                onClick = {
                    chosenThree = null
                    noneMode = !noneMode
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (noneMode) R.string.skyjoa_btn_none_cancel else R.string.skyjoa_btn_none))
            }
        }
        SkyjoPhase.REACTIVATE -> CardPanel {
            CardRows(cards = round.reactivatable(), perRow = 4) { index, m ->
                val action = round.actionDiscard[index]
                ImageCard(
                    image = skyjoActionImage(action),
                    description = actionName(action),
                    playable = true,
                    modifier = m,
                    ratio = SKYJO_RATIO,
                    onClick = { error = if (round.reactivate(index)) null else R.string.skyjoa_err_move }
                )
            }
        }
        SkyjoPhase.PEEK -> CardPanel {
            if (!round.peekShown) {
                Button(onClick = { round.showPeek() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.skyjoa_btn_show_peek))
                }
            } else {
                CardRows(cards = round.peek, perRow = 4) { seen, m ->
                    Column(modifier = m, horizontalAlignment = Alignment.CenterHorizontally) {
                        ImageCard(
                            image = skyjoNumberImage(seen.card),
                            description = skyjoCardLabel(seen.card),
                            playable = true,
                            modifier = Modifier.fillMaxWidth(),
                            ratio = SKYJO_RATIO
                        )
                        Text(
                            text = stringResource(
                                R.string.skyjoa_peek_line,
                                playerName(seen.player),
                                seen.cell / round.columns + 1,
                                seen.cell % round.columns + 1
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Button(onClick = { round.closePeek() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.skyjoa_btn_hide_peek))
                }
            }
        }
        else -> Unit
    }

    // ----- Les cartes action du joueur : les jouer ou les défausser -----
    if (canChoose && round.hands.getOrElse(round.current) { emptyList() }.isNotEmpty()) {
        CardPanel {
            Text(
                text = stringResource(R.string.skyjoa_your_actions, actor),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            round.hands[round.current].forEachIndexed { index, held ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ImageCard(
                        image = skyjoActionImage(held.action),
                        description = actionName(held.action),
                        playable = true,
                        modifier = Modifier.width(56.dp),
                        ratio = SKYJO_RATIO
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = actionName(held.action), style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { error = if (round.playAction(index)) null else R.string.skyjoa_err_locked }) {
                                Text(stringResource(R.string.skyjoa_btn_play))
                            }
                            OutlinedButton(onClick = { error = if (round.discardAction(index)) null else R.string.skyjoa_err_locked }) {
                                Text(stringResource(R.string.skyjoa_btn_throw))
                            }
                        }
                    }
                }
            }
        }
    }

    if (round.roundOver) {
        SkyjoRoundResult(round = round, players = players, canContinue = round.settled, onNext = { round.nextRound() })
    }

    // ----- Les grilles -----
    val interactive = phase == SkyjoPhase.PLACE || phase == SkyjoPhase.THREE || phase == SkyjoPhase.SWAP ||
        phase == SkyjoPhase.EXCHANGE || phase == SkyjoPhase.LOOK || phase == SkyjoPhase.METEOR
    SkyjoGrids(
        round = round,
        players = players,
        selectable = interactive,
        onCell = { player, cell ->
            val ok = when (phase) {
                SkyjoPhase.PLACE -> player == round.current &&
                    (if (flipMode) round.discardHeldAndFlip(cell) else round.placeHeld(cell))
                SkyjoPhase.THREE -> player == round.current && (
                    if (noneMode) {
                        round.threeNone(cell)
                    } else {
                        chosenThree?.let { round.threeKeep(it, cell) } ?: false
                    }
                    )
                SkyjoPhase.SWAP -> player == round.current && round.swapPick(cell)
                SkyjoPhase.EXCHANGE -> round.exchangePick(player, cell)
                SkyjoPhase.LOOK -> if (round.grids.getOrNull(player)?.getOrNull(cell) != null) {
                    lookTarget = player to cell
                    true
                } else {
                    false
                }
                SkyjoPhase.METEOR -> round.meteorPick(player, cell)
                else -> false
            }
            error = if (ok) null else R.string.skyjoa_err_move
        },
        onSteal = { player, index ->
            error = if (round.steal(player, index)) null else R.string.skyjoa_err_move
        }
    )

    // « Regarder » : la ligne ou la colonne de la carte touchée.
    lookTarget?.let { (player, cell) ->
        AlertDialog(
            onDismissRequest = { lookTarget = null },
            title = { Text(stringResource(R.string.skyjoa_look_title, playerName(player))) },
            text = { Text(stringResource(R.string.skyjoa_look_text)) },
            confirmButton = {
                TextButton(onClick = {
                    error = if (round.look(player, cell, row = true)) null else R.string.skyjoa_err_no_hidden
                    lookTarget = null
                }) { Text(stringResource(R.string.skyjoa_btn_row)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    error = if (round.look(player, cell, row = false)) null else R.string.skyjoa_err_no_hidden
                    lookTarget = null
                }) { Text(stringResource(R.string.skyjoa_btn_column)) }
            }
        )
    }
}

/** Une pile de la table : la carte puis son nom en dessous. */
@Composable
private fun TablePile(label: String, modifier: Modifier, card: @Composable (Modifier) -> Unit) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        card(Modifier.fillMaxWidth())
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Les grilles de tous les joueurs : celle du joueur qui joue en grand, les autres plus petites. */
@Composable
private fun SkyjoGrids(
    round: SkyjoActionRound,
    players: List<String>,
    selectable: Boolean,
    onCell: (Int, Int) -> Unit,
    onSteal: (Int, Int) -> Unit
) {
    val actionNames = stringResource(R.string.skyjoa_action_names).split(',')
    val stealing = round.phase == SkyjoPhase.STEAL
    for (player in players.indices) {
        val mine = !round.roundOver && player == round.current
        val grid = round.grids.getOrNull(player) ?: continue
        val selected = round.firstPick?.takeIf { it.first == player }?.second
        CardPanel(spacing = 6) {
            Text(
                text = stringResource(R.string.skyjoa_grid_title, players[player]) +
                    if (round.holdsProtection(player)) " " + stringResource(R.string.skyjoa_protected) else "",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Column(
                modifier = Modifier.fillMaxWidth(if (mine) 1f else 0.62f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                grid.chunked(round.columns).forEachIndexed { row, line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        line.forEachIndexed { col, cell ->
                            val index = row * round.columns + col
                            if (cell == null) {
                                Spacer(modifier = Modifier.weight(1f).aspectRatio(SKYJO_RATIO))
                            } else {
                                ImageCard(
                                    image = if (cell.up) skyjoNumberImage(cell.card) else R.drawable.skyjo_back,
                                    description = if (cell.up) skyjoCardLabel(cell.card) else stringResource(R.string.skyjoa_card_down),
                                    playable = true,
                                    modifier = Modifier.weight(1f),
                                    ratio = SKYJO_RATIO,
                                    selected = selected == index,
                                    onClick = if (selectable) {
                                        { onCell(player, index) }
                                    } else {
                                        null
                                    }
                                )
                            }
                        }
                    }
                }
            }
            val held = round.hands.getOrElse(player) { emptyList() }
            if (held.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    held.forEachIndexed { index, card ->
                        ImageCard(
                            image = skyjoActionImage(card.action),
                            description = actionNames.getOrElse(card.action.ordinal) { "" },
                            playable = true,
                            modifier = Modifier.width(44.dp),
                            ratio = SKYJO_RATIO,
                            onClick = if (stealing && player != round.current) {
                                { onSteal(player, index) }
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }
    }
}

/** La fin de la manche : qui a terminé, doublement éventuel et points de chacun (déjà inscrits au tableau). */
@Composable
private fun SkyjoRoundResult(round: SkyjoActionRound, players: List<String>, canContinue: Boolean, onNext: () -> Unit) {
    val points = round.roundPoints()
    val ender = players.getOrElse(round.endBy) { "" }
    RoundResultCard(
        title = stringResource(if (round.enderDoubled()) R.string.skyjoa_round_doubled else R.string.skyjoa_round_over, ender),
        players = players,
        points = points,
        winner = points.indices.minByOrNull { points[it] } ?: -1,
        canContinue = canContinue,
        onNext = onNext
    )
}
