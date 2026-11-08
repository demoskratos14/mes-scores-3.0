package com.aventure.messcores

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.math.abs
import kotlin.random.Random

// Les étapes de la séance.
private const val BREAKOUT_PHASE_READY = 0 // on attend que le joueur lance la partie
private const val BREAKOUT_PHASE_PLAYING = 1 // la balle est en jeu (ou attend sur la raquette)
private const val BREAKOUT_PHASE_OVER = 2 // la dernière vie est perdue : la partie est finie

/** Plus long temps d'une image pris en compte : après un à-coup, la balle ne doit pas sauter loin d'un coup. */
private const val MAX_FRAME_MS = 100L

/** Les couleurs des rangées de briques, de haut en bas : l'arc-en-ciel. */
private val BRICK_COLORS = listOf(
    Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835), Color(0xFF43A047),
    Color(0xFF00ACC1), Color(0xFF1E88E5), Color(0xFF8E24AA)
)

/** Les étoiles du fond (part de la largeur, part de la hauteur, rayon en unités du terrain), tirées une fois pour toutes. */
private val STARS: List<Triple<Float, Float, Float>> = Random(2024).let { random ->
    List(48) { Triple(random.nextFloat(), random.nextFloat(), 0.02f + random.nextFloat() * 0.035f) }
}

/** Garde la partie quand l'écran tourne : son état est rangé dans une liste d'entiers (voir [BreakoutGame.save]). */
private val BreakoutGameSaver = listSaver<BreakoutGame, Int>(
    save = { it.save() },
    restore = { BreakoutGame.restore(it) }
)

/**
 * Casse-briques en solo : on fait rebondir une balle avec la raquette pour casser toutes les briques, sans la laisser
 * tomber. On glisse le doigt pour déplacer la raquette et on touche l'écran pour lancer la balle. Trois vies ; à chaque
 * niveau fini, la balle va plus vite. Le meilleur score est gardé d'une séance à l'autre. La partie se met en pause quand
 * l'appli passe en arrière-plan, et tout est gardé quand l'écran tourne. [players] : le joueur dont le nom s'affiche au départ.
 */
@Composable
internal fun BreakoutScreen(players: List<String>) {
    val name = players.firstOrNull().orEmpty().ifEmpty { "1" }
    val context = LocalContext.current
    val game = rememberSaveable(saver = BreakoutGameSaver) { BreakoutGame() }
    var phase by rememberSaveable { mutableIntStateOf(BREAKOUT_PHASE_READY) }
    var paused by rememberSaveable { mutableStateOf(false) }
    val record = rememberSoloRecord(BREAKOUT_ID)

    // La partie se met en pause quand l'appli passe en arrière-plan (et donc aussi quand l'écran tourne).
    val lifecycleOwner = remember(context) { findBreakoutLifecycleOwner(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && phase == BREAKOUT_PHASE_PLAYING) paused = true
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    // Le bouton retour met la partie en pause plutôt que de la quitter par mégarde.
    BackHandler(enabled = phase == BREAKOUT_PHASE_PLAYING && !paused) { paused = true }

    fun startGame() {
        record.clearNew()
        game.reset()
        paused = false
        phase = BREAKOUT_PHASE_PLAYING
    }

    // Pluie de paillettes par-dessus l'écran quand un nouveau record est battu.
    Box(modifier = Modifier.fillMaxSize()) {
        when (phase) {
            BREAKOUT_PHASE_PLAYING -> BreakoutPlayfield(
                game = game,
                paused = paused,
                onPausedChange = { paused = it },
                onOver = {
                    record.submit(game.score)
                    phase = BREAKOUT_PHASE_OVER
                }
            )
            BREAKOUT_PHASE_OVER -> BreakoutCard {
                Text(
                    text = stringResource(R.string.breakout_over_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.breakout_score, game.score),
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
                    text = stringResource(R.string.breakout_level_reached, game.level),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                SoloRecordLine(record)
                Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.breakout_play_again))
                }
            }
            else -> BreakoutCard {
                Text(
                    text = stringResource(R.string.breakout_ready_title, name),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.breakout_ready_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                SoloRecordLine(record)
                Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.breakout_go))
                }
            }
        }
        RecordSparkles(record)
    }
}

/** Le plateau des cartes de la séance : le titre du jeu, ses règles, puis [content]. */
@Composable
private fun BreakoutCard(content: @Composable ColumnScope.() -> Unit) {
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
                        text = stringResource(R.string.game_breakout),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    RulesButton(
                        gameId = BREAKOUT_ID,
                        gameName = stringResource(R.string.game_breakout),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                content()
            }
        }
    }
}

/**
 * La partie en cours : l'en-tête (score, vies, niveau, pause) puis le terrain. La partie avance à chaque image tant
 * qu'elle n'est pas en [paused] ; quand la dernière vie est perdue, [onOver] est appelé une fois.
 */
@Composable
private fun BreakoutPlayfield(
    game: BreakoutGame,
    paused: Boolean,
    onPausedChange: (Boolean) -> Unit,
    onOver: () -> Unit
) {
    KeepScreenOn()
    // Change à chaque image ou action : le dessin le lit et se refait, sans recomposer tout l'écran.
    var revision by remember { mutableIntStateOf(0) }
    // Les chiffres de l'en-tête et l'attente de lancement : ils ne recomposent l'écran que lorsqu'ils changent.
    var score by remember { mutableIntStateOf(game.score) }
    var lives by remember { mutableIntStateOf(game.lives) }
    var level by remember { mutableIntStateOf(game.level) }
    var waiting by remember { mutableStateOf(game.attached) }
    val finished by rememberUpdatedState(onOver)
    val isPaused by rememberUpdatedState(paused)

    fun sync() {
        revision++
        score = game.score
        lives = game.lives
        level = game.level
        waiting = game.attached
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

    val fieldDescription = stringResource(R.string.breakout_field)
    val launchLabel = stringResource(R.string.breakout_launch)

    // La marge de la barre de navigation (boutons ou geste) garde le terrain hors de ses touches.
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
                        text = stringResource(R.string.breakout_score, score),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.breakout_stats, lives, level),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { onPausedChange(true) }, enabled = !paused) {
                    Text(stringResource(R.string.breakout_pause))
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Le cadre garde toujours les proportions du terrain du niveau 1 (8 × 13 unités) : les terrains plus larges des niveaux
            // suivants sont mis à l'échelle pour y tenir, et leurs briques, leur raquette et leur balle rapetissent.
            val unit = maxOf(minOf(maxWidth / Breakout.WIDTH, maxHeight / Breakout.HEIGHT), 0.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(unit * Breakout.WIDTH, unit * Breakout.HEIGHT)
                    .clip(RoundedCornerShape(8.dp))
                    .semantics {
                        contentDescription = fieldDescription
                        // Pour TalkBack et les autres aides : lancer la balle sans passer par le geste.
                        onClick(label = launchLabel) {
                            if (isPaused) return@onClick false
                            game.launch()
                            sync()
                            true
                        }
                    }
                    // Glisser déplace la raquette comme le doigt (sans qu'elle saute sous lui) ; un simple toucher lance la balle.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            val pixelsPerUnit = size.width / game.width
                            var travelled = 0f
                            var pressed = true
                            while (pressed) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { change ->
                                    val dx = change.positionChange().x
                                    if (dx != 0f && !isPaused) {
                                        travelled += abs(dx)
                                        game.movePaddleTo(game.paddleX + dx / pixelsPerUnit)
                                        change.consume()
                                    }
                                }
                                if (event.changes.none { it.pressed }) pressed = false
                            }
                            if (!isPaused && travelled < viewConfiguration.touchSlop) game.launch()
                            sync()
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) { drawBreakoutField(game, revision) }
                if (waiting && !paused) {
                    Text(
                        text = stringResource(R.string.breakout_tap_to_launch),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = unit * 3f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                if (paused) {
                    Column(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.breakout_paused_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(8.dp)
                        )
                        Button(onClick = { onPausedChange(false) }) {
                            Text(stringResource(R.string.breakout_resume))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Le terrain, dessiné en unités du terrain (une brique = une unité de large) mises à l'échelle : le fond étoilé, les
 * briques, la raquette et la balle. [revision] n'est là que pour que le dessin se refasse quand elle change.
 */
private fun DrawScope.drawBreakoutField(game: BreakoutGame, @Suppress("UNUSED_PARAMETER") revision: Int) {
    // Pixels par unité du terrain du niveau en cours : plus le terrain est large, plus tout rapetisse. Les étoiles, elles, ne bougent pas.
    val u = size.width / game.width
    val baseUnit = size.width / Breakout.WIDTH

    // L'espace : un dégradé sombre et quelques étoiles.
    drawRect(Brush.verticalGradient(listOf(Color(0xFF050B16), Color(0xFF0B1E2B), Color(0xFF0F2F36))))
    STARS.forEach { (fx, fy, radius) ->
        drawCircle(Color.White.copy(alpha = 0.35f + radius * 8f), radius * baseUnit, Offset(fx * size.width, fy * size.height))
    }

    // Les briques : chaque rangée a sa couleur, et un biseau donne du relief (clair en haut, sombre en bas).
    val path = Path()
    val rows = game.rows
    for (row in 0 until rows) {
        for (col in 0 until game.cols) {
            if (!game.hasBrick(col, row)) continue
            drawBevelBrick(
                path = path,
                left = col * Breakout.BRICK_W * u,
                top = (game.bricksTop + row * Breakout.BRICK_H) * u,
                width = Breakout.BRICK_W * u,
                height = Breakout.BRICK_H * u,
                // Les sept couleurs de l'arc-en-ciel se répartissent sur toutes les rangées, quel que soit leur nombre.
                color = BRICK_COLORS[row * BRICK_COLORS.size / rows]
            )
        }
    }

    // La raquette : une barre de métal aux embouts rouges, avec une lueur dessous.
    val paddleW = Breakout.PADDLE_W * u
    val paddleH = Breakout.PADDLE_H * u
    val paddleLeft = (game.paddleX - Breakout.PADDLE_W / 2f) * u
    val paddleTop = game.paddleTop * u
    drawOval(
        brush = Brush.radialGradient(
            listOf(Color(0x5500E5FF), Color.Transparent),
            center = Offset(paddleLeft + paddleW / 2f, paddleTop + paddleH),
            radius = paddleW * 0.7f
        ),
        topLeft = Offset(paddleLeft - paddleW * 0.2f, paddleTop + paddleH * 0.4f),
        size = Size(paddleW * 1.4f, paddleH * 1.4f)
    )
    val capW = paddleW * 0.16f
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFFF2F6F8), Color(0xFF9AA7B0), Color(0xFF4E5A63)),
            startY = paddleTop,
            endY = paddleTop + paddleH
        ),
        topLeft = Offset(paddleLeft, paddleTop),
        size = Size(paddleW, paddleH),
        cornerRadius = CornerRadius(paddleH * 0.4f)
    )
    for (capLeft in listOf(paddleLeft, paddleLeft + paddleW - capW)) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFFFF6B5E), Color(0xFFC62828), Color(0xFF7F1414)),
                startY = paddleTop,
                endY = paddleTop + paddleH
            ),
            topLeft = Offset(capLeft, paddleTop),
            size = Size(capW, paddleH),
            cornerRadius = CornerRadius(paddleH * 0.4f)
        )
    }

    // La balle : une bille chromée avec son reflet.
    val radius = Breakout.BALL_RADIUS * u
    val center = Offset(game.ballX * u, game.ballY * u)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White, Color(0xFFC9D2D8), Color(0xFF5F6B73)),
            center = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f),
            radius = radius * 1.5f
        ),
        radius = radius,
        center = center
    )
}

/** Une brique biseautée : un cadre en quatre trapèzes (haut clair, bas sombre, côtés intermédiaires) autour d'un cœur uni. */
private fun DrawScope.drawBevelBrick(path: Path, left: Float, top: Float, width: Float, height: Float, color: Color) {
    val gap = height * 0.06f
    val l = left + gap
    val t = top + gap
    val w = width - 2f * gap
    val h = height - 2f * gap
    val b = h * 0.3f

    drawRect(color, Offset(l + b, t + b), Size(w - 2f * b, h - 2f * b))

    fun face(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, x4: Float, y4: Float, shade: Color) {
        path.rewind()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        path.lineTo(x3, y3)
        path.lineTo(x4, y4)
        path.close()
        drawPath(path, color)
        drawPath(path, shade)
    }
    face(l, t, l + w, t, l + w - b, t + b, l + b, t + b, Color.White.copy(alpha = 0.38f)) // haut
    face(l, t + h, l + w, t + h, l + w - b, t + h - b, l + b, t + h - b, Color.Black.copy(alpha = 0.32f)) // bas
    face(l, t, l + b, t + b, l + b, t + h - b, l, t + h, Color.White.copy(alpha = 0.14f)) // gauche
    face(l + w, t, l + w - b, t + b, l + w - b, t + h - b, l + w, t + h, Color.Black.copy(alpha = 0.16f)) // droite
}

private fun findBreakoutLifecycleOwner(context: Context): LifecycleOwner? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return null
}
