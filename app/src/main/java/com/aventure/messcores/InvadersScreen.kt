package com.aventure.messcores

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// Les étapes de la séance.
private const val INVADERS_PHASE_READY = 0 // on attend que le joueur lance la partie
private const val INVADERS_PHASE_PLAYING = 1 // la grille descend
private const val INVADERS_PHASE_OVER = 2 // la dernière vie est perdue ou la grille est arrivée : la partie est finie

/** Plus long temps d'une image pris en compte : après un à-coup, rien ne doit sauter loin d'un coup. */
private const val INVADERS_MAX_FRAME_MS = 100L

/** Les étoiles du fond (part de la largeur, part de la hauteur, rayon en unités du terrain), tirées une fois pour toutes. */
private val INVADERS_STARS: List<Triple<Float, Float, Float>> = Random(1978).let { random ->
    List(60) { Triple(random.nextFloat(), random.nextFloat(), 0.015f + random.nextFloat() * 0.03f) }
}

private val BUNKER_COLORS = listOf(Color(0xFF9CCC65), Color(0xFF7CB342), Color(0xFF689F38), Color(0xFF558B2F))
private val INVADER_SHOT_CORE = Color(0xFFFFF176)
private val INVADER_SHOT_GLOW = Color(0x55FFB300)
private val INVADER_ENEMY_CORE = Color(0xFFFF8A80)
private val INVADER_ENEMY_GLOW = Color(0x55FF1744)
private val INVADER_SHIELD = Color(0xFF4DD0E1)
private val INVADER_SPARKS = listOf(Color(0xFF80CBC4), Color(0xFFFFB74D), Color(0xFFFFF176))

/** Garde la partie quand l'écran tourne : son état est rangé dans une liste d'entiers (voir [InvadersGame.save]). */
private val InvadersGameSaver = listSaver<InvadersGame, Int>(
    save = { it.save() },
    restore = { InvadersGame.restore(it) }
)

/**
 * Envahisseurs en solo : une grille d'ennemis va d'un bord à l'autre et descend d'un cran à chaque bord ; on la tient en
 * respect avec son vaisseau, qui glisse en bas derrière quatre abris que les tirs grignotent. Les images sont celles
 * d'Astéroïdes (le vaisseau et la planche des ennemis, voir [AsteroidsSprites]), une vague différente à chaque niveau. Trois
 * vies ; la partie est perdue aussi si la grille arrive en bas. Le meilleur score est gardé d'une séance à l'autre. La
 * partie se met en pause quand l'appli passe en arrière-plan, et tout est gardé quand l'écran tourne.
 * [players] : le joueur dont le nom s'affiche au départ.
 */
@Composable
internal fun InvadersScreen(players: List<String>) {
    val name = players.firstOrNull().orEmpty().ifEmpty { "1" }
    val context = LocalContext.current
    val game = rememberSaveable(saver = InvadersGameSaver) { InvadersGame() }
    var phase by rememberSaveable { mutableIntStateOf(INVADERS_PHASE_READY) }
    var paused by rememberSaveable { mutableStateOf(false) }
    val record = rememberSoloRecord(INVADERS_ID)

    // La partie se met en pause quand l'appli passe en arrière-plan (et donc aussi quand l'écran tourne).
    val lifecycleOwner = remember(context) { findInvadersLifecycleOwner(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && phase == INVADERS_PHASE_PLAYING) paused = true
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    // Le bouton retour met la partie en pause plutôt que de la quitter par mégarde.
    BackHandler(enabled = phase == INVADERS_PHASE_PLAYING && !paused) { paused = true }

    fun startGame() {
        record.clearNew()
        game.reset()
        paused = false
        phase = INVADERS_PHASE_PLAYING
    }

    // Pluie de paillettes par-dessus l'écran quand un nouveau record est battu.
    Box(modifier = Modifier.fillMaxSize()) {
        when (phase) {
            INVADERS_PHASE_PLAYING -> InvadersPlayfield(
                game = game,
                paused = paused,
                onPausedChange = { paused = it },
                onOver = {
                    record.submit(game.score)
                    phase = INVADERS_PHASE_OVER
                }
            )
            INVADERS_PHASE_OVER -> InvadersCard {
                Text(
                    text = stringResource(R.string.invaders_over_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.invaders_score, game.score),
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
                    text = stringResource(R.string.invaders_level_reached, game.level),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                SoloRecordLine(record)
                Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.invaders_play_again))
                }
            }
            else -> InvadersCard {
                Text(
                    text = stringResource(R.string.invaders_ready_title, name),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.invaders_ready_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                SoloRecordLine(record)
                Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.invaders_go))
                }
            }
        }
        RecordSparkles(record)
    }
}

/** Le plateau des cartes de la séance : le titre du jeu, ses règles, puis [content]. */
@Composable
private fun InvadersCard(content: @Composable ColumnScope.() -> Unit) {
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
                        text = stringResource(R.string.game_invaders),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    RulesButton(
                        gameId = INVADERS_ID,
                        gameName = stringResource(R.string.game_invaders),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                content()
            }
        }
    }
}

/**
 * La partie en cours : l'en-tête (score, vies, niveau, pause), le terrain, puis les trois boutons (à gauche, à droite, tirer),
 * qui agissent tant qu'on les tient. La partie avance à chaque image tant qu'elle n'est pas en [paused] ; quand elle est
 * perdue, [onOver] est appelé une fois.
 */
@Composable
private fun InvadersPlayfield(
    game: InvadersGame,
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
            val elapsed = ((now - last) / 1_000_000L).coerceIn(0L, INVADERS_MAX_FRAME_MS)
            last = now
            game.tick(elapsed)
            sync()
        }
        finished()
    }

    val fieldDescription = stringResource(R.string.invaders_field)

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
                        text = stringResource(R.string.invaders_score, score),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.invaders_stats, lives, level),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { onPausedChange(true) }, enabled = !paused) {
                    Text(stringResource(R.string.invaders_pause))
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val unit = maxOf(minOf(maxWidth / Invaders.WIDTH, maxHeight / Invaders.HEIGHT), 0.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(unit * Invaders.WIDTH, unit * Invaders.HEIGHT)
                    .clip(RoundedCornerShape(8.dp))
                    .semantics { contentDescription = fieldDescription }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) { drawInvadersField(game, revision, shipSprite, enemySprites) }
                if (betweenLevels && !paused) {
                    Text(
                        text = stringResource(R.string.invaders_level_banner, level + 1),
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
                            text = stringResource(R.string.invaders_paused_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(8.dp)
                        )
                        Button(onClick = { onPausedChange(false) }) {
                            Text(stringResource(R.string.invaders_resume))
                        }
                    }
                }
            }
        }

        // Les boutons sont ceux d'Astéroïdes (voir PadButton) : à gauche, à droite, et un grand bouton de tir.
        Row(
            modifier = Modifier.fillMaxWidth().height(84.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PadButton(
                icon = PadIcon.LEFT,
                label = stringResource(R.string.invaders_left),
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onHold = { game.moveLeft = it }
            )
            PadButton(
                icon = PadIcon.RIGHT,
                label = stringResource(R.string.invaders_right),
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onHold = { game.moveRight = it }
            )
            Spacer(modifier = Modifier.width(16.dp))
            PadButton(
                icon = PadIcon.FIRE,
                label = stringResource(R.string.invaders_fire),
                modifier = Modifier.weight(2f).fillMaxHeight(),
                onHold = { game.fire = it }
            )
        }
    }
}

/**
 * Dessine une image du terrain, en unités du terrain mises à l'échelle (9 de large) : le fond étoilé, les abris, la grille,
 * le vaisseau bonus, le vaisseau du joueur, les tirs et les étincelles. [revision] n'est là que pour que le dessin se refasse
 * quand elle change (et pour faire vaciller la flamme du vaisseau).
 */
private fun DrawScope.drawInvadersField(game: InvadersGame, revision: Int, shipSprite: ImageBitmap?, enemySprites: ImageBitmap?) {
    val u = size.width / Invaders.WIDTH

    // L'espace : un dégradé sombre et quelques étoiles.
    drawRect(Brush.verticalGradient(listOf(Color(0xFF03060D), Color(0xFF08131F), Color(0xFF0C2430))))
    INVADERS_STARS.forEach { (fx, fy, radius) ->
        drawCircle(Color.White.copy(alpha = 0.3f + radius * 10f), radius * u, Offset(fx * size.width, fy * size.height))
    }

    // Les abris : un carré par case, plus clair en haut.
    val cell = Invaders.BUNKER_CELL * u
    for (b in 0 until Invaders.BUNKERS) {
        val left = Invaders.bunkerLeft(b)
        for (row in 0 until Invaders.BUNKER_ROWS) {
            val color = BUNKER_COLORS[row.coerceIn(0, BUNKER_COLORS.size - 1)]
            for (col in 0 until Invaders.BUNKER_COLS) {
                if (!game.isBunkerCell(b, row, col)) continue
                val topLeft = Offset((left + col * Invaders.BUNKER_CELL) * u, (Invaders.BUNKER_Y + row * Invaders.BUNKER_CELL) * u)
                drawRect(color, topLeft = topLeft, size = Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }

    // La grille : chaque rangée a l'image que sa vague lui donne ; les soucoupes tournent, les vaisseaux regardent vers le bas.
    val side = Invaders.SPRITE * u
    for (row in 0 until Invaders.ROWS) {
        val type = Invaders.typeAt(game.level, row)
        val look = Asteroids.enemy(type).look
        for (col in 0 until Invaders.COLS) {
            if (!game.isAlive(row, col)) continue
            val cx = game.invaderX(col) * u
            // Un léger balancement, décalé d'une colonne à l'autre.
            val cy = game.invaderY(row) * u + sin(game.clockMs * 0.008f + col) * 0.04f * u
            val degrees = when (look) {
                Asteroids.LOOK_SPIN -> (game.clockMs % 6000) / 6000f * 360f + col * 37f
                Asteroids.LOOK_HEADING -> 180f
                else -> 0f
            }
            if (enemySprites != null) {
                drawAtlasSprite(enemySprites, type, cx, cy, side, degrees)
            } else {
                drawCircle(Color(0xFF80CBC4), side * 0.4f, Offset(cx, cy))
            }
        }
    }

    game.bonus?.let { saucer ->
        val cx = saucer.x * u
        val cy = Invaders.BONUS_Y * u
        val bonusSide = Invaders.BONUS_SIZE * u
        if (enemySprites != null) {
            drawAtlasSprite(enemySprites, Invaders.BONUS_TYPE, cx, cy, bonusSide, (game.clockMs % 3000) / 3000f * 360f)
        } else {
            drawOval(Color(0xFFFFEE58), topLeft = Offset(cx - bonusSide * 0.45f, cy - bonusSide * 0.2f), size = Size(bonusSide * 0.9f, bonusSide * 0.4f))
        }
    }

    if (game.playerAlive) drawPlayer(game, revision, shipSprite, u)

    for (shot in game.shots) {
        val core = if (shot.hostile) INVADER_ENEMY_CORE else INVADER_SHOT_CORE
        val glow = if (shot.hostile) INVADER_ENEMY_GLOW else INVADER_SHOT_GLOW
        val width = maxOf(Invaders.SHOT_RADIUS * 2f * u, 3f)
        val height = Invaders.SHOT_RADIUS * 5f * u
        drawRect(glow, topLeft = Offset(shot.x * u - width * 1.5f, shot.y * u - height * 0.7f), size = Size(width * 3f, height * 1.4f))
        drawRect(core, topLeft = Offset(shot.x * u - width / 2f, shot.y * u - height / 2f), size = Size(width, height))
    }

    for (spark in game.sparks) {
        val fade = (spark.lifeMs.toFloat() / spark.totalMs).coerceIn(0f, 1f)
        val color = INVADER_SPARKS[spark.kind.coerceIn(0, INVADER_SPARKS.size - 1)]
        drawCircle(color.copy(alpha = fade), 0.05f * u * (0.5f + fade), Offset(spark.x * u, spark.y * u))
    }
}

/** Une case de la planche des ennemis (voir [Asteroids.ATLAS_COLS]), de côté [side], centrée en ([cx], [cy]) et tournée de [degrees]. */
private fun DrawScope.drawAtlasSprite(atlas: ImageBitmap, type: Int, cx: Float, cy: Float, side: Float, degrees: Float) {
    val cell = Asteroids.ATLAS_CELL
    val index = type.coerceIn(0, Asteroids.ENEMIES.size - 1)
    rotate(degrees, Offset(cx, cy)) {
        drawImage(
            image = atlas,
            srcOffset = IntOffset((index % Asteroids.ATLAS_COLS) * cell, (index / Asteroids.ATLAS_COLS) * cell),
            srcSize = IntSize(cell, cell),
            dstOffset = IntOffset((cx - side / 2f).roundToInt(), (cy - side / 2f).roundToInt()),
            dstSize = IntSize(side.roundToInt(), side.roundToInt()),
            filterQuality = FilterQuality.Medium
        )
    }
}

/** Le vaisseau du joueur (celui d'Astéroïdes, nez en haut), avec deux petites flammes qui vacillent, et un anneau de protection au retour. */
private fun DrawScope.drawPlayer(game: InvadersGame, revision: Int, sprite: ImageBitmap?, u: Float) {
    val side = Invaders.PLAYER_SPRITE * u
    val cx = game.playerX * u
    val cy = Invaders.PLAYER_Y * u
    val flicker = when (revision % 3) {
        0 -> 1f
        1 -> 0.7f
        else -> 0.85f
    }
    for (offset in listOf(-0.14f, 0.14f)) {
        val x = cx + side * offset
        val top = cy + side * 0.36f
        val halfWidth = side * 0.07f
        val length = side * 0.28f * flicker
        val outer = Path().apply {
            moveTo(x - halfWidth, top)
            lineTo(x + halfWidth, top)
            lineTo(x, top + length)
            close()
        }
        drawPath(outer, Color(0xFFFF9800))
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
    if (game.invulnerableMs > 0) {
        // Le bouclier : un anneau qui s'efface en fin de protection.
        val fade = (game.invulnerableMs / 700f).coerceIn(0.25f, 1f)
        drawCircle(INVADER_SHIELD.copy(alpha = 0.55f * fade), side * 0.62f, Offset(cx, cy), style = Stroke(width = maxOf(2f, side * 0.04f)))
    }
}

private fun findInvadersLifecycleOwner(context: Context): LifecycleOwner? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return null
}
