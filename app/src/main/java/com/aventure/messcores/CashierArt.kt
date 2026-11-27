package com.aventure.messcores

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Les dessins de l'écran de Caisse, dans le repère de l'écran (720 x 405), le même que celui de Drive. La scène : un mur de
 * supermarché avec l'enseigne, la file des clients qui attendent au fond, le client de la caisse à gauche, la caissière derrière
 * le tapis et le scanner à droite. Au milieu, un grand cadre montre l'article devant le scanner (c'est là qu'on lit ses faces) ;
 * à droite, un cadre change selon l'étape : note, images à choisir, pièce d'identité, terminal de carte ou monnaie à rendre.
 * Le décor qui ne bouge pas se dessine une fois avec [drawCashierScene] ; tout le reste avec [drawCashierLive].
 */
internal class CashierLabels(
    val time: String,
    val score: String,
    val sign: String,
    val customers: String,
    val total: String,
    val received: String,
    val change: String,
    val euro: String,
    val kg: String,
    val card: String,
    val cash: String,
    val idCheck: String,
    /** Le texte de chaque message, indexé par CASHIER_POP_* (la case 0 est vide). */
    val pops: List<String>
)

private object CashierLook {
    val Ink = Color(0xFF20241F)
    val Red = Color(0xFFE2001A)
    val Blue = Color(0xFF2B6CB0)
    val White = Color(0xFFF8FBFC)
    val Metal = Color(0xFFB9C4C9)
    val Green = Color(0xFF2E9E4F)
    val GreenDark = Color(0xFF176B33)
    val Amber = Color(0xFFF0B429)
    val Skin = Color(0xFFD99B79)
    val Counter = Color(0xFF3E4A59)
    val Belt = Color(0xFF4A5057)

    // Le mur finit ici ; le sol commence.
    const val WALL_END = 262f

    // Le cadre du scanner, au milieu : l'article y est montré de face, sous la ligne rouge du laser.
    const val PANEL_X = 205f
    const val PANEL_Y = 90f
    const val PANEL_W = 220f
    const val PANEL_H = 162f
    const val FACE_CX = 315f
    const val FACE_CY = 150f
    const val FACE_HALF = 56f

    // Le cadre de droite : la note, les images à choisir, la pièce d'identité, le terminal ou la monnaie.
    const val SIDE_X = 440f
    const val SIDE_Y = 90f
    const val SIDE_W = 268f
    const val SIDE_H = 162f

    // Le tapis (les articles arrivent de la gauche vers le scanner), le scanner, la caisse enregistreuse et le comptoir.
    const val BELT_Y = 332f
    const val BELT_H = 20f
    const val BELT_X0 = 110f
    const val SCANNER_X = 470f
    const val SCANNER_W = 110f
    const val BELT_STEP = 46f
    const val REGISTER_X = 592f
    const val REGISTER_Y = 296f
    const val REGISTER_W = 114f
    const val REGISTER_H = 56f

    // Le client de la caisse et la caissière (les pieds de la caissière sont cachés par le comptoir).
    const val CUSTOMER_X = 72f
    const val CUSTOMER_FEET = 396f
    const val CASHIER_X = 530f
    const val CASHIER_FEET = 374f
    const val CASHIER_SCALE = 0.9f
}

private const val CASHIER_DEG = 0.017453292f

private fun Color.fade(a: Float): Color = copy(alpha = alpha * a)

/** Un texte centré (ou aligné) en ([x], [y]), de [px] pixels de l'écran de hauteur de police ; [align] : -1 à gauche, 0 centré, 1 à droite. */
private fun DrawScope.cashierWord(
    measurer: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    px: Float,
    color: Color,
    align: Int = 0,
    italic: Boolean = false,
    spacing: Float = 0f
) {
    val style = TextStyle(
        color = color,
        fontSize = px.toSp(),
        fontWeight = FontWeight.Bold,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        letterSpacing = spacing.toSp()
    )
    val result = measurer.measure(text = text, style = style, softWrap = false, maxLines = 1)
    val width = result.size.width.toFloat()
    val left = when {
        align < 0 -> x
        align > 0 -> x - width
        else -> x - width / 2f
    }
    drawText(result, topLeft = Offset(left, y - result.size.height / 2f))
}

// ------------------------------------------------------------------------------------------------------------ les articles

/** Les couleurs de fond des faces de chaque article (dans l'ordre de [Cashier.KINDS]). */
private val CASHIER_KIND_TINT = intArrayOf(
    0xFFD6E6F5.toInt(), 0xFFF7DE8A.toInt(), 0xFFE3E7EA.toInt(), 0xFFD3EDF8.toInt(), 0xFFE9C9DC.toInt(), 0xFFF6D88B.toInt(),
    0xFFC9DFCF.toInt(), 0xFFD6E6F5.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(),
    0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()
)

private fun DrawScope.cashierOutlineRRect(color: Color, x: Float, y: Float, w: Float, h: Float, r: Float, line: Float = 5f) {
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(r))
    drawRoundRect(CashierLook.Ink, Offset(x, y), Size(w, h), CornerRadius(r), style = Stroke(width = line))
}

private fun DrawScope.cashierOutlineOval(color: Color, x: Float, y: Float, w: Float, h: Float, line: Float = 5f) {
    drawOval(color, Offset(x, y), Size(w, h))
    drawOval(CashierLook.Ink, Offset(x, y), Size(w, h), style = Stroke(width = line))
}

private fun DrawScope.cashierOutlinePath(path: Path, color: Color, line: Float = 5f) {
    drawPath(path, color)
    drawPath(path, CashierLook.Ink, style = Stroke(width = line, join = StrokeJoin.Round))
}

/** Un article dessiné autour de l'origine, dans une boîte de 200 x 200 (de -100 à 100). */
private fun DrawScope.cashierItemArt(kind: Int) {
    val ink = CashierLook.Ink
    when (kind) {
        0 -> { // la brique de lait
            val roof = Path().apply {
                moveTo(-45f, -30f); lineTo(-30f, -78f); lineTo(30f, -78f); lineTo(45f, -30f); close()
            }
            cashierOutlinePath(roof, Color(0xFFBFD8F0))
            cashierOutlineRRect(Color(0xFFF1F6FB), -45f, -30f, 90f, 120f, 4f)
            drawRect(CashierLook.Blue, Offset(-42f, 10f), Size(84f, 44f))
            drawCircle(Color.White, 15f, Offset(0f, 32f))
            drawLine(ink, Offset(-45f, 10f), Offset(45f, 10f), strokeWidth = 3f)
            drawLine(ink, Offset(-45f, 54f), Offset(45f, 54f), strokeWidth = 3f)
        }
        1 -> { // la boîte de céréales
            cashierOutlineRRect(Color(0xFFF2C230), -50f, -88f, 100f, 176f, 6f)
            drawRect(Color(0xFFD9432F), Offset(-47f, -85f), Size(94f, 40f))
            cashierOutlineOval(Color.White, -30f, 0f, 60f, 44f)
            drawCircle(Color(0xFFE08A1E), 7f, Offset(-10f, 14f))
            drawCircle(Color(0xFFE08A1E), 7f, Offset(8f, 20f))
            drawCircle(Color(0xFFE08A1E), 7f, Offset(14f, 10f))
        }
        2 -> { // la boîte de conserve
            cashierOutlineRRect(Color(0xFFD0D5D8), -42f, -68f, 84f, 136f, 12f)
            drawRect(Color(0xFFC62828), Offset(-39f, -34f), Size(78f, 76f))
            drawLine(ink, Offset(-42f, -34f), Offset(42f, -34f), strokeWidth = 3f)
            drawLine(ink, Offset(-42f, 42f), Offset(42f, 42f), strokeWidth = 3f)
            drawCircle(Color.White, 17f, Offset(0f, 4f))
            drawCircle(Color(0xFF2E9E4F), 6f, Offset(0f, -10f))
        }
        3 -> { // la bouteille d'eau
            cashierOutlineRRect(CashierLook.Blue, -13f, -94f, 26f, 18f, 4f)
            cashierOutlineRRect(Color(0xFFCDEAF8), -16f, -80f, 32f, 34f, 6f)
            cashierOutlineRRect(Color(0xFFCDEAF8), -36f, -52f, 72f, 142f, 22f)
            drawRect(Color.White, Offset(-34f, -4f), Size(68f, 52f))
            drawArc(CashierLook.Blue, 200f, 140f, false, Offset(-22f, 8f), Size(44f, 26f), style = Stroke(width = 5f, cap = StrokeCap.Round))
            drawLine(ink, Offset(-34f, -4f), Offset(34f, -4f), strokeWidth = 3f)
            drawLine(ink, Offset(-34f, 48f), Offset(34f, 48f), strokeWidth = 3f)
        }
        4 -> { // le pot de confiture
            cashierOutlineRRect(Color(0xFF8E2C6B), -46f, -46f, 92f, 130f, 16f)
            cashierOutlineRRect(Color(0xFFD9432F), -50f, -72f, 100f, 28f, 6f)
            cashierOutlineRRect(Color(0xFFFFF3D6), -34f, 0f, 68f, 56f, 6f)
            drawCircle(Color(0xFFD9432F), 14f, Offset(0f, 28f))
            drawCircle(Color(0xFF2E9E4F), 4f, Offset(0f, 14f))
        }
        5 -> { // le paquet de pâtes
            cashierOutlineRRect(Color(0xFFF0B429), -48f, -82f, 96f, 164f, 6f)
            for (k in 0 until 6) {
                drawLine(ink, Offset(-48f + k * 19.2f, -82f), Offset(-38.4f + k * 19.2f, -70f), strokeWidth = 3f)
            }
            cashierOutlineRRect(Color(0xFFFFF3D6), -30f, -38f, 60f, 82f, 8f)
            for (k in 0 until 4) {
                drawArc(Color(0xFFC98A2B), 20f, 140f, false, Offset(-20f, -30f + k * 17f), Size(40f, 16f), style = Stroke(width = 5f, cap = StrokeCap.Round))
            }
        }
        6 -> { // la bouteille de vin
            cashierOutlineRRect(Color(0xFF1F5A3A), -12f, -88f, 24f, 82f, 8f)
            cashierOutlineRRect(Color(0xFF1F5A3A), -32f, -20f, 64f, 112f, 18f)
            cashierOutlineRRect(Color(0xFF9B1B30), -13f, -97f, 26f, 22f, 4f)
            cashierOutlineRRect(Color(0xFFFFF3D6), -27f, 20f, 54f, 50f, 4f)
            drawCircle(Color(0xFF9B1B30), 11f, Offset(0f, 45f))
        }
        7 -> { // le pack de six bouteilles, vu de dessus
            cashierOutlineRRect(Color(0xFFDCEBF5), -88f, -58f, 176f, 116f, 12f)
            for (row in 0 until 2) {
                for (col in 0 until 3) {
                    val cx = -54f + col * 54f
                    val cy = -27f + row * 54f
                    drawCircle(Color(0xFFBFE3F5), 21f, Offset(cx, cy))
                    drawCircle(ink, 21f, Offset(cx, cy), style = Stroke(width = 4f))
                    drawCircle(CashierLook.Blue, 10f, Offset(cx, cy))
                }
            }
        }
        8 -> { // la pomme
            drawCircle(Color(0xFFD62F2F), 60f, Offset(0f, 14f))
            drawCircle(ink, 60f, Offset(0f, 14f), style = Stroke(width = 5f))
            drawArc(Color.White.fade(0.6f), 200f, 60f, false, Offset(-44f, -30f), Size(60f, 60f), style = Stroke(width = 7f, cap = StrokeCap.Round))
            drawLine(Color(0xFF6B4A22), Offset(0f, -44f), Offset(8f, -76f), strokeWidth = 7f, cap = StrokeCap.Round)
            cashierOutlineOval(CashierLook.Green, 8f, -78f, 40f, 22f, 4f)
        }
        9 -> { // la banane
            drawArc(ink, 25f, 130f, false, Offset(-80f, -105f), Size(160f, 160f), style = Stroke(width = 46f, cap = StrokeCap.Round))
            drawArc(Color(0xFFF2D13B), 25f, 130f, false, Offset(-80f, -105f), Size(160f, 160f), style = Stroke(width = 34f, cap = StrokeCap.Round))
            drawCircle(Color(0xFF6B4A22), 9f, Offset(-72f, 9f))
            drawCircle(Color(0xFF6B4A22), 9f, Offset(72f, 9f))
        }
        10 -> { // la carotte
            cashierOutlineOval(CashierLook.Green, -34f, -96f, 22f, 52f, 4f)
            cashierOutlineOval(CashierLook.Green, -10f, -102f, 22f, 58f, 4f)
            cashierOutlineOval(CashierLook.Green, 14f, -96f, 22f, 52f, 4f)
            val body = Path().apply {
                moveTo(-34f, -46f); lineTo(34f, -46f); lineTo(0f, 96f); close()
            }
            cashierOutlinePath(body, Color(0xFFF07F1A))
            drawLine(ink.fade(0.5f), Offset(-18f, -20f), Offset(-4f, -20f), strokeWidth = 4f, cap = StrokeCap.Round)
            drawLine(ink.fade(0.5f), Offset(6f, 10f), Offset(16f, 10f), strokeWidth = 4f, cap = StrokeCap.Round)
            drawLine(ink.fade(0.5f), Offset(-8f, 40f), Offset(2f, 40f), strokeWidth = 4f, cap = StrokeCap.Round)
        }
        11 -> { // le citron
            cashierOutlineOval(Color(0xFFF6E04A), -78f, -14f, 20f, 28f, 4f)
            cashierOutlineOval(Color(0xFFF6E04A), 58f, -14f, 20f, 28f, 4f)
            cashierOutlineOval(Color(0xFFF6E04A), -70f, -46f, 140f, 92f)
            drawArc(Color.White.fade(0.6f), 200f, 70f, false, Offset(-48f, -34f), Size(70f, 50f), style = Stroke(width = 6f, cap = StrokeCap.Round))
        }
        12 -> { // la baguette
            rotate(-28f, Offset.Zero) {
                cashierOutlineRRect(Color(0xFFD9A25A), -94f, -22f, 188f, 44f, 22f)
                for (k in 0 until 4) {
                    drawLine(Color(0xFFF2D49A), Offset(-52f + k * 36f, -12f), Offset(-36f + k * 36f, 12f), strokeWidth = 6f, cap = StrokeCap.Round)
                }
            }
        }
        else -> { // le croissant
            drawArc(ink, 30f, 120f, false, Offset(-80f, -100f), Size(160f, 160f), style = Stroke(width = 62f, cap = StrokeCap.Round))
            drawArc(Color(0xFFE3A23C), 30f, 120f, false, Offset(-80f, -100f), Size(160f, 160f), style = Stroke(width = 50f, cap = StrokeCap.Round))
            for (angle in intArrayOf(50, 70, 90, 110, 130)) {
                val dx = cos(angle * CASHIER_DEG)
                val dy = sin(angle * CASHIER_DEG)
                drawLine(
                    Color(0xFFB87A22),
                    Offset(dx * 58f, -20f + dy * 58f),
                    Offset(dx * 102f, -20f + dy * 102f),
                    strokeWidth = 4f
                )
            }
        }
    }
}

/** Un article centré en ([cx], [cy]) ; [size] est la moitié du côté de sa boîte, en unités de l'écran. */
private fun DrawScope.cashierItemShape(kind: Int, cx: Float, cy: Float, size: Float) {
    withTransform({
        translate(cx, cy)
        scale(size / 100f, size / 100f, Offset.Zero)
    }) {
        cashierItemArt(kind)
    }
}

/** Des barres de code-barres dans la boîte ([x], [y], [w], [h]) ; leur motif vient de [seed]. */
private fun DrawScope.cashierBarcode(x: Float, y: Float, w: Float, h: Float, seed: Int, color: Color) {
    val count = 24
    val widths = IntArray(count) { 1 + ((seed * 7 + it * 13 + it * it) % 3) }
    val unit = w / widths.sum()
    var at = x
    for (i in 0 until count) {
        val barWidth = widths[i] * unit
        if (i % 2 == 0) drawRect(color, Offset(at, y), Size(barWidth, h))
        at += barWidth
    }
}

/** La face du bon code-barres : une étiquette blanche aux barres noires (un pack y ajoute les six bouteilles du lot). */
private fun DrawScope.cashierBarcodeFace(item: CashierItem, half: Float) {
    val ink = CashierLook.Ink
    drawRoundRect(Color.White, Offset(-half * 0.76f, -half * 0.52f), Size(half * 1.52f, half * 1.04f), CornerRadius(5f))
    drawRoundRect(ink.fade(0.5f), Offset(-half * 0.76f, -half * 0.52f), Size(half * 1.52f, half * 1.04f), CornerRadius(5f), style = Stroke(width = 2f))
    val seed = item.kind * 5 + item.price
    if (item.kind == Cashier.KIND_PACK) {
        for (row in 0 until 2) {
            for (col in 0 until 3) {
                drawCircle(CashierLook.Blue, 3.6f, Offset(-half * 0.6f + col * 9f, -half * 0.38f + row * 9f))
            }
        }
        cashierBarcode(-half * 0.64f, -half * 0.14f, half * 1.28f, half * 0.42f, seed, ink)
    } else {
        cashierBarcode(-half * 0.64f, -half * 0.36f, half * 1.28f, half * 0.6f, seed, ink)
    }
    drawRect(ink.fade(0.45f), Offset(-half * 0.5f, half * 0.34f), Size(half * 1.0f, 4f))
}

/** La face qui ressemble à un code-barres mais ne se scanne pas : l'étiquette jaune de promotion, ou le code d'une seule bouteille du pack. */
private fun DrawScope.cashierDecoyFace(item: CashierItem, half: Float) {
    val ink = CashierLook.Ink
    if (item.kind == Cashier.KIND_PACK) {
        // Le code d'une bouteille : petite étiquette grise, une seule bouteille dessinée.
        drawRoundRect(Color(0xFFE2E2E2), Offset(-half * 0.56f, -half * 0.3f), Size(half * 1.12f, half * 0.6f), CornerRadius(4f))
        drawCircle(CashierLook.Blue, 4f, Offset(-half * 0.42f, -half * 0.16f))
        drawRect(CashierLook.Blue, Offset(-half * 0.45f, -half * 0.12f), Size(6f, 9f))
        cashierBarcode(-half * 0.3f, -half * 0.18f, half * 0.76f, half * 0.28f, item.price + 3, Color(0xFF777777))
    } else {
        // L'étiquette de promotion : fond jaune, barres rouges et pastille rouge avec un signe pour cent.
        drawRoundRect(Color(0xFFFFE066), Offset(-half * 0.74f, -half * 0.5f), Size(half * 1.48f, half * 1.0f), CornerRadius(5f))
        cashierBarcode(-half * 0.64f, -half * 0.34f, half * 0.86f, half * 0.6f, item.kind * 3 + item.price, CashierLook.Red)
        val badge = Offset(half * 0.42f, half * 0.02f)
        drawCircle(CashierLook.Red, half * 0.3f, badge)
        drawCircle(Color.White, 3.2f, Offset(badge.x - 6f, badge.y - 6f))
        drawCircle(Color.White, 3.2f, Offset(badge.x + 6f, badge.y + 6f))
        drawLine(Color.White, Offset(badge.x + 8f, badge.y - 9f), Offset(badge.x - 8f, badge.y + 9f), strokeWidth = 2.6f, cap = StrokeCap.Round)
    }
    drawRect(ink.fade(0.45f), Offset(-half * 0.5f, half * 0.4f), Size(half * 1.0f, 3f))
}

/** Une face sans code : des lignes de texte (composition, conseils) dont la longueur dépend de la face. */
private fun DrawScope.cashierTextFace(face: Int, half: Float) {
    val ink = CashierLook.Ink
    drawRect(ink.fade(0.35f), Offset(-half * 0.7f, -half * 0.7f), Size(half * 0.9f, 6f))
    for (i in 0 until 5) {
        val length = half * (1.3f - 0.16f * ((i * 3 + face) % 4))
        drawRect(ink.fade(0.22f), Offset(-half * 0.7f, -half * 0.42f + i * half * 0.24f), Size(length, 5f))
    }
    if (face == 2) {
        drawRect(ink.fade(0.5f), Offset(half * 0.25f, half * 0.32f), Size(half * 0.45f, half * 0.38f), style = Stroke(width = 2f))
    }
}

/** Une face de l'article, à l'origine, dans un carré de côté 2 x [half] : le dessin du produit, un code-barres, un piège ou du texte. */
private fun DrawScope.cashierFaceContent(item: CashierItem, face: Int, half: Float) {
    val size = half * 2f
    drawRoundRect(Color(CASHIER_KIND_TINT[item.kind]), Offset(-half, -half), Size(size, size), CornerRadius(10f))
    when {
        face == item.codeFace -> cashierBarcodeFace(item, half)
        face == item.decoyFace -> cashierDecoyFace(item, half)
        face == 0 -> cashierItemShape(item.kind, 0f, 0f, half * 0.78f)
        else -> cashierTextFace(face, half)
    }
    drawRoundRect(CashierLook.Ink, Offset(-half, -half), Size(size, size), CornerRadius(10f), style = Stroke(width = 3f))
}

// ------------------------------------------------------------------------------------------------------------ les personnages

/** Une personne debout dont les pieds sont en ([x], [feet]), à l'échelle [scale] ; [mood] : 1 content, -1 mécontent, 0 neutre ; [cashier] : l'uniforme rouge de la caissière. */
private fun DrawScope.cashierPerson(x: Float, feet: Float, scale: Float, look: Int, mood: Int, cashier: Boolean) {
    val ink = CashierLook.Ink
    val coats = intArrayOf(0xFF4A7BB7.toInt(), 0xFF9B4F8E.toInt(), 0xFFB9783A.toInt(), 0xFF4F9A6B.toInt())
    val hairs = intArrayOf(0xFF3A2A1E.toInt(), 0xFFD9B04A.toInt(), 0xFF8A8A8A.toInt(), 0xFF1E1E22.toInt())
    val index = look.coerceIn(0, 3)
    val coat = if (cashier) CashierLook.Red else Color(coats[index])
    val hair = if (cashier) Color(0xFF5A3A22) else Color(hairs[index])
    withTransform({
        translate(x, feet)
        scale(scale, scale, Offset.Zero)
    }) {
        drawOval(Color.Black.copy(alpha = 0.2f), Offset(-30f, -7f), Size(60f, 12f))
        // Les jambes, le buste et les bras.
        drawRoundRect(Color(0xFF30343C), Offset(-16f, -28f), Size(12f, 28f), CornerRadius(3f))
        drawRoundRect(Color(0xFF30343C), Offset(4f, -28f), Size(12f, 28f), CornerRadius(3f))
        drawRoundRect(coat, Offset(-24f, -80f), Size(48f, 56f), CornerRadius(12f))
        drawRoundRect(ink, Offset(-24f, -80f), Size(48f, 56f), CornerRadius(12f), style = Stroke(width = 2.4f))
        drawRoundRect(coat, Offset(-33f, -76f), Size(11f, 40f), CornerRadius(5f))
        drawRoundRect(coat, Offset(22f, -76f), Size(11f, 40f), CornerRadius(5f))
        if (cashier) {
            // Le badge blanc sur la poitrine.
            drawRoundRect(Color.White, Offset(6f, -66f), Size(14f, 9f), CornerRadius(2f))
        }
        // La tête, les cheveux ou le chapeau, les lunettes.
        val headY = -98f
        if (cashier) {
            // Les cheveux longs tombent derrière la tête.
            drawRoundRect(hair, Offset(-22f, headY - 6f), Size(44f, 40f), CornerRadius(14f))
        }
        drawCircle(CashierLook.Skin, 19f, Offset(0f, headY))
        drawArc(hair, 180f, 180f, true, Offset(-19f, headY - 19f), Size(38f, 30f))
        if (!cashier && look == 2) {
            drawRoundRect(Color(0xFF7A5B2E), Offset(-24f, headY - 24f), Size(48f, 8f), CornerRadius(3f))
            drawRoundRect(Color(0xFF7A5B2E), Offset(-14f, headY - 36f), Size(28f, 14f), CornerRadius(4f))
        }
        drawCircle(ink, 19f, Offset(0f, headY), style = Stroke(width = 2.4f))
        drawCircle(ink, 2.2f, Offset(-7f, headY - 2f))
        drawCircle(ink, 2.2f, Offset(7f, headY - 2f))
        if (!cashier && look == 3) {
            drawCircle(ink, 5.5f, Offset(-7f, headY - 2f), style = Stroke(width = 1.8f))
            drawCircle(ink, 5.5f, Offset(7f, headY - 2f), style = Stroke(width = 1.8f))
        }
        when {
            mood > 0 -> drawArc(ink, 20f, 140f, false, Offset(-8f, headY + 2f), Size(16f, 12f), style = Stroke(width = 2.2f, cap = StrokeCap.Round))
            mood < 0 -> drawArc(ink, 200f, 140f, false, Offset(-8f, headY + 8f), Size(16f, 12f), style = Stroke(width = 2.2f, cap = StrokeCap.Round))
            else -> drawLine(ink, Offset(-6f, headY + 10f), Offset(6f, headY + 10f), strokeWidth = 2.2f, cap = StrokeCap.Round)
        }
    }
}

// ------------------------------------------------------------------------------------------------------------ le décor

/** Le décor de l'écran, qui ne change jamais : le mur du magasin avec son enseigne, et le sol carrelé. */
internal fun DrawScope.drawCashierScene(labels: CashierLabels, measurer: TextMeasurer) {
    val w = Cashier.WIDTH
    val h = Cashier.HEIGHT
    val ink = CashierLook.Ink
    // Le mur, avec des carreaux discrets.
    drawRect(
        brush = Brush.verticalGradient(0f to Color(0xFFF6EFE0), 1f to Color(0xFFE5D9C0), startY = 0f, endY = CashierLook.WALL_END),
        size = Size(w, CashierLook.WALL_END)
    )
    var x = 0f
    while (x < w) {
        drawLine(ink.fade(0.06f), Offset(x, 0f), Offset(x, CashierLook.WALL_END), strokeWidth = 1.5f)
        x += 48f
    }
    var y = 0f
    while (y < CashierLook.WALL_END) {
        drawLine(ink.fade(0.06f), Offset(0f, y), Offset(w, y), strokeWidth = 1.5f)
        y += 36f
    }
    // L'enseigne au milieu du mur, avec le voyant vert de la caisse ouverte.
    drawRoundRect(Color.White, Offset(w / 2f - 80f, 10f), Size(160f, 42f), CornerRadius(8f))
    drawRoundRect(ink, Offset(w / 2f - 80f, 10f), Size(160f, 42f), CornerRadius(8f), style = Stroke(width = 3f))
    drawRect(CashierLook.Red, Offset(w / 2f - 80f, 44f), Size(160f, 4f))
    cashierWord(measurer, labels.sign, w / 2f, 27f, 24f, CashierLook.Red, italic = true, spacing = 4f)
    drawCircle(CashierLook.Green, 8f, Offset(w / 2f - 104f, 31f))
    drawCircle(ink, 8f, Offset(w / 2f - 104f, 31f), style = Stroke(width = 2.4f))

    // Le sol carrelé, avec une plinthe au pied du mur.
    drawRect(
        brush = Brush.verticalGradient(0f to Color(0xFFD4CFC4), 1f to Color(0xFFB5AEA1), startY = CashierLook.WALL_END, endY = h),
        topLeft = Offset(0f, CashierLook.WALL_END),
        size = Size(w, h - CashierLook.WALL_END)
    )
    var fx = 0f
    while (fx < w) {
        drawLine(ink.fade(0.08f), Offset(fx, CashierLook.WALL_END), Offset(fx, h), strokeWidth = 1.5f)
        fx += 60f
    }
    var fy = CashierLook.WALL_END + 30f
    while (fy < h) {
        drawLine(ink.fade(0.08f), Offset(0f, fy), Offset(w, fy), strokeWidth = 1.5f)
        fy += 30f
    }
    drawRect(Color(0xFF8E9AA0), Offset(0f, CashierLook.WALL_END - 6f), Size(w, 6f))
    drawRect(ink, Offset(0f, CashierLook.WALL_END - 6f), Size(w, 2f))
}

// ------------------------------------------------------------------------------------------------------------ ce qui bouge

/** Un poids en kilos avec deux décimales (« 0.45 kg »). */
private fun cashierWeightText(grams: Int, kg: String): String =
    "${grams / 1000}.${(grams % 1000 / 10).toString().padStart(2, '0')} $kg"

/** Un cadre blanc aux coins arrondis, comme les panneaux de l'écran. */
private fun DrawScope.cashierCard(x: Float, y: Float, w: Float, h: Float) {
    drawRoundRect(Color.White.fade(0.96f), Offset(x, y), Size(w, h), CornerRadius(12f))
    drawRoundRect(CashierLook.Ink, Offset(x, y), Size(w, h), CornerRadius(12f), style = Stroke(width = 3f))
}

/** Le comptoir : le tapis qui amène les articles, le scanner (une vitre sombre et sa ligne rouge), la caisse enregistreuse avec la note, et la face du comptoir. */
private fun DrawScope.cashierCounter(game: CashierGame, labels: CashierLabels, measurer: TextMeasurer) {
    val ink = CashierLook.Ink
    val w = Cashier.WIDTH
    // Le tapis : une bande qui défile vers le scanner.
    val beltW = CashierLook.SCANNER_X - CashierLook.BELT_X0
    drawRect(CashierLook.Belt, Offset(CashierLook.BELT_X0, CashierLook.BELT_Y), Size(beltW, CashierLook.BELT_H))
    var sx = CashierLook.BELT_X0 + (game.clockMs * 0.04f) % 30f
    while (sx < CashierLook.SCANNER_X - 10f) {
        drawLine(Color.White.fade(0.25f), Offset(sx, CashierLook.BELT_Y + 4f), Offset(sx + 9f, CashierLook.BELT_Y + CashierLook.BELT_H - 4f), strokeWidth = 2f)
        sx += 30f
    }
    drawRect(ink, Offset(CashierLook.BELT_X0, CashierLook.BELT_Y), Size(beltW, CashierLook.BELT_H), style = Stroke(width = 2.5f))
    // Le scanner : une vitre sombre avec la ligne du laser.
    val glassX = CashierLook.SCANNER_X
    drawRect(Color(0xFF1B2229), Offset(glassX, CashierLook.BELT_Y), Size(CashierLook.SCANNER_W, CashierLook.BELT_H))
    val pulse = 0.55f + 0.35f * sin(game.clockMs / 110f)
    drawRect(CashierLook.Red.fade(pulse), Offset(glassX + 6f, CashierLook.BELT_Y + CashierLook.BELT_H / 2f - 1.5f), Size(CashierLook.SCANNER_W - 12f, 3f))
    drawRect(ink, Offset(glassX, CashierLook.BELT_Y), Size(CashierLook.SCANNER_W, CashierLook.BELT_H), style = Stroke(width = 2.5f))
    // La face du comptoir, sous le tapis.
    val frontY = CashierLook.BELT_Y + CashierLook.BELT_H
    drawRect(CashierLook.Counter, Offset(CashierLook.BELT_X0 - 10f, frontY), Size(w - CashierLook.BELT_X0 + 10f, Cashier.HEIGHT - frontY))
    drawRect(Color.White.fade(0.2f), Offset(CashierLook.BELT_X0 - 10f, frontY), Size(w - CashierLook.BELT_X0 + 10f, 5f))
    drawRect(ink, Offset(CashierLook.BELT_X0 - 10f, frontY), Size(w - CashierLook.BELT_X0 + 10f, 2.5f))
    // La caisse enregistreuse, avec son petit écran qui affiche la note.
    val rx = CashierLook.REGISTER_X
    val ry = CashierLook.REGISTER_Y
    drawRoundRect(Color(0xFFC9D1D6), Offset(rx, ry), Size(CashierLook.REGISTER_W, CashierLook.REGISTER_H), CornerRadius(8f))
    drawRoundRect(ink, Offset(rx, ry), Size(CashierLook.REGISTER_W, CashierLook.REGISTER_H), CornerRadius(8f), style = Stroke(width = 3f))
    drawRoundRect(Color(0xFF0F2A1C), Offset(rx + 10f, ry + 8f), Size(CashierLook.REGISTER_W - 20f, 26f), CornerRadius(4f))
    cashierWord(measurer, "${game.total} ${labels.euro}", rx + CashierLook.REGISTER_W - 16f, ry + 21f, 18f, Color(0xFF7CF0A4), align = 1)
    for (k in 0 until 5) {
        drawRect(ink.fade(0.55f), Offset(rx + 12f + k * 19f, ry + 40f), Size(14f, 8f))
    }
}

/** Les articles qui attendent sur le tapis, de plus en plus loin du scanner ; ils glissent d'un cran quand un article vient d'être passé. */
private fun DrawScope.cashierBeltItems(game: CashierGame) {
    if (game.step == CASHIER_STEP_ARRIVE || game.step == CASHIER_STEP_RESULT) return
    val slide = game.slideMs / Cashier.SLIDE_MS
    val first = game.index + 1
    for (i in first until game.cart.size) {
        val k = i - first
        val x = CashierLook.SCANNER_X - CashierLook.BELT_STEP * (k + 1) - CashierLook.BELT_STEP * slide
        if (x < CashierLook.BELT_X0 + 18f) continue
        cashierItemShape(game.cart[i].kind, x, CashierLook.BELT_Y - 17f, 17f)
    }
}

/** La flèche d'un côté du cadre du scanner ; [direction] : -1 vers la gauche, 1 vers la droite. */
private fun DrawScope.cashierSideArrow(x: Float, y: Float, direction: Int) {
    val tri = Path().apply {
        moveTo(x + direction * 8f, y)
        lineTo(x - direction * 6f, y - 12f)
        lineTo(x - direction * 6f, y + 12f)
        close()
    }
    drawPath(tri, CashierLook.Ink.fade(0.5f))
}

/** Le cadre du scanner quand un article à code-barres est devant la vitre : sa face, tournée avec les flèches, sous la ligne rouge du laser. */
private fun DrawScope.cashierScanView(game: CashierGame, item: CashierItem) {
    val cx = CashierLook.FACE_CX - game.slideMs / Cashier.SLIDE_MS * 60f
    val cy = CashierLook.FACE_CY
    val half = CashierLook.FACE_HALF
    // L'animation du tour : la face qui s'en va se resserre, puis la nouvelle s'élargit.
    val progress = 1f - game.turnMs / Cashier.TURN_MS
    val face: Int
    val squeeze: Float
    if (progress < 0.5f) {
        face = game.prevFacing
        squeeze = 1f - 2f * progress
    } else {
        face = game.facing
        squeeze = 2f * progress - 1f
    }
    withTransform({
        translate(cx, cy)
        scale(squeeze.coerceAtLeast(0.02f), 1f, Offset.Zero)
    }) {
        cashierFaceContent(item, face, half)
    }
    // Les quatre coins rouges du viseur et la ligne du laser, qui s'allume quand le bip vient de sonner.
    val fx = CashierLook.FACE_CX
    val bracket = half + 8f
    for (sx in floatArrayOf(-1f, 1f)) {
        for (sy in floatArrayOf(-1f, 1f)) {
            val px = fx + sx * bracket
            val py = cy + sy * bracket
            drawLine(CashierLook.Red, Offset(px, py), Offset(px - sx * 14f, py), strokeWidth = 3f, cap = StrokeCap.Round)
            drawLine(CashierLook.Red, Offset(px, py), Offset(px, py - sy * 14f), strokeWidth = 3f, cap = StrokeCap.Round)
        }
    }
    val beeping = game.popCode == CASHIER_POP_BEEP && game.popMs > Cashier.POP_MS - 220f
    drawRect(CashierLook.Red.fade(if (beeping) 0.9f else 0.35f), Offset(fx - bracket, cy - 1.5f), Size(bracket * 2f, 3f))
    if (beeping) {
        drawRoundRect(CashierLook.Green.fade(0.3f), Offset(fx - half, cy - half), Size(half * 2f, half * 2f), CornerRadius(10f))
    }
    // Les flèches de rotation, et une pastille par face (celle de devant est cerclée) : celle de la face tournée vers nous est rouge.
    cashierSideArrow(CashierLook.PANEL_X + 18f, cy, -1)
    cashierSideArrow(CashierLook.PANEL_X + CashierLook.PANEL_W - 18f, cy, 1)
    val dotsY = CashierLook.PANEL_Y + CashierLook.PANEL_H - 16f
    for (i in 0 until Cashier.FACES) {
        val dx = fx + (i - 1.5f) * 22f
        val here = i == game.facing
        drawCircle(if (here) CashierLook.Red else CashierLook.Ink.fade(0.25f), if (here) 6f else 4f, Offset(dx, dotsY))
        if (i == 0) drawCircle(CashierLook.Ink.fade(0.6f), 8f, Offset(dx, dotsY), style = Stroke(width = 1.8f))
    }
}

/** Le cadre du scanner quand un article sans code-barres est sur la balance : son dessin et, pour un fruit ou un légume, son poids. */
private fun DrawScope.cashierPickView(game: CashierGame, item: CashierItem, labels: CashierLabels, measurer: TextMeasurer) {
    val ink = CashierLook.Ink
    val cx = CashierLook.FACE_CX - game.slideMs / Cashier.SLIDE_MS * 60f
    // La balance : un plateau en inox sous l'article.
    drawRoundRect(CashierLook.Metal, Offset(cx - 74f, CashierLook.FACE_CY + 56f), Size(148f, 16f), CornerRadius(7f))
    drawRoundRect(ink, Offset(cx - 74f, CashierLook.FACE_CY + 56f), Size(148f, 16f), CornerRadius(7f), style = Stroke(width = 2.4f))
    cashierItemShape(item.kind, cx, CashierLook.FACE_CY + 2f, 56f)
    if (item.weightG > 0) {
        cashierWord(
            measurer,
            cashierWeightText(item.weightG, labels.kg),
            CashierLook.PANEL_X + CashierLook.PANEL_W - 12f,
            CashierLook.PANEL_Y + 16f,
            16f,
            ink.fade(0.75f),
            align = 1
        )
    }
}

/** Un billet de [value] euros (ou une pièce pour 1 et 2), centré en ([cx], [cy]), pour la monnaie à rendre. */
private fun DrawScope.cashierMoney(value: Int, cx: Float, cy: Float, half: Float, measurer: TextMeasurer) {
    val ink = CashierLook.Ink
    if (value <= 2) {
        val color = if (value == 1) Color(0xFFD9A441) else Color(0xFFE6C15A)
        drawCircle(color, half, Offset(cx, cy))
        drawCircle(ink, half, Offset(cx, cy), style = Stroke(width = 2.4f))
    } else {
        val color = when (value) {
            5 -> Color(0xFFB9C9B8)
            10 -> Color(0xFFE9A59B)
            else -> Color(0xFF9CC0E8)
        }
        drawRoundRect(color, Offset(cx - half * 1.1f, cy - half * 0.75f), Size(half * 2.2f, half * 1.5f), CornerRadius(4f))
        drawRoundRect(ink, Offset(cx - half * 1.1f, cy - half * 0.75f), Size(half * 2.2f, half * 1.5f), CornerRadius(4f), style = Stroke(width = 2.4f))
    }
    cashierWord(measurer, value.toString(), cx, cy, half * 0.95f, ink)
}

/** Le cadre du scanner à l'étape du paiement : la note, et en espèces le billet reçu et la monnaie rendue jusque-là. */
private fun DrawScope.cashierPayView(game: CashierGame, labels: CashierLabels, measurer: TextMeasurer) {
    val ink = CashierLook.Ink
    val left = CashierLook.PANEL_X + 16f
    val right = CashierLook.PANEL_X + CashierLook.PANEL_W - 16f
    val top = CashierLook.PANEL_Y
    if (game.payCard) {
        cashierWord(measurer, labels.total, CashierLook.FACE_CX, top + 24f, 16f, ink.fade(0.6f))
        cashierWord(measurer, "${game.total} ${labels.euro}", CashierLook.FACE_CX, top + 62f, 40f, ink)
        // La carte bancaire, avec sa puce.
        val cx = CashierLook.FACE_CX
        val cy = top + 118f
        drawRoundRect(Color(0xFF2B6CB0), Offset(cx - 38f, cy - 24f), Size(76f, 48f), CornerRadius(7f))
        drawRoundRect(ink, Offset(cx - 38f, cy - 24f), Size(76f, 48f), CornerRadius(7f), style = Stroke(width = 2.4f))
        drawRect(Color.White.fade(0.8f), Offset(cx - 38f, cy - 14f), Size(76f, 8f))
        drawRoundRect(Color(0xFFE6C15A), Offset(cx - 28f, cy + 2f), Size(16f, 12f), CornerRadius(3f))
        return
    }
    cashierWord(measurer, labels.total, left, top + 26f, 15f, ink.fade(0.6f), align = -1)
    cashierWord(measurer, "${game.total} ${labels.euro}", right, top + 26f, 26f, ink, align = 1)
    cashierWord(measurer, labels.received, left, top + 66f, 15f, ink.fade(0.6f), align = -1)
    cashierWord(measurer, "${game.given} ${labels.euro}", right, top + 66f, 26f, ink, align = 1)
    drawLine(ink.fade(0.4f), Offset(left, top + 92f), Offset(right, top + 92f), strokeWidth = 2.4f)
    cashierWord(measurer, labels.change, left, top + 128f, 15f, ink.fade(0.6f), align = -1)
    cashierWord(measurer, "${game.traySum} ${labels.euro}", right, top + 128f, 38f, CashierLook.Blue, align = 1)
}

/** Le grand cadre du milieu, selon l'étape. */
private fun DrawScope.cashierPanel(game: CashierGame, labels: CashierLabels, measurer: TextMeasurer) {
    cashierCard(CashierLook.PANEL_X, CashierLook.PANEL_Y, CashierLook.PANEL_W, CashierLook.PANEL_H)
    val item = game.currentItem
    when (game.step) {
        CASHIER_STEP_SCAN, CASHIER_STEP_AGE -> if (item != null) cashierScanView(game, item)
        CASHIER_STEP_PICK -> if (item != null) cashierPickView(game, item, labels, measurer)
        CASHIER_STEP_PAY -> cashierPayView(game, labels, measurer)
        CASHIER_STEP_RESULT -> {
            if (game.resultHappy) {
                // Une coche verte : le client a payé.
                val c = Offset(CashierLook.FACE_CX, CashierLook.FACE_CY)
                drawCircle(CashierLook.Green, 44f, c)
                drawCircle(CashierLook.Ink, 44f, c, style = Stroke(width = 3f))
                val tick = Path().apply {
                    moveTo(c.x - 20f, c.y + 2f); lineTo(c.x - 6f, c.y + 16f); lineTo(c.x + 22f, c.y - 14f)
                }
                drawPath(tick, Color.White, style = Stroke(width = 9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        else -> {}
    }
}

/** Le cadre de droite : la note et les articles du chariot, les quatre images à choisir, la pièce d'identité, le terminal de carte ou la monnaie. */
private fun DrawScope.cashierSideCard(game: CashierGame, labels: CashierLabels, measurer: TextMeasurer) {
    val ink = CashierLook.Ink
    val x = CashierLook.SIDE_X
    val y = CashierLook.SIDE_Y
    val w = CashierLook.SIDE_W
    val pulse = 0.5f + 0.5f * sin(game.clockMs / 140f)
    cashierCard(x, y, w, CashierLook.SIDE_H)
    when (game.step) {
        CASHIER_STEP_PICK -> {
            // Les quatre images, deux par ligne : celle qui est cernée de rouge sera validée par A.
            for (i in 0 until Cashier.PICK_CHOICES) {
                val tx = x + 10f + (i % 2) * 128f
                val ty = y + 10f + (i / 2) * 76f
                val selected = i == game.pickCursor
                drawRoundRect(if (selected) CashierLook.Red.fade(0.1f + 0.12f * pulse) else Color(0xFFF1F3F4), Offset(tx, ty), Size(118f, 68f), CornerRadius(10f))
                drawRoundRect(if (selected) CashierLook.Red else ink.fade(0.35f), Offset(tx, ty), Size(118f, 68f), CornerRadius(10f), style = Stroke(width = if (selected) 4f else 2f))
                cashierItemShape(game.choices[i], tx + 59f, ty + 34f, 28f)
            }
        }
        CASHIER_STEP_AGE -> {
            // La pièce d'identité : une photo, des lignes, et « 18+ » en rouge ; la barre se vide.
            val cx = x + w / 2f
            drawRoundRect(Color(0xFFD9ECF7), Offset(cx - 100f, y + 16f), Size(200f, 96f), CornerRadius(10f))
            drawRoundRect(ink, Offset(cx - 100f, y + 16f), Size(200f, 96f), CornerRadius(10f), style = Stroke(width = 3f))
            drawCircle(CashierLook.Skin, 16f, Offset(cx - 66f, y + 54f))
            drawRoundRect(Color(0xFF4A7BB7), Offset(cx - 86f, y + 74f), Size(40f, 28f), CornerRadius(12f))
            for (k in 0 until 3) {
                drawRect(ink.fade(0.3f), Offset(cx - 34f, y + 36f + k * 14f), Size(70f - k * 12f, 5f))
            }
            drawCircle(CashierLook.Red, 20f, Offset(cx + 62f, y + 82f))
            cashierWord(measurer, "18+", cx + 62f, y + 82f, 17f, Color.White)
            cashierWord(measurer, labels.idCheck, cx, y + 130f, 14f, CashierLook.Red)
            val share = (game.ageMs / Cashier.AGE_MS).coerceIn(0f, 1f)
            drawRoundRect(ink.fade(0.15f), Offset(x + 20f, y + 146f), Size(w - 40f, 6f), CornerRadius(3f))
            drawRoundRect(CashierLook.Red, Offset(x + 20f, y + 146f), Size((w - 40f) * share, 6f), CornerRadius(3f))
        }
        CASHIER_STEP_PAY -> if (game.payCard) {
            // Le terminal de carte : une piste, la zone verte à viser et le curseur qui va et vient.
            cashierWord(measurer, labels.card, x + w / 2f, y + 20f, 16f, ink.fade(0.6f))
            drawRoundRect(Color(0xFF2A3038), Offset(x + 10f, y + 40f), Size(w - 20f, 100f), CornerRadius(12f))
            drawRoundRect(ink, Offset(x + 10f, y + 40f), Size(w - 20f, 100f), CornerRadius(12f), style = Stroke(width = 3f))
            val x0 = x + 28f
            val x1 = x + w - 28f
            val ty = y + 78f
            drawRoundRect(Color(0xFFD4DADD), Offset(x0, ty), Size(x1 - x0, 28f), CornerRadius(6f))
            val half = game.termZoneW / 2f
            val from = (game.termCenter - half).coerceAtLeast(0f)
            val to = (game.termCenter + half).coerceAtMost(1f)
            drawRect(CashierLook.Green, Offset(x0 + from * (x1 - x0), ty), Size((to - from) * (x1 - x0), 28f))
            val perfectHalf = half * Cashier.TERM_PERFECT
            drawRect(
                CashierLook.GreenDark,
                Offset(x0 + (game.termCenter - perfectHalf) * (x1 - x0), ty),
                Size(perfectHalf * 2f * (x1 - x0), 28f)
            )
            drawRoundRect(ink, Offset(x0, ty), Size(x1 - x0, 28f), CornerRadius(6f), style = Stroke(width = 2.4f))
            val px = x0 + game.termPos * (x1 - x0)
            drawLine(CashierLook.Red, Offset(px, ty - 8f), Offset(px, ty + 36f), strokeWidth = 4f, cap = StrokeCap.Round)
        } else {
            // La monnaie : ce qui est déjà rendu, puis les cinq pièces et billets à choisir.
            cashierWord(measurer, labels.cash, x + w / 2f, y + 18f, 16f, ink.fade(0.6f))
            for (i in 0 until min(game.tray.size, 14)) {
                val rowY = y + 50f + (i / 7) * 34f
                cashierMoney(game.tray[i], x + 24f + (i % 7) * 36f, rowY, 14f, measurer)
            }
            for (i in Cashier.DENOMS.indices) {
                val tx = x + 12f + i * 51f
                val ty = y + 110f
                val selected = i == game.denomCursor
                drawRoundRect(if (selected) CashierLook.Red.fade(0.1f + 0.12f * pulse) else Color(0xFFF1F3F4), Offset(tx, ty), Size(46f, 44f), CornerRadius(8f))
                drawRoundRect(if (selected) CashierLook.Red else ink.fade(0.35f), Offset(tx, ty), Size(46f, 44f), CornerRadius(8f), style = Stroke(width = if (selected) 4f else 2f))
                cashierMoney(Cashier.DENOMS[i], tx + 23f, ty + 22f, 15f, measurer)
            }
        }
        else -> {
            // La note : le total du chariot, et un rond par article (plein quand il est passé).
            cashierWord(measurer, labels.total, x + 18f, y + 24f, 15f, ink.fade(0.6f), align = -1)
            cashierWord(measurer, "${game.total} ${labels.euro}", x + 18f, y + 62f, 40f, ink, align = -1)
            for (i in game.cart.indices) {
                val dx = x + 28f + i * 26f
                val dy = y + 118f
                when {
                    i < game.index -> drawCircle(CashierLook.GreenDark, 9f, Offset(dx, dy))
                    i == game.index && game.step != CASHIER_STEP_ARRIVE -> {
                        drawCircle(CashierLook.Red.fade(0.2f + 0.2f * pulse), 9f, Offset(dx, dy))
                        drawCircle(CashierLook.Red, 9f, Offset(dx, dy), style = Stroke(width = 3f))
                    }
                    else -> drawCircle(ink.fade(0.35f), 9f, Offset(dx, dy), style = Stroke(width = 2.4f))
                }
            }
        }
    }
}

/** Le message du moment (« BIP ! », « FAUX CODE ! »…) et les points gagnés ou perdus, sous le cadre du scanner. */
private fun DrawScope.cashierPopup(game: CashierGame, labels: CashierLabels, measurer: TextMeasurer) {
    if (game.popCode == CASHIER_POP_NONE || game.popMs <= 0f) return
    val text = labels.pops.getOrElse(game.popCode) { "" }
    if (text.isEmpty()) return
    val alpha = min(1f, game.popMs / 300f)
    val rise = (1f - game.popMs / Cashier.POP_MS) * 10f
    val good = game.popPoints >= 0
    val color = (if (good) CashierLook.GreenDark else CashierLook.Red).fade(alpha)
    cashierWord(measurer, text, CashierLook.FACE_CX, 272f - rise, 28f, color, italic = true)
    val points = game.popPoints
    cashierWord(measurer, if (points >= 0) "+$points" else points.toString(), CashierLook.FACE_CX, 297f - rise, 20f, color)
}

/** Les compteurs du haut de l'écran : le temps à gauche, le score à droite, et le nombre de clients servis sous l'enseigne. */
private fun DrawScope.cashierHud(game: CashierGame, labels: CashierLabels, measurer: TextMeasurer) {
    val ink = CashierLook.Ink
    val urgent = !game.over && game.timeLeftMs <= 10_000f
    cashierWord(measurer, labels.time, 16f, 24f, 14f, ink.fade(0.6f), align = -1)
    cashierWord(measurer, fishClockText(game.timeLeftMs), 16f, 62f, 46f, if (urgent) CashierLook.Red else ink, align = -1)
    cashierWord(measurer, labels.score, Cashier.WIDTH - 16f, 24f, 14f, ink.fade(0.6f), align = 1)
    cashierWord(measurer, game.score.toString(), Cashier.WIDTH - 16f, 62f, 46f, ink, align = 1)
    cashierWord(measurer, "${labels.customers} ${game.customersServed}", Cashier.WIDTH / 2f, 66f, 14f, ink.fade(0.6f))
}

/** Tout ce qui change pendant la partie, dans le repère de 720 x 405 de l'écran. */
internal fun DrawScope.drawCashierLive(game: CashierGame, labels: CashierLabels, measurer: TextMeasurer) {
    val ink = CashierLook.Ink
    cashierHud(game, labels, measurer)

    // La file d'attente au fond : un petit personnage par client qui attend.
    for (k in game.waiting - 1 downTo 0) {
        cashierPerson(30f + k * 38f, 246f, 0.6f, (game.customerLook + k + 1) % 4, 0, cashier = false)
    }

    // Le client de la caisse : il arrive de la gauche, et repart content ou furieux (son humeur se voit sur son visage).
    val result = game.step == CASHIER_STEP_RESULT
    val arrive = if (game.step == CASHIER_STEP_ARRIVE) (game.arriveMs / Cashier.ARRIVE_MS).coerceIn(0f, 1f) else 0f
    val leaving = if (result) (1f - game.resultMs / Cashier.RESULT_MS).coerceIn(0f, 1f) else 0f
    val mood = when {
        !result -> 0
        game.resultHappy -> 1
        else -> -1
    }
    val customerX = CashierLook.CUSTOMER_X - arrive * 150f - leaving * 150f
    cashierPerson(customerX, CashierLook.CUSTOMER_FEET, 1f, game.customerLook, mood, cashier = false)
    if (!result && game.step != CASHIER_STEP_ARRIVE) {
        // La patience du client : la barre passe du vert au rouge en se vidant.
        val share = (game.patienceMs / game.patienceMaxMs).coerceIn(0f, 1f)
        val bar = if (share > 0.5f) CashierLook.Green else if (share > 0.25f) CashierLook.Amber else CashierLook.Red
        drawRoundRect(ink.fade(0.15f), Offset(32f, 268f), Size(80f, 8f), CornerRadius(4f))
        drawRoundRect(bar, Offset(32f, 268f), Size(80f * share, 8f), CornerRadius(4f))
        drawRoundRect(ink, Offset(32f, 268f), Size(80f, 8f), CornerRadius(4f), style = Stroke(width = 1.6f))
    }

    // La caissière derrière le comptoir : elle saute de joie quand un client a payé.
    val hop = if (result && game.resultHappy) abs(sin(game.resultMs / 110f)) * 6f else 0f
    val cashierMood = if (result) (if (game.resultHappy) 1 else -1) else 0
    cashierPerson(CashierLook.CASHIER_X, CashierLook.CASHIER_FEET - hop, CashierLook.CASHIER_SCALE, 0, cashierMood, cashier = true)

    // Le comptoir est devant elle, avec les articles qui attendent sur le tapis.
    cashierCounter(game, labels, measurer)
    cashierBeltItems(game)

    cashierPanel(game, labels, measurer)
    cashierSideCard(game, labels, measurer)
    cashierPopup(game, labels, measurer)

    val shade = min(0.22f, game.blockedMs / Cashier.BLOCKED_MS * 0.22f)
    if (shade > 0f) drawRect(CashierLook.Red.fade(shade), size = Size(Cashier.WIDTH, Cashier.HEIGHT))
}
