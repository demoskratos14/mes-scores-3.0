package com.aventure.messcores

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Chaque état d'une lettre a sa couleur, mais aussi un style (barrée si elle n'est pas dans le mot) et sa
// description pour TalkBack : on ne doit jamais avoir besoin de voir la couleur pour jouer.

/** Le clavier est en 4 lignes de 7 lettres au plus : les touches restent assez larges pour le doigt. */
private const val KEYS_PER_ROW = 7

/**
 * Pendu à deux : réglages, choix du mot, lettres, résultat de chaque manche. Sur le même téléphone (il passe de main
 * en main, l'écran se cache pendant qu'on choisit le mot) ou chacun sur son téléphone (voir [HangmanOnline]). Les
 * manches s'enchaînent, les rôles s'échangent et le score de la séance reste affiché.
 */
@Composable
internal fun HangmanScreen(
    viewModel: HangmanViewModel,
    online: HangmanOnline,
    onExit: () -> Unit,
    onHostOnline: () -> Unit
) {
    val rules = viewModel.rules
    val game = viewModel.game
    KeepScreenOn()

    var showQuit by rememberSaveable { mutableStateOf(false) }
    BackHandler {
        if (online.active) {
            showQuit = true
        } else if (game == null) {
            viewModel.close()
            onExit()
        } else {
            showQuit = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = rules?.name ?: stringResource(R.string.game_hangman),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .semantics { heading() }
            )
            if (rules != null) {
                Spacer(modifier = Modifier.width(10.dp))
                RulesButton(rules = rules)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                online.active -> OnlineContent(online = online, onExit = {
                    online.leave()
                    onExit()
                })
                game == null -> SettingsCard(
                    settings = viewModel.settings,
                    onChange = { viewModel.updateSettings(it) },
                    onStart = { viewModel.start() },
                    onPlayOnline = onHostOnline
                )
                else -> LocalContent(viewModel = viewModel, game = game, onExit = {
                    viewModel.close()
                    onExit()
                })
            }
        }
    }

    if (showQuit) {
        AlertDialog(
            onDismissRequest = { showQuit = false },
            title = { Text(stringResource(R.string.hg_quit_title)) },
            text = { Text(stringResource(R.string.hg_quit_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showQuit = false
                    if (online.active) online.leave() else viewModel.close()
                    onExit()
                }) {
                    Text(stringResource(R.string.hg_quit_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuit = false }) { Text(stringResource(R.string.quit_continue)) }
            }
        )
    }
}

// ---------- Réglages ----------

@Composable
private fun SettingsCard(
    settings: HangmanSettings,
    onChange: (HangmanSettings) -> Unit,
    onStart: () -> Unit,
    onPlayOnline: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.hg_setting_mistakes, settings.maxMistakes),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onChange(settings.copy(maxMistakes = settings.maxMistakes - 1)) },
                    enabled = settings.maxMistakes > HangmanSettings.MISTAKES_RANGE.first
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.mm_less))
                }
                IconButton(
                    onClick = { onChange(settings.copy(maxMistakes = settings.maxMistakes + 1)) },
                    enabled = settings.maxMistakes < HangmanSettings.MISTAKES_RANGE.last
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.mm_more))
                }
            }
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.hg_start))
            }
            OutlinedButton(onClick = onPlayOnline, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.hg_online_button))
            }
        }
    }
}

// ---------- Deux joueurs sur le même téléphone ----------

@Composable
private fun LocalContent(viewModel: HangmanViewModel, game: HangmanGame, onExit: () -> Unit) {
    val handoff = viewModel.handoffTo
    val names = game.players
    val word = game.word
    when {
        handoff != null -> HandoffCard(
            message = stringResource(
                if (word == null) R.string.hg_pass_setter else R.string.hg_pass_guesser,
                names.getOrElse(handoff) { "" }
            ),
            onReady = { viewModel.takePhone() }
        )
        word == null -> WordEntryCard(
            entryKey = "local-${game.round}",
            title = stringResource(R.string.hg_word_title, names[game.setter]),
            hint = stringResource(R.string.hg_word_hide_hint, names[game.guesser]),
            onSubmit = { viewModel.submitWord(it) }
        )
        else -> {
            val outcome = game.outcome
            val over = outcome != HangmanOutcome.PLAYING
            PlayCard(
                header = if (over) {
                    stringResource(R.string.hg_round, game.round)
                } else {
                    stringResource(R.string.hg_turn_guess, names[game.guesser])
                },
                scoreLine = stringResource(R.string.hg_score, names[0], game.wins[0], names[1], game.wins[1]),
                display = if (over) word else Hangman.masked(Hangman.shape(word), game.guesses),
                guesses = game.guesses,
                settings = game.settings,
                outcome = outcome,
                canGuess = !over,
                onGuess = { viewModel.guess(it) },
                result = when (outcome) {
                    HangmanOutcome.WON -> stringResource(R.string.hg_won, names[game.guesser])
                    HangmanOutcome.LOST -> stringResource(R.string.hg_lost, names[game.guesser], names[game.setter])
                    HangmanOutcome.PLAYING -> lastGuessText(game.guesses)
                },
                note = if (over) null else stringResource(R.string.hg_roles, names[game.setter], names[game.guesser]),
                footer = {
                    if (over) {
                        Button(onClick = { viewModel.nextRound() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.hg_next_round))
                        }
                        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.hg_end))
                        }
                    }
                }
            )
        }
    }
}

/** « Passe le téléphone à… » : le mot n'est pas affiché tant que le bon joueur n'a pas touché « C'est moi ». */
@Composable
private fun HandoffCard(message: String, onReady: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = message, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Button(onClick = onReady, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.hg_im_ready)) }
        }
    }
}

// ---------- Chacun sur son téléphone ----------

@Composable
private fun OnlineContent(online: HangmanOnline, onExit: () -> Unit) {
    when {
        online.notice == HangmanNotice.HOST_LEFT -> {
            Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = stringResource(R.string.hg_online_host_left), style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_close)) }
                }
            }
        }
        !online.started -> OnlineLobby(online, onExit)
        online.stage == HangmanStage.WORD -> {
            if (online.iAmSetter) {
                WordEntryCard(
                    entryKey = "online-${online.round}",
                    title = stringResource(R.string.hg_online_word_title),
                    hint = stringResource(R.string.hg_online_word_hint, online.opponentName),
                    onSubmit = { online.submitWord(it) }
                )
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(R.string.hg_online_waiting_word, online.opponentName),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                        Text(
                            text = scoreText(online),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        else -> OnlinePlay(online, onExit)
    }
}

/** Le score de la séance, dans l'ordre des places à la table. */
@Composable
private fun scoreText(online: HangmanOnline): String = stringResource(
    R.string.hg_score,
    online.lobby.getOrElse(0) { "" }, online.wins[0],
    online.lobby.getOrElse(1) { "" }, online.wins[1]
)

/** Salle d'attente : l'autre joueur se connecte ; l'organisateur lance la partie quand il est là. */
@Composable
private fun OnlineLobby(online: HangmanOnline, onExit: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (online.notice == HangmanNotice.PLAYER_LEFT) {
                Text(
                    text = stringResource(R.string.hg_online_notice_left),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(
                text = stringResource(R.string.hg_online_lobby_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            online.lobby.forEachIndexed { index, name ->
                Text(
                    text = if (index == online.seat) stringResource(R.string.hg_online_you, name) else name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (index == online.seat) FontWeight.Bold else FontWeight.Normal
                )
            }
            Text(
                text = stringResource(R.string.hg_setting_mistakes, online.settings.maxMistakes),
                style = MaterialTheme.typography.bodyMedium
            )
            if (online.isHost) {
                if (online.lobby.size < HangmanOnline.PLAYERS) {
                    Text(text = stringResource(R.string.hg_online_need_player), style = MaterialTheme.typography.bodyMedium)
                    Text(text = stringResource(R.string.hg_online_wait_player), style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { online.startGame() },
                    enabled = online.lobby.size >= HangmanOnline.PLAYERS,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.hg_online_start))
                }
            } else {
                Text(text = stringResource(R.string.hg_online_wait_start), style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.hg_quit_confirm))
            }
        }
    }
}

/** Une manche à deux téléphones : celui qui devine tape les lettres, celui qui a le mot regarde et répond tout seul. */
@Composable
private fun OnlinePlay(online: HangmanOnline, onExit: () -> Unit) {
    val opponent = online.opponentName
    val outcome = online.outcome
    val over = outcome != HangmanOutcome.PLAYING
    val iGuess = !online.iAmSetter
    PlayCard(
        header = when {
            over -> stringResource(R.string.hg_round, online.round)
            iGuess -> stringResource(R.string.hg_your_turn)
            else -> stringResource(R.string.hg_online_opponent_guessing, opponent)
        },
        scoreLine = scoreText(online),
        display = online.display,
        guesses = online.guesses,
        settings = online.settings,
        outcome = outcome,
        canGuess = online.canGuess,
        onGuess = { online.guess(it) },
        result = when {
            outcome == HangmanOutcome.WON && iGuess -> stringResource(R.string.hg_online_you_won)
            outcome == HangmanOutcome.LOST && iGuess -> stringResource(R.string.hg_online_you_lost, opponent)
            outcome == HangmanOutcome.WON -> stringResource(R.string.hg_online_opponent_won, opponent)
            outcome == HangmanOutcome.LOST -> stringResource(R.string.hg_online_opponent_lost, opponent)
            else -> lastGuessText(online.guesses)
        },
        note = when {
            online.pendingLetter != null -> stringResource(R.string.hg_online_waiting_answer)
            !over && !iGuess -> online.myWord?.let { stringResource(R.string.hg_my_word, it) }
            else -> null
        },
        footer = {
            if (online.opponentHonest == false) {
                Text(
                    text = stringResource(R.string.hg_cheat_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            if (over) {
                Button(onClick = { online.nextRound() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.hg_next_round))
                }
                OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.hg_end))
                }
            }
        }
    )
}

// ---------- La manche ----------

/** La réponse à la dernière lettre proposée, ou null si aucune n'a encore été proposée. */
@Composable
private fun lastGuessText(guesses: List<HangmanGuess>): String? = guesses.lastOrNull()?.let {
    stringResource(if (it.isMistake) R.string.hg_result_missing else R.string.hg_result_found, it.letter.toString())
}

/**
 * Le dessin, le mot, les lettres et le résultat d'une manche. [display] : le mot tel qu'on le voit (des blancs pour
 * les lettres pas encore trouvées) ; [footer] : les boutons sous le clavier.
 */
@Composable
private fun PlayCard(
    header: String,
    scoreLine: String,
    display: String,
    guesses: List<HangmanGuess>,
    settings: HangmanSettings,
    outcome: HangmanOutcome,
    canGuess: Boolean,
    onGuess: (Char) -> Unit,
    result: String?,
    note: String?,
    footer: @Composable () -> Unit
) {
    val mistakes = Hangman.mistakes(guesses)
    val found = guesses.flatMap { it.positions }.toSet()
    // Les lettres que le mot cachait encore à la fin d'une manche perdue sont signalées.
    val missed = if (outcome == HangmanOutcome.LOST) {
        display.indices.filter { Hangman.isLetter(display[it]) && it !in found }.toSet()
    } else {
        emptySet()
    }
    val announce = Modifier.semantics { liveRegion = LiveRegionMode.Polite }

    Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = header,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = scoreLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HangmanDrawing(
                parts = Hangman.drawingParts(settings, mistakes),
                lost = outcome == HangmanOutcome.LOST,
                description = stringResource(R.string.hg_drawing_description, mistakes, settings.maxMistakes)
            )
            Text(
                text = stringResource(R.string.hg_mistakes_count, mistakes, settings.maxMistakes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            WordDisplay(text = display, missedAt = missed)
            result?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = announce.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = announce.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            LetterKeyboard(guesses = guesses, enabled = canGuess, onPick = onGuess)
            footer()
        }
    }
}

/**
 * Le pendu, dessiné pièce par pièce : socle, poteau, traverse, corde, tête, corps, bras, jambes. [parts] pièces sont
 * tracées ; le bonhomme devient rouge quand il est pendu ([lost]).
 */
@Composable
private fun HangmanDrawing(parts: Int, lost: Boolean, description: String) {
    val gallows = MaterialTheme.colorScheme.onSurface
    val figure = if (lost) MaterialTheme.colorScheme.error else gallows
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .semantics { contentDescription = description }
    ) {
        // Le dessin est pensé sur une grille de 100 × 100, centrée dans la zone.
        val side = minOf(size.width, size.height)
        val left = (size.width - side) / 2f
        fun at(x: Float, y: Float) = Offset(left + x * side / 100f, y * side / 100f)
        val width = side * 0.035f
        fun line(from: Offset, to: Offset, color: Color) =
            drawLine(color = color, start = from, end = to, strokeWidth = width, cap = StrokeCap.Round)

        if (parts >= 1) line(at(8f, 96f), at(60f, 96f), gallows)
        if (parts >= 2) line(at(24f, 96f), at(24f, 6f), gallows)
        if (parts >= 3) {
            line(at(24f, 6f), at(66f, 6f), gallows)
            line(at(24f, 24f), at(42f, 6f), gallows)
        }
        if (parts >= 4) line(at(66f, 6f), at(66f, 20f), gallows)
        if (parts >= 5) drawCircle(color = figure, radius = side * 0.08f, center = at(66f, 28f), style = Stroke(width = width))
        if (parts >= 5 && lost) {
            // Pendu : les yeux en croix, la bouche fermée (sans sourire) et une petite langue qui pend dessous.
            val eyeWidth = width * 0.7f
            for (eyeX in listOf(62.5f, 69.5f)) {
                drawLine(color = figure, start = at(eyeX - 2f, 24.5f), end = at(eyeX + 2f, 28.5f), strokeWidth = eyeWidth, cap = StrokeCap.Round)
                drawLine(color = figure, start = at(eyeX - 2f, 28.5f), end = at(eyeX + 2f, 24.5f), strokeWidth = eyeWidth, cap = StrokeCap.Round)
            }
            // La langue, comme sur le dessin : courte, bout arrondi, contour foncé et petit sillon au milieu.
            // Elle est tracée avant la bouche, qui passe par-dessus son départ.
            val unit = side / 100f
            val tongueTopLeft = at(67.9f, 32.5f)
            val tongueSize = Size(3.4f * unit, 5f * unit)
            val tongueCorner = CornerRadius(1.7f * unit)
            val tongueLine = 0.7f * unit
            drawRoundRect(color = Color(0xFFE57373), topLeft = tongueTopLeft, size = tongueSize, cornerRadius = tongueCorner)
            drawRoundRect(color = figure, topLeft = tongueTopLeft, size = tongueSize, cornerRadius = tongueCorner, style = Stroke(width = tongueLine))
            drawLine(color = figure, start = at(69.6f, 34.7f), end = at(69.6f, 36.3f), strokeWidth = tongueLine, cap = StrokeCap.Round)
            drawLine(color = figure, start = at(62.5f, 32.5f), end = at(69.5f, 32.5f), strokeWidth = eyeWidth, cap = StrokeCap.Round)
        }
        if (parts >= 6) line(at(66f, 36f), at(66f, 64f), figure)
        if (parts >= 7) line(at(66f, 42f), at(52f, 54f), figure)
        if (parts >= 8) line(at(66f, 42f), at(80f, 54f), figure)
        if (parts >= 9) line(at(66f, 64f), at(54f, 84f), figure)
        if (parts >= 10) line(at(66f, 64f), at(78f, 84f), figure)
    }
}

/**
 * Le mot, un mot par ligne : chaque lettre trouvée au-dessus de son trait, un trait vide pour les autres, les
 * séparateurs tels quels. [missedAt] : places (dans [text]) des lettres que le joueur n'a pas trouvées, en rouge.
 */
@Composable
private fun WordDisplay(text: String, missedAt: Set<Int>) {
    val blank = stringResource(R.string.hg_blank)
    // TalkBack lit le mot lettre par lettre, « vide » pour une lettre pas encore trouvée.
    val spoken = text.map { if (it == Hangman.BLANK) blank else it.toString() }.joinToString(" ")
    val description = stringResource(R.string.hg_word_description, spoken)
    val lines = text.split(' ')
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description }
    ) {
        val cell = minOf(34.dp, maxWidth / lines.maxOf { it.length }.coerceAtLeast(1))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            var index = 0
            lines.forEach { line ->
                Row {
                    line.forEach { c ->
                        val color = if (index in missedAt) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        Column(modifier = Modifier.width(cell), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (c == Hangman.BLANK) " " else c.toString(),
                                fontSize = (cell.value * 0.75f).sp,
                                fontWeight = FontWeight.Bold,
                                color = color,
                                maxLines = 1
                            )
                            // Un trait sous chaque lettre ; les tirets et apostrophes n'en ont pas.
                            if (Hangman.isLetter(c) || c == Hangman.BLANK) {
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = cell * 0.08f)
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .background(color)
                                )
                            }
                        }
                        index++
                    }
                }
                // L'espace qui séparait ce mot du suivant compte aussi dans les places.
                index++
            }
        }
    }
}

/** Les 26 lettres : neutres, dans le mot (couleur principale) ou pas dans le mot (barrées). */
@Composable
private fun LetterKeyboard(guesses: List<HangmanGuess>, enabled: Boolean, onPick: (Char) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Hangman.ALPHABET.chunked(KEYS_PER_ROW).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { letter ->
                    LetterKey(
                        letter = letter,
                        guess = guesses.firstOrNull { it.letter == letter },
                        enabled = enabled,
                        onPick = onPick,
                        modifier = Modifier.weight(1f)
                    )
                }
                // La dernière ligne est plus courte : des places vides gardent les touches à la même largeur.
                repeat(KEYS_PER_ROW - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun LetterKey(letter: Char, guess: HangmanGuess?, enabled: Boolean, onPick: (Char) -> Unit, modifier: Modifier) {
    val description = stringResource(
        when {
            guess == null -> R.string.hg_key_free
            guess.isMistake -> R.string.hg_key_missed
            else -> R.string.hg_key_found
        },
        letter.toString()
    )
    val background = when {
        guess == null -> MaterialTheme.colorScheme.surfaceVariant
        guess.isMistake -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val content = when {
        guess == null -> MaterialTheme.colorScheme.onSurfaceVariant
        guess.isMistake -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .semantics { contentDescription = description }
            .clickable(enabled = enabled && guess == null, role = Role.Button) { onPick(letter) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (guess?.isMistake == false) FontWeight.ExtraBold else FontWeight.Medium,
            textDecoration = if (guess?.isMistake == true) TextDecoration.LineThrough else TextDecoration.None,
            color = content
        )
    }
}

// ---------- Choix du mot ----------

/**
 * Saisie du mot secret. Le texte est masqué par défaut (l'autre joueur est peut-être à côté) ; [onSubmit] reçoit
 * le mot tel que saisi. [entryKey] change à chaque manche, pour repartir d'un champ vide.
 */
@Composable
private fun WordEntryCard(entryKey: Any, title: String, hint: String, onSubmit: (String) -> Boolean) {
    var text by rememberSaveable(entryKey) { mutableStateOf("") }
    var shown by rememberSaveable(entryKey) { mutableStateOf(false) }
    val parsed = Hangman.parseWord(text)
    val invalid = text.isNotBlank() && parsed == null

    fun confirm() {
        if (parsed != null) onSubmit(text)
    }

    Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(text = hint, style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(Hangman.MAX_LENGTH * 2) },
                label = { Text(stringResource(R.string.hg_word_label)) },
                supportingText = { Text(stringResource(R.string.hg_word_help, Hangman.MIN_LETTERS, Hangman.MAX_LETTERS)) },
                isError = invalid,
                singleLine = true,
                visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
                // Le clavier « mot de passe » n'enregistre pas le mot et n'affiche pas de suggestions.
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (shown) KeyboardType.Text else KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = shown, role = Role.Switch, onValueChange = { shown = it })
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.hg_word_show),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = shown, onCheckedChange = null)
            }
            Button(onClick = { confirm() }, enabled = parsed != null, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.hg_word_confirm))
            }
        }
    }
}
