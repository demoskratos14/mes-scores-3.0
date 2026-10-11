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
import androidx.compose.foundation.verticalScroll
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

    // Nombre de joueurs : vide = valeur par défaut (voir GameRules.DEFAULT_MIN_PLAYERS / DEFAULT_MAX_PLAYERS).
    var minPlayersText by rememberSaveable {
        mutableStateOf(editing?.minPlayers?.takeIf { it != GameRules.DEFAULT_MIN_PLAYERS }?.toString() ?: "")
    }
    var maxPlayersText by rememberSaveable {
        mutableStateOf(editing?.maxPlayers?.takeIf { it != GameRules.DEFAULT_MAX_PLAYERS }?.toString() ?: "")
    }
    // Mécanique reprise d'un jeu prédéfini (feuille, annonces, score exact, décompte, Tarot, phases).
    var variant by rememberSaveable { mutableStateOf(editing?.mechanic() ?: GameVariant.NONE) }
    var sheetId by rememberSaveable { mutableStateOf(editing?.sheet ?: ScoreSheets.YAMS) }
    var bidKind by rememberSaveable { mutableStateOf(editing?.bidScoring ?: BidScoring.WIZARD) }
    var exactTargetText by rememberSaveable { mutableStateOf(editing?.exactTarget?.toString() ?: "") }
    var exactResetText by rememberSaveable { mutableStateOf(editing?.exactReset?.toString() ?: "") }
    var countdownText by rememberSaveable { mutableStateOf(editing?.countdownFrom?.toString() ?: "") }
    var phasesOn by rememberSaveable { mutableStateOf(editing?.phases != null) }
    var phasesText by rememberSaveable { mutableStateOf(editing?.phases?.toString() ?: "10") }

    // La mécanique n'est valable que pour certains modes : on retombe sur « classique » sinon.
    val activeVariant = if (variant in variantsFor(scoreMode)) variant else GameVariant.NONE
    val phasesActive = scoreMode == ScoreMode.TABLE && activeVariant == GameVariant.NONE && phasesOn
    val tarotContracts = listOf(
        GameRules.NORMAL_MULTIPLIER.copy(label = stringResource(R.string.tarot_bid_petite)),
        ScoreMultiplier(id = "tarot_garde", label = stringResource(R.string.tarot_bid_garde), factor = 2),
        ScoreMultiplier(id = "tarot_garde_sans", label = stringResource(R.string.tarot_bid_garde_sans), factor = 4),
        ScoreMultiplier(id = "tarot_garde_contre", label = stringResource(R.string.tarot_bid_garde_contre), factor = 6)
    )

    val untitledGame = stringResource(R.string.create_untitled)

    Column(
        modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
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

                // ----- Nombre de joueurs -----
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.create_players_title), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(
                            value = minPlayersText,
                            onValueChange = { minPlayersText = it },
                            label = stringResource(R.string.create_players_min),
                            modifier = Modifier.weight(1f)
                        )
                        NumberField(
                            value = maxPlayersText,
                            onValueChange = { maxPlayersText = it },
                            label = stringResource(R.string.create_players_max),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        stringResource(R.string.create_players_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // ----- Mécanique reprise des jeux prédéfinis -----
                val variants = variantsFor(scoreMode)
                if (variants.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.create_variant_title), style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            variants.forEach { option ->
                                FilterChip(
                                    selected = activeVariant == option,
                                    onClick = { variant = option },
                                    label = { Text(stringResource(option.labelRes())) }
                                )
                            }
                        }
                        val desc = activeVariant.descriptionRes()
                        if (desc != null) {
                            Text(
                                stringResource(desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    when (activeVariant) {
                        GameVariant.SHEET -> {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    ScoreSheets.YAMS to R.string.game_yams,
                                    ScoreSheets.QWIXX to R.string.game_qwixx,
                                    ScoreSheets.BOWLING to R.string.game_bowling
                                ).forEach { (id, label) ->
                                    FilterChip(
                                        selected = sheetId == id,
                                        onClick = { sheetId = id },
                                        label = { Text(stringResource(label)) }
                                    )
                                }
                            }
                            Text(
                                stringResource(
                                    when (sheetId) {
                                        ScoreSheets.QWIXX -> R.string.create_sheet_qwixx_sub
                                        ScoreSheets.BOWLING -> R.string.create_sheet_bowling_sub
                                        else -> R.string.create_sheet_yams_sub
                                    }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        GameVariant.BID -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = bidKind == BidScoring.WIZARD,
                                    onClick = { bidKind = BidScoring.WIZARD },
                                    label = { Text(stringResource(R.string.game_wizard)) }
                                )
                                FilterChip(
                                    selected = bidKind == BidScoring.OH_HELL,
                                    onClick = { bidKind = BidScoring.OH_HELL },
                                    label = { Text(stringResource(R.string.game_ohhell)) }
                                )
                            }
                            Text(
                                stringResource(
                                    if (bidKind == BidScoring.OH_HELL) R.string.create_bid_ohhell_sub else R.string.create_bid_wizard_sub
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        GameVariant.EXACT_TARGET -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NumberField(
                                    value = exactTargetText,
                                    onValueChange = { exactTargetText = it },
                                    label = stringResource(R.string.create_exact_target),
                                    modifier = Modifier.weight(1f)
                                )
                                NumberField(
                                    value = exactResetText,
                                    onValueChange = { exactResetText = it },
                                    label = stringResource(R.string.create_exact_reset),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        GameVariant.COUNTDOWN -> {
                            NumberField(
                                value = countdownText,
                                onValueChange = { countdownText = it },
                                label = stringResource(R.string.create_countdown_from),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        else -> Unit
                    }
                }

                // ----- Suivi des phases (Phase 10) : tableau de manches libres uniquement -----
                if (scoreMode == ScoreMode.TABLE && activeVariant == GameVariant.NONE) {
                    SettingRow(
                        title = stringResource(R.string.create_phases_title),
                        subtitle = stringResource(R.string.create_phases_sub),
                        checked = phasesOn,
                        onCheckedChange = { phasesOn = it }
                    )
                    if (phasesOn) {
                        NumberField(
                            value = phasesText,
                            onValueChange = { phasesText = it },
                            label = stringResource(R.string.create_phases_count),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // « Le plus bas gagne » est imposé par les décomptes (fléchettes), le score exact et le Tarot.
                if (activeVariant != GameVariant.EXACT_TARGET && activeVariant != GameVariant.COUNTDOWN &&
                    activeVariant != GameVariant.TAROT
                ) {
                    SettingRow(
                        title = stringResource(R.string.create_lowest_title),
                        subtitle = stringResource(R.string.create_lowest_sub),
                        checked = lowestWins,
                        onCheckedChange = { lowestWins = it }
                    )
                }

                if (scoreMode != ScoreMode.COUNTER && activeVariant == GameVariant.NONE) {
                    SettingRow(
                        title = stringResource(R.string.create_negative_title),
                        subtitle = stringResource(R.string.create_negative_sub),
                        checked = allowNegative,
                        onCheckedChange = { allowNegative = it }
                    )
                }

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

                if (!phasesActive && (activeVariant == GameVariant.NONE || activeVariant == GameVariant.BID ||
                        activeVariant == GameVariant.TAROT)
                ) {
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
                } else {
                    // Feuille, décompte, score exact, phases : la mécanique décide seule de la fin de partie.
                    Text(
                        text = stringResource(R.string.create_end_auto),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                        val draft = CustomGameDraft(
                            name = name,
                            scoreMode = scoreMode,
                            variant = activeVariant,
                            lowestWins = lowestWins,
                            allowNegative = allowNegative,
                            customMultipliers = customRules,
                            minPlayers = minPlayersText.toIntOrNull(),
                            maxPlayers = maxPlayersText.toIntOrNull(),
                            sheet = sheetId,
                            bidScoring = bidKind,
                            exactTarget = exactTargetText.toIntOrNull(),
                            exactReset = exactResetText.toIntOrNull(),
                            countdownFrom = countdownText.toIntOrNull(),
                            phases = if (phasesOn) phasesText.toIntOrNull() else null,
                            endCondition = endCondition,
                            tarotContracts = tarotContracts
                        )
                        // L'id est conservé en modification : les parties du journal restent liées au jeu.
                        val rules = draft.toRules(id = editing?.id ?: repository.newId(), fallbackName = untitledGame)
                        if (editing != null) {
                            repository.updateCustomGame(rules)
                            onGameUpdated()
                        } else {
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

/** Champ numérique : ne garde que les chiffres (6 au plus, pour rester dans un Int). */
@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { c -> c.isDigit() }.take(6)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

private fun GameVariant.labelRes(): Int = when (this) {
    GameVariant.NONE -> R.string.create_variant_classic
    GameVariant.SHEET -> R.string.create_variant_sheet
    GameVariant.BID -> R.string.create_variant_bid
    GameVariant.EXACT_TARGET -> R.string.create_variant_exact
    GameVariant.COUNTDOWN -> R.string.create_variant_countdown
    GameVariant.TAROT -> R.string.create_variant_tarot
}

/** Explication de la mécanique, ou null pour « classique » (le mode de score a déjà son texte). */
private fun GameVariant.descriptionRes(): Int? = when (this) {
    GameVariant.NONE -> null
    GameVariant.SHEET -> R.string.create_variant_sheet_desc
    GameVariant.BID -> R.string.create_variant_bid_desc
    GameVariant.EXACT_TARGET -> R.string.create_variant_exact_desc
    GameVariant.COUNTDOWN -> R.string.create_variant_countdown_desc
    GameVariant.TAROT -> R.string.create_variant_tarot_desc
}
