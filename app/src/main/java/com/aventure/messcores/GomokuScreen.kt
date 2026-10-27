package com.aventure.messcores

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

// Le plateau est en bois clair dans les deux thèmes : les pierres noires et blanches s'y lisent toujours.
private val BoardColor = Color(0xFFE2B96F)
private val LineColor = Color(0xFF5D4037)
private val BlackStone = Color(0xFF212121)
private val WhiteStone = Color(0xFFFAFAFA)
private val MarkColor = Color(0xFFE53935)

/** Le Gomoku : premier joueur = pierres noires, deuxième joueur = pierres blanches. */
internal val GomokuSpec = DuoGameSpec<GomokuMatch>(
    rulesId = GOMOKU_ID,
    title = R.string.game_gomoku,
    initial = { GomokuMatch() },
    decode = { GomokuMatch.decode(it) },
    colorOf = { _, player -> if (player == 1) BlackStone else WhiteStone },
    board = { match, enabled, onPlay -> GomokuBoard(match, enabled, onPlay) }
)

/**
 * La grille de 15 × 15 intersections. Les cases sont petites : toucher une intersection la choisit (une pierre
 * transparente s'y pose), puis « Valider le coup » joue la pierre. On peut changer d'avis en touchant une autre case.
 */
@Composable
private fun GomokuBoard(match: GomokuMatch, enabled: Boolean, onPlay: (Int) -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    // Chaque coup (le mien ou celui de l'autre) efface le choix en cours.
    LaunchedEffect(match.board) { selected = -1 }

    val cells = remember(match.board) { match.cells }
    val line = remember(match.board) { Gomoku.winningLine(cells) }
    val resources = LocalContext.current.resources
    val preview = if (match.current == 1) BlackStone else WhiteStone

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BoardColor, RoundedCornerShape(8.dp))
                .padding(4.dp)
        ) {
            for (row in 0 until Gomoku.SIZE) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until Gomoku.SIZE) {
                        val index = row * Gomoku.SIZE + col
                        val value = cells[index]
                        val isSelected = enabled && index == selected
                        val isLast = index == match.last
                        val inLine = line?.contains(index) == true
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .drawBehind {
                                    val cx = size.width / 2
                                    val cy = size.height / 2
                                    val width = 1.dp.toPx()
                                    // Les lignes s'arrêtent au milieu des cases du bord : c'est le bord du plateau.
                                    val left = if (col == 0) cx else 0f
                                    val right = if (col == Gomoku.SIZE - 1) cx else size.width
                                    val top = if (row == 0) cy else 0f
                                    val bottom = if (row == Gomoku.SIZE - 1) cy else size.height
                                    drawLine(LineColor, Offset(left, cy), Offset(right, cy), strokeWidth = width)
                                    drawLine(LineColor, Offset(cx, top), Offset(cx, bottom), strokeWidth = width)
                                    val radius = size.minDimension * 0.44f
                                    if (value != 0) {
                                        drawCircle(if (value == 1) BlackStone else WhiteStone, radius, Offset(cx, cy))
                                        // Le contour fait ressortir la pierre blanche sur le bois.
                                        drawCircle(BlackStone, radius, Offset(cx, cy), style = Stroke(width))
                                    } else if (isSelected) {
                                        drawCircle(preview.copy(alpha = 0.55f), radius, Offset(cx, cy))
                                        drawCircle(BlackStone, radius, Offset(cx, cy), style = Stroke(width))
                                    }
                                    if (isLast) drawCircle(MarkColor, radius * 0.3f, Offset(cx, cy))
                                    if (inLine) drawCircle(MarkColor, radius + 1.dp.toPx(), Offset(cx, cy), style = Stroke(2.dp.toPx()))
                                }
                                .semantics {
                                    contentDescription = resources.getString(
                                        when (value) {
                                            1 -> R.string.gomoku_cell_black
                                            2 -> R.string.gomoku_cell_white
                                            else -> R.string.gomoku_cell_empty
                                        },
                                        col + 1,
                                        row + 1
                                    )
                                }
                                .clickable(enabled = enabled && value == 0, role = Role.Button) { selected = index }
                        )
                    }
                }
            }
        }
        if (!match.isOver) {
            if (enabled && selected < 0) {
                Text(
                    text = stringResource(R.string.gomoku_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            Button(
                onClick = {
                    val move = selected
                    selected = -1
                    if (move >= 0) onPlay(move)
                },
                enabled = enabled && selected >= 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.gomoku_confirm))
            }
        }
    }
}
