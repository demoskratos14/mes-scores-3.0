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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// Les étapes de la séance.
private const val SHMUP_PHASE_READY = 0 // on attend que le joueur lance la partie
private const val SHMUP_PHASE_PLAYING = 1 // la partie est en cours
private const val SHMUP_PHASE_OVER = 2 // la dernière vie est perdue : la partie est finie

/** Plus long temps d'une image pris en compte : après un à-coup, rien ne doit sauter loin d'un coup. */
private const val SHMUP_MAX_FRAME_MS = 100L

/** Les étoiles du fond (part de la largeur, part de la hauteur, rayon en unités du terrain), tirées une fois pour toutes. */
private val SHMUP_STARS: List<Triple<Float, Float, Float>> = Random(77).let { random ->
    List(60) { Triple(random.nextFloat(), random.nextFloat(), 0.01f + random.nextFloat() * 0.03f) }
}

private val SHMUP_PLATFORM_COLOR = Color(0x99252B3D)

/** Ce qu'affiche l'annonce au milieu du terrain : rien, l'arrivée du boss ou le nom du plateau. */
private const val BANNER_NONE = 0
private const val BANNER_WARNING = 1
private const val BANNER_STAGE = 2

private const val PI_F = 3.1415927f

/** La couleur de chaque niveau d'arme (de 1 à 5) : celle des missiles, du laser et de la lueur autour du vaisseau. */
private val SHMUP_WEAPON_COLORS = listOf(
    Color(0xFF7FE8FF), Color(0xFFFFEB3B), Color(0xFFFF9800), Color(0xFFE040FB), Color(0xFFFFFFFF)
)

/** Les traînées du saut en vitesse lumière (angle depuis le centre, position de départ, éclat), tirées une fois pour toutes. */
private val SHMUP_HYPER_STARS: List<Triple<Float, Float, Float>> = Random(5).let { random ->
    List(90) { Triple(random.nextFloat() * 2f * PI_F, random.nextFloat(), random.nextFloat()) }
}
private val SHMUP_ENEMY_SHOT_COLOR = Color(0xFFFF9F43)
private val SHMUP_SPARK_COLOR = Color(0xFFFFB340)

/** Garde la partie quand l'écran tourne : son état est rangé dans une liste d'entiers (voir [ShmupGame.save]). */
private val ShmupGameSaver = listSaver<ShmupGame, Int>(
    save = { it.save() },
    restore = { ShmupGame.restore(it) }
)

/**
 * Shoot'em up à défilement vertical en solo : on glisse le doigt pour piloter le vaisseau, qui tire tout seul, on détruit
 * les ennemis qui descendent et on évite leurs tirs. Trois vies ; le niveau monte avec le temps. Le meilleur score est
 * gardé d'une séance à l'autre. La partie se met en pause quand l'appli passe en arrière-plan, et tout est gardé quand
 * l'écran tourne. [players] : le joueur dont le nom s'affiche au départ.
 */
@Composable
internal fun ShmupScreen(players: List<String>) {
    val name = players.firstOrNull().orEmpty().ifEmpty { "1" }
    val context = LocalContext.current
    val game = rememberSaveable(saver = ShmupGameSaver) { ShmupGame() }
    var phase by rememberSaveable { mutableIntStateOf(SHMUP_PHASE_READY) }
    var paused by rememberSaveable { mutableStateOf(false) }
    val record = rememberSoloRecord(SHMUP_ID)

    // La partie se met en pause quand l'appli passe en arrière-plan (et donc aussi quand l'écran tourne).
    val lifecycleOwner = remember(context) { findShmupLifecycleOwner(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && phase == SHMUP_PHASE_PLAYING) paused = true
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    // Le bouton retour met la partie en pause plutôt que de la quitter par mégarde.
    BackHandler(enabled = phase == SHMUP_PHASE_PLAYING && !paused) { paused = true }

    fun startGame() {
        record.clearNew()
        game.reset()
        paused = false
        phase = SHMUP_PHASE_PLAYING
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (phase) {
            SHMUP_PHASE_PLAYING -> ShmupPlayfield(
                game = game,
                paused = paused,
                onPausedChange = { paused = it },
                onOver = {
                    record.submit(game.score)
                    phase = SHMUP_PHASE_OVER
                }
            )
            SHMUP_PHASE_OVER -> ShmupCard {
                Text(
                    text = stringResource(R.string.shmup_over_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.shmup_score, game.score),
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
                    text = stringResource(R.string.shmup_level_reached, game.level),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                SoloRecordLine(record)
                Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.shmup_play_again))
                }
            }
            else -> ShmupCard {
                Text(
                    text = stringResource(R.string.shmup_ready_title, name),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.shmup_ready_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                SoloRecordLine(record)
                Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.shmup_go))
                }
            }
        }
        RecordSparkles(record)
    }
}

/** Le plateau des cartes de la séance : le titre du jeu, ses règles, puis [content]. */
@Composable
private fun ShmupCard(content: @Composable ColumnScope.() -> Unit) {
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
                        text = stringResource(R.string.game_shmup),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    RulesButton(
                        gameId = SHMUP_ID,
                        gameName = stringResource(R.string.game_shmup),
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
private fun ShmupPlayfield(
    game: ShmupGame,
    paused: Boolean,
    onPausedChange: (Boolean) -> Unit,
    onOver: () -> Unit
) {
    KeepScreenOn()
    val ship = ImageBitmap.imageResource(R.drawable.shmup_ship)
    // Un sprite par type d'ennemi, dans l'ordre de l'énumération.
    val sprites = EnemyType.values().map { ImageBitmap.imageResource(enemySprite(it)) }
    // Les fonds des plateaux 2, 3 et 4 (le plateau 1, l'espace, est dessiné).
    val backgrounds = listOf(
        ImageBitmap.imageResource(R.drawable.shmup_bg_dirt),
        ImageBitmap.imageResource(R.drawable.shmup_bg_airbase),
        ImageBitmap.imageResource(R.drawable.shmup_bg_dunes)
    )
    // Change à chaque image : le dessin le lit et se refait, sans recomposer tout l'écran.
    var revision by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(game.score) }
    var lives by remember { mutableIntStateOf(game.lives) }
    var level by remember { mutableIntStateOf(game.level) }
    var weapon by remember { mutableIntStateOf(game.weapon) }
    var banner by remember { mutableIntStateOf(BANNER_NONE) }
    var bannerStage by remember { mutableIntStateOf(game.stage + 1) }
    val finished by rememberUpdatedState(onOver)
    val isPaused by rememberUpdatedState(paused)

    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (!game.isOver) {
            val now = withFrameNanos { it }
            val elapsed = ((now - last) / 1_000_000L).coerceIn(0L, SHMUP_MAX_FRAME_MS)
            last = now
            game.tick(elapsed)
            revision++
            score = game.score
            lives = game.lives
            level = game.level
            weapon = game.weapon
            banner = when {
                game.phase == ShmupPhase.TRANSITION && game.transitionProgress >= 0.5f -> BANNER_STAGE
                game.phase == ShmupPhase.BOSS && game.bossIntroMs > 0L -> BANNER_WARNING
                else -> BANNER_NONE
            }
            bannerStage = game.stage + 1
        }
        finished()
    }

    val fieldDescription = stringResource(R.string.shmup_field)

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
                        text = stringResource(R.string.shmup_score, score),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.shmup_stats, lives, level, weapon),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { onPausedChange(true) }, enabled = !paused) {
                    Text(stringResource(R.string.shmup_pause))
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Le cadre garde les proportions du terrain (9 × 16 unités).
            val unit = maxOf(minOf(maxWidth / Shmup.WIDTH, maxHeight / Shmup.HEIGHT), 0.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(unit * Shmup.WIDTH, unit * Shmup.HEIGHT)
                    .clip(RoundedCornerShape(8.dp))
                    .semantics { contentDescription = fieldDescription }
                    // Glisser déplace le vaisseau comme le doigt (sans qu'il saute sous lui) ; il tire tout seul.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            val pixelsPerUnit = size.width / Shmup.WIDTH
                            var pressed = true
                            while (pressed) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { change ->
                                    val move = change.positionChange()
                                    if ((move.x != 0f || move.y != 0f) && !isPaused) {
                                        game.movePlayerTo(
                                            game.playerX + move.x / pixelsPerUnit,
                                            game.playerY + move.y / pixelsPerUnit
                                        )
                                        change.consume()
                                    }
                                }
                                if (event.changes.none { it.pressed }) pressed = false
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) { drawShmupField(game, ship, sprites, backgrounds, revision) }
                if (banner != BANNER_NONE) {
                    Text(
                        text = if (banner == BANNER_WARNING) {
                            stringResource(R.string.shmup_warning)
                        } else {
                            stringResource(R.string.shmup_stage, bannerStage)
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
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
                            text = stringResource(R.string.shmup_paused_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(8.dp)
                        )
                        Button(onClick = { onPausedChange(false) }) {
                            Text(stringResource(R.string.shmup_resume))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Le terrain, dessiné en unités du terrain mises à l'échelle : le fond du plateau qui défile, les ennemis, les pastilles, les
 * tirs, le laser, les étincelles, le vaisseau, puis le saut en vitesse lumière par-dessus. [revision] n'est là que pour que le
 * dessin se refasse quand elle change.
 */
private fun DrawScope.drawShmupField(
    game: ShmupGame,
    ship: ImageBitmap,
    sprites: List<ImageBitmap>,
    backgrounds: List<ImageBitmap>,
    @Suppress("UNUSED_PARAMETER") revision: Int
) {
    val u = size.width / Shmup.WIDTH
    drawStageBackground(game, backgrounds, u)

    // Les ennemis : chacun a son sprite ; ceux posés au sol reposent sur une plateforme sombre qui défile avec le fond.
    for (e in game.enemies) {
        val type = e.type
        val left = (e.x - type.w / 2f) * u
        val top = (e.y - type.h / 2f) * u
        if (type.ground) {
            drawRoundRect(
                color = SHMUP_PLATFORM_COLOR,
                topLeft = Offset(left - 0.12f * u, top - 0.12f * u),
                size = Size((type.w + 0.24f) * u, (type.h + 0.24f) * u),
                cornerRadius = CornerRadius(0.2f * u)
            )
        }
        drawImage(
            image = sprites[type.ordinal],
            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
            dstSize = IntSize((type.w * u).roundToInt(), (type.h * u).roundToInt())
        )
    }

    // La barre de vie du boss.
    game.boss?.let { boss ->
        val barW = Shmup.WIDTH * 0.7f * u
        val left = (size.width - barW) / 2f
        val top = 0.3f * u
        val h = 0.28f * u
        drawRoundRect(Color.Black.copy(alpha = 0.6f), Offset(left - 2f, top - 2f), Size(barW + 4f, h + 4f), CornerRadius(h / 2f))
        val ratio = (boss.hp.toFloat() / boss.maxHp).coerceIn(0f, 1f)
        if (ratio > 0f) drawRoundRect(Color(0xFFE53935), Offset(left, top), Size(barW * ratio, h), CornerRadius(h / 2f))
    }

    // Les pastilles : vert avec un plus pour un bonus, rouge avec un moins pour un malus.
    for (p in game.pickups) {
        val c = Offset(p.x * u, p.y * u)
        val r = Shmup.PICKUP_HALF * u
        drawCircle(if (p.bonus) Color(0xFF43A047) else Color(0xFFD32F2F), r, c)
        drawCircle(Color.White, r, c, style = Stroke(width = r * 0.18f))
        val bar = r * 0.95f
        val thick = r * 0.28f
        drawRect(Color.White, Offset(c.x - bar / 2f, c.y - thick / 2f), Size(bar, thick))
        if (p.bonus) drawRect(Color.White, Offset(c.x - thick / 2f, c.y - bar / 2f), Size(thick, bar))
    }

    for (b in game.enemyBullets) {
        drawCircle(SHMUP_ENEMY_SHOT_COLOR, Shmup.ENEMY_BULLET_HIT * 1.2f * u, Offset(b.x * u, b.y * u))
    }

    // Le laser du vaisseau (armes de niveau 4 et 5) : une colonne lumineuse jusqu'en haut du terrain.
    val weaponColor = SHMUP_WEAPON_COLORS[(game.weapon - 1).coerceIn(0, SHMUP_WEAPON_COLORS.size - 1)]
    if (game.laserOn) {
        val half = if (game.weapon >= Shmup.MAX_WEAPON) Shmup.LASER_WIDE_HALF_W else Shmup.LASER_HALF_W
        val bottom = (game.playerY - Shmup.SHIP_H / 2f) * u
        for ((width, color) in listOf(
            3.6f to weaponColor.copy(alpha = 0.30f),
            2f to weaponColor.copy(alpha = 0.75f),
            0.9f to Color.White
        )) {
            drawRect(color, Offset((game.playerX - half * width / 2f * 2f) * u, 0f), Size(half * width * 2f * u, bottom))
        }
    }

    // Les missiles : une traînée colorée selon le niveau de l'arme, avec une tête claire.
    for (b in game.bullets) {
        val speed = hypot(b.vx, b.vy)
        if (speed <= 0f) continue
        val color = SHMUP_WEAPON_COLORS[(b.lvl - 1).coerceIn(0, SHMUP_WEAPON_COLORS.size - 1)]
        val head = Offset(b.x * u, b.y * u)
        drawLine(color.copy(alpha = 0.6f), Offset((b.x - b.vx / speed * 0.5f) * u, (b.y - b.vy / speed * 0.5f) * u), head, 0.16f * u, StrokeCap.Round)
        drawLine(Color.White, Offset((b.x - b.vx / speed * 0.18f) * u, (b.y - b.vy / speed * 0.18f) * u), head, 0.09f * u, StrokeCap.Round)
    }

    for (s in game.sparks) {
        drawCircle(
            SHMUP_SPARK_COLOR.copy(alpha = (s.lifeMs / 400f).coerceIn(0f, 1f)),
            0.05f * u,
            Offset(s.x * u, s.y * u)
        )
    }

    // Le vaisseau : une lueur à la couleur de l'arme dès le niveau 2, et il clignote tant qu'il est protégé après un impact.
    if (game.invulnMs <= 0L || (game.invulnMs / 100L) % 2L == 0L) {
        if (game.weapon > 1) drawCircle(weaponColor.copy(alpha = 0.22f), 0.95f * u, Offset(game.playerX * u, game.playerY * u))
        drawImage(
            image = ship,
            dstOffset = IntOffset(
                ((game.playerX - Shmup.SHIP_W / 2f) * u).roundToInt(),
                ((game.playerY - Shmup.SHIP_H / 2f) * u).roundToInt()
            ),
            dstSize = IntSize((Shmup.SHIP_W * u).roundToInt(), (Shmup.SHIP_H * u).roundToInt())
        )
    }

    if (game.phase == ShmupPhase.TRANSITION) drawHyperspace(game.transitionProgress)
}

/**
 * Le fond du plateau. Le plateau 1 est l'espace (un dégradé et des étoiles qui défilent) ; les autres sont des photos qui
 * défilent vers le bas, chaque copie étant retournée par rapport à la précédente pour qu'on ne voie pas de raccord.
 */
private fun DrawScope.drawStageBackground(game: ShmupGame, backgrounds: List<ImageBitmap>, u: Float) {
    if (game.stage == 0) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF03050C), Color(0xFF0A1430), Color(0xFF13214A))))
        // Les grosses étoiles défilent plus vite que les petites : un peu de profondeur.
        SHMUP_STARS.forEach { (fx, fy, radius) ->
            val y = ((fy + game.scroll / Shmup.HEIGHT * (0.5f + radius * 20f)) % 1f) * size.height
            drawCircle(Color.White.copy(alpha = 0.35f + radius * 15f), radius * u, Offset(fx * size.width, y))
        }
        return
    }
    val image = backgrounds[(game.stage - 1).coerceIn(0, backgrounds.size - 1)]
    val tileH = size.width * image.height / image.width
    val period = tileH * 2f
    var top = (game.scroll * u) % period - period
    var index = 0
    val dstSize = IntSize(size.width.roundToInt(), tileH.roundToInt() + 1)
    while (top < size.height) {
        if (top + tileH > 0f) {
            val dstOffset = IntOffset(0, top.roundToInt())
            if (index % 2 == 0) {
                drawImage(image = image, dstOffset = dstOffset, dstSize = dstSize)
            } else {
                withTransform({ scale(1f, -1f, Offset(size.width / 2f, top + tileH / 2f)) }) {
                    drawImage(image = image, dstOffset = dstOffset, dstSize = dstSize)
                }
            }
        }
        top += tileH
        index++
    }
    // Un voile sombre pour que les ennemis et les tirs ressortent sur la photo.
    drawRect(Color.Black.copy(alpha = 0.28f))
}

/** Le saut en vitesse lumière : des traînées qui jaillissent du centre, de plus en plus longues, et un éclair au milieu. */
private fun DrawScope.drawHyperspace(progress: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val maxDist = hypot(cx, cy)
    val speed = sin(progress * PI_F)
    drawRect(Color(0xFF020512).copy(alpha = (0.75f * speed).coerceIn(0f, 1f)))
    for ((angle, start, bright) in SHMUP_HYPER_STARS) {
        val dist = ((start + progress * 2.2f * (0.4f + start)) % 1f) * maxDist
        val length = (maxDist * 0.02f + maxDist * 0.35f * speed) * (0.2f + dist / maxDist)
        val dx = cos(angle)
        val dy = sin(angle)
        drawLine(
            Color(0xFFBFD9FF).copy(alpha = ((0.35f + 0.65f * bright) * min(1f, speed * 2f + 0.2f)).coerceIn(0f, 1f)),
            Offset(cx + dx * dist, cy + dy * dist),
            Offset(cx + dx * (dist + length), cy + dy * (dist + length)),
            1.5f + 2.5f * bright,
            StrokeCap.Round
        )
    }
    // L'éclair : c'est à ce moment que le décor change.
    val flash = (1f - abs(progress - 0.5f) * 4f).coerceIn(0f, 1f)
    if (flash > 0f) drawRect(Color.White.copy(alpha = flash))
}

/** Le sprite de chaque type d'ennemi (tiré du modèle 3D correspondant, vu de dessus). */
private fun enemySprite(type: EnemyType): Int = when (type) {
    EnemyType.WASP -> R.drawable.enemy_wasp
    EnemyType.K24 -> R.drawable.enemy_k24
    EnemyType.D14 -> R.drawable.enemy_d14
    EnemyType.TANK -> R.drawable.enemy_tank
    EnemyType.L9G -> R.drawable.enemy_l9g
    EnemyType.FALL_TURRET -> R.drawable.enemy_fall_turret
    EnemyType.D2 -> R.drawable.enemy_d2
    EnemyType.K23 -> R.drawable.enemy_k23
    EnemyType.D18 -> R.drawable.enemy_d18
    EnemyType.K27 -> R.drawable.enemy_k27
    EnemyType.SHERMAN_TURRET -> R.drawable.enemy_sherman_turret
    EnemyType.D9 -> R.drawable.enemy_d9
    EnemyType.D20 -> R.drawable.enemy_d20
    EnemyType.REDFOR_TURRET -> R.drawable.enemy_redfor_turret
    EnemyType.D4 -> R.drawable.enemy_d4
    EnemyType.D16 -> R.drawable.enemy_d16
    EnemyType.CORVETTE -> R.drawable.enemy_corvette
    EnemyType.CONSTELLATION -> R.drawable.enemy_constellation
    EnemyType.BOSS_SAUCER -> R.drawable.boss_saucer
    EnemyType.BOSS_TANK -> R.drawable.boss_tank
    EnemyType.BOSS_SHUTTLE -> R.drawable.boss_shuttle
    EnemyType.BOSS_LAUNCHER -> R.drawable.boss_launcher
    EnemyType.STARSHIP -> R.drawable.enemy_starship
    EnemyType.TANK_TOP -> R.drawable.enemy_tank_top
    EnemyType.TURRET_HEAVY -> R.drawable.enemy_turret_heavy
}

private fun findShmupLifecycleOwner(context: Context): LifecycleOwner? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return null
}
