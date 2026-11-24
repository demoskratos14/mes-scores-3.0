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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pas du chronomètre en millisecondes, et plus long temps compté d'un coup (après un à-coup, il ne saute pas). */
private const val SUDOKU_TIMER_STEP_MS = 100L
private const val SUDOKU_MAX_TICK_MS = 500L

// La roue de chiffres : taille du bouton et rayon de l'anneau. Le trou au milieu laisse voir la case en cours.
private val WheelButtonSize = 44.dp
private val WheelRingRadius = 74.dp
private val WheelSize = WheelRingRadius * 2 + WheelButtonSize

// Les couleurs de la grille (fixes, comme celles du Démineur : la grille a son propre papier).
private val SudokuBoardColor = Color(0xFF1B2229)
private val SudokuCellColor = Color(0xFFFAFBFC)
private val SudokuGivenColor = Color(0xFFE3E8EC)
private val SudokuPeerColor = Color(0xFFEAF2FA)
private val SudokuSelectedColor = Color(0xFFFFE082)
private val SudokuConflictColor = Color(0xFFFFCDD2)
private val SudokuGivenInk = Color(0xFF1B2229)
private val SudokuValueInk = Color(0xFF1565C0)
private val SudokuConflictInk = Color(0xFFC62828)
private val SudokuNoteInk = Color(0xFF546E7A)
private val SudokuLineColor = Color(0xFF263238)

/** Garde la partie quand l'écran tourne : son état est rangé dans une liste d'entiers (voir [SudokuGame.save]). */
private val SudokuGameSaver = listSaver<SudokuGame?, Int>(
    save = { it?.save() ?: emptyList() },
    restore = { if (it.isEmpty()) null else SudokuGame.restore(it) }
)

@StringRes
private fun SudokuLevel.labelRes(): Int = when (this) {
    SudokuLevel.EASY -> R.string.sudoku_level_easy
    SudokuLevel.MEDIUM -> R.string.sudoku_level_medium
    SudokuLevel.HARD -> R.string.sudoku_level_hard
    SudokuLevel.EXPERT -> R.string.sudoku_level_expert
}

private fun formatSudokuTime(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

/**
 * Sudoku en solo : on remplit une grille de 9 × 9 pour que chaque ligne, chaque colonne et chaque bloc de 3 × 3 contienne une
 * fois les chiffres de 1 à 9. Les grilles sont créées au hasard par l'appli (voir [SudokuEngine]), chacune avec une solution
 * unique. Toucher une case libre ouvre la roue des chiffres : un chiffre touché s'inscrit dans la case. Garder le doigt sur une
 * case ouvre la même roue en mode notes : chaque chiffre touché s'inscrit en tout petit, à sa place (123 / 456 / 789), comme
 * simple possibilité ; la roue reste ouverte et un toucher en dehors la ferme. Trois niveaux, un record par niveau. La partie se
 * met en pause quand l'appli passe en arrière-plan. Tout est gardé quand l'écran tourne.
 * [players] : le joueur dont le nom s'affiche au départ.
 */
@Composable
internal fun SudokuScreen(players: List<String>) {
    val name = players.firstOrNull().orEmpty().ifEmpty { "1" }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var levelIndex by rememberSaveable { mutableIntStateOf(0) }
    val menuLevel = SudokuLevel.entries[levelIndex.coerceIn(0, SudokuLevel.entries.size - 1)]
    // La partie en cours ; null tant qu'on est dans le choix du niveau.
    var game by rememberSaveable(stateSaver = SudokuGameSaver) { mutableStateOf<SudokuGame?>(null) }
    var generating by remember { mutableStateOf(false) }
    var paused by rememberSaveable { mutableStateOf(false) }
    // Change à chaque nouvelle grille : le plateau est alors reconstruit à neuf.
    var round by rememberSaveable { mutableIntStateOf(0) }
    val current = game
    val record = rememberSoloRecord(Sudoku.recordKey(current?.level ?: menuLevel))
    val currentGame by rememberUpdatedState(current)

    // La partie se met en pause quand l'appli passe en arrière-plan (et donc aussi quand l'écran tourne).
    val lifecycleOwner = remember(context) { findSudokuLifecycleOwner(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && currentGame?.state == SudokuGame.PLAYING) paused = true
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    // Le bouton retour met la partie en pause plutôt que de la quitter par mégarde.
    BackHandler(enabled = current != null && current.state == SudokuGame.PLAYING && !paused) { paused = true }

    /** Crée une grille du niveau [target] (hors du fil principal, pour ne pas figer l'écran) puis lance la partie. */
    fun startGame(target: SudokuLevel) {
        if (generating) return
        record.clearNew()
        generating = true
        scope.launch {
            val puzzle = withContext(Dispatchers.Default) { SudokuEngine.generate(target) }
            game = SudokuGame(target, puzzle.grid, puzzle.solution)
            paused = false
            round++
            generating = false
        }
    }

    fun backToMenu() {
        record.clearNew()
        game = null
        paused = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (current != null) {
            key(round) {
                SudokuPlayfield(
                    game = current,
                    paused = paused,
                    onPausedChange = { paused = it },
                    record = record,
                    onFinished = { if (current.state == SudokuGame.WON) record.submit(current.score) },
                    onReplay = { startGame(current.level) },
                    onChangeLevel = { backToMenu() },
                    generating = generating
                )
            }
        } else {
            SudokuCard {
                Text(
                    text = stringResource(R.string.sudoku_ready_title, name),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.sudoku_ready_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
                    SudokuLevel.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = option == menuLevel,
                                    enabled = !generating,
                                    onClick = { levelIndex = option.ordinal },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = option == menuLevel, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            val label = stringResource(option.labelRes())
                            Text(
                                text = if (option == SudokuLevel.EXPERT) {
                                    stringResource(R.string.sudoku_level_detail_expert, label)
                                } else {
                                    stringResource(R.string.sudoku_level_detail, label, option.clues)
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
                SoloRecordLine(record)
                if (generating) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(
                            if (menuLevel == SudokuLevel.EXPERT) R.string.sudoku_generating_expert else R.string.sudoku_generating
                        ),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Button(onClick = { startGame(menuLevel) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.sudoku_go))
                    }
                }
            }
        }
        RecordSparkles(record)
    }
}

/** La carte de départ : le titre du jeu, ses règles, puis [content]. */
@Composable
private fun SudokuCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = cardSurface()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.game_sudoku),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    RulesButton(
                        gameId = SUDOKU_ID,
                        gameName = stringResource(R.string.game_sudoku),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                content()
            }
        }
    }
}

/**
 * La partie : l'en-tête (niveau, temps, pause), la grille, puis le résultat une fois la grille réussie. [onFinished] est appelé
 * une fois, à la victoire.
 */
@Composable
private fun SudokuPlayfield(
    game: SudokuGame,
    paused: Boolean,
    onPausedChange: (Boolean) -> Unit,
    record: SoloRecord,
    onFinished: () -> Unit,
    onReplay: () -> Unit,
    onChangeLevel: () -> Unit,
    generating: Boolean
) {
    KeepScreenOn()
    // Change à chaque action : la grille se redessine, sans recomposer tout l'écran.
    var revision by remember { mutableIntStateOf(0) }
    var gameState by remember { mutableIntStateOf(game.state) }
    var seconds by remember { mutableIntStateOf(game.seconds) }
    // La case dont la roue est ouverte (-1 : aucune), et si la roue est en mode notes (appui long).
    var wheelCell by remember { mutableIntStateOf(-1) }
    var wheelNotes by remember { mutableStateOf(false) }
    val finishedCallback by rememberUpdatedState(onFinished)

    fun sync() {
        revision++
        gameState = game.state
        seconds = game.seconds
    }

    fun openWheel(index: Int, notes: Boolean) {
        if (paused || game.isFinished || game.isGiven(index)) return
        wheelCell = index
        wheelNotes = notes
    }

    /** Un chiffre touché sur la roue : il s'inscrit (la roue se ferme) ou, en mode notes, il est noté ou retiré des notes. */
    fun pick(digit: Int) {
        val index = wheelCell
        if (index < 0 || paused || game.isFinished) return
        val changed = if (wheelNotes) game.toggleNote(index, digit) else game.enterDigit(index, digit)
        if (changed) sync()
        if (!wheelNotes) wheelCell = -1
        if (game.isFinished) {
            wheelCell = -1
            finishedCallback()
        }
    }

    // La roue se ferme quand la partie passe en pause ou se termine.
    LaunchedEffect(paused, gameState) {
        if (paused || gameState != SudokuGame.PLAYING) wheelCell = -1
    }
    // Le chronomètre ne tourne que partie en cours et hors pause.
    LaunchedEffect(paused, gameState) {
        if (paused || gameState != SudokuGame.PLAYING) return@LaunchedEffect
        var last = System.nanoTime()
        while (true) {
            delay(SUDOKU_TIMER_STEP_MS)
            val now = System.nanoTime()
            game.tick(((now - last) / 1_000_000L).coerceIn(0L, SUDOKU_MAX_TICK_MS))
            last = now
            if (game.seconds != seconds) seconds = game.seconds
        }
    }

    val finished = gameState == SudokuGame.WON

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
                        text = stringResource(game.level.labelRes()),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.sudoku_time, formatSudokuTime(seconds)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { onPausedChange(true) },
                    enabled = !paused && gameState == SudokuGame.PLAYING
                ) {
                    Text(stringResource(R.string.sudoku_pause))
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Une case carrée, la plus grande qui laisse tenir la grille.
            val cell = maxOf(minOf(maxWidth, maxHeight) / Sudoku.SIZE, 0.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(cell * Sudoku.SIZE)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SudokuBoardColor)
            ) {
                if (paused) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.sudoku_paused_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(8.dp)
                        )
                        Button(onClick = { onPausedChange(false) }) {
                            Text(stringResource(R.string.sudoku_resume))
                        }
                    }
                } else {
                    SudokuBoard(
                        game = game,
                        revision = revision,
                        cell = cell,
                        wheelCell = wheelCell,
                        wheelNotes = wheelNotes,
                        onOpen = { index, notes -> openWheel(index, notes) },
                        onPick = { pick(it) },
                        onCloseWheel = { wheelCell = -1 }
                    )
                }
            }
        }

        if (finished) {
            SudokuResult(game = game, record = record, onReplay = onReplay, onChangeLevel = onChangeLevel, busy = generating)
        }
    }
}

/** Le résultat d'une grille réussie, sous la grille qui reste visible. */
@Composable
private fun SudokuResult(game: SudokuGame, record: SoloRecord, onReplay: () -> Unit, onChangeLevel: () -> Unit, busy: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.sudoku_won_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.sudoku_score, game.score),
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
            SoloRecordLine(record)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onReplay, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.sudoku_play_again), textAlign = TextAlign.Center)
                }
                OutlinedButton(onClick = onChangeLevel, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.sudoku_change_level), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/**
 * La grille : une case par composable (TalkBack lit chacune et peut ouvrir la roue), et un geste du doigt sur toute la grille,
 * qui retrouve la case touchée : un toucher court ouvre la roue des chiffres, un appui long la même roue en mode notes. La roue
 * est une fenêtre flottante centrée sur la case ; un toucher en dehors la ferme. [revision] change à chaque action pour
 * redessiner la grille.
 */
@Composable
private fun SudokuBoard(
    game: SudokuGame,
    revision: Int,
    cell: Dp,
    wheelCell: Int,
    wheelNotes: Boolean,
    onOpen: (index: Int, notes: Boolean) -> Unit,
    onPick: (digit: Int) -> Unit,
    onCloseWheel: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val cellPx = with(LocalDensity.current) { cell.toPx() }
    val openWheel by rememberUpdatedState(onOpen)
    val pickLabel = stringResource(R.string.sudoku_action_pick)
    val noteLabel = stringResource(R.string.sudoku_action_note)
    // Les cases ne sont pas un état Compose : on relit leur aspect à chaque [revision]. Le paramètre doit être réellement lu ici,
    // sinon le compilateur Compose ignore son changement et la grille ne se redessine pas après un geste.
    val values = remember(revision, game) { IntArray(Sudoku.CELLS) { game.value(it) } }
    val notes = remember(revision, game) { IntArray(Sudoku.CELLS) { game.noteMask(it) } }
    val conflicts = remember(revision, game) { BooleanArray(Sudoku.CELLS) { game.hasConflict(it) } }

    fun indexAt(offset: Offset): Int {
        val col = (offset.x / cellPx).toInt()
        val row = (offset.y / cellPx).toInt()
        return if (col in 0 until Sudoku.SIZE && row in 0 until Sudoku.SIZE) row * Sudoku.SIZE + col else -1
    }

    Box(
        modifier = Modifier
            .size(cell * Sudoku.SIZE)
            .pointerInput(cellPx, game) {
                detectTapGestures(
                    onTap = { offset ->
                        val index = indexAt(offset)
                        if (index >= 0) openWheel(index, false)
                    },
                    onLongPress = { offset ->
                        val index = indexAt(offset)
                        if (index >= 0 && !game.isGiven(index) && !game.isFinished) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            openWheel(index, true)
                        }
                    }
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            for (r in 0 until Sudoku.SIZE) {
                Row {
                    for (c in 0 until Sudoku.SIZE) {
                        val index = r * Sudoku.SIZE + c
                        SudokuCell(
                            value = values[index],
                            noteMask = notes[index],
                            given = game.isGiven(index),
                            conflict = conflicts[index],
                            selected = index == wheelCell,
                            peer = wheelCell >= 0 && Sudoku.sees(wheelCell, index),
                            size = cell,
                            row = r + 1,
                            col = c + 1,
                            pickLabel = pickLabel,
                            noteLabel = noteLabel,
                            onTap = { openWheel(index, false) },
                            onLongTap = { openWheel(index, true) }
                        )
                    }
                }
            }
        }
        // Les traits de la grille : fins entre les cases, épais entre les blocs de 3 × 3.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val unit = size.width / Sudoku.SIZE
            val thin = unit * 0.015f
            val thick = unit * 0.05f
            for (k in 0..Sudoku.SIZE) {
                val strokeWidth = if (k % 3 == 0) thick else thin
                val pos = k * unit
                drawLine(SudokuLineColor, Offset(pos, 0f), Offset(pos, size.height), strokeWidth = strokeWidth)
                drawLine(SudokuLineColor, Offset(0f, pos), Offset(size.width, pos), strokeWidth = strokeWidth)
            }
        }
        if (wheelCell >= 0) {
            SudokuWheel(
                game = game,
                revision = revision,
                index = wheelCell,
                notesMode = wheelNotes,
                cellPx = cellPx,
                onPick = onPick,
                onDismiss = onCloseWheel
            )
        }
    }
}

/** Une case : son fond, son chiffre ou ses notes, et sa description pour TalkBack (avec les deux actions possibles). */
@Composable
private fun SudokuCell(
    value: Int,
    noteMask: Int,
    given: Boolean,
    conflict: Boolean,
    selected: Boolean,
    peer: Boolean,
    size: Dp,
    row: Int,
    col: Int,
    pickLabel: String,
    noteLabel: String,
    onTap: () -> Unit,
    onLongTap: () -> Unit
) {
    val description = when {
        value != 0 && given -> stringResource(R.string.sudoku_cell_given, row, col, value)
        value != 0 && conflict -> stringResource(R.string.sudoku_cell_conflict, row, col, value)
        value != 0 -> stringResource(R.string.sudoku_cell_value, row, col, value)
        noteMask != 0 -> stringResource(R.string.sudoku_cell_notes, row, col, noteList(noteMask))
        else -> stringResource(R.string.sudoku_cell_empty, row, col)
    }
    val background = when {
        selected -> SudokuSelectedColor
        conflict -> SudokuConflictColor
        peer -> SudokuPeerColor
        given -> SudokuGivenColor
        else -> SudokuCellColor
    }
    Box(
        modifier = Modifier
            .size(size)
            .background(background)
            .clearAndSetSemantics {
                contentDescription = description
                if (!given) {
                    role = Role.Button
                    onClick(label = pickLabel) {
                        onTap()
                        true
                    }
                    onLongClick(label = noteLabel) {
                        onLongTap()
                        true
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (value != 0) {
            Text(
                text = value.toString(),
                color = when {
                    conflict -> SudokuConflictInk
                    given -> SudokuGivenInk
                    else -> SudokuValueInk
                },
                fontWeight = if (given) FontWeight.Bold else FontWeight.Medium,
                fontSize = with(LocalDensity.current) { (size * 0.6f).toSp() }
            )
        } else if (noteMask != 0) {
            val noteSize = size / 3
            val fontSize = with(LocalDensity.current) { (noteSize * 0.8f).toSp() }
            // Les notes à leur place : 1 2 3 en haut, 4 5 6 au milieu, 7 8 9 en bas.
            Column {
                for (r in 0 until 3) {
                    Row {
                        for (c in 0 until 3) {
                            val digit = r * 3 + c + 1
                            Box(modifier = Modifier.size(noteSize), contentAlignment = Alignment.Center) {
                                if (noteMask and (1 shl digit) != 0) {
                                    Text(
                                        text = digit.toString(),
                                        color = SudokuNoteInk,
                                        fontSize = fontSize,
                                        lineHeight = fontSize
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Les chiffres notés d'une case, séparés par des virgules, pour TalkBack. */
private fun noteList(mask: Int): String = (1..9).filter { mask and (1 shl it) != 0 }.joinToString(", ")

/**
 * La roue des chiffres : neuf boutons en anneau autour de la case [index], qui reste visible au milieu. En mode normal un
 * chiffre touché s'inscrit dans la case (le chiffre déjà inscrit est en couleur pleine ; le toucher de nouveau l'efface) ; en
 * mode notes ([notesMode]) il est noté ou retiré des possibilités, et la roue reste ouverte. Un toucher en dehors des boutons
 * (ou le bouton retour) ferme la roue.
 */
@Composable
private fun SudokuWheel(
    game: SudokuGame,
    revision: Int,
    index: Int,
    notesMode: Boolean,
    cellPx: Float,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    // Placée sur le centre de la case, et ramenée dans la fenêtre quand la case est près du bord.
    val positionProvider = remember(index, cellPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val centerX = anchorBounds.left + (Sudoku.colOf(index) + 0.5f) * cellPx
                val centerY = anchorBounds.top + (Sudoku.rowOf(index) + 0.5f) * cellPx
                val x = (centerX - popupContentSize.width / 2f).roundToInt()
                    .coerceIn(0, max(0, windowSize.width - popupContentSize.width))
                val y = (centerY - popupContentSize.height / 2f).roundToInt()
                    .coerceIn(0, max(0, windowSize.height - popupContentSize.height))
                return IntOffset(x, y)
            }
        }
    }
    // Relus à chaque [revision] : les notes et le chiffre de la case changent pendant que la roue reste ouverte.
    val currentValue = remember(revision, game, index) { game.value(index) }
    val currentNotes = remember(revision, game, index) { game.noteMask(index) }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier
                .size(WheelSize)
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
        ) {
            for (k in 0 until Sudoku.SIZE) {
                val digit = k + 1
                // Le 1 en haut, puis les chiffres dans le sens des aiguilles d'une montre.
                val angle = -PI / 2 + 2 * PI * k / Sudoku.SIZE
                val active = if (notesMode) currentNotes and (1 shl digit) != 0 else currentValue == digit
                WheelButton(
                    digit = digit,
                    notesMode = notesMode,
                    active = active,
                    onClick = { onPick(digit) },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset((cos(angle) * WheelRingRadius.value).dp, (sin(angle) * WheelRingRadius.value).dp)
                )
            }
        }
    }
}

/** Un bouton de la roue : plein quand le chiffre est inscrit (ou noté), plus clair sinon ; teinte différente en mode notes. */
@Composable
private fun WheelButton(digit: Int, notesMode: Boolean, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val container = when {
        notesMode && active -> scheme.secondary
        notesMode -> scheme.secondaryContainer
        active -> scheme.primary
        else -> scheme.primaryContainer
    }
    val content = when {
        notesMode && active -> scheme.onSecondary
        notesMode -> scheme.onSecondaryContainer
        active -> scheme.onPrimary
        else -> scheme.onPrimaryContainer
    }
    val description = stringResource(if (notesMode) R.string.sudoku_wheel_note else R.string.sudoku_wheel_digit, digit)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        shadowElevation = 6.dp,
        modifier = modifier
            .size(WheelButtonSize)
            .semantics { contentDescription = description }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = digit.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

private fun findSudokuLifecycleOwner(context: Context): LifecycleOwner? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return null
}
