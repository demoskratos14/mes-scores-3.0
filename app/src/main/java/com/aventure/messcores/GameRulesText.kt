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
    ),
    "builtin_dixmille" to listOf(
        RulesSection(R.string.rules_dixmille_1_title, R.string.rules_dixmille_1_body),
        RulesSection(R.string.rules_dixmille_2_title, R.string.rules_dixmille_2_body),
        RulesSection(R.string.rules_dixmille_3_title, R.string.rules_dixmille_3_body),
        RulesSection(R.string.rules_dixmille_4_title, R.string.rules_dixmille_4_body)
    ),
    "builtin_cochon" to listOf(
        RulesSection(R.string.rules_cochon_1_title, R.string.rules_cochon_1_body),
        RulesSection(R.string.rules_cochon_2_title, R.string.rules_cochon_2_body),
        RulesSection(R.string.rules_cochon_3_title, R.string.rules_cochon_3_body)
    ),
    "builtin_zombie" to listOf(
        RulesSection(R.string.rules_zombie_1_title, R.string.rules_zombie_1_body),
        RulesSection(R.string.rules_zombie_2_title, R.string.rules_zombie_2_body),
        RulesSection(R.string.rules_zombie_3_title, R.string.rules_zombie_3_body),
        RulesSection(R.string.rules_zombie_4_title, R.string.rules_zombie_4_body)
    ),
    "builtin_shutbox" to listOf(
        RulesSection(R.string.rules_shutbox_1_title, R.string.rules_shutbox_1_body),
        RulesSection(R.string.rules_shutbox_2_title, R.string.rules_shutbox_2_body),
        RulesSection(R.string.rules_shutbox_3_title, R.string.rules_shutbox_3_body),
        RulesSection(R.string.rules_shutbox_4_title, R.string.rules_shutbox_4_body)
    ),
    "builtin_bunco" to listOf(
        RulesSection(R.string.rules_bunco_1_title, R.string.rules_bunco_1_body),
        RulesSection(R.string.rules_bunco_2_title, R.string.rules_bunco_2_body),
        RulesSection(R.string.rules_bunco_3_title, R.string.rules_bunco_3_body)
    ),
    "builtin_kot" to listOf(
        RulesSection(R.string.rules_kot_1_title, R.string.rules_kot_1_body),
        RulesSection(R.string.rules_kot_2_title, R.string.rules_kot_2_body),
        RulesSection(R.string.rules_kot_3_title, R.string.rules_kot_3_body),
        RulesSection(R.string.rules_kot_4_title, R.string.rules_kot_4_body)
    ),
    "builtin_mexicain" to listOf(
        RulesSection(R.string.rules_mexicain_1_title, R.string.rules_mexicain_1_body),
        RulesSection(R.string.rules_mexicain_2_title, R.string.rules_mexicain_2_body),
        RulesSection(R.string.rules_mexicain_3_title, R.string.rules_mexicain_3_body),
        RulesSection(R.string.rules_mexicain_4_title, R.string.rules_mexicain_4_body)
    ),
    "builtin_qwixx" to listOf(
        RulesSection(R.string.rules_qwixx_1_title, R.string.rules_qwixx_1_body),
        RulesSection(R.string.rules_qwixx_2_title, R.string.rules_qwixx_2_body),
        RulesSection(R.string.rules_qwixx_3_title, R.string.rules_qwixx_3_body),
        RulesSection(R.string.rules_qwixx_4_title, R.string.rules_qwixx_4_body),
        RulesSection(R.string.rules_qwixx_5_title, R.string.rules_qwixx_5_body)
    ),
    "builtin_coinche" to listOf(
        RulesSection(R.string.rules_coinche_1_title, R.string.rules_coinche_1_body),
        RulesSection(R.string.rules_coinche_2_title, R.string.rules_coinche_2_body),
        RulesSection(R.string.rules_coinche_3_title, R.string.rules_coinche_3_body),
        RulesSection(R.string.rules_coinche_4_title, R.string.rules_coinche_4_body)
    ),
    "builtin_president" to listOf(
        RulesSection(R.string.rules_president_1_title, R.string.rules_president_1_body),
        RulesSection(R.string.rules_president_2_title, R.string.rules_president_2_body),
        RulesSection(R.string.rules_president_3_title, R.string.rules_president_3_body),
        RulesSection(R.string.rules_president_4_title, R.string.rules_president_4_body),
        RulesSection(R.string.rules_president_5_title, R.string.rules_president_5_body)
    ),
    "builtin_coeurs" to listOf(
        RulesSection(R.string.rules_coeurs_1_title, R.string.rules_coeurs_1_body),
        RulesSection(R.string.rules_coeurs_2_title, R.string.rules_coeurs_2_body),
        RulesSection(R.string.rules_coeurs_3_title, R.string.rules_coeurs_3_body),
        RulesSection(R.string.rules_coeurs_4_title, R.string.rules_coeurs_4_body)
    ),
    "builtin_pique" to listOf(
        RulesSection(R.string.rules_pique_1_title, R.string.rules_pique_1_body),
        RulesSection(R.string.rules_pique_2_title, R.string.rules_pique_2_body),
        RulesSection(R.string.rules_pique_3_title, R.string.rules_pique_3_body),
        RulesSection(R.string.rules_pique_4_title, R.string.rules_pique_4_body)
    ),
    "builtin_gin" to listOf(
        RulesSection(R.string.rules_gin_1_title, R.string.rules_gin_1_body),
        RulesSection(R.string.rules_gin_2_title, R.string.rules_gin_2_body),
        RulesSection(R.string.rules_gin_3_title, R.string.rules_gin_3_body),
        RulesSection(R.string.rules_gin_4_title, R.string.rules_gin_4_body)
    ),
    "builtin_huit" to listOf(
        RulesSection(R.string.rules_huit_1_title, R.string.rules_huit_1_body),
        RulesSection(R.string.rules_huit_2_title, R.string.rules_huit_2_body),
        RulesSection(R.string.rules_huit_3_title, R.string.rules_huit_3_body),
        RulesSection(R.string.rules_huit_4_title, R.string.rules_huit_4_body)
    ),
    "builtin_cribbage" to listOf(
        RulesSection(R.string.rules_cribbage_1_title, R.string.rules_cribbage_1_body),
        RulesSection(R.string.rules_cribbage_2_title, R.string.rules_cribbage_2_body),
        RulesSection(R.string.rules_cribbage_3_title, R.string.rules_cribbage_3_body),
        RulesSection(R.string.rules_cribbage_4_title, R.string.rules_cribbage_4_body)
    ),
    "builtin_milleb" to listOf(
        RulesSection(R.string.rules_milleb_1_title, R.string.rules_milleb_1_body),
        RulesSection(R.string.rules_milleb_2_title, R.string.rules_milleb_2_body),
        RulesSection(R.string.rules_milleb_3_title, R.string.rules_milleb_3_body),
        RulesSection(R.string.rules_milleb_4_title, R.string.rules_milleb_4_body)
    ),
    "builtin_sixqp" to listOf(
        RulesSection(R.string.rules_sixqp_1_title, R.string.rules_sixqp_1_body),
        RulesSection(R.string.rules_sixqp_2_title, R.string.rules_sixqp_2_body),
        RulesSection(R.string.rules_sixqp_3_title, R.string.rules_sixqp_3_body),
        RulesSection(R.string.rules_sixqp_4_title, R.string.rules_sixqp_4_body)
    ),
    "builtin_papayoo" to listOf(
        RulesSection(R.string.rules_papayoo_1_title, R.string.rules_papayoo_1_body),
        RulesSection(R.string.rules_papayoo_2_title, R.string.rules_papayoo_2_body),
        RulesSection(R.string.rules_papayoo_3_title, R.string.rules_papayoo_3_body)
    ),
    "builtin_cabo" to listOf(
        RulesSection(R.string.rules_cabo_1_title, R.string.rules_cabo_1_body),
        RulesSection(R.string.rules_cabo_2_title, R.string.rules_cabo_2_body),
        RulesSection(R.string.rules_cabo_3_title, R.string.rules_cabo_3_body),
        RulesSection(R.string.rules_cabo_4_title, R.string.rules_cabo_4_body)
    ),
    "builtin_manille" to listOf(
        RulesSection(R.string.rules_manille_1_title, R.string.rules_manille_1_body),
        RulesSection(R.string.rules_manille_2_title, R.string.rules_manille_2_body),
        RulesSection(R.string.rules_manille_3_title, R.string.rules_manille_3_body),
        RulesSection(R.string.rules_manille_4_title, R.string.rules_manille_4_body),
        RulesSection(R.string.rules_manille_5_title, R.string.rules_manille_5_body)
    ),
    "builtin_scopa" to listOf(
        RulesSection(R.string.rules_scopa_1_title, R.string.rules_scopa_1_body),
        RulesSection(R.string.rules_scopa_2_title, R.string.rules_scopa_2_body),
        RulesSection(R.string.rules_scopa_3_title, R.string.rules_scopa_3_body),
        RulesSection(R.string.rules_scopa_4_title, R.string.rules_scopa_4_body)
    ),
    "builtin_yaniv" to listOf(
        RulesSection(R.string.rules_yaniv_1_title, R.string.rules_yaniv_1_body),
        RulesSection(R.string.rules_yaniv_2_title, R.string.rules_yaniv_2_body),
        RulesSection(R.string.rules_yaniv_3_title, R.string.rules_yaniv_3_body),
        RulesSection(R.string.rules_yaniv_4_title, R.string.rules_yaniv_4_body)
    ),
    "builtin_barbu" to listOf(
        RulesSection(R.string.rules_barbu_1_title, R.string.rules_barbu_1_body),
        RulesSection(R.string.rules_barbu_2_title, R.string.rules_barbu_2_body),
        RulesSection(R.string.rules_barbu_3_title, R.string.rules_barbu_3_body)
    ),
    "builtin_ohhell" to listOf(
        RulesSection(R.string.rules_ohhell_1_title, R.string.rules_ohhell_1_body),
        RulesSection(R.string.rules_ohhell_2_title, R.string.rules_ohhell_2_body),
        RulesSection(R.string.rules_ohhell_3_title, R.string.rules_ohhell_3_body),
        RulesSection(R.string.rules_ohhell_4_title, R.string.rules_ohhell_4_body)
    ),
    "builtin_rummikub" to listOf(
        RulesSection(R.string.rules_rummikub_1_title, R.string.rules_rummikub_1_body),
        RulesSection(R.string.rules_rummikub_2_title, R.string.rules_rummikub_2_body),
        RulesSection(R.string.rules_rummikub_3_title, R.string.rules_rummikub_3_body),
        RulesSection(R.string.rules_rummikub_4_title, R.string.rules_rummikub_4_body)
    ),
    "builtin_dominos" to listOf(
        RulesSection(R.string.rules_dominos_1_title, R.string.rules_dominos_1_body),
        RulesSection(R.string.rules_dominos_2_title, R.string.rules_dominos_2_body),
        RulesSection(R.string.rules_dominos_3_title, R.string.rules_dominos_3_body),
        RulesSection(R.string.rules_dominos_4_title, R.string.rules_dominos_4_body)
    ),
    "builtin_wizard" to listOf(
        RulesSection(R.string.rules_wizard_1_title, R.string.rules_wizard_1_body),
        RulesSection(R.string.rules_wizard_2_title, R.string.rules_wizard_2_body),
        RulesSection(R.string.rules_wizard_3_title, R.string.rules_wizard_3_body),
        RulesSection(R.string.rules_wizard_4_title, R.string.rules_wizard_4_body)
    ),
    "builtin_phase10" to listOf(
        RulesSection(R.string.rules_phase10_1_title, R.string.rules_phase10_1_body),
        RulesSection(R.string.rules_phase10_2_title, R.string.rules_phase10_2_body),
        RulesSection(R.string.rules_phase10_3_title, R.string.rules_phase10_3_body),
        RulesSection(R.string.rules_phase10_4_title, R.string.rules_phase10_4_body)
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
