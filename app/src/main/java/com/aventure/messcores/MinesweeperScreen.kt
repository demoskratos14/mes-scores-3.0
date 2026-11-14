package com.aventure.messcores

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

// Les étapes de la séance : le choix du niveau, puis le plateau (la partie en cours ou, une fois finie, son résultat).
private const val MINE_PHASE_READY = 0
private const val MINE_PHASE_PLAYING = 1

/** Pas du chronomètre en millisecondes, et plus long temps compté d'un coup (après un à-coup, il ne saute pas). */
private const val TIMER_STEP_MS = 100L
private const val MAX_TICK_MS = 500L

// L'aspect d'une case.
private const val LOOK_CLOSED = 0
private const val LOOK_FLAG = 1
private const val LOOK_OPEN = 2
private const val LOOK_MINE = 3 // une mine non trouvée, montrée après une défaite
private const val LOOK_BLAST = 4 // la mine sur laquelle on a perdu
private const val LOOK_WRONG = 5 // un drapeau posé sur une case sans mine, montré après une défaite

private val MineClosedColor = Color(0xFF5E7A91)
private val MineClosedAltColor = Color(0xFF6B8AA3)
private val MineOpenColor = Color(0xFFE8ECEF)
private val MineOpenAltColor = Color(0xFFDDE3E7)
private val MineBlastColor = Color(0xFFEF5350)
private val MineFlagColor = Color(0xFFE53935)
private val MineInkColor = Color(0xFF263238)
private val MineBoardColor = Color(0xFF1B2229)

// La couleur de chaque nombre, de 1 à 8 (l'indice 0 ne sert pas).
private val MineNumberColors = listOf(
    Color.Transparent, Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFFC62828), Color(0xFF283593),
    Color(0xFF6D4C41), Color(0xFF00838F), Color(0xFF212121), Color(0xFF616161)
)

/** Garde la partie quand l'écran tourne : son état est rangé dans une liste d'entiers (voir [MinesweeperGame.save]). */
private val MinesweeperGameSaver = listSaver<MinesweeperGame, Int>(
    save = { it.save() },
    restore = { MinesweeperGame.restore(it) }
)

@StringRes
private fun MineLevel.labelRes(): Int = when (this) {
    MineLevel.EASY -> R.string.minesweeper_level_easy
    MineLevel.MEDIUM -> R.string.minesweeper_level_medium
    MineLevel.HARD -> R.string.minesweeper_level_hard
}

/**
 * Démineur en solo : on dévoile les cases d'un plateau sans toucher une mine. Un nombre dit combien de mines touchent la case
 * (diagonales comprises) ; un drapeau marque une case qu'on croit minée. Toucher une case la dévoile, la garder appuyée pose un
 * drapeau (le bouton « Drapeau » inverse les deux gestes) ; toucher un nombre déjà entouré d'autant de drapeaux dévoile d'un coup
 * ses autres voisines. Trois niveaux, un record par niveau (le score tient compte du temps). Le chronomètre démarre au premier
 * dévoilement ; la partie se met en pause quand l'appli passe en arrière-plan. Tout est gardé quand l'écran tourne.
 * [players] : le joueur dont le nom s'affiche au départ.
 */
@Composable
internal fun MinesweeperScreen(players: List<String>) {
    val name = players.firstOrNull().orEmpty().ifEmpty { "1" }
    val context = LocalContext.current
    var levelIndex by rememberSaveable { mutableIntStateOf(0) }
    val level = MineLevel.entries[levelIndex.coerceIn(0, MineLevel.entries.size - 1)]
    val game = rememberSaveable(levelIndex, saver = MinesweeperGameSaver) { MinesweeperGame(level) }
    var phase by rememberSaveable { mutableIntStateOf(MINE_PHASE_READY) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var flagMode by rememberSaveable { mutableStateOf(false) }
    // Change à chaque nouvelle partie : le plateau est alors reconstruit à neuf.
    var round by rememberSaveable { mutableIntStateOf(0) }
    val record = rememberSoloRecord(Minesweeper.recordKey(level))
    val currentGame by rememberUpdatedState(game)

    // La partie se met en pause quand l'appli passe en arrière-plan (et donc aussi quand l'écran tourne).
    val lifecycleOwner = remember(context) { findMinesweeperLifecycleOwner(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && phase == MINE_PHASE_PLAYING &&
                currentGame.state == MinesweeperGame.PLAYING
            ) {
                paused = true
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    // Le bouton retour met la partie en pause plutôt que de la quitter par mégarde.
    BackHandler(enabled = phase == MINE_PHASE_PLAYING && game.state == MinesweeperGame.PLAYING && !paused) { paused = true }

    fun startGame() {
        record.clearNew()
        game.reset()
        flagMode = false
        paused = false
        round++
        phase = MINE_PHASE_PLAYING
    }

    fun backToMenu() {
        record.clearNew()
        game.reset()
        paused = false
        phase = MINE_PHASE_READY
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (phase == MINE_PHASE_PLAYING) {
            key(round) {
                MinesweeperPlayfield(
                    game = game,
                    paused = paused,
                    onPausedChange = { paused = it },
                    flagMode = flagMode,
                    onFlagModeChange = { flagMode = it },
                    record = record,
                    onFinished = { if (game.state == MinesweeperGame.WON) record.submit(game.score) },
                    onReplay = { startGame() },
                    onChangeLevel = { backToMenu() }
                )
            }
        } else {
            MinesweeperCard {
                Text(
                    text = stringResource(R.string.minesweeper_ready_title, name),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.minesweeper_ready_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
                    MineLevel.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = option == level,
                                    onClick = { levelIndex = option.ordinal },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = option == level, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(
                                    R.string.minesweeper_level_detail,
                                    stringResource(option.labelRes()),
                                    option.cols,
                                    option.rows,
                                    option.mines
                                ),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
                SoloRecordLine(record)
                Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.minesweeper_go))
                }
            }
        }
        RecordSparkles(record)
    }
}

/** La carte de départ : le titre du jeu, ses règles, puis [content]. */
@Composable
private fun MinesweeperCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.game_minesweeper),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    RulesButton(
                        gameId = MINESWEEPER_ID,
                        gameName = stringResource(R.string.game_minesweeper),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                content()
            }
        }
    }
}

/**
 * La partie : l'en-tête (mines restantes, temps, pause), le plateau, puis les commandes (choix du geste) ou, une fois la partie
 * finie, le résultat. [onFinished] est appelé une fois, à la victoire comme à la défaite.
 */
@Composable
private fun MinesweeperPlayfield(
    game: MinesweeperGame,
    paused: Boolean,
    onPausedChange: (Boolean) -> Unit,
    flagMode: Boolean,
    onFlagModeChange: (Boolean) -> Unit,
    record: SoloRecord,
    onFinished: () -> Unit,
    onReplay: () -> Unit,
    onChangeLevel: () -> Unit
) {
    KeepScreenOn()
    // Change à chaque action : le plateau se redessine, sans recomposer tout l'écran.
    var revision by remember { mutableIntStateOf(0) }
    var gameState by remember { mutableIntStateOf(game.state) }
    var minesLeft by remember { mutableIntStateOf(game.minesLeft) }
    var seconds by remember { mutableIntStateOf(game.seconds) }
    val finishedCallback by rememberUpdatedState(onFinished)

    fun sync() {
        revision++
        gameState = game.state
        minesLeft = game.minesLeft
        seconds = game.seconds
    }

    /** Un geste sur une case : [alternate] pour l'appui long, qui fait l'inverse du geste choisi. */
    fun act(col: Int, row: Int, alternate: Boolean) {
        if (paused || game.isFinished) return
        val flag = flagMode != alternate
        val changed = if (flag) game.toggleFlag(col, row) else game.reveal(col, row)
        if (!changed) return
        sync()
        if (game.isFinished) finishedCallback()
    }

    // Le chronomètre ne tourne que partie en cours et hors pause.
    LaunchedEffect(paused, gameState) {
        if (paused || gameState != MinesweeperGame.PLAYING) return@LaunchedEffect
        var last = System.nanoTime()
        while (true) {
            delay(TIMER_STEP_MS)
            val now = System.nanoTime()
            game.tick(((now - last) / 1_000_000L).coerceIn(0L, MAX_TICK_MS))
            last = now
            if (game.seconds != seconds) seconds = game.seconds
        }
    }

    val finished = gameState == MinesweeperGame.WON || gameState == MinesweeperGame.LOST

    Column(
        modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.minesweeper_mines_left, minesLeft),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.minesweeper_time, seconds),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { onPausedChange(true) },
                    enabled = !paused && gameState == MinesweeperGame.PLAYING
                ) {
                    Text(stringResource(R.string.minesweeper_pause))
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Une case carrée, la plus grande qui laisse tenir le plateau.
            val cell = maxOf(minOf(maxWidth / game.cols, maxHeight / game.rows), 0.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(cell * game.cols, cell * game.rows)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MineBoardColor)
            ) {
                if (paused) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.minesweeper_paused_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(8.dp)
                        )
                        Button(onClick = { onPausedChange(false) }) {
                            Text(stringResource(R.string.minesweeper_resume))
                        }
                    }
                } else {
                    MinesweeperBoard(
                        game = game,
                        revision = revision,
                        cell = cell,
                        flagMode = flagMode,
                        onAct = { col, row, alternate -> act(col, row, alternate) }
                    )
                }
            }
        }

        if (finished) {
            MinesweeperResult(game = game, won = gameState == MinesweeperGame.WON, record = record, onReplay = onReplay, onChangeLevel = onChangeLevel)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeButton(stringResource(R.string.minesweeper_mode_reveal), selected = !flagMode, modifier = Modifier.weight(1f)) {
                    onFlagModeChange(false)
                }
                ModeButton(stringResource(R.string.minesweeper_mode_flag), selected = flagMode, modifier = Modifier.weight(1f)) {
                    onFlagModeChange(true)
                }
            }
        }
    }
}

/** Un bouton de choix du geste : plein quand il est choisi, contour sinon. */
@Composable
private fun ModeButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

/** Le résultat d'une partie finie, sous le plateau qui reste visible (toutes les mines y sont montrées après une défaite). */
@Composable
private fun MinesweeperResult(game: MinesweeperGame, won: Boolean, record: SoloRecord, onReplay: () -> Unit, onChangeLevel: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(if (won) R.string.minesweeper_won_title else R.string.minesweeper_lost_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            if (won) {
                Text(
                    text = stringResource(R.string.minesweeper_score, game.score),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = highlightContent(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(highlightContainer())
                        .padding(8.dp)
                )
            } else {
                Text(
                    text = stringResource(
                        R.string.minesweeper_lost_stats,
                        game.opened,
                        game.level.cellCount - game.level.mines
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
            SoloRecordLine(record)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onReplay, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.minesweeper_play_again), textAlign = TextAlign.Center)
                }
                OutlinedButton(onClick = onChangeLevel, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.minesweeper_change_level), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** L'aspect de la case ([col], [row]) selon l'état de la partie. */
private fun lookOf(game: MinesweeperGame, col: Int, row: Int): Int {
    val lost = game.state == MinesweeperGame.LOST
    return when {
        game.isOpen(col, row) -> LOOK_OPEN
        lost && game.isBlast(col, row) -> LOOK_BLAST
        lost && game.isMine(col, row) && !game.isFlagged(col, row) -> LOOK_MINE
        lost && game.isFlagged(col, row) && !game.isMine(col, row) -> LOOK_WRONG
        game.isFlagged(col, row) -> LOOK_FLAG
        else -> LOOK_CLOSED
    }
}

/**
 * Le plateau : une case par composable (TalkBack lit chacune et peut la dévoiler ou la marquer), et un geste du doigt sur tout
 * le plateau, qui retrouve la case touchée. [revision] change à chaque action pour redessiner le plateau.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
private fun MinesweeperBoard(
    game: MinesweeperGame,
    revision: Int,
    cell: Dp,
    flagMode: Boolean,
    onAct: (col: Int, row: Int, alternate: Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val cellPx = with(LocalDensity.current) { cell.toPx() }
    val act by rememberUpdatedState(onAct)
    val revealLabel = stringResource(R.string.minesweeper_mode_reveal)
    val flagLabel = stringResource(R.string.minesweeper_mode_flag)
    val tapLabel = if (flagMode) flagLabel else revealLabel
    val longLabel = if (flagMode) revealLabel else flagLabel

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(cellPx, game) {
                detectTapGestures(
                    onTap = { offset ->
                        val col = (offset.x / cellPx).toInt()
                        val row = (offset.y / cellPx).toInt()
                        if (game.inBounds(col, row)) act(col, row, false)
                    },
                    onLongPress = { offset ->
                        val col = (offset.x / cellPx).toInt()
                        val row = (offset.y / cellPx).toInt()
                        if (game.inBounds(col, row)) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            act(col, row, true)
                        }
                    }
                )
            }
    ) {
        for (r in 0 until game.rows) {
            Row {
                for (c in 0 until game.cols) {
                    val look = lookOf(game, c, r)
                    MineCell(
                        look = look,
                        count = if (look == LOOK_OPEN) game.adjacentMines(c, r) else 0,
                        checker = (c + r) % 2 == 0,
                        size = cell,
                        row = r + 1,
                        col = c + 1,
                        tapLabel = tapLabel,
                        longLabel = longLabel,
                        onTap = { act(c, r, false) },
                        onLongTap = { act(c, r, true) }
                    )
                }
            }
        }
    }
}

/** Une case : son dessin, son nombre éventuel, et sa description pour TalkBack (avec les deux actions possibles). */
@Composable
private fun MineCell(
    look: Int,
    count: Int,
    checker: Boolean,
    size: Dp,
    row: Int,
    col: Int,
    tapLabel: String,
    longLabel: String,
    onTap: () -> Unit,
    onLongTap: () -> Unit
) {
    val description = when (look) {
        LOOK_FLAG -> stringResource(R.string.minesweeper_cell_flag, row, col)
        LOOK_OPEN -> if (count == 0) {
            stringResource(R.string.minesweeper_cell_empty, row, col)
        } else {
            stringResource(R.string.minesweeper_cell_number, row, col, count)
        }
        LOOK_MINE, LOOK_BLAST -> stringResource(R.string.minesweeper_cell_mine, row, col)
        LOOK_WRONG -> stringResource(R.string.minesweeper_cell_wrong, row, col)
        else -> stringResource(R.string.minesweeper_cell_closed, row, col)
    }
    Box(
        modifier = Modifier
            .size(size)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(label = tapLabel) {
                    onTap()
                    true
                }
                onLongClick(label = longLabel) {
                    onLongTap()
                    true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) { drawMineCell(look, checker) }
        if (look == LOOK_OPEN && count > 0) {
            val fontSize = with(LocalDensity.current) { (size * 0.6f).toSp() }
            Text(
                text = count.toString(),
                color = MineNumberColors[count.coerceIn(1, 8)],
                fontWeight = FontWeight.Bold,
                fontSize = fontSize
            )
        }
    }
}

private fun DrawScope.drawMineCell(look: Int, checker: Boolean) {
    val s = size.minDimension
    val inset = s * 0.04f
    val inner = s - 2f * inset
    val corner = CornerRadius(s * 0.14f)
    val closed = look == LOOK_CLOSED || look == LOOK_FLAG || look == LOOK_WRONG
    val background = when {
        look == LOOK_BLAST -> MineBlastColor
        closed -> if (checker) MineClosedAltColor else MineClosedColor
        else -> if (checker) MineOpenAltColor else MineOpenColor
    }
    drawRoundRect(background, Offset(inset, inset), Size(inner, inner), corner)
    if (closed) {
        // Un reflet sur le haut de la case : elle a l'air en relief, par opposition aux cases dévoilées.
        drawRoundRect(Color.White.copy(alpha = 0.14f), Offset(inset, inset), Size(inner, inner * 0.45f), corner)
    }
    when (look) {
        LOOK_FLAG -> drawMineFlag()
        LOOK_MINE, LOOK_BLAST -> drawMineBomb()
        LOOK_WRONG -> {
            drawMineFlag()
            val cross = Color(0xFFD50000)
            drawLine(cross, Offset(s * 0.2f, s * 0.2f), Offset(s * 0.8f, s * 0.8f), strokeWidth = s * 0.08f, cap = StrokeCap.Round)
            drawLine(cross, Offset(s * 0.8f, s * 0.2f), Offset(s * 0.2f, s * 0.8f), strokeWidth = s * 0.08f, cap = StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawMineFlag() {
    val s = size.minDimension
    drawLine(MineInkColor, Offset(s * 0.42f, s * 0.2f), Offset(s * 0.42f, s * 0.78f), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
    drawLine(MineInkColor, Offset(s * 0.28f, s * 0.8f), Offset(s * 0.62f, s * 0.8f), strokeWidth = s * 0.08f, cap = StrokeCap.Round)
    val pennant = Path().apply {
        moveTo(s * 0.44f, s * 0.2f)
        lineTo(s * 0.74f, s * 0.34f)
        lineTo(s * 0.44f, s * 0.48f)
        close()
    }
    drawPath(pennant, MineFlagColor)
}

private fun DrawScope.drawMineBomb() {
    val s = size.minDimension
    val center = Offset(s / 2f, s / 2f)
    // Quatre traits qui se croisent : les huit piques de la mine.
    for (k in 0 until 4) {
        val angle = (PI / 4.0 * k).toFloat()
        val dx = cos(angle) * s * 0.3f
        val dy = sin(angle) * s * 0.3f
        drawLine(MineInkColor, Offset(center.x - dx, center.y - dy), Offset(center.x + dx, center.y + dy), strokeWidth = s * 0.06f, cap = StrokeCap.Round)
    }
    drawCircle(MineInkColor, s * 0.2f, center)
    drawCircle(Color.White.copy(alpha = 0.85f), s * 0.05f, Offset(center.x - s * 0.07f, center.y - s * 0.07f))
}

private fun findMinesweeperLifecycleOwner(context: Context): LifecycleOwner? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return null
}
