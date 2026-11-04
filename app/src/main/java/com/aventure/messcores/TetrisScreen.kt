package com.aventure.messcores

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Les étapes de la séance.
private const val TETRIS_PHASE_READY = 0 // on attend que le joueur lance la partie
private const val TETRIS_PHASE_PLAYING = 1 // les pièces tombent
private const val TETRIS_PHASE_OVER = 2 // la pile a atteint le haut : la partie est finie

/** Plus long temps d'une image pris en compte : après un à-coup, les pièces ne doivent pas sauter plusieurs cases d'un coup. */
private const val MAX_FRAME_MS = 100L

/** Un bouton maintenu répète son action : après ce délai, puis à cette cadence. */
private const val REPEAT_DELAY_MS = 170L
private const val REPEAT_MS = 55L

/** Les couleurs des pièces, dans l'ordre de [Tetromino] : I, O, T, S, Z, J, L. */
private val PIECE_COLORS = listOf(
    Color(0xFF00BCD4), Color(0xFFFDD835), Color(0xFF8E24AA), Color(0xFF43A047),
    Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFFFB8C00)
)

/** Garde la partie quand l'écran tourne : son état est rangé dans une liste d'entiers (voir [TetrisGame.save]). */
private val TetrisGameSaver = listSaver<TetrisGame, Int>(
    save = { it.save() },
    restore = { TetrisGame.restore(it) }
)

/**
 * Tetris en solo : des pièces tombent dans un plateau de 10 colonnes ; on les déplace, on les fait pivoter et on les
 * laisse tomber pour former des lignes complètes, qui disparaissent. La partie s'arrête quand la pile atteint le haut,
 * et le meilleur score est gardé d'une séance à l'autre. Elle se met en pause quand l'appli passe en arrière-plan.
 * Tout est gardé quand l'écran tourne. [players] : le joueur dont le nom s'affiche au départ.
 */
@Composable
internal fun TetrisScreen(players: List<String>) {
    val name = players.firstOrNull().orEmpty().ifEmpty { "1" }
    val context = LocalContext.current
    val game = rememberSaveable(saver = TetrisGameSaver) { TetrisGame() }
    var phase by rememberSaveable { mutableIntStateOf(TETRIS_PHASE_READY) }
    var paused by rememberSaveable { mutableStateOf(false) }
    val record = rememberSoloRecord(TETRIS_ID)

    // La partie se met en pause quand l'appli passe en arrière-plan (et donc aussi quand l'écran tourne).
    val lifecycleOwner = remember(context) { findTetrisLifecycleOwner(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && phase == TETRIS_PHASE_PLAYING) paused = true
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    // Le bouton retour met la partie en pause plutôt que de la quitter par mégarde.
    BackHandler(enabled = phase == TETRIS_PHASE_PLAYING && !paused) { paused = true }

    fun startGame() {
        record.clearNew()
        game.reset()
        paused = false
        phase = TETRIS_PHASE_PLAYING
    }

    when (phase) {
        TETRIS_PHASE_PLAYING -> TetrisPlayfield(
            game = game,
            paused = paused,
            onPausedChange = { paused = it },
            onOver = {
                record.submit(game.score)
                phase = TETRIS_PHASE_OVER
            }
        )
        TETRIS_PHASE_OVER -> TetrisCard {
            Text(
                text = stringResource(R.string.tetris_over_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.tetris_score, game.score),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = highlightContent(),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(highlightContainer())
                    .padding(12.dp)
            )
            Text(
                text = stringResource(R.string.tetris_stats, game.lines, game.level),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            SoloRecordLine(record)
            Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.tetris_play_again))
            }
        }
        else -> TetrisCard {
            Text(
                text = stringResource(R.string.tetris_ready_title, name),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.tetris_ready_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            SoloRecordLine(record)
            Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.tetris_go))
            }
        }
    }
}

/** Le plateau des cartes de la séance : le titre du jeu, ses règles, puis [content]. */
@Composable
private fun TetrisCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
                        text = stringResource(R.string.game_tetris),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    RulesButton(
                        gameId = TETRIS_ID,
                        gameName = stringResource(R.string.game_tetris),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                content()
            }
        }
    }
}

/**
 * La partie en cours : l'en-tête (score, lignes, niveau, pause), le plateau avec la réserve et les pièces à venir sur le
 * côté, puis les boutons de commande. La partie avance à chaque image tant qu'elle n'est pas en [paused] ; quand la pile
 * atteint le haut, [onOver] est appelé une fois.
 */
@Composable
private fun TetrisPlayfield(
    game: TetrisGame,
    paused: Boolean,
    onPausedChange: (Boolean) -> Unit,
    onOver: () -> Unit
) {
    KeepScreenOn()
    // Change à chaque image ou action : le dessin le lit et se refait, sans recomposer tout l'écran.
    var revision by remember { mutableIntStateOf(0) }
    // Les chiffres de l'en-tête : ils ne recomposent l'écran que lorsqu'ils changent.
    var score by remember { mutableIntStateOf(game.score) }
    var lines by remember { mutableIntStateOf(game.lines) }
    var level by remember { mutableIntStateOf(game.level) }
    val finished by rememberUpdatedState(onOver)

    fun sync() {
        revision++
        score = game.score
        lines = game.lines
        level = game.level
    }

    /** Une action du joueur (déplacer, pivoter…), sans effet en pause ou une fois la partie finie. */
    fun act(action: TetrisGame.() -> Unit) {
        if (paused || game.isOver) return
        game.action()
        sync()
    }

    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (!game.isOver) {
            val now = withFrameNanos { it }
            val elapsed = ((now - last) / 1_000_000L).coerceIn(0L, MAX_FRAME_MS)
            last = now
            game.tick(elapsed)
            sync()
        }
        finished()
    }

    val holdLabel = stringResource(R.string.tetris_hold)

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.tetris_score, score),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.tetris_stats, lines, level),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { onPausedChange(true) }, enabled = !paused) {
                    Text(stringResource(R.string.tetris_pause))
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val sideWidth = 88.dp
            val gap = 8.dp
            // Une case carrée, la plus grande qui laisse tenir le plateau (10 × 20) et la colonne de côté.
            val cell = maxOf(minOf((maxWidth - sideWidth - gap) / Tetris.COLS, maxHeight / Tetris.ROWS), 0.dp)
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(cell * Tetris.COLS, cell * Tetris.ROWS)
                        .clip(RoundedCornerShape(8.dp))
                        .background(cardSurface())
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) { drawTetrisBoard(game, revision) }
                    if (paused) {
                        Column(
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.tetris_paused_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(8.dp)
                            )
                            Button(onClick = { onPausedChange(false) }) {
                                Text(stringResource(R.string.tetris_resume))
                            }
                        }
                    }
                }
                Column(modifier = Modifier.width(sideWidth), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = holdLabel, style = MaterialTheme.typography.labelLarge)
                    // Toucher la réserve met la pièce en cours de côté.
                    Canvas(
                        modifier = Modifier
                            .size(sideWidth, 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(cardSurface())
                            .clickable(onClickLabel = holdLabel, role = Role.Button) { act { hold() } }
                    ) { drawTetrisHold(game, revision) }
                    Text(text = stringResource(R.string.tetris_next), style = MaterialTheme.typography.labelLarge)
                    Canvas(
                        modifier = Modifier
                            .size(sideWidth, 132.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(cardSurface())
                    ) { drawTetrisNext(game, revision) }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TetrisKey(stringResource(R.string.tetris_left), TetrisGlyph.LEFT, Modifier.weight(1f), repeating = true) { act { moveLeft() } }
            TetrisKey(stringResource(R.string.tetris_right), TetrisGlyph.RIGHT, Modifier.weight(1f), repeating = true) { act { moveRight() } }
            TetrisKey(stringResource(R.string.tetris_soft_drop), TetrisGlyph.DOWN, Modifier.weight(1f), repeating = true) { act { softDrop() } }
            TetrisKey(stringResource(R.string.tetris_rotate), TetrisGlyph.ROTATE, Modifier.weight(1f)) { act { rotate() } }
            TetrisKey(stringResource(R.string.tetris_hard_drop), TetrisGlyph.DROP, Modifier.weight(1f)) { act { hardDrop() } }
        }
    }
}

/** Les dessins des boutons de commande. */
private enum class TetrisGlyph { LEFT, RIGHT, DOWN, ROTATE, DROP }

/**
 * Un bouton de commande qui agit dès que le doigt se pose : attendre qu'il se lève ferait perdre un temps précieux. Avec
 * [repeating], le maintenir répète l'action ([REPEAT_DELAY_MS] puis toutes les [REPEAT_MS]).
 */
@Composable
private fun TetrisKey(
    description: String,
    glyph: TetrisGlyph,
    modifier: Modifier = Modifier,
    repeating: Boolean = false,
    onAction: () -> Unit
) {
    val action by rememberUpdatedState(onAction)
    val color = MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
                // Pour TalkBack et les autres aides : l'action sans passer par le geste.
                onClick(label = description) {
                    action()
                    true
                }
            }
            .pointerInput(repeating) {
                detectTapGestures(onPress = {
                    action()
                    if (repeating) {
                        coroutineScope {
                            val repeater = launch {
                                delay(REPEAT_DELAY_MS)
                                while (true) {
                                    action()
                                    delay(REPEAT_MS)
                                }
                            }
                            tryAwaitRelease()
                            repeater.cancel()
                        }
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(28.dp)) { drawTetrisGlyph(glyph, color) }
    }
}

/** Une ligne brisée passant par ces points, donnés en part de la largeur et de la hauteur du dessin. */
private fun DrawScope.polyline(vararg points: Pair<Float, Float>): Path {
    val w = size.width
    val h = size.height
    return Path().apply {
        points.forEachIndexed { index, (px, py) ->
            if (index == 0) moveTo(px * w, py * h) else lineTo(px * w, py * h)
        }
    }
}

private fun DrawScope.drawTetrisGlyph(glyph: TetrisGlyph, color: Color) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (glyph) {
        TetrisGlyph.LEFT -> drawPath(polyline(0.65f to 0.15f, 0.3f to 0.5f, 0.65f to 0.85f), color, style = stroke)
        TetrisGlyph.RIGHT -> drawPath(polyline(0.35f to 0.15f, 0.7f to 0.5f, 0.35f to 0.85f), color, style = stroke)
        TetrisGlyph.DOWN -> drawPath(polyline(0.15f to 0.35f, 0.5f to 0.7f, 0.85f to 0.35f), color, style = stroke)
        TetrisGlyph.DROP -> {
            // Un chevron qui tombe sur une ligne : la pièce arrive au fond.
            drawPath(polyline(0.15f to 0.12f, 0.5f to 0.47f, 0.85f to 0.12f), color, style = stroke)
            drawLine(color, Offset(w * 0.15f, h * 0.85f), Offset(w * 0.85f, h * 0.85f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
        }
        TetrisGlyph.ROTATE -> {
            // Trois quarts de cercle dans le sens horaire, terminés par une pointe de flèche.
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(w * 0.2f, h * 0.2f),
                size = Size(w * 0.6f, h * 0.6f),
                style = stroke
            )
            val head = Path().apply {
                moveTo(w * 0.05f, h * 0.5f)
                lineTo(w * 0.35f, h * 0.5f)
                lineTo(w * 0.2f, h * 0.25f)
                close()
            }
            drawPath(head, color)
        }
    }
}

/** Une case posée : un carré de la couleur de sa pièce, avec un reflet en haut et une ombre en bas pour lui donner du relief. */
private fun DrawScope.drawBlock(left: Float, top: Float, cell: Float, color: Color) {
    val inset = cell * 0.04f
    val side = cell - 2f * inset
    val corner = Offset(left + inset, top + inset)
    drawRect(color, corner, Size(side, side))
    drawRect(Color.White.copy(alpha = 0.28f), corner, Size(side, side * 0.18f))
    drawRect(Color.Black.copy(alpha = 0.22f), Offset(corner.x, corner.y + side * 0.82f), Size(side, side * 0.18f))
}

/**
 * Le plateau : sa grille, les cases posées, la pièce « fantôme » (son contour là où elle tombera) puis la pièce en cours.
 * [revision] n'est là que pour que le dessin se refasse quand elle change.
 */
private fun DrawScope.drawTetrisBoard(game: TetrisGame, @Suppress("UNUSED_PARAMETER") revision: Int) {
    val cell = size.width / Tetris.COLS
    val grid = Color.Gray.copy(alpha = 0.18f)
    for (col in 1 until Tetris.COLS) drawLine(grid, Offset(col * cell, 0f), Offset(col * cell, size.height), strokeWidth = 1f)
    for (row in 1 until Tetris.ROWS) drawLine(grid, Offset(0f, row * cell), Offset(size.width, row * cell), strokeWidth = 1f)
    for (row in 0 until Tetris.ROWS) {
        for (col in 0 until Tetris.COLS) {
            val kind = game.cell(col, row)
            if (kind != 0) drawBlock(col * cell, row * cell, cell, PIECE_COLORS[kind - 1])
        }
    }
    if (game.isOver) return
    val color = PIECE_COLORS[game.current]
    val line = cell * 0.08f
    game.ghostCells().forEach { (col, row) ->
        drawRect(
            color = color.copy(alpha = 0.7f),
            topLeft = Offset(col * cell + line / 2f, row * cell + line / 2f),
            size = Size(cell - line, cell - line),
            style = Stroke(width = line)
        )
    }
    game.pieceCells().forEach { (col, row) -> drawBlock(col * cell, row * cell, cell, color) }
}

/**
 * Une pièce [kind] dessinée au centre d'un emplacement de [slotHeight] pixels de haut commençant en [top], dans le dessin
 * qui la contient : les cases mesurent un cinquième de la largeur, ce qui laisse de la marge autour d'un « I » (4 cases).
 */
private fun DrawScope.drawMiniPiece(kind: Int, top: Float, slotHeight: Float, alpha: Float = 1f) {
    val cells = Tetromino.entries[kind].cells(0)
    val cell = size.width / 5f
    val minX = cells.minOf { it.first }
    val maxX = cells.maxOf { it.first }
    val minY = cells.minOf { it.second }
    val maxY = cells.maxOf { it.second }
    val left = (size.width - (maxX - minX + 1) * cell) / 2f - minX * cell
    val upper = top + (slotHeight - (maxY - minY + 1) * cell) / 2f - minY * cell
    cells.forEach { (cx, cy) -> drawBlock(left + cx * cell, upper + cy * cell, cell, PIECE_COLORS[kind].copy(alpha = alpha)) }
}

/** La réserve : la pièce mise de côté, grisée quand on ne peut plus l'échanger avant la prochaine pièce. */
private fun DrawScope.drawTetrisHold(game: TetrisGame, @Suppress("UNUSED_PARAMETER") revision: Int) {
    if (game.held == TetrisGame.NONE) return
    drawMiniPiece(game.held, 0f, size.height, alpha = if (game.holdUsed) 0.35f else 1f)
}

/** Les pièces à venir, de la plus proche (en haut) à la plus lointaine. */
private fun DrawScope.drawTetrisNext(game: TetrisGame, @Suppress("UNUSED_PARAMETER") revision: Int) {
    val slot = size.height / Tetris.NEXT_COUNT
    game.next(Tetris.NEXT_COUNT).forEachIndexed { index, kind -> drawMiniPiece(kind, index * slot, slot) }
}

private fun findTetrisLifecycleOwner(context: Context): LifecycleOwner? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return null
}
