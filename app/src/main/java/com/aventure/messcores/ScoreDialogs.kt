package com.aventure.messcores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/** Fenêtre de saisie d'un score : champ numérique plein format + bascule +/− éventuelle. */
@Composable
internal fun ScoreInputDialog(
    title: String?,
    initialValue: Int?,
    initialNegative: Boolean,
    allowNegative: Boolean,
    onConfirm: (Int?, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val initialText = initialValue?.toString() ?: ""
    // Texte présélectionné : taper un nouveau score remplace directement l'ancien.
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText, TextRange(0, initialText.length)))
    }
    var negative by rememberSaveable { mutableStateOf(initialNegative) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val confirm = { onConfirm(field.text.toIntOrNull(), negative) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title ?: stringResource(R.string.score_enter_title)) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (allowNegative) {
                    OutlinedButton(
                        onClick = { negative = !negative },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = if (negative) "−" else "+",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            color = if (negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                OutlinedTextField(
                    value = field,
                    onValueChange = { input ->
                        field = input.copy(text = input.text.filter { it.isDigit() })
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { confirm() }) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

/**
 * Fenêtre de saisie à boutons, pour les catégories à score fixe d'une feuille (ex : « Full » = 25) :
 * toucher une valeur la saisit ; 0 barre la case ; « Effacer » la vide.
 */
@Composable
internal fun ScoreChoiceDialog(
    title: String,
    options: List<Int>,
    hint: String?,
    zeroCrossesOut: Boolean,
    current: Int?,
    onPick: (Int?) -> Unit,
    onDismiss: () -> Unit,
    optionLabel: ((Int) -> String)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.chunked(3).forEach { rowOptions ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowOptions.forEach { value ->
                            val label = optionLabel?.invoke(value)
                                ?: if (value == 0 && zeroCrossesOut) stringResource(R.string.sheet_cross_out) else value.toString()
                            if (value == current) {
                                Button(onClick = { onPick(value) }, modifier = Modifier.weight(1f)) { Text(label, maxLines = 1) }
                            } else {
                                OutlinedButton(onClick = { onPick(value) }, modifier = Modifier.weight(1f)) { Text(label, maxLines = 1) }
                            }
                        }
                        repeat(3 - rowOptions.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
                if (hint != null) {
                    Text(hint, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.sheet_clear)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

/**
 * Saisie « annonce / plis réalisés » (Wizard, Oh Hell) : calcule le score de la manche
 * (bonus si l'annonce est exacte, pénalité sinon) et enregistre ce score dans la case.
 */
@Composable
internal fun BidScoreDialog(
    title: String?,
    kind: String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var bid by remember { mutableStateOf("") }
    var tricks by remember { mutableStateOf("") }
    val bidValue = bid.toIntOrNull()
    val tricksValue = tricks.toIntOrNull()
    val score = if (bidValue != null && tricksValue != null) BidScoring.score(kind, bidValue, tricksValue) else null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title ?: stringResource(R.string.score_enter_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = bid,
                    onValueChange = { bid = it.filter(Char::isDigit).take(2) },
                    label = { Text(stringResource(R.string.bid_announced)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = tricks,
                    onValueChange = { tricks = it.filter(Char::isDigit).take(2) },
                    label = { Text(stringResource(R.string.bid_tricks)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text(
                    text = if (score != null) stringResource(R.string.bid_score_preview, score) else stringResource(R.string.bid_score_hint),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(enabled = score != null, onClick = { score?.let(onConfirm) }) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

/**
 * Saisie d'une frame de bowling lancer par lancer : les boutons proposent seulement le nombre de
 * quilles encore debout. La frame est enregistrée quand tous ses lancers sont saisis.
 */
@Composable
internal fun BowlingFrameDialog(
    frame: Int,
    title: String?,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val rolls = remember { mutableStateListOf<Int>() }
    val maxNext = BowlingScoring.maxNextRoll(frame, rolls)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title ?: stringResource(R.string.score_enter_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (rolls.isEmpty()) stringResource(R.string.bowling_roll_hint) else BowlingScoring.notation(rolls),
                    style = MaterialTheme.typography.bodyLarge
                )
                if (maxNext != null) {
                    Text(stringResource(R.string.bowling_next_roll, rolls.size + 1), style = MaterialTheme.typography.bodyMedium)
                    (0..maxNext).toList().chunked(4).forEach { rowValues ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowValues.forEach { pins ->
                                OutlinedButton(
                                    onClick = { rolls.add(pins) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(0.dp)
                                ) { Text(pins.toString(), maxLines = 1) }
                            }
                            repeat(4 - rowValues.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = maxNext == null && rolls.isNotEmpty(),
                onClick = { onConfirm(BowlingScoring.encode(rolls)) }
            ) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            Row {
                if (rolls.isNotEmpty()) {
                    TextButton(onClick = { rolls.removeAt(rolls.lastIndex) }) { Text(stringResource(R.string.bowling_undo)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        }
    )
}
