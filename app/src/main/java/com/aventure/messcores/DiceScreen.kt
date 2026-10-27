package com.aventure.messcores

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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

/** La manche de 421 de la partie : gardée par la partie, donc aussi quand on passe par l'écran des scores et qu'on revient. */
@Composable
private fun rememberRound421(viewModel: ScoreViewModel, setup: DiceSetup): Round421 =
    remember(viewModel, viewModel.players.size) { viewModel.round421(setup.maxRolls ?: 3) }

/**
 * Écran des dés d'un jeu : les dés du jeu à lancer, ceux que l'on garde pour relancer les autres, et
 * l'inscription du résultat là où le jeu le permet :
 * - Yams : le score de chaque ligne est calculé d'après les dés ; on l'inscrit (ou on barre la ligne) ;
 * - Cochon : total du tour, à garder ou perdu au premier 1 ;
 * - 421 : la manche se joue tour par tour (voir [Round421] et [Round421Section]), les jetons du perdant sont comptés seuls ;
 * - autres jeux : les dés et leur total (Qwixx : les combinaisons possibles avec les dés blancs).
 */
@Composable
internal fun DiceScreen(viewModel: ScoreViewModel, session: SyncSession? = null, onBack: () -> Unit) {
    val rules = viewModel.gameRules
    val setup = DiceSetups.forGame(rules.id)
    if (setup == null) {
        // Aucun dé pour ce jeu (partie ouverte entre-temps) : retour à la partie.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    KeepScreenOn()

    val players = viewModel.players
    val is421 = rules.id == "builtin_421"
    // Au 421, la manche (tirage, ordre des joueurs, limite de lancers) garde elle-même les dés du joueur qui joue.
    val round: Round421? = if (is421) rememberRound421(viewModel, setup) else null
    val turn = round?.turn ?: rememberDiceTurn(setup)
    // Les dés tournent puis s'arrêtent un à un ; ce qui dévoile le résultat attend la fin (voir DiceRollAnimation).
    val rollAnimation = rememberDiceRollAnimation(turn)
    var player by rememberSaveable { mutableIntStateOf(0) }
    // Cochon : total du tour en cours, perdu dès qu'un 1 sort.
    var pigTotal by rememberSaveable { mutableIntStateOf(0) }
    var pigBust by rememberSaveable { mutableStateOf(false) }

    val isYams = rules.sheet == ScoreSheets.YAMS
    val isPig = rules.id == "builtin_cochon"
    val isQwixx = rules.id == "builtin_qwixx"
    val gameOver = viewModel.isGameOver()
    val currentPlayer = (round?.currentPlayer ?: player).coerceIn(0, (players.size - 1).coerceAtLeast(0))

    // Ce qui est partagé des dés : le tour de dés, ou toute la manche au 421.
    fun tableTurn(): List<Int> = round?.snapshot() ?: turn.snapshot()

    // Plusieurs téléphones : le tour de dés est partagé, et seul le joueur dont c'est le tour peut lancer.
    val shared = session != null && session.isConnected && session.kind == SyncKind.SCORE
    val myPlayer = session?.dicePlayer ?: DICE_UNSET
    val canAct = !shared || myPlayer == DICE_ALL || myPlayer == currentPlayer

    fun publish(forPlayer: Int = currentPlayer) {
        if (shared) session?.publishDice(DiceTable(forPlayer, tableTurn(), pigTotal, pigBust))
    }

    // Ce que les autres téléphones ont fait : on reprend leur tour de dés tel quel.
    val sharedTable = session?.diceTable
    LaunchedEffect(sharedTable, shared) {
        if (shared && sharedTable != null && sharedTable != DiceTable(currentPlayer, tableTurn(), pigTotal, pigBust)) {
            if (round != null) round.restoreFrom(sharedTable.turn) else turn.restoreFrom(sharedTable.turn)
            player = sharedTable.player
            pigTotal = sharedTable.pigTotal
            pigBust = sharedTable.pigBust
        }
    }

    // Un nouveau tour, un autre joueur ou la fin de partie : on remonte en haut, sinon la feuille du bas
    // (Yams) garde l'écran défilé trop bas et les dés sortent de l'écran.
    val scrollState = rememberScrollState()
    LaunchedEffect(currentPlayer, turn.hasRolled, gameOver) {
        if (!turn.hasRolled || gameOver) scrollState.animateScrollTo(0)
    }

    fun newTurn(forPlayer: Int = currentPlayer) {
        turn.reset()
        pigTotal = 0
        pigBust = false
        publish(forPlayer)
    }

    fun nextPlayer() {
        val next = if (players.isNotEmpty()) (currentPlayer + 1) % players.size else currentPlayer
        player = next
        newTurn(next)
    }

    fun roll() {
        turn.roll()
        if (isPig) {
            val value = turn.values.firstOrNull()
            if (value == 1) {
                pigBust = true
                pigTotal = 0
            } else if (value != null) {
                pigTotal += value
            }
        }
        publish()
    }

    fun toggleHold(index: Int) {
        turn.toggleHold(index)
        publish()
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = rules.name,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(10.dp))
            RulesButton(rules = rules)
        }
        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (gameOver) {
                // Toutes les cases sont remplies : la partie est terminée, avec son vainqueur.
                GameOverBanner(players = players, winners = viewModel.winners())
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.dice_see_scores))
                }
            }
            if (shared && session != null) {
                DiceSeatChooser(
                    players = players,
                    mine = myPlayer,
                    current = currentPlayer,
                    canAct = canAct,
                    onChoose = { session.chooseDicePlayer(it) }
                )
            }
            if ((isYams || isPig) && players.size > 1 && canAct) {
                PlayerChooser(players = players, selected = currentPlayer, onSelect = { player = it; newTurn(it) })
            }

            if (round != null) {
                Round421Section(
                    viewModel = viewModel,
                    round = round,
                    canAct = canAct,
                    onChanged = { publish() },
                    onBack = onBack
                )
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = cardSurface()),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DiceTray(
                        turn = turn,
                        animation = rollAnimation,
                        rollEnabled = turn.canRoll && !(isPig && pigBust) && canAct,
                        interactive = canAct,
                        onRoll = { roll() },
                        onToggleHold = { toggleHold(it) },
                        onNewTurn = { newTurn() }
                    )
                }
            }

            when {
                // Les dés tournent encore : rien ne dit déjà ce qu'ils valent.
                rollAnimation.rolling && round == null -> Unit
                isYams -> YamsPanel(
                    viewModel = viewModel,
                    turn = turn,
                    player = currentPlayer,
                    enabled = !gameOver && canAct,
                    onWritten = { nextPlayer() }
                )
                isPig -> PigPanel(
                    viewModel = viewModel,
                    player = currentPlayer,
                    total = pigTotal,
                    bust = pigBust,
                    hasRolled = turn.hasRolled,
                    enabled = !gameOver && canAct,
                    onWritten = { nextPlayer() }
                )
                isQwixx -> QwixxPanel(turn = turn)
                // 421 : tout est dans la section de la manche, plus haut.
                round != null -> Unit
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

        // Au 421, les jetons suivent la manche : on la rejoue plutôt que d'annuler un seul compteur.
        if (viewModel.canUndo && !is421) {
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

/**
 * Plusieurs téléphones : qui joue sur celui-ci (un joueur, ou tous), et à qui c'est de jouer quand ce n'est pas
 * le joueur de ce téléphone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiceSeatChooser(players: List<String>, mine: Int, current: Int, canAct: Boolean, onChoose: (Int) -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.dice_shared_me),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            players.forEachIndexed { index, name ->
                FilterChip(selected = index == mine, onClick = { onChoose(index) }, label = { Text(name) })
            }
            FilterChip(
                selected = mine == DICE_ALL,
                onClick = { onChoose(DICE_ALL) },
                label = { Text(stringResource(R.string.dice_shared_all)) }
            )
        }
        when {
            mine == DICE_UNSET -> Text(
                text = stringResource(R.string.dice_shared_pick),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            !canAct -> Text(
                text = stringResource(R.string.dice_shared_wait, players.getOrElse(current) { "" }),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
    }
}

/** Les dés, le bouton pour les lancer, les lancers restants et « Nouveau tour ». */
@Composable
internal fun DiceTray(
    turn: DiceTurn,
    animation: DiceRollAnimation,
    rollEnabled: Boolean,
    interactive: Boolean,
    showNewTurn: Boolean = true,
    onRoll: () -> Unit,
    onToggleHold: (Int) -> Unit,
    onNewTurn: () -> Unit
) {
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
                val spinning = animation.isSpinning(index)
                // Un dé qui tourne montre des faces au hasard ; il se fige sur sa vraie valeur quand son tour vient.
                val value = if (spinning) animation.faceOf(index) else turn.values.getOrNull(index)
                val held = index in turn.held
                val description = when {
                    turn.values.getOrNull(index) == null -> stringResource(R.string.dice_die_empty, index + 1)
                    held -> stringResource(R.string.dice_die_held, index + 1, turn.values[index])
                    else -> stringResource(R.string.dice_die, index + 1, turn.values[index])
                }
                DieFace(
                    value = value,
                    held = held,
                    spinning = spinning,
                    tint = setup.tints.getOrNull(index)?.let { Color(it) } ?: Color.White,
                    description = description,
                    onClick = if (interactive && turn.canHold && !animation.rolling) ({ onToggleHold(index) }) else null,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Button(onClick = onRoll, enabled = rollEnabled && !animation.rolling, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (turn.hasRolled) R.string.dice_reroll else R.string.dice_roll))
        }

        turn.rollsLeft?.let { left ->
            Text(text = stringResource(R.string.dice_rolls_left, left), style = MaterialTheme.typography.bodyMedium)
        }
        if (interactive && turn.canHold && !animation.rolling) {
            Text(text = stringResource(R.string.dice_hold_hint), style = MaterialTheme.typography.bodySmall)
        }
        if (interactive && showNewTurn && turn.hasRolled && !animation.rolling) {
            TextButton(onClick = onNewTurn) { Text(stringResource(R.string.dice_new_turn)) }
        }
    }
}

/** Un dé : ses points dessinés sur une face blanche (ou colorée) ; entouré d'un trait épais s'il est gardé. */
@Composable
private fun DieFace(
    value: Int?,
    held: Boolean,
    spinning: Boolean,
    tint: Color,
    description: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    val borderColor = if (held) MaterialTheme.colorScheme.primary else Color.Gray
    // Le dé tourne sur lui-même tant qu'il est lancé, un peu grossi ; à l'arrêt il se remet droit par le plus court chemin.
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (spinning) {
            while (true) {
                rotation.snapTo(0f)
                rotation.animateTo(360f, tween(durationMillis = 300, easing = LinearEasing))
            }
        } else {
            rotation.animateTo(if (rotation.value > 180f) 360f else 0f, tween(durationMillis = 120))
            rotation.snapTo(0f)
        }
    }
    val scale by animateFloatAsState(if (spinning) 1.08f else 1f, tween(durationMillis = 120), label = "dieScale")
    Box(
        modifier = modifier
            .graphicsLayer {
                rotationZ = rotation.value
                scaleX = scale
                scaleY = scale
            }
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
