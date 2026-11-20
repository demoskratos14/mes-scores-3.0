package com.aventure.messcores

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private const val ROOM_ACTION_HOST = "host"
private const val ROOM_ACTION_JOIN = "join"

/**
 * La salle multijoueur : on se connecte une seule fois, puis l'organisateur enchaîne les jeux.
 *
 * - L'organisateur ouvre la salle, les autres téléphones la rejoignent. Quand tout le monde est là, il choisit un jeu
 *   ([onChooseGame]) ; les invités y sont emmenés tout seuls.
 * - Quand l'organisateur quitte un jeu, tout le monde revient ici : il en choisit un autre sans que personne ait à se
 *   reconnecter ni à retaper son nom (il est gardé, voir [MultiplayerName]).
 */
@Composable
internal fun RoomScreen(
    session: SyncSession,
    onBack: () -> Unit,
    onChooseGame: () -> Unit
) {
    val context = LocalContext.current
    // Le nom que voient les autres téléphones : retrouvé d'une fois sur l'autre.
    var playerName by rememberSaveable { mutableStateOf(MultiplayerName.current(context)) }
    val deviceName = playerName.trim().ifBlank { MultiplayerName.fallback() }

    // Ce que l'utilisateur a demandé avant que les autorisations soient accordées.
    var requestedAction by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var showLeave by rememberSaveable { mutableStateOf(false) }

    fun start(action: String) {
        MultiplayerName.save(context, playerName)
        if (action == ROOM_ACTION_HOST) session.startHostingRoom(deviceName) else session.startJoiningRoom()
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val action = requestedAction
        requestedAction = null
        if (action != null && results.values.all { it }) {
            permissionDenied = false
            start(action)
        } else {
            permissionDenied = true
        }
    }

    fun request(action: String) {
        val missing = nearbyPermissions(Build.VERSION.SDK_INT).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            permissionDenied = false
            start(action)
        } else {
            requestedAction = action
            launcher.launch(missing.toTypedArray())
        }
    }

    // Quitter la salle coupe les connexions : on demande confirmation tant qu'elle est ouverte.
    fun leave() {
        if (session.role != SyncRole.NONE) session.closeRoom()
        showLeave = false
        onBack()
    }

    BackHandler {
        if (session.role == SyncRole.NONE) leave() else showLeave = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        TextButton(onClick = { if (session.role == SyncRole.NONE) leave() else showLeave = true }) {
            Text(stringResource(R.string.common_back), color = Color.White)
        }
        Text(
            text = stringResource(R.string.room_title),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (session.role == SyncRole.NONE) {
                        Text(text = stringResource(R.string.room_intro), style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = playerName,
                            onValueChange = {
                                playerName = it.take(MultiplayerName.MAX_LENGTH)
                                MultiplayerName.save(context, playerName)
                            },
                            label = { Text(stringResource(R.string.share_your_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(onClick = { request(ROOM_ACTION_HOST) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.room_create_button))
                        }
                        OutlinedButton(onClick = { request(ROOM_ACTION_JOIN) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.room_join_button))
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.share_connected, session.roomMembers.joinToString(", ")),
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (session.role == SyncRole.HOST) {
                            Text(text = stringResource(R.string.room_host_wait), style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = onChooseGame, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.room_choose_game))
                            }
                        } else if (session.isConnected) {
                            Text(text = stringResource(R.string.room_guest_wait), style = MaterialTheme.typography.bodyMedium)
                        } else {
                            val pending = session.pending
                            if (pending != null) {
                                Text(
                                    text = stringResource(R.string.room_pending_guest, pending.digits),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            } else {
                                Text(text = stringResource(R.string.room_searching), style = MaterialTheme.typography.bodyMedium)
                                session.endpoints.forEach { endpoint ->
                                    Button(
                                        onClick = { session.connectTo(endpoint, deviceName) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.room_join_endpoint, endpoint.name))
                                    }
                                }
                            }
                        }
                        OutlinedButton(onClick = { showLeave = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (session.role == SyncRole.HOST) R.string.room_close else R.string.room_leave))
                        }
                    }
                }
            }

            if (permissionDenied) {
                Text(
                    text = stringResource(R.string.share_permission_denied),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            session.problem?.let { problem ->
                Text(
                    text = stringResource(
                        when (problem) {
                            SyncProblem.START_FAILED -> R.string.share_problem_start
                            SyncProblem.CONNECTION_FAILED -> R.string.share_problem_connection
                            SyncProblem.REFUSED -> R.string.share_problem_refused
                            SyncProblem.HOST_LOST -> R.string.room_problem_host_lost
                            SyncProblem.TOO_BIG -> R.string.share_problem_too_big
                        }
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    if (showLeave) {
        val host = session.role == SyncRole.HOST
        AlertDialog(
            onDismissRequest = { showLeave = false },
            title = { Text(stringResource(if (host) R.string.room_close_title else R.string.room_leave_title)) },
            text = { Text(stringResource(if (host) R.string.room_close_message else R.string.room_leave_message)) },
            confirmButton = {
                TextButton(onClick = { leave() }) {
                    Text(
                        stringResource(if (host) R.string.room_close else R.string.room_leave),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeave = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}
