package com.aventure.messcores

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Morpion à deux joueurs sur un seul téléphone : on se passe l'appareil. Les manches s'enchaînent, le premier
 * à jouer change à chaque manche, et le score de la séance (victoires de chacun, égalités) reste affiché.
 * Le premier nom saisi joue les croix, le deuxième les ronds.
 */
@Composable
fun TicTacToeScreen(players: List<String>) {
    val nameX = players.getOrElse(0) { "X" }
    val nameO = players.getOrElse(1) { "O" }

    // Tout est gardé quand l'écran tourne (la grille sous forme de texte, voir TicTacToe.EMPTY).
    var board by rememberSaveable { mutableStateOf(TicTacToe.EMPTY) }
    var starter by rememberSaveable { mutableIntStateOf(0) }
    var winsX by rememberSaveable { mutableIntStateOf(0) }
    var winsO by rememberSaveable { mutableIntStateOf(0) }
    var draws by rememberSaveable { mutableIntStateOf(0) }

    val cells = TicTacToe.parse(board)
    val line = TicTacToe.winningLine(cells)
    val winner = TicTacToe.winner(cells)
    val full = TicTacToe.isFull(cells)
    val over = winner != 0 || full
    val current = TicTacToe.nextPlayer(cells, starter + 1)

    fun play(index: Int) {
        val next = TicTacToe.play(cells, index, current) ?: return
        board = TicTacToe.format(next)
        when (TicTacToe.winner(next)) {
            1 -> winsX += 1
            2 -> winsO += 1
            else -> if (TicTacToe.isFull(next)) draws += 1
        }
    }

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
                Text(
                    text = stringResource(R.string.game_morpion),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = stringResource(R.string.morpion_score, nameX, winsX, nameO, winsO, draws),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = when {
                        winner != 0 -> stringResource(R.string.morpion_win, if (winner == 1) nameX else nameO)
                        full -> stringResource(R.string.morpion_draw)
                        else -> stringResource(R.string.morpion_turn, if (current == 1) nameX else nameO, symbolOf(current))
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in 0 until 3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (col in 0 until 3) {
                                val index = row * 3 + col
                                val value = cells[index]
                                val description = when (value) {
                                    1 -> stringResource(R.string.morpion_cell_x, index + 1)
                                    2 -> stringResource(R.string.morpion_cell_o, index + 1)
                                    else -> stringResource(R.string.morpion_cell_empty, index + 1)
                                }
                                val highlighted = line?.contains(index) == true
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (highlighted) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .semantics { contentDescription = description }
                                        .clickable(enabled = !over && value == 0, role = Role.Button) { play(index) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = symbolOf(value),
                                        fontSize = 48.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (value == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }
                    }
                }
                if (over) {
                    Button(
                        onClick = {
                            board = TicTacToe.EMPTY
                            starter = 1 - starter
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.morpion_new_round))
                    }
                }
            }
        }
    }
}

/** Le symbole d'un joueur : X pour 1, O pour 2, rien pour une case vide. */
private fun symbolOf(value: Int): String = when (value) {
    1 -> "X"
    2 -> "O"
    else -> ""
}
