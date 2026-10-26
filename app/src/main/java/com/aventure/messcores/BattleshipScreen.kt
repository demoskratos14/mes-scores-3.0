package com.aventure.messcores

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Chaque état d'une case a sa couleur, mais aussi son symbole et sa description pour TalkBack : on ne doit
// jamais avoir besoin de voir la couleur pour jouer. Les bateaux sont dessinés sous les marques de tir.
private val WATER_COLOR = Color(0xFFBBDEFB)
private val MISS_COLOR = Color(0xFFEEEEEE)
private val HIT_COLOR = Color(0xFFFFA726)
private val SUNK_COLOR = Color(0xFFB71C1C)
private val HULL_COLOR = Color(0xFF78909C)
private val SUNK_HULL_COLOR = Color(0xFF5D4037)
private val HULL_EDGE_COLOR = Color(0xFF263238)
private val DECK_COLOR = Color(0xFFCFD8DC)

// Un nom par bateau de Battleship.FLEET, dans le même ordre.
private val SHIP_NAMES = listOf(
    R.string.bs_ship_carrier, R.string.bs_ship_cruiser, R.string.bs_ship_destroyer,
    R.string.bs_ship_submarine, R.string.bs_ship_torpedo
)

/**
 * Touché coulé à deux : réglages, placement des flottes, tirs à tour de rôle, résultat. Sur le même téléphone (il
 * passe de main en main, l'écran se cache entre les deux joueurs) ou chacun sur son téléphone (voir [BattleshipOnline]).
 */
@Composable
internal fun BattleshipScreen(
    viewModel: BattleshipViewModel,
    online: BattleshipOnline,
    onExit: () -> Unit,
    onHostOnline: () -> Unit
) {
    val rules = viewModel.rules
    val game = viewModel.game
    KeepScreenOn()

    var showQuit by rememberSaveable { mutableStateOf(false) }
    BackHandler {
        if (online.active) {
            showQuit = true
        } else if (game == null || (game.phase == BattleshipPhase.FINISHED && viewModel.lastShot == null)) {
            viewModel.close()
            onExit()
        } else {
            showQuit = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = rules?.name ?: stringResource(R.string.game_battleship),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .semantics { heading() }
            )
            if (rules != null) {
                Spacer(modifier = Modifier.width(10.dp))
                RulesButton(rules = rules)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                online.active -> OnlineContent(online = online, onExit = {
                    online.leave()
                    onExit()
                })
                game == null -> SettingsCard(
                    settings = viewModel.settings,
                    onChange = { viewModel.updateSettings(it) },
                    onStart = { viewModel.start() },
                    onPlayOnline = onHostOnline
                )
                else -> LocalContent(viewModel = viewModel, game = game, onExit = {
                    viewModel.close()
                    onExit()
                })
            }
        }
    }

    if (showQuit) {
        AlertDialog(
            onDismissRequest = { showQuit = false },
            title = { Text(stringResource(R.string.bs_quit_title)) },
            text = { Text(stringResource(R.string.bs_quit_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showQuit = false
                    if (online.active) online.leave() else viewModel.close()
                    onExit()
                }) {
                    Text(stringResource(R.string.bs_quit_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuit = false }) { Text(stringResource(R.string.quit_continue)) }
            }
        )
    }
}

// ---------- Réglages ----------

@Composable
private fun SettingsCard(
    settings: BattleshipSettings,
    onChange: (BattleshipSettings) -> Unit,
    onStart: () -> Unit,
    onPlayOnline: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = settings.shootAgainOnHit,
                        role = Role.Switch,
                        onValueChange = { onChange(settings.copy(shootAgainOnHit = it)) }
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.bs_setting_shoot_again),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = settings.shootAgainOnHit, onCheckedChange = null)
            }
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.bs_start))
            }
            OutlinedButton(onClick = onPlayOnline, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.bs_online_button))
            }
        }
    }
}

// ---------- Deux joueurs sur le même téléphone ----------

@Composable
private fun LocalContent(viewModel: BattleshipViewModel, game: BattleshipGame, onExit: () -> Unit) {
    val handoff = viewModel.handoffTo
    val shot = viewModel.lastShot
    when {
        handoff != null -> HandoffCard(name = game.players[handoff], onReady = { viewModel.takePhone() })
        game.phase == BattleshipPhase.PLACEMENT -> {
            val player = game.fleets.indexOfFirst { it == null }
            Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.bs_place_title, game.players[player]),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                    FleetPlacement(entryKey = "local-$player", onConfirm = { viewModel.submitFleet(it) })
                }
            }
        }
        game.phase == BattleshipPhase.PLAYING || shot != null -> {
            val viewer = viewModel.viewer
            PlayView(
                header = stringResource(R.string.bs_turn_of, game.players[viewer]),
                myShots = game.shots[viewer],
                myFleet = game.fleets[viewer].orEmpty(),
                shotsAtMe = game.shots[1 - viewer],
                canFire = shot == null && game.phase == BattleshipPhase.PLAYING && game.turn == viewer,
                lastMine = shot,
                note = null,
                onFire = { viewModel.fire(it) },
                afterShot = {
                    if (shot != null) {
                        val label = when {
                            game.phase == BattleshipPhase.FINISHED -> stringResource(R.string.bs_see_results)
                            game.turn == viewer -> stringResource(R.string.bs_continue_shoot)
                            else -> stringResource(R.string.bs_pass_to, game.players[game.turn])
                        }
                        Button(onClick = { viewModel.afterShot() }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                    }
                }
            )
        }
        else -> ResultsView(
            winner = game.players.getOrElse(game.winner) { "" },
            boards = game.players.indices.map { player ->
                ResultBoard(
                    title = stringResource(R.string.bs_fleet_of, game.players[player]),
                    fleet = game.fleets[player],
                    shots = game.shots[1 - player]
                )
            },
            warning = false,
            onReplay = viewModel::replay,
            onExit = onExit
        )
    }
}

/** « Passe le téléphone à… » : aucune grille n'est affichée tant que le bon joueur n'a pas touché « C'est moi ». */
@Composable
private fun HandoffCard(name: String, onReady: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.bs_pass_phone, name),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Button(onClick = onReady, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.bs_im_ready)) }
        }
    }
}

// ---------- Chacun sur son téléphone ----------

@Composable
private fun OnlineContent(online: BattleshipOnline, onExit: () -> Unit) {
    when {
        online.notice == BattleshipNotice.HOST_LEFT -> {
            Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = stringResource(R.string.bs_online_host_left), style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_close)) }
                }
            }
        }
        !online.started -> OnlineLobby(online, onExit)
        online.phase == BattleshipPhase.PLACEMENT -> {
            Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (online.myFleet == null) {
                        Text(
                            text = stringResource(R.string.bs_online_place_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() }
                        )
                        FleetPlacement(entryKey = "online-fleet", onConfirm = { online.submitFleet(it) })
                    } else {
                        Text(text = stringResource(R.string.bs_online_waiting_fleet), style = MaterialTheme.typography.titleMedium)
                        ShotBoard(fleet = online.myFleet, shots = emptyList())
                    }
                }
            }
        }
        online.phase == BattleshipPhase.PLAYING -> PlayView(
            header = if (online.myTurn) {
                stringResource(R.string.bs_your_turn)
            } else {
                stringResource(R.string.bs_online_opponent_turn, online.opponentName)
            },
            myShots = online.myShots,
            myFleet = online.myFleet.orEmpty(),
            shotsAtMe = online.shotsAtMe,
            canFire = online.myTurn && online.pendingShot == null,
            lastMine = online.myShots.lastOrNull(),
            note = if (online.pendingShot != null) stringResource(R.string.bs_online_waiting_answer) else null,
            onFire = { online.fire(it) }
        )
        else -> ResultsView(
            winner = online.lobby.getOrElse(online.winner) { "" },
            boards = listOf(
                ResultBoard(stringResource(R.string.bs_fleet_title), online.myFleet, online.shotsAtMe),
                ResultBoard(stringResource(R.string.bs_fleet_of, online.opponentName), online.opponentFleet, online.myShots)
            ),
            warning = online.opponentHonest == false,
            onReplay = if (online.isHost) online::replay else null,
            onExit = onExit
        )
    }
}

/** Salle d'attente : l'autre joueur se connecte ; l'organisateur lance la partie quand il est là. */
@Composable
private fun OnlineLobby(online: BattleshipOnline, onExit: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (online.notice == BattleshipNotice.PLAYER_LEFT) {
                Text(
                    text = stringResource(R.string.bs_online_notice_left),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(
                text = stringResource(R.string.bs_online_lobby_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            online.lobby.forEachIndexed { index, name ->
                Text(
                    text = if (index == online.seat) stringResource(R.string.bs_online_you, name) else name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (index == online.seat) FontWeight.Bold else FontWeight.Normal
                )
            }
            Text(
                text = stringResource(
                    if (online.settings.shootAgainOnHit) R.string.bs_shoot_again_on else R.string.bs_shoot_again_off
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            if (online.isHost) {
                if (online.lobby.size < BattleshipOnline.PLAYERS) {
                    Text(text = stringResource(R.string.bs_online_need_player), style = MaterialTheme.typography.bodyMedium)
                    Text(text = stringResource(R.string.bs_online_wait_player), style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { online.startGame() },
                    enabled = online.lobby.size >= BattleshipOnline.PLAYERS,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.bs_online_start))
                }
            } else {
                Text(text = stringResource(R.string.bs_online_wait_start), style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.bs_quit_confirm))
            }
        }
    }
}

// ---------- Les tirs ----------

/**
 * L'écran d'un joueur qui tire : le résultat du dernier tir, la grille de l'adversaire (mes tirs) et ma propre flotte
 * avec les tirs reçus. [afterShot] : les boutons à afficher sous le résultat d'un tir qui vient d'être fait.
 */
@Composable
private fun PlayView(
    header: String,
    myShots: List<BattleshipShot>,
    myFleet: List<List<Int>>,
    shotsAtMe: List<BattleshipShot>,
    canFire: Boolean,
    lastMine: BattleshipShot?,
    note: String?,
    onFire: (Int) -> Unit,
    afterShot: @Composable () -> Unit = {}
) {
    // Une nouvelle clé (autre joueur) repart de la case A1.
    var selected by rememberSaveable(header) { mutableIntStateOf(0) }
    val already = myShots.any { it.cell == selected }
    val pick: (Int) -> Unit = { selected = it }
    val announce = Modifier.semantics { liveRegion = LiveRegionMode.Polite }

    Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = header,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            lastMine?.let {
                Text(
                    text = resultText(it),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = announce
                )
            }
            shotsAtMe.lastOrNull()?.let {
                Text(text = incomingText(it), style = MaterialTheme.typography.bodyMedium, modifier = announce)
            }
            note?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium, modifier = announce) }
            afterShot()
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.bs_shots_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            if (canFire) CoordinatePicker(selected = selected, onSelect = pick)
            ShotBoard(
                fleet = null,
                shots = myShots,
                selected = if (canFire) selected else null,
                onCellClick = if (canFire) pick else null
            )
            if (canFire) {
                Button(onClick = { onFire(selected) }, enabled = !already, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (already) {
                            stringResource(R.string.bs_already_shot)
                        } else {
                            stringResource(R.string.bs_fire, Battleship.cellName(selected))
                        }
                    )
                }
            }
            Text(text = stringResource(R.string.bs_legend), style = MaterialTheme.typography.bodySmall)
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.bs_fleet_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            ShotBoard(fleet = myFleet, shots = shotsAtMe)
        }
    }
}

@Composable
private fun resultText(shot: BattleshipShot): String {
    val name = Battleship.cellName(shot.cell)
    return when (shot.outcome) {
        ShotOutcome.MISS -> stringResource(R.string.bs_result_miss, name)
        ShotOutcome.HIT -> stringResource(R.string.bs_result_hit, name)
        ShotOutcome.SUNK -> stringResource(R.string.bs_result_sunk, name, shot.sunk.size)
    }
}

@Composable
private fun incomingText(shot: BattleshipShot): String {
    val name = Battleship.cellName(shot.cell)
    return when (shot.outcome) {
        ShotOutcome.MISS -> stringResource(R.string.bs_incoming_miss, name)
        ShotOutcome.HIT -> stringResource(R.string.bs_incoming_hit, name)
        ShotOutcome.SUNK -> stringResource(R.string.bs_incoming_sunk, name, shot.sunk.size)
    }
}

/**
 * Choix d'une case par sa colonne et sa ligne, avec des boutons : une alternative à la grille, dont les cases sont
 * petites, pour viser sans chercher la case au doigt ni parcourir cent cases avec TalkBack.
 */
@Composable
private fun CoordinatePicker(selected: Int, onSelect: (Int) -> Unit) {
    val column = Battleship.column(selected)
    val row = Battleship.row(selected)
    Column(modifier = Modifier.fillMaxWidth()) {
        PickerRow(
            label = stringResource(R.string.bs_column_label, Battleship.columnLetter(column).toString()),
            previous = stringResource(R.string.bs_column_prev),
            next = stringResource(R.string.bs_column_next),
            canPrevious = column > 0,
            canNext = column < Battleship.SIZE - 1,
            onPrevious = { onSelect(Battleship.cellOf(row, column - 1)) },
            onNext = { onSelect(Battleship.cellOf(row, column + 1)) }
        )
        PickerRow(
            label = stringResource(R.string.bs_row_label, row + 1),
            previous = stringResource(R.string.bs_row_prev),
            next = stringResource(R.string.bs_row_next),
            canPrevious = row > 0,
            canNext = row < Battleship.SIZE - 1,
            onPrevious = { onSelect(Battleship.cellOf(row - 1, column)) },
            onNext = { onSelect(Battleship.cellOf(row + 1, column)) }
        )
    }
}

@Composable
private fun PickerRow(
    label: String,
    previous: String,
    next: String,
    canPrevious: Boolean,
    canNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onPrevious, enabled = canPrevious) {
            Icon(Icons.Filled.Remove, contentDescription = previous)
        }
        IconButton(onClick = onNext, enabled = canNext) {
            Icon(Icons.Filled.Add, contentDescription = next)
        }
    }
}

// ---------- Placement de la flotte ----------

// Un bateau placé tient dans un entier : case de départ × 2, plus 1 s'il est vertical (0 : horizontal, vers la droite).
private fun encodeShip(ship: List<Int>): Int {
    val horizontal = ship.size > 1 && ship[1] - ship[0] == 1
    return ship[0] * 2 + (if (horizontal) 0 else 1)
}

private fun decodeShip(code: Int, length: Int): List<Int>? = Battleship.shipCells(code / 2, length, code % 2 == 0)

/**
 * Placement de sa flotte : on choisit un bateau, une orientation et une case de départ (sur la grille ou avec les
 * boutons de colonne et de ligne), puis on le place. « Au hasard » place toute la flotte d'un coup.
 */
@Composable
private fun FleetPlacement(entryKey: Any, onConfirm: (List<List<Int>>) -> Unit) {
    // Pour chaque bateau de la flotte : -1 tant qu'il n'est pas placé, sinon son code (voir encodeShip).
    var placement by rememberSaveable(entryKey) { mutableStateOf(List(Battleship.FLEET.size) { -1 }) }
    var current by rememberSaveable(entryKey) { mutableIntStateOf(0) }
    var horizontal by rememberSaveable(entryKey) { mutableStateOf(true) }
    var selected by rememberSaveable(entryKey) { mutableIntStateOf(0) }
    var failed by rememberSaveable(entryKey) { mutableStateOf(false) }

    val ships = placement.mapIndexedNotNull { index, code ->
        if (code < 0) null else decodeShip(code, Battleship.FLEET[index])
    }
    val allPlaced = ships.size == Battleship.FLEET.size
    val pick: (Int) -> Unit = { selected = it }

    fun place() {
        val cells = Battleship.shipCells(selected, Battleship.FLEET[current], horizontal)
        val others = placement.mapIndexedNotNull { index, code ->
            if (index == current || code < 0) null else decodeShip(code, Battleship.FLEET[index])
        }.flatten()
        if (cells == null || cells.any { it in others }) {
            failed = true
            return
        }
        failed = false
        val updated = placement.toMutableList()
        updated[current] = encodeShip(cells)
        placement = updated
        // Passe au bateau suivant qui n'est pas encore placé, s'il en reste.
        val size = updated.size
        val next = (1..size).map { (current + it) % size }.firstOrNull { updated[it] < 0 }
        if (next != null) current = next
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = stringResource(R.string.bs_place_hint), style = MaterialTheme.typography.bodyMedium)
        Battleship.FLEET.forEachIndexed { index, length ->
            val status = stringResource(if (placement[index] >= 0) R.string.bs_ship_placed else R.string.bs_ship_todo)
            val label = stringResource(R.string.bs_ship_label, stringResource(SHIP_NAMES[index]), length, status)
            if (index == current) {
                Button(onClick = { current = index }, modifier = Modifier.fillMaxWidth()) { Text(label) }
            } else {
                OutlinedButton(onClick = { current = index }, modifier = Modifier.fillMaxWidth()) { Text(label) }
            }
        }
        OutlinedButton(onClick = { horizontal = !horizontal }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (horizontal) R.string.bs_orientation_h else R.string.bs_orientation_v))
        }
        CoordinatePicker(selected = selected, onSelect = pick)
        ShotBoard(fleet = ships, shots = emptyList(), selected = selected, onCellClick = pick)
        Button(onClick = { place() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.bs_place_here, Battleship.cellName(selected)))
        }
        if (failed) {
            Text(
                text = stringResource(R.string.bs_place_impossible),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = {
                    placement = Battleship.randomFleet().map { encodeShip(it) }
                    failed = false
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.bs_random))
            }
            TextButton(onClick = {
                placement = List(Battleship.FLEET.size) { -1 }
                current = 0
                failed = false
            }) {
                Text(stringResource(R.string.bs_clear_all))
            }
        }
        Button(onClick = { onConfirm(ships) }, enabled = allPlaced, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.bs_validate_fleet))
        }
    }
}

// ---------- Résultats ----------

/** Une grille de fin de partie : la flotte d'un joueur (null si pas dévoilée) et les tirs qu'elle a reçus. */
private class ResultBoard(val title: String, val fleet: List<List<Int>>?, val shots: List<BattleshipShot>)

@Composable
private fun ResultsView(
    winner: String,
    boards: List<ResultBoard>,
    warning: Boolean,
    onReplay: (() -> Unit)?,
    onExit: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.bs_results_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = stringResource(R.string.bs_winner, winner),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            if (warning) {
                Text(
                    text = stringResource(R.string.bs_cheat_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            boards.forEach { board ->
                Text(
                    text = board.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() }
                )
                ShotBoard(fleet = board.fleet, shots = board.shots)
            }
            if (onReplay != null) {
                Button(onClick = onReplay, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.bs_replay)) }
            }
            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.common_close))
            }
        }
    }
}

// ---------- La grille ----------

/** Ce qu'une case montre : un bateau (le mien ou dévoilé), la réponse à un tir, et si elle fait partie d'un bateau coulé. */
private class CellDisplay(val ship: Boolean, val outcome: ShotOutcome?, val sunk: Boolean)

/**
 * Une grille : [fleet] donne les bateaux visibles (null : aucun, sauf ceux que les tirs ont coulés) et [shots] les
 * tirs et leurs réponses. Si [onCellClick] est renseigné, toucher une case la sélectionne ([selected]).
 */
@Composable
private fun ShotBoard(
    fleet: List<List<Int>>?,
    shots: List<BattleshipShot>,
    selected: Int? = null,
    onCellClick: ((Int) -> Unit)? = null
) {
    val shipCells = remember(fleet) { fleet?.flatten()?.toSet().orEmpty() }
    val byCell = remember(shots) { shots.associateBy { it.cell } }
    val sunk = remember(shots) { Battleship.sunkCells(shots) }
    // Sans flotte connue, on ne voit que les bateaux coulés : leurs cases ont été annoncées avec le tir.
    val ships = remember(fleet, shots) { fleet ?: shots.filter { it.outcome == ShotOutcome.SUNK }.map { it.sunk } }
    BoardView(
        ships = ships,
        sunkCells = sunk,
        display = { cell -> CellDisplay(ship = cell in shipCells, outcome = byCell[cell]?.outcome, sunk = cell in sunk) },
        selected = selected,
        onCellClick = onCellClick
    )
}

@Composable
private fun BoardView(
    ships: List<List<Int>>,
    sunkCells: Set<Int>,
    display: (Int) -> CellDisplay,
    selected: Int?,
    onCellClick: ((Int) -> Unit)?
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        // Onze cases de large : les lettres des colonnes, puis les dix colonnes (et autant de lignes sous les lettres).
        val cellSize = minOf(maxWidth / (Battleship.SIZE + 1), 40.dp)
        Box(modifier = Modifier.size(cellSize * (Battleship.SIZE + 1))) {
            // Sous les cases : la mer et les bateaux. Les cases, transparentes, ne portent que les marques de tir.
            Canvas(modifier = Modifier
                .fillMaxSize()
                .clearAndSetSemantics { }) {
                val cs = cellSize.toPx()
                drawRect(WATER_COLOR, topLeft = Offset(cs, cs), size = Size(cs * Battleship.SIZE, cs * Battleship.SIZE))
                for (ship in ships) drawShip(ship, cs, sunk = ship.isNotEmpty() && ship.all { it in sunkCells })
            }
            Column {
                Row {
                    Spacer(modifier = Modifier.size(cellSize))
                    for (column in 0 until Battleship.SIZE) {
                        BoardLabel(text = Battleship.columnLetter(column).toString(), size = cellSize)
                    }
                }
                for (row in 0 until Battleship.SIZE) {
                    Row {
                        BoardLabel(text = (row + 1).toString(), size = cellSize)
                        for (column in 0 until Battleship.SIZE) {
                            val cell = Battleship.cellOf(row, column)
                            CellBox(
                                cell = cell,
                                size = cellSize,
                                display = display(cell),
                                selected = cell == selected,
                                onClick = onCellClick
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dessine un bateau vu de dessus : une coque à proue pointue qui couvre toutes ses cases, un pont avec des hublots
 * et une passerelle. [cs] : la taille d'une case en pixels ; la grille commence à une case du bord (lettres, numéros).
 */
private fun DrawScope.drawShip(ship: List<Int>, cs: Float, sunk: Boolean) {
    val cells = ship.sorted()
    if (cells.isEmpty()) return
    val first = cells.first()
    val horizontal = cells.size > 1 && cells[1] - cells[0] == 1
    val length = cells.size * cs
    val left = cs + Battleship.column(first) * cs
    val top = cs + Battleship.row(first) * cs
    val margin = cs * 0.14f
    val pad = cs * 0.08f
    val bow = cs * 0.55f
    val hull = Path().apply {
        moveTo(pad, margin)
        lineTo(length - bow, margin)
        quadraticBezierTo(length - pad, margin, length - pad, cs / 2)
        quadraticBezierTo(length - pad, cs - margin, length - bow, cs - margin)
        lineTo(pad, cs - margin)
        close()
    }
    // Le bateau est dessiné couché vers la droite ; à la verticale, on le tourne d'un quart de tour (la proue vers le bas).
    withTransform({
        if (horizontal) {
            translate(left, top)
        } else {
            translate(left + cs, top)
            rotate(90f, Offset.Zero)
        }
    }) {
        drawPath(hull, color = if (sunk) SUNK_HULL_COLOR else HULL_COLOR)
        drawPath(hull, color = HULL_EDGE_COLOR, style = Stroke(width = cs * 0.06f))
        val bridge = cells.size / 2
        for (index in cells.indices) {
            if (index != bridge) drawCircle(DECK_COLOR, radius = cs * 0.09f, center = Offset(index * cs + cs / 2, cs / 2))
        }
        drawRoundRect(
            color = DECK_COLOR,
            topLeft = Offset(bridge * cs + cs * 0.22f, cs * 0.3f),
            size = Size(cs * 0.56f, cs * 0.4f),
            cornerRadius = CornerRadius(cs * 0.08f)
        )
    }
}

/** Lettre de colonne ou numéro de ligne : la description de chaque case dit déjà où elle est, TalkBack les saute. */
@Composable
private fun BoardLabel(text: String, size: Dp) {
    Box(modifier = Modifier
        .size(size)
        .clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CellBox(cell: Int, size: Dp, display: CellDisplay, selected: Boolean, onClick: ((Int) -> Unit)?) {
    val symbol: String
    val overlay: Color
    val marker: Color?
    val textColor: Color
    val descriptionRes: Int
    when {
        display.sunk -> {
            symbol = "✖"
            overlay = SUNK_COLOR.copy(alpha = 0.55f)
            marker = null
            textColor = Color.White
            descriptionRes = R.string.bs_cell_sunk
        }
        display.outcome == ShotOutcome.HIT || display.outcome == ShotOutcome.SUNK -> {
            symbol = "✕"
            overlay = Color.Transparent
            marker = HIT_COLOR
            textColor = Color.Black
            descriptionRes = R.string.bs_cell_hit
        }
        display.outcome == ShotOutcome.MISS -> {
            symbol = "•"
            overlay = Color.Transparent
            marker = MISS_COLOR
            textColor = Color.Black
            descriptionRes = R.string.bs_cell_miss
        }
        display.ship -> {
            symbol = ""
            overlay = Color.Transparent
            marker = null
            textColor = Color.Black
            descriptionRes = R.string.bs_cell_ship
        }
        else -> {
            symbol = ""
            overlay = Color.Transparent
            marker = null
            textColor = Color.Black
            descriptionRes = R.string.bs_cell_water
        }
    }
    val name = Battleship.cellName(cell)
    val base = stringResource(descriptionRes, name)
    val description = if (selected) stringResource(R.string.bs_cell_selected, base) else base
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button) { onClick(cell) } else Modifier
    Box(
        modifier = Modifier
            .size(size)
            .background(overlay)
            .border(
                width = if (selected) 3.dp else 0.5.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f)
            )
            .then(clickable)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        // Un rond de couleur derrière le symbole d'un tir : on le voit même par-dessus la coque d'un bateau.
        val symbolModifier = if (marker != null) {
            Modifier
                .size(size * 0.66f)
                .background(marker, CircleShape)
        } else {
            Modifier
        }
        Box(modifier = symbolModifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            Text(
                text = symbol,
                color = textColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
