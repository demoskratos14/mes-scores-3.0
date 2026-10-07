package com.aventure.messcores

import androidx.compose.ui.res.stringResource
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import org.json.JSONArray

@Composable
fun SetupScreen(
    onNext: (List<String>) -> Unit,
    onOpenJournal: () -> Unit,
    onOpenStats: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenTournament: () -> Unit
) {
    val context = LocalContext.current
    val playerPrefix = stringResource(R.string.player_default)
    // Noms de la dernière partie lancée, pour ne pas les retaper à chaque fois.
    val savedNames = remember { loadLastPlayerNames(context) }

    var playerCountText by rememberSaveable {
        mutableStateOf((savedNames.size.takeIf { it >= 2 } ?: 2).toString())
    }
    val playerCount = (playerCountText.toIntOrNull() ?: 2).coerceIn(1, 12)

    var names by rememberSaveable(
        stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it })
    ) {
        mutableStateOf(List(playerCount) { i -> savedNames.getOrElse(i) { "" } })
    }

    // Ajuste la taille de la liste de noms quand le nombre de joueurs change,
    // en conservant les noms déjà saisis.
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
            colors = CardDefaults.cardColors(
                containerColor = cardSurface()
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.setup_title),
                    style = MaterialTheme.typography.headlineMedium
                )

                OutlinedTextField(
                    value = playerCountText,
                    onValueChange = { input -> playerCountText = input.filter { it.isDigit() } },
                    label = { Text(stringResource(R.string.setup_player_count)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

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
                            label = { Text(stringResource(R.string.setup_player_name, index + 1)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Prévient avant de continuer si des noms identiques vont être numérotés.
                val duplicateNotice = duplicateNoticeText(context, names, playerPrefix)
                if (duplicateNotice != null) {
                    Text(
                        text = duplicateNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(
                    onClick = {
                        saveLastPlayerNames(context, names)
                        onNext(PlayerNames.resolve(names, playerPrefix))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.common_continue))
                }

                OutlinedButton(
                    onClick = onOpenTournament,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.new_championship))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onOpenJournal, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.journal_title))
                    }
                    TextButton(onClick = onOpenStats, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.stats_title))
                    }
                }

                // Apparence : Auto suit le réglage clair / sombre du téléphone.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.setup_appearance),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.values().forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { onThemeModeChange(mode) },
                                label = { Text(stringResource(mode.labelRes)) }
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

private const val SETUP_PREFS = "mes_scores_setup"
private const val KEY_LAST_NAMES = "last_player_names"

private fun loadLastPlayerNames(context: Context): List<String> = try {
    val json = context.getSharedPreferences(SETUP_PREFS, Context.MODE_PRIVATE)
        .getString(KEY_LAST_NAMES, null)
    if (json == null) {
        emptyList()
    } else {
        val array = JSONArray(json)
        (0 until array.length()).map { array.getString(it) }
    }
} catch (e: Exception) {
    emptyList()
}

private fun saveLastPlayerNames(context: Context, names: List<String>) {
    context.getSharedPreferences(SETUP_PREFS, Context.MODE_PRIVATE).edit {
        putString(KEY_LAST_NAMES, JSONArray(names).toString())
    }
}
