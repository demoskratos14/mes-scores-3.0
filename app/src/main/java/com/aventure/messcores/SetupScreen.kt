package com.aventure.messcores

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import org.json.JSONArray
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription

@Composable
fun SetupScreen(
    // Noms des joueurs, et une couleur par joueur (null = laissée au hasard).
    onNext: (List<String>, List<Color?>) -> Unit,
    onOpenJournal: () -> Unit,
    onOpenStats: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenTournament: () -> Unit,
    colorBlindMode: Boolean = false,
    onColorBlindModeChange: (Boolean) -> Unit = {},
    // Partie en cours la plus récente du journal (ou null) : proposée en haut de l'écran.
    unfinishedGame: SavedGame? = null,
    onResumeGame: () -> Unit = {},
    // Partager une partie avec un autre téléphone, ou rejoindre celle d'un autre.
    onOpenShare: () -> Unit = {}
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

    // Couleur choisie pour chaque joueur (couleur ARGB) ; 0 = pas de choix, tirée au sort au lancement.
    var chosenColors by rememberSaveable(
        stateSaver = listSaver<List<Int>, Int>(save = { it }, restore = { it })
    ) {
        mutableStateOf(List(playerCount) { 0 })
    }
    // Joueur dont la fenêtre de choix de couleur est ouverte.
    var colorPickerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val randomColorLabel = stringResource(R.string.score_color_random)

    // Ajuste la taille de la liste de noms quand le nombre de joueurs change,
    // en conservant les noms et les couleurs déjà saisis.
    LaunchedEffect(playerCount) {
        names = List(playerCount) { i -> names.getOrElse(i) { "" } }
        chosenColors = List(playerCount) { i -> chosenColors.getOrElse(i) { 0 } }
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
        // « Reprendre » : après une fermeture de l'appli par Android, la partie enregistrée
        // automatiquement se retrouve en un appui, sans passer par le journal.
        var dismissedGameId by rememberSaveable { mutableStateOf<String?>(null) }
        if (unfinishedGame != null && unfinishedGame.id != dismissedGameId) {
            Card(
                colors = CardDefaults.cardColors(containerColor = cardSurface()),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.resume_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = stringResource(
                            R.string.resume_summary,
                            unfinishedGame.gameRules.name,
                            unfinishedGame.players.joinToString(", ")
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onResumeGame) { Text(stringResource(R.string.history_resume)) }
                        TextButton(onClick = { dismissedGameId = unfinishedGame.id }) {
                            Text(stringResource(R.string.resume_dismiss))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.setup_title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f).semantics { heading() }
                    )
                    // Drapeau de la langue en cours : un appui permet d'en choisir une autre.
                    LanguageSelector()
                }

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
                            trailingIcon = {
                                val argb = chosenColors.getOrElse(index) { 0 }
                                val colorDescription = stringResource(R.string.setup_player_color, index + 1)
                                IconButton(
                                    onClick = { colorPickerIndex = index },
                                    modifier = Modifier.semantics {
                                        contentDescription = colorDescription
                                        if (argb == 0) stateDescription = randomColorLabel
                                    }
                                ) {
                                    if (argb != 0) {
                                        PlayerColorDot(color = Color(argb))
                                    } else {
                                        Icon(Icons.Filled.Palette, contentDescription = null)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                colorPickerIndex?.let { picked ->
                    if (picked in chosenColors.indices) {
                        PlayerColorDialog(
                            title = stringResource(R.string.setup_player_color, picked + 1),
                            name = null,
                            currentColor = chosenColors[picked].takeIf { it != 0 }?.let { Color(it) },
                            takenColors = chosenColors
                                .filterIndexed { i, argb -> i != picked && argb != 0 }
                                .map { Color(it) }
                                .toSet(),
                            onPick = { color ->
                                chosenColors = chosenColors.toMutableList().also { it[picked] = color.toArgb() }
                            },
                            onRandom = {
                                chosenColors = chosenColors.toMutableList().also { it[picked] = 0 }
                            },
                            onDismiss = { colorPickerIndex = null }
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
                        onNext(
                            PlayerNames.resolve(names, playerPrefix),
                            chosenColors.map { argb -> if (argb == 0) null else Color(argb) }
                        )
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

                OutlinedButton(
                    onClick = onOpenShare,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.share_join_button))
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
                    // Mode daltonien : un petit code (A, B, C…) s'écrit sur chaque couleur.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = colorBlindMode,
                                role = Role.Switch,
                                onValueChange = onColorBlindModeChange
                            )
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.setup_color_blind),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = stringResource(R.string.setup_color_blind_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = colorBlindMode, onCheckedChange = null)
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
