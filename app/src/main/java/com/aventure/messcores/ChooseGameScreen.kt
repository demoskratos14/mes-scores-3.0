package com.aventure.messcores

import androidx.compose.ui.res.stringResource
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role

/** Jeux pour lesquels le seuil de fin de partie (500/1000 points) est ajustable ici. */
private val LONG_GAME_TOGGLE_IDS = setOf("builtin_belote", "builtin_rami")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChooseGameScreen(
    repository: GameRepository,
    playerCount: Int,
    onGameChosen: (GameRules) -> Unit,
    onCreateNewGame: () -> Unit,
    onEditGame: (GameRules) -> Unit
) {
    var games by remember { mutableStateOf(repository.allGames()) }
    // On retient l'id (et non le jeu) : la sélection survit au passage par le formulaire de modification.
    var selectedId by rememberSaveable { mutableStateOf("builtin_generic") }
    val selected = games.firstOrNull { it.id == selectedId } ?: games.first()
    val context = LocalContext.current
    var expandedCategory by remember { mutableStateOf<GameCategory?>(null) }
    var longGame by remember { mutableStateOf(false) }
    var genericMode by remember { mutableStateOf(ScoreMode.TABLE) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
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
                Text(stringResource(R.string.choose_title), style = MaterialTheme.typography.headlineMedium)

                // Un menu déroulant par rubrique : chaque liste reste courte. Le jeu choisi s'affiche
                // dans le menu de sa rubrique, les autres menus restent vides.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameCategory.entries.forEach { category ->
                        val inCategory = games.filter { it.category() == category }
                        if (inCategory.isNotEmpty()) {
                            val isOpen = expandedCategory == category
                            ExposedDropdownMenuBox(
                                expanded = isOpen,
                                onExpandedChange = { expandedCategory = if (it) category else null }
                            ) {
                                OutlinedTextField(
                                    value = if (selected.category() == category) selected.name else "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(category.label)) },
                                    placeholder = { Text(stringResource(R.string.choose_pick)) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isOpen) },
                                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = isOpen,
                                    onDismissRequest = { expandedCategory = null }
                                ) {
                                    inCategory.forEach { game ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(game.name)
                                                    Text(
                                                        text = ruleSummary(context, game),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            onClick = {
                                                selectedId = game.id
                                                errorMessage = null
                                                expandedCategory = null
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    text = ruleSummary(context, selected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (selected.id == "builtin_generic") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.score_mode), style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = genericMode == ScoreMode.TABLE,
                                onClick = { genericMode = ScoreMode.TABLE },
                                label = { Text(stringResource(R.string.score_mode_table)) }
                            )
                            FilterChip(
                                selected = genericMode == ScoreMode.COUNTER,
                                onClick = { genericMode = ScoreMode.COUNTER },
                                label = { Text(stringResource(R.string.score_mode_counter)) }
                            )
                        }
                    }
                }

                if (selected.id in LONG_GAME_TOGGLE_IDS) {
                    // La ligne entière est l'interrupteur (une seule cible, avec son texte, pour TalkBack).
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = longGame, role = Role.Switch, onValueChange = { longGame = it }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.choose_long_game))
                            Text(
                                text = stringResource(if (longGame) R.string.choose_end_1000 else R.string.choose_end_500),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = longGame, onCheckedChange = null)
                    }
                }

                // Seuls les jeux créés par l'utilisateur sont modifiables ou supprimables.
                if (!selected.id.startsWith("builtin_")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onEditGame(selected) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.choose_edit_game))
                        }
                        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                OutlinedButton(
                    onClick = onCreateNewGame,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.choose_create_game))
                }

                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(
                    onClick = {
                        errorMessage = playerCountError(context, selected, playerCount)
                        if (errorMessage != null) return@Button
                        val finalRules = when {
                            selected.id == "builtin_generic" -> selected.copy(scoreMode = genericMode)
                            selected.id in LONG_GAME_TOGGLE_IDS -> selected.copy(
                                endCondition = EndCondition(
                                    type = EndConditionType.SCORE_THRESHOLD,
                                    scoreThreshold = if (longGame) 1000 else 500
                                )
                            )
                            else -> selected
                        }
                        onGameChosen(finalRules)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.common_continue))
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.choose_delete_title, selected.name)) },
            text = { Text(stringResource(R.string.choose_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    repository.deleteCustomGame(selected.id)
                    games = repository.allGames()
                    selectedId = "builtin_generic"
                    errorMessage = null
                    confirmDelete = false
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

/** Message d'erreur si le nombre de joueurs ne convient pas au jeu, sinon null. */
private fun playerCountError(context: Context, game: GameRules, playerCount: Int): String? {
    val entered = context.resources.getQuantityString(R.plurals.players_count, playerCount, playerCount)
    return when {
        playerCount < game.minPlayers ->
            context.getString(R.string.choose_error_too_few, game.name, playersRange(context, game), entered, game.minPlayers)
        playerCount > game.maxPlayers ->
            context.getString(R.string.choose_error_too_many, game.name, playersRange(context, game), entered, game.maxPlayers)
        else -> null
    }
}

private fun playersRange(context: Context, game: GameRules): String =
    if (game.minPlayers == game.maxPlayers) context.resources.getQuantityString(R.plurals.players_count, game.minPlayers, game.minPlayers)
    else context.getString(R.string.choose_players_range, game.minPlayers, game.maxPlayers)

private fun ruleSummary(context: Context, game: GameRules): String {
    val direction = context.getString(if (game.lowestWins) R.string.rule_lowest_wins else R.string.rule_highest_wins)
    val negative = context.getString(if (game.allowNegativeScores) R.string.rule_negatives_allowed else R.string.rule_positives_only)
    val players = if (game.minPlayers == GameRules.DEFAULT_MIN_PLAYERS && game.maxPlayers == GameRules.DEFAULT_MAX_PLAYERS) ""
    else context.getString(R.string.choose_rule_players_suffix, playersRange(context, game))
    return context.getString(R.string.choose_rule_summary, direction, negative, players)
}
