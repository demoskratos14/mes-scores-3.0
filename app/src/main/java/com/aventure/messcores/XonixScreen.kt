package com.aventure.messcores

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import kotlin.math.abs

// Les étapes de la séance.
private const val XONIX_PHASE_READY = 0 // on attend que le joueur lance la partie
private const val XONIX_PHASE_PLAYING = 1 // le joueur avance, les billes rebondissent
private const val XONIX_PHASE_OVER = 2 // la dernière vie est perdue : la partie est finie

/** Plus long temps d'une image pris en compte : après un à-coup, le joueur ne doit pas sauter plusieurs cases d'un coup. */
private const val XONIX_MAX_FRAME_MS = 100L

private val SEA_COLOR = Color(0xFF0D2B52)
private val LAND_COLOR = Color(0xFF4E9F5B)
private val LAND_EDGE_COLOR = Color(0xFF7CC587)
private val TRAIL_COLOR = Color(0xFFFFD54F)
private val BALL_COLOR = Color(0xFFE53935)
private val SOLDIER_COLOR = Color(0xFF1B1B1F)

/** Garde la partie quand l'écran tourne : son état est rangé dans une liste d'entiers (voir [XonixGame.save]). */
private val XonixGameSaver = listSaver<XonixGame, Int>(
    save = { it.save() },
    restore = { XonixGame.restore(it) }
)

/**
 * Xonix en solo : on part de la terre ferme qui borde le plateau, on trace un chemin dans la mer et on revient sur la
 * terre : la partie de la mer sans bille devient de la terre. On passe au niveau suivant à 75 % de la mer conquise. La
 * partie s'arrête à la dernière vie perdue, et le meilleur score est gardé d'une séance à l'autre. Elle se met en pause
 * quand l'appli passe en arrière-plan. Tout est gardé quand l'écran tourne. [players] : le joueur dont le nom s'affiche
 * au départ.
 */
@Composable
internal fun XonixScreen(players: List<String>) {
    val name = players.firstOrNull().orEmpty().ifEmpty { "1" }
    val context = LocalContext.current
    val game = rememberSaveable(saver = XonixGameSaver) { XonixGame() }
    var phase by rememberSaveable { mutableIntStateOf(XONIX_PHASE_READY) }
    var paused by rememberSaveable { mutableStateOf(false) }
    val record = rememberSoloRecord(XONIX_ID)

    // La partie se met en pause quand l'appli passe en arrière-plan (et donc aussi quand l'écran tourne).
    val lifecycleOwner = remember(context) { findXonixLifecycleOwner(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && phase == XONIX_PHASE_PLAYING) paused = true
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    // Le bouton retour met la partie en pause plutôt que de la quitter par mégarde.
    BackHandler(enabled = phase == XONIX_PHASE_PLAYING && !paused) { paused = true }

    fun startGame() {
        record.clearNew()
        game.reset()
        paused = false
        phase = XONIX_PHASE_PLAYING
    }

    // Pluie de paillettes par-dessus l'écran quand un nouveau record est battu.
    Box(modifier = Modifier.fillMaxSize()) {
        when (phase) {
            XONIX_PHASE_PLAYING -> XonixPlayfield(
                game = game,
                paused = paused,
                onPausedChange = { paused = it },
                onOver = {
                    record.submit(game.score)
                    phase = XONIX_PHASE_OVER
                }
            )
            XONIX_PHASE_OVER -> XonixCard {
                Text(
                    text = stringResource(R.string.xonix_over_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.xonix_score, game.score),
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
                    text = stringResource(R.string.xonix_over_stats, game.level, game.percent),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                SoloRecordLine(record)
                Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.xonix_play_again))
                }
            }
            else -> XonixCard {
                Text(
                    text = stringResource(R.string.xonix_ready_title, name),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.xonix_ready_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                SoloRecordLine(record)
                Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.xonix_go))
                }
            }
        }
        RecordSparkles(record)
    }
}

/** Le plateau des cartes de la séance : le titre du jeu, ses règles, puis [content]. */
@Composable
private fun XonixCard(content: @Composable ColumnScope.() -> Unit) {
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
                        text = stringResource(R.string.game_xonix),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    RulesButton(
                        gameId = XONIX_ID,
                        gameName = stringResource(R.string.game_xonix),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                content()
            }
        }
    }
}

/**
 * La partie en cours : l'en-tête (score, niveau, terrain conquis, vies, pause), le plateau, puis les flèches de
 * direction. On peut aussi glisser le doigt sur le plateau pour tourner, et le toucher pour s'arrêter sur la terre. La
 * partie avance à chaque image tant qu'elle n'est pas en [paused] ; à la dernière vie perdue, [onOver] est appelé une fois.
 */
@Composable
private fun XonixPlayfield(
    game: XonixGame,
    paused: Boolean,
    onPausedChange: (Boolean) -> Unit,
    onOver: () -> Unit
) {
    KeepScreenOn()
    // Change à chaque image ou action : le dessin le lit et se refait, sans recomposer tout l'écran.
    var revision by remember { mutableIntStateOf(0) }
    // Les chiffres de l'en-tête et l'annonce de fin de niveau : ils ne recomposent l'écran que lorsqu'ils changent.
    var score by remember { mutableIntStateOf(game.score) }
    var level by remember { mutableIntStateOf(game.level) }
    var percent by remember { mutableIntStateOf(game.percent) }
    var lives by remember { mutableIntStateOf(game.lives) }
    var levelCleared by remember { mutableStateOf(game.isLevelClearing) }
    val finished by rememberUpdatedState(onOver)

    fun sync() {
        revision++
        score = game.score
        level = game.level
        percent = game.percent
        lives = game.lives
        levelCleared = game.isLevelClearing
    }

    /** Une action du joueur (tourner, s'arrêter), sans effet en pause ou une fois la partie finie. */
    fun act(action: XonixGame.() -> Unit) {
        if (paused || game.isOver) return
        game.action()
        sync()
    }

    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (!game.isOver) {
            val now = withFrameNanos { it }
            val elapsed = ((now - last) / 1_000_000L).coerceIn(0L, XONIX_MAX_FRAME_MS)
            last = now
            game.tick(elapsed)
            sync()
        }
        finished()
    }

    val turn: (Int) -> Unit by rememberUpdatedState({ direction: Int -> act { setDirection(direction) } })
    val halt: () -> Unit by rememberUpdatedState({ act { stop() } })

    // La marge de la barre de navigation (boutons ou geste) garde les commandes du bas hors de ses touches.
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
                        text = stringResource(R.string.xonix_score, score),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.xonix_stats, level, percent, lives),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { onPausedChange(true) }, enabled = !paused) {
                    Text(stringResource(R.string.xonix_pause))
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Une case carrée, la plus grande qui laisse tenir le plateau (24 × 34).
            val cell = maxOf(minOf(maxWidth / Xonix.COLS, maxHeight / Xonix.ROWS), 0.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(cell * Xonix.COLS, cell * Xonix.ROWS)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SEA_COLOR)
                    // Toucher le plateau arrête le joueur quand il est sur la terre.
                    .pointerInput(Unit) { detectTapGestures(onTap = { halt() }) }
                    // Glisser le doigt donne la direction : on peut enchaîner les virages sans le lever.
                    .pointerInput(Unit) {
                        val threshold = 18.dp.toPx()
                        var travelled = Offset.Zero
                        detectDragGestures(
                            onDragStart = { travelled = Offset.Zero },
                            onDrag = { change, amount ->
                                change.consume()
                                travelled += amount
                                if (abs(travelled.x) >= threshold || abs(travelled.y) >= threshold) {
                                    val direction = if (abs(travelled.x) > abs(travelled.y)) {
                                        if (travelled.x > 0f) Xonix.RIGHT else Xonix.LEFT
                                    } else {
                                        if (travelled.y > 0f) Xonix.DOWN else Xonix.UP
                                    }
                                    turn(direction)
                                    travelled = Offset.Zero
                                }
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) { drawXonixBoard(game, revision) }
                if (levelCleared && !paused) {
                    Text(
                        text = stringResource(R.string.xonix_level_clear),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                if (paused) {
                    Column(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.xonix_paused_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(8.dp)
                        )
                        Button(onClick = { onPausedChange(false) }) {
                            Text(stringResource(R.string.xonix_resume))
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            XonixKey(stringResource(R.string.xonix_left), XonixGlyph.LEFT, Modifier.weight(1f)) { turn(Xonix.LEFT) }
            XonixKey(stringResource(R.string.xonix_up), XonixGlyph.UP, Modifier.weight(1f)) { turn(Xonix.UP) }
            XonixKey(stringResource(R.string.xonix_down), XonixGlyph.DOWN, Modifier.weight(1f)) { turn(Xonix.DOWN) }
            XonixKey(stringResource(R.string.xonix_right), XonixGlyph.RIGHT, Modifier.weight(1f)) { turn(Xonix.RIGHT) }
        }
    }
}

/** Les dessins des flèches de direction. */
private enum class XonixGlyph { LEFT, RIGHT, UP, DOWN }

/** Une flèche de direction qui agit dès que le doigt se pose : attendre qu'il se lève ferait perdre un temps précieux. */
@Composable
private fun XonixKey(
    description: String,
    glyph: XonixGlyph,
    modifier: Modifier = Modifier,
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
            .pointerInput(Unit) { detectTapGestures(onPress = { action() }) },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(28.dp)) { drawXonixGlyph(glyph, color) }
    }
}

/** Une ligne brisée passant par ces points, donnés en part de la largeur et de la hauteur du dessin. */
private fun DrawScope.xonixPolyline(vararg points: Pair<Float, Float>): Path {
    val w = size.width
    val h = size.height
    return Path().apply {
        points.forEachIndexed { index, (px, py) ->
            if (index == 0) moveTo(px * w, py * h) else lineTo(px * w, py * h)
        }
    }
}

private fun DrawScope.drawXonixGlyph(glyph: XonixGlyph, color: Color) {
    val stroke = Stroke(width = size.width * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val path = when (glyph) {
        XonixGlyph.LEFT -> xonixPolyline(0.65f to 0.15f, 0.3f to 0.5f, 0.65f to 0.85f)
        XonixGlyph.RIGHT -> xonixPolyline(0.35f to 0.15f, 0.7f to 0.5f, 0.35f to 0.85f)
        XonixGlyph.UP -> xonixPolyline(0.15f to 0.65f, 0.5f to 0.3f, 0.85f to 0.65f)
        XonixGlyph.DOWN -> xonixPolyline(0.15f to 0.35f, 0.5f to 0.7f, 0.85f to 0.35f)
    }
    drawPath(path, color, style = stroke)
}

/**
 * Le plateau : la mer, la terre ferme (avec un liseré clair sur son bord côté mer), le tracé en cours, les mines, les
 * billes puis le joueur. Une vie perdue teinte le plateau de rouge le temps de l'arrêt.
 * [revision] n'est là que pour que le dessin se refasse quand elle change.
 */
private fun DrawScope.drawXonixBoard(game: XonixGame, @Suppress("UNUSED_PARAMETER") revision: Int) {
    val cell = size.width / Xonix.COLS
    drawRect(SEA_COLOR, Offset.Zero, size)
    // Les cases de même sorte d'une ligne sont dessinées d'un seul bloc.
    for (row in 0 until Xonix.ROWS) {
        var col = 0
        while (col < Xonix.COLS) {
            val kind = game.cell(col, row)
            if (kind == Xonix.SEA) {
                col++
                continue
            }
            var end = col + 1
            while (end < Xonix.COLS && game.cell(end, row) == kind) end++
            val color = if (kind == Xonix.LAND) LAND_COLOR else TRAIL_COLOR
            drawRect(color, Offset(col * cell, row * cell), Size((end - col) * cell, cell))
            col = end
        }
    }
    // Un liseré clair le long de la terre, du côté de la mer : on voit d'un coup d'œil où l'on peut tracer.
    val edge = cell * 0.18f
    for (row in 0 until Xonix.ROWS) {
        for (col in 0 until Xonix.COLS) {
            if (game.cell(col, row) != Xonix.LAND) continue
            val left = col * cell
            val top = row * cell
            if (game.cell(col, row - 1) == Xonix.SEA) drawRect(LAND_EDGE_COLOR, Offset(left, top), Size(cell, edge))
            if (game.cell(col, row + 1) == Xonix.SEA) drawRect(LAND_EDGE_COLOR, Offset(left, top + cell - edge), Size(cell, edge))
            if (game.cell(col - 1, row) == Xonix.SEA) drawRect(LAND_EDGE_COLOR, Offset(left, top), Size(edge, cell))
            if (game.cell(col + 1, row) == Xonix.SEA) drawRect(LAND_EDGE_COLOR, Offset(left + cell - edge, top), Size(edge, cell))
        }
    }
    val inset = cell * 0.08f
    game.soldiers.forEach { soldier ->
        val topLeft = Offset(soldier.x * cell + inset, soldier.y * cell + inset)
        val side = cell - 2f * inset
        drawRect(SOLDIER_COLOR, topLeft, Size(side, side))
        drawRect(TRAIL_COLOR, topLeft + Offset(side * 0.3f, side * 0.3f), Size(side * 0.4f, side * 0.4f))
    }
    game.balls.forEach { ball ->
        val center = Offset(ball.x * cell, ball.y * cell)
        drawCircle(Color.White, cell * 0.62f, center)
        drawCircle(BALL_COLOR, cell * 0.46f, center)
    }
    // Le joueur : un carré clair cerclé de sombre, bien visible sur la terre comme sur son tracé.
    val player = Offset(game.px * cell + inset, game.py * cell + inset)
    val playerSide = cell - 2f * inset
    drawRect(Color(0xFF102A43), player, Size(playerSide, playerSide))
    drawRect(Color.White, player + Offset(playerSide * 0.18f, playerSide * 0.18f), Size(playerSide * 0.64f, playerSide * 0.64f))
    if (game.isHurt) drawRect(Color.Red.copy(alpha = 0.3f), Offset.Zero, size)
}

private fun findXonixLifecycleOwner(context: Context): LifecycleOwner? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return null
}
