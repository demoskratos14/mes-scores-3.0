package com.aventure.messcores

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

// Couleurs des cartes, proches du Uno officiel (rouge, jaune, vert, bleu).
private val UnoCardRed = Color(0xFFE5202A)
private val UnoCardYellow = Color(0xFFFFD500)
private val UnoCardGreen = Color(0xFF16A04B)
private val UnoCardBlue = Color(0xFF0A8FD6)
private val UnoCardBlack = Color(0xFF111111)
private val UnoCardEdge = Color(0xFFFDFDFD)

/** Largeur / hauteur d'une carte de Uno. */
private const val UNO_CARD_RATIO = 0.64f

private fun unoCardColor(color: Int): Color = when (color) {
    0 -> UnoCardRed
    1 -> UnoCardYellow
    2 -> UnoCardGreen
    3 -> UnoCardBlue
    else -> UnoCardBlack
}

/** Petit repère écrit dans les coins : chiffre ou symbole de l'action ; vide pour un joker simple. */
private fun cornerText(kind: Int): String = when (kind) {
    in 0..9 -> kind.toString()
    UnoScoring.SKIP -> "⊘"
    UnoScoring.REVERSE -> "⇄"
    UnoScoring.DRAW_TWO -> "+2"
    UnoScoring.WILD_FOUR -> "+4"
    else -> ""
}

/**
 * Une carte de Uno dessinée comme sur un vrai jeu : bord blanc, fond de la couleur, grande ellipse blanche inclinée avec le
 * chiffre ou le symbole de l'action, repères dans deux coins. Joker : fond noir et ellipse aux quatre couleurs ; joker +4 :
 * quatre petites cartes. [playable] faux : grisée ; [selected] entoure la carte ; [onClick] null : simple affichage.
 */
@Composable
internal fun UnoCardImage(
    card: Int,
    description: String,
    playable: Boolean,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val measurer = rememberTextMeasurer()
    val shape = RoundedCornerShape(8.dp)
    val kind = UnoScoring.kind(card)
    val color = UnoScoring.color(card)
    Box(
        modifier = modifier
            .aspectRatio(UNO_CARD_RATIO)
            .clip(shape)
            .border(
                width = if (selected) 4.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray,
                shape = shape
            )
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { contentDescription = description }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawUnoCard(measurer, kind, color)
            if (!playable) {
                drawRect(Color(0xFF9E9E9E).copy(alpha = 0.68f))
            }
        }
    }
}

private fun DrawScope.drawUnoCard(measurer: TextMeasurer, kind: Int, color: Int) {
    val w = size.width
    val h = size.height
    val center = Offset(w / 2, h / 2)
    val wild = color < 0
    val face = unoCardColor(color)

    // Bord blanc puis fond coloré en retrait.
    drawRect(UnoCardEdge)
    val inset = w * 0.055f
    drawRoundRect(
        color = face,
        topLeft = Offset(inset, inset),
        size = Size(w - 2 * inset, h - 2 * inset),
        cornerRadius = CornerRadius(w * 0.09f)
    )

    // Grande ellipse blanche inclinée, rognée par le bord de la carte.
    val ovalW = w * 0.74f
    val ovalH = h * 0.86f
    val inner = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                Rect(Offset(inset, inset), Size(w - 2 * inset, h - 2 * inset)),
                CornerRadius(w * 0.09f)
            )
        )
    }
    clipPath(inner) {
        rotate(degrees = 28f, pivot = center) {
            val oval = Rect(center.x - ovalW / 2, center.y - ovalH / 2, center.x + ovalW / 2, center.y + ovalH / 2)
            drawOval(Color.White, oval.topLeft, oval.size)
            if (kind == UnoScoring.WILD) {
                val ring = w * 0.035f
                val colored = Path().apply { addOval(oval.deflate(ring)) }
                clipPath(colored) {
                    val l = oval.left
                    val t = oval.top
                    val r = oval.right
                    val b = oval.bottom
                    drawRect(UnoCardRed, Offset(l, t), Size(center.x - l, center.y - t))
                    drawRect(UnoCardBlue, Offset(center.x, t), Size(r - center.x, center.y - t))
                    drawRect(UnoCardGreen, Offset(l, center.y), Size(center.x - l, b - center.y))
                    drawRect(UnoCardYellow, Offset(center.x, center.y), Size(r - center.x, b - center.y))
                }
            }
        }
    }

    // Motif central.
    when (kind) {
        in 0..9 -> {
            val style = TextStyle(
                fontSize = (w * 0.56f).toSp(),
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic
            )
            val layout = measurer.measure(kind.toString(), style)
            val topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f)
            drawText(
                layout,
                color = Color.Black.copy(alpha = 0.9f),
                topLeft = topLeft + Offset(w * 0.025f, w * 0.025f),
                textDecoration = if (kind == 6 || kind == 9) TextDecoration.Underline else null
            )
            drawText(
                layout,
                color = face,
                topLeft = topLeft,
                textDecoration = if (kind == 6 || kind == 9) TextDecoration.Underline else null
            )
        }
        UnoScoring.SKIP -> {
            val r = w * 0.27f
            val stroke = w * 0.085f
            drawCircle(face, r, center, style = Stroke(stroke))
            val d = r * 0.7071f
            drawLine(face, Offset(center.x - d, center.y + d), Offset(center.x + d, center.y - d), strokeWidth = stroke)
        }
        UnoScoring.REVERSE -> {
            rotate(degrees = 28f, pivot = center) {
                val u = w
                val stroke = u * 0.075f
                fun arrow(x: Float, up: Boolean) {
                    val dir = if (up) -1f else 1f
                    val shaftFrom = Offset(center.x + x * u, center.y - dir * 0.2f * u)
                    val shaftTo = Offset(center.x + x * u, center.y + dir * 0.08f * u)
                    drawLine(face, shaftFrom, shaftTo, strokeWidth = stroke)
                    val head = Path().apply {
                        moveTo(center.x + x * u, center.y + dir * 0.27f * u)
                        lineTo(center.x + x * u - 0.12f * u, center.y + dir * 0.07f * u)
                        lineTo(center.x + x * u + 0.12f * u, center.y + dir * 0.07f * u)
                        close()
                    }
                    drawPath(head, face)
                }
                arrow(-0.12f, up = false)
                arrow(0.12f, up = true)
            }
        }
        UnoScoring.DRAW_TWO -> {
            rotate(degrees = 20f, pivot = center) {
                val cw = w * 0.25f
                val ch = w * 0.38f
                val radius = CornerRadius(w * 0.03f)
                drawRoundRect(
                    face,
                    Offset(center.x - cw / 2 - w * 0.07f, center.y - ch / 2 - w * 0.07f),
                    Size(cw, ch),
                    radius
                )
                val front = Offset(center.x - cw / 2 + w * 0.07f, center.y - ch / 2 + w * 0.07f)
                drawRoundRect(Color.White, front, Size(cw, ch), radius)
                drawRoundRect(face, front, Size(cw, ch), radius, style = Stroke(w * 0.035f))
            }
        }
        UnoScoring.WILD_FOUR -> {
            val cw = w * 0.22f
            val ch = w * 0.32f
            val radius = CornerRadius(w * 0.03f)
            val cards = listOf(
                Triple(UnoCardBlue, -42f, -0.17f),
                Triple(UnoCardGreen, -14f, -0.06f),
                Triple(UnoCardRed, 14f, 0.06f),
                Triple(UnoCardYellow, 42f, 0.17f)
            )
            for ((c, angle, dx) in cards) {
                val pivot = Offset(center.x, center.y + ch * 0.55f)
                rotate(degrees = angle, pivot = pivot) {
                    val topLeft = Offset(center.x - cw / 2 + dx * 0.15f * w, center.y - ch / 2 - w * 0.02f)
                    drawRoundRect(c, topLeft, Size(cw, ch), radius)
                    drawRoundRect(Color.White, topLeft, Size(cw, ch), radius, style = Stroke(w * 0.02f))
                }
            }
        }
    }

    // Repères dans les coins : en haut à gauche, et en bas à droite à l'envers.
    val corner = cornerText(kind)
    if (corner.isNotEmpty()) {
        val style = TextStyle(
            fontSize = (w * (if (corner.length > 1) 0.15f else 0.2f)).toSp(),
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic
        )
        val layout = measurer.measure(corner, style)
        val underline = if (kind == 6 || kind == 9) TextDecoration.Underline else null
        val shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(w * 0.01f, w * 0.01f), 0f)
        val pos = Offset(inset + w * 0.04f, inset + w * 0.005f)
        drawText(layout, color = Color.White, topLeft = pos, shadow = shadow, textDecoration = underline)
        rotate(degrees = 180f, pivot = center) {
            drawText(layout, color = Color.White, topLeft = pos, shadow = shadow, textDecoration = underline)
        }
    }
}
