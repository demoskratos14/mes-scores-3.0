package com.aventure.messcores

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

// Les étapes de la séance.
private const val FRESH_PHASE_READY = 0 // on attend que le joueur lance la partie (touche A)
private const val FRESH_PHASE_PLAYING = 1 // la partie tourne
private const val FRESH_PHASE_OVER = 2 // la partie est terminée (service fini, ou plus rien à vendre)

/**
 * Les actions de Rayon frais réglables à la manette : les flèches gauche et droite (croix ou stick, tenir répète le pas) pour
 * se déplacer le long du meuble, les boutons A et B pour prendre, ranger, retirer, démarquer ou fermer la porte, et pause /
 * reprise (Start).
 */
private val FRESH_PAD_ACTIONS = listOf(
    padDirection("left", R.string.drive_dir_left, horizontal = true, sign = -1),
    padDirection("right", R.string.drive_dir_right, horizontal = true, sign = 1),
    padButtons("a", R.string.drive_button_a, KeyEvent.KEYCODE_BUTTON_A),
    padButtons("b", R.string.drive_button_b, KeyEvent.KEYCODE_BUTTON_B),
    padStart()
)

/** Plus long temps d'une image pris en compte : après un à-coup, la partie ne doit pas sauter d'un coup plusieurs secondes. */
private const val FRESH_MAX_FRAME_MS = 100L

/** Une flèche tenue répète le pas : première répétition après 250 ms, puis toutes les 190 ms (comme dans Drive). */
private const val FRESH_REPEAT_FIRST_MS = -60f
private const val FRESH_REPEAT_MS = 190f

// Le dessin de la console, en unités de 1 000 x 780 ; l'écran à cristaux liquides (16/9) y mesure 892 de large.
private const val CONSOLE_W = 1000f
private const val CONSOLE_H = 780f
private const val SHELL_RADIUS = 56f
private const val LCD_X = 54f
private const val LCD_Y = 84f
private const val LCD_W = 892f
private const val LCD_SCALE = LCD_W / Fresh.WIDTH
private const val LCD_H = Fresh.HEIGHT * LCD_SCALE

// Les boutons : les flèches gauche et droite en bas à gauche, A et B en bas à droite (centre et rayon du dessin).
private const val LEFT_X = 130f
private const val RIGHT_X = 270f
private const val SIDE_Y = 696f
private const val PAD_RADIUS = 38f
private const val A_X = 770f
private const val B_X = 880f
private const val CAP_Y = 696f
private const val CAP_RADIUS = 40f

// Les zones tactiles (gauche, haut, largeur, hauteur) : plus larges que les boutons dessinés, et sans se chevaucher.
private val LEFT_ZONE = floatArrayOf(60f, 626f, 140f, 140f)
private val RIGHT_ZONE = floatArrayOf(200f, 626f, 140f, 140f)
private val A_ZONE = floatArrayOf(712f, 626f, 112f, 140f)
private val B_ZONE = floatArrayOf(824f, 626f, 112f, 140f)

// Les boutons enfoncés, un bit chacun.
private const val PRESS_LEFT = 1
private const val PRESS_RIGHT = 2
private const val PRESS_A = 4
private const val PRESS_B = 8

/** Les couleurs de la console : coque blanche à liseré noir, cadre noir, rouge et noir pour les touches (couleurs seulement, aucun logo). */
private val ShellTop = Color(0xFFFFFFFF)
private val ShellMid = Color(0xFFF4F4F2)
private val ShellLow = Color(0xFFDEDEDA)
private val ShellBorder = Color(0xFF14161A)
private val ConsoleRed = Color(0xFFE2001A)
private val BezelTop = Color(0xFF34373C)
private val BezelLow = Color(0xFF0F1012)
private val CapLight = Color(0xFFFF5A63)
private val CapDark = Color(0xFF9A0012)
private val PadLight = Color(0xFF555960)
private val PadMid = Color(0xFF25272B)
private val PadDark = Color(0xFF0E0F11)

/** Garde la partie quand l'écran tourne : son état est rangé dans une liste de nombres (voir [FreshGame.save]). */
private val FreshGameSaver = listSaver<FreshGame, Double>(
    save = { it.save() },
    restore = { FreshGame.restore(it) }
)

/** La touche du jeu à répéter pour les boutons [pressed] enfoncés, ou -1 si aucune flèche n'est tenue. */
private fun heldArrow(pressed: Int): Int = when {
    pressed and PRESS_LEFT != 0 -> FRESH_LEFT
    pressed and PRESS_RIGHT != 0 -> FRESH_RIGHT
    else -> -1
}

/**
 * Rayon frais en solo : le personnage du Drive tient le rayon frais d'un supermarché. Il se déplace le long du meuble avec les
 * flèches (le chariot à gauche, cinq colonnes de produits, la porte du frigo à droite) et agit avec A et B : prendre un paquet au
 * chariot, le ranger au fond (A) ou devant (B), retirer (A) ou démarquer (B) le paquet qui va périmer, fermer la porte. Les clients
 * prennent les paquets ; un paquet périmé vendu, un contrôle d'hygiène raté ou une porte laissée ouverte sont des fautes graves
 * (trois, et c'est le licenciement). La console remplit l'écran en paysage ; le bouton retour et l'arrière-plan mettent la partie en
 * pause ; le meilleur score est gardé d'une séance à l'autre. [players] : le joueur dont le nom s'affiche au départ.
 */
@Composable
internal fun FreshScreen(players: List<String>) {
    val name = players.firstOrNull().orEmpty().ifEmpty { "1" }
    val context = LocalContext.current
    // Le mode TV (voir [TvMode]) : jouer à la manette sur une télé, avec seulement l'écran du jeu, sur tout l'écran.
    var tvMode by remember { mutableStateOf(TvMode.load(context, FRESH_ID)) }
    // Le jeu se met déjà en paysage tout seul : le mode TV n'ajoute que les barres du système cachées.
    if (tvMode) TvModeEffect(forceLandscape = false)
    fun changeTvMode(enabled: Boolean) {
        tvMode = enabled
        TvMode.save(context, FRESH_ID, enabled)
    }
    val game = rememberSaveable(saver = FreshGameSaver) { FreshGame() }
    var phase by rememberSaveable { mutableIntStateOf(FRESH_PHASE_READY) }
    var paused by rememberSaveable { mutableStateOf(false) }
    val record = rememberSoloRecord(FRESH_ID)
    // Change à chaque image ou action : le dessin le lit et se refait, sans recomposer tout l'écran.
    var revision by remember { mutableIntStateOf(0) }
    // Les chiffres lus par TalkBack et par la carte de fin : ils ne recomposent que lorsqu'ils changent.
    var score by remember { mutableIntStateOf(game.score) }
    var sold by remember { mutableIntStateOf(game.sold) }
    var removed by remember { mutableIntStateOf(game.removedExpired) }
    var wasted by remember { mutableIntStateOf(game.wasted) }
    var inspections by remember { mutableIntStateOf(game.inspectOk) }
    var fired by remember { mutableStateOf(game.endReason == FRESH_END_FIRED) }
    // Les boutons de la console actuellement enfoncés (un bit chacun, voir PRESS_LEFT et les suivants).
    var pressed by remember { mutableIntStateOf(0) }
    // Vrai pendant que le réglage de la manette est affiché sur l'écran de la console.
    var showPad by remember { mutableStateOf(false) }
    val sprites = remember { loadDriveSprites(context) }
    val measurer = rememberTextMeasurer()
    val labels = FreshLabels(
        time = stringResource(R.string.fresh_label_time),
        score = stringResource(R.string.fresh_label_score),
        faults = stringResource(R.string.fresh_label_faults),
        sign = stringResource(R.string.fresh_sign),
        opening = stringResource(R.string.fresh_opening),
        no = stringResource(R.string.fresh_no),
        cart = stringResource(R.string.fresh_cart),
        doorAlert = stringResource(R.string.fresh_door_alert),
        inspector = stringResource(R.string.fresh_inspector),
        products = listOf(
            stringResource(R.string.fresh_yogurt),
            stringResource(R.string.fresh_ham),
            stringResource(R.string.fresh_cheese),
            stringResource(R.string.fresh_salad),
            stringResource(R.string.fresh_chicken)
        ),
        events = listOf(
            "",
            stringResource(R.string.fresh_event_stock_ok),
            stringResource(R.string.fresh_event_stock_late),
            stringResource(R.string.fresh_event_sale),
            stringResource(R.string.fresh_event_sale_marked),
            stringResource(R.string.fresh_event_removed),
            stringResource(R.string.fresh_event_waste),
            stringResource(R.string.fresh_event_marked),
            stringResource(R.string.fresh_event_sick),
            stringResource(R.string.fresh_event_stockout),
            stringResource(R.string.fresh_event_wrong),
            stringResource(R.string.fresh_event_door_closed),
            stringResource(R.string.fresh_event_door_fault),
            stringResource(R.string.fresh_event_inspect_ok),
            stringResource(R.string.fresh_event_inspect_ko)
        )
    )
    val screenDescription = stringResource(R.string.fresh_score, score)

    // La console se joue à l'horizontale : l'écran passe en paysage tant que le jeu est ouvert.
    DisposableEffect(context) {
        val activity = findFreshActivity(context)
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { if (previous != null) activity?.requestedOrientation = previous }
    }

    // La partie se met en pause quand l'appli passe en arrière-plan (et donc aussi quand l'écran tourne).
    val lifecycleOwner = remember(context) { findFreshLifecycleOwner(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && phase == FRESH_PHASE_PLAYING) paused = true
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    // Le bouton retour met la partie en pause plutôt que de la quitter par mégarde.
    BackHandler(enabled = phase == FRESH_PHASE_PLAYING && !paused) { paused = true }
    if (phase == FRESH_PHASE_PLAYING && !paused) KeepScreenOn()

    fun sync() {
        revision++
        score = game.score
        sold = game.sold
        removed = game.removedExpired
        wasted = game.wasted
        inspections = game.inspectOk
        fired = game.endReason == FRESH_END_FIRED
    }

    fun press(bit: Int, down: Boolean) {
        pressed = if (down) pressed or bit else pressed and bit.inv()
    }

    /** Lance une partie neuve. */
    fun startGame() {
        record.clearNew()
        game.reset()
        paused = false
        phase = FRESH_PHASE_PLAYING
        sync()
    }

    /** Une touche du jeu pressée : en jeu elle agit ; sinon A lance une partie (après le message de fin, une fois qu'il a été lu). */
    fun key(key: Int) {
        if (phase == FRESH_PHASE_PLAYING && !paused) {
            game.press(key)
            sync()
        } else if (key == FRESH_A && !paused && (phase == FRESH_PHASE_READY || game.canRestart)) {
            startGame()
        }
    }

    LaunchedEffect(phase, paused) {
        if (phase == FRESH_PHASE_READY || paused) return@LaunchedEffect
        var last = withFrameNanos { it }
        var heldMs = FRESH_REPEAT_FIRST_MS
        var heldKey = -1
        while (true) {
            val now = withFrameNanos { it }
            val elapsed = ((now - last) / 1_000_000L).coerceIn(0L, FRESH_MAX_FRAME_MS).toFloat()
            last = now
            game.tick(elapsed)
            if (phase == FRESH_PHASE_PLAYING) {
                // Une flèche tenue répète le pas ; le premier pas a déjà été fait à l'appui.
                val held = heldArrow(pressed)
                if (held != heldKey) {
                    heldKey = held
                    heldMs = FRESH_REPEAT_FIRST_MS
                } else if (held >= 0) {
                    heldMs += elapsed
                    if (heldMs >= FRESH_REPEAT_MS) {
                        heldMs = 0f
                        game.press(held)
                    }
                }
                if (game.over) {
                    record.submit(game.score)
                    phase = FRESH_PHASE_OVER
                }
            }
            sync()
        }
    }

    // La manette agit comme les flèches et les boutons de la console : le bouton s'enfonce à l'appui et se relève au relâchement.
    GamepadListener(onRelease = { input ->
        when (GamepadBindings.resolve(context, FRESH_ID, FRESH_PAD_ACTIONS, input)) {
            "left" -> press(PRESS_LEFT, false)
            "right" -> press(PRESS_RIGHT, false)
            "a" -> press(PRESS_A, false)
            "b" -> press(PRESS_B, false)
            else -> {}
        }
    }) { input ->
        when (GamepadBindings.resolve(context, FRESH_ID, FRESH_PAD_ACTIONS, input)) {
            "left" -> {
                press(PRESS_LEFT, true)
                key(FRESH_LEFT)
            }
            "right" -> {
                press(PRESS_RIGHT, true)
                key(FRESH_RIGHT)
            }
            "a" -> {
                press(PRESS_A, true)
                key(FRESH_A)
            }
            "b" -> {
                press(PRESS_B, true)
                key(FRESH_B)
            }
            "pause" -> {
                if (phase == FRESH_PHASE_PLAYING) paused = !paused
                else if (!paused && (phase == FRESH_PHASE_READY || game.canRestart)) startGame()
            }
            else -> return@GamepadListener false
        }
        true
    }

    // Les messages (réglage de la manette, pause, fin de partie, accueil) : sur l'écran à cristaux liquides de la
    // console, ou sur tout l'écran en mode TV.
    val messages: @Composable (Modifier) -> Unit = { boxModifier ->
        if (showPad || phase != FRESH_PHASE_PLAYING || paused) {
            Box(modifier = boxModifier) {
                when {
                    showPad -> FreshInfoCard { GamepadSettings(FRESH_ID, FRESH_PAD_ACTIONS) { showPad = false } }
                    phase == FRESH_PHASE_PLAYING -> Column(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.drive_paused_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(8.dp)
                        )
                        if (tvMode) TvResumeHint()
                        Button(onClick = { paused = false }) {
                            Text(stringResource(R.string.drive_resume))
                        }
                    }
                    phase == FRESH_PHASE_OVER -> FreshInfoCard {
                        Text(
                            text = stringResource(if (fired) R.string.fresh_over_fired else R.string.fresh_over_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            color = Color(0xFFE2001A),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.fresh_over_summary, sold, removed, wasted, inspections),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.fresh_score, score),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = highlightContent(),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(highlightContainer())
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        SoloRecordLine(record)
                        Text(
                            text = stringResource(R.string.fresh_replay_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (tvMode) TvBackHint()
                        TvModeToggle(checked = tvMode, onChange = { changeTvMode(it) })
                        OutlinedButton(onClick = { showPad = true }) {
                            Text(stringResource(R.string.gamepad_title))
                        }
                    }
                    else -> FreshInfoCard {
                        Text(
                            text = stringResource(R.string.fresh_ready_title, name),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.fresh_ready_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        SoloRecordLine(record)
                        if (tvMode) TvBackHint()
                        TvModeToggle(checked = tvMode, onChange = { changeTvMode(it) })
                        OutlinedButton(onClick = { showPad = true }) {
                            Text(stringResource(R.string.gamepad_title))
                        }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (tvMode) {
            // Mode TV : seul l'écran du jeu (16:9, comme une télé), sans la console ni ses boutons, sur tout l'écran.
            BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                val sceneRatio = Fresh.WIDTH / Fresh.HEIGHT
                val sceneWidth = minOf(maxWidth, maxHeight * sceneRatio)
                Box(modifier = Modifier.align(Alignment.Center).size(sceneWidth, sceneWidth / sceneRatio)) {
                    // Le décor ne change jamais : il se dessine une fois ; ce qui bouge se dessine par-dessus.
                    Canvas(modifier = Modifier.fillMaxSize()) { drawFreshTvScene(labels, measurer) }
                    Canvas(modifier = Modifier.fillMaxSize().semantics { contentDescription = screenDescription }) {
                        drawFreshTvLive(game, revision, sprites, labels, measurer)
                    }
                    messages(Modifier.fillMaxSize())
                }
            }
        } else {
            // La console remplit tout l'écran (hors zones système), sur fond noir, en gardant ses proportions.
            BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
                val ratio = CONSOLE_W / CONSOLE_H
                val consoleWidth = minOf(maxWidth, maxHeight * ratio)
                // Une unité du dessin de la console, en dp.
                val unit = consoleWidth / CONSOLE_W
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(consoleWidth, consoleWidth / ratio)
                ) {
                    // La console et le décor de l'écran ne changent jamais : ils se dessinent une fois.
                    Canvas(modifier = Modifier.fillMaxSize()) { drawFreshConsole(labels, measurer) }
                    // Ce qui bouge : la scène sur l'écran et les boutons enfoncés.
                    Canvas(modifier = Modifier.fillMaxSize().semantics { contentDescription = screenDescription }) {
                        drawFreshLiveConsole(game, revision, pressed, sprites, labels, measurer)
                    }

                    // Le titre sur le cadre de l'écran.
                    FreshLabel(R.string.game_fresh, unit, LCD_X, 28f, LCD_W, 26f, Color.White, bold = true, italic = true)

                    // Les flèches et les deux boutons : ils réagissent dès que le doigt se pose.
                    FreshZone(stringResource(R.string.drive_dir_left), LEFT_ZONE, unit) { down ->
                        press(PRESS_LEFT, down)
                        if (down) key(FRESH_LEFT)
                    }
                    FreshZone(stringResource(R.string.drive_dir_right), RIGHT_ZONE, unit) { down ->
                        press(PRESS_RIGHT, down)
                        if (down) key(FRESH_RIGHT)
                    }
                    FreshZone(stringResource(R.string.drive_button_a), A_ZONE, unit) { down ->
                        press(PRESS_A, down)
                        if (down) key(FRESH_A)
                    }
                    FreshZone(stringResource(R.string.drive_button_b), B_ZONE, unit) { down ->
                        press(PRESS_B, down)
                        if (down) key(FRESH_B)
                    }

                    messages(
                        Modifier
                            .align(Alignment.TopStart)
                            .offset(unit * LCD_X, unit * LCD_Y)
                            .size(unit * LCD_W, unit * LCD_H)
                    )
                }
            }
        }
        RecordSparkles(record)
    }
}

/** La carte posée sur l'écran de la console avant et après une partie : le nom du jeu, ses règles, puis [content]. */
@Composable
private fun BoxScope.FreshInfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        modifier = Modifier.align(Alignment.Center).padding(6.dp)
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.game_fresh),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.width(8.dp))
                RulesButton(
                    gameId = FRESH_ID,
                    gameName = stringResource(R.string.game_fresh),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }
            content()
        }
    }
}

/** Une inscription imprimée : [textId] centré dans une boîte qui commence en ([x], [y]) ; [unit] est une unité du dessin. */
@Composable
private fun BoxScope.FreshLabel(
    textId: Int,
    unit: Dp,
    x: Float,
    y: Float,
    width: Float,
    size: Float,
    color: Color,
    bold: Boolean = false,
    italic: Boolean = false
) {
    Text(
        text = stringResource(textId),
        color = color,
        fontSize = (unit * size).value.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(unit * x, unit * y)
            .width(unit * width)
    )
}

/**
 * Une zone de la console (gauche, haut, largeur, hauteur dans [zone]) qui réagit dès que le doigt se pose (attendre qu'il se lève
 * ferait perdre un temps précieux : on martèle) : [onPress] reçoit vrai à l'appui puis faux au relâchement.
 */
@Composable
private fun BoxScope.FreshZone(
    description: String,
    zone: FloatArray,
    unit: Dp,
    onPress: (Boolean) -> Unit
) {
    val action by rememberUpdatedState(onPress)
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(unit * zone[0], unit * zone[1])
            .size(unit * zone[2], unit * zone[3])
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

// ------------------------------------------------------------------------------------------------------------ dessin de la console

/** Un bouton noir de la croix : ombre, dôme sombre, triangle blanc qui pointe vers [direction] (1 gauche, 2 droite). */
private fun DrawScope.freshPadButton(cx: Float, cy: Float, direction: Int, k: Float) {
    val r = PAD_RADIUS
    drawCircle(Color.Black.copy(alpha = 0.35f), r * k, Offset(cx * k, (cy + 4f) * k))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(PadLight, PadMid, PadDark),
            center = Offset((cx - r * 0.3f) * k, (cy - r * 0.35f) * k),
            radius = r * 1.5f * k
        ),
        radius = r * k,
        center = Offset(cx * k, cy * k)
    )
    val triangle = Path()
    if (direction == 1) {
        triangle.moveTo(cx - 14f, cy); triangle.lineTo(cx + 9f, cy - 13f); triangle.lineTo(cx + 9f, cy + 13f)
    } else {
        triangle.moveTo(cx + 14f, cy); triangle.lineTo(cx - 9f, cy - 13f); triangle.lineTo(cx - 9f, cy + 13f)
    }
    triangle.close()
    withTransform({ scale(k, k, pivot = Offset.Zero) }) { drawPath(triangle, Color.White) }
}

/** Un bouton bombé avec sa lettre blanche : rouge pour A, noir pour B. */
private fun DrawScope.freshCapButton(cx: Float, cy: Float, letter: Int, k: Float) {
    val r = CAP_RADIUS
    drawCircle(Color.Black.copy(alpha = 0.35f), r * k, Offset(cx * k, (cy + 5f) * k))
    drawCircle(
        brush = Brush.radialGradient(
            colors = if (letter == GLYPH_A) listOf(CapLight, ConsoleRed, CapDark) else listOf(PadLight, PadMid, PadDark),
            center = Offset((cx - r * 0.3f) * k, (cy - r * 0.35f) * k),
            radius = r * 1.5f * k
        ),
        radius = r * k,
        center = Offset(cx * k, cy * k)
    )
    driveGlyph(letter, cx * k, cy * k, 28f * k, Color.White)
}

/** La console : la coque blanche, le bandeau rouge et noir, le cadre de l'écran, le décor de l'écran, les boutons. */
private fun DrawScope.drawFreshConsole(labels: FreshLabels, measurer: TextMeasurer) {
    val k = size.width / CONSOLE_W
    val shellRadius = CornerRadius(SHELL_RADIUS * k)
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(ShellTop, ShellMid, ShellLow)),
        size = size,
        cornerRadius = shellRadius
    )
    // Le bandeau rouge et noir du bas, en deux courbes qui se croisent : leurs formes sont les nôtres.
    val shell = Path()
    shell.addRoundRect(RoundRect(0f, 0f, size.width, size.height, shellRadius))
    clipPath(shell) {
        withTransform({
            translate(0f, size.height * 0.66f)
            scale(k, size.height * 0.34f / 200f, pivot = Offset.Zero)
        }) {
            val red = Path()
            red.moveTo(250f, 188f)
            red.cubicTo(420f, 70f, 650f, 20f, 800f, 38f)
            red.cubicTo(660f, 56f, 470f, 118f, 330f, 200f)
            red.close()
            drawPath(red, ConsoleRed)
            val black = Path()
            black.moveTo(330f, 200f)
            black.cubicTo(500f, 128f, 720f, 92f, 930f, 96f)
            black.cubicTo(740f, 124f, 540f, 176f, 410f, 200f)
            black.close()
            drawPath(black, ShellBorder)
        }
    }
    drawRoundRect(ShellBorder, size = size, cornerRadius = shellRadius, style = Stroke(width = 8f * k))

    // Le cadre noir de l'écran, le titre souligné de rouge, puis l'écran lui-même.
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(BezelTop, BezelLow), startY = 22f * k, endY = 598f * k),
        topLeft = Offset(40f * k, 22f * k),
        size = Size(920f * k, 576f * k),
        cornerRadius = CornerRadius(26f * k)
    )
    drawRect(ConsoleRed, Offset(320f * k, 64f * k), Size(360f * k, 4f * k))
    drawRoundRect(
        Color.Black,
        topLeft = Offset((LCD_X - 4f) * k, (LCD_Y - 4f) * k),
        size = Size((LCD_W + 8f) * k, (LCD_H + 8f) * k),
        cornerRadius = CornerRadius(6f * k)
    )
    val lcdLeft = LCD_X * k
    val lcdTop = LCD_Y * k
    clipRect(lcdLeft, lcdTop, lcdLeft + LCD_W * k, lcdTop + LCD_H * k) {
        withTransform({
            translate(lcdLeft, lcdTop)
            scale(k * LCD_SCALE, k * LCD_SCALE, pivot = Offset.Zero)
        }) {
            drawFreshScene(labels, measurer)
        }
    }
    // Les flèches de gauche et de droite, et les boutons A et B.
    freshPadButton(LEFT_X, SIDE_Y, 1, k)
    freshPadButton(RIGHT_X, SIDE_Y, 2, k)
    freshCapButton(A_X, CAP_Y, GLYPH_A, k)
    freshCapButton(B_X, CAP_Y, GLYPH_B, k)
}

/** Ce qui change : la scène sur l'écran (dans son repère de 720 x 405), puis un voile sur les boutons enfoncés. */
private fun DrawScope.drawFreshLiveConsole(
    game: FreshGame,
    @Suppress("UNUSED_PARAMETER") revision: Int,
    pressed: Int,
    sprites: DriveSprites,
    labels: FreshLabels,
    measurer: TextMeasurer
) {
    val k = size.width / CONSOLE_W
    val lcdLeft = LCD_X * k
    val lcdTop = LCD_Y * k
    clipRect(lcdLeft, lcdTop, lcdLeft + LCD_W * k, lcdTop + LCD_H * k) {
        withTransform({
            translate(lcdLeft, lcdTop)
            scale(k * LCD_SCALE, k * LCD_SCALE, pivot = Offset.Zero)
        }) {
            drawFreshLive(game, sprites, labels, measurer)
        }
    }
    val shade = Color.Black.copy(alpha = 0.3f)
    fun round(bit: Int, cx: Float, cy: Float, radius: Float) {
        if (pressed and bit != 0) drawCircle(shade, radius * k, Offset(cx * k, cy * k))
    }
    round(PRESS_LEFT, LEFT_X, SIDE_Y, PAD_RADIUS)
    round(PRESS_RIGHT, RIGHT_X, SIDE_Y, PAD_RADIUS)
    round(PRESS_A, A_X, CAP_Y, CAP_RADIUS)
    round(PRESS_B, B_X, CAP_Y, CAP_RADIUS)
}

private fun findFreshActivity(context: Context): Activity? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun findFreshLifecycleOwner(context: Context): LifecycleOwner? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return null
}

/** Le décor de l'écran seul, sans la console : le mode TV (voir [TvMode]) l'agrandit sur tout l'écran. */
private fun DrawScope.drawFreshTvScene(labels: FreshLabels, measurer: TextMeasurer) {
    val k = size.width / Fresh.WIDTH
    clipRect(0f, 0f, size.width, size.height) {
        withTransform({ scale(k, k, pivot = Offset.Zero) }) {
            drawFreshScene(labels, measurer)
        }
    }
}

/** Ce qui bouge sur l'écran, sans la console ni ses boutons (voir [drawFreshTvScene]). */
private fun DrawScope.drawFreshTvLive(
    game: FreshGame,
    @Suppress("UNUSED_PARAMETER") revision: Int,
    sprites: DriveSprites,
    labels: FreshLabels,
    measurer: TextMeasurer
) {
    val k = size.width / Fresh.WIDTH
    clipRect(0f, 0f, size.width, size.height) {
        withTransform({ scale(k, k, pivot = Offset.Zero) }) {
            drawFreshLive(game, sprites, labels, measurer)
        }
    }
}
