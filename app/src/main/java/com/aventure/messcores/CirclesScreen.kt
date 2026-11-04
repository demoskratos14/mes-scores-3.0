package com.aventure.messcores

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit

// Les étapes de la séance.
private const val PHASE_READY = 0 // on attend que le joueur courant lance sa série
private const val PHASE_PLAYING = 1 // les ronds apparaissent
private const val PHASE_RUN_OVER = 2 // la série du joueur courant est finie (ou toute la partie)

/** Rayon de départ d'un rond, en dp : assez grand pour viser au doigt, assez petit pour qu'on doive bouger. */
private const val MAX_RADIUS_DP = 46

/** Les couleurs des ronds, qui changent à chaque rond touché pour qu'on voie la série avancer. */
private val CIRCLE_COLORS = listOf(
    Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047),
    Color(0xFFFB8C00), Color(0xFF8E24AA), Color(0xFF00ACC1)
)

private val IntListSaver = listSaver<List<Int>, Int>(save = { it }, restore = { it })

/**
 * Jeu des Ronds sur un seul téléphone qu'on se passe : des ronds apparaissent au hasard sur l'écran et rétrécissent
 * jusqu'à disparaître ; il faut toucher chacun avant qu'il s'efface. La série s'arrête au premier rond qui disparaît
 * sans avoir été touché, et la plus longue série de la table gagne (voir [CirclesGame]). Tout est gardé quand l'écran tourne.
 */
@Composable
internal fun CirclesScreen(players: List<String>) {
    val names = players.take(Circles.MAX_PLAYERS).ifEmpty { listOf("1") }
    var streaks by rememberSaveable(stateSaver = IntListSaver) { mutableStateOf(listOf<Int>()) }
    var phase by rememberSaveable { mutableIntStateOf(PHASE_READY) }
    var lastStreak by rememberSaveable { mutableIntStateOf(0) }
    // Vrai quand la dernière série s'est arrêtée sur une tête de mort (niveau difficile) et non sur un rond manqué.
    var lastSkull by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current.applicationContext
    var levelIndex by rememberSaveable { mutableIntStateOf(CirclesPreferences.load(context)) }
    val level = CirclesLevel.fromIndex(levelIndex)
    // Le record n'existe que seul : à plusieurs, on compare les séries de la table. Un par niveau.
    val record = rememberSoloRecord(level.recordId)

    val game = CirclesGame(players = names.size, streaks = streaks)
    fun nameOf(seat: Int) = names.getOrElse(seat) { (seat + 1).toString() }

    when {
        phase == PHASE_PLAYING && !game.isOver -> CirclesPlayfield(
            name = nameOf(game.current),
            level = level,
            onFinished = { streak, hitSkull ->
                streaks = game.finishRun(streak).streaks
                lastStreak = streak
                lastSkull = hitSkull
                if (names.size == 1) record.submit(streak)
                phase = PHASE_RUN_OVER
            }
        )
        phase == PHASE_RUN_OVER -> CirclesCard {
            if (lastSkull) {
                Text(
                    text = stringResource(R.string.circles_skull_hit, nameOf((game.current - 1).coerceAtLeast(0)), lastStreak),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
            if (game.isOver) {
                CirclesResults(game = game, names = names, record = record, onNewGame = {
                    streaks = game.newGame().streaks
                    lastSkull = false
                    record.clearNew()
                    phase = PHASE_READY
                })
            } else {
                Text(
                    text = stringResource(R.string.circles_run_over, nameOf(game.current - 1), lastStreak),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Button(onClick = { phase = PHASE_READY }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.circles_next_player))
                }
            }
        }
        else -> CirclesCard {
            Text(
                text = stringResource(R.string.circles_ready_title, nameOf(game.current)),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.circles_ready_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.circles_level_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CirclesLevel.entries.forEach { option ->
                    FilterChip(
                        selected = option == level,
                        // Pas de changement de niveau en cours de partie : tous les joueurs jouent le même.
                        enabled = streaks.isEmpty(),
                        onClick = {
                            levelIndex = option.ordinal
                            CirclesPreferences.save(context, option.ordinal)
                        },
                        label = { Text(stringResource(levelName(option))) }
                    )
                }
            }
            if (level == CirclesLevel.HARD) {
                Text(
                    text = stringResource(R.string.circles_hard_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (names.size == 1) SoloRecordLine(record)
            if (streaks.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    streaks.forEachIndexed { seat, streak ->
                        Text(
                            text = stringResource(R.string.circles_streak_entry, nameOf(seat), streak),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Button(onClick = { phase = PHASE_PLAYING }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.circles_go))
            }
        }
    }
}

/** Le plateau des cartes de la séance : le titre du jeu, ses règles, puis [content]. */
@Composable
private fun CirclesCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
                        text = stringResource(R.string.game_circles),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    RulesButton(
                        gameId = CIRCLES_ID,
                        gameName = stringResource(R.string.game_circles),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                content()
            }
        }
    }
}

/** Le résultat de la partie : le gagnant (ou les ex æquo), puis toutes les séries de la plus longue à la plus courte. */
@Composable
private fun CirclesResults(game: CirclesGame, names: List<String>, record: SoloRecord, onNewGame: () -> Unit) {
    fun nameOf(seat: Int) = names.getOrElse(seat) { (seat + 1).toString() }
    val winners = game.winners
    val headline = when {
        names.size == 1 -> stringResource(R.string.circles_run_over, nameOf(0), game.best)
        winners.isEmpty() -> stringResource(R.string.circles_nobody)
        winners.size == 1 -> stringResource(R.string.circles_winner, nameOf(winners.single()), game.best)
        else -> stringResource(R.string.circles_tie, winners.joinToString(", ") { nameOf(it) }, game.best)
    }
    Text(
        text = stringResource(R.string.circles_results_title),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() }
    )
    Text(
        text = headline,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = highlightContent(),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(highlightContainer())
            .padding(12.dp)
    )
    if (names.size == 1) SoloRecordLine(record)
    if (names.size > 1) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            game.ranking().forEachIndexed { index, seat ->
                Text(
                    text = stringResource(R.string.circles_rank_entry, index + 1, nameOf(seat), game.streaks[seat]),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
    Button(onClick = onNewGame, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.circles_new_game))
    }
}

/**
 * Une série du joueur [name] : un rond apparaît à un endroit tiré au hasard et rétrécit jusqu'à disparaître. Le toucher
 * le remplace aussitôt par un autre, un peu plus rapide ; s'il disparaît avant, la série est finie et [onFinished]
 * reçoit le nombre de ronds touchés. Un toucher à côté d'un rond ne compte pas.
 *
 * Au niveau [CirclesLevel.HARD], des têtes de mort (de plus en plus nombreuses) apparaissent en même temps que chaque rond
 * et disparaissent avec lui : en toucher une arrête aussitôt la série ([onFinished] reçoit alors `true` en second).
 */
@Composable
private fun CirclesPlayfield(name: String, level: CirclesLevel, onFinished: (streak: Int, hitSkull: Boolean) -> Unit) {
    KeepScreenOn()
    val maxRadius = with(LocalDensity.current) { MAX_RADIUS_DP.dp.toPx() }
    val skullRadius = maxRadius * Circles.SKULL_RADIUS_FRACTION

    var streak by rememberSaveable { mutableIntStateOf(0) }
    var areaWidth by remember { mutableFloatStateOf(0f) }
    var areaHeight by remember { mutableFloatStateOf(0f) }
    // Change à chaque rond touché : relance l'apparition du rond suivant.
    var circleId by remember { mutableIntStateOf(0) }
    var spot by remember { mutableStateOf(CirclePoint(0f, 0f)) }
    var radius by remember { mutableFloatStateOf(0f) }
    var active by remember { mutableStateOf(false) }
    var skulls by remember { mutableStateOf(emptyList<CirclePoint>()) }
    // Vrai dès que la série est finie sur une tête de mort : l'animation du rond ne doit plus la terminer une seconde fois.
    var skullHit by remember { mutableStateOf(false) }
    val finished by rememberUpdatedState(onFinished)

    LaunchedEffect(circleId, areaWidth, areaHeight) {
        if (areaWidth <= 0f || areaHeight <= 0f) return@LaunchedEffect
        val center = Circles.randomCenter(areaWidth, areaHeight, maxRadius, previous = spot.takeIf { circleId > 0 })
        spot = center
        skulls = Circles.randomSkulls(
            width = areaWidth,
            height = areaHeight,
            circle = center,
            maxRadius = maxRadius,
            skullRadius = skullRadius,
            count = Circles.skullCount(level, streak)
        )
        val lifetime = Circles.lifetimeMs(streak)
        radius = maxRadius
        active = true
        val start = withFrameNanos { it }
        var elapsed = 0L
        while (!skullHit && Circles.isAlive(elapsed, lifetime)) {
            radius = Circles.radiusAt(elapsed, lifetime, maxRadius)
            elapsed = (withFrameNanos { it } - start) / 1_000_000L
        }
        // Le rond a disparu sans être touché : la série est finie (sauf si une tête de mort l'a déjà finie).
        if (skullHit) return@LaunchedEffect
        active = false
        skulls = emptyList()
        finished(streak, false)
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.circles_streak, streak),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(cardSurface())
                .onSizeChanged {
                    areaWidth = it.width.toFloat()
                    areaHeight = it.height.toFloat()
                }
                .pointerInput(maxRadius, skullRadius) {
                    // Dès le doigt posé : attendre qu'il se lève ferait perdre un temps précieux.
                    detectTapGestures(onPress = { tap ->
                        if (active) {
                            // La tête de mort passe en premier : un piège touché coûte la série, même si le rond est tout près.
                            if (skulls.any { Circles.isSkullHit(tap.x, tap.y, it, skullRadius) }) {
                                skullHit = true
                                active = false
                                skulls = emptyList()
                                finished(streak, true)
                            } else if (Circles.isHit(tap.x, tap.y, spot.x, spot.y, radius, maxRadius)) {
                                active = false
                                skulls = emptyList()
                                streak += 1
                                circleId += 1
                            }
                        }
                    })
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (active) {
                    val color = CIRCLE_COLORS[streak % CIRCLE_COLORS.size]
                    val middle = Offset(spot.x, spot.y)
                    // Le contour de départ montre où le rond est apparu et de quelle taille il était.
                    drawCircle(color.copy(alpha = 0.3f), radius = maxRadius, center = middle, style = Stroke(width = 2.dp.toPx()))
                    drawCircle(color, radius = radius, center = middle)
                    drawCircle(Color.White.copy(alpha = 0.85f), radius = radius * 0.4f, center = middle)
                    // Au niveau difficile, le fond des têtes de mort prend la couleur du rond, qui change à chaque rond touché.
                    skulls.forEach { drawSkull(Offset(it.x, it.y), skullRadius, background = color) }
                }
            }
        }
    }
}

/** Le nom du niveau [level] dans la liste de choix. */
private fun levelName(level: CirclesLevel): Int = when (level) {
    CirclesLevel.NORMAL -> R.string.circles_level_normal
    CirclesLevel.HARD -> R.string.circles_level_hard
}

/**
 * Une tête de mort de rayon [radius] centrée en [center] : un crâne blanc sur un disque de la couleur [background] (celle du
 * rond en cours, pour prêter à confusion), cerclé de sombre pour que le disque se détache du fond de l'écran. On la
 * reconnaît à son crâne, pas à sa couleur.
 */
private fun DrawScope.drawSkull(center: Offset, radius: Float, background: Color) {
    val dark = Color(0xFF212121)
    val bone = Color(0xFFF5F5F5)
    drawCircle(background, radius = radius, center = center)
    drawCircle(dark, radius = radius, center = center, style = Stroke(width = radius * 0.08f))
    // Le crâne, puis la mâchoire.
    drawCircle(bone, radius = radius * 0.6f, center = Offset(center.x, center.y - radius * 0.12f))
    drawRoundRect(
        color = bone,
        topLeft = Offset(center.x - radius * 0.33f, center.y + radius * 0.2f),
        size = Size(radius * 0.66f, radius * 0.5f),
        cornerRadius = CornerRadius(radius * 0.12f)
    )
    // Les orbites, le nez et les dents.
    drawCircle(dark, radius = radius * 0.16f, center = Offset(center.x - radius * 0.23f, center.y - radius * 0.12f))
    drawCircle(dark, radius = radius * 0.16f, center = Offset(center.x + radius * 0.23f, center.y - radius * 0.12f))
    val nose = Path().apply {
        moveTo(center.x, center.y + radius * 0.02f)
        lineTo(center.x - radius * 0.08f, center.y + radius * 0.2f)
        lineTo(center.x + radius * 0.08f, center.y + radius * 0.2f)
        close()
    }
    drawPath(nose, dark)
    listOf(-0.14f, 0f, 0.14f).forEach { dx ->
        drawLine(
            color = dark,
            start = Offset(center.x + radius * dx, center.y + radius * 0.42f),
            end = Offset(center.x + radius * dx, center.y + radius * 0.7f),
            strokeWidth = radius * 0.05f
        )
    }
}

/** Le niveau choisi, gardé d'une séance à l'autre. */
internal object CirclesPreferences {
    private const val PREFS = "mes_scores_setup"
    private const val KEY_LEVEL = "circles_level"

    fun load(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_LEVEL, CirclesLevel.NORMAL.ordinal)

    fun save(context: Context, levelIndex: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putInt(KEY_LEVEL, levelIndex) }
    }
}
