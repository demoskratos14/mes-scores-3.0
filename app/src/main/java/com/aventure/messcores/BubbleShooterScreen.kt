package com.aventure.messcores

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Les étapes de la séance.
private const val BUBBLE_PHASE_READY = 0 // on attend que le joueur lance la partie
private const val BUBBLE_PHASE_PLAYING = 1 // la partie est en cours
private const val BUBBLE_PHASE_OVER = 2 // une bulle a touché la ligne rouge : la partie est finie

/** Plus long temps d'une image pris en compte : après un à-coup, la bulle ne doit pas sauter à travers les autres. */
private const val MAX_FRAME_MS = 50L

/** Temps d'attente, en millisecondes, entre un tableau vidé et la reprise du jeu sur le nouveau tableau. */
private const val CELEBRATION_MS = 1500L

/** Temps laissé à voir le tableau final avant la carte de fin. */
private const val GAME_OVER_DELAY_MS = 900L

/** Tourner la visée avec les flèches : radians par pas, délai avant la répétition et temps entre deux pas, en millisecondes. */
private const val AIM_STEP = 0.025f
private const val AIM_REPEAT_DELAY_MS = 250L
private const val AIM_REPEAT_MS = 25L

/** La bulle suivante, posée à gauche du lanceur : son décalage, son rayon (en unités du terrain) et la tolérance du toucher. */
private const val NEXT_OFFSET_X = -2.6f
private const val NEXT_OFFSET_Y = 0.25f
private const val NEXT_RADIUS = 0.4f
private const val NEXT_TOUCH_RADIUS = 0.95f

// Les animations : un anneau qui s'élargit (la bulle éclate), des étincelles, et les bulles qui tombent avec de la gravité.
private const val PARTICLE_RING = 0
private const val PARTICLE_SPARK = 1
private const val PARTICLE_FALL = 2

/** Durée de vie, en millisecondes, d'un anneau d'éclatement et d'une étincelle. */
private const val RING_LIFE_MS = 280f
private const val SPARK_LIFE_MS = 450f

/** Étincelles par bulle éclatée, et nombre maximal par tir (un gros éclatement ne doit pas alourdir le dessin). */
private const val SPARKS_PER_BUBBLE = 4
private const val MAX_SPARKS_PER_SHOT = 80

/** Gravité des bulles qui tombent, en unités du terrain par seconde au carré. */
private const val FALL_GRAVITY = 55f

private const val TWO_PI = 6.2831855f

/**
 * Un élément d'animation, en unités du terrain : sa [kind] ([PARTICLE_RING], [PARTICLE_SPARK] ou [PARTICLE_FALL]), sa position et
 * sa vitesse (par seconde), la couleur de la bulle d'où il vient, son âge et sa durée de vie [life] en millisecondes.
 */
private class BubbleParticle(
    val kind: Int,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Int,
    val life: Float
) {
    var age = 0f
}

// Le guide de visée : sans, court (deux rebonds au plus) ou long (cinq). Le choix est gardé d'une séance à l'autre, comme le mode.
private const val GUIDE_OFF = 0
private const val GUIDE_SHORT = 1
private const val GUIDE_LONG = 2

/** Le nombre de rebonds que le guide montre, ou -1 s'il n'y a pas de guide. */
private fun guideBounces(guide: Int): Int = when (guide) {
    GUIDE_LONG -> 5
    GUIDE_SHORT -> 2
    else -> -1
}

private const val PREFS_NAME = "mes_scores_bubble"
private const val PREF_MODE = "mode"
private const val PREF_GUIDE = "guide"

private val BubbleBoardColor = Color(0xFF14213D)
private val BubbleShooterZoneColor = Color(0xFF1B2D52)
private val BubbleLoseLineColor = Color(0xFFFF5252)
private val BubbleGuideColor = Color(0xFFFFFFFF)
private val BubbleBarrelColor = Color(0xFF90A4AE)

/** Les six couleurs de bulles, dans l'ordre de leur numéro. */
private val BubbleColors = listOf(
    Color(0xFFE53935), // rouge
    Color(0xFF1E88E5), // bleu
    Color(0xFF43A047), // vert
    Color(0xFFFDD835), // jaune
    Color(0xFF8E24AA), // violet
    Color(0xFFFB8C00) // orange
)

/** Les couleurs de l'arc-en-ciel, dans l'ordre, la première répétée à la fin pour que le tour se referme. */
private val RainbowColors = listOf(
    Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835), Color(0xFF43A047),
    Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFE53935)
)

/** La couleur d'une bulle de numéro [index] : une des six couleurs, ou celle qui représente une bulle spéciale (anneaux, étincelles, guide). */
private fun bubbleBase(index: Int): Color = when (index) {
    Bubble.BOMB -> Color(0xFFFF7043)
    Bubble.RAINBOW -> Color.White
    Bubble.LIGHTNING -> Color(0xFFFFEB3B)
    else -> BubbleColors[index.coerceAtLeast(0) % BubbleColors.size]
}

/** Le nom d'une bulle de numéro [kind] pour les lecteurs d'écran. */
@StringRes
private fun bubbleKindName(kind: Int): Int = when (kind) {
    0 -> R.string.bubble_kind_0
    1 -> R.string.bubble_kind_1
    2 -> R.string.bubble_kind_2
    3 -> R.string.bubble_kind_3
    4 -> R.string.bubble_kind_4
    5 -> R.string.bubble_kind_5
    Bubble.BOMB -> R.string.bubble_kind_6
    Bubble.RAINBOW -> R.string.bubble_kind_7
    else -> R.string.bubble_kind_8
}

/** Le crayon des lettres du mode daltonien : A, B, C… sur chaque couleur. */
private val CODE_PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.WHITE
    textAlign = Paint.Align.CENTER
    typeface = Typeface.DEFAULT_BOLD
    setShadowLayer(3f, 0f, 0f, android.graphics.Color.BLACK)
}

/** Garde la partie quand l'écran tourne : son état est rangé dans une liste d'entiers (voir [BubbleGame.save]). */
private val BubbleGameSaver = listSaver<BubbleGame, Int>(
    save = { it.save() },
    restore = { BubbleGame.restore(it) }
)

/**
 * Bubble Shooter en solo : des bulles de couleur sont accrochées au plafond ; on en tire une à la fois depuis le lanceur,
 * en glissant le doigt sur le plateau pour viser et en le relevant pour tirer (ou avec les flèches et le bouton). Un groupe de
 * trois bulles de même couleur éclate, et les bulles qui ne tiennent plus au plafond tombent. Les tirs manqués font descendre le
 * plafond ; une bulle sur la ligne rouge finit la partie. Le meilleur score est gardé d'une séance à l'autre. Vider le tableau
 * donne un bonus, une pluie de paillettes et le niveau suivant. Elle se met en pause quand l'appli passe en arrière-plan.
 * Avant la partie, on choisit le mode (classique, zen, défi du jour : un record chacun) et le guide de visée (sans, court, long) ;
 * les gros coups font arriver des bulles spéciales (bombe, arc-en-ciel, éclair) et les échanges de bulles sont comptés.
 * Tout est gardé quand l'écran tourne, la bulle en vol comprise. [players] : le joueur dont le nom s'affiche au départ.
 */
@Composable
internal fun BubbleShooterScreen(players: List<String>) {
    val name = players.firstOrNull().orEmpty().ifEmpty { "1" }
    val context = LocalContext.current
    val game = rememberSaveable(saver = BubbleGameSaver) { BubbleGame() }
    var phase by rememberSaveable { mutableIntStateOf(BUBBLE_PHASE_READY) }
    var paused by rememberSaveable { mutableStateOf(false) }
    // Nombre de tableaux vidés : à chaque hausse, la pluie de paillettes tombe.
    var cleared by rememberSaveable { mutableIntStateOf(game.level - 1) }
    // Le mode et le guide : gardés d'une séance à l'autre dans les préférences, et quand l'écran tourne.
    val prefs = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var modeIndex by rememberSaveable { mutableIntStateOf(prefs.getInt(PREF_MODE, 0)) }
    var guide by rememberSaveable { mutableIntStateOf(prefs.getInt(PREF_GUIDE, GUIDE_SHORT).coerceIn(GUIDE_OFF, GUIDE_LONG)) }
    val mode = BubbleMode.fromIndex(modeIndex)
    // Le défi du jour est le même pour tout le monde, un jour donné : sa graine vient de la date.
    val dailySeed = remember {
        val today = LocalDate.now()
        Bubble.dailySeed(today.year, today.monthValue, today.dayOfMonth)
    }
    // Un record par mode, et un par jour pour le défi du jour.
    val record = rememberSoloRecord(
        when (mode) {
            BubbleMode.CLASSIC -> BUBBLE_ID
            BubbleMode.ZEN -> BUBBLE_ID + "_zen"
            BubbleMode.DAILY -> BUBBLE_ID + "_daily_" + dailySeed
        }
    )

    // La partie se met en pause quand l'appli passe en arrière-plan (et donc aussi quand l'écran tourne).
    val lifecycleOwner = remember(context) { findBubbleLifecycleOwner(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && phase == BUBBLE_PHASE_PLAYING) paused = true
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    // Le bouton retour met la partie en pause plutôt que de la quitter par mégarde.
    BackHandler(enabled = phase == BUBBLE_PHASE_PLAYING && !paused) { paused = true }

    fun startGame() {
        record.clearNew()
        game.start(mode, dailySeed)
        cleared = 0
        paused = false
        phase = BUBBLE_PHASE_PLAYING
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (phase) {
            BUBBLE_PHASE_PLAYING -> BubblePlayfield(
                game = game,
                guide = guide,
                paused = paused,
                onPausedChange = { paused = it },
                onCleared = { cleared = it },
                onOver = {
                    record.submit(game.score)
                    phase = BUBBLE_PHASE_OVER
                }
            )
            BUBBLE_PHASE_OVER -> BubbleCard {
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
                Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.bubble_play_again))
                }
                OutlinedButton(onClick = { phase = BUBBLE_PHASE_READY }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.bubble_change_mode))
                }
            }
            else -> BubbleCard {
                Text(
                    text = stringResource(R.string.bubble_ready_title, name),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.bubble_ready_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                BubbleSetup(
                    modeIndex = modeIndex,
                    guide = guide,
                    onModeChange = {
                        modeIndex = it
                        prefs.edit { putInt(PREF_MODE, it) }
                    },
                    onGuideChange = {
                        guide = it
                        prefs.edit { putInt(PREF_GUIDE, it) }
                    }
                )
                SoloRecordLine(record)
                Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.bubble_go))
                }
            }
        }
        RecordSparkles(record)
        SparkleRain(cleared)
    }
}

/**
 * Les choix d'avant la partie : le mode (classique, zen, défi du jour) avec une phrase qui l'explique, et le guide de visée
 * (sans, court, long). Le choix en cours est le bouton plein, les autres sont cernés.
 */
@Composable
private fun BubbleSetup(
    modeIndex: Int,
    guide: Int,
    onModeChange: (Int) -> Unit,
    onGuideChange: (Int) -> Unit
) {
    val modes = listOf(
        stringResource(R.string.bubble_mode_classic),
        stringResource(R.string.bubble_mode_zen),
        stringResource(R.string.bubble_mode_daily)
    )
    val guides = listOf(
        stringResource(R.string.bubble_guide_off),
        stringResource(R.string.bubble_guide_short),
        stringResource(R.string.bubble_guide_long)
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BubbleChoiceRow(labels = modes, selected = modeIndex, onSelect = onModeChange)
        Text(
            text = stringResource(
                when (BubbleMode.fromIndex(modeIndex)) {
                    BubbleMode.CLASSIC -> R.string.bubble_mode_classic_hint
                    BubbleMode.ZEN -> R.string.bubble_mode_zen_hint
                    BubbleMode.DAILY -> R.string.bubble_mode_daily_hint
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
        BubbleChoiceRow(labels = guides, selected = guide, onSelect = onGuideChange)
    }
}

/** Une rangée de choix de même largeur : celui de numéro [selected] est un bouton plein, les autres sont cernés. */
@Composable
private fun BubbleChoiceRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
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
private fun BubbleCard(content: @Composable ColumnScope.() -> Unit) {
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
                        text = stringResource(R.string.game_bubble),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    RulesButton(
                        gameId = BUBBLE_ID,
                        gameName = stringResource(R.string.game_bubble),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                content()
            }
        }
    }
}

/**
 * La partie en cours : l'en-tête (score, niveau, tirs avant la descente, pause), le plateau, puis les commandes. La partie avance
 * à chaque image tant qu'elle n'est pas en [paused] ; quand la partie est perdue, [onOver] est appelé une fois.
 */
@Composable
private fun BubblePlayfield(
    game: BubbleGame,
    guide: Int,
    paused: Boolean,
    onPausedChange: (Boolean) -> Unit,
    onCleared: (Int) -> Unit,
    onOver: () -> Unit
) {
    KeepScreenOn()
    val colorBlind = LocalColorBlindMode.current
    // Change à chaque image ou action : le dessin le lit et se refait, sans recomposer tout l'écran.
    var revision by remember { mutableIntStateOf(0) }
    // Les chiffres de l'en-tête : ils ne recomposent l'écran que lorsqu'ils changent.
    var score by remember { mutableIntStateOf(game.score) }
    var level by remember { mutableIntStateOf(game.level) }
    var untilDrop by remember { mutableIntStateOf(game.shotsUntilDrop) }
    // Les échanges qui restent, le nombre de bulles et les deux bulles du lanceur : l'étiquette de l'échange et la description du plateau pour les lecteurs d'écran.
    var swaps by remember { mutableIntStateOf(game.swapCharges) }
    var bubbles by remember { mutableIntStateOf(game.bubbleCount) }
    var currentKind by remember { mutableIntStateOf(game.current) }
    var nextKind by remember { mutableIntStateOf(game.next) }
    // L'angle de visée, de l'horizontale : tout droit au départ, gardé quand l'écran tourne.
    var aim by rememberSaveable { mutableFloatStateOf(Bubble.START_ANGLE) }
    // Vrai pendant que la fête du tableau vidé se joue : les tirs attendent un instant.
    var celebrating by remember { mutableStateOf(false) }
    val finished by rememberUpdatedState(onOver)
    val clearedChanged by rememberUpdatedState(onCleared)

    fun sync() {
        revision++
        score = game.score
        level = game.level
        untilDrop = game.shotsUntilDrop
        swaps = game.swapCharges
        bubbles = game.bubbleCount
        currentKind = game.current
        nextKind = game.next
    }

    // Les animations en cours : ni sauvegardées ni observées, le dessin les relit à chaque image (voir [revision]).
    val particles = remember { mutableListOf<BubbleParticle>() }
    val random = remember { Random.Default }
    val haptic = LocalHapticFeedback.current

    /** Lance l'animation des bulles que le dernier tir a fait éclater ou tomber. */
    fun spawnEffects(effects: List<BubbleEffect>) {
        var sparks = 0
        for (e in effects) {
            if (e.falling) {
                // Un petit saut vers le haut, une dérive de côté, puis la chute.
                particles.add(
                    BubbleParticle(PARTICLE_FALL, e.x, e.y, (random.nextFloat() - 0.5f) * 3f, -random.nextFloat() * 4f, e.color, Float.MAX_VALUE)
                )
            } else {
                particles.add(BubbleParticle(PARTICLE_RING, e.x, e.y, 0f, 0f, e.color, RING_LIFE_MS))
                for (i in 0 until SPARKS_PER_BUBBLE) {
                    if (sparks >= MAX_SPARKS_PER_SHOT) break
                    val angle = random.nextFloat() * TWO_PI
                    val speed = 3f + random.nextFloat() * 4f
                    particles.add(
                        BubbleParticle(PARTICLE_SPARK, e.x, e.y, cos(angle) * speed, sin(angle) * speed, e.color, SPARK_LIFE_MS)
                    )
                    sparks++
                }
            }
        }
    }

    /** Fait avancer les animations de [elapsedMs] millisecondes et retire celles qui sont finies ou sorties du terrain. */
    fun stepParticles(elapsedMs: Long) {
        val seconds = elapsedMs / 1000f
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.age += elapsedMs
            when (p.kind) {
                PARTICLE_FALL -> {
                    p.vy += FALL_GRAVITY * seconds
                    p.x += p.vx * seconds
                    p.y += p.vy * seconds
                }
                PARTICLE_SPARK -> {
                    p.vy += FALL_GRAVITY * 0.3f * seconds
                    p.x += p.vx * seconds
                    p.y += p.vy * seconds
                }
            }
            if (p.age >= p.life || p.y > Bubble.HEIGHT + 1f) iterator.remove()
        }
    }

    fun fire() {
        if (paused || celebrating || game.isOver) return
        if (game.shoot(aim)) sync()
    }

    fun swap() {
        if (paused || celebrating) return
        if (game.swap()) sync()
    }

    fun turnAim(delta: Float) {
        if (paused) return
        aim = Bubble.clampAngle(aim + delta)
        revision++
    }

    // Les gestes et les touches gardent ces actions à jour sans être relancés à chaque image.
    val fireAction by rememberUpdatedState<() -> Unit>({ fire() })
    val swapAction by rememberUpdatedState<() -> Unit>({ swap() })
    val aimLeftAction by rememberUpdatedState<() -> Unit>({ turnAim(AIM_STEP) })
    val aimRightAction by rememberUpdatedState<() -> Unit>({ turnAim(-AIM_STEP) })

    LaunchedEffect(celebrating) {
        if (!celebrating) return@LaunchedEffect
        delay(CELEBRATION_MS)
        celebrating = false
    }

    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        var last = withFrameNanos { it }
        var seenLevel = game.level
        var seenShots = game.shotCount
        while (!game.isOver) {
            val now = withFrameNanos { it }
            val elapsed = ((now - last) / 1_000_000L).coerceIn(0L, MAX_FRAME_MS)
            last = now
            val wasFlying = game.isFlying
            game.tick(elapsed)
            if (game.shotCount != seenShots) {
                // Une bulle vient de se poser : les bulles parties s'animent, et le téléphone vibre (plus fort si quelque chose est parti).
                seenShots = game.shotCount
                spawnEffects(game.lastEffects)
                haptic.performHapticFeedback(
                    if (game.lastEffects.isEmpty()) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress
                )
            }
            val animating = particles.isNotEmpty()
            if (animating) stepParticles(elapsed)
            if (wasFlying || game.isFlying || animating) sync()
            if (game.level != seenLevel) {
                // Le tableau vient d'être vidé : le jeu est déjà reparti sur le niveau suivant.
                seenLevel = game.level
                clearedChanged(seenLevel - 1)
                celebrating = true
            }
        }
        // La partie est perdue : on laisse les dernières animations se jouer avant la carte de fin.
        var wait = GAME_OVER_DELAY_MS
        while (wait > 0L) {
            val now = withFrameNanos { it }
            val elapsed = ((now - last) / 1_000_000L).coerceIn(0L, MAX_FRAME_MS)
            last = now
            wait -= elapsed.coerceAtLeast(1L)
            stepParticles(elapsed)
            sync()
        }
        finished()
    }

    val boardDescription = stringResource(
        R.string.bubble_board_description,
        level,
        bubbles,
        stringResource(bubbleKindName(currentKind)),
        stringResource(bubbleKindName(nextKind))
    )

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
                        text = stringResource(R.string.bubble_score, score),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (game.mode == BubbleMode.ZEN) {
                            stringResource(R.string.bubble_stats_zen, level)
                        } else {
                            stringResource(R.string.bubble_stats, level, untilDrop)
                        },
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
            // Une unité du terrain (le diamètre d'une bulle), la plus grande qui laisse tenir le terrain entier.
            val unit = maxOf(minOf(maxWidth / Bubble.WIDTH, maxHeight / Bubble.HEIGHT), 0.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(unit * Bubble.WIDTH, unit * Bubble.HEIGHT)
                    .clip(RoundedCornerShape(8.dp))
                    .semantics { contentDescription = boardDescription }
                    // Poser le doigt vise, le glisser règle la visée, le relever tire. Toucher la bulle suivante l'échange avec celle du lanceur.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val px = size.width / Bubble.WIDTH
                            fun aimAt(point: Offset) {
                                aim = Bubble.angleTo(
                                    point.x - Bubble.SHOOTER_X * px,
                                    Bubble.SHOOTER_Y * px - point.y
                                )
                            }
                            val nextCenter = Offset(
                                (Bubble.SHOOTER_X + NEXT_OFFSET_X) * px,
                                (Bubble.SHOOTER_Y + NEXT_OFFSET_Y) * px
                            )
                            val onNext = (down.position - nextCenter).getDistance() <= NEXT_TOUCH_RADIUS * px
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
                Canvas(modifier = Modifier.fillMaxSize()) { drawBubbleBoard(game, aim, guideBounces(guide), colorBlind, particles, revision) }
                if (celebrating && !paused) {
                    Text(
                        text = stringResource(R.string.bubble_cleared_title, level),
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
                        Button(onClick = { onPausedChange(false) }) {
                            Text(stringResource(R.string.bubble_resume))
                        }
                    }
                }
            }
        }

        // Les commandes : tourner la visée (en maintenant pour continuer), échanger les bulles, tirer.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BubbleKey(
                description = stringResource(R.string.bubble_left),
                modifier = Modifier.weight(1f),
                repeating = true,
                onAction = { aimLeftAction() }
            ) { BubbleChevron(pointsLeft = true) }
            BubbleKey(
                description = stringResource(R.string.bubble_swap),
                modifier = Modifier.weight(1.4f),
                repeating = false,
                onAction = { swapAction() }
            ) { BubbleKeyLabel(stringResource(R.string.bubble_swap_count, swaps), enabled = swaps > 0) }
            BubbleKey(
                description = stringResource(R.string.bubble_fire),
                modifier = Modifier.weight(1.4f),
                repeating = false,
                onAction = { fireAction() }
            ) { BubbleKeyLabel(stringResource(R.string.bubble_fire)) }
            BubbleKey(
                description = stringResource(R.string.bubble_right),
                modifier = Modifier.weight(1f),
                repeating = true,
                onAction = { aimRightAction() }
            ) { BubbleChevron(pointsLeft = false) }
        }
    }
}

/**
 * Une touche de commande qui agit dès que le doigt se pose : attendre qu'il se lève ferait perdre un temps précieux. Si
 * [repeating], garder le doigt posé répète l'action (pour tourner la visée sans lever le doigt).
 */
@Composable
private fun BubbleKey(
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
                                delay(AIM_REPEAT_DELAY_MS)
                                while (true) {
                                    action()
                                    delay(AIM_REPEAT_MS)
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
private fun BubbleKeyLabel(text: String, enabled: Boolean = true) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = if (enabled) 1f else 0.4f),
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

/** Un chevron tourné vers la gauche ou la droite. */
@Composable
private fun BubbleChevron(pointsLeft: Boolean) {
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

/**
 * Le terrain : le fond, la ligne rouge, les bulles accrochées, le guide de visée, la bulle en vol, le lanceur et la bulle suivante.
 * [revision] n'est là que pour que le dessin se refasse quand elle change.
 */
private fun DrawScope.drawBubbleBoard(
    game: BubbleGame,
    aim: Float,
    guideBounces: Int,
    colorBlind: Boolean,
    particles: List<BubbleParticle>,
    @Suppress("UNUSED_PARAMETER") revision: Int
) {
    val u = size.width / Bubble.WIDTH
    val radius = u * Bubble.RADIUS
    drawRect(BubbleBoardColor)
    // La zone du lanceur, sous la ligne rouge.
    drawRect(
        color = BubbleShooterZoneColor,
        topLeft = Offset(0f, Bubble.DEADLINE_Y * u),
        size = Size(size.width, size.height - Bubble.DEADLINE_Y * u)
    )
    drawLine(
        color = BubbleLoseLineColor.copy(alpha = 0.8f),
        start = Offset(0f, Bubble.DEADLINE_Y * u),
        end = Offset(size.width, Bubble.DEADLINE_Y * u),
        strokeWidth = u * 0.07f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * 0.3f, u * 0.2f))
    )

    for (row in 0 until Bubble.MAX_ROWS) {
        for (col in 0 until Bubble.COLS) {
            val color = game.colorAt(row, col)
            if (color == BubbleGame.EMPTY) continue
            drawBubble(Offset(game.centerX(row, col) * u, game.centerY(row) * u), radius * 0.96f, color, colorBlind)
        }
    }

    // Les animations : bulles qui éclatent (elle rétrécit pendant qu'un anneau s'élargit), étincelles, bulles qui tombent.
    for (p in particles) {
        val t = (p.age / p.life).coerceIn(0f, 1f)
        val position = Offset(p.x * u, p.y * u)
        val base = bubbleBase(p.color)
        when (p.kind) {
            PARTICLE_RING -> {
                if (t < 0.5f) drawBubble(position, radius * 0.96f * (1f - 2f * t), p.color, colorBlind)
                drawCircle(
                    color = lerp(base, Color.White, 0.4f).copy(alpha = 1f - t),
                    radius = radius * (0.7f + 0.9f * t),
                    center = position,
                    style = Stroke(width = radius * 0.35f * (1f - t) + 1f)
                )
            }
            PARTICLE_SPARK -> drawCircle(
                color = lerp(base, Color.White, 0.3f).copy(alpha = 1f - t),
                radius = radius * 0.22f * (1f - 0.6f * t),
                center = position
            )
            else -> drawBubble(position, radius * 0.96f, p.color, colorBlind)
        }
    }

    val shooter = Offset(Bubble.SHOOTER_X * u, Bubble.SHOOTER_Y * u)
    val flying = game.isFlying

    // Le guide de visée : un trait en pointillés (avec ses rebonds) et la place où la bulle se poserait.
    if (!flying && !game.isOver && guideBounces >= 0) {
        val path = game.trajectory(aim, guideBounces).map { Offset(it.first * u, it.second * u) }
        val dash = PathEffect.dashPathEffect(floatArrayOf(u * 0.18f, u * 0.22f))
        for (i in 0 until path.size - 1) {
            drawLine(
                color = BubbleGuideColor.copy(alpha = 0.55f),
                start = path[i],
                end = path[i + 1],
                strokeWidth = u * 0.07f,
                cap = StrokeCap.Round,
                pathEffect = dash
            )
        }
        drawCircle(
            color = bubbleBase(game.current).copy(alpha = 0.45f),
            radius = radius * 0.96f,
            center = path.last(),
            style = Stroke(width = u * 0.08f)
        )
    }

    // Le canon : un tube dans le sens de la visée, la bulle chargée posée par-dessus.
    val barrelEnd = shooter + Offset(cos(aim), -sin(aim)) * (u * 1.25f)
    drawLine(BubbleBarrelColor, shooter, barrelEnd, strokeWidth = u * 0.55f, cap = StrokeCap.Round)
    drawCircle(Color(0xFF546E7A), u * 0.62f, shooter)
    // Pendant un tir, le lanceur montre déjà la bulle suivante (c'est elle qui sera tirée ensuite).
    drawBubble(shooter, radius * 0.96f, if (flying) game.next else game.current, colorBlind)

    if (flying) {
        drawBubble(Offset(game.flightX * u, game.flightY * u), radius * 0.96f, game.current, colorBlind)
    } else if (!game.isOver) {
        // La bulle suivante, à gauche du lanceur : la toucher l'échange avec celle du lanceur.
        val nextCenter = Offset((Bubble.SHOOTER_X + NEXT_OFFSET_X) * u, (Bubble.SHOOTER_Y + NEXT_OFFSET_Y) * u)
        drawCircle(
            color = Color.White.copy(alpha = 0.35f),
            radius = u * NEXT_RADIUS * 1.25f,
            center = nextCenter,
            style = Stroke(width = u * 0.05f)
        )
        drawBubble(nextCenter, u * NEXT_RADIUS, game.next, colorBlind)
    }
}

/** Une bulle : une bille brillante cernée d'un trait sombre, avec sa lettre en mode daltonien. */
private fun DrawScope.drawBubble(center: Offset, radius: Float, index: Int, colorBlind: Boolean) {
    if (Bubble.isSpecial(index)) {
        drawSpecialBubble(center, radius, index)
        return
    }
    val base = bubbleBase(index)
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
    drawCircle(Color.White.copy(alpha = 0.5f), radius * 0.16f, center - Offset(radius * 0.38f, radius * 0.38f))
    if (colorBlind) {
        val paint = CODE_PAINT
        paint.textSize = radius * 1.1f
        val letter = ('A' + index).toString()
        drawContext.canvas.nativeCanvas.drawText(letter, center.x, center.y - (paint.ascent() + paint.descent()) / 2f, paint)
    }
}

/**
 * Une bulle spéciale : la bombe (sombre, avec sa mèche allumée), l'arc-en-ciel (tous les tons en tour) ou l'éclair (jaune, avec
 * son trait). Elles ne portent pas de lettre : leur forme suffit à les reconnaître, même sans les couleurs.
 */
private fun DrawScope.drawSpecialBubble(center: Offset, radius: Float, kind: Int) {
    val base = bubbleBase(kind)
    val fill = when (kind) {
        Bubble.RAINBOW -> Brush.sweepGradient(colors = RainbowColors, center = center)
        Bubble.BOMB -> Brush.radialGradient(
            colors = listOf(Color(0xFF78909C), Color(0xFF263238), Color(0xFF000000)),
            center = center - Offset(radius * 0.35f, radius * 0.35f),
            radius = radius * 1.6f
        )
        else -> Brush.radialGradient(
            colors = listOf(Color.White, base, lerp(base, Color.Black, 0.3f)),
            center = center - Offset(radius * 0.35f, radius * 0.35f),
            radius = radius * 1.6f
        )
    }
    drawCircle(brush = fill, radius = radius, center = center)
    drawCircle(
        color = Color.Black.copy(alpha = 0.55f),
        radius = radius,
        center = center,
        style = Stroke(width = radius * 0.08f)
    )
    when (kind) {
        Bubble.BOMB -> {
            // La mèche, et sa flamme.
            val from = center + Offset(radius * 0.45f, -radius * 0.55f)
            val to = center + Offset(radius * 0.75f, -radius * 0.95f)
            drawLine(Color(0xFFBCAAA4), from, to, strokeWidth = radius * 0.14f, cap = StrokeCap.Round)
            drawCircle(Color(0xFFFFB300), radius * 0.2f, to)
            drawCircle(Color.White.copy(alpha = 0.35f), radius * 0.16f, center - Offset(radius * 0.38f, radius * 0.38f))
        }
        Bubble.LIGHTNING -> {
            val bolt = Path().apply {
                moveTo(center.x + radius * 0.15f, center.y - radius * 0.75f)
                lineTo(center.x - radius * 0.4f, center.y + radius * 0.1f)
                lineTo(center.x - radius * 0.02f, center.y + radius * 0.1f)
                lineTo(center.x - radius * 0.2f, center.y + radius * 0.75f)
                lineTo(center.x + radius * 0.4f, center.y - radius * 0.15f)
                lineTo(center.x + radius * 0.02f, center.y - radius * 0.15f)
                close()
            }
            drawPath(bolt, Color(0xFF263238))
        }
        else -> drawCircle(Color.White.copy(alpha = 0.5f), radius * 0.16f, center - Offset(radius * 0.38f, radius * 0.38f))
    }
}

private fun findBubbleLifecycleOwner(context: Context): LifecycleOwner? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return null
}
