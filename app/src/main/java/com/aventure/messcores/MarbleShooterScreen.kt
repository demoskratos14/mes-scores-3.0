package com.aventure.messcores

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Paint
import android.graphics.Typeface
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.time.LocalDate
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Les étapes de la séance.
private const val MARBLE_PHASE_READY = 0 // on attend que le joueur lance la partie
private const val MARBLE_PHASE_PLAYING = 1 // la partie est en cours
private const val MARBLE_PHASE_OVER = 2 // la chaîne a atteint le trou : la partie est finie

/** Plus long temps d'une image pris en compte : après un à-coup, la chaîne ne doit pas sauter d'un bloc. */
private const val MARBLE_MAX_FRAME_MS = 50L

/** Temps pendant lequel l'annonce « niveau gagné » reste affichée. */
private const val MARBLE_CELEBRATION_MS = 1500L

/** Temps pendant lequel l'annonce d'une réaction en chaîne reste affichée. */
private const val MARBLE_COMBO_MS = 1200L

/** Temps laissé à voir le terrain final avant la carte de fin. */
private const val MARBLE_GAME_OVER_DELAY_MS = 900L

/** Tourner la visée avec les flèches : radians par pas, délai avant la répétition et temps entre deux pas, en millisecondes. */
private const val MARBLE_AIM_STEP = 0.04f
private const val MARBLE_AIM_REPEAT_DELAY_MS = 250L
private const val MARBLE_AIM_REPEAT_MS = 25L

private const val MARBLE_PI = 3.1415927f
private const val MARBLE_TWO_PI = 6.2831855f

/** La visée de départ : tout droit vers le haut de l'écran. */
private const val MARBLE_START_AIM = -MARBLE_PI / 2f

/** La bille suivante, posée à côté du lanceur : son décalage et son rayon (en cases), et la tolérance du toucher. */
private const val MARBLE_NEXT_DX = -1.0f
private const val MARBLE_NEXT_DY = 0.8f
private const val MARBLE_NEXT_RADIUS = 0.24f
private const val MARBLE_NEXT_TOUCH = 0.8f

// Les animations d'une bille qui éclate : un anneau qui s'élargit et des étincelles.
private const val MARBLE_RING_LIFE_MS = 280f
private const val MARBLE_SPARK_LIFE_MS = 450f
private const val MARBLE_SPARKS_PER_MARBLE = 4
private const val MARBLE_MAX_SPARKS = 80

private class MarbleParticle(
    val ring: Boolean,
    var x: Float,
    var y: Float,
    val vx: Float,
    var vy: Float,
    val color: Int,
    val life: Float
) {
    var age = 0f
}

// Le guide de visée : sans ou avec. Le choix est gardé d'une séance à l'autre, comme le mode.
private const val MARBLE_GUIDE_OFF = 0
private const val MARBLE_GUIDE_ON = 1

private const val MARBLE_PREFS_NAME = "mes_scores_marble"
private const val MARBLE_PREF_MODE = "mode"
private const val MARBLE_PREF_GUIDE = "guide"

private const val MARBLE_TILE_PX = 64
private const val MARBLE_TILE_COLS = 8
private const val MARBLE_PROP_PX = 96
private const val MARBLE_PROP_COLS = 8

private val MarbleRoadColor = Color(0xFFA5C96B)
private val MarbleSwampColor = Color(0xFF3E5C3A)
private val MarbleGuideColor = Color(0xFFFFFFFF)
private val MarbleBarrelColor = Color(0xFF90A4AE)
private val MarbleHoleColor = Color(0xFF1A1A1A)
private val MarbleDangerColor = Color(0xFFFF5252)

/** Les six couleurs de billes, dans l'ordre de leur numéro (les mêmes que les bulles du Bubble Shooter). */
private val MarbleColors = listOf(
    Color(0xFFE53935), // rouge
    Color(0xFF1E88E5), // bleu
    Color(0xFF43A047), // vert
    Color(0xFFFDD835), // jaune
    Color(0xFF8E24AA), // violet
    Color(0xFFFB8C00) // orange
)

private fun marbleBase(index: Int): Color = MarbleColors[index.coerceAtLeast(0) % MarbleColors.size]

/** Le nom d'une bille de numéro [kind] pour les lecteurs d'écran. */
@StringRes
private fun marbleKindName(kind: Int): Int = when (kind) {
    0 -> R.string.bubble_kind_0
    1 -> R.string.bubble_kind_1
    2 -> R.string.bubble_kind_2
    3 -> R.string.bubble_kind_3
    4 -> R.string.bubble_kind_4
    else -> R.string.bubble_kind_5
}

/**
 * Les actions du Marble Shooter réglables à la manette : tourner la visée à gauche ou à droite (croix ou stick, tenir répète
 * le pas), tirer (A, R ou ZR), échanger les deux billes (B, X ou L) et pause / démarrer (Start).
 */
private val MARBLE_PAD_ACTIONS = listOf(
    padDirection("left", R.string.bubble_left, horizontal = true, sign = -1),
    padDirection("right", R.string.bubble_right, horizontal = true, sign = 1),
    padButtons("fire", R.string.bubble_fire, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_R2),
    padButtons("swap", R.string.bubble_swap, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_L1),
    padStart()
)

/** Le crayon des lettres du mode daltonien : A, B, C… sur chaque couleur. */
private val MARBLE_CODE_PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.WHITE
    textAlign = Paint.Align.CENTER
    typeface = Typeface.DEFAULT_BOLD
    setShadowLayer(3f, 0f, 0f, android.graphics.Color.BLACK)
}

/** Garde la partie quand l'écran tourne : son état est rangé dans une liste d'entiers (voir [MarbleGame.save]). */
private val MarbleGameSaver = listSaver<MarbleGame, Int>(
    save = { it.save() },
    restore = { MarbleGame.restore(it) }
)

/** Ramène un angle entre -π (exclu) et π. */
private fun wrapAngle(angle: Float): Float {
    var a = angle
    while (a > MARBLE_PI) a -= MARBLE_TWO_PI
    while (a <= -MARBLE_PI) a += MARBLE_TWO_PI
    return a
}

/**
 * Marble Shooter en solo : une chaîne de billes de couleur roule sur le chemin d'une carte du tower defense, vers un trou. Le
 * lanceur, au centre, tire des billes dans toutes les directions : on glisse le doigt sur le plateau pour viser et on le relève
 * pour tirer (ou avec les flèches et le bouton). Trois billes de même couleur (ou plus) éclatent ; quand le trou se referme et
 * que deux groupes de même couleur se rejoignent, ils éclatent aussi (réaction en chaîne, points multipliés). Si la chaîne
 * atteint le trou, la partie est perdue ; vider la chaîne donne un bonus et le niveau suivant. Le meilleur score est gardé
 * d'une séance à l'autre. Elle se met en pause quand l'appli passe en arrière-plan. Avant la partie, on choisit le mode
 * (classique ou défi du jour : un record chacun) et le guide de visée. Tout est gardé quand l'écran tourne, la bille en vol
 * comprise. [players] : le joueur dont le nom s'affiche au départ.
 */
@Composable
internal fun MarbleShooterScreen(players: List<String>) {
    val name = players.firstOrNull().orEmpty().ifEmpty { "1" }
    val context = LocalContext.current
    // Le mode TV (voir [TvMode]) : jouer à la manette sur une télé, plein écran, sans commandes tactiles.
    var tvMode by remember { mutableStateOf(TvMode.load(context, MARBLE_ID)) }
    if (tvMode) TvModeEffect()
    fun changeTvMode(enabled: Boolean) {
        tvMode = enabled
        TvMode.save(context, MARBLE_ID, enabled)
    }
    val game = rememberSaveable(saver = MarbleGameSaver) { MarbleGame() }
    var phase by rememberSaveable { mutableIntStateOf(MARBLE_PHASE_READY) }
    var paused by rememberSaveable { mutableStateOf(false) }
    // Vrai pendant que le réglage de la manette est affiché à la place de l'écran d'accueil ou de fin.
    var showPad by remember { mutableStateOf(false) }
    // Nombre de niveaux gagnés : à chaque hausse, la pluie de paillettes tombe.
    var cleared by rememberSaveable { mutableIntStateOf(game.level - 1) }
    // Le mode et le guide : gardés d'une séance à l'autre dans les préférences, et quand l'écran tourne.
    val prefs = remember(context) { context.getSharedPreferences(MARBLE_PREFS_NAME, Context.MODE_PRIVATE) }
    var modeIndex by rememberSaveable { mutableIntStateOf(prefs.getInt(MARBLE_PREF_MODE, 0)) }
    var guide by rememberSaveable {
        mutableIntStateOf(prefs.getInt(MARBLE_PREF_GUIDE, MARBLE_GUIDE_ON).coerceIn(MARBLE_GUIDE_OFF, MARBLE_GUIDE_ON))
    }
    val mode = MarbleMode.fromIndex(modeIndex)
    // Le défi du jour est le même pour tout le monde, un jour donné : sa graine vient de la date.
    val dailySeed = remember {
        val today = LocalDate.now()
        Marble.dailySeed(today.year, today.monthValue, today.dayOfMonth)
    }
    // Un record par mode, et un par jour pour le défi du jour.
    val record = rememberSoloRecord(
        when (mode) {
            MarbleMode.CLASSIC -> MARBLE_ID
            MarbleMode.DAILY -> MARBLE_ID + "_daily_" + dailySeed
        }
    )

    // La partie se met en pause quand l'appli passe en arrière-plan (et donc aussi quand l'écran tourne).
    val lifecycleOwner = remember(context) { findMarbleLifecycleOwner(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && phase == MARBLE_PHASE_PLAYING) paused = true
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    // Le bouton retour met la partie en pause plutôt que de la quitter par mégarde.
    BackHandler(enabled = phase == MARBLE_PHASE_PLAYING && !paused) { paused = true }

    fun startGame() {
        record.clearNew()
        game.start(mode, dailySeed)
        cleared = 0
        paused = false
        phase = MARBLE_PHASE_PLAYING
    }

    // La manette hors partie : Start lance la partie (en partie, c'est le terrain qui écoute).
    GamepadListener { input ->
        if (phase == MARBLE_PHASE_PLAYING || showPad) return@GamepadListener false
        if (GamepadBindings.resolve(context, MARBLE_ID, MARBLE_PAD_ACTIONS, input) == "pause") {
            startGame()
            true
        } else false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            showPad -> MarbleCard(tvMode) { GamepadSettings(MARBLE_ID, MARBLE_PAD_ACTIONS) { showPad = false } }
            else -> when (phase) {
                MARBLE_PHASE_PLAYING -> MarblePlayfield(
                    game = game,
                    tv = tvMode,
                    guide = guide,
                    paused = paused,
                    onPausedChange = { paused = it },
                    onCleared = { cleared = it },
                    onOver = {
                        record.submit(game.score)
                        phase = MARBLE_PHASE_OVER
                    }
                )
                MARBLE_PHASE_OVER -> MarbleCard(tvMode) {
                    Text(
                        text = stringResource(R.string.bubble_over_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.bubble_score, game.score),
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
                        text = stringResource(R.string.bubble_level_reached, game.level),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    SoloRecordLine(record)
                    if (tvMode) TvStartHint()
                    Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.bubble_play_again))
                    }
                    OutlinedButton(onClick = { phase = MARBLE_PHASE_READY }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.bubble_change_mode))
                    }
                    TvModeToggle(checked = tvMode, onChange = { changeTvMode(it) })
                    OutlinedButton(onClick = { showPad = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.gamepad_title))
                    }
                }
                else -> MarbleCard(tvMode) {
                    Text(
                        text = stringResource(R.string.bubble_ready_title, name),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    if (!tvMode) {
                        Text(
                            text = stringResource(R.string.marble_ready_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    MarbleSetup(
                        modeIndex = modeIndex,
                        guide = guide,
                        onModeChange = {
                            modeIndex = it
                            prefs.edit { putInt(MARBLE_PREF_MODE, it) }
                        },
                        onGuideChange = {
                            guide = it
                            prefs.edit { putInt(MARBLE_PREF_GUIDE, it) }
                        }
                    )
                    SoloRecordLine(record)
                    if (tvMode) TvStartHint()
                    Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.bubble_go))
                    }
                    TvModeToggle(checked = tvMode, onChange = { changeTvMode(it) })
                    OutlinedButton(onClick = { showPad = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.gamepad_title))
                    }
                }
            }
        }
        RecordSparkles(record)
        SparkleRain(cleared)
    }
}

/**
 * Les choix d'avant la partie : le mode (classique ou défi du jour) avec une phrase qui l'explique, et le guide de visée (sans
 * ou avec). Le choix en cours est le bouton plein, l'autre est cerné.
 */
@Composable
private fun MarbleSetup(
    modeIndex: Int,
    guide: Int,
    onModeChange: (Int) -> Unit,
    onGuideChange: (Int) -> Unit
) {
    val modes = listOf(
        stringResource(R.string.bubble_mode_classic),
        stringResource(R.string.bubble_mode_daily)
    )
    val guides = listOf(
        stringResource(R.string.marble_guide_off),
        stringResource(R.string.marble_guide_on)
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        MarbleChoiceRow(labels = modes, selected = modeIndex, onSelect = onModeChange)
        Text(
            text = stringResource(
                when (MarbleMode.fromIndex(modeIndex)) {
                    MarbleMode.CLASSIC -> R.string.marble_mode_classic_hint
                    MarbleMode.DAILY -> R.string.marble_mode_daily_hint
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.bubble_guide_title),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center
        )
        MarbleChoiceRow(labels = guides, selected = guide, onSelect = onGuideChange)
    }
}

/** Une rangée de choix de même largeur : celui de numéro [selected] est un bouton plein, les autres sont cernés. */
@Composable
private fun MarbleChoiceRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { index, label ->
            val modifier = Modifier.weight(1f)
            val padding = PaddingValues(horizontal = 4.dp)
            if (index == selected) {
                Button(onClick = { onSelect(index) }, modifier = modifier, contentPadding = padding) {
                    Text(text = label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                }
            } else {
                OutlinedButton(onClick = { onSelect(index) }, modifier = modifier, contentPadding = padding) {
                    Text(text = label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** La carte des étapes hors partie : le titre du jeu, ses règles, puis [content]. */
@Composable
private fun MarbleCard(tv: Boolean, content: @Composable ColumnScope.() -> Unit) {
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
                        text = stringResource(R.string.game_marble),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    RulesButton(
                        gameId = MARBLE_ID,
                        gameName = stringResource(R.string.game_marble),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                content()
            }
        }
    }
}

/**
 * La partie en cours : l'en-tête (score, niveau, billes restantes, pause), le plateau, puis les commandes. La partie avance à
 * chaque image tant qu'elle n'est pas en [paused] ; quand elle est perdue, [onOver] est appelé une fois.
 */
@Composable
private fun MarblePlayfield(
    game: MarbleGame,
    tv: Boolean,
    guide: Int,
    paused: Boolean,
    onPausedChange: (Boolean) -> Unit,
    onCleared: (Int) -> Unit,
    onOver: () -> Unit
) {
    KeepScreenOn()
    val colorBlind = LocalColorBlindMode.current
    val context = LocalContext.current
    val sprites = remember(context) { TdSprites.get(context) }
    val tileCache = remember { MarbleTileCache() }
    // Change à chaque image ou action : le dessin le lit et se refait, sans recomposer tout l'écran.
    var revision by remember { mutableIntStateOf(0) }
    // Les chiffres de l'en-tête : ils ne recomposent l'écran que lorsqu'ils changent.
    var score by remember { mutableIntStateOf(game.score) }
    var level by remember { mutableIntStateOf(game.level) }
    var remaining by remember { mutableIntStateOf(game.marbleCount + game.supply) }
    // Les deux billes du lanceur : la description du plateau pour les lecteurs d'écran.
    var currentKind by remember { mutableIntStateOf(game.current) }
    var nextKind by remember { mutableIntStateOf(game.next) }
    // Le rang de la dernière réaction en chaîne, tant qu'on l'annonce (0 : rien à annoncer).
    var comboRank by remember { mutableIntStateOf(0) }
    // L'angle de visée (de l'horizontale vers la droite, tourné vers le bas de l'écran) : vers le haut au départ, gardé quand l'écran tourne.
    var aim by rememberSaveable { mutableFloatStateOf(MARBLE_START_AIM) }
    // Vrai pendant que l'annonce du niveau gagné s'affiche.
    var celebrating by remember { mutableStateOf(false) }
    val finished by rememberUpdatedState(onOver)
    val clearedChanged by rememberUpdatedState(onCleared)
    fun sync() {
        revision++
        score = game.score
        level = game.level
        remaining = game.marbleCount + game.supply
        currentKind = game.current
        nextKind = game.next
    }

    // Les animations en cours : ni sauvegardées ni observées, le dessin les relit à chaque image (voir [revision]).
    val particles = remember { mutableListOf<MarbleParticle>() }
    val random = remember { Random.Default }
    val haptic = LocalHapticFeedback.current

    /** Lance l'animation des billes que le dernier éclatement a fait partir. */
    fun spawnEffects(effects: List<MarbleEffect>) {
        var sparks = 0
        for (e in effects) {
            particles.add(MarbleParticle(true, e.x, e.y, 0f, 0f, e.color, MARBLE_RING_LIFE_MS))
            for (i in 0 until MARBLE_SPARKS_PER_MARBLE) {
                if (sparks >= MARBLE_MAX_SPARKS) break
                val angle = random.nextFloat() * MARBLE_TWO_PI
                val speed = 1.5f + random.nextFloat() * 2.5f
                particles.add(MarbleParticle(false, e.x, e.y, cos(angle) * speed, sin(angle) * speed, e.color, MARBLE_SPARK_LIFE_MS))
                sparks++
            }
        }
    }

    /** Fait avancer les animations de [elapsedMs] millisecondes et retire celles qui sont finies. */
    fun stepParticles(elapsedMs: Long) {
        val seconds = elapsedMs / 1000f
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.age += elapsedMs
            if (!p.ring) {
                p.vy += 6f * seconds
                p.x += p.vx * seconds
                p.y += p.vy * seconds
            }
            if (p.age >= p.life) iterator.remove()
        }
    }

    fun fire() {
        if (paused || game.isOver) return
        if (game.shoot(aim)) sync()
    }

    fun swap() {
        if (paused) return
        if (game.swap()) sync()
    }

    fun turnAim(delta: Float) {
        if (paused) return
        aim = wrapAngle(aim + delta)
        revision++
    }

    // Les gestes et les touches gardent ces actions à jour sans être relancés à chaque image.
    val fireAction by rememberUpdatedState<() -> Unit>({ fire() })
    val swapAction by rememberUpdatedState<() -> Unit>({ swap() })
    // Gauche : la visée tourne dans le sens inverse des aiguilles d'une montre (l'angle diminue).
    val aimLeftAction by rememberUpdatedState<() -> Unit>({ turnAim(-MARBLE_AIM_STEP) })
    val aimRightAction by rememberUpdatedState<() -> Unit>({ turnAim(MARBLE_AIM_STEP) })

    // La direction de visée tenue à la manette (-1 gauche, 1 droite, 0 aucune) : elle répète le pas, comme une touche tenue.
    var padAim by remember { mutableIntStateOf(0) }
    LaunchedEffect(padAim) {
        if (padAim == 0) return@LaunchedEffect
        if (padAim < 0) aimLeftAction() else aimRightAction()
        delay(MARBLE_AIM_REPEAT_DELAY_MS)
        while (true) {
            if (padAim < 0) aimLeftAction() else aimRightAction()
            delay(MARBLE_AIM_REPEAT_MS)
        }
    }
    // La manette : la croix vise, A tire, B échange les billes, Start met en pause ou reprend.
    GamepadListener(onRelease = { input ->
        when (GamepadBindings.resolve(context, MARBLE_ID, MARBLE_PAD_ACTIONS, input)) {
            "left", "right" -> padAim = 0
            else -> {}
        }
    }) { input ->
        when (GamepadBindings.resolve(context, MARBLE_ID, MARBLE_PAD_ACTIONS, input)) {
            "left" -> padAim = -1
            "right" -> padAim = 1
            "fire" -> fireAction()
            "swap" -> swapAction()
            "pause" -> onPausedChange(!paused)
            else -> return@GamepadListener false
        }
        true
    }
    LaunchedEffect(celebrating) {
        if (!celebrating) return@LaunchedEffect
        delay(MARBLE_CELEBRATION_MS)
        celebrating = false
    }

    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        var last = withFrameNanos { it }
        var seenLevel = game.level
        var seenShots = game.shotCount
        var seenPops = game.popCount
        var comboLeft = 0L
        while (!game.isOver) {
            val now = withFrameNanos { it }
            val elapsed = ((now - last) / 1_000_000L).coerceIn(0L, MARBLE_MAX_FRAME_MS)
            last = now
            game.tick(elapsed)
            val shot = game.shotCount != seenShots
            val popped = game.popCount != seenPops
            if (popped) {
                // Un groupe vient d'éclater (par un tir ou parce que le trou s'est refermé) : les billes s'animent, le téléphone vibre.
                spawnEffects(game.lastEffects)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (game.lastChain >= 2) {
                    comboRank = game.lastChain
                    comboLeft = MARBLE_COMBO_MS
                }
            } else if (shot) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            seenShots = game.shotCount
            seenPops = game.popCount
            if (comboLeft > 0L) {
                comboLeft -= elapsed
                if (comboLeft <= 0L) comboRank = 0
            }
            if (particles.isNotEmpty()) stepParticles(elapsed)
            sync()
            if (game.level != seenLevel) {
                // La chaîne vient d'être vidée : le jeu est déjà reparti sur le niveau suivant.
                seenLevel = game.level
                clearedChanged(seenLevel - 1)
                celebrating = true
            }
        }
        // La partie est perdue : on laisse les dernières animations se jouer avant la carte de fin.
        var wait = MARBLE_GAME_OVER_DELAY_MS
        while (wait > 0L) {
            val now = withFrameNanos { it }
            val elapsed = ((now - last) / 1_000_000L).coerceIn(0L, MARBLE_MAX_FRAME_MS)
            last = now
            wait -= elapsed.coerceAtLeast(1L)
            stepParticles(elapsed)
            sync()
        }
        finished()
    }

    val boardDescription = stringResource(
        R.string.marble_board_description,
        level,
        remaining,
        stringResource(marbleKindName(currentKind)),
        stringResource(marbleKindName(nextKind))
    )

    // La marge de la barre de navigation (boutons ou geste) garde les commandes du bas hors de ses touches.
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
            primary = stringResource(R.string.bubble_score, score),
            secondary = stringResource(R.string.marble_stats, level, remaining)
        ) else Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.bubble_score, score),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.marble_stats, level, remaining),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { onPausedChange(true) }, enabled = !paused) {
                    Text(stringResource(R.string.bubble_pause))
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Une case du terrain, la plus grande qui laisse tenir le terrain entier.
            val cell = maxOf(minOf(maxWidth / TdMap.COLS, maxHeight / TdMap.ROWS), 0.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(cell * TdMap.COLS, cell * TdMap.ROWS)
                    .clip(RoundedCornerShape(8.dp))
                    .semantics { contentDescription = boardDescription }
                    // Poser le doigt vise, le glisser règle la visée, le relever tire. Toucher la bille suivante l'échange avec celle du lanceur.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val px = size.width / TdMap.COLS.toFloat()
                            fun aimAt(point: Offset) {
                                aim = atan2(point.y - game.shooterY * px, point.x - game.shooterX * px)
                            }
                            val nextCenter = Offset(
                                (game.shooterX + MARBLE_NEXT_DX) * px,
                                (game.shooterY + MARBLE_NEXT_DY) * px
                            )
                            val onNext = (down.position - nextCenter).getDistance() <= MARBLE_NEXT_TOUCH * px
                            if (!onNext) aimAt(down.position)
                            down.consume()
                            val released = drag(down.id) { change ->
                                change.consume()
                                if (!onNext) aimAt(change.position)
                            }
                            if (released) {
                                if (onNext) swapAction() else fireAction()
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawMarbleBoard(game, aim, guide == MARBLE_GUIDE_ON, colorBlind, particles, sprites, tileCache, revision)
                }
                if (comboRank >= 2 && !paused) {
                    Text(
                        text = stringResource(R.string.marble_combo, comboRank),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                if (celebrating && !paused) {
                    Text(
                        text = stringResource(R.string.marble_cleared_title, level),
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
                            text = stringResource(R.string.bubble_paused_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(8.dp)
                        )
                        if (tv) TvResumeHint()
                        Button(onClick = { onPausedChange(false) }) {
                            Text(stringResource(R.string.bubble_resume))
                        }
                    }
                }
            }
        }

        // Les commandes : tourner la visée (en maintenant pour continuer), échanger les billes, tirer.
        if (!tv) Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MarbleKey(
                description = stringResource(R.string.bubble_left),
                modifier = Modifier.weight(1f),
                repeating = true,
                onAction = { aimLeftAction() }
            ) { MarbleChevron(pointsLeft = true) }
            MarbleKey(
                description = stringResource(R.string.bubble_swap),
                modifier = Modifier.weight(1.4f),
                repeating = false,
                onAction = { swapAction() }
            ) { MarbleKeyLabel(stringResource(R.string.bubble_swap)) }
            MarbleKey(
                description = stringResource(R.string.bubble_fire),
                modifier = Modifier.weight(1.4f),
                repeating = false,
                onAction = { fireAction() }
            ) { MarbleKeyLabel(stringResource(R.string.bubble_fire)) }
            MarbleKey(
                description = stringResource(R.string.bubble_right),
                modifier = Modifier.weight(1f),
                repeating = true,
                onAction = { aimRightAction() }
            ) { MarbleChevron(pointsLeft = false) }
        }
    }
}

/**
 * Une touche de commande qui agit dès que le doigt se pose : attendre qu'il se lève ferait perdre un temps précieux. Si
 * [repeating], garder le doigt posé répète l'action (pour tourner la visée sans lever le doigt).
 */
@Composable
private fun MarbleKey(
    description: String,
    modifier: Modifier = Modifier,
    repeating: Boolean,
    onAction: () -> Unit,
    content: @Composable () -> Unit
) {
    val action by rememberUpdatedState(onAction)
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
            .pointerInput(repeating) {
                detectTapGestures(onPress = {
                    action()
                    if (repeating) {
                        coroutineScope {
                            val repeat = launch {
                                delay(MARBLE_AIM_REPEAT_DELAY_MS)
                                while (true) {
                                    action()
                                    delay(MARBLE_AIM_REPEAT_MS)
                                }
                            }
                            tryAwaitRelease()
                            repeat.cancel()
                        }
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun MarbleKeyLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

/** Un chevron tourné vers la gauche ou la droite. */
@Composable
private fun MarbleChevron(pointsLeft: Boolean) {
    val color = MaterialTheme.colorScheme.onSecondaryContainer
    Canvas(modifier = Modifier.size(28.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val chevron = Path().apply {
            if (pointsLeft) {
                moveTo(w * 0.65f, h * 0.15f)
                lineTo(w * 0.3f, h * 0.5f)
                lineTo(w * 0.65f, h * 0.85f)
            } else {
                moveTo(w * 0.35f, h * 0.15f)
                lineTo(w * 0.7f, h * 0.5f)
                lineTo(w * 0.35f, h * 0.85f)
            }
        }
        drawPath(chevron, color, style = stroke)
    }
}

/** Retient les numéros de tuiles du sol de la carte en cours : on ne les recalcule que quand la carte change. */
private class MarbleTileCache {
    var map: TdMap? = null
    var tiles = IntArray(0)
}

/** Dessine un morceau de planche ([srcX], [srcY], [srcW], [srcH] en pixels de l'image) dans le rectangle ([x], [y], [w], [h]). */
private fun DrawScope.drawMarblePiece(
    image: ImageBitmap,
    srcX: Int, srcY: Int, srcW: Int, srcH: Int,
    x: Float, y: Float, w: Float, h: Float
) {
    drawImage(
        image = image,
        srcOffset = IntOffset(srcX, srcY),
        srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
        dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
        filterQuality = FilterQuality.Medium
    )
}

/**
 * Le terrain : le sol et les décors de la carte, le trou (qui rougit quand la chaîne approche), les billes, le guide de visée,
 * la bille en vol, le lanceur et la bille suivante. [revision] n'est là que pour que le dessin se refasse quand elle change.
 */
private fun DrawScope.drawMarbleBoard(
    game: MarbleGame,
    aim: Float,
    showGuide: Boolean,
    colorBlind: Boolean,
    particles: List<MarbleParticle>,
    sprites: TdSpriteSet,
    cache: MarbleTileCache,
    revision: Int
) {
    // Lire la révision suffit à refaire le dessin à chaque image.
    if (revision < 0) return
    val cell = size.width / TdMap.COLS
    val map = game.map
    if (cache.map !== map) {
        cache.map = map
        cache.tiles = IntArray(TdMap.COLS * TdMap.ROWS) { map.tileIndex(it / TdMap.COLS, it % TdMap.COLS) }
    }

    // Le sol : une tuile par case, sans trait entre elles (les bords sont arrondis à l'entier le plus proche).
    // Le thème de la carte (marais ou été) ne change que les images, comme au Défense de tours.
    val tiles = if (map.theme == TdMap.THEME_SUMMER) sprites.tilesSummer else sprites.tiles
    for (r in 0 until TdMap.ROWS) {
        val y0 = (r * cell).roundToInt()
        val y1 = ((r + 1) * cell).roundToInt()
        for (c in 0 until TdMap.COLS) {
            val x0 = (c * cell).roundToInt()
            val x1 = ((c + 1) * cell).roundToInt()
            val tile = cache.tiles[r * TdMap.COLS + c]
            if (tiles != null) {
                drawMarblePiece(
                    tiles, (tile % MARBLE_TILE_COLS) * MARBLE_TILE_PX, (tile / MARBLE_TILE_COLS) * MARBLE_TILE_PX,
                    MARBLE_TILE_PX, MARBLE_TILE_PX,
                    x0.toFloat(), y0.toFloat(), (x1 - x0).toFloat(), (y1 - y0).toFloat()
                )
            } else {
                drawRect(
                    if (map.isPath(r, c)) MarbleRoadColor else MarbleSwampColor,
                    Offset(x0.toFloat(), y0.toFloat()),
                    Size((x1 - x0).toFloat(), (y1 - y0).toFloat())
                )
            }
        }
    }

    // Les décors.
    val props = if (map.theme == TdMap.THEME_SUMMER) sprites.propsSummer else sprites.props
    if (props != null) {
        for (r in 0 until TdMap.ROWS) {
            for (c in 0 until TdMap.COLS) {
                val decor = map.decor[r * TdMap.COLS + c]
                if (decor < 0) continue
                drawMarblePiece(
                    props, (decor % MARBLE_PROP_COLS) * MARBLE_PROP_PX, (decor / MARBLE_PROP_COLS) * MARBLE_PROP_PX,
                    MARBLE_PROP_PX, MARBLE_PROP_PX,
                    c * cell, r * cell, cell, cell
                )
            }
        }
    }

    // Le trou, au bout du chemin : son contour rougit à mesure que la tête de la chaîne s'en approche.
    val hole = Offset(game.path.holeX * cell, game.path.holeY * cell)
    val danger = ((game.progress - 0.6f) / 0.4f).coerceIn(0f, 1f)
    drawCircle(MarbleHoleColor, cell * 0.5f, hole)
    drawCircle(
        color = MarbleDangerColor.copy(alpha = 0.25f + 0.75f * danger),
        radius = cell * 0.5f,
        center = hole,
        style = Stroke(width = cell * (0.06f + 0.06f * danger))
    )

    // Les billes de la chaîne.
    val radius = cell * Marble.RADIUS * 0.96f
    for (i in game.marbleCount - 1 downTo 0) {
        val y = game.marbleY(i)
        if (y < -Marble.RADIUS) continue
        drawMarble(Offset(game.marbleX(i) * cell, y * cell), radius, game.marbleColor(i), colorBlind)
    }

    // Les animations : la bille rétrécit pendant qu'un anneau s'élargit, et des étincelles partent.
    for (p in particles) {
        val t = (p.age / p.life).coerceIn(0f, 1f)
        val position = Offset(p.x * cell, p.y * cell)
        val base = marbleBase(p.color)
        if (p.ring) {
            if (t < 0.5f) drawMarble(position, radius * (1f - 2f * t), p.color, colorBlind)
            drawCircle(
                color = lerp(base, Color.White, 0.4f).copy(alpha = 1f - t),
                radius = radius * (0.7f + 0.9f * t),
                center = position,
                style = Stroke(width = radius * 0.35f * (1f - t) + 1f)
            )
        } else {
            drawCircle(
                color = lerp(base, Color.White, 0.3f).copy(alpha = 1f - t),
                radius = radius * 0.22f * (1f - 0.6f * t),
                center = position
            )
        }
    }

    val shooter = Offset(game.shooterX * cell, game.shooterY * cell)
    val flying = game.isFlying
    val dir = Offset(cos(aim), sin(aim))

    // Le guide de visée : un trait en pointillés jusqu'à la première bille touchée, et la place où la bille arriverait.
    if (showGuide && !flying && !game.isOver) {
        val guide = game.trajectory(aim).map { Offset(it.first * cell, it.second * cell) }
        drawLine(
            color = MarbleGuideColor.copy(alpha = 0.6f),
            start = guide[0],
            end = guide[1],
            strokeWidth = cell * 0.07f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(cell * 0.18f, cell * 0.22f))
        )
        drawCircle(
            color = marbleBase(game.current).copy(alpha = 0.55f),
            radius = radius,
            center = guide[1],
            style = Stroke(width = cell * 0.07f)
        )
    }

    // Le canon : un tube dans le sens de la visée, la bille chargée posée par-dessus.
    drawLine(MarbleBarrelColor, shooter, shooter + dir * (cell * 0.95f), strokeWidth = cell * 0.42f, cap = StrokeCap.Round)
    drawCircle(Color(0xFF546E7A), cell * 0.55f, shooter)
    drawMarble(shooter, radius, game.current, colorBlind)

    if (flying) {
        drawMarble(Offset(game.flightX * cell, game.flightY * cell), radius, game.flightColor, colorBlind)
    }

    // La bille suivante, à côté du lanceur : la toucher l'échange avec celle du lanceur.
    if (!game.isOver) {
        val nextCenter = Offset((game.shooterX + MARBLE_NEXT_DX) * cell, (game.shooterY + MARBLE_NEXT_DY) * cell)
        drawCircle(
            color = Color.White.copy(alpha = 0.5f),
            radius = cell * MARBLE_NEXT_RADIUS * 1.3f,
            center = nextCenter,
            style = Stroke(width = cell * 0.05f)
        )
        drawMarble(nextCenter, cell * MARBLE_NEXT_RADIUS, game.next, colorBlind)
    }
}

/** Une bille : une sphère brillante cernée d'un trait sombre, avec sa lettre en mode daltonien. */
private fun DrawScope.drawMarble(center: Offset, radius: Float, index: Int, colorBlind: Boolean) {
    if (radius <= 0f) return
    val base = marbleBase(index)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(lerp(base, Color.White, 0.55f), base, lerp(base, Color.Black, 0.35f)),
            center = center - Offset(radius * 0.35f, radius * 0.35f),
            radius = radius * 1.6f
        ),
        radius = radius,
        center = center
    )
    drawCircle(
        color = lerp(base, Color.Black, 0.5f),
        radius = radius,
        center = center,
        style = Stroke(width = radius * 0.08f)
    )
    drawCircle(Color.White.copy(alpha = 0.55f), radius * 0.16f, center - Offset(radius * 0.38f, radius * 0.38f))
    if (colorBlind) {
        val paint = MARBLE_CODE_PAINT
        paint.textSize = radius * 1.1f
        val letter = ('A' + index).toString()
        drawContext.canvas.nativeCanvas.drawText(letter, center.x, center.y - (paint.ascent() + paint.descent()) / 2f, paint)
    }
}

private fun findMarbleLifecycleOwner(context: Context): LifecycleOwner? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return null
}
