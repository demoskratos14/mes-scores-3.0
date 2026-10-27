package com.aventure.messcores

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val BoardGreen = Color(0xFF2E7D4F)
private val GridLine = Color(0xFF1B5E3A)
private val DiscBlack = Color(0xFF111111)
private val DiscWhite = Color(0xFFF5F5F5)

/**
 * Othello (Reversi) à deux joueurs : sur un seul téléphone (on se passe l'appareil) ou chacun sur le sien (voir
 * [OthelloOnline]). Les manches s'enchaînent, les couleurs s'échangent à chaque manche, et le score de la séance
 * (victoires de chacun, égalités) reste affiché. Les cases où l'on peut jouer sont marquées d'un point.
 */
@Composable
internal fun OthelloScreen(
    players: List<String>,
    online: OthelloOnline,
    onExit: () -> Unit,
    onHostOnline: () -> Unit
) {
    var showQuit by rememberSaveable { mutableStateOf(false) }
    // Hors partie à deux téléphones, le retour système quitte simplement l'écran.
    BackHandler(enabled = online.active) {
        if (online.notice == OthelloNotice.HOST_LEFT) {
            online.leave()
            onExit()
        } else {
            showQuit = true
        }
    }

    if (online.active) {
        OnlineOthello(online = online, onQuit = { showQuit = true }, onClose = {
            online.leave()
            onExit()
        })
    } else {
        LocalOthello(players = players, onPlayOnline = onHostOnline)
    }

    if (showQuit && online.active) {
        AlertDialog(
            onDismissRequest = { showQuit = false },
            title = { Text(stringResource(R.string.othello_quit_title)) },
            text = { Text(stringResource(R.string.othello_quit_message)) },
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

// ---------- Un seul téléphone ----------

@Composable
private fun LocalOthello(players: List<String>, onPlayOnline: () -> Unit) {
    val names = listOf(players.getOrElse(0) { "1" }, players.getOrElse(1) { "2" })

    // Toute la séance est gardée quand l'écran tourne (voir OthelloMatch.encode).
    var match by rememberSaveable(stateSaver = OthelloMatchSaver) { mutableStateOf(OthelloMatch()) }
    // Proposé tant que rien n'a été joué : passer à deux téléphones en pleine séance perdrait le score.
    val fresh = match == OthelloMatch()

    OthelloCard {
        OthelloTitle()
        OthelloStatus(match = match, names = names, me = null)
        OthelloBoard(
            match = match,
            hints = match.validMoves(match.currentSeat),
            enabled = !match.isOver,
            onPlay = { index -> match.play(index, match.currentSeat)?.let { match = it } }
        )
        if (match.isOver) {
            Button(onClick = { match.newRound()?.let { match = it } }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.othello_new_round))
            }
        }
        if (fresh) {
            OutlinedButton(onClick = onPlayOnline, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.othello_online_button))
            }
        }
    }
}

// ---------- Chacun sur son téléphone ----------

/** La partie à deux téléphones : attente de l'autre joueur, puis le plateau et le score de la séance. */
@Composable
private fun OnlineOthello(online: OthelloOnline, onQuit: () -> Unit, onClose: () -> Unit) {
    if (online.notice == OthelloNotice.HOST_LEFT) {
        OthelloCard {
            OthelloTitle()
            Text(text = stringResource(R.string.othello_online_host_left), style = MaterialTheme.typography.titleMedium)
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_close)) }
        }
        return
    }
    if (!online.ready) {
        OthelloCard {
            OthelloTitle()
            if (online.notice == OthelloNotice.PLAYER_LEFT) {
                Text(
                    text = stringResource(R.string.othello_online_notice_left),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(text = stringResource(R.string.othello_online_waiting), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.othello_online_leave))
            }
        }
        return
    }

    val match = online.match
    val names = listOf(online.lobby.getOrElse(0) { "1" }, online.lobby.getOrElse(1) { "2" })
    val opponent = names[1 - online.seat]

    OthelloCard {
        OthelloTitle()
        Text(
            text = stringResource(
                if (online.myColor == 1) R.string.othello_online_you_black else R.string.othello_online_you_white,
                opponent
            ),
            style = MaterialTheme.typography.bodyLarge
        )
        OthelloStatus(match = match, names = names, me = online.seat)
        OthelloBoard(
            match = match,
            hints = match.validMoves(online.seat),
            enabled = online.myTurn,
            onPlay = online::play
        )
        if (match.isOver) {
            Button(onClick = { online.newRound() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.othello_new_round))
            }
        }
        OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.othello_online_leave))
        }
    }
}

// ---------- Éléments communs ----------

/**
 * Score de la séance, nombre de pions et phrase d'état (à qui de jouer, passe de tour, résultat).
 * [me] : ma place sur un téléphone à deux téléphones, null quand on se passe un seul téléphone.
 */
@Composable
private fun ColumnScope.OthelloStatus(match: OthelloMatch, names: List<String>, me: Int?) {
    Text(
        text = stringResource(R.string.othello_score, names[0], match.wins0, names[1], match.wins1, match.draws),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = stringResource(R.string.othello_counts, match.blackCount, match.whiteCount),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (match.passed != 0 && !match.isOver) {
        Text(
            text = stringResource(R.string.othello_pass, names[match.seatOf(match.passed)]),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
    val winnerSeat = if (match.winnerColor == 0) -1 else match.seatOf(match.winnerColor)
    val colorName = stringResource(if (match.turn == 1) R.string.othello_color_black else R.string.othello_color_white)
    Text(
        text = when {
            match.isOver && winnerSeat == -1 -> stringResource(R.string.othello_draw)
            match.isOver && winnerSeat == me -> stringResource(R.string.othello_online_you_win)
            match.isOver -> stringResource(R.string.othello_win, names[winnerSeat])
            match.currentSeat == me -> stringResource(R.string.othello_online_your_turn, colorName)
            else -> stringResource(R.string.othello_turn, names[match.currentSeat], colorName)
        },
        style = MaterialTheme.typography.titleMedium
    )
}

/** La carte centrée de l'écran, qui défile si le téléphone est petit ou tourné. */
@Composable
private fun OthelloCard(content: @Composable ColumnScope.() -> Unit) {
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
private fun OthelloTitle() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.game_othello),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f, fill = false).semantics { heading() }
        )
        Spacer(modifier = Modifier.width(12.dp))
        // Sur la carte claire, le bouton prend la couleur principale (il est blanc sur l'en-tête des autres parties).
        RulesButton(
            gameId = OTHELLO_ID,
            gameName = stringResource(R.string.game_othello),
            contentColor = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Le plateau de 8 × 8 cases. [hints] : les cases où le joueur peut jouer, marquées d'un point ; [enabled] : on peut
 * toucher une de ces cases ; la dernière case jouée est cerclée. Chaque case se lit par son nom (colonne A à H, ligne 1 à 8).
 */
@Composable
private fun OthelloBoard(match: OthelloMatch, hints: List<Int>, enabled: Boolean, onPlay: (Int) -> Unit) {
    val cells = match.cells
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(GridLine).padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (row in 0 until Othello.SIZE) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (col in 0 until Othello.SIZE) {
                    val index = row * Othello.SIZE + col
                    val value = cells[index]
                    val hint = index in hints
                    val column = ('A' + col).toString()
                    val description = when {
                        value == 1 -> stringResource(R.string.othello_cell_black, column, row + 1)
                        value == 2 -> stringResource(R.string.othello_cell_white, column, row + 1)
                        hint -> stringResource(R.string.othello_cell_hint, column, row + 1)
                        else -> stringResource(R.string.othello_cell_empty, column, row + 1)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(BoardGreen)
                            .semantics { contentDescription = description }
                            .clickable(enabled = enabled && hint, role = Role.Button) { onPlay(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            value != 0 -> Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.82f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(if (value == 1) DiscBlack else DiscWhite)
                                    // Dernier coup : anneau de couleur contrastée avec les deux couleurs de pions.
                                    .then(
                                        if (index == match.last) {
                                            Modifier.border(3.dp, Color(0xFFFFC107), CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                            )
                            hint -> Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.55f))
                            )
                        }
                    }
                }
            }
        }
    }
}
