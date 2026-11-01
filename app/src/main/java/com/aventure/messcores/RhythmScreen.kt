package com.aventure.messcores

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class RhythmStage { MENU, ANALYZING, PLAYING, RESULT }

private enum class RhythmError { NONE, UNREADABLE, TOO_LONG, NO_NOTES }

/** Couleurs des pistes, façon manche de guitare ; en mode daltonien, des teintes qui se distinguent par leur clarté. */
private fun laneColors(colorBlind: Boolean): List<Color> =
    if (colorBlind) {
        listOf(Color(0xFF0072B2), Color(0xFFE69F00), Color(0xFFF0E442), Color(0xFFCC79A7), Color(0xFF56B4E9))
    } else {
        listOf(Color(0xFF43A047), Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF1E88E5), Color(0xFFFB8C00))
    }

/**
 * Jeu de rythme, seul sur son téléphone : le joueur choisit une chanson de son téléphone, l'appli l'écoute en arrière-plan
 * (voir [RhythmAudioDecoder] et [RhythmAnalyzer]) pour placer des notes sur les attaques des graves aux aigus, puis les
 * notes descendent en rythme sur des pistes et il faut les toucher quand elles passent la ligne, en maintenant les notes longues.
 * [players] : le joueur dont le nom s'affiche sur le résultat.
 */
@Composable
internal fun RhythmScreen(players: List<String>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colorBlind = LocalColorBlindMode.current

    var stage by remember { mutableStateOf(RhythmStage.MENU) }
    var songUri by rememberSaveable { mutableStateOf<String?>(null) }
    var songName by rememberSaveable { mutableStateOf<String?>(null) }
    var difficultyIndex by rememberSaveable { mutableIntStateOf(RhythmPreferences.loadDifficulty(context)) }
    var offsetMs by rememberSaveable { mutableIntStateOf(RhythmPreferences.loadOffset(context)) }
    var error by remember { mutableStateOf(RhythmError.NONE) }
    var progress by remember { mutableFloatStateOf(0f) }
    var spectrogram by remember { mutableStateOf<Spectrogram?>(null) }
    var session by remember { mutableStateOf<RhythmSession?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // Le fichier reste lisible tant que l'appli est ouverte, c'est suffisant.
            }
            songUri = uri.toString()
            songName = RhythmAudioDecoder.displayName(context, uri)
            spectrogram = null
            error = RhythmError.NONE
        }
    }

    val startGame: () -> Unit = {
        val uriString = songUri
        if (uriString != null) {
            val uri = uriString.toUri()
            val difficulty = RhythmDifficulty.entries[difficultyIndex]
            RhythmPreferences.save(context, difficultyIndex, offsetMs)
            session?.close()
            session = null
            job?.cancel()
            job = scope.launch {
                error = RhythmError.NONE
                progress = 0f
                stage = RhythmStage.ANALYZING
                try {
                    val spec = spectrogram ?: withContext(Dispatchers.Default) {
                        val audio = RhythmAudioDecoder.decode(context, uri, { progress = it * 0.6f }, { ensureActive() })
                        RhythmAnalyzer.spectrogram(audio.samples, audio.sampleRate, { progress = 0.6f + it * 0.4f }, { ensureActive() })
                    }.also { spectrogram = it }
                    val chart = withContext(Dispatchers.Default) { RhythmAnalyzer.buildChart(spec, difficulty) }
                    if (chart.notes.isEmpty()) {
                        error = RhythmError.NO_NOTES
                        stage = RhythmStage.MENU
                        return@launch
                    }
                    val player = withContext(Dispatchers.IO) { createRhythmPlayer(context, uri) }
                    session = RhythmSession(player, chart, offsetMs, SystemClock.uptimeMillis())
                    stage = RhythmStage.PLAYING
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RhythmTooLongException) {
                    error = RhythmError.TOO_LONG
                    stage = RhythmStage.MENU
                } catch (e: Exception) {
                    // Fichier illisible, pas un son, décodeur absent ou en erreur : le même message pour tous.
                    error = RhythmError.UNREADABLE
                    stage = RhythmStage.MENU
                }
            }
        }
    }

    val backToMenu: () -> Unit = {
        job?.cancel()
        session?.close()
        session = null
        stage = RhythmStage.MENU
    }

    // La musique et le calcul s'arrêtent quand on quitte l'écran.
    DisposableEffect(Unit) {
        onDispose {
            job?.cancel()
            session?.close()
        }
    }

    // La partie se met en pause quand l'appli passe en arrière-plan.
    val lifecycleOwner = remember(context) { findLifecycleOwner(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) session?.pause(SystemClock.uptimeMillis())
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    BackHandler(enabled = stage != RhythmStage.MENU) {
        when (stage) {
            RhythmStage.PLAYING -> session?.let { current ->
                if (current.stage == SessionStage.PAUSED) current.resume(SystemClock.uptimeMillis())
                else current.pause(SystemClock.uptimeMillis())
            }
            else -> backToMenu()
        }
    }

    when (stage) {
        RhythmStage.MENU -> RhythmMenu(
            hasSong = songUri != null,
            songName = songName,
            difficultyIndex = difficultyIndex,
            offsetMs = offsetMs,
            error = error,
            onPickSong = { picker.launch(arrayOf("audio/*")) },
            onDifficulty = { difficultyIndex = it },
            onOffset = { offsetMs = it.coerceIn(-RhythmPreferences.MAX_OFFSET_MS, RhythmPreferences.MAX_OFFSET_MS) },
            onPlay = startGame
        )
        RhythmStage.ANALYZING -> RhythmAnalyzing(progress = progress, onCancel = backToMenu)
        RhythmStage.PLAYING -> session?.let { current ->
            RhythmPlayfield(
                session = current,
                colorBlind = colorBlind,
                onFinished = { stage = RhythmStage.RESULT },
                onQuit = backToMenu
            )
        }
        RhythmStage.RESULT -> session?.let { current ->
            RhythmResult(
                run = current.run,
                playerName = players.firstOrNull().orEmpty(),
                onReplay = startGame,
                onMenu = backToMenu
            )
        }
    }
}

/** Le lecteur audio de la chanson, prêt à jouer. Lève une exception si le fichier ne se lit pas. */
private fun createRhythmPlayer(context: Context, uri: Uri): MediaPlayer {
    val player = MediaPlayer()
    try {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        player.setDataSource(context, uri)
        player.prepare()
        return player
    } catch (e: Exception) {
        player.release()
        throw e
    }
}

private fun findLifecycleOwner(context: Context): LifecycleOwner? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return null
}

@Composable
private fun RhythmMenu(
    hasSong: Boolean,
    songName: String?,
    difficultyIndex: Int,
    offsetMs: Int,
    error: RhythmError,
    onPickSong: () -> Unit,
    onDifficulty: (Int) -> Unit,
    onOffset: (Int) -> Unit,
    onPlay: () -> Unit
) {
    val cardColors = CardDefaults.cardColors(containerColor = cardSurface())
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.game_rhythm),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.width(12.dp))
            RulesButton(
                gameId = RHYTHM_ID,
                gameName = stringResource(R.string.game_rhythm),
                contentColor = MaterialTheme.colorScheme.primary
            )
        }

        Card(colors = cardColors, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (hasSong) stringResource(R.string.rhythm_song_label, songName ?: "?") else stringResource(R.string.rhythm_no_song),
                    style = MaterialTheme.typography.titleMedium
                )
                if (hasSong) {
                    OutlinedButton(onClick = onPickSong) { Text(stringResource(R.string.rhythm_change_song)) }
                } else {
                    Button(onClick = onPickSong) { Text(stringResource(R.string.rhythm_pick_song)) }
                }
            }
        }

        Card(colors = cardColors, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.rhythm_difficulty_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RhythmDifficulty.entries.forEach { difficulty ->
                        FilterChip(
                            selected = difficulty.ordinal == difficultyIndex,
                            onClick = { onDifficulty(difficulty.ordinal) },
                            label = { Text(stringResource(difficultyName(difficulty))) }
                        )
                    }
                }
                val lanes = RhythmDifficulty.entries[difficultyIndex].lanes
                Text(
                    text = pluralStringResource(R.plurals.rhythm_lanes_count, lanes, lanes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(colors = cardColors, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.rhythm_offset_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { onOffset(offsetMs - RhythmPreferences.OFFSET_STEP_MS) }) {
                        Text(stringResource(R.string.rhythm_offset_less))
                    }
                    Text(stringResource(R.string.rhythm_offset_value, offsetMs), style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { onOffset(offsetMs + RhythmPreferences.OFFSET_STEP_MS) }) {
                        Text(stringResource(R.string.rhythm_offset_more))
                    }
                }
                Text(
                    text = stringResource(R.string.rhythm_offset_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val errorText = when (error) {
            RhythmError.UNREADABLE -> R.string.rhythm_error_unreadable
            RhythmError.TOO_LONG -> R.string.rhythm_error_too_long
            RhythmError.NO_NOTES -> R.string.rhythm_error_no_notes
            RhythmError.NONE -> null
        }
        if (errorText != null) {
            Text(text = stringResource(errorText), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Button(onClick = onPlay, enabled = hasSong, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.rhythm_play))
        }
    }
}

private fun difficultyName(difficulty: RhythmDifficulty): Int = when (difficulty) {
    RhythmDifficulty.EASY -> R.string.rhythm_easy
    RhythmDifficulty.MEDIUM -> R.string.rhythm_medium
    RhythmDifficulty.HARD -> R.string.rhythm_hard
}

@Composable
private fun RhythmAnalyzing(progress: Float, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.rhythm_analyzing),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.rhythm_analyzing_hint),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
    }
}

/** L'écran de jeu : les pistes, les notes qui descendent, le score, et la pause. */
@Composable
private fun RhythmPlayfield(session: RhythmSession, colorBlind: Boolean, onFinished: () -> Unit, onQuit: () -> Unit) {
    KeepScreenOn()
    val lanes = session.chart.lanes
    val colors = remember(colorBlind) { laneColors(colorBlind) }
    var pressedMask by remember { mutableIntStateOf(0) }
    val currentOnFinished by rememberUpdatedState(onFinished)

    // Une image après l'autre, la partie avance.
    LaunchedEffect(session) {
        while (isActive) {
            withFrameNanos { }
            session.tick(SystemClock.uptimeMillis())
            if (session.stage == SessionStage.FINISHED) {
                currentOnFinished()
                break
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF10131A))
            .pointerInput(session) {
                // Chaque doigt qui se pose touche la piste sous lui ; plusieurs doigts à la fois pour les accords.
                awaitPointerEventScope {
                    val laneOf = HashMap<PointerId, Int>()
                    while (true) {
                        val event = awaitPointerEvent()
                        for (change in event.changes) {
                            if (change.pressed && !change.previousPressed && !change.isConsumed) {
                                val lane = (change.position.x / size.width * lanes).toInt().coerceIn(0, lanes - 1)
                                laneOf[change.id] = lane
                                pressedMask = pressedMask or (1 shl lane)
                                session.press(lane, change.uptimeMillis)
                            } else if (!change.pressed && change.previousPressed) {
                                val lane = laneOf.remove(change.id)
                                if (lane != null) {
                                    pressedMask = pressedMask and (1 shl lane).inv()
                                    session.release(lane, change.uptimeMillis)
                                }
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawPlayfield(session, colors, pressedMask, session.songMs)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.rhythm_score, session.score),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                if (session.combo >= 2) {
                    Text(
                        text = stringResource(R.string.rhythm_combo, session.combo) + " " + stringResource(R.string.rhythm_multiplier, session.multiplier),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFFFD54F)
                    )
                }
            }
            IconButton(onClick = { session.pause(SystemClock.uptimeMillis()) }) {
                Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.rhythm_pause), tint = Color.White)
            }
        }

        JudgementLabel(session = session, modifier = Modifier.align(Alignment.Center))

        if (session.stage == SessionStage.PAUSED) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.rhythm_pause),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        modifier = Modifier.semantics { heading() }
                    )
                    Button(onClick = { session.resume(SystemClock.uptimeMillis()) }) {
                        Text(stringResource(R.string.quit_continue))
                    }
                    OutlinedButton(onClick = onQuit) {
                        Text(stringResource(R.string.mm_quit_confirm), color = Color.White)
                    }
                }
            }
        }
    }
}

/** « Parfait ! », « Bien » ou « Raté » juste après une note, pendant un instant. */
@Composable
private fun JudgementLabel(session: RhythmSession, modifier: Modifier = Modifier) {
    val judgement = session.judgement ?: return
    val age = session.songMs - session.judgementMs
    if (age !in 0..JUDGEMENT_SHOWN_MS) return
    val (text, color) = when (judgement) {
        Judgement.PERFECT -> R.string.rhythm_perfect to Color(0xFF69F0AE)
        Judgement.GOOD -> R.string.rhythm_good to Color(0xFFFFD54F)
        Judgement.MISS -> R.string.rhythm_miss to Color(0xFFFF8A80)
    }
    Text(
        text = stringResource(text),
        color = color.copy(alpha = 1f - age.toFloat() / JUDGEMENT_SHOWN_MS * 0.7f),
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

private const val JUDGEMENT_SHOWN_MS = 450

/** Dessine les pistes (qui s'éclairent avec la musique), la ligne de frappe et les notes. */
private fun DrawScope.drawPlayfield(session: RhythmSession, colors: List<Color>, pressedMask: Int, nowMs: Int) {
    val chart = session.chart
    val lanes = chart.lanes
    val laneWidth = size.width / lanes
    val hitY = size.height * 0.8f
    val approach = chart.difficulty.approachMs.toFloat()
    val radius = min(laneWidth * 0.34f, size.height * 0.045f)

    // Les pistes pulsent avec la musique, comme les barres d'un égaliseur.
    for (lane in 0 until lanes) {
        val level = chart.levelAt(lane, nowMs)
        drawRect(
            color = colors[lane].copy(alpha = 0.05f + 0.3f * level),
            topLeft = Offset(lane * laneWidth, 0f),
            size = Size(laneWidth, size.height)
        )
        if (lane > 0) {
            drawLine(Color.White.copy(alpha = 0.12f), Offset(lane * laneWidth, 0f), Offset(lane * laneWidth, size.height), strokeWidth = 2f)
        }
    }
    drawLine(Color.White.copy(alpha = 0.55f), Offset(0f, hitY), Offset(size.width, hitY), strokeWidth = 3f)
    for (lane in 0 until lanes) {
        val center = Offset(lane * laneWidth + laneWidth / 2, hitY)
        val pressed = pressedMask and (1 shl lane) != 0
        drawNoteShape(lane, center, radius * 1.1f, colors[lane], filled = pressed, alpha = if (pressed) 0.9f else 0.8f)
    }

    val run = session.run
    for ((index, note) in run.notes.withIndex()) {
        if (note.timeMs - nowMs > approach) break
        if (note.endMs < nowMs - 300) continue
        val state = run.states[index]
        val x = note.lane * laneWidth + laneWidth / 2
        val headY = hitY - (note.timeMs - nowMs) / approach * hitY
        val color = colors[note.lane]
        val dead = state == NoteState.MISSED || state == NoteState.BROKEN
        if (note.isHold && state != NoteState.DONE) {
            val tailTop = hitY - (note.endMs - nowMs) / approach * hitY
            val tailBottom = if (state == NoteState.HOLDING) hitY else headY
            if (tailBottom > tailTop) {
                val tailWidth = laneWidth * 0.26f
                drawRoundRect(
                    color = if (dead) Color.Gray.copy(alpha = 0.35f) else color.copy(alpha = if (state == NoteState.HOLDING) 0.95f else 0.6f),
                    topLeft = Offset(x - tailWidth / 2, tailTop),
                    size = Size(tailWidth, tailBottom - tailTop),
                    cornerRadius = CornerRadius(tailWidth / 2)
                )
            }
        }
        when (state) {
            NoteState.PENDING -> drawNoteShape(note.lane, Offset(x, headY), radius, color, filled = true)
            NoteState.HOLDING -> drawNoteShape(note.lane, Offset(x, hitY), radius * 1.15f, color, filled = true)
            NoteState.MISSED -> if (headY - radius < size.height) {
                drawNoteShape(note.lane, Offset(x, headY), radius, Color.Gray, filled = true, alpha = 0.45f)
            }
            else -> Unit
        }
    }
}

/**
 * Une note : chaque piste a sa forme (rond, carré, triangle, losange, hexagone) en plus de sa couleur, pour qu'on les
 * distingue sans voir les couleurs.
 */
private fun DrawScope.drawNoteShape(lane: Int, center: Offset, radius: Float, color: Color, filled: Boolean, alpha: Float = 1f) {
    val style = if (filled) Fill else Stroke(width = radius * 0.22f)
    when (lane % 5) {
        0 -> drawCircle(color = color, radius = radius, center = center, alpha = alpha, style = style)
        1 -> drawRoundRect(
            color = color,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            cornerRadius = CornerRadius(radius * 0.25f),
            alpha = alpha,
            style = style
        )
        2 -> drawPath(polygon(center, radius * 1.15f, 3, -90f), color, alpha, style)
        3 -> drawPath(polygon(center, radius * 1.2f, 4, 0f), color, alpha, style)
        else -> drawPath(polygon(center, radius * 1.1f, 6, 0f), color, alpha, style)
    }
    if (filled) {
        // Un contour clair pour bien détacher la note du fond coloré.
        val outline = Stroke(width = radius * 0.12f)
        when (lane % 5) {
            0 -> drawCircle(color = Color.White, radius = radius, center = center, alpha = alpha * 0.8f, style = outline)
            1 -> drawRoundRect(
                color = Color.White,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                cornerRadius = CornerRadius(radius * 0.25f),
                alpha = alpha * 0.8f,
                style = outline
            )
            2 -> drawPath(polygon(center, radius * 1.15f, 3, -90f), Color.White, alpha * 0.8f, outline)
            3 -> drawPath(polygon(center, radius * 1.2f, 4, 0f), Color.White, alpha * 0.8f, outline)
            else -> drawPath(polygon(center, radius * 1.1f, 6, 0f), Color.White, alpha * 0.8f, outline)
        }
    }
}

/** Un polygone régulier à [sides] côtés, dont le premier sommet est à l'angle [startDegrees]. */
private fun polygon(center: Offset, radius: Float, sides: Int, startDegrees: Float): Path {
    val path = Path()
    for (i in 0 until sides) {
        val angle = Math.toRadians((startDegrees + 360.0 * i / sides))
        val x = center.x + radius * cos(angle).toFloat()
        val y = center.y + radius * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

@Composable
private fun RhythmResult(run: RhythmRun, playerName: String, onReplay: () -> Unit, onMenu: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.rhythm_result_title, playerName),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = run.grade.name,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(stringResource(R.string.rhythm_score, run.score), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.rhythm_result_accuracy, (run.accuracy * 100).toInt()),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(stringResource(R.string.rhythm_result_perfect, run.perfect), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.rhythm_result_good, run.good), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.rhythm_result_missed, run.missed), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.rhythm_result_holds, run.holdsDone, run.holdsDone + run.holdsBroken + holdsMissed(run)),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(stringResource(R.string.rhythm_result_combo, run.maxCombo), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Button(onClick = onReplay, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.rhythm_replay)) }
        OutlinedButton(onClick = onMenu, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.rhythm_back_to_menu)) }
    }
}

/** Nombre de notes à maintenir ratées d'entrée (jamais touchées). */
private fun holdsMissed(run: RhythmRun): Int =
    run.notes.indices.count { run.notes[it].isHold && run.states[it] == NoteState.MISSED }
