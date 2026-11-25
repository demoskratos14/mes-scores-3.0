package com.aventure.messcores

import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.view.KeyEvent
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

// Les étapes de la séance.
private const val FISHCHASE_PHASE_READY = 0 // on attend que le joueur lance la partie
private const val FISHCHASE_PHASE_PLAYING = 1 // l'homme court derrière la femme
private const val FISHCHASE_PHASE_OVER = 2 // l'homme a rattrapé la femme : la partie est finie

/** Les actions de Poursuite aux poissons réglables à la manette : lancer un poisson (A, B, R ou ZR) et pause / démarrer (Start). */
private val FISHCHASE_PAD_ACTIONS = listOf(
    padButtons("fire", R.string.fishchase_fire, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_R2),
    padStart()
)

/** Plus long temps d'une image pris en compte : après un à-coup, rien ne doit sauter loin d'un coup. */
private const val FISHCHASE_MAX_FRAME_MS = 100L

private val FISH_BODY = Color(0xFFFF9800)
private val FISH_BELLY = Color(0xFFFFE0B2)
private val FISH_TAIL = Color(0xFFF57C00)
private val FISH_SPARK_HIT = listOf(Color(0xFFFFF176), Color(0xFFFFB74D), Color(0xFFFFFFFF))
private val FISH_SPARK_MISS = listOf(Color(0xFF80DEEA), Color(0xFFB2EBF2))
private val CROSS_IDLE = Color(0xFFFFF176)
private val CROSS_ON_TARGET = Color(0xFFFF5252)

/** Garde la partie quand l'écran tourne : son état est rangé dans une liste d'entiers (voir [FishChaseGame.save]). */
private val FishChaseGameSaver = listSaver<FishChaseGame, Int>(
    save = { it.save() },
    restore = { FishChaseGame.restore(it) }
)

/**
 * Les images du jeu : le décor (la rue, déjà doublée par son reflet pour tourner sans couture) et les planches des deux
 * personnages (l'homme qui marche, la femme qui court), rangées en grille. Elles sont lues une fois pour toutes.
 */
internal object FishChaseSprites {
    const val MAN_COLS = 8
    const val MAN_FRAMES = 16
    const val MAN_CELL_W = 120
    const val MAN_CELL_H = 256
    const val WOMAN_COLS = 9
    const val WOMAN_FRAMES = 27
    const val WOMAN_CELL_W = 200
    const val WOMAN_CELL_H = 256

    /** Les images où ils se tiennent debout (jambes droites), pour la scène de fin. */
    const val MAN_STAND_FRAME = 12
    const val WOMAN_STAND_FRAME = 15

    private var backdropBitmap: ImageBitmap? = null
    private var manBitmap: ImageBitmap? = null
    private var womanBitmap: ImageBitmap? = null
    private var loaded = false

    @Synchronized
    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        fun read(id: Int): ImageBitmap? = try {
            BitmapFactory.decodeResource(context.resources, id)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
        backdropBitmap = read(R.drawable.fishchase_bg)
        manBitmap = read(R.drawable.fishchase_man)
        womanBitmap = read(R.drawable.fishchase_woman)
    }

    fun background(context: Context): ImageBitmap? {
        load(context)
        return backdropBitmap
    }

    fun man(context: Context): ImageBitmap? {
        load(context)
        return manBitmap
    }

    fun woman(context: Context): ImageBitmap? {
        load(context)
        return womanBitmap
    }
}

/**
 * Poursuite aux poissons en solo : un homme court derrière une femme dans une rue de nuit. Une mire se promène toute seule
 * sur l'écran : au bon moment, on lance un poisson qui repousse l'homme s'il le touche. S'il rattrape la femme, la partie est
 * perdue. Le meilleur score est gardé d'une séance à l'autre. La partie se met en pause quand l'appli passe en arrière-plan,
 * et tout est gardé quand l'écran tourne. [players] : le joueur dont le nom s'affiche au départ.
 */
@Composable
internal fun FishChaseScreen(players: List<String>) {
    val name = players.firstOrNull().orEmpty().ifEmpty { "1" }
    val context = LocalContext.current
    // Le mode TV (voir [TvMode]) : jouer à la manette sur une télé, plein écran, sans commandes tactiles.
    var tvMode by remember { mutableStateOf(TvMode.load(context, FISHCHASE_ID)) }
    if (tvMode) TvModeEffect()
    fun changeTvMode(enabled: Boolean) {
        tvMode = enabled
        TvMode.save(context, FISHCHASE_ID, enabled)
    }
    val game = rememberSaveable(saver = FishChaseGameSaver) { FishChaseGame() }
    var phase by rememberSaveable { mutableIntStateOf(FISHCHASE_PHASE_READY) }
    var paused by rememberSaveable { mutableStateOf(false) }
    // Vrai pendant que le réglage de la manette est affiché à la place de l'écran d'accueil ou de fin.
    var showPad by remember { mutableStateOf(false) }
    val record = rememberSoloRecord(FISHCHASE_ID)

    // La partie se met en pause quand l'appli passe en arrière-plan (et donc aussi quand l'écran tourne).
    val lifecycleOwner = remember(context) { findFishChaseLifecycleOwner(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && phase == FISHCHASE_PHASE_PLAYING) paused = true
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    // Le bouton retour met la partie en pause plutôt que de la quitter par mégarde.
    BackHandler(enabled = phase == FISHCHASE_PHASE_PLAYING && !paused) { paused = true }

    fun startGame() {
        record.clearNew()
        game.reset()
        paused = false
        phase = FISHCHASE_PHASE_PLAYING
    }

    // La manette hors partie : Start lance la partie (en partie, c'est le terrain qui écoute).
    GamepadListener { input ->
        if (phase == FISHCHASE_PHASE_PLAYING || showPad) return@GamepadListener false
        if (GamepadBindings.resolve(context, FISHCHASE_ID, FISHCHASE_PAD_ACTIONS, input) == "pause") {
            startGame()
            true
        } else false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            showPad -> FishChaseCard(tvMode) { GamepadSettings(FISHCHASE_ID, FISHCHASE_PAD_ACTIONS) { showPad = false } }
            else -> when (phase) {
                FISHCHASE_PHASE_PLAYING -> FishChasePlayfield(
                    game = game,
                    tv = tvMode,
                    paused = paused,
                    onPausedChange = { paused = it },
                    onOver = {
                        record.submit(game.score)
                        phase = FISHCHASE_PHASE_OVER
                    }
                )
                FISHCHASE_PHASE_OVER -> FishChaseCard(tvMode) {
                    Text(
                        text = stringResource(R.string.fishchase_over_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.fishchase_score, game.score),
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
                        text = stringResource(R.string.fishchase_result, game.hits, game.level),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    SoloRecordLine(record)
                    if (tvMode) TvStartHint()
                    Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.fishchase_play_again))
                    }
                    TvModeToggle(checked = tvMode, onChange = { changeTvMode(it) })
                    OutlinedButton(onClick = { showPad = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.gamepad_title))
                    }
                }
                else -> FishChaseCard(tvMode) {
                    Text(
                        text = stringResource(R.string.fishchase_ready_title, name),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    if (!tvMode) {
                        Text(
                            text = stringResource(R.string.fishchase_ready_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    SoloRecordLine(record)
                    if (tvMode) TvStartHint()
                    Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.fishchase_go))
                    }
                    TvModeToggle(checked = tvMode, onChange = { changeTvMode(it) })
                    OutlinedButton(onClick = { showPad = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.gamepad_title))
                    }
                }
            }
        }
        RecordSparkles(record)
    }
}

/** Le plateau des cartes de la séance : le titre du jeu, ses règles, puis [content]. En mode [tv], la carte reste étroite, au centre de l'écran large. */
@Composable
private fun FishChaseCard(tv: Boolean, content: @Composable ColumnScope.() -> Unit) {
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
            modifier = if (tv) Modifier.widthIn(max = 560.dp).fillMaxWidth() else Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.game_fishchase),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    RulesButton(
                        gameId = FISHCHASE_ID,
                        gameName = stringResource(R.string.game_fishchase),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                content()
            }
        }
    }
}

/**
 * La partie en cours : l'en-tête (score, niveau, pause), le terrain, puis le grand bouton qui lance un poisson (toucher le
 * terrain le lance aussi). La partie avance à chaque image tant qu'elle n'est pas en [paused] ; quand la femme est
 * rattrapée, [onOver] est appelé une fois.
 */
@Composable
private fun FishChasePlayfield(
    game: FishChaseGame,
    tv: Boolean,
    paused: Boolean,
    onPausedChange: (Boolean) -> Unit,
    onOver: () -> Unit
) {
    KeepScreenOn()
    val context = LocalContext.current
    val backdrop = remember(context) { FishChaseSprites.background(context) }
    val manSheet = remember(context) { FishChaseSprites.man(context) }
    val womanSheet = remember(context) { FishChaseSprites.woman(context) }
    // Change à chaque image ou action : le dessin le lit et se refait, sans recomposer tout l'écran.
    var revision by remember { mutableIntStateOf(0) }
    // Les chiffres de l'en-tête et l'annonce du niveau : ils ne recomposent l'écran que lorsqu'ils changent.
    var score by remember { mutableIntStateOf(game.score) }
    var level by remember { mutableIntStateOf(game.level) }
    var hits by remember { mutableIntStateOf(game.hits) }
    var banner by remember { mutableStateOf(game.bannerMs > 0) }
    var caught by remember { mutableStateOf(game.isOver) }
    val finished by rememberUpdatedState(onOver)

    fun sync() {
        revision++
        score = game.score
        level = game.level
        hits = game.hits
        banner = game.bannerMs > 0
        caught = game.isOver
    }

    // La manette : A, B, R ou ZR lancent un poisson, Start met en pause ou reprend.
    GamepadListener { input ->
        when (GamepadBindings.resolve(context, FISHCHASE_ID, FISHCHASE_PAD_ACTIONS, input)) {
            "fire" -> game.fire()
            "pause" -> onPausedChange(!paused)
            else -> return@GamepadListener false
        }
        true
    }

    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (!game.isOver) {
            val now = withFrameNanos { it }
            val elapsed = ((now - last) / 1_000_000L).coerceIn(0L, FISHCHASE_MAX_FRAME_MS)
            last = now
            game.tick(elapsed)
            sync()
        }
        // La scène de fin : la femme s'arrête et l'homme est derrière elle, un court instant avant l'écran de fin.
        while (game.endingMs < FishChase.ENDING_MS) {
            val now = withFrameNanos { it }
            val elapsed = ((now - last) / 1_000_000L).coerceIn(0L, FISHCHASE_MAX_FRAME_MS)
            last = now
            game.tickEnding(elapsed)
            sync()
        }
        finished()
    }

    val fieldDescription = stringResource(R.string.fishchase_field)

    // La marge de la barre de navigation (boutons ou geste) garde le terrain et le bouton hors de ses touches.
    Column(
        modifier = if (tv) {
            Modifier.fillMaxSize().background(TvBackground)
        } else {
            Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)
        },
        verticalArrangement = Arrangement.spacedBy(if (tv) 0.dp else 8.dp)
    ) {
        // En mode TV : une bande de score fine, le terrain plein écran et pas de commandes tactiles (la manette suffit).
        if (tv) TvHud(
            primary = stringResource(R.string.fishchase_score, score),
            secondary = stringResource(R.string.fishchase_stats, level, hits)
        ) else Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.fishchase_score, score),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.fishchase_stats, level, hits),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { onPausedChange(true) }, enabled = !paused) {
                    Text(stringResource(R.string.fishchase_pause))
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val unit = maxOf(minOf(maxWidth / FishChase.WIDTH, maxHeight / FishChase.HEIGHT), 0.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(unit * FishChase.WIDTH, unit * FishChase.HEIGHT)
                    .clip(RoundedCornerShape(8.dp))
                    .semantics { contentDescription = fieldDescription }
                    .pointerInput(paused) {
                        // Toucher le terrain lance un poisson, comme le bouton.
                        detectTapGestures(onTap = { if (!paused) game.fire() })
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // À la capture, tout le terrain tremble un instant, de moins en moins fort.
                    val shake = if (game.isOver) {
                        val left = 1f - (game.endingMs.toFloat() / FishChase.ENDING_MS).coerceIn(0f, 1f)
                        sin(game.endingMs / 22f) * 0.22f * (size.width / FishChase.WIDTH) * left
                    } else 0f
                    translate(left = shake) { drawFishChaseField(game, revision, backdrop, manSheet, womanSheet) }
                }
                if (caught) {
                    Text(
                        text = stringResource(R.string.fishchase_caught),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xCCB71C1C))
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
                if (banner && !paused) {
                    Text(
                        text = stringResource(R.string.fishchase_level_banner, level),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
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
                            text = stringResource(R.string.fishchase_paused_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(8.dp)
                        )
                        if (tv) TvResumeHint()
                        Button(onClick = { onPausedChange(false) }) {
                            Text(stringResource(R.string.fishchase_resume))
                        }
                    }
                }
            }
        }

        // Un grand bouton de tir, comme dans Envahisseurs (voir PadButton) : le poisson part dès que le doigt se pose.
        if (!tv) Row(
            modifier = Modifier.fillMaxWidth().height(84.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PadButton(
                icon = PadIcon.FIRE,
                label = stringResource(R.string.fishchase_fire),
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onHold = { if (it && !paused) game.fire() }
            )
        }
    }
}

/**
 * Dessine une image du terrain, en unités du terrain mises à l'échelle (15 de large) : la rue qui défile, la femme qui
 * court, l'homme qui la poursuit, les poissons en vol, les éclaboussures et la mire. [revision] n'est là que pour que le
 * dessin se refasse quand elle change.
 */
private fun DrawScope.drawFishChaseField(
    game: FishChaseGame,
    @Suppress("UNUSED_PARAMETER") revision: Int,
    backdrop: ImageBitmap?,
    manSheet: ImageBitmap?,
    womanSheet: ImageBitmap?
) {
    val u = size.width / FishChase.WIDTH

    // La rue : une fenêtre du décor, qui glisse vers la gauche et revient au début quand elle arrive au bout.
    if (backdrop != null) {
        drawScrollingBackdrop(backdrop, game.scrollPx, size.width.roundToInt(), size.height.roundToInt())
    } else {
        drawRect(Color(0xFF101A33))
        drawRect(Color(0xFF3A3F55), topLeft = Offset(0f, FishChase.GROUND_Y * u), size = Size(size.width, size.height - FishChase.GROUND_Y * u))
    }

    // La femme court toujours au même endroit, à vingt images par seconde.
    val womanFrame = if (game.isOver) FishChaseSprites.WOMAN_STAND_FRAME else ((game.clockMs * 20L) / 1000L).toInt() % FishChaseSprites.WOMAN_FRAMES
    drawShadow(FishChase.WOMAN_X * u, FishChase.GROUND_Y * u, FishChase.WOMAN_HEIGHT * u * 0.5f, u)
    if (womanSheet != null) {
        drawSheetFrame(
            sheet = womanSheet,
            cols = FishChaseSprites.WOMAN_COLS,
            cellW = FishChaseSprites.WOMAN_CELL_W,
            cellH = FishChaseSprites.WOMAN_CELL_H,
            frame = womanFrame,
            footX = FishChase.WOMAN_X * u,
            footY = FishChase.GROUND_Y * u,
            height = FishChase.WOMAN_HEIGHT * u,
            tint = null,
            tiltDegrees = 0f
        )
    }

    // L'homme garde son animation de marche (quinze images par seconde) ; étourdi, il devient rouge et se penche en arrière.
    val manFrame = if (game.isOver) FishChaseSprites.MAN_STAND_FRAME else ((game.clockMs * 15L) / 1000L).toInt() % FishChaseSprites.MAN_FRAMES
    drawShadow(game.manX * u, FishChase.GROUND_Y * u, FishChase.MAN_HEIGHT * u * 0.4f, u)
    if (manSheet != null) {
        val stunned = game.stunMs > 0
        val flash = if (stunned && (game.stunMs / 80) % 2 == 0) Color(0xAAFF1744) else null
        drawSheetFrame(
            sheet = manSheet,
            cols = FishChaseSprites.MAN_COLS,
            cellW = FishChaseSprites.MAN_CELL_W,
            cellH = FishChaseSprites.MAN_CELL_H,
            frame = manFrame,
            footX = game.manX * u,
            footY = FishChase.GROUND_Y * u,
            height = FishChase.MAN_HEIGHT * u,
            tint = flash,
            tiltDegrees = if (stunned) -8f else 0f
        )
    } else {
        drawRect(Color(0xFF1E88E5), topLeft = Offset((game.manX - 0.7f) * u, FishChase.MAN_TOP * u), size = Size(1.4f * u, FishChase.MAN_HEIGHT * u))
    }

    if (game.isOver) {
        // Un point d'exclamation au-dessus de la femme, qui s'est arrêtée.
        val markX = FishChase.WOMAN_X * u
        val markTop = (FishChase.GROUND_Y - FishChase.WOMAN_HEIGHT - 0.95f) * u
        drawRoundRect(Color(0xFFFF5252), Offset(markX - 0.12f * u, markTop), Size(0.24f * u, 0.6f * u), CornerRadius(0.12f * u))
        drawCircle(Color(0xFFFF5252), 0.14f * u, Offset(markX, markTop + 0.82f * u))
        // Un voile rouge qui clignote.
        drawRect(Color(0xFFFF1744).copy(alpha = 0.28f * abs(sin(game.endingMs / 110f))))
    }

    for (spark in game.sparks) {
        val fade = (spark.lifeMs.toFloat() / spark.totalMs).coerceIn(0f, 1f)
        val colors = if (spark.kind == 0) FISH_SPARK_HIT else FISH_SPARK_MISS
        val color = colors[(spark.totalMs + spark.lifeMs / 40) % colors.size]
        drawCircle(color.copy(alpha = fade), 0.09f * u * (0.5f + fade), Offset(spark.x * u, spark.y * u))
    }

    for (f in game.fish) {
        // Le poisson grossit un peu en partant du premier plan (le bas de l'écran) vers le fond.
        val progress = (f.ageMs.toFloat() / FishChase.FLIGHT_MS).coerceIn(0f, 1f)
        val length = (1.5f - 0.4f * progress) * u
        drawFish(f.x * u, f.y * u, f.angle, length)
    }

    drawCrosshair(game.crossX * u, game.crossY * u, u, game.crossOnMan, game.cooldownMs > 0)
}

/** Dessine la fenêtre du décor de largeur [dstW] qui commence à [scrollPx] pixels de l'image (qui reboucle au bout). */
private fun DrawScope.drawScrollingBackdrop(image: ImageBitmap, scrollPx: Float, dstW: Int, dstH: Int) {
    val srcH = image.height
    val srcW = (srcH * (FishChase.WIDTH / FishChase.HEIGHT)).roundToInt().coerceAtMost(image.width)
    val start = scrollPx.toInt().mod(image.width)
    val firstW = minOf(srcW, image.width - start)
    val firstDstW = (dstW.toFloat() * firstW / srcW).roundToInt()
    drawImage(
        image = image,
        srcOffset = IntOffset(start, 0),
        srcSize = IntSize(firstW, srcH),
        dstOffset = IntOffset(0, 0),
        dstSize = IntSize(firstDstW, dstH),
        filterQuality = FilterQuality.Medium
    )
    if (firstW < srcW) {
        drawImage(
            image = image,
            srcOffset = IntOffset(0, 0),
            srcSize = IntSize(srcW - firstW, srcH),
            dstOffset = IntOffset(firstDstW, 0),
            dstSize = IntSize(dstW - firstDstW, dstH),
            filterQuality = FilterQuality.Medium
        )
    }
}

/** L'ombre d'un personnage sur le trottoir. */
private fun DrawScope.drawShadow(cx: Float, groundY: Float, halfWidth: Float, u: Float) {
    drawOval(
        Color.Black.copy(alpha = 0.3f),
        topLeft = Offset(cx - halfWidth, groundY - 0.12f * u),
        size = Size(halfWidth * 2f, 0.3f * u)
    )
}

/**
 * Une case d'une planche en grille (voir [FishChaseSprites]), centrée sur [footX] et posée sur [footY] ; [height] est sa
 * hauteur à l'écran. [tint] la teinte (par exemple en rouge quand l'homme est touché) et [tiltDegrees] l'incline autour des pieds.
 */
private fun DrawScope.drawSheetFrame(
    sheet: ImageBitmap,
    cols: Int,
    cellW: Int,
    cellH: Int,
    frame: Int,
    footX: Float,
    footY: Float,
    height: Float,
    tint: Color?,
    tiltDegrees: Float
) {
    val width = height * cellW / cellH
    rotate(tiltDegrees, Offset(footX, footY)) {
        drawImage(
            image = sheet,
            srcOffset = IntOffset((frame % cols) * cellW, (frame / cols) * cellH),
            srcSize = IntSize(cellW, cellH),
            dstOffset = IntOffset((footX - width / 2f).roundToInt(), (footY - height).roundToInt()),
            dstSize = IntSize(width.roundToInt(), height.roundToInt()),
            colorFilter = tint?.let { ColorFilter.tint(it, BlendMode.SrcAtop) },
            filterQuality = FilterQuality.Medium
        )
    }
}

/** Un poisson de longueur [length], centré en ([cx], [cy]), la tête dans le sens de [degrees] (0 = vers la droite). */
private fun DrawScope.drawFish(cx: Float, cy: Float, degrees: Float, length: Float) {
    val h = length * 0.42f
    rotate(degrees, Offset(cx, cy)) {
        val tail = Path().apply {
            moveTo(cx - length * 0.38f, cy)
            lineTo(cx - length * 0.62f, cy - h * 0.6f)
            lineTo(cx - length * 0.62f, cy + h * 0.6f)
            close()
        }
        drawPath(tail, FISH_TAIL)
        drawOval(FISH_BODY, topLeft = Offset(cx - length * 0.45f, cy - h / 2f), size = Size(length * 0.9f, h))
        drawOval(FISH_BELLY, topLeft = Offset(cx - length * 0.32f, cy + h * 0.02f), size = Size(length * 0.6f, h * 0.32f))
        val fin = Path().apply {
            moveTo(cx - length * 0.05f, cy - h * 0.45f)
            lineTo(cx - length * 0.2f, cy - h * 0.85f)
            lineTo(cx + length * 0.1f, cy - h * 0.42f)
            close()
        }
        drawPath(fin, FISH_TAIL)
        drawCircle(Color.White, h * 0.17f, Offset(cx + length * 0.27f, cy - h * 0.1f))
        drawCircle(Color.Black, h * 0.08f, Offset(cx + length * 0.29f, cy - h * 0.1f))
    }
}

/** La mire : un anneau, quatre repères et un point ; elle devient rouge quand elle est sur l'homme et s'éteint pendant la pause entre deux lancers. */
private fun DrawScope.drawCrosshair(cx: Float, cy: Float, u: Float, onTarget: Boolean, reloading: Boolean) {
    val color = (if (onTarget) CROSS_ON_TARGET else CROSS_IDLE).copy(alpha = if (reloading) 0.45f else 1f)
    val radius = 0.62f * u
    val stroke = maxOf(2f, 0.07f * u)
    drawCircle(Color.Black.copy(alpha = 0.35f), radius + stroke, Offset(cx, cy), style = Stroke(width = stroke * 2.2f))
    drawCircle(color, radius, Offset(cx, cy), style = Stroke(width = stroke))
    val tick = radius * 0.55f
    drawLine(color, Offset(cx - radius - tick, cy), Offset(cx - radius * 0.45f, cy), strokeWidth = stroke)
    drawLine(color, Offset(cx + radius * 0.45f, cy), Offset(cx + radius + tick, cy), strokeWidth = stroke)
    drawLine(color, Offset(cx, cy - radius - tick), Offset(cx, cy - radius * 0.45f), strokeWidth = stroke)
    drawLine(color, Offset(cx, cy + radius * 0.45f), Offset(cx, cy + radius + tick), strokeWidth = stroke)
    drawCircle(color, stroke * 0.9f, Offset(cx, cy))
}

private fun findFishChaseLifecycleOwner(context: Context): LifecycleOwner? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return null
}
