package com.aventure.messcores

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Bouton « Dés » de l'en-tête d'une partie : ouvre l'écran des dés du jeu (voir [DiceScreen]). */
@Composable
fun DiceButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.9f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
    ) {
        Text(stringResource(R.string.dice_button), style = MaterialTheme.typography.labelLarge)
    }
}

/** Vrai si le jeu a des dés à lancer dans l'appli. */
internal fun GameRules.hasDice(): Boolean = DiceSetups.forGame(id) != null

/** Garde l'état d'un tour de dés quand l'écran tourne. */
@Composable
private fun rememberDiceTurn(setup: DiceSetup): DiceTurn = rememberSaveable(
    setup,
    saver = Saver<DiceTurn, List<Int>>(
        save = { it.snapshot() },
        restore = { data -> DiceTurn(setup).also { turn -> turn.restoreFrom(data) } }
    )
) { DiceTurn(setup) }

/**
 * Écran des dés d'un jeu : les dés du jeu à lancer, ceux que l'on garde pour relancer les autres, et
 * l'inscription du résultat là où le jeu le permet :
 * - Yams : le score de chaque ligne est calculé d'après les dés ; on l'inscrit (ou on barre la ligne) ;
 * - Cochon : total du tour, à garder ou perdu au premier 1 ;
 * - 421 : valeur du jeu, à donner au joueur qui prend les jetons ;
 * - autres jeux : les dés et leur total (Qwixx : les combinaisons possibles avec les dés blancs).
 */
@Composable
fun DiceScreen(viewModel: ScoreViewModel, onBack: () -> Unit) {
    val rules = viewModel.gameRules
    val setup = DiceSetups.forGame(rules.id)
    if (setup == null) {
        // Aucun dé pour ce jeu (partie ouverte entre-temps) : retour à la partie.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    KeepScreenOn()

    val players = viewModel.players
    val turn = rememberDiceTurn(setup)
    var player by rememberSaveable { mutableIntStateOf(0) }
    // Cochon : total du tour en cours, perdu dès qu'un 1 sort.
    var pigTotal by rememberSaveable { mutableIntStateOf(0) }
    var pigBust by rememberSaveable { mutableStateOf(false) }

    val isYams = rules.sheet == ScoreSheets.YAMS
    val isPig = rules.id == "builtin_cochon"
    val is421 = rules.id == "builtin_421"
    val isQwixx = rules.id == "builtin_qwixx"
    val gameOver = viewModel.isGameOver()
    val currentPlayer = player.coerceIn(0, (players.size - 1).coerceAtLeast(0))

    fun newTurn() {
        turn.reset()
        pigTotal = 0
        pigBust = false
    }

    fun nextPlayer() {
        if (players.isNotEmpty()) player = (currentPlayer + 1) % players.size
        newTurn()
    }

    fun roll() {
        turn.roll()
        if (isPig) {
            val value = turn.values.firstOrNull() ?: return
            if (value == 1) {
                pigBust = true
                pigTotal = 0
            } else {
                pigTotal += value
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.common_back), color = Color.White)
        }
        Text(
            text = stringResource(R.string.dice_title),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = rules.name,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f)
        )
        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if ((isYams || isPig) && players.size > 1) {
                PlayerChooser(players = players, selected = currentPlayer, onSelect = { player = it; newTurn() })
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = cardSurface()),
                modifier = Modifier.fillMaxWidth()
            ) {
                DiceTray(
                    turn = turn,
                    rollEnabled = turn.canRoll && !(isPig && pigBust),
                    onRoll = { roll() },
                    onNewTurn = { newTurn() }
                )
            }

            when {
                isYams -> YamsPanel(
                    viewModel = viewModel,
                    turn = turn,
                    player = currentPlayer,
                    enabled = !gameOver,
                    onWritten = { nextPlayer() }
                )
                isPig -> PigPanel(
                    viewModel = viewModel,
                    player = currentPlayer,
                    total = pigTotal,
                    bust = pigBust,
                    hasRolled = turn.hasRolled,
                    enabled = !gameOver,
                    onWritten = { nextPlayer() }
                )
                is421 -> Panel421(
                    viewModel = viewModel,
                    turn = turn,
                    enabled = !gameOver,
                    onGiven = { newTurn() }
                )
                isQwixx -> QwixxPanel(turn = turn)
                turn.hasRolled -> Card(
                    colors = CardDefaults.cardColors(containerColor = cardSurface()),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.dice_sum, turn.values.sum()),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        if (viewModel.canUndo) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.undo() },
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.9f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.actions_undo))
            }
        }
    }
}

/** Choix du joueur qui lance : un bouton par joueur, dans l'ordre de la table. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerChooser(players: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.dice_player_label),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            players.forEachIndexed { index, name ->
                FilterChip(
                    selected = index == selected,
                    onClick = { onSelect(index) },
                    label = { Text(name) }
                )
            }
        }
    }
}

/** Les dés, le bouton pour les lancer, les lancers restants et « Nouveau tour ». */
@Composable
private fun DiceTray(turn: DiceTurn, rollEnabled: Boolean, onRoll: () -> Unit, onNewTurn: () -> Unit) {
    val setup = turn.setup
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = (DIE_MAX_SIZE_DP * setup.diceCount).dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (index in 0 until setup.diceCount) {
                val value = turn.values.getOrNull(index)
                val held = index in turn.held
                val description = when {
                    value == null -> stringResource(R.string.dice_die_empty, index + 1)
                    held -> stringResource(R.string.dice_die_held, index + 1, value)
                    else -> stringResource(R.string.dice_die, index + 1, value)
                }
                DieFace(
                    value = value,
                    held = held,
                    tint = setup.tints.getOrNull(index)?.let { Color(it) } ?: Color.White,
                    description = description,
                    onClick = if (turn.canHold) ({ turn.toggleHold(index) }) else null,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Button(onClick = onRoll, enabled = rollEnabled, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (turn.hasRolled) R.string.dice_reroll else R.string.dice_roll))
        }

        turn.rollsLeft?.let { left ->
            Text(text = stringResource(R.string.dice_rolls_left, left), style = MaterialTheme.typography.bodyMedium)
        }
        if (turn.canHold) {
            Text(text = stringResource(R.string.dice_hold_hint), style = MaterialTheme.typography.bodySmall)
        }
        if (turn.hasRolled) {
            TextButton(onClick = onNewTurn) { Text(stringResource(R.string.dice_new_turn)) }
        }
    }
}

/** Un dé : ses points dessinés sur une face blanche (ou colorée) ; entouré d'un trait épais s'il est gardé. */
@Composable
private fun DieFace(
    value: Int?,
    held: Boolean,
    tint: Color,
    description: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    val borderColor = if (held) MaterialTheme.colorScheme.primary else Color.Gray
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(tint, shape)
            .border(if (held) 4.dp else 1.dp, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { contentDescription = description }
    ) {
        if (value != null) {
            Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                val radius = size.width / 9f
                for ((column, row) in pipsOf(value)) {
                    drawCircle(
                        color = Color(0xFF212121),
                        radius = radius,
                        center = Offset(size.width * (1f + 2f * column) / 6f, size.height * (1f + 2f * row) / 6f)
                    )
                }
            }
        }
    }
}

/** Position (colonne, ligne) de chaque point d'une face, sur une grille de 3 × 3. */
internal fun pipsOf(value: Int): List<Pair<Int, Int>> = when (value) {
    1 -> listOf(1 to 1)
    2 -> listOf(0 to 0, 2 to 2)
    3 -> listOf(0 to 0, 1 to 1, 2 to 2)
    4 -> listOf(0 to 0, 2 to 0, 0 to 2, 2 to 2)
    5 -> listOf(0 to 0, 2 to 0, 1 to 1, 0 to 2, 2 to 2)
    6 -> listOf(0 to 0, 2 to 0, 0 to 1, 2 to 1, 0 to 2, 2 to 2)
    else -> emptyList()
}

/**
 * Yams : chaque ligne de la feuille du joueur propose le score que rapportent les dés. « Inscrire »
 * le met dans la case, « Barrer » la raye (0 point) si l'on n'en veut pas ; la case remplie reste affichée.
 */
@Composable
private fun YamsPanel(
    viewModel: ScoreViewModel,
    turn: DiceTurn,
    player: Int,
    enabled: Boolean,
    onWritten: () -> Unit
) {
    val rows = viewModel.sheetRows ?: return
    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!turn.hasRolled) {
                Text(text = stringResource(R.string.dice_yams_prompt), style = MaterialTheme.typography.bodyMedium)
            }
            rows.forEachIndexed { index, row ->
                val filled = viewModel.scores.getOrNull(index)?.getOrNull(player)?.baseValue
                val proposed = if (turn.hasRolled) YamsScoring.score(row.id, turn.values) else null
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(row.label),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    when {
                        filled != null -> Text(
                            text = if (filled == 0) stringResource(R.string.dice_yams_crossed) else filled.toString(),
                            fontWeight = FontWeight.Bold
                        )
                        proposed != null && enabled -> {
                            if (proposed > 0) {
                                Button(onClick = {
                                    viewModel.setCell(index, player, proposed, false)
                                    onWritten()
                                }) {
                                    Text(stringResource(R.string.dice_yams_write, proposed))
                                }
                            }
                            OutlinedButton(onClick = {
                                viewModel.setCell(index, player, 0, false)
                                onWritten()
                            }) {
                                Text(stringResource(R.string.sheet_cross_out))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Cochon : total du tour (perdu au premier 1) ; on le garde dans la prochaine case libre du joueur, ou on passe à 0. */
@Composable
private fun PigPanel(
    viewModel: ScoreViewModel,
    player: Int,
    total: Int,
    bust: Boolean,
    hasRolled: Boolean,
    enabled: Boolean,
    onWritten: () -> Unit
) {
    fun write(points: Int) {
        val round = viewModel.scores.indexOfFirst { it.getOrNull(player)?.baseValue == null }
        if (round >= 0) viewModel.setCell(round, player, points, false)
        onWritten()
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.dice_pig_total, total),
                style = MaterialTheme.typography.titleMedium
            )
            if (bust) {
                Text(
                    text = stringResource(R.string.dice_pig_bust),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (enabled && hasRolled) {
                if (bust) {
                    Button(onClick = { write(0) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.dice_pig_pass))
                    }
                } else {
                    Button(onClick = { write(total) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.dice_pig_keep, total))
                    }
                }
            }
        }
    }
}

/** 421 : valeur du jeu obtenu ; on la donne en jetons au joueur qui perd la manche. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Panel421(viewModel: ScoreViewModel, turn: DiceTurn, enabled: Boolean, onGiven: () -> Unit) {
    if (!turn.hasRolled) return
    val combo = Dice421.combo(turn.values)
    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.dice_combo_value, stringResource(combo.label), combo.points),
                style = MaterialTheme.typography.titleMedium
            )
            if (enabled) {
                Text(
                    text = stringResource(R.string.dice_421_give, combo.points),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    viewModel.players.forEachIndexed { index, name ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                viewModel.incrementCounter(index, combo.points)
                                onGiven()
                            },
                            label = { Text(name) }
                        )
                    }
                }
            }
        }
    }
}

/** Qwixx : total des deux dés blancs, puis pour chaque couleur les deux totaux possibles (un dé blanc + le dé de couleur). */
@Composable
private fun QwixxPanel(turn: DiceTurn) {
    if (!turn.hasRolled) return
    val colorNames = listOf(
        R.string.sheet_qwixx_red,
        R.string.sheet_qwixx_yellow,
        R.string.sheet_qwixx_green,
        R.string.sheet_qwixx_blue
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.dice_qwixx_white, QwixxDice.whiteSum(turn.values)),
                style = MaterialTheme.typography.titleMedium
            )
            colorNames.forEachIndexed { offset, name ->
                val (first, second) = QwixxDice.colorOptions(turn.values, colorDie = 2 + offset)
                Text(
                    text = stringResource(R.string.dice_qwixx_option, stringResource(name), first, second),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

private const val DIE_MAX_SIZE_DP = 84
