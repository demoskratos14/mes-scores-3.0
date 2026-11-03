package com.aventure.messcores

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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
    // Le record n'existe que seul : à plusieurs, on compare les séries de la table.
    val record = rememberSoloRecord(CIRCLES_ID)

    val game = CirclesGame(players = names.size, streaks = streaks)
    fun nameOf(seat: Int) = names.getOrElse(seat) { (seat + 1).toString() }

    when {
        phase == PHASE_PLAYING && !game.isOver -> CirclesPlayfield(
            name = nameOf(game.current),
            onFinished = { streak ->
                streaks = game.finishRun(streak).streaks
                lastStreak = streak
                if (names.size == 1) record.submit(streak)
                phase = PHASE_RUN_OVER
            }
        )
        phase == PHASE_RUN_OVER -> CirclesCard {
            if (game.isOver) {
                CirclesResults(game = game, names = names, record = record, onNewGame = {
                    streaks = game.newGame().streaks
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
 */
@Composable
private fun CirclesPlayfield(name: String, onFinished: (Int) -> Unit) {
    KeepScreenOn()
    val maxRadius = with(LocalDensity.current) { MAX_RADIUS_DP.dp.toPx() }

    var streak by rememberSaveable { mutableIntStateOf(0) }
    var areaWidth by remember { mutableFloatStateOf(0f) }
    var areaHeight by remember { mutableFloatStateOf(0f) }
    // Change à chaque rond touché : relance l'apparition du rond suivant.
    var circleId by remember { mutableIntStateOf(0) }
    var spot by remember { mutableStateOf(CirclePoint(0f, 0f)) }
    var radius by remember { mutableFloatStateOf(0f) }
    var active by remember { mutableStateOf(false) }
    val finished by rememberUpdatedState(onFinished)

    LaunchedEffect(circleId, areaWidth, areaHeight) {
        if (areaWidth <= 0f || areaHeight <= 0f) return@LaunchedEffect
        spot = Circles.randomCenter(areaWidth, areaHeight, maxRadius, previous = spot.takeIf { circleId > 0 })
        val lifetime = Circles.lifetimeMs(streak)
        radius = maxRadius
        active = true
        val start = withFrameNanos { it }
        var elapsed = 0L
        while (Circles.isAlive(elapsed, lifetime)) {
            radius = Circles.radiusAt(elapsed, lifetime, maxRadius)
            elapsed = (withFrameNanos { it } - start) / 1_000_000L
        }
        // Le rond a disparu sans être touché : la série est finie.
        active = false
        finished(streak)
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
                .pointerInput(maxRadius) {
                    // Dès le doigt posé : attendre qu'il se lève ferait perdre un temps précieux.
                    detectTapGestures(onPress = { tap ->
                        if (active && Circles.isHit(tap.x, tap.y, spot.x, spot.y, radius, maxRadius)) {
                            active = false
                            streak += 1
                            circleId += 1
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
                }
            }
        }
    }
}
