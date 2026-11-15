package com.aventure.messcores

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
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
private const val DRIVE_PHASE_READY = 0 // on attend que le joueur lance la partie (touche A)
private const val DRIVE_PHASE_PLAYING = 1 // la partie tourne
private const val DRIVE_PHASE_OVER = 2 // trois erreurs : le joueur est licencié

/** Plus long temps d'une image pris en compte : après un à-coup, la partie ne doit pas sauter d'un coup plusieurs secondes. */
private const val DRIVE_MAX_FRAME_MS = 100L

/** Une direction tenue répète le pas : première répétition après 440 ms, puis toutes les 190 ms (comme sur la version web). */
private const val DRIVE_REPEAT_FIRST_MS = -250f
private const val DRIVE_REPEAT_MS = 190f

// Le dessin de la console, en unités de 1 000 x 780 ; l'écran à cristaux liquides (16/9) y mesure 892 de large.
private const val CONSOLE_W = 1000f
private const val CONSOLE_H = 780f
private const val SHELL_RADIUS = 56f
private const val LCD_X = 54f
private const val LCD_Y = 84f
private const val LCD_W = 892f
private const val LCD_SCALE = LCD_W / Drive.WIDTH
private const val LCD_H = Drive.HEIGHT * LCD_SCALE

// Les boutons : la croix (haut, gauche, droite) en bas à gauche, A et B en bas à droite (centre et rayon du dessin).
private const val UP_X = 190f
private const val UP_Y = 650f
private const val LEFT_X = 130f
private const val RIGHT_X = 250f
private const val SIDE_Y = 728f
private const val PAD_RADIUS = 30f
private const val A_X = 840f
private const val A_Y = 646f
private const val B_X = 840f
private const val B_Y = 736f
private const val CAP_RADIUS = 36f

// Les zones tactiles (gauche, haut, largeur, hauteur) : plus larges que les boutons dessinés, et sans se chevaucher.
private val UP_ZONE = floatArrayOf(126f, 598f, 128f, 104f)
private val LEFT_ZONE = floatArrayOf(70f, 702f, 108f, 78f)
private val RIGHT_ZONE = floatArrayOf(202f, 702f, 108f, 78f)
private val A_ZONE = floatArrayOf(784f, 602f, 112f, 88f)
private val B_ZONE = floatArrayOf(784f, 692f, 112f, 88f)

// Les boutons enfoncés, un bit chacun.
private const val PRESS_LEFT = 1
private const val PRESS_RIGHT = 2
private const val PRESS_UP = 4
private const val PRESS_A = 8
private const val PRESS_B = 16

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

/** Garde la partie quand l'écran tourne : son état est rangé dans une liste de nombres (voir [DriveGame.save]). */
private val DriveGameSaver = listSaver<DriveGame, Double>(
    save = { it.save() },
    restore = { DriveGame.restore(it) }
)

/** La touche du jeu à répéter pour les boutons [pressed] enfoncés, ou -1 si aucune direction n'est tenue. */
private fun heldDirection(pressed: Int): Int = when {
    pressed and PRESS_LEFT != 0 -> DRIVE_LEFT
    pressed and PRESS_RIGHT != 0 -> DRIVE_RIGHT
    else -> -1
}

/**
 * Drive inter Longpont en solo : le joueur prépare les commandes d'un drive. Il se déplace dans douze zones « adaddadadada » (a :
 * action, de face ; d : déplacement, de profil). Au bureau, A ou B devant un bac fait tomber dans ce bac le produit au bout du tapis
 * voisin (la lettre du tapis) ; dehors, A ou B remet la commande de cette lettre à la voiture voisine ; la flèche du haut saute (pour
 * éviter la souris) ; A décroche le téléphone dans la dernière zone. Trois erreurs : « Vous êtes licencié ». La console remplit
 * l'écran en paysage ; le bouton retour et l'arrière-plan mettent la partie en pause ; le meilleur score est gardé d'une séance à
 * l'autre. [players] : le joueur dont le nom s'affiche au départ.
 */
@Composable
internal fun DriveScreen(players: List<String>) {
    val name = players.firstOrNull().orEmpty().ifEmpty { "1" }
    val context = LocalContext.current
    val game = rememberSaveable(saver = DriveGameSaver) { DriveGame() }
    var phase by rememberSaveable { mutableIntStateOf(DRIVE_PHASE_READY) }
    var paused by rememberSaveable { mutableStateOf(false) }
    val record = rememberSoloRecord(DRIVE_ID)
    // Change à chaque image ou action : le dessin le lit et se refait, sans recomposer tout l'écran.
    var revision by remember { mutableIntStateOf(0) }
    // Les chiffres lus par TalkBack et par la carte de fin : ils ne recomposent que lorsqu'ils changent.
    var points by remember { mutableIntStateOf(game.points) }
    var errors by remember { mutableIntStateOf(game.errors) }
    var orders by remember { mutableIntStateOf(game.orders) }
    // Les boutons de la console actuellement enfoncés (un bit chacun, voir PRESS_LEFT et les suivants).
    var pressed by remember { mutableIntStateOf(0) }
    val sprites = remember { loadDriveSprites(context) }
    val measurer = rememberTextMeasurer()
    val labels = DriveLabels(
        points = stringResource(R.string.drive_label_points),
        orders = stringResource(R.string.drive_label_orders),
        errors = stringResource(R.string.drive_label_errors),
        stock = stringResource(R.string.drive_label_stock),
        ready = stringResource(R.string.drive_order_ready),
        sign = stringResource(R.string.drive_sign)
    )
    val screenDescription = stringResource(R.string.drive_score, points) + ", " +
        stringResource(R.string.drive_errors, errors, Drive.MAX_ERRORS)

    // La console se joue à l'horizontale : l'écran passe en paysage tant que le jeu est ouvert.
    DisposableEffect(context) {
        val activity = findDriveActivity(context)
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { if (previous != null) activity?.requestedOrientation = previous }
    }

    // La partie se met en pause quand l'appli passe en arrière-plan (et donc aussi quand l'écran tourne).
    val lifecycleOwner = remember(context) { findDriveLifecycleOwner(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && phase == DRIVE_PHASE_PLAYING) paused = true
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    // Le bouton retour met la partie en pause plutôt que de la quitter par mégarde.
    BackHandler(enabled = phase == DRIVE_PHASE_PLAYING && !paused) { paused = true }
    if (phase == DRIVE_PHASE_PLAYING && !paused) KeepScreenOn()

    fun sync() {
        revision++
        points = game.points
        errors = game.errors
        orders = game.orders
    }

    fun press(bit: Int, down: Boolean) {
        pressed = if (down) pressed or bit else pressed and bit.inv()
    }

    /** Lance une partie neuve. */
    fun startGame() {
        record.clearNew()
        game.reset()
        paused = false
        phase = DRIVE_PHASE_PLAYING
        sync()
    }

    /** Une touche du jeu pressée : en jeu elle agit ; sinon A lance une partie (après le message de fin, une fois qu'il a été lu). */
    fun key(key: Int) {
        if (phase == DRIVE_PHASE_PLAYING && !paused) {
            game.press(key)
            sync()
        } else if (key == DRIVE_A && !paused && (phase == DRIVE_PHASE_READY || game.canRestart)) {
            startGame()
        }
    }

    LaunchedEffect(phase, paused) {
        if (phase == DRIVE_PHASE_READY || paused) return@LaunchedEffect
        var last = withFrameNanos { it }
        var heldMs = DRIVE_REPEAT_FIRST_MS
        var heldKey = -1
        while (true) {
            val now = withFrameNanos { it }
            val elapsed = ((now - last) / 1_000_000L).coerceIn(0L, DRIVE_MAX_FRAME_MS).toFloat()
            last = now
            game.tick(elapsed)
            if (phase == DRIVE_PHASE_PLAYING) {
                // Une direction tenue répète le pas ; le premier pas a déjà été fait à l'appui.
                val held = heldDirection(pressed)
                if (held != heldKey) {
                    heldKey = held
                    heldMs = DRIVE_REPEAT_FIRST_MS
                } else if (held >= 0) {
                    heldMs += elapsed
                    if (heldMs >= DRIVE_REPEAT_MS) {
                        heldMs = 0f
                        game.press(held)
                    }
                }
                if (game.over) {
                    record.submit(game.points)
                    phase = DRIVE_PHASE_OVER
                }
            }
            sync()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                Canvas(modifier = Modifier.fillMaxSize()) { drawDriveConsole(labels, measurer) }
                // Ce qui bouge : la scène sur l'écran et les boutons enfoncés.
                Canvas(modifier = Modifier.fillMaxSize().semantics { contentDescription = screenDescription }) {
                    drawDriveLiveConsole(game, revision, pressed, sprites, labels, measurer)
                }

                // Le titre sur le cadre de l'écran et l'inscription de la croix.
                DriveLabel(R.string.game_drive, unit, LCD_X, 28f, LCD_W, 26f, Color.White, bold = true, italic = true)
                DriveLabel(R.string.drive_label_pad, unit, UP_X - 60f, 683f, 120f, 13f, Color(0xFF14161A).copy(alpha = 0.7f), bold = true)

                // La croix et les deux boutons : ils réagissent dès que le doigt se pose.
                DriveZone(stringResource(R.string.drive_button_up), UP_ZONE, unit) { down ->
                    press(PRESS_UP, down)
                    if (down) key(DRIVE_UP)
                }
                DriveZone(stringResource(R.string.drive_dir_left), LEFT_ZONE, unit) { down ->
                    press(PRESS_LEFT, down)
                    if (down) key(DRIVE_LEFT)
                }
                DriveZone(stringResource(R.string.drive_dir_right), RIGHT_ZONE, unit) { down ->
                    press(PRESS_RIGHT, down)
                    if (down) key(DRIVE_RIGHT)
                }
                DriveZone(stringResource(R.string.drive_button_a), A_ZONE, unit) { down ->
                    press(PRESS_A, down)
                    if (down) key(DRIVE_A)
                }
                DriveZone(stringResource(R.string.drive_button_b), B_ZONE, unit) { down ->
                    press(PRESS_B, down)
                    if (down) key(DRIVE_B)
                }

                // Les messages se posent sur l'écran à cristaux liquides, pas sur le reste de la console.
                if (phase != DRIVE_PHASE_PLAYING || paused) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(unit * LCD_X, unit * LCD_Y)
                            .size(unit * LCD_W, unit * LCD_H)
                    ) {
                        when {
                            phase == DRIVE_PHASE_PLAYING -> Column(
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
                                Button(onClick = { paused = false }) {
                                    Text(stringResource(R.string.drive_resume))
                                }
                            }
                            phase == DRIVE_PHASE_OVER -> DriveInfoCard {
                                Text(
                                    text = stringResource(R.string.drive_over_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    fontStyle = FontStyle.Italic,
                                    color = Color(0xFFE2001A),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = stringResource(R.string.drive_over_summary, errors, orders, points),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = stringResource(R.string.drive_score, points),
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
                                    text = stringResource(R.string.drive_replay_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                            else -> DriveInfoCard {
                                Text(
                                    text = stringResource(R.string.drive_ready_title, name),
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = stringResource(R.string.drive_ready_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                SoloRecordLine(record)
                            }
                        }
                    }
                }
            }
        }
        RecordSparkles(record)
    }
}

/** La carte posée sur l'écran de la console avant et après une partie : le nom du jeu, ses règles, puis [content]. */
@Composable
private fun BoxScope.DriveInfoCard(content: @Composable ColumnScope.() -> Unit) {
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
                    text = stringResource(R.string.game_drive),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.width(8.dp))
                RulesButton(
                    gameId = DRIVE_ID,
                    gameName = stringResource(R.string.game_drive),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }
            content()
        }
    }
}

/** Une inscription imprimée : [textId] centré dans une boîte qui commence en ([x], [y]) ; [unit] est une unité du dessin. */
@Composable
private fun BoxScope.DriveLabel(
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
 * ferait perdre un temps précieux) : [onPress] reçoit vrai à l'appui puis faux au relâchement.
 */
@Composable
private fun BoxScope.DriveZone(
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

/** Un bouton noir de la croix : ombre, dôme sombre, triangle blanc qui pointe vers [direction] (0 haut, 1 gauche, 2 droite). */
private fun DrawScope.padButton(cx: Float, cy: Float, direction: Int, k: Float) {
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
    when (direction) {
        0 -> { triangle.moveTo(cx, cy - 12f); triangle.lineTo(cx - 11f, cy + 8f); triangle.lineTo(cx + 11f, cy + 8f) }
        1 -> { triangle.moveTo(cx - 12f, cy); triangle.lineTo(cx + 8f, cy - 11f); triangle.lineTo(cx + 8f, cy + 11f) }
        else -> { triangle.moveTo(cx + 12f, cy); triangle.lineTo(cx - 8f, cy - 11f); triangle.lineTo(cx - 8f, cy + 11f) }
    }
    triangle.close()
    withTransform({ scale(k, k, pivot = Offset.Zero) }) { drawPath(triangle, Color.White) }
}

/** Un bouton rouge bombé (A ou B) avec sa lettre blanche. */
private fun DrawScope.capButton(cx: Float, cy: Float, letter: Int, k: Float) {
    val r = CAP_RADIUS
    drawCircle(Color.Black.copy(alpha = 0.35f), r * k, Offset(cx * k, (cy + 5f) * k))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(CapLight, ConsoleRed, CapDark),
            center = Offset((cx - r * 0.3f) * k, (cy - r * 0.35f) * k),
            radius = r * 1.5f * k
        ),
        radius = r * k,
        center = Offset(cx * k, cy * k)
    )
    driveGlyph(letter, cx * k, cy * k, 26f * k, Color.White)
}

/** La console : la coque blanche, le bandeau rouge et noir, le cadre de l'écran, le décor de l'écran, les boutons. */
private fun DrawScope.drawDriveConsole(labels: DriveLabels, measurer: TextMeasurer) {
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
            drawDriveScene(labels, measurer)
        }
    }
    // La croix de gauche (haut, gauche, droite) et les boutons A et B.
    padButton(UP_X, UP_Y, 0, k)
    padButton(LEFT_X, SIDE_Y, 1, k)
    padButton(RIGHT_X, SIDE_Y, 2, k)
    capButton(A_X, A_Y, GLYPH_A, k)
    capButton(B_X, B_Y, GLYPH_B, k)
}

/** Ce qui change : la scène sur l'écran (dans son repère de 720 x 405), puis un voile sur les boutons enfoncés. */
private fun DrawScope.drawDriveLiveConsole(
    game: DriveGame,
    @Suppress("UNUSED_PARAMETER") revision: Int,
    pressed: Int,
    sprites: DriveSprites,
    labels: DriveLabels,
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
            drawDriveLive(game, sprites, labels, measurer)
        }
    }
    val shade = Color.Black.copy(alpha = 0.3f)
    fun round(bit: Int, cx: Float, cy: Float, radius: Float) {
        if (pressed and bit != 0) drawCircle(shade, radius * k, Offset(cx * k, cy * k))
    }
    round(PRESS_UP, UP_X, UP_Y, PAD_RADIUS)
    round(PRESS_LEFT, LEFT_X, SIDE_Y, PAD_RADIUS)
    round(PRESS_RIGHT, RIGHT_X, SIDE_Y, PAD_RADIUS)
    round(PRESS_A, A_X, A_Y, CAP_RADIUS)
    round(PRESS_B, B_X, B_Y, CAP_RADIUS)
}

private fun findDriveActivity(context: Context): Activity? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun findDriveLifecycleOwner(context: Context): LifecycleOwner? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return null
}
