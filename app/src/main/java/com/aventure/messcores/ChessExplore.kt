package com.aventure.messcores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.max

/*
 * Le mode « explorer » de l'échiquier sorcier : un œil au bout de la ligne qui annonce le tour. Une fois activé, le plateau
 * ne joue plus de coups (les touches servent à la caméra) et se regarde avec les doigts :
 *  - deux doigts qui s'écartent / se rapprochent : zoom avant / arrière ;
 *  - deux doigts qui glissent dans le même sens : le plateau tourne (côté = tour autour de l'axe vertical, haut / bas =
 *    inclinaison, comme la caméra des prises) ;
 *  - un doigt qui glisse : on se déplace sur le plateau (utile une fois zoomé).
 * Désactiver l'œil remet le plateau droit, pour jouer.
 */

/** La vue du plateau en mode explorer. Les valeurs sont lues dans `graphicsLayer` : bouger ne recompose rien. */
internal class BoardView {
    var zoom by mutableFloatStateOf(1f)
    var panX by mutableFloatStateOf(0f)
    var panY by mutableFloatStateOf(0f)

    /** Rotation autour de l'axe horizontal, en degrés (positif : on regarde le plateau plus d'en haut). */
    var tilt by mutableFloatStateOf(0f)

    /** Rotation autour de l'axe vertical, en degrés. */
    var orbit by mutableFloatStateOf(0f)

    fun reset() {
        zoom = 1f
        panX = 0f
        panY = 0f
        tilt = 0f
        orbit = 0f
    }

    fun zoomBy(factor: Float, width: Float, height: Float) {
        zoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        clampPan(width, height)
    }

    fun panBy(dx: Float, dy: Float, width: Float, height: Float) {
        panX += dx
        panY += dy
        clampPan(width, height)
    }

    fun rotateBy(dx: Float, dy: Float) {
        orbit = (orbit + dx * DEGREES_PER_PIXEL).coerceIn(-MAX_ORBIT, MAX_ORBIT)
        tilt = (tilt + dy * DEGREES_PER_PIXEL).coerceIn(-MAX_TILT, MAX_TILT)
    }

    // On ne peut pas pousser le plateau hors de la vue : la marge est celle que le zoom a agrandie.
    private fun clampPan(width: Float, height: Float) {
        val maxX = max(0f, (zoom - 1f) * width / 2f)
        val maxY = max(0f, (zoom - 1f) * height / 2f)
        panX = panX.coerceIn(-maxX, maxX)
        panY = panY.coerceIn(-maxY, maxY)
    }

    companion object {
        const val MIN_ZOOM = 0.7f
        const val MAX_ZOOM = 5f
        const val MAX_ORBIT = 75f
        const val MAX_TILT = 65f
        const val DEGREES_PER_PIXEL = 0.25f
    }
}

/**
 * Les gestes du mode explorer. Ils sont lus dans la passe « Initial » et consommés : les cases du plateau (et le défilement de
 * l'écran) ne reçoivent donc rien tant que le mode est actif.
 */
internal fun Modifier.exploreGestures(view: BoardView): Modifier = pointerInput(view) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        down.consume()
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            val width = size.width.toFloat()
            val height = size.height.toFloat()
            if (pressed.size >= 2) {
                val a = pressed[0]
                val b = pressed[1]
                val before = (a.previousPosition - b.previousPosition).getDistance()
                val after = (a.position - b.position).getDistance()
                if (before > 0f && after > 0f) view.zoomBy(after / before, width, height)
                // Deux doigts qui vont dans le même sens font tourner le plateau ; s'ils vont en sens contraire, c'est un zoom.
                val da = a.position - a.previousPosition
                val db = b.position - b.previousPosition
                if (da.x * db.x + da.y * db.y > 0f) {
                    val mean = Offset((da.x + db.x) / 2f, (da.y + db.y) / 2f)
                    view.rotateBy(mean.x, mean.y)
                }
            } else if (pressed.size == 1) {
                val c = pressed[0]
                val delta = c.position - c.previousPosition
                view.panBy(delta.x, delta.y, width, height)
            }
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

/**
 * La ligne qui annonce le tour, avec l'œil au bout (style sorcier seulement) : il active le mode explorer ([explore]) et le
 * quitte.
 */
@Composable
internal fun ChessTurnLine(
    text: String,
    style: TextStyle,
    showEye: Boolean,
    explore: Boolean,
    onToggleExplore: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = text, style = style, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (showEye) {
            val label = stringResource(if (explore) R.string.chess_explore_off else R.string.chess_explore_on)
            if (explore) {
                FilledTonalIconButton(onClick = onToggleExplore) {
                    Icon(Icons.Filled.VisibilityOff, contentDescription = label)
                }
            } else {
                IconButton(onClick = onToggleExplore) {
                    Icon(Icons.Filled.Visibility, contentDescription = label)
                }
            }
        }
    }
}
