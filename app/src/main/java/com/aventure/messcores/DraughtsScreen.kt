package com.aventure.messcores

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/**
 * Dames à deux joueurs : sur un seul téléphone (on se passe l'appareil) ou chacun sur le sien (voir
 * [DraughtsOnline]). Les parties s'enchaînent, les couleurs s'échangent à chaque partie et le score de la séance
 * (victoires de chacun, parties nulles) reste affiché. Pour jouer, on touche un pion puis la case où il va ; une
 * suite de prises se joue case après case.
 */
@Composable
internal fun DraughtsScreen(
    players: List<String>,
    online: DraughtsOnline,
    onExit: () -> Unit,
    onHostOnline: () -> Unit
) {
    var showQuit by rememberSaveable { mutableStateOf(false) }
    // Hors partie à deux téléphones, le retour système quitte simplement l'écran.
    BackHandler(enabled = online.active) {
        if (online.notice == DraughtsNotice.HOST_LEFT) {
            online.leave()
            onExit()
        } else {
            showQuit = true
        }
    }

    if (online.active) {
        OnlineDraughts(online = online, onQuit = { showQuit = true }, onClose = {
            online.leave()
            onExit()
        })
    } else {
        LocalDraughts(players = players, onPlayOnline = onHostOnline)
    }

    if (showQuit && online.active) {
        AlertDialog(
            onDismissRequest = { showQuit = false },
            title = { Text(stringResource(R.string.draughts_quit_title)) },
            text = { Text(stringResource(R.string.draughts_quit_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showQuit = false
                    online.leave()
                    onExit()
                }) {
                    Text(stringResource(R.string.mm_quit_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuit = false }) { Text(stringResource(R.string.quit_continue)) }
            }
        )
    }
}

// Garde la séance quand l'écran tourne.
private val DraughtsMatchSaver = Saver<DraughtsMatch, String>(
    save = { it.toJson().toString() },
    restore = { draughtsMatchFromJson(JSONObject(it)) }
)

// ---------- Un seul téléphone ----------

@Composable
private fun LocalDraughts(players: List<String>, onPlayOnline: () -> Unit) {
    val names = listOf(players.getOrElse(0) { "1" }, players.getOrElse(1) { "2" })
    var match by rememberSaveable(stateSaver = DraughtsMatchSaver) { mutableStateOf(DraughtsMatch()) }
    var showResign by rememberSaveable { mutableStateOf(false) }
    // Proposé tant que rien n'a été joué : passer à deux téléphones en pleine séance perdrait le score.
    val fresh = match == DraughtsMatch()

    DraughtsCard {
        DraughtsTitle()
        Text(
            text = stringResource(R.string.draughts_score, names[0], match.winsA, names[1], match.winsB, match.draws),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = when {
                match.winnerSeat >= 0 -> stringResource(R.string.draughts_win, names[match.winnerSeat])
                match.isOver -> stringResource(R.string.draughts_draw)
                else -> stringResource(R.string.draughts_turn, names[match.currentSeat], colorName(match.turn))
            },
            style = MaterialTheme.typography.titleMedium
        )
        DraughtsPlay(match = match, flip = false, enabled = !match.isOver) { path ->
            match = match.play(path, match.currentSeat) ?: match
        }
        if (match.isOver) {
            Button(onClick = { match = match.newRound() ?: match }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.draughts_new_round))
            }
        } else {
            OutlinedButton(onClick = { showResign = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.draughts_resign))
            }
        }
        if (fresh) {
            OutlinedButton(onClick = onPlayOnline, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.draughts_online_button))
            }
        }
    }

    if (showResign && !match.isOver) {
        val loser = names[match.currentSeat]
        val winner = names[1 - match.currentSeat]
        AlertDialog(
            onDismissRequest = { showResign = false },
            title = { Text(stringResource(R.string.draughts_resign_title)) },
            text = { Text(stringResource(R.string.draughts_resign_local_message, loser, winner)) },
            confirmButton = {
                TextButton(onClick = {
                    showResign = false
                    match = match.resign(match.currentSeat) ?: match
                }) {
                    Text(stringResource(R.string.draughts_resign), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResign = false }) { Text(stringResource(R.string.quit_continue)) }
            }
        )
    }
}

// ---------- Chacun sur son téléphone ----------

/** La partie à deux téléphones : attente de l'autre joueur, puis le damier et le score de la séance. */
@Composable
private fun OnlineDraughts(online: DraughtsOnline, onQuit: () -> Unit, onClose: () -> Unit) {
    var showResign by rememberSaveable { mutableStateOf(false) }

    if (online.notice == DraughtsNotice.HOST_LEFT) {
        DraughtsCard {
            DraughtsTitle()
            Text(text = stringResource(R.string.draughts_online_host_left), style = MaterialTheme.typography.titleMedium)
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_close)) }
        }
        return
    }
    if (!online.ready) {
        DraughtsCard {
            DraughtsTitle()
            if (online.notice == DraughtsNotice.PLAYER_LEFT) {
                Text(
                    text = stringResource(R.string.draughts_online_notice_left),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(text = stringResource(R.string.draughts_online_waiting), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.draughts_online_leave))
            }
        }
        return
    }

    val match = online.match
    val names = listOf(online.lobby.getOrElse(0) { "1" }, online.lobby.getOrElse(1) { "2" })
    val me = online.seat
    val opponent = names[1 - me]
    val myColor = online.myColor

    DraughtsCard {
        DraughtsTitle()
        Text(
            text = stringResource(
                if (myColor == Draughts.WHITE) R.string.draughts_online_you_white else R.string.draughts_online_you_black,
                opponent
            ),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = stringResource(R.string.draughts_score, names[0], match.winsA, names[1], match.winsB, match.draws),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = when {
                match.winnerSeat == me -> stringResource(R.string.draughts_online_you_win)
                match.winnerSeat >= 0 -> stringResource(R.string.draughts_win, opponent)
                match.isOver -> stringResource(R.string.draughts_draw)
                match.currentSeat == me -> stringResource(R.string.draughts_online_your_turn, colorName(myColor))
                else -> stringResource(R.string.draughts_turn, opponent, colorName(match.turn))
            },
            style = MaterialTheme.typography.titleMedium
        )
        // Chacun voit ses pièces en bas du damier.
        DraughtsPlay(match = match, flip = myColor == Draughts.BLACK, enabled = online.myTurn, onPlay = online::play)
        if (match.isOver) {
            Button(onClick = { online.newRound() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.draughts_new_round))
            }
        } else {
            OutlinedButton(onClick = { showResign = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.draughts_resign))
            }
        }
        OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.draughts_online_leave))
        }
    }

    if (showResign && !match.isOver) {
        AlertDialog(
            onDismissRequest = { showResign = false },
            title = { Text(stringResource(R.string.draughts_resign_title)) },
            text = { Text(stringResource(R.string.draughts_resign_online_message, opponent)) },
            confirmButton = {
                TextButton(onClick = {
                    showResign = false
                    online.resign()
                }) {
                    Text(stringResource(R.string.draughts_resign), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResign = false }) { Text(stringResource(R.string.quit_continue)) }
            }
        )
    }
}

// ---------- Éléments communs ----------

/** La carte centrée de l'écran, qui défile si le téléphone est petit ou tourné. Marges réduites : le damier a besoin de place. */
@Composable
private fun DraughtsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = cardSurface()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
private fun DraughtsTitle() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.game_draughts),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f, fill = false).semantics { heading() }
        )
        Spacer(modifier = Modifier.width(12.dp))
        // Sur la carte claire, le bouton prend la couleur principale (il est blanc sur l'en-tête des autres parties).
        RulesButton(
            gameId = DRAUGHTS_ID,
            gameName = stringResource(R.string.game_draughts),
            contentColor = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun colorName(color: Int): String =
    stringResource(if (color == Draughts.WHITE) R.string.draughts_white else R.string.draughts_black)

private fun parseSelection(text: String): List<Int> = if (text.isEmpty()) emptyList() else text.split(",").map { it.toInt() }

/**
 * Le damier et son indication de prise obligatoire. Le joueur touche un pion qui peut jouer, puis la case d'arrivée ;
 * pour une suite de prises, il touche chaque case où le pion se pose, l'une après l'autre. Le coup n'est envoyé à
 * [onPlay] qu'une fois complet. [flip] : damier tourné d'un demi-tour (le joueur noir voit ses pièces en bas).
 */
@Composable
private fun DraughtsPlay(match: DraughtsMatch, flip: Boolean, enabled: Boolean, onPlay: (List<Int>) -> Unit) {
    // Ce qui est déjà choisi du coup en cours (« 63,52 ») ; reparti de zéro dès que la partie avance.
    var selectionText by rememberSaveable(match.board, match.turn, match.result) { mutableStateOf("") }
    val selection = parseSelection(selectionText)
    val moves = remember(match) { match.legalMoves }

    val movable = if (enabled) moves.map { it.from }.toSet() else emptySet()
    val targets = if (enabled && selection.isNotEmpty()) {
        moves.filter { it.path.size > selection.size && it.path.take(selection.size) == selection }
            .map { it.path[selection.size] }.toSet()
    } else {
        emptySet()
    }

    fun onTap(index: Int) {
        if (!enabled) return
        when {
            selection.isEmpty() -> if (index in movable) selectionText = index.toString()
            index in targets -> {
                val next = selection + index
                if (moves.any { it.path == next }) {
                    selectionText = ""
                    onPlay(next)
                } else {
                    selectionText = next.joinToString(",")
                }
            }
            index == selection.first() -> selectionText = ""
            selection.size == 1 && index in movable -> selectionText = index.toString()
        }
    }

    if (enabled && moves.firstOrNull()?.isCapture == true) {
        Text(
            text = stringResource(R.string.draughts_must_capture),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
    DraughtsBoard(
        cells = match.cells,
        flip = flip,
        selected = selection.toSet(),
        targets = targets,
        last = match.last.toSet(),
        enabled = enabled,
        onTap = ::onTap
    )
}

private val LightSquare = Color(0xFFF0D9B5)
private val DarkSquare = Color(0xFFB58863)
private val WhitePiece = Color(0xFFFAFAFA)
private val WhiteEdge = Color(0xFF8D6E63)
private val BlackPiece = Color(0xFF2B2B2B)
private val BlackEdge = Color(0xFFBDBDBD)
private val KingMark = Color(0xFFFFC107)

/** Le damier de 10 × 10 ; [selected] : cases du coup en cours ; [targets] : cases où l'on peut aller ; [last] : dernier coup. */
@Composable
private fun DraughtsBoard(
    cells: List<Int>,
    flip: Boolean,
    selected: Set<Int>,
    targets: Set<Int>,
    last: Set<Int>,
    enabled: Boolean,
    onTap: (Int) -> Unit
) {
    val selectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val lastColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.40f)
    val targetColor = MaterialTheme.colorScheme.primary
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp).clip(RoundedCornerShape(8.dp))) {
            for (row in 0 until Draughts.SIZE) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until Draughts.SIZE) {
                        val shown = row * Draughts.SIZE + col
                        val index = if (flip) Draughts.CELLS - 1 - shown else shown
                        val dark = Draughts.isDark(index)
                        val piece = cells[index]
                        val description = if (dark) {
                            val number = Draughts.number(index)
                            when (piece) {
                                Draughts.MAN_WHITE -> stringResource(R.string.draughts_cell_man_white, number)
                                Draughts.MAN_BLACK -> stringResource(R.string.draughts_cell_man_black, number)
                                Draughts.KING_WHITE -> stringResource(R.string.draughts_cell_king_white, number)
                                Draughts.KING_BLACK -> stringResource(R.string.draughts_cell_king_black, number)
                                else -> stringResource(R.string.draughts_cell_empty, number)
                            }
                        } else {
                            ""
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(if (dark) DarkSquare else LightSquare)
                                .then(
                                    if (dark) {
                                        Modifier
                                            .semantics { contentDescription = description }
                                            .clickable(enabled = enabled, role = Role.Button) { onTap(index) }
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (index in last) Box(modifier = Modifier.fillMaxSize().background(lastColor))
                            if (index in selected) Box(modifier = Modifier.fillMaxSize().background(selectedColor))
                            if (piece != 0) DraughtsPiece(piece = piece, modifier = Modifier.fillMaxSize())
                            if (index in targets) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawCircle(color = targetColor, radius = size.minDimension * 0.17f, center = Offset(size.width / 2, size.height / 2))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Un pion, ou une dame (marquée d'un disque doré au centre). */
@Composable
private fun DraughtsPiece(piece: Int, modifier: Modifier) {
    val white = Draughts.colorOf(piece) == Draughts.WHITE
    val king = Draughts.isKing(piece)
    Canvas(modifier = modifier) {
        val radius = size.minDimension * 0.39f
        val stroke = size.minDimension * 0.05f
        drawCircle(color = if (white) WhitePiece else BlackPiece, radius = radius)
        drawCircle(color = if (white) WhiteEdge else BlackEdge, radius = radius, style = Stroke(width = stroke))
        drawCircle(color = if (white) WhiteEdge else BlackEdge, radius = radius * 0.62f, style = Stroke(width = stroke * 0.6f))
        if (king) drawCircle(color = KingMark, radius = radius * 0.34f)
    }
}
