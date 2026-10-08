package com.aventure.messcores

import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Une section d'une fiche de règles (ex : "But du jeu"). */
private data class RulesSection(@StringRes val title: Int, @StringRes val body: Int)

/** Règles rédigées pour les jeux prédéfinis, indexées par l'id du jeu (voir GameRepository). */
private val BUILT_IN_RULES: Map<String, List<RulesSection>> = mapOf(
    "builtin_generic" to listOf(
        RulesSection(R.string.rules_generic_1_title, R.string.rules_generic_1_body),
        RulesSection(R.string.rules_generic_2_title, R.string.rules_generic_2_body),
        RulesSection(R.string.rules_generic_3_title, R.string.rules_generic_3_body)
    ),
    "builtin_skyjo" to listOf(
        RulesSection(R.string.rules_skyjo_1_title, R.string.rules_skyjo_1_body),
        RulesSection(R.string.rules_skyjo_2_title, R.string.rules_skyjo_2_body),
        RulesSection(R.string.rules_skyjo_3_title, R.string.rules_skyjo_3_body),
        RulesSection(R.string.rules_skyjo_4_title, R.string.rules_skyjo_4_body),
        RulesSection(R.string.rules_skyjo_5_title, R.string.rules_skyjo_5_body)
    ),
    "builtin_tarot" to listOf(
        RulesSection(R.string.rules_tarot_1_title, R.string.rules_tarot_1_body),
        RulesSection(R.string.rules_tarot_2_title, R.string.rules_tarot_2_body),
        RulesSection(R.string.rules_tarot_3_title, R.string.rules_tarot_3_body),
        RulesSection(R.string.rules_tarot_4_title, R.string.rules_tarot_4_body),
        RulesSection(R.string.rules_tarot_5_title, R.string.rules_tarot_5_body),
        RulesSection(R.string.rules_tarot_6_title, R.string.rules_tarot_6_body)
    ),
    "builtin_belote" to listOf(
        RulesSection(R.string.rules_belote_1_title, R.string.rules_belote_1_body),
        RulesSection(R.string.rules_belote_2_title, R.string.rules_belote_2_body),
        RulesSection(R.string.rules_belote_3_title, R.string.rules_belote_3_body),
        RulesSection(R.string.rules_belote_4_title, R.string.rules_belote_4_body),
        RulesSection(R.string.rules_belote_5_title, R.string.rules_belote_5_body)
    ),
    "builtin_rami" to listOf(
        RulesSection(R.string.rules_rami_1_title, R.string.rules_rami_1_body),
        RulesSection(R.string.rules_rami_2_title, R.string.rules_rami_2_body),
        RulesSection(R.string.rules_rami_3_title, R.string.rules_rami_3_body),
        RulesSection(R.string.rules_rami_4_title, R.string.rules_rami_4_body)
    ),
    "builtin_uno" to listOf(
        RulesSection(R.string.rules_uno_1_title, R.string.rules_uno_1_body),
        RulesSection(R.string.rules_uno_2_title, R.string.rules_uno_2_body),
        RulesSection(R.string.rules_uno_3_title, R.string.rules_uno_3_body),
        RulesSection(R.string.rules_uno_4_title, R.string.rules_uno_4_body)
    ),
    "builtin_yams" to listOf(
        RulesSection(R.string.rules_yams_1_title, R.string.rules_yams_1_body),
        RulesSection(R.string.rules_yams_2_title, R.string.rules_yams_2_body),
        RulesSection(R.string.rules_yams_3_title, R.string.rules_yams_3_body),
        RulesSection(R.string.rules_yams_4_title, R.string.rules_yams_4_body),
        RulesSection(R.string.rules_yams_5_title, R.string.rules_yams_5_body)
    ),
    "builtin_421" to listOf(
        RulesSection(R.string.rules_421_1_title, R.string.rules_421_1_body),
        RulesSection(R.string.rules_421_2_title, R.string.rules_421_2_body),
        RulesSection(R.string.rules_421_3_title, R.string.rules_421_3_body),
        RulesSection(R.string.rules_421_4_title, R.string.rules_421_4_body),
        RulesSection(R.string.rules_421_5_title, R.string.rules_421_5_body)
    ),
    "builtin_cdc" to listOf(
        RulesSection(R.string.rules_cdc_1_title, R.string.rules_cdc_1_body),
        RulesSection(R.string.rules_cdc_2_title, R.string.rules_cdc_2_body),
        RulesSection(R.string.rules_cdc_3_title, R.string.rules_cdc_3_body),
        RulesSection(R.string.rules_cdc_4_title, R.string.rules_cdc_4_body),
        RulesSection(R.string.rules_cdc_5_title, R.string.rules_cdc_5_body)
    )
)

/**
 * Bouton « Règles » à afficher à la suite du nom du jeu. N'affiche rien pour les jeux
 * personnalisés, qui n'ont pas de règles rédigées.
 */
@Composable
fun RulesButton(rules: GameRules, modifier: Modifier = Modifier) {
    val sections = BUILT_IN_RULES[rules.id] ?: return
    var showDialog by rememberSaveable { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showDialog = true },
        modifier = modifier.height(32.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.9f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
    ) {
        Text(stringResource(R.string.rules_button), style = MaterialTheme.typography.labelLarge)
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.rules_dialog_title, rules.name)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    sections.forEachIndexed { index, section ->
                        if (index > 0) Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = stringResource(section.title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(section.body),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.common_close)) }
            }
        )
    }
}
