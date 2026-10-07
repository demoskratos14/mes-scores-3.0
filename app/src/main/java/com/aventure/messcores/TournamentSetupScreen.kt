package com.aventure.messcores

import androidx.compose.ui.res.stringResource
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun TournamentSetupScreen(
    /** Noms, format choisi, et — en élimination directe — vrai si le tableau suit l'ordre de la liste. */
    onNext: (List<String>, TournamentFormat, Boolean) -> Unit,
    onBack: () -> Unit
) {
    // rememberSaveable : les choix survivent à une rotation de l'écran (l'enum est sauvegardé par son nom).
    var formatName by rememberSaveable { mutableStateOf(TournamentFormat.KNOCKOUT.name) }
    val format = TournamentFormat.valueOf(formatName)
    var keepListOrder by rememberSaveable { mutableStateOf(false) }

    var playerCountText by rememberSaveable { mutableStateOf("4") }
    // Poules : n participants jouent n(n−1)/2 matchs, d'où une limite plus basse qu'en élimination directe.
    val maxCount = if (format == TournamentFormat.ROUND_ROBIN) MAX_POULE_PARTICIPANTS else MAX_KNOCKOUT_PARTICIPANTS
    val typedCount = playerCountText.toIntOrNull() ?: 4
    val playerCount = typedCount.coerceIn(2, maxCount)
    val context = LocalContext.current
    val participantPrefix = stringResource(R.string.participant_default)

    var names by rememberSaveable(stateSaver = StringListSaver) { mutableStateOf(List(playerCount) { "" }) }

    LaunchedEffect(playerCount) {
        names = List(playerCount) { i -> names.getOrElse(i) { "" } }
    }

    // Toute la page défile : avec le clavier ouvert, on peut faire remonter les champs
    // (et le bouton) sans avoir à fermer le clavier.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(24.dp),
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
                Text(stringResource(R.string.new_championship), style = MaterialTheme.typography.headlineMedium)

                Column(modifier = Modifier.selectableGroup()) {
                    Text(stringResource(R.string.tsetup_format), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChoiceRow(
                        selected = format == TournamentFormat.KNOCKOUT,
                        title = stringResource(TournamentFormat.KNOCKOUT.labelRes),
                        subtitle = stringResource(R.string.tsetup_knockout_desc),
                        onClick = { formatName = TournamentFormat.KNOCKOUT.name }
                    )
                    ChoiceRow(
                        selected = format == TournamentFormat.ROUND_ROBIN,
                        title = stringResource(TournamentFormat.ROUND_ROBIN.labelRes),
                        subtitle = stringResource(R.string.tsetup_round_robin_desc, MAX_POULE_PARTICIPANTS),
                        onClick = { formatName = TournamentFormat.ROUND_ROBIN.name }
                    )
                }

                if (format == TournamentFormat.KNOCKOUT) {
                    Column(modifier = Modifier.selectableGroup()) {
                        Text(stringResource(R.string.tsetup_bracket), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ChoiceRow(
                            selected = !keepListOrder,
                            title = stringResource(R.string.tsetup_draw),
                            subtitle = stringResource(R.string.tsetup_draw_desc),
                            onClick = { keepListOrder = false }
                        )
                        ChoiceRow(
                            selected = keepListOrder,
                            title = stringResource(R.string.tsetup_list_order),
                            subtitle = stringResource(R.string.tsetup_list_order_desc),
                            onClick = { keepListOrder = true }
                        )
                    }
                }

                OutlinedTextField(
                    value = playerCountText,
                    onValueChange = { input -> playerCountText = input.filter { it.isDigit() } },
                    label = { Text(stringResource(R.string.tsetup_count)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (typedCount > maxCount) {
                    Text(
                        text = stringResource(R.string.tsetup_max, maxCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    names.forEachIndexed { index, name ->
                        OutlinedTextField(
                            value = name,
                            onValueChange = { newName ->
                                names = names.toMutableList().also { it[index] = newName }
                            },
                            label = { Text(stringResource(R.string.tsetup_participant, index + 1)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Prévient avant de continuer si des noms identiques vont être numérotés.
                val duplicateNotice = duplicateNoticeText(context, names, participantPrefix)
                if (duplicateNotice != null) {
                    Text(
                        text = duplicateNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(
                    onClick = {
                        onNext(
                            PlayerNames.resolve(names, participantPrefix),
                            format,
                            format == TournamentFormat.KNOCKOUT && keepListOrder
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            when {
                                format == TournamentFormat.ROUND_ROBIN -> R.string.tsetup_start_round_robin
                                keepListOrder -> R.string.tsetup_start
                                else -> R.string.tsetup_start_draw
                            }
                        )
                    )
                }

                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.common_back))
                }
            }
        }
        }
    }
}

private const val MAX_KNOCKOUT_PARTICIPANTS = 32
private const val MAX_POULE_PARTICIPANTS = 12

/** Une option exclusive (bouton radio + titre + explication) ; toute la ligne est cliquable. */
@Composable
private fun ChoiceRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        // onClick = null : le clic est géré par la ligne entière (une seule cible pour TalkBack).
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
