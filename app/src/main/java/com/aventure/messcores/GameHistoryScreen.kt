package com.aventure.messcores

import androidx.compose.ui.res.stringResource
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun historyDate(context: Context, millis: Long): String =
    SimpleDateFormat(context.getString(R.string.history_date_pattern), Locale.getDefault()).format(Date(millis))

/**
 * Journal des parties : liste, triée de la plus récente à la plus ancienne, des parties
 * enregistrées via le bouton "Enregistrer" des écrans de score. Une partie en cours peut
 * être reprise là où elle en était ; une partie terminée peut être revue.
 */
@Composable
fun GameHistoryScreen(
    repository: GameHistoryRepository,
    onResumeGame: (SavedGame) -> Unit,
    onBack: () -> Unit
) {
    var games by remember { mutableStateOf(repository.listGames()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Résultat du dernier export/import, affiché sous les boutons.
    var status by remember { mutableStateOf<String?>(null) }

    // Sélecteurs de fichier d'Android (aucune permission nécessaire) : l'utilisateur choisit
    // lui-même où enregistrer la sauvegarde ou quel fichier relire.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                status = withContext(Dispatchers.IO) {
                    try {
                        val json = repository.exportJson()
                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                            ?: throw IOException(context.getString(R.string.history_stream_unavailable))
                        context.getString(R.string.history_export_ok, context.quantity(R.plurals.games_count, repository.listGames().size))
                    } catch (e: Exception) {
                        context.getString(R.string.history_export_failed, e.message ?: context.getString(R.string.error_unknown))
                    }
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val message = withContext(Dispatchers.IO) {
                    try {
                        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                            ?: throw IOException(context.getString(R.string.history_file_unreadable))
                        var error: JournalBackup.ImportError? = null
                        val summary = repository.importJson(text) { error = it }
                        if (summary == null) importErrorText(context, error) else importMessage(context, summary)
                    } catch (e: Exception) {
                        context.getString(R.string.history_import_failed, e.message ?: context.getString(R.string.error_unknown))
                    }
                }
                games = repository.listGames()
                status = message
            }
        }
    }
    var gameToDelete by remember { mutableStateOf<SavedGame?>(null) }
    var confirmClearFinished by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }

        Text(
            text = stringResource(R.string.journal_title),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (games.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.history_limit, GameHistoryRepository.MAX_SAVED_GAMES),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f)
                )
                if (games.any { it.isFinished }) {
                    TextButton(onClick = { confirmClearFinished = true }) { Text(stringResource(R.string.history_clear_finished)) }
                }
            }
        }

        // Sauvegarde / restauration : à garder avant un changement de téléphone ou une désinstallation.
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val secondaryColors = ButtonDefaults.outlinedButtonColors(containerColor = cardSurface())
            OutlinedButton(
                onClick = {
                    exportLauncher.launch("mes-scores-journal-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.json")
                },
                enabled = games.isNotEmpty(),
                colors = secondaryColors,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.history_export)) }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                colors = secondaryColors,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.history_import)) }
        }
        status?.let { message ->
            Card(
                colors = CardDefaults.cardColors(containerColor = cardSurface()),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { status = null }) { Text(stringResource(R.string.common_ok)) }
                }
            }
        }

        if (games.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = cardSurface()),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.history_empty),
                    modifier = Modifier.padding(20.dp)
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(games, key = { it.id }) { game ->
                    GameHistoryRow(
                        game = game,
                        onResume = { onResumeGame(game) },
                        onDelete = { gameToDelete = game }
                    )
                }
            }
        }
    }

    if (confirmClearFinished) {
        val count = games.count { it.isFinished }
        AlertDialog(
            onDismissRequest = { confirmClearFinished = false },
            confirmButton = {
                TextButton(onClick = {
                    repository.deleteFinishedGames()
                    games = repository.listGames()
                    confirmClearFinished = false
                }) { Text(stringResource(R.string.history_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearFinished = false }) { Text(stringResource(R.string.common_cancel)) }
            },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_message, count)) }
        )
    }

    gameToDelete?.let { game ->
        AlertDialog(
            onDismissRequest = { gameToDelete = null },
            confirmButton = {
                TextButton(onClick = {
                    repository.deleteGame(game.id)
                    games = repository.listGames()
                    gameToDelete = null
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { gameToDelete = null }) { Text(stringResource(R.string.common_cancel)) }
            },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text("${game.gameRules.name} · ${game.players.joinToString(", ")}") }
        )
    }
}

@Composable
private fun GameHistoryRow(
    game: SavedGame,
    onResume: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = game.gameRules.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(if (game.isFinished) R.string.history_finished else R.string.history_in_progress),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (game.isFinished) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            Text(
                text = historyDate(context, game.savedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val totals = game.totals()
            val order = if (game.gameRules.lowestWins) {
                game.players.indices.sortedBy { totals[it] }
            } else {
                game.players.indices.sortedByDescending { totals[it] }
            }
            Text(
                text = order.joinToString(" · ") { "${game.players[it]} ${totals[it]}" },
                style = MaterialTheme.typography.bodyMedium
            )
            if (game.isFinished && totals.distinct().size > 1) {
                val best = if (game.gameRules.lowestWins) totals.min() else totals.max()
                val leaders = game.players.indices.filter { totals[it] == best }
                Text(
                    text = stringResource(
                        if (leaders.size > 1) R.string.result_winners else R.string.result_winner,
                        leaders.joinToString(stringResource(R.string.result_and)) { game.players[it] }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                Button(onClick = onResume) {
                    Text(stringResource(if (game.isFinished) R.string.history_review else R.string.history_resume))
                }
                OutlinedButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.history_delete_description, game.gameRules.name, historyDate(context, game.savedAt)))
                }
            }
        }
    }
}

/** Phrase de bilan affichée après un import (voir [GameHistoryRepository.ImportSummary]). */
private fun importMessage(context: Context, r: GameHistoryRepository.ImportSummary): String {
    val res = context.resources
    val parts = mutableListOf<String>()
    if (r.added > 0) parts.add(res.getQuantityString(R.plurals.history_import_added, r.added, r.added))
    if (r.updated > 0) parts.add(res.getQuantityString(R.plurals.history_import_updated, r.updated, r.updated))
    if (r.unchanged > 0) parts.add(res.getQuantityString(R.plurals.history_import_unchanged, r.unchanged, r.unchanged))
    if (r.invalid > 0) parts.add(res.getQuantityString(R.plurals.history_import_invalid, r.invalid, r.invalid))
    if (r.droppedForSpace > 0) {
        parts.add(
            res.getQuantityString(
                R.plurals.history_import_dropped, r.droppedForSpace, r.droppedForSpace,
                GameHistoryRepository.MAX_SAVED_GAMES
            )
        )
    }
    return if (parts.isEmpty()) context.getString(R.string.history_import_empty)
    else context.getString(R.string.history_import_done, parts.joinToString(", "))
}

/** Texte de l'erreur d'import (raison fournie par [GameHistoryRepository.importJson]). */
private fun importErrorText(context: Context, error: JournalBackup.ImportError?): String = context.getString(
    when (error) {
        JournalBackup.ImportError.NOT_A_BACKUP -> R.string.history_import_not_backup
        JournalBackup.ImportError.NEWER_VERSION -> R.string.history_import_newer
        null -> R.string.history_import_impossible
    }
)
