package com.aventure.messcores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/** Tapis de départ proposés d'un geste ; le champ permet d'écrire n'importe quelle valeur. */
private val STACK_PRESETS = listOf(1000, 2000, 5000, 10000, 20000)

/** Durées de niveau proposées d'un geste (en minutes). */
private val MINUTE_PRESETS = listOf(5, 10, 15, 20, 30)

/** Nombre de niveaux montrés dans l'aperçu de la structure des blinds. */
private const val PREVIEW_LEVELS = 6

/**
 * Création d'une partie de poker : tapis de départ, blinds, durée d'un niveau, antes (et à partir de
 * combien d'augmentations de blinds), et joueur qui a le bouton à la première main.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PokerSetupScreen(
    playerNames: List<String>,
    onStart: (PokerSettings) -> Unit
) {
    var stackText by rememberSaveable { mutableStateOf("5000") }
    var smallText by rememberSaveable { mutableStateOf("25") }
    var bigText by rememberSaveable { mutableStateOf("50") }
    // Tant que la grosse blind n'a pas été modifiée à la main, elle suit la petite (le double).
    var bigEdited by rememberSaveable { mutableStateOf(false) }
    var minutesText by rememberSaveable { mutableStateOf("15") }
    var anteOn by rememberSaveable { mutableStateOf(false) }
    var anteAfterText by rememberSaveable { mutableStateOf("3") }
    // -1 = bouton tiré au sort.
    var dealerChoice by rememberSaveable { mutableStateOf(-1) }

    val stack = stackText.toIntOrNull()
    val small = smallText.toIntOrNull()
    val big = bigText.toIntOrNull()
    val minutes = minutesText.toIntOrNull()
    val anteAfter = anteAfterText.toIntOrNull()

    val valid = stack != null && small != null && big != null && minutes != null && anteAfter != null &&
        small >= 1 && big >= small && stack > big && minutes in 0..600 && anteAfter in 0..99

    val previewSettings = if (valid && stack != null && small != null && big != null && minutes != null && anteAfter != null) {
        PokerSettings(
            startingStack = stack,
            smallBlind = small,
            bigBlind = big,
            levelMinutes = minutes,
            anteEnabled = anteOn,
            anteAfterIncreases = anteAfter,
            firstDealer = 0
        )
    } else {
        null
    }

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
                Text(stringResource(R.string.poker_setup_title), style = MaterialTheme.typography.headlineMedium)

                // Tapis de départ
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PokerNumberField(
                        value = stackText,
                        onChange = { stackText = it },
                        label = stringResource(R.string.poker_setup_stack)
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        STACK_PRESETS.forEach { preset ->
                            FilterChip(
                                selected = stack == preset,
                                onClick = { stackText = preset.toString() },
                                label = { Text(preset.toString()) }
                            )
                        }
                    }
                }

                // Blinds
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PokerNumberField(
                        value = smallText,
                        onChange = { value ->
                            smallText = value
                            val parsed = value.toIntOrNull()
                            if (!bigEdited && parsed != null) bigText = (parsed * 2).toString()
                        },
                        label = stringResource(R.string.poker_setup_small_blind),
                        modifier = Modifier.weight(1f)
                    )
                    PokerNumberField(
                        value = bigText,
                        onChange = { value ->
                            bigText = value
                            bigEdited = true
                        },
                        label = stringResource(R.string.poker_setup_big_blind),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Durée d'un niveau
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PokerNumberField(
                        value = minutesText,
                        onChange = { minutesText = it },
                        label = stringResource(R.string.poker_setup_level_minutes),
                        supporting = stringResource(R.string.poker_setup_level_minutes_hint)
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MINUTE_PRESETS.forEach { preset ->
                            FilterChip(
                                selected = minutes == preset,
                                onClick = { minutesText = preset.toString() },
                                label = { Text(preset.toString()) }
                            )
                        }
                    }
                }

                // Antes : la ligne entière est l'interrupteur (une seule cible pour TalkBack).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = anteOn, role = Role.Switch, onValueChange = { anteOn = it }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.poker_setup_ante))
                        Text(
                            text = stringResource(R.string.poker_setup_ante_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = anteOn, onCheckedChange = null)
                }
                if (anteOn) {
                    PokerNumberField(
                        value = anteAfterText,
                        onChange = { anteAfterText = it },
                        label = stringResource(R.string.poker_setup_ante_after),
                        supporting = stringResource(R.string.poker_setup_ante_after_hint)
                    )
                }

                // Joueur au bouton à la première main
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.poker_setup_dealer), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = dealerChoice < 0,
                            onClick = { dealerChoice = -1 },
                            label = { Text(stringResource(R.string.poker_setup_dealer_random)) }
                        )
                        playerNames.forEachIndexed { index, name ->
                            FilterChip(
                                selected = dealerChoice == index,
                                onClick = { dealerChoice = index },
                                label = { Text(name) }
                            )
                        }
                    }
                }

                // Aperçu de la structure : on voit tout de suite comment les blinds vont monter.
                if (previewSettings != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.poker_setup_structure), style = MaterialTheme.typography.titleSmall)
                        for (level in 1..PREVIEW_LEVELS) {
                            val levelSmall = PokerBlinds.smallBlindAt(previewSettings, level)
                            val levelBig = PokerBlinds.bigBlindAt(previewSettings, level)
                            val levelAnte = PokerBlinds.anteAt(previewSettings, level)
                            Text(
                                text = if (levelAnte > 0) {
                                    stringResource(R.string.poker_level_line_ante, level, levelSmall, levelBig, levelAnte)
                                } else {
                                    stringResource(R.string.poker_level_line, level, levelSmall, levelBig)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.poker_setup_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(
                    onClick = {
                        val settings = previewSettings ?: return@Button
                        val dealer = if (dealerChoice in playerNames.indices) dealerChoice else Random.nextInt(playerNames.size)
                        onStart(settings.copy(firstDealer = dealer))
                    },
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.poker_setup_start))
                }
            }
        }
    }
}

@Composable
private fun PokerNumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() }.take(9)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = if (supporting != null) {
            { Text(supporting) }
        } else {
            null
        },
        modifier = modifier.fillMaxWidth()
    )
}
