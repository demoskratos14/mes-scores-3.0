package com.aventure.messcores

import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// Les étapes de la séance.
private const val ASTEROIDS_PHASE_READY = 0 // on attend que le joueur lance la partie
private const val ASTEROIDS_PHASE_PLAYING = 1 // le vaisseau est en vol
private const val ASTEROIDS_PHASE_OVER = 2 // la dernière vie est perdue : la partie est finie

/** Plus long temps d'une image pris en compte : après un à-coup, rien ne doit sauter loin d'un coup. */
private const val ASTEROIDS_MAX_FRAME_MS = 100L

/** Les étoiles du fond (part de la largeur, part de la hauteur, rayon en unités du terrain), tirées une fois pour toutes. */
private val ASTEROIDS_STARS: List<Triple<Float, Float, Float>> = Random(1979).let { random ->
    List(60) { Triple(random.nextFloat(), random.nextFloat(), 0.015f + random.nextFloat() * 0.03f) }
}

private val ROCK_LIGHT = Color(0xFF9A8F83)
private val ROCK_DARK = Color(0xFF4A423A)
private val ROCK_EDGE = Color(0xFFD0C6B8)
private val SHOT_CORE = Color(0xFFFFF176)
private val SHOT_GLOW = Color(0x55FFB300)
private val ENEMY_SHOT_CORE = Color(0xFFFF8A80)
private val ENEMY_SHOT_GLOW = Color(0x55FF1744)
private val SHIELD_COLOR = Color(0xFF4DD0E1)
private val SPARK_COLORS = listOf(Color(0xFFD7CCC8), Color(0xFFFFB74D), Color(0xFF80DEEA))

/** Garde la partie quand l'écran tourne : son état est rangé dans une liste d'entiers (voir [AsteroidsGame.save]). */
private val AsteroidsGameSaver = listSaver<AsteroidsGame, Int>(
    save = { it.save() },
    restore = { AsteroidsGame.restore(it) }
)

/**
 * Les images du jeu, tirées de modèles 3D vus de dessus et chargées une fois : le vaisseau du joueur (nez en haut) et la planche des
 * ennemis (voir [Asteroids.ATLAS_COLS]) ; null si une image ne se charge pas, le dessin se rabat alors sur des formes simples.
 */
internal object AsteroidsSprites {
    private var ship: ImageBitmap? = null
    private var loaded = false
    private var enemies: ImageBitmap? = null
    private var enemiesLoaded = false

    @Synchronized
    fun enemies(context: Context): ImageBitmap? {
        if (!enemiesLoaded) {
            enemiesLoaded = true
            enemies = try {
                BitmapFactory.decodeResource(context.resources, R.drawable.asteroids_enemies)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
        return enemies
    }

    @Synchronized
    fun ship(context: Context): ImageBitmap? {
        if (!loaded) {
            loaded = true
            ship = try {
                BitmapFactory.decodeResource(context.resources, R.drawable.asteroids_ship)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
        return ship
    }
}

/**
 * Astéroïdes en solo : on pilote un vaisseau (tourner, pousser, tirer) dans un terrain qui se referme sur lui-même, pour
 * casser les roches en morceaux de plus en plus petits sans en toucher une, et abattre les ennemis qui traversent le terrain (soucoupes et vaisseaux de combat). Trois vies, une de
 * plus tous les 10 000 points ; à chaque niveau fini, il y a plus de roches et elles vont plus vite. Le meilleur score est
 * gardé d'une séance à l'autre. La partie se met en pause quand l'appli passe en arrière-plan, et tout est gardé quand
 * l'écran tourne. [players] : le joueur dont le nom s'affiche au départ.
 */
@Composable
internal fun AsteroidsScreen(players: List<String>) {
    val name = players.firstOrNull().orEmpty().ifEmpty { "1" }
    val context = LocalContext.current
    val game = rememberSaveable(saver = AsteroidsGameSaver) { AsteroidsGame() }
    var phase by rememberSaveable { mutableIntStateOf(ASTEROIDS_PHASE_READY) }
    var paused by rememberSaveable { mutableStateOf(false) }
    val record = rememberSoloRecord(ASTEROIDS_ID)

    // La partie se met en pause quand l'appli passe en arrière-plan (et donc aussi quand l'écran tourne).
    val lifecycleOwner = remember(context) { findAsteroidsLifecycleOwner(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && phase == ASTEROIDS_PHASE_PLAYING) paused = true
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    // Le bouton retour met la partie en pause plutôt que de la quitter par mégarde.
    BackHandler(enabled = phase == ASTEROIDS_PHASE_PLAYING && !paused) { paused = true }

    fun startGame() {
        record.clearNew()
        game.reset()
        paused = false
        phase = ASTEROIDS_PHASE_PLAYING
    }

    // Pluie de paillettes par-dessus l'écran quand un nouveau record est battu.
    Box(modifier = Modifier.fillMaxSize()) {
        when (phase) {
            ASTEROIDS_PHASE_PLAYING -> AsteroidsPlayfield(
                game = game,
                paused = paused,
                onPausedChange = { paused = it },
                onOver = {
                    record.submit(game.score)
                    phase = ASTEROIDS_PHASE_OVER
                }
            )
            ASTEROIDS_PHASE_OVER -> AsteroidsCard {
                Text(
                    text = stringResource(R.string.asteroids_over_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.asteroids_score, game.score),
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
                    text = stringResource(R.string.asteroids_level_reached, game.level),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                SoloRecordLine(record)
                Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.asteroids_play_again))
                }
            }
            else -> AsteroidsCard {
                Text(
                    text = stringResource(R.string.asteroids_ready_title, name),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.asteroids_ready_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                SoloRecordLine(record)
                Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.asteroids_go))
                }
            }
        }
        RecordSparkles(record)
    }
}

/** Le plateau des cartes de la séance : le titre du jeu, ses règles, puis [content]. */
@Composable
private fun AsteroidsCard(content: @Composable ColumnScope.() -> Unit) {
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
                        text = stringResource(R.string.game_asteroids),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    RulesButton(
                        gameId = ASTEROIDS_ID,
                        gameName = stringResource(R.string.game_asteroids),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                content()
            }
        }
    }
}

/**
 * La partie en cours : l'en-tête (score, vies, niveau, pause), le terrain, puis les quatre boutons (tourner à gauche,
 * tourner à droite, pousser, tirer), qui agissent tant qu'on les tient. La partie avance à chaque image tant qu'elle
 * n'est pas en [paused] ; quand la dernière vie est perdue, [onOver] est appelé une fois.
 */
@Composable
private fun AsteroidsPlayfield(
    game: AsteroidsGame,
    paused: Boolean,
    onPausedChange: (Boolean) -> Unit,
    onOver: () -> Unit
) {
    KeepScreenOn()
    val context = LocalContext.current
    val shipSprite = remember(context) { AsteroidsSprites.ship(context) }
    val enemySprites = remember(context) { AsteroidsSprites.enemies(context) }
    // Change à chaque image ou action : le dessin le lit et se refait, sans recomposer tout l'écran.
    var revision by remember { mutableIntStateOf(0) }
    // Les chiffres de l'en-tête et l'annonce du niveau : ils ne recomposent l'écran que lorsqu'ils changent.
    var score by remember { mutableIntStateOf(game.score) }
    var lives by remember { mutableIntStateOf(game.lives) }
    var level by remember { mutableIntStateOf(game.level) }
    var betweenLevels by remember { mutableStateOf(game.isBetweenLevels) }
    val finished by rememberUpdatedState(onOver)

    fun sync() {
        revision++
        score = game.score
        lives = game.lives
        level = game.level
        betweenLevels = game.isBetweenLevels
    }

    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (!game.isOver) {
            val now = withFrameNanos { it }
            val elapsed = ((now - last) / 1_000_000L).coerceIn(0L, ASTEROIDS_MAX_FRAME_MS)
            last = now
            game.tick(elapsed)
            sync()
        }
        finished()
    }

    val fieldDescription = stringResource(R.string.asteroids_field)

    // La marge de la barre de navigation (boutons ou geste) garde le terrain et les boutons hors de ses touches.
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
                        text = stringResource(R.string.asteroids_score, score),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.asteroids_stats, lives, level),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { onPausedChange(true) }, enabled = !paused) {
                    Text(stringResource(R.string.asteroids_pause))
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val unit = maxOf(minOf(maxWidth / Asteroids.WIDTH, maxHeight / Asteroids.HEIGHT), 0.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(unit * Asteroids.WIDTH, unit * Asteroids.HEIGHT)
                    .clip(RoundedCornerShape(8.dp))
                    .semantics { contentDescription = fieldDescription }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) { drawAsteroidsField(game, revision, shipSprite, enemySprites) }
                if (betweenLevels && !paused) {
                    Text(
                        text = stringResource(R.string.asteroids_level_banner, level + 1),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
                if (paused) {
                    Column(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.asteroids_paused_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(8.dp)
                        )
                        Button(onClick = { onPausedChange(false) }) {
                            Text(stringResource(R.string.asteroids_resume))
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(84.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PadButton(
                icon = PadIcon.LEFT,
                label = stringResource(R.string.asteroids_left),
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onHold = { game.turnLeft = it }
            )
            PadButton(
                icon = PadIcon.RIGHT,
                label = stringResource(R.string.asteroids_right),
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onHold = { game.turnRight = it }
            )
            Spacer(modifier = Modifier.width(16.dp))
            PadButton(
                icon = PadIcon.THRUST,
                label = stringResource(R.string.asteroids_thrust),
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onHold = { game.thrust = it }
            )
            PadButton(
                icon = PadIcon.FIRE,
                label = stringResource(R.string.asteroids_fire),
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onHold = { game.fire = it }
            )
        }
    }
}

internal enum class PadIcon { LEFT, RIGHT, THRUST, FIRE }

/**
 * Un bouton qui agit tant qu'on le tient : [onHold] reçoit vrai dès que le doigt se pose et faux quand il se lève (ou que
 * le bouton disparaît). Chaque bouton suit son propre doigt, on peut donc en tenir plusieurs à la fois.
 */
@Composable
internal fun PadButton(icon: PadIcon, label: String, modifier: Modifier, onHold: (Boolean) -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val update by rememberUpdatedState(onHold)
    val idleColor = MaterialTheme.colorScheme.secondaryContainer
    val pressedColor = MaterialTheme.colorScheme.primary
    val idleIcon = MaterialTheme.colorScheme.onSecondaryContainer
    val pressedIcon = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (pressed) pressedColor else idleColor)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    update(true)
                    try {
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change -> change.consume() }
                        } while (event.changes.any { change -> change.pressed })
                    } finally {
                        pressed = false
                        update(false)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(36.dp)) { drawPadIcon(icon, if (pressed) pressedIcon else idleIcon) }
    }
}

/** Le pictogramme d'un bouton, dessiné dans le carré de la zone. */
private fun DrawScope.drawPadIcon(icon: PadIcon, color: Color) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val s = size.minDimension / 2f
    when (icon) {
        PadIcon.LEFT, PadIcon.RIGHT -> {
            // Un triangle qui montre le sens de rotation du vaisseau.
            val d = if (icon == PadIcon.LEFT) -1f else 1f
            val path = Path().apply {
                moveTo(c.x + d * s, c.y)
                lineTo(c.x - d * s * 0.8f, c.y - s)
                lineTo(c.x - d * s * 0.8f, c.y + s)
                close()
            }
            drawPath(path, color)
        }
        PadIcon.THRUST -> {
            // Une flèche vers le haut : le vaisseau avance.
            val path = Path().apply {
                moveTo(c.x, c.y - s)
                lineTo(c.x + s, c.y + s * 0.2f)
                lineTo(c.x + s * 0.4f, c.y + s * 0.2f)
                lineTo(c.x + s * 0.4f, c.y + s)
                lineTo(c.x - s * 0.4f, c.y + s)
                lineTo(c.x - s * 0.4f, c.y + s * 0.2f)
                lineTo(c.x - s, c.y + s * 0.2f)
                close()
            }
            drawPath(path, color)
        }
        PadIcon.FIRE -> {
            // Une cible : le tir.
            drawCircle(color, radius = s * 0.4f, center = c)
            drawCircle(color, radius = s * 0.85f, center = c, style = Stroke(width = s * 0.22f))
        }
    }
}

/**
 * Dessine une image du terrain, en unités du terrain mises à l'échelle (9 de large) : le fond étoilé, les roches, les
 * tirs, l'ennemi, le vaisseau et les étincelles. Ce qui dépasse d'un bord réapparaît de l'autre côté. [revision] n'est
 * là que pour que le dessin se refasse quand elle change (et pour faire vaciller la flamme).
 */
private fun DrawScope.drawAsteroidsField(game: AsteroidsGame, revision: Int, shipSprite: ImageBitmap?, enemySprites: ImageBitmap?) {
    val u = size.width / Asteroids.WIDTH

    // L'espace : un dégradé sombre et quelques étoiles.
    drawRect(Brush.verticalGradient(listOf(Color(0xFF03060D), Color(0xFF08131F), Color(0xFF0C2430))))
    ASTEROIDS_STARS.forEach { (fx, fy, radius) ->
        drawCircle(Color.White.copy(alpha = 0.3f + radius * 10f), radius * u, Offset(fx * size.width, fy * size.height))
    }

    for (rock in game.rocks) drawRock(rock, u)
    for (shot in game.shots) drawShot(shot, u)
    game.ufo?.let { drawEnemy(it, u, enemySprites) }
    if (game.shipAlive) drawShip(game, revision, shipSprite, u)
    for (spark in game.sparks) {
        val fade = (spark.lifeMs.toFloat() / spark.totalMs).coerceIn(0f, 1f)
        val color = SPARK_COLORS[spark.kind.coerceIn(0, SPARK_COLORS.size - 1)]
        drawCircle(color.copy(alpha = fade), 0.05f * u * (0.5f + fade), Offset(spark.x * u, spark.y * u))
    }
}

/**
 * Appelle [draw] avec la position (en pixels) d'un objet de rayon [reach] et, s'il déborde d'un bord du terrain, avec
 * celles de ses copies de l'autre côté : l'objet se voit des deux côtés à la fois quand il traverse.
 */
private inline fun drawWrapped(x: Float, y: Float, reach: Float, u: Float, draw: (Float, Float) -> Unit) {
    val dx = if (x < reach) 1 else if (x > Asteroids.WIDTH - reach) -1 else 0
    val dy = if (y < reach) 1 else if (y > Asteroids.HEIGHT - reach) -1 else 0
    draw(x * u, y * u)
    if (dx != 0) draw((x + dx * Asteroids.WIDTH) * u, y * u)
    if (dy != 0) draw(x * u, (y + dy * Asteroids.HEIGHT) * u)
    if (dx != 0 && dy != 0) draw((x + dx * Asteroids.WIDTH) * u, (y + dy * Asteroids.HEIGHT) * u)
}

private fun DrawScope.drawRock(rock: AsteroidRock, u: Float) {
    val radius = Asteroids.radiusOf(rock.size) * u
    val radii = Asteroids.shapeRadii(rock.shape)
    val cosA = cos(rock.angle)
    val sinA = sin(rock.angle)
    drawWrapped(rock.x, rock.y, Asteroids.radiusOf(rock.size), u) { cx, cy ->
        val path = Path()
        for (i in radii.indices) {
            val a = rock.angle + i * Asteroids.TWO_PI / radii.size
            val px = cx + sin(a) * radius * radii[i]
            val py = cy - cos(a) * radius * radii[i]
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        val light = Offset(cx - radius * 0.3f, cy - radius * 0.3f)
        drawPath(path, Brush.radialGradient(listOf(ROCK_LIGHT, ROCK_DARK), center = light, radius = radius * 1.7f))
        drawPath(path, ROCK_EDGE, style = Stroke(width = maxOf(1.5f, radius * 0.06f)))
        if (rock.size > Asteroids.SMALL) {
            // Deux cratères, qui tournent avec la roche.
            drawCircle(Color.Black.copy(alpha = 0.25f), radius * 0.17f, Offset(cx + (0.3f * cosA + 0.2f * sinA) * radius, cy + (0.3f * sinA - 0.2f * cosA) * radius))
            drawCircle(Color.Black.copy(alpha = 0.2f), radius * 0.11f, Offset(cx + (-0.35f * cosA - 0.25f * sinA) * radius, cy + (-0.35f * sinA + 0.25f * cosA) * radius))
        }
    }
}

private fun DrawScope.drawShot(shot: AsteroidShot, u: Float) {
    val radius = maxOf(Asteroids.SHOT_RADIUS * u * 1.2f, 2f)
    val core = if (shot.hostile) ENEMY_SHOT_CORE else SHOT_CORE
    val glow = if (shot.hostile) ENEMY_SHOT_GLOW else SHOT_GLOW
    drawWrapped(shot.x, shot.y, 0.1f, u) { cx, cy ->
        drawCircle(glow, radius * 2.4f, Offset(cx, cy))
        drawCircle(core, radius, Offset(cx, cy))
    }
}

/**
 * Un ennemi : son image de la planche, tournée selon sa façon de se dessiner (il tourne sur lui-même, reste droit ou pointe dans le
 * sens de sa course), et une barre de points de vie s'il en a plusieurs. Il ne se referme pas sur les côtés (il entre et sort), mais
 * se voit des deux côtés quand il passe par le haut ou le bas du terrain. Sans planche, une soucoupe simple le remplace.
 */
private fun DrawScope.drawEnemy(ufo: AsteroidUfo, u: Float, atlas: ImageBitmap?) {
    val kind = Asteroids.enemy(ufo.type)
    val side = kind.size * u
    val degrees = when (kind.look) {
        Asteroids.LOOK_SPIN -> ufo.spin.radiansToDegrees()
        Asteroids.LOOK_HEADING -> ufo.heading.radiansToDegrees()
        else -> 0f
    }
    val index = ufo.type.coerceIn(0, Asteroids.ENEMIES.size - 1)
    val cell = Asteroids.ATLAS_CELL

    fun paint(cx: Float, cy: Float) {
        rotate(degrees, Offset(cx, cy)) {
            if (atlas != null) {
                drawImage(
                    image = atlas,
                    srcOffset = IntOffset((index % Asteroids.ATLAS_COLS) * cell, (index / Asteroids.ATLAS_COLS) * cell),
                    srcSize = IntSize(cell, cell),
                    dstOffset = IntOffset((cx - side / 2f).roundToInt(), (cy - side / 2f).roundToInt()),
                    dstSize = IntSize(side.roundToInt(), side.roundToInt()),
                    filterQuality = FilterQuality.Medium
                )
            } else {
                val r = side * 0.45f
                drawOval(Color(0xFF4DD0E1), topLeft = Offset(cx - r * 0.45f, cy - r * 0.8f), size = Size(r * 0.9f, r * 0.9f))
                drawOval(Color(0xFFB0BEC5), topLeft = Offset(cx - r, cy - r * 0.35f), size = Size(r * 2f, r * 0.7f))
                for (i in -1..1) drawCircle(Color(0xFFFFEE58), r * 0.09f, Offset(cx + i * r * 0.5f, cy))
            }
        }
        if (kind.hp > 1) {
            // Un petit trait par point de vie, rouge tant qu'il reste, pâle une fois perdu.
            val gap = side * 0.04f
            val pip = (side * 0.7f - gap * (kind.hp - 1)) / kind.hp
            val left = cx - side * 0.35f
            val top = cy - side * 0.62f
            for (i in 0 until kind.hp) {
                val color = if (i < ufo.hp) Color(0xFFFF5252) else Color(0x66FFFFFF)
                drawRect(color, topLeft = Offset(left + i * (pip + gap), top), size = Size(pip, maxOf(2f, side * 0.05f)))
            }
        }
    }

    val cx = ufo.x * u
    val cy = ufo.y * u
    paint(cx, cy)
    val reach = kind.size / 2f
    if (ufo.y < reach) paint(cx, cy + Asteroids.HEIGHT * u)
    if (ufo.y > Asteroids.HEIGHT - reach) paint(cx, cy - Asteroids.HEIGHT * u)
}

private fun DrawScope.drawShip(game: AsteroidsGame, revision: Int, sprite: ImageBitmap?, u: Float) {
    val side = Asteroids.SHIP_SPRITE * u
    val degrees = game.shipAngle.radiansToDegrees()
    val flicker = when (revision % 3) {
        0 -> 1f
        1 -> 0.7f
        else -> 0.85f
    }
    drawWrapped(game.shipX, game.shipY, Asteroids.SHIP_SPRITE / 2f, u) { cx, cy ->
        rotate(degrees, Offset(cx, cy)) {
            if (game.thrusting) {
                // Deux flammes sous les tuyères (le vaisseau pointe vers le haut avant la rotation).
                drawFlame(cx - side * 0.14f, cy + side * 0.36f, side * 0.07f, side * 0.34f * flicker)
                drawFlame(cx + side * 0.14f, cy + side * 0.36f, side * 0.07f, side * 0.34f * flicker)
            }
            if (sprite != null) {
                drawImage(
                    image = sprite,
                    dstOffset = IntOffset((cx - side / 2f).roundToInt(), (cy - side / 2f).roundToInt()),
                    dstSize = IntSize(side.roundToInt(), side.roundToInt()),
                    filterQuality = FilterQuality.Medium
                )
            } else {
                val path = Path().apply {
                    moveTo(cx, cy - side * 0.45f)
                    lineTo(cx + side * 0.35f, cy + side * 0.4f)
                    lineTo(cx, cy + side * 0.25f)
                    lineTo(cx - side * 0.35f, cy + side * 0.4f)
                    close()
                }
                drawPath(path, Color(0xFFB0C4DE))
            }
        }
        if (game.invulnerableMs > 0) {
            // Le bouclier : un anneau qui s'efface en fin de protection.
            val fade = (game.invulnerableMs / 700f).coerceIn(0.25f, 1f)
            drawCircle(SHIELD_COLOR.copy(alpha = 0.55f * fade), side * 0.62f, Offset(cx, cy), style = Stroke(width = maxOf(2f, side * 0.04f)))
        }
    }
}

private fun DrawScope.drawFlame(x: Float, top: Float, halfWidth: Float, length: Float) {
    val outer = Path().apply {
        moveTo(x - halfWidth, top)
        lineTo(x + halfWidth, top)
        lineTo(x, top + length)
        close()
    }
    drawPath(outer, Color(0xFFFF9800))
    val inner = Path().apply {
        moveTo(x - halfWidth * 0.5f, top)
        lineTo(x + halfWidth * 0.5f, top)
        lineTo(x, top + length * 0.6f)
        close()
    }
    drawPath(inner, Color(0xFFFFF176))
}

private fun findAsteroidsLifecycleOwner(context: Context): LifecycleOwner? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return null
}
