package com.aventure.messcores

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Les dessins de l'écran de Boulangerie, dans le repère de l'écran (720 x 405), le même que celui de Drive. La scène se lit de
 * haut en bas : au mur du fond, les six plaques où est accroché le temps de cuisson de chaque produit (réglable avec les
 * flèches) ; derrière, le boulanger (le personnage de Drive, [DriveSprites]) et les trois fours ; puis la table où attendent les
 * plateaux de pâtons crus ; au premier plan, l'étalage où les clients se servent, et le sol où l'on voit les clients arriver.
 * Le décor qui ne bouge pas (mur, sol, cadre de l'étalage) se dessine une fois avec [drawBakeryScene] ; tout ce qui bouge avec
 * [drawBakeryLive].
 */
internal class BakeryLabels(
    val sign: String,
    val misses: String,
    val revenue: String,
    val opening: String,
    val no: String,
    val second: String,
    /** Le modèle d'un prix en euros, avec un seul nombre entier (par exemple « %1$d € »). */
    val money: String,
    /** Le nom de chaque produit, dans l'ordre des colonnes. */
    val products: List<String>
)

private object BakeLook {
    val Ink = Color(0xFF2A1E14)
    val Red = Color(0xFFE2001A)
    val Green = Color(0xFF2E9E4F)
    val GreenDark = Color(0xFF176B33)
    val Amber = Color(0xFFF0B429)
    val Wood = Color(0xFF9A6B3C)
    val WoodLight = Color(0xFFD7B27A)
    val WoodDark = Color(0xFF5E3F22)
    val Brick = Color(0xFFB5563A)
    val BrickDark = Color(0xFF7E3524)
    val Metal = Color(0xFFB9C0C4)
    val Skin = Color(0xFFD99B79)
    val Paper = Color(0xFFFBF3E0)

    // Les six colonnes (plaque, pâte, étalage) : même largeur et même pas que dans la Poissonnerie.
    const val GROUP_X0 = 18f
    const val GROUP_STEP = 116f
    const val GROUP_W = 104f

    // Le mur du fond : les plaques des temps de cuisson.
    const val PLAQUE_Y = 30f
    const val PLAQUE_H = 70f

    // Les fours, au milieu ; le boulanger à gauche.
    const val OVEN_Y = 120f
    const val OVEN_H = 96f
    const val OVEN_W = 146f
    val OVEN_X = floatArrayOf(118f, 288f, 458f)
    const val BAKER_X = 62f
    const val BAKER_FEET = 240f
    const val BAKER_SCALE = 1.1f

    // La table des pâtons crus.
    const val TABLE_Y = 222f
    const val TABLE_TOP_H = 30f
    const val TABLE_H = 42f

    // L'étalage au premier plan : le cadre, les deux planches, la bande des étiquettes, puis le sol.
    const val CASE_Y = 270f
    const val SHELF_ONE = 299f
    const val SHELF_TWO = 327f
    const val TAG_Y = 331f
    const val TAG_H = 17f
    const val FLOOR_Y = 352f

    // Les clients : la tête et les épaules dépassent en bas de l'écran.
    const val CUSTOMER_HEAD_Y = 374f
    const val CUSTOMER_SCALE = 0.9f
}

private fun Color.fade(a: Float): Color = copy(alpha = alpha * a)

private fun mix(from: Float, to: Float, t: Float): Float = from + (to - from) * t.coerceIn(0f, 1f)

private fun colX(column: Int): Float = BakeLook.GROUP_X0 + column * BakeLook.GROUP_STEP + BakeLook.GROUP_W / 2f

/** Un texte centré (ou aligné) en ([x], [y]), de [px] pixels de l'écran de hauteur de police ; [align] : -1 à gauche, 0 centré, 1 à droite. */
private fun DrawScope.bakeWord(
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

/** Un prix ou un chiffre d'affaires en euros, écrit avec le modèle de la langue (voir [BakeryLabels.money]). */
private fun BakeryLabels.price(euros: Int): String = String.format(Locale.ROOT, money, euros)

// ------------------------------------------------------------------------------------------------------------ les pains

private val RawDough = Color(0xFFF2E4C4)
private val PaleDough = Color(0xFFE8C27C)
private val PaleGold = Color(0xFFDDA550)
private val Golden = Color(0xFFCF8A2D)
private val DarkCrust = Color(0xFF7A471D)
private val BurntCrust = Color(0xFF2A1B12)

/**
 * La couleur d'un pain cuit pendant [ratio] fois son vrai temps de cuisson : pâte crue, puis pâle, puis doré (le bon moment), puis
 * de plus en plus foncé, puis noir. Les seuils sont ceux de [Bakery] (voir [Bakery.qualityAt]).
 */
internal fun bakeColor(ratio: Float): Color = when {
    ratio <= 0f -> RawDough
    ratio < Bakery.RAW_LIMIT -> lerp(RawDough, PaleDough, ratio / Bakery.RAW_LIMIT)
    ratio < Bakery.PALE_LIMIT -> lerp(PaleDough, PaleGold, (ratio - Bakery.RAW_LIMIT) / (Bakery.PALE_LIMIT - Bakery.RAW_LIMIT))
    ratio < 0.97f -> lerp(PaleGold, Golden, (ratio - Bakery.PALE_LIMIT) / (0.97f - Bakery.PALE_LIMIT))
    ratio <= Bakery.GOLD_LIMIT -> Golden
    ratio < 1.35f -> lerp(Golden, DarkCrust, (ratio - Bakery.GOLD_LIMIT) / (1.35f - Bakery.GOLD_LIMIT))
    ratio < Bakery.BURN_LIMIT -> lerp(DarkCrust, BurntCrust, (ratio - 1.35f) / (Bakery.BURN_LIMIT - 1.35f))
    else -> BurntCrust
}

private fun polar(angle: Float, radius: Float): Offset {
    val a = angle * PI.toFloat() / 180f
    return Offset(cos(a) * radius, sin(a) * radius)
}

/**
 * Un produit (0 baguette, 1 pain de campagne, 2 croissant, 3 pain au chocolat, 4 brioche, 5 chausson aux pommes) centré en
 * ([cx], [cy]), à l'échelle [s] : à l'échelle 1 il tient dans un carré de 30 unités ; [body] est la couleur de la croûte.
 */
private fun DrawScope.bakeShape(product: Int, cx: Float, cy: Float, s: Float, body: Color) {
    val ink = BakeLook.Ink
    val shade = lerp(body, Color.Black, 0.35f)
    withTransform({
        translate(cx, cy)
        scale(s, s, Offset.Zero)
    }) {
        when (product) {
            0 -> withTransform({ rotate(-35f, Offset.Zero) }) {
                // La baguette, en biais, avec ses coups de lame.
                drawRoundRect(body, Offset(-18f, -4.5f), Size(36f, 9f), CornerRadius(4.5f))
                for (k in -1..1) {
                    drawLine(shade, Offset(k * 9f - 2.5f, -3f), Offset(k * 9f + 2.5f, 3f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                }
                drawRoundRect(ink, Offset(-18f, -4.5f), Size(36f, 9f), CornerRadius(4.5f), style = Stroke(width = 1.6f))
            }
            1 -> {
                // Le pain de campagne : une boule farinée, marquée en croix.
                drawCircle(body, 12.5f, Offset.Zero)
                drawLine(Color.White.fade(0.6f), Offset(-6f, -6f), Offset(6f, 6f), strokeWidth = 2.2f, cap = StrokeCap.Round)
                drawLine(Color.White.fade(0.6f), Offset(6f, -6f), Offset(-6f, 6f), strokeWidth = 2.2f, cap = StrokeCap.Round)
                drawCircle(ink, 12.5f, Offset.Zero, style = Stroke(width = 1.6f))
            }
            2 -> {
                // Le croissant : un arc épais aux pointes arrondies, cranté.
                val topLeft = Offset(-13f, -8f)
                val box = Size(26f, 26f)
                drawArc(ink, 200f, 140f, false, topLeft, box, style = Stroke(width = 11.5f, cap = StrokeCap.Round))
                drawArc(body, 200f, 140f, false, topLeft, box, style = Stroke(width = 8.5f, cap = StrokeCap.Round))
                val center = Offset(0f, 5f)
                for (angle in floatArrayOf(232f, 270f, 308f)) {
                    drawLine(shade, center + polar(angle, 9f), center + polar(angle, 16.5f), strokeWidth = 1.6f, cap = StrokeCap.Round)
                }
            }
            3 -> {
                // Le pain au chocolat : un rectangle feuilleté avec deux barres de chocolat.
                drawRoundRect(body, Offset(-15f, -8f), Size(30f, 16f), CornerRadius(5f))
                drawRoundRect(Color(0xFF4B2A1B), Offset(-7f, -7f), Size(4f, 14f), CornerRadius(2f))
                drawRoundRect(Color(0xFF4B2A1B), Offset(3f, -7f), Size(4f, 14f), CornerRadius(2f))
                drawLine(shade, Offset(-13f, -4f), Offset(-10f, -4f), strokeWidth = 1.4f, cap = StrokeCap.Round)
                drawLine(shade, Offset(10f, 4f), Offset(13f, 4f), strokeWidth = 1.4f, cap = StrokeCap.Round)
                drawRoundRect(ink, Offset(-15f, -8f), Size(30f, 16f), CornerRadius(5f), style = Stroke(width = 1.6f))
            }
            4 -> {
                // La brioche à tête : une grosse boule surmontée d'une petite, avec ses sillons.
                drawCircle(body, 11.5f, Offset(0f, 3f))
                drawCircle(ink, 11.5f, Offset(0f, 3f), style = Stroke(width = 1.6f))
                drawCircle(body, 6f, Offset(0f, -10f))
                drawCircle(ink, 6f, Offset(0f, -10f), style = Stroke(width = 1.6f))
                drawArc(shade, 200f, 140f, false, Offset(-8f, -2f), Size(16f, 16f), style = Stroke(width = 1.4f, cap = StrokeCap.Round))
            }
            else -> {
                // Le chausson aux pommes : un demi-cercle feuilleté, bord crénelé, trois entailles.
                val half = Path()
                half.moveTo(-15f, 8f)
                half.cubicTo(-15f, -13f, 15f, -13f, 15f, 8f)
                half.close()
                drawPath(half, body)
                for (k in -1..1) {
                    drawLine(shade, Offset(k * 6f - 1.5f, -4f), Offset(k * 6f + 1.5f, 2f), strokeWidth = 1.6f, cap = StrokeCap.Round)
                }
                for (k in -2..2) {
                    drawLine(shade, Offset(k * 6f - 1f, 8f), Offset(k * 6f + 1f, 5f), strokeWidth = 1.3f, cap = StrokeCap.Round)
                }
                drawPath(half, ink, style = Stroke(width = 1.6f, join = StrokeJoin.Round))
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------------------ le décor

/** Le décor de l'écran, qui ne change jamais : le mur, l'enseigne, le sac de farine, le cadre de l'étalage et le sol. */
internal fun DrawScope.drawBakeryScene(labels: BakeryLabels, measurer: TextMeasurer) {
    val w = Bakery.WIDTH
    val h = Bakery.HEIGHT
    val ink = BakeLook.Ink
    // Le mur : un crépi crème, avec de grands carreaux.
    drawRect(
        brush = Brush.verticalGradient(0f to Color(0xFFF8EDD6), 1f to Color(0xFFE6CFA6), startY = 0f, endY = BakeLook.CASE_Y),
        size = Size(w, BakeLook.CASE_Y)
    )
    var x = 0f
    while (x < w) {
        drawLine(ink.fade(0.06f), Offset(x, 0f), Offset(x, BakeLook.CASE_Y), strokeWidth = 1.5f)
        x += 40f
    }
    var y = 0f
    while (y < BakeLook.CASE_Y) {
        drawLine(ink.fade(0.06f), Offset(0f, y), Offset(w, y), strokeWidth = 1.5f)
        y += 30f
    }
    // L'enseigne, au milieu du haut du mur.
    drawRoundRect(BakeLook.WoodDark, Offset(w / 2f - 96f, 3f), Size(192f, 25f), CornerRadius(7f))
    drawRoundRect(ink, Offset(w / 2f - 96f, 3f), Size(192f, 25f), CornerRadius(7f), style = Stroke(width = 2.5f))
    bakeWord(measurer, labels.sign, w / 2f, 16f, 15f, Color(0xFFFBE7B2), italic = true, spacing = 2f)
    // Un sac de farine empilé à droite des fours.
    for (k in 0 until 2) {
        val top = 164f + k * 26f
        drawRoundRect(Color(0xFFEFE6D2), Offset(626f, top), Size(66f, 30f), CornerRadius(8f))
        drawRoundRect(ink, Offset(626f, top), Size(66f, 30f), CornerRadius(8f), style = Stroke(width = 2.2f))
        drawLine(ink.fade(0.35f), Offset(634f, top + 9f), Offset(684f, top + 9f), strokeWidth = 2f, cap = StrokeCap.Round)
    }
    // Le cadre de l'étalage : le fond de bois clair, les deux planches, les montants entre les colonnes.
    drawRect(
        brush = Brush.verticalGradient(0f to Color(0xFFE9D3A8), 1f to Color(0xFFD9B97E), startY = BakeLook.CASE_Y, endY = BakeLook.TAG_Y),
        topLeft = Offset(8f, BakeLook.CASE_Y),
        size = Size(w - 16f, BakeLook.TAG_Y - BakeLook.CASE_Y)
    )
    for (shelf in floatArrayOf(BakeLook.SHELF_ONE, BakeLook.SHELF_TWO)) {
        drawRect(BakeLook.Wood, Offset(8f, shelf), Size(w - 16f, 4f))
        drawRect(ink.fade(0.5f), Offset(8f, shelf + 4f), Size(w - 16f, 1.5f))
    }
    for (column in 0..Bakery.PRODUCTS) {
        val px = BakeLook.GROUP_X0 + column * BakeLook.GROUP_STEP - 6f
        drawRect(BakeLook.WoodDark, Offset(px - 1.5f, BakeLook.CASE_Y), Size(3f, BakeLook.TAG_Y - BakeLook.CASE_Y))
    }
    drawRect(BakeLook.WoodDark, Offset(8f, BakeLook.CASE_Y - 4f), Size(w - 16f, 6f))
    drawRect(ink, Offset(8f, BakeLook.CASE_Y - 4f), Size(w - 16f, 2f))
    // La face de l'étalage, qui porte les étiquettes, puis le sol de lattes de bois.
    drawRect(BakeLook.Wood, Offset(8f, BakeLook.TAG_Y), Size(w - 16f, BakeLook.FLOOR_Y - BakeLook.TAG_Y))
    drawRect(ink, Offset(8f, BakeLook.TAG_Y), Size(w - 16f, 2f))
    drawRect(ink, Offset(8f, BakeLook.FLOOR_Y - 2f), Size(w - 16f, 2f))
    drawRect(
        brush = Brush.verticalGradient(0f to Color(0xFFC59B64), 1f to Color(0xFFA77B47), startY = BakeLook.FLOOR_Y, endY = h),
        topLeft = Offset(0f, BakeLook.FLOOR_Y),
        size = Size(w, h - BakeLook.FLOOR_Y)
    )
    var fx = 0f
    while (fx < w) {
        drawLine(ink.fade(0.18f), Offset(fx, BakeLook.FLOOR_Y), Offset(fx, h), strokeWidth = 1.5f)
        fx += 52f
    }
    // Les montants du cadre, à gauche et à droite.
    drawRect(BakeLook.WoodDark, Offset(0f, BakeLook.CASE_Y - 4f), Size(8f, BakeLook.FLOOR_Y - BakeLook.CASE_Y + 4f))
    drawRect(BakeLook.WoodDark, Offset(w - 8f, BakeLook.CASE_Y - 4f), Size(8f, BakeLook.FLOOR_Y - BakeLook.CASE_Y + 4f))
}

// ------------------------------------------------------------------------------------------------------------ ce qui bouge

/** Les compteurs du haut de l'écran : les clients repartis les mains vides à gauche, le chiffre d'affaires à droite. */
private fun DrawScope.hud(game: BakeryGame, labels: BakeryLabels, measurer: TextMeasurer) {
    val ink = BakeLook.Ink
    bakeWord(measurer, labels.misses, 16f, 7f, 11f, ink.fade(0.6f), align = -1)
    for (k in 0 until Bakery.MAX_MISSES) {
        val cx = 26f + k * 24f
        val cy = 21f
        if (k < game.misses) {
            drawCircle(BakeLook.Red, 8f, Offset(cx, cy))
            drawLine(Color.White, Offset(cx - 4f, cy - 4f), Offset(cx + 4f, cy + 4f), strokeWidth = 2.6f, cap = StrokeCap.Round)
            drawLine(Color.White, Offset(cx + 4f, cy - 4f), Offset(cx - 4f, cy + 4f), strokeWidth = 2.6f, cap = StrokeCap.Round)
        } else {
            drawCircle(ink.fade(0.12f), 8f, Offset(cx, cy))
        }
        drawCircle(ink, 8f, Offset(cx, cy), style = Stroke(width = 2f))
    }
    bakeWord(measurer, labels.revenue, Bakery.WIDTH - 16f, 7f, 11f, ink.fade(0.6f), align = 1)
    bakeWord(measurer, labels.price(game.revenue), Bakery.WIDTH - 16f, 22f, 22f, ink, align = 1)
}

/**
 * Les six plaques du mur du fond : le produit, le temps de cuisson réglé (avec les petites flèches pour le changer) et, dans le
 * coin, un indice de la dernière fournée sortie (une flèche vers le haut : trop pâle, il faut allonger le temps ; vers le bas :
 * trop cuite, il faut le raccourcir ; une coche : bien dorée). La plaque du produit choisi est rouge.
 */
private fun DrawScope.plaques(game: BakeryGame, labels: BakeryLabels, measurer: TextMeasurer) {
    val ink = BakeLook.Ink
    val pulse = 0.5f + 0.5f * sin(game.clockMs / 140f)
    for (product in 0 until Bakery.PRODUCTS) {
        val cx = colX(product)
        val left = cx - BakeLook.GROUP_W / 2f
        val top = BakeLook.PLAQUE_Y
        val selected = product == game.productCursor
        // Les deux clous et les ficelles.
        drawLine(ink.fade(0.5f), Offset(left + 24f, top - 2f), Offset(cx, top + 6f), strokeWidth = 1.5f)
        drawLine(ink.fade(0.5f), Offset(left + BakeLook.GROUP_W - 24f, top - 2f), Offset(cx, top + 6f), strokeWidth = 1.5f)
        if (selected) {
            drawRoundRect(
                BakeLook.Red.fade(0.15f + 0.15f * pulse),
                Offset(left - 4f, top - 4f),
                Size(BakeLook.GROUP_W + 8f, BakeLook.PLAQUE_H + 8f),
                CornerRadius(12f)
            )
        }
        drawRoundRect(BakeLook.Paper, Offset(left, top), Size(BakeLook.GROUP_W, BakeLook.PLAQUE_H), CornerRadius(8f))
        // Le bandeau du nom, rouge pour le produit choisi.
        drawRoundRect(
            if (selected) BakeLook.Red else BakeLook.WoodDark,
            Offset(left, top),
            Size(BakeLook.GROUP_W, 18f),
            CornerRadius(8f)
        )
        drawRect(if (selected) BakeLook.Red else BakeLook.WoodDark, Offset(left, top + 9f), Size(BakeLook.GROUP_W, 9f))
        drawRoundRect(
            if (selected) BakeLook.Red else ink,
            Offset(left, top),
            Size(BakeLook.GROUP_W, BakeLook.PLAQUE_H),
            CornerRadius(8f),
            style = Stroke(width = if (selected) 3.5f else 2.5f)
        )
        bakeWord(measurer, labels.products.getOrElse(product) { "" }, cx, top + 9.5f, 12f, Color.White)
        // Le produit doré à point, et le temps réglé.
        bakeShape(product, left + 24f, top + 45f, 1.15f, Golden)
        bakeWord(
            measurer,
            "${game.setting[product]} ${labels.second}",
            left + 62f,
            top + 45f,
            21f,
            if (selected) BakeLook.Red else ink
        )
        // Les flèches pour allonger ou raccourcir le temps.
        val arrow = if (selected) BakeLook.Red else ink.fade(0.35f)
        val ax = left + 91f
        val up = Path()
        up.moveTo(ax, top + 32f)
        up.lineTo(ax - 6f, top + 41f)
        up.lineTo(ax + 6f, top + 41f)
        up.close()
        drawPath(up, arrow)
        val down = Path()
        down.moveTo(ax, top + 60f)
        down.lineTo(ax - 6f, top + 51f)
        down.lineTo(ax + 6f, top + 51f)
        down.close()
        drawPath(down, arrow)
        // L'indice de la dernière fournée.
        val hx = left + BakeLook.GROUP_W - 9f
        val hy = top + 9f
        when (game.lastPull[product]) {
            BAKE_PALE -> {
                val hint = Path()
                hint.moveTo(hx, hy - 5f)
                hint.lineTo(hx - 5f, hy + 4f)
                hint.lineTo(hx + 5f, hy + 4f)
                hint.close()
                drawPath(hint, BakeLook.Amber)
            }
            BAKE_DARK, BAKE_BURNT -> {
                val hint = Path()
                hint.moveTo(hx, hy + 5f)
                hint.lineTo(hx - 5f, hy - 4f)
                hint.lineTo(hx + 5f, hy - 4f)
                hint.close()
                drawPath(hint, Color(0xFFFF8A80))
            }
            BAKE_GOLD -> {
                drawLine(Color(0xFF7CE08F), Offset(hx - 5f, hy), Offset(hx - 1.5f, hy + 4f), strokeWidth = 2.6f, cap = StrokeCap.Round)
                drawLine(Color(0xFF7CE08F), Offset(hx - 1.5f, hy + 4f), Offset(hx + 5f, hy - 4f), strokeWidth = 2.6f, cap = StrokeCap.Round)
            }
        }
    }
}

/** La fumée d'une fournée brûlée : quelques ronds gris qui montent en s'effaçant. */
private fun DrawScope.smoke(game: BakeryGame, cx: Float, top: Float) {
    for (k in 0 until 4) {
        val phase = ((game.clockMs / 26f + k * 22f) % 88f) / 88f
        val px = cx + sin(game.clockMs / 300f + k * 1.7f) * 9f + (k - 1.5f) * 10f
        val py = top - phase * 44f
        drawCircle(Color(0xFF4A4A4A).fade(0.65f * (1f - phase)), 5f + phase * 8f, Offset(px, py))
    }
}

/** Une petite cloche en ([cx], [cy]) ; elle tremble quand le four sonne. */
private fun DrawScope.bell(cx: Float, cy: Float, color: Color, shake: Float) {
    withTransform({ rotate(shake, Offset(cx, cy - 4f)) }) {
        val dome = Path()
        dome.moveTo(cx - 6f, cy + 4f)
        dome.cubicTo(cx - 6f, cy - 8f, cx + 6f, cy - 8f, cx + 6f, cy + 4f)
        dome.close()
        drawPath(dome, color)
        drawRect(color, Offset(cx - 8f, cy + 4f), Size(16f, 2.5f))
        drawCircle(color, 2f, Offset(cx, cy + 8f))
    }
}

/**
 * Un four : la façade en briques, l'afficheur (cloche, temps cuit « 12/16 s » avec le temps réglé sur le mur), la vitre où l'on
 * voit la fournée dorer, le numéro. Le four choisi est cerclé de rouge, avec une flèche au-dessus.
 */
private fun DrawScope.ovenView(game: BakeryGame, oven: Int, labels: BakeryLabels, measurer: TextMeasurer) {
    val ink = BakeLook.Ink
    val x = BakeLook.OVEN_X[oven]
    val y = BakeLook.OVEN_Y
    val w = BakeLook.OVEN_W
    val h = BakeLook.OVEN_H
    val product = game.ovenProduct[oven]
    val quality = game.ovenQuality(oven)
    val ratio = game.ovenRatio(oven)
    val ringing = game.ringing(oven)
    val selected = oven == game.ovenCursor
    val blink = sin(game.clockMs / 90f) > 0f
    // La façade en briques.
    drawRoundRect(BakeLook.Brick, Offset(x, y), Size(w, h), CornerRadius(14f))
    for (row in 0 until 6) {
        val ly = y + 8f + row * 15f
        drawLine(BakeLook.BrickDark.fade(0.7f), Offset(x + 6f, ly), Offset(x + w - 6f, ly), strokeWidth = 1.5f)
        val shift = if (row % 2 == 0) 0f else 15f
        var bx = x + 10f + shift
        while (bx < x + w - 8f) {
            drawLine(BakeLook.BrickDark.fade(0.7f), Offset(bx, ly), Offset(bx, ly + 15f), strokeWidth = 1.5f)
            bx += 30f
        }
    }
    // L'afficheur : la cloche, le temps cuit, le temps réglé.
    drawRoundRect(Color(0xFF1B1411), Offset(x + 12f, y + 6f), Size(w - 24f, 20f), CornerRadius(5f))
    val led = when {
        quality == BAKE_BURNT -> BakeLook.Red
        ringing && blink -> BakeLook.Red
        else -> BakeLook.Amber
    }
    val shake = if (game.dingMs[oven] > 0f || (ringing && blink)) sin(game.clockMs / 30f) * 14f else 0f
    bell(x + 26f, y + 15f, if (product < 0) Color(0xFF5A4A3E) else led, shake)
    if (product < 0) {
        bakeWord(measurer, "--", x + w / 2f + 8f, y + 16f, 14f, Color(0xFF5A4A3E))
    } else {
        val seconds = (game.ovenMs[oven] / 1000f).toInt()
        bakeWord(measurer, "$seconds/${game.setting[product]} ${labels.second}", x + w / 2f + 8f, y + 16f, 14f, led)
    }
    // La vitre : l'intérieur du four, la plaque et la fournée.
    val wx = x + 12f
    val wy = y + 30f
    val ww = w - 24f
    val wh = 46f
    val glow = if (product < 0) 0f else 0.35f + 0.1f * sin(game.clockMs / 200f + oven)
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF241713), lerp(Color(0xFF3A2218), Color(0xFFC4571F), glow))
        ),
        topLeft = Offset(wx, wy),
        size = Size(ww, wh),
        cornerRadius = CornerRadius(8f)
    )
    if (product >= 0) {
        val count = Bakery.BATCH[product]
        val spacing = min(22f, (ww - 20f) / count)
        val start = x + w / 2f - (count - 1) * spacing / 2f
        val body = bakeColor(ratio)
        val lift = if (game.loadMs[oven] > 0f) game.loadMs[oven] / Bakery.LOAD_MS * 10f else 0f
        for (k in 0 until count) {
            bakeShape(product, start + k * spacing, wy + wh - 19f + lift, 0.9f, body)
        }
    }
    drawRect(Color(0xFF8E979B), Offset(wx + 4f, wy + wh - 8f), Size(ww - 8f, 3f))
    if (game.pullMs[oven] > 0f) {
        drawRoundRect(Color.White.fade(0.6f * game.pullMs[oven] / Bakery.PULL_MS), Offset(wx, wy), Size(ww, wh), CornerRadius(8f))
    }
    drawRoundRect(ink, Offset(wx, wy), Size(ww, wh), CornerRadius(8f), style = Stroke(width = 3f))
    // Le numéro du four, sur une plaque ronde en bas.
    drawCircle(BakeLook.Metal, 8f, Offset(x + w / 2f, y + h - 9f))
    drawCircle(ink, 8f, Offset(x + w / 2f, y + h - 9f), style = Stroke(width = 2f))
    bakeWord(measurer, (oven + 1).toString(), x + w / 2f, y + h - 9f, 11f, ink)
    drawRoundRect(ink, Offset(x, y), Size(w, h), CornerRadius(14f), style = Stroke(width = 3f))
    if (quality == BAKE_BURNT) smoke(game, x + w / 2f, y)
    if (selected) {
        val pulse = 0.5f + 0.5f * sin(game.clockMs / 140f)
        drawRoundRect(BakeLook.Red.fade(0.7f + 0.3f * pulse), Offset(x - 3f, y - 3f), Size(w + 6f, h + 6f), CornerRadius(16f), style = Stroke(width = 4.5f))
        val bob = sin(game.clockMs / 140f) * 2f
        val arrow = Path()
        arrow.moveTo(x + w / 2f, y - 4f + bob)
        arrow.lineTo(x + w / 2f - 9f, y - 16f + bob)
        arrow.lineTo(x + w / 2f + 9f, y - 16f + bob)
        arrow.close()
        drawPath(arrow, BakeLook.Red)
        drawPath(arrow, ink, style = Stroke(width = 2f, join = StrokeJoin.Round))
    }
}

/** Le boulanger (le personnage de Drive, de face) à gauche des fours : il saute de joie quand une fournée dorée sort, et tremble quand un geste est refusé. */
private fun DrawScope.baker(game: BakeryGame, sprites: DriveSprites) {
    val image = sprites.front ?: return
    val w = (image.width * BakeLook.BAKER_SCALE).roundToInt()
    val h = (image.height * BakeLook.BAKER_SCALE).roundToInt()
    val hop = if (game.happyMs > 0f) abs(sin(game.happyMs / 90f)) * 9f else 0f
    val shake = if (game.blockedMs > 0f) sin(game.clockMs / 25f) * 3f else 0f
    val cx = BakeLook.BAKER_X + shake
    val top = BakeLook.BAKER_FEET - h - hop
    drawImage(
        image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset((cx - w / 2f).roundToInt(), top.roundToInt()),
        dstSize = IntSize(w, h),
        filterQuality = FilterQuality.None
    )
}

/**
 * La table, devant le boulanger et les fours : sur chaque colonne, les plateaux de pâtons crus qui attendent (deux au plus) et la
 * barre de pousse du prochain. La colonne du produit choisi est éclairée en rouge.
 */
private fun DrawScope.table(game: BakeryGame) {
    val ink = BakeLook.Ink
    val w = Bakery.WIDTH
    val pulse = 0.5f + 0.5f * sin(game.clockMs / 140f)
    drawRect(
        brush = Brush.verticalGradient(0f to Color(0xFFE8CC9A), 1f to Color(0xFFD2AE74), startY = BakeLook.TABLE_Y, endY = BakeLook.TABLE_Y + BakeLook.TABLE_TOP_H),
        topLeft = Offset(0f, BakeLook.TABLE_Y),
        size = Size(w, BakeLook.TABLE_TOP_H)
    )
    drawRect(BakeLook.Wood, Offset(0f, BakeLook.TABLE_Y + BakeLook.TABLE_TOP_H), Size(w, BakeLook.TABLE_H - BakeLook.TABLE_TOP_H))
    drawRect(ink, Offset(0f, BakeLook.TABLE_Y), Size(w, 2.5f))
    drawRect(ink, Offset(0f, BakeLook.TABLE_Y + BakeLook.TABLE_TOP_H), Size(w, 2.5f))
    for (product in 0 until Bakery.PRODUCTS) {
        val cx = colX(product)
        if (product == game.productCursor) {
            drawRoundRect(
                BakeLook.Red.fade(0.12f + 0.12f * pulse),
                Offset(cx - BakeLook.GROUP_W / 2f, BakeLook.TABLE_Y + 3f),
                Size(BakeLook.GROUP_W, BakeLook.TABLE_TOP_H - 4f),
                CornerRadius(8f)
            )
        }
        for (slot in 0 until Bakery.MAX_RAW) {
            val sx = cx - 26f + slot * 52f
            if (slot < game.raw[product]) {
                // Un plateau de pâtons crus : trois pièces sur une plaque de métal.
                drawRoundRect(Color(0xFF8E979B), Offset(sx - 23f, BakeLook.TABLE_Y + 11f), Size(46f, 13f), CornerRadius(4f))
                for (k in -1..1) bakeShape(product, sx + k * 14f, BakeLook.TABLE_Y + 11f, 0.62f, RawDough)
                drawRoundRect(ink, Offset(sx - 23f, BakeLook.TABLE_Y + 11f), Size(46f, 13f), CornerRadius(4f), style = Stroke(width = 1.8f))
            } else {
                drawRoundRect(ink.fade(0.18f), Offset(sx - 23f, BakeLook.TABLE_Y + 11f), Size(46f, 13f), CornerRadius(4f), style = Stroke(width = 1.5f))
            }
        }
        // La pousse du prochain plateau.
        if (game.raw[product] < Bakery.MAX_RAW) {
            val share = (game.doughMs[product] / Bakery.DOUGH_MS).coerceIn(0f, 1f)
            drawRoundRect(ink.fade(0.18f), Offset(cx - 40f, BakeLook.TABLE_Y + 25f), Size(80f, 3.5f), CornerRadius(2f))
            drawRoundRect(Color(0xFF7BB8E0), Offset(cx - 40f, BakeLook.TABLE_Y + 25f), Size(80f * share, 3.5f), CornerRadius(2f))
        }
    }
}

/**
 * L'étalage, au premier plan : les pièces de chaque produit posées sur les deux planches (huit au plus ; les bien cuites d'abord,
 * puis les pâles et les trop cuites, vendues à demi-tarif), et en dessous l'étiquette avec le prix et le nombre de pièces. Un
 * produit demandé par le client est éclairé en jaune ; une étiquette est rouge pour le produit choisi.
 */
private fun DrawScope.showcase(game: BakeryGame, labels: BakeryLabels, measurer: TextMeasurer) {
    val ink = BakeLook.Ink
    val pulse = 0.5f + 0.5f * sin(game.clockMs / 140f)
    val asking = game.customerStage == CUSTOMER_ARRIVE || game.customerStage == CUSTOMER_THINK
    for (product in 0 until Bakery.PRODUCTS) {
        val cx = colX(product)
        val left = cx - BakeLook.GROUP_W / 2f
        val wanted = asking && (0 until game.wantLines).any { game.wantProduct[it] == product }
        if (wanted) {
            drawRoundRect(
                BakeLook.Amber.fade(0.22f + 0.18f * pulse),
                Offset(left - 2f, BakeLook.CASE_Y),
                Size(BakeLook.GROUP_W + 4f, BakeLook.TAG_Y - BakeLook.CASE_Y),
                CornerRadius(6f)
            )
        }
        // Les pièces, par rangées de quatre : d'abord les bien cuites, puis les pâles, puis les trop cuites.
        var slot = 0
        for (kind in SHELF_GOOD..SHELF_DARK) {
            val color = when (kind) {
                SHELF_GOOD -> Golden
                SHELF_PALE -> bakeColor(0.82f)
                else -> bakeColor(1.3f)
            }
            repeat(game.stock[product][kind]) {
                if (slot < Bakery.SHELF_CAP) {
                    val px = cx - 37.5f + (slot % 4) * 25f
                    val py = if (slot < 4) BakeLook.SHELF_ONE - 13f else BakeLook.SHELF_TWO - 13f
                    bakeShape(product, px, py, 0.8f, color)
                    slot++
                }
            }
        }
        // L'étiquette : le prix à gauche, le nombre de pièces à droite.
        val total = game.total(product)
        val selected = product == game.productCursor
        val empty = total == 0
        val tagLeft = cx - 50f
        val fill = when {
            selected -> BakeLook.Red
            empty -> Color(0xFFFFD6D0)
            else -> BakeLook.Paper
        }
        drawRoundRect(fill, Offset(tagLeft, BakeLook.TAG_Y + 0.5f), Size(100f, BakeLook.TAG_H), CornerRadius(5f))
        drawRoundRect(ink, Offset(tagLeft, BakeLook.TAG_Y + 0.5f), Size(100f, BakeLook.TAG_H), CornerRadius(5f), style = Stroke(width = 2f))
        val textColor = when {
            selected -> Color.White
            empty -> BakeLook.Red
            else -> ink
        }
        bakeWord(measurer, labels.price(Bakery.PRICE[product]), tagLeft + 7f, BakeLook.TAG_Y + 9f, 12f, textColor, align = -1)
        bakeWord(measurer, "×$total", tagLeft + 93f, BakeLook.TAG_Y + 9f, 13f, textColor, align = 1)
    }
}

/** Un client vu de face, dont seules la tête et les épaules dépassent en bas de l'écran ; [mood] : 1 content, -1 fâché, 0 neutre. */
private fun DrawScope.customerBust(look: Int, x: Float, mood: Int) {
    val ink = BakeLook.Ink
    val s = BakeLook.CUSTOMER_SCALE
    val coats = intArrayOf(0xFF4A7BB7.toInt(), 0xFF9B4F8E.toInt(), 0xFFB9783A.toInt(), 0xFF4F9A6B.toInt())
    val hairs = intArrayOf(0xFF3A2A1E.toInt(), 0xFFD9B04A.toInt(), 0xFF8A8A8A.toInt(), 0xFF1E1E22.toInt())
    val coat = Color(coats[look.coerceIn(0, 3)])
    val hair = Color(hairs[look.coerceIn(0, 3)])
    val headY = BakeLook.CUSTOMER_HEAD_Y
    withTransform({
        translate(x, headY)
        scale(s, s, Offset.Zero)
    }) {
        // Le buste et les bras (le bas sort de l'écran), puis la tête, les cheveux, le béret ou les lunettes.
        drawRoundRect(coat, Offset(-24f, 22f), Size(48f, 60f), CornerRadius(12f))
        drawRoundRect(ink, Offset(-24f, 22f), Size(48f, 60f), CornerRadius(12f), style = Stroke(width = 2.6f))
        drawCircle(BakeLook.Skin, 19f, Offset.Zero)
        drawArc(hair, 180f, 180f, true, Offset(-19f, -19f), Size(38f, 30f))
        if (look == 2) {
            drawRoundRect(Color(0xFF3C4A63), Offset(-23f, -23f), Size(46f, 9f), CornerRadius(4f))
            drawRoundRect(Color(0xFF3C4A63), Offset(-13f, -31f), Size(26f, 12f), CornerRadius(5f))
        }
        drawCircle(ink, 19f, Offset.Zero, style = Stroke(width = 2.6f))
        drawCircle(ink, 2.4f, Offset(-7f, -2f))
        drawCircle(ink, 2.4f, Offset(7f, -2f))
        if (look == 3) {
            drawCircle(ink, 5.8f, Offset(-7f, -2f), style = Stroke(width = 2f))
            drawCircle(ink, 5.8f, Offset(7f, -2f), style = Stroke(width = 2f))
        }
        when {
            mood > 0 -> drawArc(ink, 20f, 140f, false, Offset(-8f, 2f), Size(16f, 12f), style = Stroke(width = 2.4f, cap = StrokeCap.Round))
            mood < 0 -> {
                drawArc(ink, 200f, 140f, false, Offset(-8f, 8f), Size(16f, 12f), style = Stroke(width = 2.4f, cap = StrokeCap.Round))
                drawLine(ink, Offset(-12f, -9f), Offset(-3f, -6f), strokeWidth = 2.4f, cap = StrokeCap.Round)
                drawLine(ink, Offset(12f, -9f), Offset(3f, -6f), strokeWidth = 2.4f, cap = StrokeCap.Round)
            }
            else -> drawLine(ink, Offset(-6f, 10f), Offset(6f, 10f), strokeWidth = 2.4f, cap = StrokeCap.Round)
        }
    }
}

/**
 * La bulle du client, à côté de lui : ce qu'il veut (un produit et sa quantité par ligne). Une fois servi, chaque ligne montre
 * ce qu'il a pu prendre (en vert s'il a tout trouvé, en rouge sinon) et, à droite, ce qu'il a payé.
 */
private fun DrawScope.orderBubble(game: BakeryGame, x: Float, labels: BakeryLabels, measurer: TextMeasurer, alpha: Float) {
    if (alpha <= 0f) return
    val ink = BakeLook.Ink
    val bw = 14f + game.wantLines * 46f + 56f
    val bh = 38f
    val top = 358f
    val onRight = x < Bakery.WIDTH / 2f
    val left = (if (onRight) x + 36f else x - 36f - bw).coerceIn(4f, Bakery.WIDTH - 4f - bw)
    val tail = Path()
    if (onRight) {
        tail.moveTo(left + 2f, top + 12f)
        tail.lineTo(left - 10f, top + 22f)
        tail.lineTo(left + 2f, top + 30f)
    } else {
        tail.moveTo(left + bw - 2f, top + 12f)
        tail.lineTo(left + bw + 10f, top + 22f)
        tail.lineTo(left + bw - 2f, top + 30f)
    }
    tail.close()
    drawPath(tail, Color.White.fade(alpha))
    drawPath(tail, ink.fade(alpha), style = Stroke(width = 2.5f, join = StrokeJoin.Round))
    drawRoundRect(Color.White.fade(alpha), Offset(left, top), Size(bw, bh), CornerRadius(10f))
    drawRoundRect(ink.fade(alpha), Offset(left, top), Size(bw, bh), CornerRadius(10f), style = Stroke(width = 2.5f))
    val served = game.customerStage == CUSTOMER_RESULT || game.customerStage == CUSTOMER_LEAVE
    for (line in 0 until game.wantLines) {
        val lx = left + 10f + line * 46f
        bakeShape(game.wantProduct[line], lx + 12f, top + 19f, 0.8f, Golden.fade(alpha))
        val want = game.wantQty[line]
        val got = game.gotQty[line]
        val text = if (served) "$got/$want" else "×$want"
        val color = when {
            !served -> ink
            got >= want -> BakeLook.GreenDark
            else -> BakeLook.Red
        }
        bakeWord(measurer, text, lx + 25f, top + 19f, 14f, color.fade(alpha), align = -1)
    }
    if (served) {
        val color = if (game.mood > 0) BakeLook.GreenDark else BakeLook.Red
        bakeWord(measurer, "+" + labels.price(game.lastPoints), left + bw - 8f, top + 19f, 15f, color.fade(alpha), align = 1)
    }
}

/** Le client sur le sol : il entre par un côté, vient devant la colonne du premier produit qu'il veut, regarde l'étalage, paie, puis repart de l'autre côté. */
private fun DrawScope.customerLayer(game: BakeryGame, labels: BakeryLabels, measurer: TextMeasurer) {
    if (game.customerStage == CUSTOMER_NONE || game.wantLines == 0) return
    val target = colX(game.wantProduct[0])
    val entry = if (game.customerSide > 0) Bakery.WIDTH + 40f else -40f
    val exit = if (game.customerSide > 0) -40f else Bakery.WIDTH + 40f
    val x: Float
    var alpha = 1f
    when (game.customerStage) {
        CUSTOMER_ARRIVE -> {
            val t = 1f - game.stageMs / Bakery.ARRIVE_MS
            val ease = 1f - (1f - t) * (1f - t)
            x = mix(entry, target, ease)
            alpha = ((t - 0.6f) / 0.4f).coerceIn(0f, 1f)
        }
        CUSTOMER_LEAVE -> {
            val t = 1f - game.stageMs / Bakery.LEAVE_MS
            x = mix(target, exit, t * t)
            alpha = (1f - t * 1.5f).coerceIn(0f, 1f)
        }
        else -> x = target
    }
    val mood = if (game.customerStage == CUSTOMER_RESULT || game.customerStage == CUSTOMER_LEAVE) game.mood else 0
    customerBust(game.customerLook, x, mood)
    orderBubble(game, x, labels, measurer, alpha)
}

/** Tout ce qui change pendant la partie, dans le repère de 720 x 405 de l'écran. */
internal fun DrawScope.drawBakeryLive(game: BakeryGame, sprites: DriveSprites, labels: BakeryLabels, measurer: TextMeasurer) {
    hud(game, labels, measurer)
    plaques(game, labels, measurer)
    for (index in 0 until Bakery.OVENS) ovenView(game, index, labels, measurer)
    baker(game, sprites)
    table(game)
    showcase(game, labels, measurer)
    customerLayer(game, labels, measurer)
    if (!game.over && game.clockMs < 2_400f) {
        val pulse = 1f + 0.06f * sin(game.clockMs / 110f)
        bakeWord(measurer, labels.opening, Bakery.WIDTH / 2f, 160f, 40f * pulse, BakeLook.Red, italic = true, spacing = 3f)
    }
    val shade = min(0.25f, game.blockedMs / Bakery.BLOCKED_MS * 0.25f)
    if (shade > 0f) {
        drawRect(BakeLook.Red.fade(shade), size = Size(Bakery.WIDTH, Bakery.HEIGHT))
        bakeWord(measurer, labels.no, Bakery.WIDTH / 2f, 160f, 44f, BakeLook.Red, italic = true)
    }
}
