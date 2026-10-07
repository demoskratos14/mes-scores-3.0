package com.aventure.messcores

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role

/** Brouillon d'une règle en cours de saisie dans le formulaire (multiplicateur + bonus). */
private data class RuleDraft(val label: String, val factorText: String, val bonusText: String)

/** Sauvegarde des brouillons de règles (3 textes par règle, à plat) pour survivre à une rotation. */
private val RuleDraftsSaver: Saver<List<RuleDraft>, Any> = listSaver(
    save = { drafts -> drafts.flatMap { listOf(it.label, it.factorText, it.bonusText) } },
    restore = { flat -> flat.chunked(3).map { RuleDraft(it[0], it[1], it[2]) } }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGameScreen(
    repository: GameRepository,
    /** Jeu personnalisé à modifier ; null pour en créer un nouveau. */
    editing: GameRules? = null,
    onGameCreated: (GameRules) -> Unit,
    /** Appelé après l'enregistrement d'une modification (mode édition uniquement). */
    onGameUpdated: () -> Unit = {}
) {
    // rememberSaveable : le formulaire survit à une rotation de l'écran. En mode édition, les champs
    // démarrent avec les valeurs du jeu modifié.
    val endCondition = editing?.endCondition
    var name by rememberSaveable { mutableStateOf(editing?.name ?: "") }
    var scoreMode by rememberSaveable { mutableStateOf(editing?.scoreMode ?: ScoreMode.TABLE) }
    var lowestWins by rememberSaveable { mutableStateOf(editing?.lowestWins ?: false) }
    var allowNegative by rememberSaveable { mutableStateOf(editing?.allowNegativeScores ?: false) }
    var ruleDrafts by rememberSaveable(stateSaver = RuleDraftsSaver) {
        mutableStateOf(
            editing?.multipliers.orEmpty()
                .filter { it.id != GameRules.NORMAL_MULTIPLIER_ID }
                .map { RuleDraft(it.label, it.factor.toString(), if (it.bonus == 0) "" else it.bonus.toString()) }
        )
    }
    var endConditionType by rememberSaveable { mutableStateOf(endCondition?.type ?: EndConditionType.NONE) }
    var endValueText by rememberSaveable {
        mutableStateOf(
            when (endCondition?.type) {
                EndConditionType.ROUND_COUNT -> endCondition?.roundCount?.toString()
                EndConditionType.SCORE_THRESHOLD -> endCondition?.scoreThreshold?.toString()
                else -> null
            } ?: ""
        )
    }
    var thresholdDirection by rememberSaveable {
        mutableStateOf(endCondition?.thresholdDirection ?: ThresholdDirection.ABOVE)
    }
    var stopImmediately by rememberSaveable { mutableStateOf(endCondition?.stopImmediately ?: true) }
    var tieBreakOnEqualLeaders by rememberSaveable { mutableStateOf(endCondition?.tieBreakOnEqualLeaders ?: false) }

    val untitledGame = stringResource(R.string.create_untitled)

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).imePadding(),
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = cardSurface()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    stringResource(if (editing == null) R.string.create_title_new else R.string.create_title_edit),
                    style = MaterialTheme.typography.headlineMedium
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.create_name)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.score_mode), style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = scoreMode == ScoreMode.TABLE,
                            onClick = { scoreMode = ScoreMode.TABLE },
                            label = { Text(stringResource(R.string.score_mode_table)) }
                        )
                        FilterChip(
                            selected = scoreMode == ScoreMode.COUNTER,
                            onClick = { scoreMode = ScoreMode.COUNTER },
                            label = { Text(stringResource(R.string.score_mode_counter)) }
                        )
                        FilterChip(
                            selected = scoreMode == ScoreMode.VARIABLE_TEAMS,
                            onClick = { scoreMode = ScoreMode.VARIABLE_TEAMS },
                            label = { Text(stringResource(R.string.score_mode_teams)) }
                        )
                    }
                    Text(
                        text = when (scoreMode) {
                            ScoreMode.TABLE -> stringResource(R.string.create_mode_table_desc)
                            ScoreMode.COUNTER -> stringResource(R.string.create_mode_counter_desc)
                            ScoreMode.VARIABLE_TEAMS -> stringResource(R.string.create_mode_teams_desc)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (scoreMode != ScoreMode.COUNTER) {
                    SettingRow(
                        title = stringResource(R.string.create_lowest_title),
                        subtitle = stringResource(R.string.create_lowest_sub),
                        checked = lowestWins,
                        onCheckedChange = { lowestWins = it }
                    )

                    SettingRow(
                        title = stringResource(R.string.create_negative_title),
                        subtitle = stringResource(R.string.create_negative_sub),
                        checked = allowNegative,
                        onCheckedChange = { allowNegative = it }
                    )

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

                Button(
                    onClick = {
                        val customRules = ruleDrafts.mapIndexedNotNull { index, draft ->
                            val factor = draft.factorText.toIntOrNull() ?: 1
                            val bonus = draft.bonusText.toIntOrNull() ?: 0
                            if (draft.label.isBlank() || (factor == 1 && bonus == 0)) {
                                null
                            } else {
                                ScoreMultiplier(id = "custom_$index", label = draft.label, factor = factor, bonus = bonus)
                            }
                        }
                        val endValue = endValueText.toIntOrNull()
                        val endCondition = when (endConditionType) {
                            EndConditionType.NONE -> EndCondition()
                            EndConditionType.ROUND_COUNT -> EndCondition(type = EndConditionType.ROUND_COUNT, roundCount = endValue)
                            EndConditionType.SCORE_THRESHOLD -> EndCondition(
                                type = EndConditionType.SCORE_THRESHOLD,
                                scoreThreshold = endValue,
                                thresholdDirection = thresholdDirection,
                                stopImmediately = stopImmediately,
                                tieBreakOnEqualLeaders = tieBreakOnEqualLeaders
                            )
                        }
                        if (editing != null) {
                            // copy() : conserve ce que le formulaire ne propose pas (id, nombre de joueurs…).
                            repository.updateCustomGame(
                                editing.copy(
                                    name = name.ifBlank { untitledGame },
                                    lowestWins = lowestWins,
                                    allowNegativeScores = allowNegative,
                                    multipliers = listOf(GameRules.NORMAL_MULTIPLIER) + customRules,
                                    scoreMode = scoreMode,
                                    endCondition = endCondition
                                )
                            )
                            onGameUpdated()
                        } else {
                            val rules = GameRules(
                                id = repository.newId(),
                                name = name.ifBlank { untitledGame },
                                lowestWins = lowestWins,
                                allowNegativeScores = allowNegative,
                                multipliers = listOf(GameRules.NORMAL_MULTIPLIER) + customRules,
                                scoreMode = scoreMode,
                                endCondition = endCondition
                            )
                            repository.saveCustomGame(rules)
                            onGameCreated(rules)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(if (editing == null) R.string.create_submit_new else R.string.create_submit_edit))
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    // La ligne entière est l'interrupteur : TalkBack annonce le titre avec l'état.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
