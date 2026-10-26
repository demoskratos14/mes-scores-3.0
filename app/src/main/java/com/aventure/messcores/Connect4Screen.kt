package com.aventure.messcores

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Puissance 4 à deux joueurs : sur un seul téléphone (on se passe l'appareil) ou chacun sur le sien (voir
 * [Connect4Online]). Comme le Morpion, les manches s'enchaînent, le premier à jouer change à chaque manche et le
 * score de la séance (victoires de chacun, égalités) reste affiché. Le premier joueur joue les rouges, le deuxième
 * les jaunes. On touche une colonne pour y lâcher un jeton.
 */
@Composable
internal fun Connect4Screen(
    players: List<String>,
    online: Connect4Online,
    onExit: () -> Unit,
    onHostOnline: () -> Unit
) {
    var showQuit by rememberSaveable { mutableStateOf(false) }
    // Hors partie à deux téléphones, le retour système quitte simplement l'écran.
    BackHandler(enabled = online.active) {
        if (online.notice == Connect4Notice.HOST_LEFT) {
            online.leave()
            onExit()
        } else {
            showQuit = true
        }
    }

    if (online.active) {
        OnlineConnect4(online = online, onQuit = { showQuit = true }, onClose = {
            online.leave()
            onExit()
        })
    } else {
        LocalConnect4(players = players, onPlayOnline = onHostOnline)
    }

    if (showQuit && online.active) {
        AlertDialog(
            onDismissRequest = { showQuit = false },
            title = { Text(stringResource(R.string.morpion_quit_title)) },
            text = { Text(stringResource(R.string.morpion_quit_message)) },
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
private fun LocalConnect4(players: List<String>, onPlayOnline: () -> Unit) {
    val nameRed = players.getOrElse(0) { stringResource(R.string.connect4_red) }
    val nameYellow = players.getOrElse(1) { stringResource(R.string.connect4_yellow) }

    // Tout est gardé quand l'écran tourne (la grille sous forme de texte, voir Connect4.EMPTY).
    var board by rememberSaveable { mutableStateOf(Connect4.EMPTY) }
    var starter by rememberSaveable { mutableIntStateOf(0) }
    var winsRed by rememberSaveable { mutableIntStateOf(0) }
    var winsYellow by rememberSaveable { mutableIntStateOf(0) }
    var draws by rememberSaveable { mutableIntStateOf(0) }

    val cells = Connect4.parse(board)
    val line = Connect4.winningLine(cells)
    val winner = Connect4.winner(cells)
    val full = Connect4.isFull(cells)
    val over = winner != 0 || full
    val current = Connect4.nextPlayer(cells, starter + 1)
    // Proposé tant que rien n'a été joué : passer à deux téléphones en pleine séance perdrait le score.
    val fresh = board == Connect4.EMPTY && winsRed + winsYellow + draws == 0

    fun play(column: Int) {
        val next = Connect4.drop(cells, column, current) ?: return
        board = Connect4.format(next)
        when (Connect4.winner(next)) {
            1 -> winsRed += 1
            2 -> winsYellow += 1
            else -> if (Connect4.isFull(next)) draws += 1
        }
    }

    Connect4Card {
        Connect4Title()
        Text(
            text = stringResource(R.string.morpion_score, nameRed, winsRed, nameYellow, winsYellow, draws),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = when {
                winner != 0 -> stringResource(R.string.morpion_win, if (winner == 1) nameRed else nameYellow)
                full -> stringResource(R.string.morpion_draw)
                else -> stringResource(R.string.morpion_turn, if (current == 1) nameRed else nameYellow, colorName(current))
            },
            style = MaterialTheme.typography.titleMedium
        )
        Connect4Board(cells = cells, line = line, enabled = !over, onPlay = ::play)
        if (over) {
            Button(
                onClick = {
                    board = Connect4.EMPTY
                    starter = 1 - starter
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.morpion_new_round))
            }
        }
        if (fresh) {
            OutlinedButton(onClick = onPlayOnline, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.morpion_online_button))
            }
        }
    }
}

// ---------- Chacun sur son téléphone ----------

/** La partie à deux téléphones : attente de l'autre joueur, puis la grille et le score de la séance. */
@Composable
private fun OnlineConnect4(online: Connect4Online, onQuit: () -> Unit, onClose: () -> Unit) {
    if (online.notice == Connect4Notice.HOST_LEFT) {
        Connect4Card {
            Connect4Title()
            Text(text = stringResource(R.string.morpion_online_host_left), style = MaterialTheme.typography.titleMedium)
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_close)) }
        }
        return
    }
    if (!online.ready) {
        Connect4Card {
            Connect4Title()
            if (online.notice == Connect4Notice.PLAYER_LEFT) {
                Text(
                    text = stringResource(R.string.morpion_online_notice_left),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(text = stringResource(R.string.morpion_online_waiting), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.morpion_online_leave))
            }
        }
        return
    }

    val match = online.match
    val cells = match.cells
    val nameRed = online.lobby.getOrElse(0) { stringResource(R.string.connect4_red) }
    val nameYellow = online.lobby.getOrElse(1) { stringResource(R.string.connect4_yellow) }
    val me = online.myColor
    val opponent = if (me == 1) nameYellow else nameRed
    val winner = match.winner

    Connect4Card {
        Connect4Title()
        Text(
            text = stringResource(if (me == 1) R.string.connect4_online_you_red else R.string.connect4_online_you_yellow, opponent),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = stringResource(R.string.morpion_score, nameRed, match.winsRed, nameYellow, match.winsYellow, match.draws),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = when {
                winner == me -> stringResource(R.string.morpion_online_you_win)
                winner != 0 -> stringResource(R.string.morpion_win, opponent)
                match.isOver -> stringResource(R.string.morpion_draw)
                match.current == me -> stringResource(R.string.morpion_online_your_turn, colorName(me))
                else -> stringResource(R.string.morpion_turn, opponent, colorName(match.current))
            },
            style = MaterialTheme.typography.titleMedium
        )
        Connect4Board(cells = cells, line = Connect4.winningLine(cells), enabled = online.myTurn, onPlay = online::play)
        if (match.isOver) {
            Button(onClick = { online.newRound() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.morpion_new_round))
            }
        }
        OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.morpion_online_leave))
        }
    }
}

// ---------- Éléments communs ----------

/** La carte centrée de l'écran, qui défile si le téléphone est petit ou tourné. */
@Composable
private fun Connect4Card(content: @Composable ColumnScope.() -> Unit) {
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content
            )
        }
    }
}

@Composable
private fun Connect4Title() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.game_connect4),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f, fill = false).semantics { heading() }
        )
        Spacer(modifier = Modifier.width(12.dp))
        // Sur la carte claire, le bouton prend la couleur principale (il est blanc sur l'en-tête des autres parties).
        RulesButton(
            gameId = CONNECT4_ID,
            gameName = stringResource(R.string.game_connect4),
            contentColor = MaterialTheme.colorScheme.primary
        )
    }
}

private val RedDisc = Color(0xFFD32F2F)
private val YellowDisc = Color(0xFFFBC02D)

/**
 * La grille de 7 colonnes × 6 rangées ; [enabled] : on peut toucher une colonne qui n'est pas pleine ; [line] : les
 * quatre jetons gagnants, mis en valeur. Chaque colonne est un seul bouton qui lâche le jeton ; les jaunes portent
 * un anneau, pour ne pas dépendre de la couleur seule.
 */
@Composable
private fun Connect4Board(cells: List<Int>, line: List<Int>?, enabled: Boolean, onPlay: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (column in 0 until Connect4.COLUMNS) {
            val playable = enabled && !Connect4.isColumnFull(cells, column)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        enabled = playable,
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.connect4_drop_in_column, column + 1)
                    ) { onPlay(column) },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (row in 0 until Connect4.ROWS) {
                    val index = Connect4.index(row, column)
                    val value = cells[index]
                    val description = stringResource(
                        when (value) {
                            1 -> R.string.connect4_cell_red
                            2 -> R.string.connect4_cell_yellow
                            else -> R.string.connect4_cell_empty
                        },
                        column + 1,
                        Connect4.ROWS - row
                    )
                    val highlighted = line?.contains(index) == true
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    value == 1 -> RedDisc
                                    value == 2 -> YellowDisc
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            )
                            .then(
                                if (highlighted) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                else Modifier
                            )
                            .semantics { contentDescription = description },
                        contentAlignment = Alignment.Center
                    ) {
                        if (value == 2) {
                            Box(Modifier.fillMaxWidth(0.5f).aspectRatio(1f).border(2.dp, Color(0xFF8D6E00), CircleShape))
                        }
                    }
                }
            }
        }
    }
}

/** Le nom de la couleur d'un joueur : rouge pour 1, jaune pour 2. */
@Composable
private fun colorName(value: Int): String =
    stringResource(if (value == 1) R.string.connect4_red else R.string.connect4_yellow)
