package com.aventure.messcores

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Les modes de jeu (à plusieurs, on choisit au départ ; seul, on joue directement sa série).
private const val MODE_MENU = 0
private const val MODE_SERIES = 1 // chacun sa série : chacun sa propre suite, le meilleur score gagne
private const val MODE_TURNS = 2 // chacun son tour : une seule suite pour tous, une erreur élimine

// Les étapes d'une séance.
private const val PHASE_READY = 0 // on attend que le joueur courant se lance
private const val PHASE_PLAYING = 1 // l'appli montre la suite, puis le joueur la reproduit
private const val PHASE_RUN_OVER = 2 // la série ou le tour est fini (ou toute la partie)

/** Durée d'allumage d'une touche quand le joueur la touche. */
private const val PRESS_FLASH_MS = 200L

/** Pause avant d'ajouter une couleur, une fois la suite reproduite en entier. */
private const val ROUND_PAUSE_MS = 700L

/** Pause après une erreur, le temps de la voir avant la fin de la série ou du tour. */
private const val ERROR_PAUSE_MS = 900L

/** Les quatre touches : vert, rouge, jaune, bleu. */
private val PAD_COLORS = listOf(Color(0xFF2E9E4F), Color(0xFFD93A35), Color(0xFFF2C21B), Color(0xFF2F6FDB))
private val PAD_NAMES = listOf(R.string.simon_pad_green, R.string.simon_pad_red, R.string.simon_pad_yellow, R.string.simon_pad_blue)

private val IntListSaver = listSaver<List<Int>, Int>(save = { it }, restore = { it })

/**
 * Jeu Simon sur un seul téléphone qu'on se passe : l'appli allume une suite de couleurs (avec un son) et le joueur la
 * reproduit de mémoire ; la suite s'allonge d'une couleur et va un peu plus vite à chaque manche réussie. Seul, on
 * joue sa série (voir [SimonRun]). À plusieurs, deux modes au choix : « chacun sa série » (chacun sa suite, le meilleur
 * score gagne, voir [SimonGame]) ou « chacun son tour » (une seule suite pour tous, une erreur élimine, le dernier en
 * jeu gagne, voir [SimonTurns]). Chaque touche porte aussi une forme pour la reconnaître sans les couleurs.
 */
@Composable
internal fun SimonScreen(players: List<String>) {
    val names = players.take(Simon.MAX_PLAYERS).ifEmpty { listOf("1") }
    var mode by rememberSaveable { mutableIntStateOf(if (names.size >= 2) MODE_MENU else MODE_SERIES) }
    when (mode) {
        MODE_SERIES -> SimonSeries(names = names, onChangeMode = if (names.size >= 2) ({ mode = MODE_MENU }) else null)
        MODE_TURNS -> SimonTurnsMode(names = names, onChangeMode = { mode = MODE_MENU })
        else -> SimonCard {
            Text(
                text = stringResource(R.string.simon_mode_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Button(onClick = { mode = MODE_SERIES }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.simon_mode_series))
            }
            Text(
                text = stringResource(R.string.simon_mode_series_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = { mode = MODE_TURNS }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.simon_mode_turns))
            }
            Text(
                text = stringResource(R.string.simon_mode_turns_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Le mode « chacun sa série » : chacun joue sa série à son tour, avec sa propre suite, puis on compare les scores. */
@Composable
private fun SimonSeries(names: List<String>, onChangeMode: (() -> Unit)?) {
    var scores by rememberSaveable(stateSaver = IntListSaver) { mutableStateOf(listOf<Int>()) }
    var phase by rememberSaveable { mutableIntStateOf(PHASE_READY) }
    var lastScore by rememberSaveable { mutableIntStateOf(0) }

    val game = SimonGame(players = names.size, scores = scores)
    fun nameOf(seat: Int) = names.getOrElse(seat) { (seat + 1).toString() }

    when {
        phase == PHASE_PLAYING && !game.isOver -> SimonPlayfield(
            name = nameOf(game.current),
            onFinished = { score ->
                scores = game.finishRun(score).scores
                lastScore = score
                phase = PHASE_RUN_OVER
            }
        )
        phase == PHASE_RUN_OVER -> SimonCard {
            if (game.isOver) {
                SimonResults(game = game, names = names, onChangeMode = onChangeMode, onNewGame = {
                    scores = game.newGame().scores
                    phase = PHASE_READY
                })
            } else {
                Text(
                    text = stringResource(R.string.simon_run_over, nameOf(game.current - 1), lastScore),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Button(onClick = { phase = PHASE_READY }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.simon_next_player))
                }
            }
        }
        else -> SimonCard {
            Text(
                text = stringResource(R.string.simon_ready_title, nameOf(game.current)),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.simon_ready_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (scores.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    scores.forEachIndexed { seat, score ->
                        Text(
                            text = stringResource(R.string.simon_score_entry, nameOf(seat), score),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Button(onClick = { phase = PHASE_PLAYING }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.simon_go))
            }
        }
    }
}

/**
 * Le mode « chacun son tour » : une seule suite pour tous, qui s'allonge à chaque tour réussi. Celui qui se trompe est
 * éliminé et le suivant retente la même suite ; le dernier en jeu gagne. Tout est gardé quand l'écran tourne.
 */
@Composable
private fun SimonTurnsMode(names: List<String>, onChangeMode: () -> Unit) {
    var sequence by rememberSaveable(stateSaver = IntListSaver) { mutableStateOf(SimonRun.start().sequence) }
    var eliminated by rememberSaveable(stateSaver = IntListSaver) { mutableStateOf(listOf<Int>()) }
    var current by rememberSaveable { mutableIntStateOf(0) }
    var phase by rememberSaveable { mutableIntStateOf(PHASE_READY) }
    // La place qui vient d'être éliminée, pour l'annoncer.
    var lastOut by rememberSaveable { mutableIntStateOf(-1) }

    val game = SimonTurns(players = names.size, sequence = sequence, eliminated = eliminated, current = current)
    fun nameOf(seat: Int) = names.getOrElse(seat) { (seat + 1).toString() }
    fun apply(next: SimonTurns) {
        sequence = next.sequence
        eliminated = next.eliminated
        current = next.current
    }

    when {
        phase == PHASE_PLAYING && !game.isOver -> SimonTurnPlayfield(
            name = nameOf(game.current),
            sequence = sequence,
            onResult = { reproduced ->
                if (reproduced) {
                    apply(game.succeed())
                    phase = PHASE_READY
                } else {
                    lastOut = game.current
                    apply(game.fail())
                    phase = PHASE_RUN_OVER
                }
            }
        )
        phase == PHASE_RUN_OVER -> SimonCard {
            if (game.isOver) {
                SimonTurnsResults(game = game, names = names, onChangeMode = onChangeMode, onNewGame = {
                    apply(game.newGame())
                    lastOut = -1
                    phase = PHASE_READY
                })
            } else {
                Text(
                    text = stringResource(R.string.simon_turns_out, nameOf(lastOut)),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Button(onClick = { phase = PHASE_READY }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.simon_next_player))
                }
            }
        }
        else -> SimonCard {
            Text(
                text = stringResource(R.string.simon_ready_title, nameOf(game.current)),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.simon_turns_ready_hint, sequence.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (eliminated.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.simon_turns_alive, game.alive.joinToString(", ") { nameOf(it) }),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Button(onClick = { phase = PHASE_PLAYING }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.simon_go))
            }
        }
    }
}

/** La carte de la séance : le titre du jeu, ses règles, puis [content]. */
@Composable
private fun SimonCard(content: @Composable ColumnScope.() -> Unit) {
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
                        text = stringResource(R.string.game_simon),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    RulesButton(
                        gameId = SIMON_ID,
                        gameName = stringResource(R.string.game_simon),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                content()
            }
        }
    }
}

/** Le bandeau du résultat : le titre, puis [headline] sur fond jaune. */
@Composable
private fun SimonResultsHeader(headline: String) {
    Text(
        text = stringResource(R.string.simon_results_title),
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
}

/** Les boutons de fin de partie : rejouer, et changer de mode quand il y en a plusieurs. */
@Composable
private fun SimonEndButtons(onNewGame: () -> Unit, onChangeMode: (() -> Unit)?) {
    Button(onClick = onNewGame, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.simon_new_game))
    }
    if (onChangeMode != null) {
        OutlinedButton(onClick = onChangeMode, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.simon_change_mode))
        }
    }
}

/** Le résultat de « chacun sa série » : le gagnant (ou les ex æquo), puis tous les scores du meilleur au plus faible. */
@Composable
private fun SimonResults(game: SimonGame, names: List<String>, onChangeMode: (() -> Unit)?, onNewGame: () -> Unit) {
    fun nameOf(seat: Int) = names.getOrElse(seat) { (seat + 1).toString() }
    val winners = game.winners
    val headline = when {
        names.size == 1 -> stringResource(R.string.simon_run_over, nameOf(0), game.best)
        winners.isEmpty() -> stringResource(R.string.simon_nobody)
        winners.size == 1 -> stringResource(R.string.simon_winner, nameOf(winners.single()), game.best)
        else -> stringResource(R.string.simon_tie, winners.joinToString(", ") { nameOf(it) }, game.best)
    }
    SimonResultsHeader(headline)
    if (names.size > 1) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            game.ranking().forEachIndexed { index, seat ->
                Text(
                    text = stringResource(R.string.simon_rank_entry, index + 1, nameOf(seat), game.scores[seat]),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
    SimonEndButtons(onNewGame = onNewGame, onChangeMode = onChangeMode)
}

/** Le résultat de « chacun son tour » : le dernier en jeu, la plus longue suite reproduite, puis le classement. */
@Composable
private fun SimonTurnsResults(game: SimonTurns, names: List<String>, onChangeMode: () -> Unit, onNewGame: () -> Unit) {
    fun nameOf(seat: Int) = names.getOrElse(seat) { (seat + 1).toString() }
    SimonResultsHeader(stringResource(R.string.simon_turns_winner, nameOf(game.winner ?: 0)))
    Text(
        text = stringResource(R.string.simon_turns_reached, game.reached),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        game.ranking().forEachIndexed { index, seat ->
            Text(
                text = stringResource(R.string.simon_turns_rank_entry, index + 1, nameOf(seat)),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
    SimonEndButtons(onNewGame = onNewGame, onChangeMode = onChangeMode)
}

/** Les sons de la séance : libérés quand l'écran de jeu disparaît. */
@Composable
private fun rememberSimonSound(): SimonSound {
    val sound = remember { SimonSound() }
    DisposableEffect(sound) { onDispose { sound.release() } }
    return sound
}

/** Montre la suite : une pause, puis chaque touche s'allume (avec sa note) à la vitesse de la longueur de la suite. */
private suspend fun playSequence(sequence: List<Int>, sound: SimonSound, light: (Int) -> Unit) {
    delay(Simon.PRE_SHOW_MS)
    val flash = Simon.flashMs(sequence.size)
    for (pad in sequence) {
        light(pad)
        sound.play(pad, flash.toInt())
        delay(flash)
        light(-1)
        delay(Simon.GAP_MS)
    }
}

/** Le bandeau du haut : le joueur à gauche, [value] (score ou longueur de la suite) à droite. */
@Composable
private fun SimonHud(name: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = name, style = MaterialTheme.typography.titleMedium)
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

/** Le message sous le bandeau, annoncé par TalkBack quand il change. */
@Composable
private fun SimonStatus(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
    )
}

/** Les quatre touches en deux rangées de deux ; [lit] est la touche allumée (-1 si aucune). */
@Composable
private fun ColumnScope.SimonPadGrid(lit: Int, onPad: (Int) -> Unit) {
    Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (rowStart in 0 until Simon.PADS step 2) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (pad in rowStart until rowStart + 2) {
                    SimonPad(
                        pad = pad,
                        lit = lit == pad,
                        onPress = { onPad(pad) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

/**
 * Une série du joueur [name] (mode « chacun sa série ») : l'appli montre la suite (une couleur au départ), le joueur la
 * reproduit, puis la suite s'allonge d'une couleur. À la première erreur, la série est finie et [onFinished] reçoit le
 * nombre de suites reproduites en entier. Tout est gardé quand l'écran tourne ; la suite est alors remontrée depuis le début.
 */
@Composable
private fun SimonPlayfield(name: String, onFinished: (Int) -> Unit) {
    KeepScreenOn()
    val sound = rememberSimonSound()
    val scope = rememberCoroutineScope()

    var sequence by rememberSaveable(stateSaver = IntListSaver) { mutableStateOf(SimonRun.start().sequence) }
    var progress by rememberSaveable { mutableIntStateOf(0) }
    var completed by rememberSaveable { mutableIntStateOf(0) }
    var failed by rememberSaveable { mutableStateOf(false) }
    // L'appli montre la suite : les touches ne répondent pas.
    var showing by remember { mutableStateOf(true) }
    var lit by remember { mutableIntStateOf(-1) }
    val finished by rememberUpdatedState(onFinished)

    fun run() = SimonRun(sequence, progress, completed, failed)
    fun apply(next: SimonRun) {
        sequence = next.sequence
        progress = next.progress
        completed = next.completed
        failed = next.failed
    }

    // La suite est montrée au départ, puis chaque fois qu'elle s'allonge.
    LaunchedEffect(sequence) {
        if (failed) return@LaunchedEffect
        showing = true
        progress = 0
        playSequence(sequence, sound) { lit = it }
        showing = false
    }

    // Toute la suite est reproduite : une courte pause, puis une couleur de plus.
    val roundDone = !failed && progress >= sequence.size
    LaunchedEffect(roundDone) {
        if (roundDone) {
            showing = true
            delay(ROUND_PAUSE_MS)
            apply(run().nextRound())
        }
    }

    // Une erreur : on la laisse voir un instant avant de finir la série.
    LaunchedEffect(failed) {
        if (failed) {
            showing = true
            sound.error()
            delay(ERROR_PAUSE_MS)
            finished(completed)
        }
    }

    fun onPad(pad: Int) {
        if (showing || failed || run().roundComplete) return
        val next = run().tap(pad)
        apply(next)
        if (!next.failed) sound.play(pad, PRESS_FLASH_MS.toInt())
        scope.launch {
            lit = pad
            delay(PRESS_FLASH_MS)
            lit = -1
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SimonHud(name = name, value = stringResource(R.string.simon_score, completed))
        SimonStatus(
            when {
                failed -> stringResource(R.string.simon_wrong)
                showing && !roundDone -> stringResource(R.string.simon_watch)
                roundDone -> stringResource(R.string.simon_well_done)
                else -> stringResource(R.string.simon_your_turn)
            }
        )
        SimonPadGrid(lit = lit, onPad = ::onPad)
    }
}

/**
 * Le tour du joueur [name] (mode « chacun son tour ») : l'appli montre la [sequence] commune, le joueur la reproduit.
 * [onResult] reçoit `true` s'il l'a reproduite en entier, `false` à la première erreur. Tout est gardé quand l'écran
 * tourne ; la suite est alors remontrée depuis le début.
 */
@Composable
private fun SimonTurnPlayfield(name: String, sequence: List<Int>, onResult: (Boolean) -> Unit) {
    KeepScreenOn()
    val sound = rememberSimonSound()
    val scope = rememberCoroutineScope()

    var progress by rememberSaveable { mutableIntStateOf(0) }
    var failed by rememberSaveable { mutableStateOf(false) }
    var showing by remember { mutableStateOf(true) }
    var lit by remember { mutableIntStateOf(-1) }
    val result by rememberUpdatedState(onResult)
    val done = !failed && progress >= sequence.size

    fun run() = SimonRun(sequence, progress, 0, failed)

    LaunchedEffect(Unit) {
        if (failed || done) return@LaunchedEffect
        showing = true
        progress = 0
        playSequence(sequence, sound) { lit = it }
        showing = false
    }

    LaunchedEffect(done) {
        if (done) {
            showing = true
            delay(ROUND_PAUSE_MS)
            result(true)
        }
    }

    LaunchedEffect(failed) {
        if (failed) {
            showing = true
            sound.error()
            delay(ERROR_PAUSE_MS)
            result(false)
        }
    }

    fun onPad(pad: Int) {
        if (showing || failed || done) return
        val next = run().tap(pad)
        progress = next.progress
        failed = next.failed
        if (!next.failed) sound.play(pad, PRESS_FLASH_MS.toInt())
        scope.launch {
            lit = pad
            delay(PRESS_FLASH_MS)
            lit = -1
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SimonHud(name = name, value = stringResource(R.string.simon_length, sequence.size))
        SimonStatus(
            when {
                failed -> stringResource(R.string.simon_wrong)
                done -> stringResource(R.string.simon_well_done)
                showing -> stringResource(R.string.simon_watch)
                else -> stringResource(R.string.simon_your_turn)
            }
        )
        SimonPadGrid(lit = lit, onPad = ::onPad)
    }
}

/** Une touche colorée, avec sa forme ; elle réagit dès que le doigt se pose. */
@Composable
private fun SimonPad(pad: Int, lit: Boolean, onPress: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(PAD_NAMES[pad])
    val press by rememberUpdatedState(onPress)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(PAD_COLORS[pad].copy(alpha = if (lit) 1f else 0.5f))
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .pointerInput(pad) { detectTapGestures(onPress = { press() }) },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(48.dp)) {
            drawShape(pad, Color.White.copy(alpha = if (lit) 1f else 0.75f))
        }
    }
}

/** La forme de la touche [pad] : rond, triangle, carré, losange. */
private fun DrawScope.drawShape(pad: Int, color: Color) {
    val w = size.width
    val h = size.height
    when (pad) {
        0 -> drawCircle(color, radius = size.minDimension / 2f)
        1 -> drawPath(
            Path().apply {
                moveTo(w / 2f, 0f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            },
            color
        )
        2 -> drawRect(color, topLeft = Offset(w * 0.1f, h * 0.1f), size = Size(w * 0.8f, h * 0.8f))
        else -> drawPath(
            Path().apply {
                moveTo(w / 2f, 0f)
                lineTo(w, h / 2f)
                lineTo(w / 2f, h)
                lineTo(0f, h / 2f)
                close()
            },
            color
        )
    }
}

/** Les sons des touches (une note par touche) et de l'erreur ; sans son si l'appareil ne sait pas en produire. */
private class SimonSound {
    private val generator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 80)
    } catch (e: RuntimeException) {
        null
    }

    fun play(pad: Int, durationMs: Int) {
        generator?.startTone(TONES[pad.coerceIn(0, TONES.lastIndex)], durationMs)
    }

    fun error() {
        generator?.startTone(ToneGenerator.TONE_PROP_NACK, ERROR_TONE_MS)
    }

    fun release() {
        generator?.release()
    }

    private companion object {
        val TONES = intArrayOf(
            ToneGenerator.TONE_DTMF_1,
            ToneGenerator.TONE_DTMF_3,
            ToneGenerator.TONE_DTMF_7,
            ToneGenerator.TONE_DTMF_9
        )
        const val ERROR_TONE_MS = 600
    }
}
