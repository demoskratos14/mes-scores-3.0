package com.aventure.messcores

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
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

/** Les couleurs des rangées de briques, de haut en bas : l'arc-en-ciel (pour une partie reprise d'une ancienne sauvegarde). */
/** L'acier des briques incassables, et la teinte vers laquelle les briques solides sont assombries. */
private val UNBREAKABLE_COLOR = Color(0xFF78909C)
private val STURDY_TINT = Color(0xFF263238)

private val BRICK_COLORS = listOf(
    Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835), Color(0xFF43A047),
    Color(0xFF00ACC1), Color(0xFF1E88E5), Color(0xFF8E24AA)
)

/** Les étoiles du fond (part de la largeur, part de la hauteur, rayon en unités du terrain), tirées une fois pour toutes. */
private val STARS: List<Triple<Float, Float, Float>> = Random(2024).let { random ->
    List(48) { Triple(random.nextFloat(), random.nextFloat(), 0.02f + random.nextFloat() * 0.035f) }
}

/** Le pinceau des lettres des capsules : une lettre blanche en gras, centrée sur le point où on la dessine. */
private val CAPSULE_LETTER_PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.WHITE
    textAlign = Paint.Align.CENTER
    typeface = Typeface.DEFAULT_BOLD
}

private val GOOD_CAPSULE_COLORS = listOf(Color(0xFF66BB6A), Color(0xFF2E7D32), Color(0xFF1B5E20))
private val BAD_CAPSULE_COLORS = listOf(Color(0xFFEF5350), Color(0xFFC62828), Color(0xFF7F1414))

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
    var waiting by remember { mutableStateOf(game.hasStuckBall) }
    val finished by rememberUpdatedState(onOver)
    val isPaused by rememberUpdatedState(paused)

    fun sync() {
        revision++
        score = game.score
        lives = game.lives
        level = game.level
        waiting = game.hasStuckBall
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

    // Les briques : chacune a la couleur de son dessin (ou celle de sa rangée, pour une ancienne sauvegarde), et un
    // biseau donne du relief (clair en haut, sombre en bas).
    val path = Path()
    val rows = game.rows
    for (row in 0 until rows) {
        for (col in 0 until game.cols) {
            if (!game.hasBrick(col, row)) continue
            val kind = game.brickKind(col, row)
            val left = col * Breakout.BRICK_W * u
            val top = (game.bricksTop + row * Breakout.BRICK_H) * u
            val w = Breakout.BRICK_W * u
            val h = Breakout.BRICK_H * u
            // Sans couleur de dessin, les sept couleurs de l'arc-en-ciel se répartissent sur toutes les rangées.
            val pictureColor = game.brickColor(col, row).let { argb ->
                if (argb != 0) Color(argb) else BRICK_COLORS[row * BRICK_COLORS.size / rows]
            }
            val color = when (kind) {
                Breakout.UNBREAKABLE -> UNBREAKABLE_COLOR
                // Une brique solide garde la teinte de son dessin, mais assombrie et cerclée de métal.
                Breakout.STURDY, Breakout.CRACKED -> lerp(pictureColor, STURDY_TINT, 0.4f)
                else -> pictureColor
            }
            drawBevelBrick(path = path, left = left, top = top, width = w, height = h, color = color)
            when (kind) {
                Breakout.STURDY -> drawSturdyFrame(left, top, w, h)
                Breakout.CRACKED -> {
                    drawSturdyFrame(left, top, w, h)
                    drawCracks(left, top, w, h)
                }
                Breakout.UNBREAKABLE -> drawRivets(left, top, w, h)
            }
        }
    }

    // La raquette : une barre de métal aux embouts rouges, avec une lueur dessous.
    val paddleW = game.paddleWidth * u
    val paddleH = Breakout.PADDLE_H * u
    val paddleLeft = (game.paddleX - game.paddleWidth / 2f) * u
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

    // Les canons du laser, aux deux bouts de la raquette.
    if (game.hasLaser) {
        val cannonW = paddleW * 0.07f
        for (x in listOf(paddleLeft + paddleW * 0.08f, paddleLeft + paddleW * 0.92f - cannonW)) {
            drawRoundRect(
                color = Color(0xFFFFD54F),
                topLeft = Offset(x, paddleTop - paddleH * 0.7f),
                size = Size(cannonW, paddleH * 0.8f),
                cornerRadius = CornerRadius(cannonW * 0.3f)
            )
        }
    }

    // Les tirs de laser.
    for (bolt in game.laserBolts) {
        drawLine(
            color = Color(0xFFFFEE58),
            start = Offset(bolt.x * u, bolt.y * u),
            end = Offset(bolt.x * u, (bolt.y + LASER_BOLT_LENGTH) * u),
            strokeWidth = maxOf(2f, 0.08f * u)
        )
    }

    // Les capsules qui tombent.
    for (capsule in game.fallingCapsules) {
        drawCapsule(capsule.bonus, capsule.x * u, capsule.y * u, Breakout.CAPSULE_W * u, Breakout.CAPSULE_H * u)
    }

    // Les balles : des billes chromées avec leur reflet ; incandescentes quand elles traversent les briques.
    val radius = Breakout.BALL_RADIUS * u
    for (ball in game.allBalls) {
        val center = Offset(ball.x * u, ball.y * u)
        if (game.isPiercing) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xAAFF6D00), Color.Transparent),
                    center = center,
                    radius = radius * 3f
                ),
                radius = radius * 3f,
                center = center
            )
        }
        val shades = if (game.isPiercing) {
            listOf(Color(0xFFFFF8E1), Color(0xFFFFB300), Color(0xFFE64A19))
        } else {
            listOf(Color.White, Color(0xFFC9D2D8), Color(0xFF5F6B73))
        }
        drawCircle(
            brush = Brush.radialGradient(
                shades,
                center = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f),
                radius = radius * 1.5f
            ),
            radius = radius,
            center = center
        )
    }

    // Les effets en cours : une petite capsule avec sa lettre, et une barre qui se vide avec le temps.
    val chipW = baseUnit * 0.6f
    val chipH = baseUnit * 0.3f
    game.activeEffects.forEachIndexed { index, (bonus, remaining) ->
        val cx = baseUnit * 0.15f + chipW / 2f + index * (chipW + baseUnit * 0.1f)
        val cy = baseUnit * 0.1f + chipH / 2f
        drawCapsule(bonus, cx, cy, chipW, chipH)
        drawRect(
            color = Color.White.copy(alpha = 0.8f),
            topLeft = Offset(cx - chipW / 2f, cy + chipH / 2f + baseUnit * 0.03f),
            size = Size(chipW * remaining.coerceIn(0f, 1f), baseUnit * 0.04f)
        )
    }
}

/** Longueur d'un tir de laser, en unités du terrain. */
private const val LASER_BOLT_LENGTH = 0.35f

/** Une capsule centrée en ([cx], [cy]) : verte pour un bonus, rouge pour un malus, avec sa lettre. */
private fun DrawScope.drawCapsule(bonus: BreakoutBonus, cx: Float, cy: Float, w: Float, h: Float) {
    val colors = if (bonus.good) GOOD_CAPSULE_COLORS else BAD_CAPSULE_COLORS
    drawRoundRect(
        brush = Brush.verticalGradient(colors, startY = cy - h / 2f, endY = cy + h / 2f),
        topLeft = Offset(cx - w / 2f, cy - h / 2f),
        size = Size(w, h),
        cornerRadius = CornerRadius(h / 2f)
    )
    val paint = CAPSULE_LETTER_PAINT
    paint.textSize = h * 0.8f
    drawContext.canvas.nativeCanvas.drawText(bonus.letter.toString(), cx, cy - (paint.ascent() + paint.descent()) / 2f, paint)
}

/** Le cadre de métal clair qui distingue une brique solide d'une brique ordinaire. */
private fun DrawScope.drawSturdyFrame(left: Float, top: Float, width: Float, height: Float) {
    val inset = height * 0.1f
    drawRect(
        color = Color(0xFFECEFF1).copy(alpha = 0.85f),
        topLeft = Offset(left + inset, top + inset),
        size = Size(width - 2f * inset, height - 2f * inset),
        style = Stroke(width = height * 0.09f)
    )
}

/** La fissure d'une brique solide déjà touchée : un éclair sombre en travers de la brique. */
private fun DrawScope.drawCracks(left: Float, top: Float, width: Float, height: Float) {
    val stroke = height * 0.08f
    val points = listOf(
        Offset(left + width * 0.42f, top + height * 0.06f),
        Offset(left + width * 0.52f, top + height * 0.38f),
        Offset(left + width * 0.38f, top + height * 0.55f),
        Offset(left + width * 0.55f, top + height * 0.94f)
    )
    for (i in 0 until points.size - 1) drawLine(Color.Black.copy(alpha = 0.85f), points[i], points[i + 1], strokeWidth = stroke)
    // Une branche, pour que la fissure se voie bien même sur une petite brique.
    drawLine(
        Color.Black.copy(alpha = 0.85f),
        Offset(left + width * 0.52f, top + height * 0.38f),
        Offset(left + width * 0.74f, top + height * 0.5f),
        strokeWidth = stroke
    )
}

/** Les rivets d'une brique incassable : un à chaque bout, sur l'acier gris. */
private fun DrawScope.drawRivets(left: Float, top: Float, width: Float, height: Float) {
    val radius = height * 0.09f
    val y = top + height / 2f
    for (x in listOf(left + width * 0.2f, left + width * 0.8f)) {
        drawCircle(Color(0xFF263238).copy(alpha = 0.8f), radius * 1.3f, Offset(x, y + radius * 0.3f))
        drawCircle(Color(0xFFCFD8DC), radius, Offset(x, y))
    }
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
