package com.aventure.messcores

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Bouton de l'en-tête d'une partie : ouvre l'écran de partage ; [active] quand un autre téléphone est connecté. */
@Composable
internal fun ShareButton(active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.9f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
    ) {
        Text(
            text = stringResource(if (active) R.string.share_button_active else R.string.share_button),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/**
 * Autorisations nécessaires à Nearby Connections selon la version d'Android : « appareils à proximité »
 * à partir de la 13, Bluetooth à partir de la 12, la position avant. Écrites en toutes lettres, car les
 * constantes de [android.Manifest.permission] récentes n'existent pas sur les anciens Android.
 */
internal fun nearbyPermissions(sdk: Int): List<String> = when {
    sdk >= 33 -> listOf(
        "android.permission.BLUETOOTH_SCAN",
        "android.permission.BLUETOOTH_ADVERTISE",
        "android.permission.BLUETOOTH_CONNECT",
        "android.permission.NEARBY_WIFI_DEVICES"
    )
    sdk >= 31 -> listOf(
        "android.permission.BLUETOOTH_SCAN",
        "android.permission.BLUETOOTH_ADVERTISE",
        "android.permission.BLUETOOTH_CONNECT",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION"
    )
    sdk >= 29 -> listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION")
    else -> listOf("android.permission.ACCESS_COARSE_LOCATION")
}

/** Ce que l'écran de partage organise. */
internal enum class ShareKind { SCORE, MASTERMIND }

private const val MAX_NAME_LENGTH = 24
private const val ACTION_HOST = "host"
private const val ACTION_JOIN = "join"

/**
 * Partage de la partie entre deux téléphones, sans internet : l'un l'organise, l'autre la rejoint et
 * suit la même partie ; ce que l'un saisit apparaît sur l'autre.
 */
@Composable
internal fun ShareScreen(
    game: ScoreViewModel,
    session: SyncSession,
    onBack: () -> Unit,
    // Ce qu'on organise : la partie de score en cours, ou un Mastermind chacun sur son téléphone.
    kind: ShareKind = ShareKind.SCORE,
    mastermind: MastermindOnline? = null,
    mastermindSettings: MastermindSettings = MastermindSettings(),
    onHostedMastermind: () -> Unit = {}
) {
    val context = LocalContext.current
    val defaultName = remember { Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android" }
    // Le nom que voient les autres téléphones (et, au Mastermind, le nom du joueur).
    var playerName by rememberSaveable { mutableStateOf(defaultName) }
    val deviceName = playerName.trim().ifBlank { defaultName }

    // Ce que l'utilisateur a demandé avant que les autorisations soient accordées.
    var requestedAction by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }

    fun start(action: String) {
        when {
            action != ACTION_HOST -> session.startJoining()
            kind == ShareKind.MASTERMIND && mastermind != null -> {
                session.startHostingMastermind(deviceName)
                mastermind.openLobby(deviceName, mastermindSettings)
                onHostedMastermind()
            }
            else -> session.startHosting(deviceName)
        }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.common_back), color = Color.White) }
        Text(
            text = stringResource(R.string.share_title),
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
                    Text(text = stringResource(R.string.share_intro), style = MaterialTheme.typography.bodyMedium)

                    if (session.role == SyncRole.NONE) {
                        OutlinedTextField(
                            value = playerName,
                            onValueChange = { playerName = it.take(MAX_NAME_LENGTH) },
                            label = { Text(stringResource(R.string.share_your_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    when (session.role) {
                        SyncRole.NONE -> {
                            if (kind == ShareKind.MASTERMIND || session.canShareCurrentGame()) {
                                Button(onClick = { request(ACTION_HOST) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.share_host_button))
                                }
                            } else {
                                Text(
                                    text = stringResource(
                                        if (game.isGameActive) R.string.share_not_shareable else R.string.share_no_game
                                    ),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            OutlinedButton(onClick = { request(ACTION_JOIN) }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.share_join_button))
                            }
                            if (game.isGameActive) {
                                Text(text = stringResource(R.string.share_replace_warning), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        SyncRole.HOST -> {
                            if (session.isConnected) {
                                Text(
                                    text = stringResource(R.string.share_connected, session.connected.joinToString(", ") { it.name }),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(text = stringResource(R.string.share_hosting_wait), style = MaterialTheme.typography.bodyMedium)
                        }
                        SyncRole.GUEST -> {
                            if (session.isConnected) {
                                Text(
                                    text = stringResource(R.string.share_connected, session.connected.joinToString(", ") { it.name }),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            } else {
                                session.pending?.let { pending ->
                                    Text(
                                        text = stringResource(R.string.share_pending_guest, pending.digits),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                } ?: run {
                                    Text(text = stringResource(R.string.share_searching), style = MaterialTheme.typography.bodyMedium)
                                    session.endpoints.forEach { endpoint ->
                                        Button(
                                            onClick = { session.connectTo(endpoint, deviceName) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(stringResource(R.string.share_join_endpoint, endpoint.name))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (session.role != SyncRole.NONE) {
                        OutlinedButton(onClick = { session.disconnect() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.share_disconnect))
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
                            SyncProblem.HOST_LOST -> R.string.share_problem_host_lost
                            SyncProblem.TOO_BIG -> R.string.share_problem_too_big
                        }
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * Demande de connexion d'un autre téléphone, affichée sur n'importe quel écran : l'organisateur doit vérifier
 * que le même code s'affiche sur les deux téléphones avant d'accepter.
 */
@Composable
internal fun SyncPendingDialog(session: SyncSession) {
    val pending = session.pending
    if (session.role != SyncRole.HOST || pending == null) return
    AlertDialog(
        onDismissRequest = { session.refuse() },
        title = { Text(stringResource(R.string.share_pending_title)) },
        text = { Text(stringResource(R.string.share_pending_host_message, pending.name, pending.digits)) },
        confirmButton = { TextButton(onClick = { session.accept() }) { Text(stringResource(R.string.share_accept)) } },
        dismissButton = { TextButton(onClick = { session.refuse() }) { Text(stringResource(R.string.share_refuse)) } }
    )
}

// Court délai avant d'envoyer : une saisie qui modifie plusieurs choses d'un coup ne part qu'une fois.
private const val SYNC_DELAY_MS = 80L

// Écrans d'une partie : un invité qui reçoit une partie y est amené.
private val GAME_ROUTES = setOf("score", "counter", "teamRounds")

/**
 * Fait vivre le partage :
 * - organisateur : envoie l'état de la partie aux invités à chaque changement ;
 * - invité : quand la partie de l'organisateur arrive, ouvre l'écran de ce jeu (le même que sur l'autre téléphone).
 */
@Composable
internal fun SyncEffects(
    game: ScoreViewModel,
    session: SyncSession,
    navController: NavHostController,
    routeFor: (GameRules) -> String,
    mastermind: MastermindOnline,
    onMastermindJoined: () -> Unit
) {
    LaunchedEffect(game, session) {
        snapshotFlow { Triple(game.chartSnapshot().contentKey(), game.canUndo, game.canRedo) }
            .collectLatest {
                delay(SYNC_DELAY_MS)
                session.broadcastState()
            }
    }
    LaunchedEffect(mastermind.joined) {
        if (mastermind.joined == 0 || mastermind.isHost) return@LaunchedEffect
        val current = navController.currentDestination?.route
        if (current == "share" || current == "setup") onMastermindJoined()
    }
    LaunchedEffect(session.receivedStates) {
        if (session.receivedStates == 0 || session.role != SyncRole.GUEST) return@LaunchedEffect
        val target = routeFor(game.gameRules)
        val current = navController.currentDestination?.route
        // On n'arrache pas l'utilisateur à un autre écran (journal, statistiques…) : seulement à l'attente ou à une partie.
        if (current != target && (current == "share" || current == "setup" || current in GAME_ROUTES)) {
            navController.navigate(target) { popUpTo("setup") }
        }
    }
}
