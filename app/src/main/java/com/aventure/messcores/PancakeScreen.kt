package com.aventure.messcores

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

// Les étapes de la séance.
private const val PANCAKE_PHASE_READY = 0 // on attend que le joueur lance la partie
private const val PANCAKE_PHASE_PLAYING = 1 // les crêpes volent
private const val PANCAKE_PHASE_OVER = 2 // cinq crêpes sont tombées : la partie est finie

/** Plus long temps d'une image pris en compte : après un à-coup, les crêpes ne doivent pas sauter plusieurs positions d'un coup. */
private const val PANCAKE_MAX_FRAME_MS = 100L

// Le dessin de la console, en unités de la photo d'origine recadrée (633 x 423).
private const val CONSOLE_W = 633f
private const val CONSOLE_H = 423f
private const val SCREEN_X = 173f
private const val SCREEN_Y = 59f
private const val BUTTON_LEFT_X = 76f
private const val BUTTON_LEFT_Y = 269.5f
private const val BUTTON_RIGHT_X = 556.5f
private const val BUTTON_RIGHT_Y = 270.3f
private const val BUTTON_RADIUS = 27f

/** Part de la largeur de la console, de chaque côté, où un appui déplace le serveur. */
private const val TOUCH_ZONE = 0.27f

/** Décalage, vers la gauche, du chiffre des dizaines par rapport à celui des unités (en unités de l'écran). */
private const val TENS_SHIFT = -17f

/** Les segments allumés de chaque chiffre, dans l'ordre a (haut), b, c, d (bas), e, f, g (milieu) de [PancakeArt.UNITS]. */
private val PancakeDigits = listOf(
    intArrayOf(0, 1, 2, 3, 4, 5),
    intArrayOf(1, 2),
    intArrayOf(0, 1, 6, 4, 3),
    intArrayOf(0, 1, 6, 2, 3),
    intArrayOf(5, 6, 1, 2),
    intArrayOf(0, 5, 6, 2, 3),
    intArrayOf(0, 5, 6, 4, 2, 3),
    intArrayOf(0, 1, 2),
    intArrayOf(0, 1, 2, 3, 4, 5, 6),
    intArrayOf(0, 1, 2, 3, 5, 6)
)

/** Les morceaux du cuisinier (a à e de [PancakeArt.CHEF]) allumés pour chacune de ses six positions. */
private val PancakeChefSets = listOf(
    intArrayOf(0, 4),
    intArrayOf(0),
    intArrayOf(0, 1),
    intArrayOf(1, 3, 4),
    intArrayOf(3, 4),
    intArrayOf(2, 3)
)

/** Les segments à cristaux liquides sont gris très foncé, jamais tout à fait noirs. */
private val PancakeInkColor = Color(0xE62A2724)

/** Garde la partie quand l'écran tourne : son état est rangé dans une liste d'entiers (voir [PancakeGame.save]). */
private val PancakeGameSaver = listSaver<PancakeGame, Int>(
    save = { it.save() },
    restore = { PancakeGame.restore(it) }
)

/** Les tracés de [PancakeArt], lus une seule fois. */
private class PancakeShapes {
    private fun parse(d: String): Path = PathParser().parsePathString(d).toPath().apply { fillType = PathFillType.EvenOdd }

    val layers: List<Pair<Color, Path>> = PancakeArt.LAYERS.map { (color, d) -> Color(color) to parse(d) }
    val waiters: List<Path> = listOf(PancakeArt.WAITER_LEFT, PancakeArt.WAITER_CENTER, PancakeArt.WAITER_RIGHT).map { parse(it) }
    val steps: List<Path> = PancakeArt.STEPS.map { parse(it) }
    val blobs: List<Path> = listOf(PancakeArt.BLOB_LEFT, PancakeArt.BLOB_CENTER, PancakeArt.BLOB_RIGHT).map { parse(it) }
    val splats: List<Path> = listOf(PancakeArt.SPLAT_LEFT, PancakeArt.SPLAT_CENTER, PancakeArt.SPLAT_RIGHT).map { parse(it) }
    val approach: Path = parse(PancakeArt.APPROACH)
    val diner: Path = parse(PancakeArt.DINER)
    val table: Path = parse(PancakeArt.TABLE)
    val hand: Path = parse(PancakeArt.HAND)
    val markers: List<Path> = PancakeArt.MARKERS.map { parse(it) }
    val chef: List<Path> = PancakeArt.CHEF.map { parse(it) }
    val units: List<Path> = PancakeArt.UNITS.map { parse(it) }
}

/**
 * Pancake en solo, d'après la console à écran à cristaux liquides de 1981 : le cuisinier fait sauter des crêpes qui
 * traversent l'écran en arcs, d'assiette en assiette, jusqu'au client. Les deux boutons ronds (ou les deux côtés de la
 * console) font sauter le serveur d'une voie à l'autre pour rattraper chaque crêpe ; une crêpe tombée au sol compte pour
 * un raté, et la partie s'arrête au cinquième. Le meilleur score est gardé d'une séance à l'autre. Elle se met en
 * pause quand l'appli passe en arrière-plan. Tout est gardé quand l'écran tourne.
 * [players] : le joueur dont le nom s'affiche au départ.
 */
@Composable
internal fun PancakeScreen(players: List<String>) {
    val name = players.firstOrNull().orEmpty().ifEmpty { "1" }
    val context = LocalContext.current
    val game = rememberSaveable(saver = PancakeGameSaver) { PancakeGame() }
    var phase by rememberSaveable { mutableIntStateOf(PANCAKE_PHASE_READY) }
    var paused by rememberSaveable { mutableStateOf(false) }
    val record = rememberSoloRecord(PANCAKE_ID)

    // La partie se met en pause quand l'appli passe en arrière-plan (et donc aussi quand l'écran tourne).
    val lifecycleOwner = remember(context) { findPancakeLifecycleOwner(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && phase == PANCAKE_PHASE_PLAYING) paused = true
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    // Le bouton retour met la partie en pause plutôt que de la quitter par mégarde.
    BackHandler(enabled = phase == PANCAKE_PHASE_PLAYING && !paused) { paused = true }

    fun startGame() {
        record.clearNew()
        game.reset()
        paused = false
        phase = PANCAKE_PHASE_PLAYING
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (phase) {
            PANCAKE_PHASE_PLAYING -> PancakePlayfield(
                game = game,
                paused = paused,
                onPausedChange = { paused = it },
                onOver = {
                    record.submit(game.score)
                    phase = PANCAKE_PHASE_OVER
                }
            )
            PANCAKE_PHASE_OVER -> PancakeCard {
                Text(
                    text = stringResource(R.string.pancake_over_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.pancake_score, game.score),
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
                SoloRecordLine(record)
                Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pancake_play_again))
                }
            }
            else -> PancakeCard {
                Text(
                    text = stringResource(R.string.pancake_ready_title, name),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.pancake_ready_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                SoloRecordLine(record)
                Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pancake_go))
                }
            }
        }
        RecordSparkles(record)
    }
}

/** Les cartes de la séance : le titre du jeu, ses règles, puis [content]. */
@Composable
private fun PancakeCard(content: @Composable ColumnScope.() -> Unit) {
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
                        text = stringResource(R.string.game_pancake),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    RulesButton(
                        gameId = PANCAKE_ID,
                        gameName = stringResource(R.string.game_pancake),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                content()
            }
        }
    }
}

/**
 * La partie en cours : l'en-tête (score, crêpes tombées, pause) puis la console. La partie avance à chaque image tant
 * qu'elle n'est pas en [paused] ; quand la cinquième crêpe tombe, [onOver] est appelé une fois.
 */
@Composable
private fun PancakePlayfield(
    game: PancakeGame,
    paused: Boolean,
    onPausedChange: (Boolean) -> Unit,
    onOver: () -> Unit
) {
    KeepScreenOn()
    // Change à chaque image ou action : le dessin le lit et se refait, sans recomposer tout l'écran.
    var revision by remember { mutableIntStateOf(0) }
    // Les chiffres de l'en-tête : ils ne recomposent l'écran que lorsqu'ils changent.
    var score by remember { mutableIntStateOf(game.score) }
    var misses by remember { mutableIntStateOf(game.misses) }
    val finished by rememberUpdatedState(onOver)
    var leftDown by remember { mutableStateOf(false) }
    var rightDown by remember { mutableStateOf(false) }
    val shapes = remember { PancakeShapes() }
    val ink = ImageBitmap.imageResource(R.drawable.pancake_ink)
    val console = ImageBitmap.imageResource(R.drawable.pancake_console)

    fun sync() {
        revision++
        score = game.score
        misses = game.misses
    }

    /** Un saut du serveur demandé par le joueur, sans effet en pause ou une fois la partie finie. */
    fun move(toLeft: Boolean) {
        if (paused || game.isOver) return
        if (toLeft) game.moveLeft() else game.moveRight()
        sync()
    }

    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (!game.isOver) {
            val now = withFrameNanos { it }
            val elapsed = ((now - last) / 1_000_000L).coerceIn(0L, PANCAKE_MAX_FRAME_MS)
            last = now
            game.tick(elapsed)
            sync()
        }
        finished()
    }

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
                        text = stringResource(R.string.pancake_score, score),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.pancake_misses, misses, Pancake.MAX_MISSES),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { onPausedChange(true) }, enabled = !paused) {
                    Text(stringResource(R.string.pancake_pause))
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // La console garde ses proportions : la plus grande qui tient dans la place disponible.
            val ratio = CONSOLE_W / CONSOLE_H
            val consoleWidth = minOf(maxWidth, maxHeight * ratio)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(consoleWidth, consoleWidth / ratio)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawPancakeConsole(game, revision, shapes, ink, console, leftDown, rightDown)
                }
                // Les deux côtés de la console : un appui déplace le serveur dès que le doigt se pose.
                PancakeZone(
                    description = stringResource(R.string.pancake_left),
                    modifier = Modifier.align(Alignment.CenterStart),
                    onPress = { down ->
                        leftDown = down
                        if (down) move(toLeft = true)
                    }
                )
                PancakeZone(
                    description = stringResource(R.string.pancake_right),
                    modifier = Modifier.align(Alignment.CenterEnd),
                    onPress = { down ->
                        rightDown = down
                        if (down) move(toLeft = false)
                    }
                )
                if (paused) {
                    Column(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.pancake_paused_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(8.dp)
                        )
                        Button(onClick = { onPausedChange(false) }) {
                            Text(stringResource(R.string.pancake_resume))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Un côté de la console, sur toute sa hauteur, qui réagit dès que le doigt se pose (attendre qu'il se lève ferait
 * perdre un temps précieux) : [onPress] reçoit vrai à l'appui puis faux au relâchement.
 */
@Composable
private fun PancakeZone(
    description: String,
    modifier: Modifier = Modifier,
    onPress: (Boolean) -> Unit
) {
    val action by rememberUpdatedState(onPress)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(TOUCH_ZONE)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
                // Pour TalkBack et les autres aides : l'action sans passer par le geste.
                onClick(label = description) {
                    action(true)
                    action(false)
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    action(true)
                    tryAwaitRelease()
                    action(false)
                })
            }
    )
}

/** Le centre d'un des boutons ronds, en pixels, pour une console large de [width] pixels. */
private fun buttonCenter(x: Float, y: Float, width: Float): Offset = Offset(x * width / CONSOLE_W, y * width / CONSOLE_W)

/** La console : l'écran (fond, personnages, crêpes) sous le dessin de la console, dont l'écran est évidé. */
private fun DrawScope.drawPancakeConsole(
    game: PancakeGame,
    @Suppress("UNUSED_PARAMETER") revision: Int,
    shapes: PancakeShapes,
    ink: ImageBitmap,
    console: ImageBitmap,
    leftDown: Boolean,
    rightDown: Boolean
) {
    val k = size.width / CONSOLE_W
    val screenTopLeft = Offset(SCREEN_X * k, SCREEN_Y * k)
    val screenSize = Size(PancakeArt.WIDTH * k, PancakeArt.HEIGHT * k)
    clipRect(screenTopLeft.x, screenTopLeft.y, screenTopLeft.x + screenSize.width, screenTopLeft.y + screenSize.height) {
        withTransform({
            translate(screenTopLeft.x, screenTopLeft.y)
            scale(k, k, pivot = Offset.Zero)
        }) {
            drawPancakeScreen(game, shapes, ink)
        }
    }
    drawImage(
        console,
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        filterQuality = FilterQuality.High
    )
    // Un bouton rond enfoncé s'assombrit.
    val pressed = Color.Black.copy(alpha = 0.3f)
    if (leftDown) drawCircle(pressed, BUTTON_RADIUS * k, buttonCenter(BUTTON_LEFT_X, BUTTON_LEFT_Y, size.width))
    if (rightDown) drawCircle(pressed, BUTTON_RADIUS * k, buttonCenter(BUTTON_RIGHT_X, BUTTON_RIGHT_Y, size.width))
}

/** L'écran à cristaux liquides, dans son propre repère de 288 x 218 : fond, personnages fixes, puis segments allumés. */
private fun DrawScope.drawPancakeScreen(game: PancakeGame, shapes: PancakeShapes, ink: ImageBitmap) {
    drawRect(Color(PancakeArt.PAPER), size = Size(PancakeArt.WIDTH, PancakeArt.HEIGHT))
    for ((color, path) in shapes.layers) drawPath(path, color)
    // Le client, le cuisinier au repos, le fourneau et le titre, dessinés en une seule image.
    drawImage(
        ink,
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(PancakeArt.WIDTH.toInt(), PancakeArt.HEIGHT.toInt()),
        filterQuality = FilterQuality.High
    )
    fun lit(path: Path) = drawPath(path, PancakeInkColor)

    // Le cuisinier.
    for (i in PancakeChefSets[game.chefPhase]) lit(shapes.chef[i])
    // Les crêpes tombées au sol.
    val misses = if (game.isOver) Pancake.MAX_MISSES else game.misses
    for (i in 0 until misses.coerceAtMost(shapes.markers.size)) lit(shapes.markers[i])
    // Le score : deux chiffres, les dizaines seulement à partir de dix.
    val score = game.score.coerceAtMost(99)
    for (i in PancakeDigits[score % 10]) lit(shapes.units[i])
    if (score >= 10) {
        withTransform({ translate(TENS_SHIFT, 0f) }) {
            for (i in PancakeDigits[score / 10]) lit(shapes.units[i])
        }
    }
    // Le serveur.
    lit(shapes.waiters[game.waiter])
    // Les crêpes en vol, posées sur une assiette ou servies.
    for (sprite in game.sprites()) {
        when (sprite) {
            is Pancake.Sprite.Step -> lit(shapes.steps[sprite.index])
            is Pancake.Sprite.OnPlate -> lit(shapes.blobs[sprite.lane])
            Pancake.Sprite.Approach -> lit(shapes.approach)
            Pancake.Sprite.Diner -> lit(shapes.diner)
            Pancake.Sprite.Table -> lit(shapes.table)
            Pancake.Sprite.Hand -> lit(shapes.hand)
        }
    }
    // La crêpe écrasée au sol.
    if (game.splatLane >= 0) lit(shapes.splats[game.splatLane])
}

private fun findPancakeLifecycleOwner(context: Context): LifecycleOwner? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return null
}
