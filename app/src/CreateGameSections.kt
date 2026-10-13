package com.aventure.messcores

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

/** Choix du mode de score : tableau par manches, compteurs ou équipes variables. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScoreModeSection(form: CreateGameForm) {
    var scoreMode by form.scoreMode
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
}

/** Nombre minimum et maximum de joueurs (vide = valeur par défaut). */
@Composable
internal fun PlayersSection(form: CreateGameForm) {
    var minPlayersText by form.minPlayersText
    var maxPlayersText by form.maxPlayersText
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
}

/** Mécanique reprise d'un jeu prédéfini (feuille, annonces, score exact, décompte, Tarot), si le mode en propose. */
@Composable
internal fun VariantSection(form: CreateGameForm, activeVariant: GameVariant) {
    val variants = variantsFor(form.scoreMode.value)
    if (variants.size > 1) {
        VariantChips(form, variants, activeVariant)
        VariantOptions(form, activeVariant)
    }
}

/** Puces de choix de la mécanique, avec son explication. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VariantChips(form: CreateGameForm, variants: List<GameVariant>, activeVariant: GameVariant) {
    var variant by form.variant
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
}

/** Réglages propres à la mécanique choisie (feuille, annonces, score exact, décompte). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VariantOptions(form: CreateGameForm, activeVariant: GameVariant) {
    var sheetId by form.sheetId
    var sheetRowsText by form.sheetRowsText
    var bidKind by form.bidKind
    var exactTargetText by form.exactTargetText
    var exactResetText by form.exactResetText
    var countdownText by form.countdownText
    when (activeVariant) {
        GameVariant.SHEET -> {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    ScoreSheets.YAMS to R.string.game_yams,
                    ScoreSheets.QWIXX to R.string.game_qwixx,
                    ScoreSheets.BOWLING to R.string.game_bowling,
                    ScoreSheets.CUSTOM to R.string.create_sheet_custom_chip
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
                        ScoreSheets.CUSTOM -> R.string.create_sheet_custom_sub
                        else -> R.string.create_sheet_yams_sub
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (sheetId == ScoreSheets.CUSTOM) {
                OutlinedTextField(
                    value = sheetRowsText,
                    onValueChange = { sheetRowsText = it },
                    label = { Text(stringResource(R.string.create_sheet_custom_rows)) },
                    minLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                if (CustomSheetText.parse(sheetRowsText).isEmpty()) {
                    Text(
                        stringResource(R.string.create_sheet_custom_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
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

/** Suivi des phases (Phase 10) : tableau de manches libres uniquement. */
@Composable
internal fun PhasesSection(form: CreateGameForm, activeVariant: GameVariant) {
    val scoreMode by form.scoreMode
    var phasesOn by form.phasesOn
    var phasesText by form.phasesText
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
}

/** Interrupteurs « le plus bas gagne » et « scores négatifs », selon la mécanique. */
@Composable
internal fun ScoringTogglesSection(form: CreateGameForm, activeVariant: GameVariant) {
    val scoreMode by form.scoreMode
    var lowestWins by form.lowestWins
    var allowNegative by form.allowNegative
    val sheetId by form.sheetId
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

    if (scoreMode != ScoreMode.COUNTER &&
        (activeVariant == GameVariant.NONE || (activeVariant == GameVariant.SHEET && sheetId == ScoreSheets.CUSTOM))
    ) {
        SettingRow(
            title = stringResource(R.string.create_negative_title),
            subtitle = stringResource(R.string.create_negative_sub),
            checked = allowNegative,
            onCheckedChange = { allowNegative = it }
        )
    }
}
