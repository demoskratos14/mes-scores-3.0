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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Le mode « explorer » de l'échiquier sorcier : un œil au bout de la ligne qui annonce le tour. Une fois activé, le plateau
 * passe en vraie 3D (voir WizardExplore3D.kt) : une caméra tourne, s'approche et se déplace au-dessus de l'échiquier. Les
 * touches ne jouent plus de coups, elles pilotent la caméra :
 *  - deux doigts qui s'écartent / se rapprochent : la caméra s'approche / s'éloigne ;
 *  - deux doigts qui glissent dans le même sens : la caméra tourne autour du plateau (côté : tour complet, haut / bas :
 *    hauteur de vue, de presque à ras des pièces jusqu'à la vue du dessus) ;
 *  - un doigt qui glisse : on se déplace sur le plateau.
 * Désactiver l'œil rend la main au plateau de jeu habituel.
 */

/** La caméra du mode explorer. Les valeurs sont lues au dessin : bouger ne recompose rien (sauf [moving]). */
internal class BoardView {
    /** Tour autour du plateau, en degrés (0 : on regarde depuis le côté du joueur du bas). */
    var yaw by mutableFloatStateOf(DEFAULT_YAW)

    /** Hauteur de la caméra, en degrés au-dessus de l'horizon (90 : vue du dessus). */
    var pitch by mutableFloatStateOf(DEFAULT_PITCH)

    /** Distance au point regardé, en cases. */
    var distance by mutableFloatStateOf(DEFAULT_DISTANCE)

    /** Le point du plateau regardé (en cases : 4, 4 est le centre). */
    var targetX by mutableFloatStateOf(CENTER)
    var targetZ by mutableFloatStateOf(CENTER)

    /** Un doigt est posé : la scène se calcule alors en plus petit, pour rester fluide. */
    var moving by mutableStateOf(false)

    /** Largeur / hauteur de la zone de dessin (renseignée par l'écran, lue au dessin). */
    var aspect: Float = DEFAULT_ASPECT

    fun reset() {
        yaw = DEFAULT_YAW
        pitch = DEFAULT_PITCH
        distance = DEFAULT_DISTANCE
        targetX = CENTER
        targetZ = CENTER
    }

    /** [factor] > 1 : les doigts s'écartent, la caméra s'approche. */
    fun zoomBy(factor: Float) {
        if (factor <= 0f) return
        distance = (distance / factor).coerceIn(MIN_DISTANCE, MAX_DISTANCE)
    }

    /** La caméra tourne : le plateau suit les doigts (vers la droite, il tourne vers la droite ; vers le bas, on le voit plus de dessus). */
    fun rotateBy(dx: Float, dy: Float) {
        yaw = (yaw - dx * DEGREES_PER_PIXEL) % 360f
        pitch = (pitch + dy * DEGREES_PER_PIXEL).coerceIn(MIN_PITCH, MAX_PITCH)
    }

    /** Déplace le point regardé : le plateau suit le doigt ([heightPx] : la hauteur de la zone de dessin, en pixels). */
    fun panBy(dx: Float, dy: Float, heightPx: Float) {
        if (heightPx <= 0f) return
        val a = min(aspect, 1f).coerceAtLeast(0.2f)
        val worldPerPixel = 2f * distance * (TAN_HALF_WIDE / a) / heightPx
        val radians = yaw * (PI / 180.0).toFloat()
        val rightX = cos(radians)
        val rightZ = -sin(radians)
        val forwardX = -sin(radians)
        val forwardZ = -cos(radians)
        // Vu de haut, un même geste vertical couvre plus de plateau que vu de près du plateau.
        val depth = worldPerPixel / sin(pitch * (PI / 180.0).toFloat()).coerceAtLeast(0.25f)
        targetX = (targetX - rightX * dx * worldPerPixel + forwardX * dy * depth).coerceIn(-MARGIN, 8f + MARGIN)
        targetZ = (targetZ - rightZ * dx * worldPerPixel + forwardZ * dy * depth).coerceIn(-MARGIN, 8f + MARGIN)
    }

    companion object {
        const val DEFAULT_YAW = 0f
        const val DEFAULT_PITCH = 50f
        const val DEFAULT_DISTANCE = 15f
        const val DEFAULT_ASPECT = 0.8f
        const val CENTER = 4f
        const val MIN_DISTANCE = 3.5f
        const val MAX_DISTANCE = 30f
        const val MIN_PITCH = 8f
        const val MAX_PITCH = 88f
        const val MARGIN = 1.5f
        const val DEGREES_PER_PIXEL = 0.3f

        /** Tangente de la moitié du champ horizontal : 17,5°, comme le champ de 35° de la caméra par défaut. */
        const val TAN_HALF_WIDE = 0.3153f
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
        view.moving = true
        try {
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    val a = pressed[0]
                    val b = pressed[1]
                    val before = (a.previousPosition - b.previousPosition).getDistance()
                    val after = (a.position - b.position).getDistance()
                    if (before > 0f && after > 0f) view.zoomBy(after / before)
                    // Deux doigts qui vont dans le même sens font tourner la caméra ; en sens contraire, c'est un zoom.
                    val da = a.position - a.previousPosition
                    val db = b.position - b.previousPosition
                    if (da.x * db.x + da.y * db.y > 0f) {
                        view.rotateBy((da.x + db.x) / 2f, (da.y + db.y) / 2f)
                    }
                } else if (pressed.size == 1) {
                    val c = pressed[0]
                    val delta = c.position - c.previousPosition
                    view.panBy(delta.x, delta.y, size.height.toFloat())
                }
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            } while (event.changes.any { it.pressed })
        } finally {
            view.moving = false
        }
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
