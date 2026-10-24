package com.aventure.messcores

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Huit couleurs bien distinctes. Chacune porte aussi un numéro (et un nom pour TalkBack) : on ne
// doit jamais avoir besoin de voir la couleur pour jouer.
private val MASTERMIND_COLORS = listOf(
    Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFDD835),
    Color(0xFFFB8C00), Color(0xFF8E24AA), Color(0xFF00ACC1), Color(0xFF6D4C41)
)

private val MASTERMIND_COLOR_NAMES = listOf(
    R.string.mm_color_red, R.string.mm_color_blue, R.string.mm_color_green, R.string.mm_color_yellow,
    R.string.mm_color_orange, R.string.mm_color_purple, R.string.mm_color_teal, R.string.mm_color_brown
)

// Le jaune et l'orange sont clairs : leur numéro s'écrit en noir.
private val MASTERMIND_DARK_LABEL_COLORS = setOf(3, 4)

@Composable
private fun colorName(color: Int): String = stringResource(MASTERMIND_COLOR_NAMES[color])

/**
 * Mastermind à plusieurs : réglages, saisie secrète des suites en passant le téléphone, recherche des
 * suites l'une après l'autre (les autres joueurs se mettent d'accord sur chaque essai), puis résultats.
 */
@Composable
fun MastermindScreen(viewModel: MastermindViewModel, onExit: () -> Unit) {
    val rules = viewModel.rules
    val state = viewModel.state
    KeepScreenOn()

    var showQuit by rememberSaveable { mutableStateOf(false) }
    BackHandler {
        if (state == null || state.phase == MastermindPhase.RESULTS) {
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
                text = rules?.name.orEmpty(),
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
                state == null -> SettingsContent(
                    settings = viewModel.settings,
                    onChange = { viewModel.updateSettings(it) },
                    onStart = { viewModel.start() }
                )
                state.phase == MastermindPhase.SECRETS -> SecretsContent(state, onSecret = { viewModel.submitSecret(it) })
                state.phase == MastermindPhase.PLAYING -> PlayContent(
                    state = state,
                    onGuess = { viewModel.submitGuess(it) },
                    onNext = { viewModel.nextTarget() }
                )
                else -> ResultsContent(
                    state = state,
                    onReplay = { viewModel.replay() },
                    onExit = {
                        viewModel.close()
                        onExit()
                    }
                )
            }
        }
    }

    if (showQuit) {
        AlertDialog(
            onDismissRequest = { showQuit = false },
            title = { Text(stringResource(R.string.mm_quit_title)) },
            text = { Text(stringResource(R.string.mm_quit_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showQuit = false
                    viewModel.close()
                    onExit()
                }) {
                    Text(stringResource(R.string.mm_quit_confirm), color = MaterialTheme.colorScheme.error)
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
private fun SettingsContent(settings: MastermindSettings, onChange: (MastermindSettings) -> Unit, onStart: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Stepper(
                label = stringResource(R.string.mm_setting_length, settings.codeLength),
                value = settings.codeLength,
                range = MastermindSettings.LENGTH_RANGE,
                onChange = { onChange(settings.copy(codeLength = it)) }
            )
            Stepper(
                label = stringResource(R.string.mm_setting_colors, settings.colorCount),
                value = settings.colorCount,
                range = MastermindSettings.COLOR_RANGE,
                onChange = { onChange(settings.copy(colorCount = it)) }
            )
            Stepper(
                label = stringResource(R.string.mm_setting_attempts, settings.maxAttempts),
                value = settings.maxAttempts,
                range = MastermindSettings.ATTEMPTS_RANGE,
                onChange = { onChange(settings.copy(maxAttempts = it)) }
            )
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.mm_start))
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(value - 1) }, enabled = value > range.first) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.mm_less))
        }
        IconButton(onClick = { onChange(value + 1) }, enabled = value < range.last) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.mm_more))
        }
    }
}

// ---------- Saisie des suites secrètes ----------

/** Chaque joueur saisit sa suite à son tour : écran « passe le téléphone » d'abord, pour que personne ne la voie. */
@Composable
private fun SecretsContent(state: MastermindState, onSecret: (List<Int>) -> Unit) {
    val name = state.players[state.secretTurn]
    var ready by rememberSaveable(state.secretTurn) { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!ready) {
                Text(
                    text = stringResource(R.string.mm_pass_phone, name),
                    style = MaterialTheme.typography.titleLarge
                )
                Button(onClick = { ready = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.mm_im_ready))
                }
            } else {
                Text(
                    text = stringResource(R.string.mm_choose_secret, name),
                    style = MaterialTheme.typography.titleMedium
                )
                CodeEntry(
                    settings = state.settings,
                    entryKey = state.secretTurn,
                    confirmLabel = R.string.mm_validate_secret,
                    onConfirm = onSecret
                )
            }
        }
    }
}

// ---------- Recherche d'une suite ----------

/**
 * Un tour : un essai pour la suite de [MastermindState.target]. Une fois l'essai validé, on lit la réponse
 * puis on passe à la suite suivante (les suites alternent, voir [Mastermind.nextTarget]).
 */
@Composable
private fun PlayContent(state: MastermindState, onGuess: (List<Int>) -> Unit, onNext: () -> Unit) {
    val target = state.target
    val owner = state.players[target]
    val guesses = state.guesses[target]
    val solved = Mastermind.isSolved(state, target)
    val failed = Mastermind.isFailed(state, target)
    val searchers = state.players.filterIndexed { index, _ -> index != target }.joinToString(", ")
    // Point dont on affiche la signification : 0 = noir (bien placé), 1 = blanc (mal placé), -1 = aucun.
    var legend by rememberSaveable { mutableIntStateOf(-1) }

    Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.mm_target_title, owner),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            Text(text = stringResource(R.string.mm_searchers, searchers), style = MaterialTheme.typography.bodyMedium)
            if (!state.awaitingNext) {
                Text(
                    text = stringResource(R.string.mm_attempt_counter, guesses.size + 1, state.settings.maxAttempts),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    if (guesses.isNotEmpty()) {
        Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(
                        when (legend) {
                            0 -> R.string.mm_legend_black
                            1 -> R.string.mm_legend_white
                            else -> R.string.mm_legend_hint
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                guesses.forEachIndexed { index, guess ->
                    GuessRow(
                        number = index + 1,
                        guess = guess,
                        pegSize = if (state.settings.codeLength > 4) 28.dp else 34.dp,
                        onDotClick = { dot -> legend = if (legend == dot) -1 else dot }
                    )
                }
            }
        }
    }

    val allOver = state.players.indices.all { Mastermind.isOver(state, it) }
    val nextLabel = if (allOver) R.string.mm_see_results else R.string.mm_next_code
    when {
        solved -> Card(colors = CardDefaults.cardColors(containerColor = highlightContainer(), contentColor = highlightContent()), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.mm_found, guesses.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text(stringResource(nextLabel)) }
            }
        }
        failed -> Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(R.string.mm_failed), style = MaterialTheme.typography.titleMedium)
                state.secrets[target]?.let { PegRow(pegs = it, pegSize = 40.dp) }
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text(stringResource(nextLabel)) }
            }
        }
        state.awaitingNext -> Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                guesses.lastOrNull()?.let { last ->
                    Text(
                        text = stringResource(R.string.mm_feedback_description, last.feedback.wellPlaced, last.feedback.misplaced),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text(stringResource(nextLabel)) }
            }
        }
        else -> Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                CodeEntry(
                    settings = state.settings,
                    entryKey = target to guesses.size,
                    confirmLabel = R.string.mm_validate_guess,
                    onConfirm = onGuess
                )
            }
        }
    }
}

/** Un essai passé : son numéro, ses pions et la réponse (noirs = bien placés, blancs = mal placés). */
@Composable
private fun GuessRow(number: Int, guess: MastermindGuess, pegSize: Dp, onDotClick: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = number.toString(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(22.dp))
        guess.pegs.forEachIndexed { index, color ->
            Peg(
                color = color,
                size = pegSize,
                description = stringResource(R.string.mm_peg_description, colorName(color), index + 1)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        FeedbackView(guess.feedback, onDotClick)
    }
}

/**
 * La réponse à un essai : un point noir et son nombre (bien placés), un point blanc et son nombre (mal placés).
 * On touche un point pour afficher ce qu'il signifie ([onDotClick] reçoit 0 pour le noir, 1 pour le blanc).
 */
@Composable
private fun FeedbackView(feedback: MastermindFeedback, onDotClick: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FeedbackItem(
            color = Color.Black,
            count = feedback.wellPlaced,
            description = stringResource(R.string.mm_well_placed_description, feedback.wellPlaced),
            onClick = { onDotClick(0) }
        )
        FeedbackItem(
            color = Color.White,
            count = feedback.misplaced,
            description = stringResource(R.string.mm_misplaced_description, feedback.misplaced),
            onClick = { onDotClick(1) }
        )
    }
}

/** Un point de réponse et son nombre, assez grand pour être touché. */
@Composable
private fun FeedbackItem(color: Color, count: Int, description: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .defaultMinSize(minHeight = 44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp)
            .semantics { contentDescription = description }
    ) {
        Dot(color)
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyLarge,
            // La description du point dit déjà le nombre.
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .clearAndSetSemantics { }
        )
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color, CircleShape)
            .border(1.dp, Color.Gray, CircleShape)
    )
}

// ---------- Résultats ----------

@Composable
private fun ResultsContent(state: MastermindState, onReplay: () -> Unit, onExit: () -> Unit) {
    val winners = Mastermind.winners(state)
    val ranks = Mastermind.ranks(state)

    Card(
        colors = CardDefaults.cardColors(containerColor = highlightContainer(), contentColor = highlightContent()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(
                if (winners.size > 1) R.string.score_game_over_winners else R.string.score_game_over_winner,
                winners.joinToString(stringResource(R.string.result_and)) { state.players[it] }
            ),
            modifier = Modifier.padding(12.dp),
            fontWeight = FontWeight.Bold
        )
    }

    Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.mm_results_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            state.players.indices.forEach { target ->
                val attempts = Mastermind.attempts(state, target) ?: 0
                val owner = state.players[target]
                Text(
                    text = if (Mastermind.isSolved(state, target)) {
                        stringResource(R.string.mm_code_found, owner, attempts)
                    } else {
                        stringResource(R.string.mm_code_missed, owner, attempts)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                state.secrets[target]?.let { PegRow(pegs = it, pegSize = 28.dp) }
            }
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.players.indices.sortedBy { ranks[it] }.forEach { player ->
                Text(
                    text = stringResource(
                        R.string.mm_average_line,
                        ranks[player],
                        state.players[player],
                        Mastermind.average(state, player)
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (ranks[player] == 1) FontWeight.Bold else FontWeight.Normal
                )
            }
            Text(text = stringResource(R.string.mm_average_help), style = MaterialTheme.typography.bodySmall)
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onReplay, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.mm_replay)) }
        OutlinedButton(onClick = onExit, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.common_close)) }
    }
}

// ---------- Pions et saisie d'une combinaison ----------

/** Saisie d'une combinaison : on touche un emplacement, puis une couleur ; le bouton valide quand tout est rempli. */
@Composable
private fun CodeEntry(
    settings: MastermindSettings,
    entryKey: Any,
    @StringRes confirmLabel: Int,
    onConfirm: (List<Int>) -> Unit
) {
    // -1 = emplacement vide. Une nouvelle clé (autre joueur, autre essai) repart d'une combinaison vide.
    var pegs by rememberSaveable(entryKey) { mutableStateOf(List(settings.codeLength) { -1 }) }
    var selected by rememberSaveable(entryKey) { mutableIntStateOf(0) }

    fun pick(color: Int) {
        val updated = pegs.toMutableList()
        updated[selected] = color
        pegs = updated
        // Passe à l'emplacement vide suivant, s'il en reste.
        val next = (1..updated.size).map { (selected + it) % updated.size }.firstOrNull { updated[it] < 0 }
        if (next != null) selected = next
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pegs.forEachIndexed { index, color ->
                val description = if (color < 0) {
                    stringResource(R.string.mm_peg_empty, index + 1)
                } else {
                    stringResource(R.string.mm_peg_description, colorName(color), index + 1)
                }
                Peg(
                    color = color.takeIf { it >= 0 },
                    size = 48.dp,
                    description = description,
                    selected = index == selected,
                    onClick = { selected = index }
                )
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (color in 0 until settings.colorCount) {
                Peg(color = color, size = 48.dp, description = colorName(color), onClick = { pick(color) })
            }
        }
        TextButton(onClick = {
            val updated = pegs.toMutableList()
            updated[selected] = -1
            pegs = updated
        }) {
            Text(stringResource(R.string.mm_clear))
        }
        Button(
            onClick = { onConfirm(pegs) },
            enabled = pegs.all { it >= 0 },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(confirmLabel))
        }
    }
}

/** Une suite de pions (la suite secrète révélée, par exemple). */
@Composable
private fun PegRow(pegs: List<Int>, pegSize: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        pegs.forEachIndexed { index, color ->
            Peg(
                color = color,
                size = pegSize,
                description = stringResource(R.string.mm_peg_description, colorName(color), index + 1)
            )
        }
    }
}

/** Un pion : rond de la couleur, avec son numéro dedans ; vide (simple contour) si [color] est null. */
@Composable
private fun Peg(
    color: Int?,
    size: Dp,
    description: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val fill = if (color != null) MASTERMIND_COLORS[color] else Color.Transparent
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Gray
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(fill, CircleShape)
            .border(if (selected) 3.dp else 1.dp, borderColor, CircleShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        if (color != null) {
            Text(
                text = (color + 1).toString(),
                color = if (color in MASTERMIND_DARK_LABEL_COLORS) Color.Black else Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                // Le nom de la couleur est déjà lu par la description du pion.
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
    }
}
