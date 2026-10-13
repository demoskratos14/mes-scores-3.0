package com.aventure.messcores

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

// Couleurs de secours si une ancienne sauvegarde n'a pas conservé celles des joueurs.
private val FALLBACK_COLORS = listOf(
    Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00),
    Color(0xFF8E24AA), Color(0xFF00897B), Color(0xFFD81B60), Color(0xFF3949AB)
)

/**
 * Icône de l'en-tête des écrans de score : ouvre la courbe d'évolution des scores de la partie en
 * cours. N'apparaît qu'à partir de deux manches saisies (voir [ScoreProgress.series]).
 */
@Composable
fun ScoreChartButton(viewModel: ScoreViewModel) {
    val available by remember(viewModel) { derivedStateOf { ScoreProgress.series(viewModel.chartSnapshot()) != null } }
    var showChart by remember { mutableStateOf(false) }
    if (available) {
        IconButton(onClick = { showChart = true }) {
            Icon(
                imageVector = Icons.Filled.Timeline,
                contentDescription = stringResource(R.string.chart_button),
                tint = Color.White
            )
        }
    }
    if (showChart && available) {
        ScoreChartDialog(game = viewModel.chartSnapshot(), onDismiss = { showChart = false })
    }
}

/** Fenêtre avec la courbe du total de chaque joueur, manche après manche, et sa légende. */
@Composable
fun ScoreChartDialog(game: SavedGame, onDismiss: () -> Unit) {
    val series = remember(game) { ScoreProgress.series(game) }
    if (series == null) return
    val colors = game.players.indices.map { i ->
        game.playerColorsArgb.getOrNull(i)?.let { Color(it) } ?: FALLBACK_COLORS[i % FALLBACK_COLORS.size]
    }
    val all = series.flatten()
    val lowest = minOf(all.min(), 0)
    val highest = maxOf(all.max(), 0)
    val rounds = series.first().size
    val axisColor = MaterialTheme.colorScheme.outline
    val description = stringResource(R.string.chart_description)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chart_title), modifier = Modifier.semantics { heading() }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .semantics { contentDescription = description }
                        .padding(8.dp)
                ) {
                    val span = (highest - lowest).coerceAtLeast(1).toFloat()
                    val stepX = if (rounds > 1) size.width / (rounds - 1) else 0f
                    fun yOf(value: Int): Float = size.height - (value - lowest) / span * size.height

                    // Axe vertical et ligne du zéro.
                    drawLine(axisColor, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 2f)
                    drawLine(axisColor, Offset(0f, yOf(0)), Offset(size.width, yOf(0)), strokeWidth = 2f)

                    series.forEachIndexed { player, values ->
                        val path = Path()
                        values.forEachIndexed { i, value ->
                            val point = Offset(i * stepX, yOf(value))
                            if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                        }
                        drawPath(path, colors[player], style = Stroke(width = 4f, cap = StrokeCap.Round))
                        values.forEachIndexed { i, value ->
                            drawCircle(colors[player], radius = 5f, center = Offset(i * stepX, yOf(value)))
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.chart_range, rounds, lowest, highest),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Légende : couleur, nom et total actuel de chaque joueur.
                game.players.forEachIndexed { player, name ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(colors[player])
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$name : ${series[player].last()}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) }
        }
    )
}
