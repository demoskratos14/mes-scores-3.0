package com.aventure.messcores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

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
    val form = rememberCreateGameForm(editing)
    val scoreMode = form.scoreMode.value

    // La mécanique n'est valable que pour certains modes : on retombe sur « classique » sinon.
    val activeVariant = if (form.variant.value in variantsFor(scoreMode)) form.variant.value else GameVariant.NONE
    val phasesActive = scoreMode == ScoreMode.TABLE && activeVariant == GameVariant.NONE && form.phasesOn.value
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
                    value = form.name.value,
                    onValueChange = { form.name.value = it },
                    label = { Text(stringResource(R.string.create_name)) },
                    modifier = Modifier.fillMaxWidth()
                )

                ScoreModeSection(form)
                PlayersSection(form)
                VariantSection(form, activeVariant)
                PhasesSection(form, activeVariant)
                ScoringTogglesSection(form, activeVariant)
                MultiplierRulesSection(form, activeVariant)

                if (!phasesActive && (activeVariant == GameVariant.NONE || activeVariant == GameVariant.BID ||
                        activeVariant == GameVariant.TAROT)
                ) {
                    EndConditionSection(form)
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
                        val draft = form.toDraft(activeVariant, tarotContracts)
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
