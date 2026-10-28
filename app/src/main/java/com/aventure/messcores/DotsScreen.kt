package com.aventure.messcores

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** La Pipopipette : le premier joueur trace en couleur principale (carrés marqués d'un rond), le deuxième en couleur secondaire (carrés marqués d'un carré). */
internal val DotsSpec = DuoGameSpec<DotsMatch>(
    rulesId = DOTS_ID,
    title = R.string.game_dots,
    initial = { DotsMatch() },
    decode = { DotsMatch.decode(it) },
    colorOf = { scheme, player -> if (player == 1) scheme.primary else scheme.tertiary },
    board = { match, enabled, onPlay -> DotsBoard(match, enabled, onPlay) },
    chooseColors = true
)

private val Margin = 20.dp

// Zone qui réagit au toucher autour d'un trait : assez épaisse pour le doigt, assez courte pour ne pas gêner les traits voisins.
private val TouchThickness = 36.dp
private val TouchTrim = 22.dp

/**
 * La grille de 6 × 6 points. Toucher l'espace entre deux points voisins choisit ce trait (il apparaît en transparence) ;
 * « Valider le trait » le trace pour de bon et donne la main. On peut changer d'avis en touchant un autre trait. Les
 * carrés fermés sont colorés et marqués d'une forme (rond ou carré) en plus de leur couleur, pour qu'on les distingue
 * sans voir les couleurs.
 */
@Composable
private fun DotsBoard(match: DotsMatch, enabled: Boolean, onPlay: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val chosen = LocalDuoColors.current
    val colorA = chosen?.first ?: scheme.primary
    val colorB = chosen?.second ?: scheme.tertiary
    val ink = scheme.onSurface
    val resources = LocalResources.current
    // Le trait choisi, pas encore validé (-1 : aucun). Chaque coup, le mien ou celui de l'autre, efface le choix en cours.
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(match.edges) { selected = -1 }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.dots_boxes_count, match.boxesA, match.boxesB),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        // Dit pourquoi le tour change ou non : après un trait, la main passe à l'autre joueur, sauf si ce trait vient de fermer un carré.
        val closedSquare = match.last >= 0 && !match.isOver &&
            Dots.boxesOf(match.last).any { match.boxes[it] == match.edges[match.last] }
        Text(
            text = stringResource(if (closedSquare) R.string.dots_hint_replay else R.string.dots_hint_rule),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (closedSquare) FontWeight.Bold else FontWeight.Normal,
            color = if (closedSquare) scheme.primary else scheme.onSurfaceVariant
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            val step: Dp = (maxWidth - Margin * 2) / Dots.SIZE

            Canvas(modifier = Modifier.fillMaxSize()) {
                val margin = Margin.toPx()
                val stepPx = (size.width - margin * 2) / Dots.SIZE
                fun dot(row: Int, col: Int) = Offset(margin + col * stepPx, margin + row * stepPx)

                // Les carrés fermés : un fond de la couleur du joueur et sa forme.
                for (box in 0 until Dots.BOXES) {
                    val owner = match.boxes[box]
                    if (owner == '0') continue
                    val top = dot(box / Dots.SIZE, box % Dots.SIZE)
                    val color = if (owner == '1') colorA else colorB
                    // Couleur pleine, sans transparence : toute la case est de la couleur du trait.
                    drawRect(color, top, Size(stepPx, stepPx))
                    val mark = contentColorFor(color)
                    val center = Offset(top.x + stepPx / 2, top.y + stepPx / 2)
                    val half = stepPx * 0.16f
                    if (owner == '1') drawCircle(mark, half, center)
                    else drawRect(mark, Offset(center.x - half, center.y - half), Size(half * 2, half * 2))
                }

                // Les traits : libres en gris clair, tracés dans la couleur du joueur ; le dernier est souligné.
                for (edge in 0 until Dots.EDGES) {
                    val from: Offset
                    val to: Offset
                    if (Dots.isHorizontal(edge)) {
                        from = dot(edge / Dots.SIZE, edge % Dots.SIZE)
                        to = dot(edge / Dots.SIZE, edge % Dots.SIZE + 1)
                    } else {
                        val e = edge - Dots.HORIZONTAL
                        from = dot(e / (Dots.SIZE + 1), e % (Dots.SIZE + 1))
                        to = dot(e / (Dots.SIZE + 1) + 1, e % (Dots.SIZE + 1))
                    }
                    val owner = match.edges[edge]
                    if (owner == '0' && enabled && edge == selected) {
                        // Le trait choisi : dans la couleur du joueur, en transparence tant qu'il n'est pas validé.
                        drawLine(
                            (if (match.current == 1) colorA else colorB).copy(alpha = 0.55f),
                            from, to, strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round
                        )
                    } else if (owner == '0') {
                        drawLine(ink.copy(alpha = 0.18f), from, to, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    } else {
                        if (edge == match.last) drawLine(ink.copy(alpha = 0.3f), from, to, strokeWidth = 11.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(if (owner == '1') colorA else colorB, from, to, strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
                    }
                }

                for (row in 0..Dots.SIZE) {
                    for (col in 0..Dots.SIZE) drawCircle(ink, 5.dp.toPx(), dot(row, col))
                }
            }

            // Une zone par trait, par-dessus le dessin : elle porte le toucher et le nom lu par TalkBack.
            for (edge in 0 until Dots.EDGES) {
                val horizontal = Dots.isHorizontal(edge)
                val row: Int
                val col: Int
                if (horizontal) {
                    row = edge / Dots.SIZE
                    col = edge % Dots.SIZE
                } else {
                    row = (edge - Dots.HORIZONTAL) / (Dots.SIZE + 1)
                    col = (edge - Dots.HORIZONTAL) % (Dots.SIZE + 1)
                }
                val width = if (horizontal) step - TouchTrim else TouchThickness
                val height = if (horizontal) TouchThickness else step - TouchTrim
                val centerX = if (horizontal) Margin + step * col + step / 2 else Margin + step * col
                val centerY = if (horizontal) Margin + step * row else Margin + step * row + step / 2
                val free = match.edges[edge] == '0'
                val description = resources.getString(
                    if (horizontal) R.string.dots_edge_h else R.string.dots_edge_v,
                    row + 1,
                    col + 1
                )
                val state = resources.getString(if (free) R.string.dots_edge_free else R.string.dots_edge_taken)
                Box(
                    modifier = Modifier
                        .offset(x = centerX - width / 2, y = centerY - height / 2)
                        .size(width, height)
                        .semantics {
                            contentDescription = description
                            stateDescription = state
                        }
                        .clickable(enabled = enabled && free, role = Role.Button) { selected = edge }
                )
            }
        }
        if (!match.isOver) {
            if (enabled && selected < 0) {
                Text(
                    text = stringResource(R.string.dots_hint_choose),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
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
                Text(stringResource(R.string.dots_confirm))
            }
        }
    }
}
