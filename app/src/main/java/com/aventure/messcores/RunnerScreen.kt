package com.aventure.messcores

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Les étapes de la séance.
private const val RUNNER_PHASE_READY = 0 // on attend que le joueur lance la partie
private const val RUNNER_PHASE_PLAYING = 1 // le coureur court
private const val RUNNER_PHASE_OVER = 2 // le coureur s'est écrasé : la partie est finie

/** Les actions de la Course réglables à la manette : sauter, se baisser, changer de voie (croix ou stick gauche par défaut) et la pause. */
private val RUNNER_PAD_ACTIONS = listOf(
    padDirection("up", R.string.runner_up, horizontal = false, sign = -1),
    padDirection("down", R.string.runner_down, horizontal = false, sign = 1),
    padDirection("left", R.string.runner_left, horizontal = true, sign = -1),
    padDirection("right", R.string.runner_right, horizontal = true, sign = 1),
    // Start met en pause, reprend, et lance une partie depuis l'écran d'accueil ou de fin.
    PadAction("pause", R.string.runner_pad_pause, listOf(PadInput.Key(KeyEvent.KEYCODE_BUTTON_START)))
)

/** Plus long temps d'une image pris en compte : après un à-coup, le coureur ne doit pas sauter plusieurs mètres d'un coup. */
private const val MAX_FRAME_MS = 100L

/** Distance glissée (en dp) à partir de laquelle un geste du doigt compte (voie, saut ou glissade). */
private const val SWIPE_DP = 22

/** Durée de l'animation de chute avant l'écran de fin. */
private const val DEATH_MS = 1100L

/** Résolution de calcul du décor 3D, en part de la taille de la vue : elle s'adapte à la vitesse du téléphone entre ces bornes. */
private const val SCALE_START = 0.36f
private const val SCALE_MIN = 0.2f
private const val SCALE_MAX = 0.5f

/** Images par seconde de l'animation de course, et hauteur du coureur dessiné (en mètres du monde). */
private const val RUN_FPS = 16f
private const val SPRITE_HEIGHT_M = 1.2f

private val SkyTop = Color(0xFF6FA8E6)
private val SkyHorizon = Color(0xFFD6E8FA)
private val GroundColor = Color(0xFF5F8A49)

/**
 * Ce que la Course charge une fois : les modèles 3D (fichiers .glb du dossier assets/runner) et les images du coureur (fichiers assassin_….png du même dossier).
 * Le chargement se fait hors du fil principal (voir [RunnerScreen]) ; un fichier absent est simplement ignoré.
 */
internal object RunnerAssets {
    private val meshes = HashMap<String, Mesh3D>()

    var running: List<ImageBitmap> = emptyList()
        private set
    var hurt: List<ImageBitmap> = emptyList()
        private set
    var die: List<ImageBitmap> = emptyList()
        private set

    @Volatile
    var isReady: Boolean = false
        private set

    /** Le maillage déjà chargé de [name] (sans extension), ou null. */
    @Synchronized
    fun mesh(name: String): Mesh3D? = meshes[name]

    @Synchronized
    fun load(assets: AssetManager) {
        if (isReady) return
        for (name in Runner.allModels()) {
            if (name in meshes) continue
            try {
                assets.open("runner/$name.glb").use { meshes[name] = GlbParser.parse(it.readBytes()) }
            } catch (_: java.io.IOException) {
                // modèle absent : le décor s'en passe
            } catch (_: IllegalArgumentException) {
                // fichier illisible : idem
            }
        }
        running = frames(assets, "run", 12)
        hurt = frames(assets, "hurt", 10)
        die = frames(assets, "die", 10)
        isReady = true
    }

    private fun frames(assets: AssetManager, set: String, count: Int): List<ImageBitmap> {
        val out = ArrayList<ImageBitmap>()
        for (i in 0 until count) {
            try {
                val bitmap = assets.open("runner/assassin_${set}_${if (i < 10) "0$i" else "$i"}.png").use { BitmapFactory.decodeStream(it) }
                if (bitmap != null) out.add(bitmap.asImageBitmap())
            } catch (_: java.io.IOException) {
                // image absente
            }
        }
        return out
    }
}

/**
 * La Course en solo, dans le style de Temple Run : un coureur sur une voie de chemin de fer, qui court tout droit sur trois
 * voies. On glisse le doigt à gauche ou à droite pour changer de voie, vers le haut (ou on touche l'écran) pour sauter,
 * vers le bas pour se baisser ; à la manette, les directions font de même. On ramasse des pièces et des bonus. La partie
 * s'arrête au premier choc (sans vie en réserve ni bouclier) et le meilleur score est gardé. Elle se met en pause quand
 * l'appli passe en arrière-plan. [players] : le joueur dont le nom s'affiche au départ.
 */
@Composable
internal fun RunnerScreen(players: List<String>) {
    val name = players.firstOrNull().orEmpty().ifEmpty { "1" }
    val context = LocalContext.current
    var game by remember { mutableStateOf(RunnerGame()) }
    var phase by remember { mutableIntStateOf(RUNNER_PHASE_READY) }
    var paused by remember { mutableStateOf(false) }
    // Vrai pendant que le réglage de la manette est affiché à la place de l'écran d'accueil ou de fin.
    var showPad by remember { mutableStateOf(false) }
    // Vrai pendant que le menu des améliorations est affiché.
    var showShop by remember { mutableStateOf(false) }
    // Change à chaque achat : le menu des améliorations se refait.
    var shopRevision by remember { mutableIntStateOf(0) }
    val record = rememberSoloRecord(RUNNER_ID)
    // Les modèles et les images se chargent une fois, hors du fil principal.
    val ready by produceState(initialValue = RunnerAssets.isReady, context) {
        if (!RunnerAssets.isReady) withContext(Dispatchers.IO) { RunnerAssets.load(context.applicationContext.assets) }
        value = true
    }

    // La partie se met en pause quand l'appli passe en arrière-plan (et donc aussi quand l'écran tourne).
    val lifecycleOwner = remember(context) { findRunnerLifecycleOwner(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && phase == RUNNER_PHASE_PLAYING) paused = true
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    // Le bouton retour met la partie en pause plutôt que de la quitter par mégarde.
    BackHandler(enabled = phase == RUNNER_PHASE_PLAYING && !paused && !showShop) { paused = true }
    // Dans le menu des améliorations, retour revient à la pause.
    BackHandler(enabled = showShop) { showShop = false }

    fun startGame() {
        record.clearNew()
        game = RunnerGame()
        paused = false
        phase = RUNNER_PHASE_PLAYING
    }

    // La manette : haut = saut, bas = glissade, gauche et droite = voie ; Start met en pause, reprend ou lance la partie.
    GamepadListener { input ->
        val action = GamepadBindings.resolve(context, RUNNER_ID, RUNNER_PAD_ACTIONS, input)
        val playing = phase == RUNNER_PHASE_PLAYING && !paused && !game.isOver
        when (action) {
            "up" -> { if (playing) game.jump(); phase == RUNNER_PHASE_PLAYING }
            "down" -> { if (playing) game.slide(); phase == RUNNER_PHASE_PLAYING }
            "left" -> { if (playing) game.moveLeft(); phase == RUNNER_PHASE_PLAYING }
            "right" -> { if (playing) game.moveRight(); phase == RUNNER_PHASE_PLAYING }
            "pause" -> {
                if (phase == RUNNER_PHASE_PLAYING) { if (!showShop) paused = !paused } else if (ready && !showShop) startGame()
                true
            }
            else -> false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            showPad -> RunnerCard { GamepadSettings(RUNNER_ID, RUNNER_PAD_ACTIONS) { showPad = false } }
            showShop -> RunnerCard {
                // Relire la révision ici refait le menu à chaque achat.
                @Suppress("UNUSED_VARIABLE") val revision = shopRevision
                RunnerShopContent(
                    purse = game.purse,
                    levels = game.levels,
                    onBuy = { upgrade -> if (game.buy(upgrade)) shopRevision++ },
                    onBack = { showShop = false }
                )
            }
            !ready -> RunnerCard {
                Text(
                    text = stringResource(R.string.runner_loading),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
            phase == RUNNER_PHASE_PLAYING -> RunnerPlayfield(
                game = game,
                paused = paused,
                onPausedChange = { paused = it },
                onShop = { showShop = true },
                onOver = {
                    record.submit(game.score)
                    phase = RUNNER_PHASE_OVER
                }
            )
            phase == RUNNER_PHASE_OVER -> RunnerCard {
                Text(
                    text = stringResource(R.string.runner_over_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.runner_score, game.score),
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
                    text = stringResource(R.string.runner_stats, game.coins, game.dist.toInt()),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                SoloRecordLine(record)
                Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.runner_play_again))
                }
                OutlinedButton(onClick = { showPad = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.gamepad_title))
                }
            }
            else -> RunnerCard {
                Text(
                    text = stringResource(R.string.runner_ready_title, name),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.runner_ready_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                SoloRecordLine(record)
                Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.runner_go))
                }
                OutlinedButton(onClick = { showPad = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.gamepad_title))
                }
            }
        }
        RecordSparkles(record)
    }
}

/** La carte des écrans d'accueil et de fin : le titre du jeu, ses règles, puis [content]. */
@Composable
private fun RunnerCard(content: @Composable ColumnScope.() -> Unit) {
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
                        text = stringResource(R.string.game_runner),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    RulesButton(
                        gameId = RUNNER_ID,
                        gameName = stringResource(R.string.game_runner),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                content()
            }
        }
    }
}

/**
 * La partie en cours : l'en-tête (score, pièces, vies, pause) et la voie en 3D avec le coureur dessiné devant. La partie avance
 * à chaque image tant qu'elle n'est pas en [paused] ; quand le coureur s'est écrasé, l'animation de chute se joue puis [onOver]
 * est appelé une fois.
 */
@Composable
private fun RunnerPlayfield(
    game: RunnerGame,
    paused: Boolean,
    onPausedChange: (Boolean) -> Unit,
    onShop: () -> Unit,
    onOver: () -> Unit
) {
    KeepScreenOn()
    // Change à chaque image : le dessin le lit et se refait, sans recomposer tout l'écran.
    var revision by remember(game) { mutableIntStateOf(0) }
    // Les chiffres de l'en-tête ne recomposent l'écran que lorsqu'ils changent.
    var score by remember(game) { mutableIntStateOf(0) }
    var coins by remember(game) { mutableIntStateOf(0) }
    var meters by remember(game) { mutableIntStateOf(0) }
    var lives by remember(game) { mutableIntStateOf(game.lives) }
    var purse by remember(game) { mutableIntStateOf(game.purse) }
    var shield by remember(game) { mutableStateOf(false) }
    var star by remember(game) { mutableStateOf(false) }
    var magnet by remember(game) { mutableStateOf(false) }
    var boost by remember(game) { mutableStateOf(false) }
    // Temps écoulé depuis le choc fatal, en millisecondes (anime la chute).
    var deathMs by remember(game) { mutableFloatStateOf(0f) }
    var renderScale by remember(game) { mutableFloatStateOf(SCALE_START) }
    val finished by rememberUpdatedState(onOver)
    val stage = remember(game) { RunnerStage(RunnerAssets::mesh) }

    fun sync() {
        revision++
        score = game.score
        coins = game.coins
        meters = game.dist.toInt()
        lives = game.lives
        purse = game.purse
        shield = game.shield
        star = game.starLeft > 0f
        magnet = game.magnetLeft > 0f
        boost = game.boostLeft > 0f
    }

    LaunchedEffect(game, paused) {
        if (paused) return@LaunchedEffect
        var last = withFrameNanos { it }
        var slow = 0
        var fast = 0
        val livesAtStart = game.lives
        while (!game.isOver) {
            val now = withFrameNanos { it }
            val elapsed = ((now - last) / 1_000_000L).coerceIn(0L, MAX_FRAME_MS)
            last = now
            game.tick(elapsed)
            sync()
            // Une vie perdue (sans que ce soit la dernière) met la partie en pause : on reprend, ou on dépense ses pièces.
            if (game.lives < livesAtStart && !game.isOver) {
                onPausedChange(true)
                return@LaunchedEffect
            }
            // Résolution adaptative : le rendu 3D est calculé par le processeur, on baisse la résolution si l'image tarde.
            if (elapsed > 40L) { slow++; fast = 0 } else if (elapsed < 22L) { fast++; slow = 0 } else { slow = 0; fast = 0 }
            if (slow >= 15 && renderScale > SCALE_MIN) {
                renderScale = max(SCALE_MIN, renderScale - 0.04f)
                slow = 0
            } else if (fast >= 300 && renderScale < SCALE_MAX) {
                renderScale = min(SCALE_MAX, renderScale + 0.04f)
                fast = 0
            }
        }
        // Le coureur s'est écrasé : il tombe, puis l'écran de fin s'affiche.
        val fallStart = withFrameNanos { it }
        while (deathMs < DEATH_MS) {
            val now = withFrameNanos { it }
            deathMs = (now - fallStart) / 1_000_000f
            revision++
        }
        delay(150)
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
                        text = stringResource(R.string.runner_score, score),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.runner_stats, coins, meters),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val bonuses = listOfNotNull(
                        if (shield) stringResource(R.string.runner_bonus_shield) else null,
                        if (star) stringResource(R.string.runner_bonus_star) else null,
                        if (magnet) stringResource(R.string.runner_bonus_magnet) else null,
                        if (boost) stringResource(R.string.runner_bonus_boost) else null
                    )
                    if (bonuses.isNotEmpty()) {
                        Text(
                            text = bonuses.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.runner_lives, lives),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(onClick = { onPausedChange(true) }, enabled = !paused) {
                    Text(stringResource(R.string.runner_pause))
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                // Un tap fait sauter.
                .pointerInput(game) {
                    detectTapGestures(onTap = { if (!paused && !game.isOver) game.jump() })
                }
                // Glisser le doigt : à gauche ou à droite, on change de voie ; vers le haut, on saute ; vers le bas, on se baisse.
                .pointerInput(game, swipePx) {
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
                                if (!paused && !game.isOver) {
                                    if (abs(dx) > abs(dy)) {
                                        if (dx > 0f) game.moveRight() else game.moveLeft()
                                    } else {
                                        if (dy > 0f) game.slide() else game.jump()
                                    }
                                }
                                dx = 0f
                                dy = 0f
                            }
                        }
                    )
                }
        ) {
            // Le ciel et la plaine, derrière la 3D (qui a un fond transparent).
            Canvas(modifier = Modifier.fillMaxSize()) {
                val horizon = RunnerView.horizon(RunnerView.camera(0f)) * size.height
                drawRect(
                    brush = Brush.verticalGradient(listOf(SkyTop, SkyHorizon), startY = 0f, endY = horizon),
                    size = Size(size.width, horizon)
                )
                drawRect(GroundColor, topLeft = Offset(0f, horizon), size = Size(size.width, size.height - horizon))
            }
            Scene3DView(
                scene = stage.scene,
                modifier = Modifier.fillMaxSize(),
                renderScale = renderScale,
                update = {
                    // Lire la révision ici redessine la vue à chaque image.
                    @Suppress("UNUSED_VARIABLE") val frame = revision
                    stage.update(game)
                }
            )
            // Le coureur (en 2D) posé sur la voie 3D, à l'endroit où ses pieds tombent à l'écran.
            Canvas(modifier = Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE") val frame = revision
                drawRunnerSprite(game, stage.scene.camera, deathMs)
            }
            if (paused) {
                Column(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.runner_paused_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp)
                    )
                    Text(
                        text = stringResource(R.string.runner_lives, lives),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(4.dp)
                    )
                    Text(
                        text = stringResource(R.string.runner_wallet, purse),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(4.dp)
                    )
                    Button(onClick = { onPausedChange(false) }) {
                        Text(stringResource(R.string.runner_resume))
                    }
                    OutlinedButton(onClick = onShop) {
                        Text(stringResource(R.string.runner_shop))
                    }
                }
            }
        }
    }
}

/** Le menu des améliorations : pour chaque bonus, son niveau, ce qu'il apporte, et le bouton pour acheter le niveau suivant. */
@Composable
private fun RunnerShopContent(
    purse: Int,
    levels: RunnerLevels,
    onBuy: (RunnerUpgrade) -> Unit,
    onBack: () -> Unit
) {
    Text(
        text = stringResource(R.string.runner_shop),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() }
    )
    Text(
        text = stringResource(R.string.runner_wallet, purse),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = highlightContent(),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(highlightContainer())
            .padding(10.dp)
    )
    for (upgrade in RunnerUpgrade.entries) {
        val level = levels.of(upgrade)
        val cost = RunnerShop.cost(level)
        val seconds = RunnerShop.seconds(upgrade, level)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(
                    when (upgrade) {
                        RunnerUpgrade.MAGNET -> R.string.runner_bonus_magnet
                        RunnerUpgrade.BOOST -> R.string.runner_bonus_boost
                        RunnerUpgrade.STAR -> R.string.runner_bonus_star
                    }
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (cost == null) {
                    stringResource(R.string.runner_level_max)
                } else {
                    stringResource(R.string.runner_level, level, RunnerShop.MAX_LEVEL)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = when (upgrade) {
                    RunnerUpgrade.MAGNET -> stringResource(
                        R.string.runner_up_magnet_desc, seconds, RunnerShop.magnetAhead(level).roundToInt()
                    )
                    RunnerUpgrade.BOOST -> stringResource(R.string.runner_up_boost_desc, seconds)
                    RunnerUpgrade.STAR -> stringResource(R.string.runner_up_star_desc, seconds)
                },
                style = MaterialTheme.typography.bodyMedium
            )
            if (upgrade == RunnerUpgrade.MAGNET) {
                Text(
                    text = stringResource(R.string.runner_up_magnet_all, RunnerShop.ALL_LANES_LEVEL),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (cost != null) {
                Text(
                    text = stringResource(R.string.runner_up_next, RunnerShop.seconds(upgrade, level + 1)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = { onBuy(upgrade) }, enabled = cost <= purse, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.runner_buy, cost))
                }
            }
        }
    }
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.runner_back))
    }
}

/** Dessine le coureur, son ombre et ses auras de bonus sur la vue, d'après la caméra [cam] de la scène 3D. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRunnerSprite(game: RunnerGame, cam: Camera3D, deathMs: Float) {
    val ground = RunnerView.project(cam, game.x, 0f, 0f, size.width, size.height) ?: return
    val foot = RunnerView.project(cam, game.x, game.y, 0f, size.width, size.height) ?: return
    val pxPerM = foot[2]

    // L'ombre reste au sol quand le coureur saute.
    val shadowW = 0.9f * pxPerM
    val shadowH = shadowW * 0.28f
    drawOval(
        Color.Black.copy(alpha = 0.28f),
        topLeft = Offset(ground[0] - shadowW / 2f, ground[1] - shadowH / 2f),
        size = Size(shadowW, shadowH)
    )

    val dying = game.isOver
    val hurt = !dying && game.hurtLeft > 0f
    val frames = when {
        dying -> RunnerAssets.die
        hurt -> RunnerAssets.hurt
        else -> RunnerAssets.running
    }
    if (frames.isEmpty()) return
    val last = frames.size - 1
    val index = when {
        dying -> ((deathMs / DEATH_MS) * frames.size).toInt().coerceIn(0, last)
        hurt -> ((1f - game.hurtLeft / 0.6f) * frames.size).toInt().coerceIn(0, last)
        // En l'air, le coureur garde la même foulée.
        game.isAirborne -> min(4, last)
        else -> (game.clock * RUN_FPS).toInt().coerceAtLeast(0) % frames.size
    }
    val image = frames[index]

    val heightPx = SPRITE_HEIGHT_M * pxPerM
    val widthPx = heightPx * image.width / image.height
    // Après un choc, le coureur clignote pendant qu'il est invulnérable.
    val blink = game.invuln > 0f && !dying && ((game.clock * 12f).toInt() % 2 == 0)
    val squash = if (game.isSliding) 0.55f else 1f
    val footX = foot[0]
    val footY = foot[1]
    withTransform({
        scale(1f, squash, pivot = Offset(footX, footY))
    }) {
        drawImage(
            image = image,
            dstOffset = IntOffset((footX - widthPx / 2f).roundToInt(), (footY - heightPx).roundToInt()),
            dstSize = IntSize(widthPx.roundToInt().coerceAtLeast(1), heightPx.roundToInt().coerceAtLeast(1)),
            alpha = if (blink) 0.35f else 1f
        )
    }
    val centre = Offset(footX, footY - heightPx * 0.5f * squash)
    if (game.shield) {
        drawCircle(Color(0xFF55B8FF).copy(alpha = 0.25f), radius = heightPx * 0.62f, center = centre)
        drawCircle(Color(0xFF55B8FF), radius = heightPx * 0.62f, center = centre, style = Stroke(width = 3f))
    }
    if (game.starLeft > 0f) {
        drawCircle(Color(0xFFFFD84A), radius = heightPx * 0.7f, center = centre, style = Stroke(width = 3f))
    }
    if (game.magnetLeft > 0f) {
        drawCircle(Color(0xFF9C6BFF), radius = heightPx * 0.78f, center = centre, style = Stroke(width = 2f))
    }
    if (game.boostLeft > 0f) {
        // Super-vitesse : une aura orange et des traînées de vitesse.
        val flame = Color(0xFFFF9A1F)
        drawCircle(flame.copy(alpha = 0.22f), radius = heightPx * 0.85f, center = centre)
        drawCircle(flame, radius = heightPx * 0.85f, center = centre, style = Stroke(width = 3f))
        for (i in -2..2) {
            val lineX = centre.x + i * heightPx * 0.24f
            val from = centre.y + heightPx * 0.2f
            drawLine(flame.copy(alpha = 0.6f), Offset(lineX, from), Offset(lineX, from + heightPx * (0.45f + 0.12f * ((i + 2) % 3))), strokeWidth = 3f)
        }
    }
}

/** L'écran qui héberge [context] (le contexte peut envelopper l'activité), pour surveiller son cycle de vie. */
private fun findRunnerLifecycleOwner(context: Context): LifecycleOwner? {
    var c: Context? = context
    while (c is ContextWrapper) {
        if (c is LifecycleOwner) return c
        c = c.baseContext
    }
    return null
}
