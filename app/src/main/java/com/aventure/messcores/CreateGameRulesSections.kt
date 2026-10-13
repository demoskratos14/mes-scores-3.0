package com.aventure.messcores

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Règles de multiplication (coefficient + bonus) définies par l'utilisateur. */
@Composable
internal fun MultiplierRulesSection(form: CreateGameForm, activeVariant: GameVariant) {
    val scoreMode by form.scoreMode
    var ruleDrafts by form.ruleDrafts
    // Les coefficients n'ont pas de sens pour une feuille, des annonces ou le Tarot (contrats fixes).
    if (scoreMode != ScoreMode.COUNTER && (activeVariant == GameVariant.NONE ||
            activeVariant == GameVariant.EXACT_TARGET || activeVariant == GameVariant.COUNTDOWN)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.create_rules_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.create_rules_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ruleDrafts.forEachIndexed { index, draft ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = draft.label,
                        onValueChange = { newLabel ->
                            ruleDrafts = ruleDrafts.toMutableList().also {
                                it[index] = draft.copy(label = newLabel)
                            }
                        },
                        label = { Text(stringResource(R.string.create_rule_name)) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedTextField(
                        value = draft.factorText,
                        onValueChange = { newFactor ->
                            val digitsOnly = newFactor.filter { it.isDigit() }
                            ruleDrafts = ruleDrafts.toMutableList().also {
                                it[index] = draft.copy(factorText = digitsOnly)
                            }
                        },
                        label = { Text(stringResource(R.string.create_rule_factor)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(60.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedTextField(
                        value = draft.bonusText,
                        onValueChange = { newBonus ->
                            val digitsOnly = newBonus.filter { it.isDigit() }
                            ruleDrafts = ruleDrafts.toMutableList().also {
                                it[index] = draft.copy(bonusText = digitsOnly)
                            }
                        },
                        label = { Text("+") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(60.dp)
                    )
                    IconButton(onClick = {
                        ruleDrafts = ruleDrafts.toMutableList().also { it.removeAt(index) }
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.create_rule_delete))
                    }
                }
            }

            TextButton(onClick = { ruleDrafts = ruleDrafts + RuleDraft("", "", "") }) {
                Text(stringResource(R.string.create_rule_add))
            }
        }
    }
}

/** Condition de fin de partie : aucune, nombre de manches ou score à atteindre. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EndConditionSection(form: CreateGameForm) {
    val scoreMode by form.scoreMode
    var endConditionType by form.endConditionType
    var endValueText by form.endValueText
    var thresholdDirection by form.thresholdDirection
    var stopImmediately by form.stopImmediately
    var tieBreakOnEqualLeaders by form.tieBreakOnEqualLeaders
    var minLeadText by form.minLeadText
    var maxScoreText by form.maxScoreText
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.create_end_title), style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = endConditionType == EndConditionType.NONE,
                onClick = { endConditionType = EndConditionType.NONE },
                label = { Text(stringResource(R.string.create_end_none)) }
            )
            FilterChip(
                selected = endConditionType == EndConditionType.ROUND_COUNT,
                onClick = { endConditionType = EndConditionType.ROUND_COUNT },
                label = { Text(stringResource(R.string.create_end_rounds)) }
            )
            FilterChip(
                selected = endConditionType == EndConditionType.SCORE_THRESHOLD,
                onClick = { endConditionType = EndConditionType.SCORE_THRESHOLD },
                label = { Text(stringResource(R.string.create_end_score)) }
            )
        }
        if (endConditionType != EndConditionType.NONE) {
            OutlinedTextField(
                value = endValueText,
                onValueChange = { endValueText = it.filter { c -> c.isDigit() } },
                label = {
                    Text(
                        stringResource(
                            if (endConditionType == EndConditionType.ROUND_COUNT) R.string.create_end_rounds
                            else R.string.create_end_threshold
                        )
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (endConditionType == EndConditionType.SCORE_THRESHOLD) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.create_direction), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = thresholdDirection == ThresholdDirection.ABOVE,
                        onClick = { thresholdDirection = ThresholdDirection.ABOVE },
                        label = { Text(stringResource(R.string.create_dir_above)) }
                    )
                    FilterChip(
                        selected = thresholdDirection == ThresholdDirection.BELOW,
                        onClick = { thresholdDirection = ThresholdDirection.BELOW },
                        label = { Text(stringResource(R.string.create_dir_below)) }
                    )
                }

                NumberField(
                    value = minLeadText,
                    onValueChange = { minLeadText = it },
                    label = stringResource(R.string.create_end_min_lead),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.create_end_min_lead_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                NumberField(
                    value = maxScoreText,
                    onValueChange = { maxScoreText = it },
                    label = stringResource(R.string.create_end_max_score),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.create_end_max_score_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (scoreMode == ScoreMode.TABLE) {
                    Text(stringResource(R.string.create_stop_moment), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = stopImmediately,
                            onClick = { stopImmediately = true },
                            label = { Text(stringResource(R.string.create_stop_now)) }
                        )
                        FilterChip(
                            selected = !stopImmediately,
                            onClick = { stopImmediately = false },
                            label = { Text(stringResource(R.string.create_stop_round_end)) }
                        )
                    }
                }

                SettingRow(
                    title = stringResource(R.string.create_tiebreak_title),
                    subtitle = stringResource(R.string.create_tiebreak_sub),
                    checked = tieBreakOnEqualLeaders,
                    onCheckedChange = { tieBreakOnEqualLeaders = it }
                )
            }
        }
    }
}
