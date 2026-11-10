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
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// Les étapes de la séance.
private const val SNAKE_PHASE_READY = 0 // on attend que le joueur lance la partie
private const val SNAKE_PHASE_PLAYING = 1 // le serpent avance
private const val SNAKE_PHASE_OVER = 2 // le serpent s'est mordu, ou remplit le plateau : la partie est finie

/** Plus long temps d'une image pris en compte : après un à-coup, le serpent ne doit pas sauter plusieurs cases d'un coup. */
private const val MAX_FRAME_MS = 100L

/** Temps d'attente, en millisecondes, entre un plateau rempli et la reprise du jeu sur le nouveau plateau. */
private const val CELEBRATION_MS = 3000L

/** Distance glissée (en dp) à partir de laquelle un geste du doigt compte pour un virage. */
private const val SWIPE_DP = 18

private val SnakeBoardColor = Color(0xFF1B2A1F)
private val SnakeBoardAltColor = Color(0xFF203125)
// Le serpent cuivré : des taches plus sombres en alternance sur un fond orangé, cerné de brun très foncé.
private val SnakeBodyColor = Color(0xFFC8652F)
private val SnakeBodyAltColor = Color(0xFFE09050)
private val SnakeHeadColor = Color(0xFFDC8E52)
private val SnakeOutlineColor = Color(0xFF2A1608)
private val SnakeTongueColor = Color(0xFFFF6FA3)
private val MouseFurColor = Color(0xFFB8B0A8)
private val MouseInnerColor = Color(0xFFF4A6B8)

/** Garde la partie quand l'écran tourne : son état est rangé dans une liste d'entiers (voir [SnakeGame.save]). */
private val SnakeGameSaver = listSaver<SnakeGame, Int>(
    save = { it.save() },
    restore = { SnakeGame.restore(it) }
)

/**
 * Snake en solo : un serpent qui avance sans cesse sur un plateau dont les bords se referment (sortir à droite fait
 * revenir à gauche, et de même en haut et en bas). On le guide en glissant le doigt ou avec les flèches pour manger les
 * souris qui apparaissent : chacune le fait grandir d'une case. La partie s'arrête quand il se mord, et le meilleur
 * score est gardé d'une séance à l'autre. S'il remplit tout le plateau, une pluie de paillettes tombe, un bonus est ajouté et le
 * jeu repart sur un nouveau plateau, plus vite. Elle se met en pause quand l'appli passe en arrière-plan.
 * Tout est gardé quand l'écran tourne. [players] : le joueur dont le nom s'affiche au départ.
 */
@Composable
internal fun SnakeScreen(players: List<String>) {
    val name = players.firstOrNull().orEmpty().ifEmpty { "1" }
    val context = LocalContext.current
    val game = rememberSaveable(saver = SnakeGameSaver) { SnakeGame() }
    var phase by rememberSaveable { mutableIntStateOf(SNAKE_PHASE_READY) }
    var paused by rememberSaveable { mutableStateOf(false) }
    // Nombre de plateaux remplis : à chaque hausse, la pluie de paillettes tombe.
    var cleared by rememberSaveable { mutableIntStateOf(game.boardsCleared) }
    val record = rememberSoloRecord(SNAKE_ID)

    // La partie se met en pause quand l'appli passe en arrière-plan (et donc aussi quand l'écran tourne).
    val lifecycleOwner = remember(context) { findSnakeLifecycleOwner(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && phase == SNAKE_PHASE_PLAYING) paused = true
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    // Le bouton retour met la partie en pause plutôt que de la quitter par mégarde.
    BackHandler(enabled = phase == SNAKE_PHASE_PLAYING && !paused) { paused = true }

    fun startGame() {
        record.clearNew()
        game.reset()
        cleared = 0
        paused = false
        phase = SNAKE_PHASE_PLAYING
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (phase) {
            SNAKE_PHASE_PLAYING -> SnakePlayfield(
                game = game,
                paused = paused,
                onPausedChange = { paused = it },
                onCleared = { cleared = it },
                onOver = {
                    record.submit(game.score)
                    phase = SNAKE_PHASE_OVER
                }
            )
            SNAKE_PHASE_OVER -> SnakeCard {
                Text(
                    text = stringResource(R.string.snake_over_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.snake_score, game.score),
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
                    text = stringResource(R.string.snake_stats, game.eaten, game.length),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                SoloRecordLine(record)
                Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.snake_play_again))
                }
            }
            else -> SnakeCard {
                Text(
                    text = stringResource(R.string.snake_ready_title, name),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.snake_ready_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                SoloRecordLine(record)
                Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.snake_go))
                }
            }
        }
        RecordSparkles(record)
        SparkleRain(cleared)
    }
}

/** Le plateau des cartes de la séance : le titre du jeu, ses règles, puis [content]. */
@Composable
private fun SnakeCard(content: @Composable ColumnScope.() -> Unit) {
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
                        text = stringResource(R.string.game_snake),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    RulesButton(
                        gameId = SNAKE_ID,
                        gameName = stringResource(R.string.game_snake),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                content()
            }
        }
    }
}

/**
 * La partie en cours : l'en-tête (score, souris mangées, longueur, pause), le plateau, puis les flèches. La partie avance
 * à chaque image tant qu'elle n'est pas en [paused] ; quand le serpent se mord, [onOver] est appelé une fois.
 */
@Composable
private fun SnakePlayfield(
    game: SnakeGame,
    paused: Boolean,
    onPausedChange: (Boolean) -> Unit,
    onCleared: (Int) -> Unit,
    onOver: () -> Unit
) {
    KeepScreenOn()
    // Change à chaque image ou action : le dessin le lit et se refait, sans recomposer tout l'écran.
    var revision by remember { mutableIntStateOf(0) }
    // Les chiffres de l'en-tête : ils ne recomposent l'écran que lorsqu'ils changent.
    var score by remember { mutableIntStateOf(game.score) }
    var eaten by remember { mutableIntStateOf(game.eaten) }
    var length by remember { mutableIntStateOf(game.length) }
    val finished by rememberUpdatedState(onOver)
    val clearedChanged by rememberUpdatedState(onCleared)
    // Vrai pendant que la fête du plateau rempli se joue : le serpent attend un instant avant de repartir.
    var celebrating by remember { mutableStateOf(false) }

    fun sync() {
        revision++
        score = game.score
        eaten = game.eaten
        length = game.length
    }

    /** Un virage demandé par le joueur, sans effet en pause ou une fois la partie finie. */
    fun turn(dir: SnakeDir) {
        if (paused || game.isOver) return
        game.turn(dir)
    }

    LaunchedEffect(celebrating) {
        if (!celebrating) return@LaunchedEffect
        delay(CELEBRATION_MS)
        celebrating = false
    }

    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        var last = withFrameNanos { it }
        var seenCleared = game.boardsCleared
        while (!game.isOver) {
            val now = withFrameNanos { it }
            val elapsed = ((now - last) / 1_000_000L).coerceIn(0L, MAX_FRAME_MS)
            last = now
            if (celebrating) continue
            game.tick(elapsed)
            sync()
            if (game.boardsCleared != seenCleared) {
                // Le plateau vient d'être rempli : le jeu est déjà reparti sur un nouveau plateau, plus rapide.
                seenCleared = game.boardsCleared
                clearedChanged(seenCleared)
                celebrating = true
            }
        }
        finished()
    }

    val swipePx = with(LocalDensity.current) { SWIPE_DP.dp.toPx() }

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
                        text = stringResource(R.string.snake_score, score),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.snake_stats, eaten, length),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { onPausedChange(true) }, enabled = !paused) {
                    Text(stringResource(R.string.snake_pause))
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Une case carrée, la plus grande qui laisse tenir le plateau (12 × 18).
            val cell = maxOf(minOf(maxWidth / Snake.COLS, maxHeight / Snake.ROWS), 0.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(cell * Snake.COLS, cell * Snake.ROWS)
                    .clip(RoundedCornerShape(8.dp))
                    // Glisser le doigt sur le plateau fait tourner le serpent ; on peut enchaîner sans lever le doigt.
                    .pointerInput(swipePx) {
                        var dx = 0f
                        var dy = 0f
                        detectDragGestures(
                            onDragStart = {
                                dx = 0f
                                dy = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dx += amount.x
                                dy += amount.y
                                if (maxOf(abs(dx), abs(dy)) >= swipePx) {
                                    val dir = if (abs(dx) > abs(dy)) {
                                        if (dx > 0) SnakeDir.RIGHT else SnakeDir.LEFT
                                    } else {
                                        if (dy > 0) SnakeDir.DOWN else SnakeDir.UP
                                    }
                                    turn(dir)
                                    dx = 0f
                                    dy = 0f
                                }
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) { drawSnakeBoard(game, revision) }
                if (celebrating && !paused) {
                    Text(
                        text = stringResource(R.string.snake_cleared_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                if (paused) {
                    Column(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.snake_paused_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(8.dp)
                        )
                        Button(onClick = { onPausedChange(false) }) {
                            Text(stringResource(R.string.snake_resume))
                        }
                    }
                }
            }
        }

        // Les quatre flèches en croix : haut au-dessus, puis gauche, bas et droite.
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(modifier = Modifier.weight(1f))
                SnakeKey(stringResource(R.string.snake_up), SnakeDir.UP, Modifier.weight(1f)) { turn(SnakeDir.UP) }
                Spacer(modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SnakeKey(stringResource(R.string.snake_left), SnakeDir.LEFT, Modifier.weight(1f)) { turn(SnakeDir.LEFT) }
                SnakeKey(stringResource(R.string.snake_down), SnakeDir.DOWN, Modifier.weight(1f)) { turn(SnakeDir.DOWN) }
                SnakeKey(stringResource(R.string.snake_right), SnakeDir.RIGHT, Modifier.weight(1f)) { turn(SnakeDir.RIGHT) }
            }
        }
    }
}

/**
 * Une flèche de commande qui agit dès que le doigt se pose : attendre qu'il se lève ferait perdre un temps précieux,
 * et le serpent n'attend pas.
 */
@Composable
private fun SnakeKey(
    description: String,
    dir: SnakeDir,
    modifier: Modifier = Modifier,
    onAction: () -> Unit
) {
    val action by rememberUpdatedState(onAction)
    val color = MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        modifier = modifier
            .height(56.dp)
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
            .pointerInput(Unit) {
                detectTapGestures(onPress = { action() })
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(28.dp)) { drawSnakeArrow(dir, color) }
    }
}

/** Un chevron tourné vers [dir]. */
private fun DrawScope.drawSnakeArrow(dir: SnakeDir, color: Color) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    // Dessiné vers le haut, puis tourné autour du centre.
    val chevron = Path().apply {
        moveTo(w * 0.15f, h * 0.65f)
        lineTo(w * 0.5f, h * 0.3f)
        lineTo(w * 0.85f, h * 0.65f)
    }
    rotate(degrees = dir.ordinal * 90f, pivot = center) { drawPath(chevron, color, style = stroke) }
}

/** Le centre de la case de numéro [index] dans un plateau dont une case mesure [cell] pixels. */
private fun cellCenter(index: Int, cell: Float): Offset =
    Offset((index % Snake.COLS + 0.5f) * cell, (index / Snake.COLS + 0.5f) * cell)

/**
 * Le plateau : un damier discret, un liseré pointillé qui rappelle que les bords se referment, la souris puis le serpent.
 * [revision] n'est là que pour que le dessin se refasse quand elle change.
 */
private fun DrawScope.drawSnakeBoard(game: SnakeGame, @Suppress("UNUSED_PARAMETER") revision: Int) {
    val cell = size.width / Snake.COLS
    for (row in 0 until Snake.ROWS) {
        for (col in 0 until Snake.COLS) {
            drawRect(
                color = if ((row + col) % 2 == 0) SnakeBoardColor else SnakeBoardAltColor,
                topLeft = Offset(col * cell, row * cell),
                size = Size(cell, cell)
            )
        }
    }
    // Le liseré en pointillés : ici, le plateau se prolonge de l'autre côté.
    val dash = cell * 0.35f
    drawRect(
        color = Color(0xFF9CCC65).copy(alpha = 0.55f),
        topLeft = Offset.Zero,
        size = size,
        style = Stroke(width = cell * 0.1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)))
    )
    if (game.mouse != SnakeGame.NONE) drawMouse(cellCenter(game.mouse, cell), cell)
    drawSnake(game, cell)
}

/**
 * Le serpent, dans le style d'un dessin : un corps orangé à taches alternées, cerné d'un trait foncé, qui relie chaque
 * case à la suivante (même à travers un bord) et s'affine vers la queue ; la tête est un peu plus grosse, avec de gros
 * yeux ronds et une langue rose fourchue qui sort et rentre. Le contour est dessiné en premier pour tout le serpent, puis le remplissage par-dessus : les jonctions
 * entre cases restent propres et seul le pourtour est cerné.
 */
private fun DrawScope.drawSnake(game: SnakeGame, cell: Float) {
    val cells = game.bodyCells()
    val half = cell * 0.5f
    val outline = cell * 0.07f
    val n = cells.size
    /** Épaisseur de la case de rang [i] : pleine sur le corps, plus fine sur les quatre dernières cases, plus large à la tête. */
    fun thickness(i: Int): Float {
        if (i == 0) return cell * 0.86f
        val fromTail = n - 1 - i
        val taper = if (n > 1) (0.45f + 0.18f * fromTail).coerceAtMost(1f) else 1f
        return cell * 0.74f * taper
    }
    // La langue rose, fourchue, dessinée avant le serpent pour que la tête la recouvre à sa base : elle sort et rentre à chaque case.
    run {
        val start = cellCenter(cells.first(), cell)
        val ahead = Offset(game.direction.dx.toFloat(), game.direction.dy.toFloat())
        val across = Offset(-ahead.y, ahead.x)
        val length = cell * (0.3f + 0.25f * sin(game.progress * PI.toFloat()))
        val base = start + ahead * (cell * 0.38f)
        val fork = base + ahead * (length * 0.6f)
        val width = cell * 0.08f
        drawLine(SnakeTongueColor, base, fork, strokeWidth = width, cap = StrokeCap.Round)
        listOf(-1f, 1f).forEach { sign ->
            val tip = fork + ahead * (length * 0.4f) + across * (sign * length * 0.28f)
            drawLine(SnakeTongueColor, fork, tip, strokeWidth = width, cap = StrokeCap.Round)
        }
    }
    for (pass in 0..1) {
        val fill = pass == 1
        val grow = if (fill) 0f else outline * 2f
        // Du bout de la queue vers la tête : la tête est dessinée en dernier, par-dessus.
        for (i in n - 1 downTo 0) {
            val color = if (!fill) SnakeOutlineColor else if (i == 0) SnakeHeadColor else if (i % 2 == 0) SnakeBodyColor else SnakeBodyAltColor
            val center = cellCenter(cells[i], cell)
            val thick = thickness(i) + grow
            drawCircle(color, thick / 2f, center)
            // Un raccord vers la case suivante (vers la queue), et un autre depuis la précédente : chacun s'arrête au bord de
            // sa case, si bien qu'à travers un bord du plateau le serpent ressort de l'autre côté sans trait traversant l'écran.
            if (i + 1 < n) connector(cells[i], cells[i + 1], center, half, thick, color)
            if (i > 0) connector(cells[i], cells[i - 1], center, half, thick, color)
        }
    }
    val head = cellCenter(cells.first(), cell)
    val forward = Offset(game.direction.dx.toFloat(), game.direction.dy.toFloat())
    val side = Offset(-forward.y, forward.x)
    val eye = cell * 0.13f
    listOf(-1f, 1f).forEach { sign ->
        val at = head + forward * (cell * 0.14f) + side * (sign * cell * 0.22f)
        drawCircle(SnakeOutlineColor, eye * 1.3f, at)
        drawCircle(Color.White, eye * 1.05f, at)
        drawCircle(Color.Black, eye * 0.72f, at + forward * (eye * 0.3f))
        drawCircle(Color.White, eye * 0.22f, at + forward * (eye * 0.3f) - side * (sign * eye * 0.2f) - forward * (eye * 0.25f))
    }
}

/** Un raccord de la case [from] vers sa voisine [to] : un rectangle qui va du centre de [from] jusqu'au bord de sa case. */
private fun DrawScope.connector(from: Int, to: Int, center: Offset, half: Float, thick: Float, color: Color) {
    val dir = SnakeDir.entries.firstOrNull {
        val (c, r) = Snake.wrap(from % Snake.COLS, from / Snake.COLS, it)
        r * Snake.COLS + c == to
    } ?: return
    val length = half
    val topLeft = when (dir) {
        SnakeDir.UP -> Offset(center.x - thick / 2f, center.y - length)
        SnakeDir.DOWN -> Offset(center.x - thick / 2f, center.y)
        SnakeDir.LEFT -> Offset(center.x - length, center.y - thick / 2f)
        SnakeDir.RIGHT -> Offset(center.x, center.y - thick / 2f)
    }
    val rect = if (dir == SnakeDir.UP || dir == SnakeDir.DOWN) Size(thick, length) else Size(length, thick)
    drawRect(color, topLeft, rect)
}

/** Une petite souris vue de dessus : un corps rond, deux oreilles roses, des yeux, un museau et une queue. */
private fun DrawScope.drawMouse(center: Offset, cell: Float) {
    val tail = Path().apply {
        moveTo(center.x, center.y + cell * 0.25f)
        cubicTo(
            center.x + cell * 0.05f, center.y + cell * 0.5f,
            center.x + cell * 0.3f, center.y + cell * 0.35f,
            center.x + cell * 0.38f, center.y + cell * 0.46f
        )
    }
    drawPath(tail, MouseInnerColor, style = Stroke(width = cell * 0.07f, cap = StrokeCap.Round))
    drawCircle(MouseFurColor, cell * 0.3f, center + Offset(0f, cell * 0.04f))
    listOf(-1f, 1f).forEach { sign ->
        val ear = center + Offset(sign * cell * 0.22f, -cell * 0.22f)
        drawCircle(MouseFurColor, cell * 0.15f, ear)
        drawCircle(MouseInnerColor, cell * 0.09f, ear)
        drawCircle(Color.Black, cell * 0.04f, center + Offset(sign * cell * 0.11f, -cell * 0.02f))
    }
    drawCircle(MouseInnerColor, cell * 0.06f, center + Offset(0f, cell * 0.17f))
}

private fun findSnakeLifecycleOwner(context: Context): LifecycleOwner? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return null
}
