package com.aventure.messcores

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Hauteurs fixes pour que toutes les colonnes restent alignées entre elles.
private val RANK_ROW_HEIGHT = 40.dp
private val NAME_ROW_HEIGHT = 48.dp
private val TOTAL_ROW_HEIGHT = 56.dp
private val LABEL_COLUMN_WIDTH = 80.dp
private val MIN_PLAYER_COLUMN_WIDTH = 28.dp
private val MAX_PLAYER_COLUMN_WIDTH = 128.dp

/** Renvoie du noir ou du blanc selon le fond donné, pour rester lisible. */
private fun contentColorFor(background: Color): Color {
    val luminance = 0.299 * background.red + 0.587 * background.green + 0.114 * background.blue
    return if (luminance > 0.6) Color.Black else Color.White
}

@Composable
fun ScoreScreen(viewModel: ScoreViewModel, historyRepository: GameHistoryRepository) {
    val players = viewModel.players
    // Totaux et rangs recalculés une seule fois par changement de score, pas une fois par ligne.
    val totals by remember(viewModel) { derivedStateOf { viewModel.totals } }
    val ranks by remember(viewModel) { derivedStateOf { viewModel.ranks } }
    val playerColors = viewModel.playerColors
    val scores = viewModel.scores
    val multipliers = viewModel.gameRules.multipliers
    val hasCustomMultipliers = multipliers.size > 1
    // Un peu plus de hauteur par case quand le sélecteur de règle est affiché.
    val scoreRowHeight = if (hasCustomMultipliers) 92.dp else 72.dp

    KeepScreenOn()
    val context = LocalContext.current

    // Nom complet affiché en popup quand une case de nom tronquée est cliquée.
    var expandedNameIndex by remember { mutableStateOf<Int?>(null) }

    // Un seul état de scroll horizontal partagé entre l'en-tête fixe et le corps
    // du tableau, pour que les colonnes restent alignées quand on défile latéralement.
    val horizontalScrollState = rememberScrollState()

    // Confirmation temporaire affichée après un clic sur "Enregistrer".
    var justSaved by remember { mutableStateOf(false) }
    LaunchedEffect(justSaved) {
        if (justSaved) {
            delay(2000)
            justSaved = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.scoreboard),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = viewModel.gameRules.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    RulesButton(rules = viewModel.gameRules)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (viewModel.isGameOver()) {
            val winners = viewModel.winners()
            Card(
                colors = CardDefaults.cardColors(containerColor = highlightContainer(), contentColor = highlightContent()),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Text(
                    text = if (winners.isEmpty()) {
                        stringResource(R.string.score_game_over)
                    } else {
                        stringResource(
                            if (winners.size > 1) R.string.score_game_over_winners else R.string.score_game_over_winner,
                            winners.joinToString(stringResource(R.string.result_and)) { players[it] }
                        )
                    },
                    modifier = Modifier.padding(12.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = cardSurface()
            ),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val playerColumnWidth: Dp = if (players.isEmpty()) {
                    MAX_PLAYER_COLUMN_WIDTH
                } else {
                    val available = maxWidth - LABEL_COLUMN_WIDTH - 16.dp
                    (available / players.size).coerceIn(MIN_PLAYER_COLUMN_WIDTH, MAX_PLAYER_COLUMN_WIDTH)
                }

                Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    // ----- En-tête fixe : rang + nom (toujours visible au-dessus des manches) -----
                    Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                        Column {
                            Box(Modifier.width(LABEL_COLUMN_WIDTH).height(RANK_ROW_HEIGHT))
                            Box(Modifier.width(LABEL_COLUMN_WIDTH).height(NAME_ROW_HEIGHT))
                        }
                        players.forEachIndexed { playerIndex, name ->
                            val rank = viewModel.rankOf(playerIndex, ranks)
                            val isLeader = viewModel.isLeader(playerIndex, totals, ranks)
                            val color = playerColors.getOrElse(playerIndex) { Color.Gray }
                            Column {
                                Box(
                                    modifier = Modifier.width(playerColumnWidth).height(RANK_ROW_HEIGHT),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isLeader) {
                                            Icon(
                                                imageVector = Icons.Filled.EmojiEvents,
                                                contentDescription = stringResource(R.string.first_place),
                                                tint = Color(0xFFFFC107),
                                                modifier = Modifier.padding(end = 4.dp)
                                            )
                                        }
                                        Text("#$rank", fontWeight = FontWeight.Bold)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .width(playerColumnWidth)
                                        .height(NAME_ROW_HEIGHT)
                                        .background(color)
                                        .clickable(onClickLabel = stringResource(R.string.score_show_full_name), role = Role.Button) { expandedNameIndex = playerIndex },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = name,
                                        color = contentColorFor(color),
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // ----- Corps défilant : une manche par ligne, plus le total -----
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                            Column {
                                scores.forEachIndexed { index, _ ->
                                    Box(
                                        modifier = Modifier.width(LABEL_COLUMN_WIDTH).height(scoreRowHeight),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(stringResource(R.string.score_round_n, index + 1))
                                    }
                                }
                                Box(
                                    modifier = Modifier.width(LABEL_COLUMN_WIDTH).height(TOTAL_ROW_HEIGHT),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(stringResource(R.string.score_total), fontWeight = FontWeight.Bold)
                                }
                            }

                            players.forEachIndexed { playerIndex, _ ->
                                val color = playerColors.getOrElse(playerIndex) { Color.Black }
                                Column {
                                    scores.forEachIndexed { roundIndex, round ->
                                        Box(
                                            modifier = Modifier.width(playerColumnWidth).height(scoreRowHeight),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            ScoreCell(
                                                cell = round[playerIndex],
                                                allowNegative = viewModel.gameRules.allowNegativeScores,
                                                multipliers = multipliers,
                                                playerColor = color,
                                                onValueEntered = { value, negative ->
                                                    viewModel.setCell(roundIndex, playerIndex, value, negative)
                                                },
                                                onMultiplierPicked = { id ->
                                                    viewModel.setMultiplier(roundIndex, playerIndex, id)
                                                }
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(playerColumnWidth)
                                            .height(TOTAL_ROW_HEIGHT)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = totals.getOrElse(playerIndex) { 0 }.toString(),
                                            fontWeight = FontWeight.Bold,
                                            color = playerTextColor(color)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
            canUndo = viewModel.canUndo
        )
    }

    expandedNameIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { expandedNameIndex = null },
            confirmButton = {
                TextButton(onClick = { expandedNameIndex = null }) { Text(stringResource(R.string.common_ok)) }
            },
            title = { Text(stringResource(R.string.score_player)) },
            text = { Text(players.getOrElse(index) { "" }) }
        )
    }
}

/**
 * Case du tableau : elle n'affiche que le score (dans la couleur du joueur).
 * Un clic ouvre une fenêtre de saisie (voir [ScoreInputDialog]) où l'on écrit le
 * score et, si les scores négatifs sont autorisés, choisit le signe. Le sélecteur de
 * règle de multiplication reste au-dessus de la case (si le jeu en a plusieurs).
 */
@Composable
private fun ScoreCell(
    cell: CellState,
    allowNegative: Boolean,
    multipliers: List<ScoreMultiplier>,
    playerColor: Color,
    onValueEntered: (Int?, Boolean) -> Unit,
    onMultiplierPicked: (String) -> Unit
) {
    // rememberSaveable : la boîte de saisie reste ouverte après une rotation.
    var showDialog by rememberSaveable { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (multipliers.size > 1) {
            var expanded by remember { mutableStateOf(false) }
            val current = multipliers.find { it.id == cell.multiplierId } ?: multipliers.first()
            Box {
                Text(
                    text = "${current.label} ×${current.factor}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable(onClickLabel = stringResource(R.string.score_change_multiplier), role = Role.Button) { expanded = true }
                        .padding(vertical = 2.dp)
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    multipliers.forEach { multiplier ->
                        DropdownMenuItem(
                            text = { Text("${multiplier.label} ×${multiplier.factor}") },
                            onClick = {
                                expanded = false
                                onMultiplierPicked(multiplier.id)
                            }
                        )
                    }
                }
            }
        }

        val base = cell.baseValue
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 4.dp)
                .height(48.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = stringResource(R.string.score_enter), role = Role.Button) { showDialog = true },
            contentAlignment = Alignment.Center
        ) {
            if (base != null) {
                Text(
                    text = (if (cell.isNegative) "−" else "") + base,
                    color = playerTextColor(playerColor),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }

    if (showDialog) {
        ScoreInputDialog(
            initialValue = cell.baseValue,
            initialNegative = cell.isNegative,
            allowNegative = allowNegative,
            onConfirm = { value, negative ->
                showDialog = false
                onValueEntered(value, negative)
            },
            onDismiss = { showDialog = false }
        )
    }
}

/** Fenêtre de saisie d'un score : champ numérique plein format + bascule +/− éventuelle. */
@Composable
private fun ScoreInputDialog(
    initialValue: Int?,
    initialNegative: Boolean,
    allowNegative: Boolean,
    onConfirm: (Int?, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val initialText = initialValue?.toString() ?: ""
    // Texte présélectionné : taper un nouveau score remplace directement l'ancien.
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText, TextRange(0, initialText.length)))
    }
    var negative by rememberSaveable { mutableStateOf(initialNegative) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val confirm = { onConfirm(field.text.toIntOrNull(), negative) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.score_enter_title)) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (allowNegative) {
                    OutlinedButton(
                        onClick = { negative = !negative },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = if (negative) "−" else "+",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            color = if (negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                OutlinedTextField(
                    value = field,
                    onValueChange = { input ->
                        field = input.copy(text = input.text.filter { it.isDigit() })
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { confirm() }) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
