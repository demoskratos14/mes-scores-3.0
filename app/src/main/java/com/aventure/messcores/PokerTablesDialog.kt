package com.aventure.messcores

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.IOException
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Taille maximale lue dans un fichier de joueurs (un vrai fichier fait quelques centaines d'octets). */
private const val MAX_FILE_BYTES = 1_000_000

private enum class TablesStep { MENU, EXPORT, IMPORT }

/**
 * Réunion de tables d'un tournoi : on exporte les joueurs d'une table dans un fichier (nom, tapis,
 * couleur), puis on importe ce fichier sur l'autre table. Les arrivants s'assoient au hasard entre
 * les joueurs déjà présents, sans changer leur ordre ni la place du bouton (voir [PokerEngine.reseat]).
 * Accessible seulement entre deux mains.
 */
@Composable
fun PokerTablesDialog(viewModel: ScoreViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    var step by rememberSaveable { mutableStateOf(TablesStep.MENU) }
    // Résultat de la dernière opération, affiché dans le menu.
    var message by remember { mutableStateOf<String?>(null) }
    // Joueurs lus dans le fichier choisi, en attente de confirmation.
    var pending by remember { mutableStateOf<List<PokerTransferPlayer>?>(null) }

    val alive = viewModel.pokerAliveIndices()
    var selected by rememberSaveable { mutableStateOf(alive.toSet()) }
    var removeAfter by rememberSaveable { mutableStateOf(true) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val toExport = viewModel.pokerTransferPlayers(selected)
            scope.launch {
                val error: String? = withContext(Dispatchers.IO) {
                    try {
                        val json = PokerTransfer.export(toExport, System.currentTimeMillis())
                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                            ?: throw IOException(resources.getString(R.string.history_stream_unavailable))
                        null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        e.message ?: resources.getString(R.string.error_unknown)
                    }
                }
                if (error != null) {
                    message = resources.getString(R.string.poker_tables_export_failed, error)
                } else {
                    val count = context.quantity(R.plurals.poker_players_count, toExport.size)
                    val removed = removeAfter && viewModel.pokerCanRemove(selected) &&
                        viewModel.pokerRemovePlayers(selected) == PokerReseatResult.DONE
                    message = resources.getString(
                        if (removed) R.string.poker_tables_export_ok_removed else R.string.poker_tables_export_ok,
                        count
                    )
                }
                step = TablesStep.MENU
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val outcome: Pair<List<PokerTransferPlayer>?, String?> = withContext(Dispatchers.IO) {
                    try {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IOException(resources.getString(R.string.history_file_unreadable))
                        // Un fichier trop gros n'est pas une liste de joueurs : même message que pour un fichier invalide.
                        (if (bytes.size > MAX_FILE_BYTES) null else PokerTransfer.parse(bytes.toString(Charsets.UTF_8))) to null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null to (e.message ?: resources.getString(R.string.error_unknown))
                    }
                }
                val (players, error) = outcome
                when {
                    error != null -> message = resources.getString(R.string.poker_tables_import_failed, error)
                    players == null -> message = resources.getString(R.string.poker_tables_import_invalid)
                    else -> {
                        pending = players
                        message = null
                        step = TablesStep.IMPORT
                    }
                }
            }
        }
    }

    val title = when (step) {
        TablesStep.MENU -> R.string.poker_tables_title
        TablesStep.EXPORT -> R.string.poker_tables_export_title
        TablesStep.IMPORT -> R.string.poker_tables_import_title
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title), modifier = Modifier.semantics { heading() }) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (step) {
                    TablesStep.MENU -> {
                        Text(stringResource(R.string.poker_tables_intro), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(
                            onClick = {
                                selected = alive.toSet()
                                message = null
                                step = TablesStep.EXPORT
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.poker_tables_export)) }
                        OutlinedButton(
                            onClick = {
                                message = null
                                importLauncher.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.poker_tables_import)) }
                        message?.let { Text(it, fontWeight = FontWeight.SemiBold) }
                    }

                    TablesStep.EXPORT -> {
                        val players = viewModel.players
                        val seats = viewModel.poker?.seats.orEmpty()
                        alive.forEach { index ->
                            val checked = index in selected
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Checkbox) {
                                        selected = if (checked) selected - index else selected + index
                                    }
                            ) {
                                // La ligne entière est cliquable : la case n'a pas besoin de réagir seule.
                                Checkbox(checked = checked, onCheckedChange = null)
                                Text(
                                    text = stringResource(
                                        R.string.poker_tables_player_line,
                                        players.getOrElse(index) { "" },
                                        NumberFormat.getIntegerInstance().format(seats.getOrNull(index)?.stack ?: 0)
                                    ),
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                        val canRemove = viewModel.pokerCanRemove(selected)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = canRemove, role = Role.Checkbox) { removeAfter = !removeAfter }
                        ) {
                            Checkbox(checked = removeAfter && canRemove, onCheckedChange = null, enabled = canRemove)
                            Text(
                                text = stringResource(R.string.poker_tables_remove_after),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        if (!canRemove && selected.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.poker_tables_remove_blocked),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    TablesStep.IMPORT -> {
                        val incoming = pending.orEmpty()
                        Text(
                            text = stringResource(
                                R.string.poker_tables_import_confirm,
                                context.quantity(R.plurals.poker_players_count, incoming.size)
                            )
                        )
                        incoming.forEach { p ->
                            Text(
                                text = stringResource(
                                    R.string.poker_tables_player_line,
                                    p.name,
                                    NumberFormat.getIntegerInstance().format(p.stack)
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (viewModel.pokerImportNeedsRoom(incoming.size)) {
                            Text(
                                text = stringResource(R.string.poker_tables_purge_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                TablesStep.MENU -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
                TablesStep.EXPORT -> Button(
                    onClick = {
                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        exportLauncher.launch("mes-scores-poker-joueurs-$date.json")
                    },
                    enabled = selected.isNotEmpty()
                ) { Text(stringResource(R.string.poker_tables_export_action)) }
                TablesStep.IMPORT -> Button(
                    onClick = {
                        when (viewModel.pokerImportPlayers(pending.orEmpty())) {
                            PokerReseatResult.DONE -> {
                                message = resources.getString(
                                    R.string.poker_tables_import_ok,
                                    context.quantity(R.plurals.poker_players_count, pending.orEmpty().size)
                                )
                                pending = null
                                step = TablesStep.MENU
                            }
                            PokerReseatResult.TABLE_FULL ->
                                message = resources.getQuantityString(R.plurals.poker_tables_full, viewModel.gameRules.maxPlayers, viewModel.gameRules.maxPlayers)
                            PokerReseatResult.NOT_ALLOWED ->
                                message = resources.getString(R.string.poker_tables_not_allowed)
                        }
                    },
                    enabled = pending?.isNotEmpty() == true
                ) { Text(stringResource(R.string.poker_tables_import_action)) }
            }
        },
        dismissButton = {
            if (step != TablesStep.MENU) {
                TextButton(onClick = {
                    pending = null
                    step = TablesStep.MENU
                }) { Text(stringResource(R.string.common_back)) }
            }
        }
    )
}
