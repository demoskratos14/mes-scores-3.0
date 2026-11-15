package com.aventure.messcores

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Les dessins de l'écran de Drive inter Longpont, dans le repère de l'écran (720 x 405). Le décor qui ne bouge pas (dehors, murs, porte,
 * tapis, chariots) se dessine une fois avec [drawDriveScene] ; tout ce qui bouge (commandes, produits, voitures, téléphone, souris,
 * personnage, compteurs) avec [drawDriveLive]. Les personnages sont les images de l'auteur du jeu ([DriveSprites]).
 */

/** Les trois images du personnage : de face (zones d'action), de profil vers la droite et vers la gauche (zones de déplacement). */
internal class DriveSprites(val front: ImageBitmap?, val left: ImageBitmap?, val right: ImageBitmap?)

/** Charge les images du personnage une fois (dans les ressources `drawable-nodpi`, donc sans mise à l'échelle). */
internal fun loadDriveSprites(context: Context): DriveSprites {
    fun load(id: Int): ImageBitmap? = BitmapFactory.decodeResource(context.resources, id)?.asImageBitmap()
    return DriveSprites(
        front = load(R.drawable.drive_hero_front),
        left = load(R.drawable.drive_hero_left),
        right = load(R.drawable.drive_hero_right)
    )
}

/** Les mots écrits sur l'écran (ils viennent des ressources : aucun texte en dur). */
internal class DriveLabels(
    val points: String,
    val orders: String,
    val errors: String,
    val stock: String,
    val ready: String,
    val sign: String
)

/** La géométrie de la scène : la pièce vue de trois quarts, la porte, la perspective des tapis, des bacs et des pistes. */
private object Geo {
    const val W = Drive.WIDTH
    const val H = Drive.HEIGHT
    const val DX = Drive.DOOR_X
    const val RX = Drive.ROOM_RIGHT
    const val BX0 = DX + 78f
    const val BX1 = RX - 78f
    const val CT0 = 14f
    const val CT1 = 64f
    const val FL1 = 262f
    const val FY = H - 2f
    const val CX = (DX + RX) / 2f
    const val RATIO = (BX1 - BX0) / (RX - DX)

    /** Hauteur des bacs et des tapis, demi-largeur d'un tapis ou d'un bac, épaisseur d'un tapis. */
    const val HT = 50f
    const val HW = 25f
    const val DECK = 12f

    /** Le personnage : ses pieds touchent le bas de l'écran, à 75 % de la taille de l'image. */
    const val SPRITE_SCALE = 0.75f

    /** Où s'arrêtent vers le fond les pistes de livraison. */
    const val LANE_FAR = 0.9f

    fun y(s: Float): Float = FY + (FL1 - FY) * s
    fun k(s: Float): Float = 1f - (1f - RATIO) * s
    fun x(fx: Float, s: Float): Float = CX + (fx - CX) * k(s)

    /** Un point de la pièce : [fx] sur le devant, [s] la profondeur (0 devant, 1 au fond), [h] la hauteur au-dessus du sol. */
    fun at(fx: Float, s: Float, h: Float): Offset = Offset(x(fx, s), y(s) - h * k(s))

    /** Les pistes rétrécissent en montant, et les voitures avec. */
    fun lane(s: Float): Float = 1f - 0.75f * s / LANE_FAR
}

private val Ink = Color(0xFF20241F)
private val Red = Color(0xFFE2001A)

/** La couleur d'une lettre : A en rouge, B en noir (les boutons, les tapis, les commandes et les voitures s'y retrouvent). */
internal fun driveLetterColor(glyph: Int): Color = if (glyph == GLYPH_A) Red else Color(0xFF111111)
private val Paper = Color(0xFFE3E6D9)
private val PaperLight = Color(0xFFF6F6EE)

private fun Color.fade(a: Float): Color = copy(alpha = alpha * a)

// ------------------------------------------------------------------------------------------------------------ outils de dessin

private fun polygon(vararg c: Float): Path {
    val path = Path()
    path.moveTo(c[0], c[1])
    var i = 2
    while (i + 1 < c.size) {
        path.lineTo(c[i], c[i + 1])
        i += 2
    }
    path.close()
    return path
}

private fun polygon(points: List<Offset>): Path {
    val path = Path()
    points.forEachIndexed { n, p -> if (n == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
    path.close()
    return path
}

private fun rectPath(left: Float, top: Float, right: Float, bottom: Float): Path = polygon(left, top, right, top, right, bottom, left, bottom)

private fun ovalPath(cx: Float, cy: Float, rx: Float, ry: Float): Path {
    val path = Path()
    path.addOval(androidx.compose.ui.geometry.Rect(cx - rx, cy - ry, cx + rx, cy + ry))
    return path
}

/** Remplit puis trace [path] ; [fill] absent : seulement le trait. */
private fun DrawScope.fillStroke(path: Path, fill: Color?, ink: Color, width: Float) {
    if (fill != null) drawPath(path, fill)
    drawPath(path, ink, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.segment(from: Offset, to: Offset, color: Color, width: Float, cap: StrokeCap = StrokeCap.Round) {
    drawLine(color, from, to, strokeWidth = width, cap = cap)
}

/** Un mot centré en ([cx], [cy]), de [px] pixels de l'écran de hauteur de police (le dessin est mis à l'échelle ensuite). */
private fun DrawScope.word(
    measurer: TextMeasurer,
    text: String,
    cx: Float,
    cy: Float,
    px: Float,
    color: Color,
    italic: Boolean = false,
    spacing: Float = 0f,
    leftAligned: Boolean = false
) {
    val style = TextStyle(
        color = color,
        fontSize = px.toSp(),
        fontWeight = FontWeight.Bold,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        letterSpacing = spacing.toSp()
    )
    val result = measurer.measure(text = text, style = style, softWrap = false, maxLines = 1)
    val left = if (leftAligned) cx else cx - result.size.width / 2f
    drawText(result, topLeft = Offset(left, cy - result.size.height / 2f))
}

// Les signes tracés au trait (pas de police) : les lettres A et B, la flèche du haut et le point d'exclamation.
internal const val GLYPH_A = 0
internal const val GLYPH_B = 1
internal const val GLYPH_UP = 2
internal const val GLYPH_BANG = 3

/** Un signe centré en ([cx], [cy]) dans une boîte de [size] de côté. */
internal fun DrawScope.driveGlyph(kind: Int, cx: Float, cy: Float, size: Float, color: Color) {
    withTransform({ translate(cx, cy); scale(size, size, pivot = Offset.Zero) }) {
        val path = Path()
        when (kind) {
            GLYPH_A -> {
                path.moveTo(-0.42f, 0.5f); path.lineTo(0f, -0.5f); path.lineTo(0.42f, 0.5f)
                path.moveTo(-0.24f, 0.12f); path.lineTo(0.24f, 0.12f)
            }
            GLYPH_B -> {
                path.moveTo(-0.3f, -0.5f); path.lineTo(-0.3f, 0.5f)
                path.moveTo(-0.3f, -0.5f); path.lineTo(0.05f, -0.5f)
                path.cubicTo(0.42f, -0.5f, 0.42f, 0f, 0.05f, 0f)
                path.lineTo(-0.3f, 0f)
                path.moveTo(0.05f, 0f)
                path.cubicTo(0.5f, 0f, 0.5f, 0.5f, 0.05f, 0.5f)
                path.lineTo(-0.3f, 0.5f)
            }
            GLYPH_UP -> {
                path.moveTo(0f, 0.5f); path.lineTo(0f, -0.5f)
                path.moveTo(-0.34f, -0.14f); path.lineTo(0f, -0.5f); path.lineTo(0.34f, -0.14f)
            }
            else -> {
                path.moveTo(0f, -0.5f); path.lineTo(0f, 0.12f)
                path.moveTo(0f, 0.44f); path.lineTo(0f, 0.45f)
            }
        }
        // Le trait est exprimé dans le repère de la boîte (de 1 de côté) : 0,2 de large.
        drawPath(path, color, style = Stroke(width = 0.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Les signes pour une touche de la croix ou des boutons : 0 = A, 1 = B, 2 = haut (voir [DriveGame.phone]). */
private fun glyphFor(want: Int): Int = when (want) {
    0 -> GLYPH_A
    1 -> GLYPH_B
    else -> GLYPH_UP
}

// Les chiffres à sept segments des compteurs.
private val Digits = arrayOf("abcdef", "bc", "abdeg", "abcdg", "bcfg", "acdfg", "acdefg", "abc", "abcdefg", "abcdfg")

private fun segmentLine(name: Char): FloatArray = when (name) {
    'a' -> floatArrayOf(3f, 2f, 15f, 2f)
    'b' -> floatArrayOf(17f, 4f, 17f, 15f)
    'c' -> floatArrayOf(17f, 19f, 17f, 30f)
    'd' -> floatArrayOf(3f, 32f, 15f, 32f)
    'e' -> floatArrayOf(1f, 19f, 1f, 30f)
    'f' -> floatArrayOf(1f, 4f, 1f, 15f)
    else -> floatArrayOf(3f, 17f, 15f, 17f)
}

/** Un chiffre à sept segments, un peu penché comme sur un afficheur ; [digit] de 0 à 9, de coin haut gauche ([x], [y]). */
private fun DrawScope.digit(digit: Int, x: Float, y: Float, alpha: Float, scale: Float = 1f) {
    val color = Ink.fade(alpha)
    for (name in Digits[digit.coerceIn(0, 9)]) {
        val l = segmentLine(name)
        val from = Offset(x + (l[0] - 0.12f * l[1]) * scale, y + l[1] * scale)
        val to = Offset(x + (l[2] - 0.12f * l[3]) * scale, y + l[3] * scale)
        segment(from, to, color, 3.2f * scale, StrokeCap.Butt)
    }
}

/** Un compteur de deux chiffres (le chiffre des dizaines éteint sous 10) ; son nom à gauche. */
private fun DrawScope.counter(measurer: TextMeasurer, value: Int, label: String, y: Float) {
    val n = min(99, max(0, value))
    word(measurer, label, 12f, y + 18f, 14f, Ink.fade(0.6f), leftAligned = true)
    if (n >= 10) digit(n / 10, 54f, y, 0.92f) else digit(8, 54f, y, 0.05f)
    digit(n % 10, 80f, y, 0.92f)
}

// ------------------------------------------------------------------------------------------------------------ les produits

/** Un des six produits, posé sur le sol en ([x], [y]) (le bas), à l'échelle [size] (1 produit = [size] pixels de haut environ). */
internal fun DrawScope.driveProduct(id: Int, x: Float, y: Float, size: Float, alpha: Float = 1f) {
    val line = Stroke(width = max(0.05f, 1.6f / size), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val ink = Ink.fade(alpha)
    fun paint(path: Path, color: Long) {
        drawPath(path, Color(color).fade(alpha))
        drawPath(path, ink, style = line)
    }
    withTransform({ translate(x, y); scale(size, size, pivot = Offset.Zero) }) {
        when (id) {
            DRIVE_MILK -> {
                paint(rectPath(-0.26f, -0.78f, 0.26f, 0f), 0xFFEEF0F5)
                paint(polygon(-0.26f, -0.78f, -0.15f, -1f, 0.15f, -1f, 0.26f, -0.78f), 0xFFC3C8D4)
                paint(rectPath(-0.26f, -0.5f, 0.26f, -0.3f), 0xFF5D7FC4)
            }
            DRIVE_BREAD -> {
                paint(ovalPath(0f, -0.3f, 0.46f, 0.28f), 0xFFD9A35C)
                val cuts = Path()
                for (q in floatArrayOf(-0.22f, 0f, 0.22f)) {
                    cuts.moveTo(q - 0.06f, -0.42f)
                    cuts.lineTo(q + 0.06f, -0.2f)
                }
                drawPath(cuts, ink, style = line)
            }
            DRIVE_APPLE -> {
                val body = Path()
                body.addOval(androidx.compose.ui.geometry.Rect(-0.34f, -0.74f, 0.34f, -0.06f))
                paint(body, 0xFFD65A4A)
                val stem = Path()
                stem.moveTo(0f, -0.72f)
                stem.lineTo(0.04f, -0.92f)
                drawPath(stem, ink, style = line)
                withTransform({ rotate(-0.5f * 180f / PI.toFloat(), pivot = Offset(0.14f, -0.86f)) }) {
                    paint(ovalPath(0.14f, -0.86f, 0.12f, 0.06f), 0xFF4F9A52)
                }
            }
            DRIVE_CAN -> {
                paint(rectPath(-0.3f, -0.78f, 0.3f, 0f), 0xFFC9CCD2)
                paint(rectPath(-0.3f, -0.58f, 0.3f, -0.28f), 0xFFE0A43A)
                paint(ovalPath(0f, -0.78f, 0.3f, 0.09f), 0xFFE4E6EA)
            }
            DRIVE_BOTTLE -> {
                paint(rectPath(-0.21f, -0.62f, 0.21f, 0f), 0xFF7FB36A)
                paint(rectPath(-0.08f, -0.98f, 0.08f, -0.6f), 0xFF7FB36A)
                paint(rectPath(-0.1f, -1.04f, 0.1f, -0.96f), 0xFFC9CCD2)
            }
            else -> {
                paint(polygon(-0.46f, 0f, 0.46f, 0f, 0.46f, -0.42f, -0.46f, -0.72f), 0xFFF0CF5A)
                drawCircle(ink, 0.06f, Offset(0.12f, -0.2f))
                drawCircle(ink, 0.05f, Offset(-0.18f, -0.2f))
                drawCircle(ink, 0.04f, Offset(0.3f, -0.3f))
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------------------ le décor fixe

/** Une voiture vue de face, ses roues au sol en ([x], [y]), à l'échelle [k]. */
private fun DrawScope.car(x: Float, y: Float, k: Float, body: Color, alpha: Float) {
    val ink = Ink.fade(alpha)
    val width = max(1.2f, 2f / k)
    val paint = body.fade(alpha)
    withTransform({ translate(x, y); scale(k, k, pivot = Offset.Zero) }) {
        drawRect(ink, Offset(-31f, -13f), Size(7f, 13f))
        drawRect(ink, Offset(24f, -13f), Size(7f, 13f))
        fillStroke(polygon(-21f, -27f, -16f, -47f, 16f, -47f, 21f, -27f), paint, ink, width)
        fillStroke(polygon(-16f, -29f, -12.5f, -43f, 12.5f, -43f, 16f, -29f), Color(0xFFE9F0F6).fade(alpha), ink, width)
        drawRoundRect(paint, Offset(-28f, -28f), Size(56f, 22f), CornerRadius(6f))
        drawRoundRect(ink, Offset(-28f, -28f), Size(56f, 22f), CornerRadius(6f), style = Stroke(width))
        for (q in floatArrayOf(-19f, 19f)) {
            drawCircle(Color(0xFFFFF3B8).fade(alpha), 4.2f, Offset(q, -18f))
            drawCircle(ink, 4.2f, Offset(q, -18f), style = Stroke(width))
        }
        fillStroke(rectPath(-9f, -21f, 9f, -13f), Color(0xFF8F949B).fade(alpha), ink, width)
    }
}

private val CarColors = longArrayOf(0xFFC9D6E6, 0xFFE6D3C4)

/**
 * Le décor qui ne change pas : le dehors à gauche (trottoir, deux pistes sous la coupole DRIVE, bornes d'appel), le bureau à droite
 * (murs, porte, les quatre tapis et les trois lignes de chariots). Dessiné dans le repère de 720 x 405.
 */
internal fun DrawScope.drawDriveScene(labels: DriveLabels, measurer: TextMeasurer) {
    val g = Geo
    val tint = Ink.fade(0.05f)
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color(0xFFCDD6E3), 0.7f to Color(0xFFE6E5D6), 1f to Color(0xFFD3D9C3),
            startY = 0f, endY = g.H
        ),
        size = Size(g.W, g.H)
    )

    // Dehors : le sol, le trottoir, les pistes (elles rétrécissent vers le fond), la coupole, les bornes.
    drawPath(polygon(0f, g.FL1, g.BX0, g.FL1, g.DX, g.FY, 0f, g.FY), tint)
    fillStroke(polygon(0f, g.FY, g.DX, g.FY, g.DX, g.y(0.1f), 0f, g.y(0.1f)), PaperLight.fade(0.6f), Ink, 2f)
    for (zone in Drive.BAY_ZONES) {
        val fx = Drive.zoneX(zone)
        val far = 28f * g.lane(g.LANE_FAR)
        fillStroke(polygon(fx - 28f, g.FY, fx + 28f, g.FY, fx + far, g.y(g.LANE_FAR), fx - far, g.y(g.LANE_FAR)), Ink.fade(0.2f), Ink, 2f)
        drawLine(
            Ink, Offset(fx, g.y(0.1f)), Offset(fx, g.y(g.LANE_FAR)), strokeWidth = 1.4f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 7f))
        )
    }
    // La coupole au-dessus des pistes : toit rouge bombé, bandeau blanc « DRIVE », filet noir, deux poteaux.
    run {
        val left = 6f
        val right = 236f
        val top = 262f
        val bottom = 290f
        drawRect(Ink, Offset(left, bottom), Size(4f, g.FY - bottom))
        drawRect(Ink, Offset(right - 4f, bottom), Size(4f, g.FY - bottom))
        val roof = Path()
        roof.moveTo(left - 4f, top)
        roof.quadraticTo((left + right) / 2f, top - 46f, right + 4f, top)
        roof.lineTo(right + 4f, top + 3f)
        roof.lineTo(left - 4f, top + 3f)
        roof.close()
        fillStroke(roof, Red, Ink, 2.5f)
        fillStroke(rectPath(left, top + 3f, right, bottom), Color.White, Ink, 2.5f)
        drawRect(Color(0xFF14161A), Offset(left, bottom - 5f), Size(right - left, 5f))
        word(measurer, labels.sign, (left + right) / 2f + 3f, (top + bottom) / 2f + 2f, 21f, Color(0xFF14161A), italic = true, spacing = 6f)
    }
    // Les bornes d'appel sur le trottoir, une à côté de chaque zone d'action du dehors.
    for (i in 0 until Drive.ZONE_COUNT) {
        if (!Drive.isAction(i) || Drive.zoneX(i) > g.DX) continue
        val kx = g.x(Drive.zoneX(i) - 24f, 0.07f)
        val ky = g.y(0.07f)
        val k = g.k(0.07f)
        val w = 15f * k
        val h = 50f * k
        fillStroke(rectPath(kx - w / 2f, ky - h, kx + w / 2f, ky), Paper.fade(0.97f), Ink, 2f)
        drawRect(Ink, Offset(kx - w / 2f + 3f * k, ky - h + 5f * k), Size(w - 6f * k, 10f * k))
        drawCircle(Ink, 3f * k, Offset(kx, ky - h + 25f * k))
        for (q in 0 until 3) drawRect(Ink, Offset(kx - w / 2f + 3f * k, ky - h + 34f * k + q * 3.4f * k), Size(w - 6f * k, 1.4f * k))
    }

    // Le bureau : le fond, le plafond, le sol.
    drawPath(polygon(g.DX, g.CT0, g.BX0, g.CT1, g.BX1, g.CT1, g.RX, g.CT0, g.RX, g.FY, g.DX, g.FY), tint)
    drawPath(polygon(g.DX, g.FY, g.BX0, g.FL1, g.BX1, g.FL1, g.RX, g.FY), Ink.fade(0.07f))
    val wall = Stroke(width = 3f, cap = StrokeCap.Square, join = StrokeJoin.Miter)
    val room = Path()
    room.addRect(androidx.compose.ui.geometry.Rect(g.BX0, g.CT1, g.BX1, g.FL1))
    room.moveTo(g.DX, g.CT0); room.lineTo(g.BX0, g.CT1)
    room.moveTo(g.RX, g.CT0); room.lineTo(g.BX1, g.CT1)
    room.moveTo(g.DX, g.FY); room.lineTo(g.BX0, g.FL1)
    room.moveTo(g.RX, g.FY); room.lineTo(g.BX1, g.FL1)
    room.moveTo(g.DX, g.CT0); room.lineTo(g.RX, g.CT0)
    room.moveTo(g.RX, g.CT0); room.lineTo(g.RX, g.FY)
    drawPath(room, Ink, style = wall)
    // La porte, découpée dans le devant du mur de gauche.
    run {
        val s = 0.09f
        val x0 = g.DX
        val fl0 = g.FY
        val x1 = g.DX + (g.BX0 - g.DX) * s
        val fl1 = g.FY + (g.FL1 - g.FY) * s
        val doorHeight = 84f
        val door = Path()
        door.moveTo(x0, fl0); door.lineTo(x0, fl0 - doorHeight)
        door.lineTo(x1, fl1 - doorHeight * (1f - s * 0.55f)); door.lineTo(x1, fl1)
        drawPath(door, Ink, style = Stroke(width = 3f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
    }
    drawLine(Ink, Offset(0f, g.FY), Offset(g.RX, g.FY), strokeWidth = 3f)

    // Les tapis (surélevés, arrêtés au bac le plus proche) : pieds, plateau, panneau de devant avec sa lettre.
    for (zone in Drive.BELT_ZONES) {
        val fx = Drive.zoneX(zone)
        val f0 = fx - g.HW
        val f1 = fx + g.HW
        for (s in floatArrayOf(Drive.S_NEAR + 0.02f, 0.42f, 0.72f, Drive.S_FAR - 0.02f)) {
            for (f in floatArrayOf(f0 + 4f, f1 - 4f)) {
                drawLine(Ink, g.at(f, s, g.HT - g.DECK), g.at(f, s, 0f), strokeWidth = 2f, cap = StrokeCap.Round)
            }
        }
        fillStroke(
            polygon(listOf(g.at(f0, Drive.S_NEAR, g.HT), g.at(f1, Drive.S_NEAR, g.HT), g.at(f1, Drive.S_FAR, g.HT), g.at(f0, Drive.S_FAR, g.HT))),
            Color(0xFF969C92).fade(0.95f), Ink, 2f
        )
        fillStroke(
            polygon(
                listOf(
                    g.at(f0, Drive.S_NEAR, g.HT), g.at(f1, Drive.S_NEAR, g.HT),
                    g.at(f1, Drive.S_NEAR, g.HT - g.DECK), g.at(f0, Drive.S_NEAR, g.HT - g.DECK)
                )
            ),
            Paper.fade(0.97f), Ink, 2f
        )
        // La lettre du tapis est peinte au sol, juste devant lui, bien grande et de la couleur de sa touche.
        val glyph = if (Drive.beltLabel(zone) == 0) GLYPH_A else GLYPH_B
        val mark = g.at(fx, 0.09f, 0f)
        driveGlyph(glyph, mark.x, mark.y - 14f, 30f, driveLetterColor(glyph))
    }
    // Les chariots : quatre par ligne, du fond au devant, chacun deux bacs l'un sur l'autre posés sur des roulettes.
    val rows = arrayOf(floatArrayOf(0.74f, 0.90f), floatArrayOf(0.54f, 0.70f), floatArrayOf(0.34f, 0.50f), floatArrayOf(0.14f, 0.30f))
    for (row in rows) {
        val a = row[0]
        val z = row[1]
        for (zone in Drive.LANE_ZONES) {
            val fx = Drive.zoneX(zone)
            val f0 = fx - g.HW
            val f1 = fx + g.HW
            val wheelsHeight = 6f
            fillStroke(polygon(listOf(g.at(f0, a, g.HT), g.at(f1, a, g.HT), g.at(f1, z, g.HT), g.at(f0, z, g.HT))), Color(0xFFB0B5AA).fade(0.97f), Ink, 2f)
            fillStroke(
                polygon(listOf(g.at(f0, z, g.HT), g.at(f1, z, g.HT), g.at(f1, z, wheelsHeight), g.at(f0, z, wheelsHeight))),
                Paper.fade(0.97f), Ink, 2f
            )
            val half = (g.HT + wheelsHeight) / 2f
            drawLine(Ink, g.at(f0, z, half), g.at(f1, z, half), strokeWidth = 2f)
            for (h in floatArrayOf(g.HT, half)) {
                drawLine(Ink, g.at(f0 - 1.5f, z, h), g.at(f1 + 1.5f, z, h), strokeWidth = 2.6f, cap = StrokeCap.Round)
            }
            for (f in floatArrayOf(f0 + 7f, f1 - 7f)) drawCircle(Ink, 2.8f * g.k(z), g.at(f, z, 2.6f))
        }
    }
}

// ------------------------------------------------------------------------------------------------------------ ce qui bouge

private fun DrawScope.mouse(x: Float, running: Boolean, clockMs: Float) {
    val step = running && floor(clockMs / 70f).toInt() % 2 == 1
    val gray = Color(0xFFB8B9B0)
    val width = 2f
    withTransform({ translate(x, Geo.H - 6f) }) {
        val tail = Path()
        tail.moveTo(9f, -4f)
        tail.cubicTo(20f, -4f, 22f, -12f, 28f, -9f)
        drawPath(tail, Ink, style = Stroke(width, cap = StrokeCap.Round))
        fillStroke(ovalPath(0f, -6f, 11f, 6.5f), gray, Ink, width)
        fillStroke(polygon(-8f, -8f, -17f, -5f, -8f, -3f), gray, Ink, width)
        drawCircle(gray, 3.4f, Offset(-5f, -12f))
        drawCircle(Ink, 3.4f, Offset(-5f, -12f), style = Stroke(width))
        drawCircle(Ink, 1.3f, Offset(-9f, -7f))
        drawCircle(Ink, 1.2f, Offset(-17f, -5f))
        val legs = Path()
        legs.moveTo(-3f, -1f); legs.lineTo(-3f + if (step) 3f else -3f, 2f)
        legs.moveTo(5f, -1f); legs.lineTo(5f + if (step) -3f else 3f, 2f)
        drawPath(legs, Ink, style = Stroke(width, cap = StrokeCap.Round))
    }
}

private fun DrawScope.phone(game: DriveGame) {
    val g = Geo
    val ringing = game.phone.mode == PHONE_RING
    val calling = game.phone.mode == PHONE_CALL
    val px = g.RX - 24f
    val py = g.H - 150f
    val shake = if (ringing) sin(game.clockMs / 22f) * 1.6f else 0f
    withTransform({ translate(px + shake, py) }) {
        drawRoundRect(Paper.fade(0.9f), Offset(-11f, -16f), Size(22f, 34f), CornerRadius(4f))
        drawRoundRect(Ink, Offset(-11f, -16f), Size(22f, 34f), CornerRadius(4f), style = Stroke(2.4f))
        for (r in 0 until 3) for (q in 0 until 3) drawRect(Ink, Offset(-6f + q * 5f, -4f + r * 5f), Size(2.4f, 2.4f))
        drawRect(Ink, Offset(-7f, -12f), Size(14f, 4f))
        if (!calling) {
            // Le combiné sur son socle.
            drawLine(Ink, Offset(-14f, -20f), Offset(14f, -20f), strokeWidth = 4f, cap = StrokeCap.Round)
            drawLine(Ink, Offset(-14f, -20f), Offset(-14f, -14f), strokeWidth = 2.4f, cap = StrokeCap.Round)
            drawLine(Ink, Offset(14f, -20f), Offset(14f, -14f), strokeWidth = 2.4f, cap = StrokeCap.Round)
        } else {
            // Le combiné décroché, pendu au bout de son fil.
            val cord = Path()
            cord.moveTo(0f, 18f)
            cord.cubicTo(-8f, 28f, 10f, 34f, -4f, 44f)
            drawPath(cord, Ink, style = Stroke(2.4f, cap = StrokeCap.Round))
            withTransform({ translate(-6f, 52f); rotate(0.5f * 180f / PI.toFloat(), pivot = Offset.Zero) }) {
                drawLine(Ink, Offset(-13f, 0f), Offset(13f, 0f), strokeWidth = 4f, cap = StrokeCap.Round)
            }
        }
    }
    if (ringing) {
        // Les notes de musique qui montent du téléphone.
        val now = game.clockMs / 1000f
        for (i in 0 until 4) {
            val u = (now * 0.8f + i / 4f) % 1f
            val x = px - 6f - u * 34f + sin(u * 9f + i) * 5f
            val y = py - 26f - u * 54f
            val color = Ink.fade(sin(u * PI.toFloat()) * 0.9f)
            withTransform({ rotate(-0.4f * 180f / PI.toFloat(), pivot = Offset(x, y)) }) {
                drawOval(color, Offset(x - 4.4f, y - 3.2f), Size(8.8f, 6.4f))
            }
            val stem = Path()
            stem.moveTo(x + 3.8f, y - 1f)
            stem.lineTo(x + 3.8f, y - 14f)
            if (i % 2 == 0) {
                stem.moveTo(x + 3.8f, y - 14f)
                stem.quadraticTo(x + 11f, y - 11f, x + 8f, y - 5f)
            }
            drawPath(stem, color, style = Stroke(2f, cap = StrokeCap.Round))
        }
    }
    if (calling) {
        // Ce qu'il faut presser pour raccrocher, et le temps qui reste.
        val bx = g.RX - 92f
        val by = g.H - 158f
        val left = max(0f, 1f - game.phone.elapsedMs / game.phone.limitMs)
        drawCircle(Paper.fade(0.95f), 22f, Offset(bx, by))
        drawCircle(Ink, 22f, Offset(bx, by), style = Stroke(3f))
        val askGlyph = glyphFor(game.phone.want)
        driveGlyph(askGlyph, bx, by, 24f, if (askGlyph == GLYPH_UP) Ink else driveLetterColor(askGlyph))
        drawRect(Ink.fade(0.2f), Offset(bx - 24f, by + 30f), Size(48f, 7f))
        drawRect(Ink, Offset(bx - 24f, by + 30f), Size(48f * left, 7f))
    }
}

private fun DrawScope.orderPanel(lane: DriveLane, labels: DriveLabels, measurer: TextMeasurer) {
    val g = Geo
    val bx = g.x(lane.x, 1f)
    val bw = 36f
    val bh = 114f
    val by = 70f
    val fill = if (lane.done) Color(0xFFF0A43A).fade(0.85f) else PaperLight.fade(0.93f)
    drawRoundRect(fill, Offset(bx - bw / 2f, by), Size(bw, bh), CornerRadius(4f))
    drawRoundRect(Ink, Offset(bx - bw / 2f, by), Size(bw, bh), CornerRadius(4f), style = Stroke(2f))
    val tagGlyph = if (lane.tag == 0) GLYPH_A else GLYPH_B
    drawCircle(driveLetterColor(tagGlyph), 9f, Offset(bx, by + 14f))
    driveGlyph(tagGlyph, bx, by + 14.5f, 10f, Color(0xFFF4F4EC))
    if (lane.done) {
        word(measurer, labels.ready, bx, by + bh / 2f + 14f, 10f, Ink)
        return
    }
    for (k in 0 until Drive.ORDER_SIZE) {
        val top = by + 30f + k * 27f
        driveProduct(lane.need[k], bx, top + 22f, 22f, if (lane.got[k]) 0.3f else 1f)
        if (lane.got[k]) {
            val tick = Path()
            tick.moveTo(bx - 8f, top + 11f)
            tick.lineTo(bx - 2f, top + 18f)
            tick.lineTo(bx + 9f, top + 3f)
            drawPath(tick, Ink, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private fun DrawScope.cars(game: DriveGame) {
    val g = Geo
    for ((index, bay) in game.bays.withIndex()) {
        if (bay.mode == CAR_WARN) {
            if (floor(bay.elapsedMs / 220f).toInt() % 2 == 0) {
                // Le panneau d'avertissement qui clignote : une voiture va surgir ici.
                val tx = bay.x
                val ty = g.H - 70f
                val sign = polygon(tx, ty - 24f, tx + 24f, ty + 18f, tx - 24f, ty + 18f)
                fillStroke(sign, Color(0xFFF6D65A).fade(0.97f), Ink, 3f)
                driveGlyph(GLYPH_BANG, tx, ty + 5f, 24f, Ink)
            }
        } else if (bay.mode != CAR_IDLE) {
            val x = bay.x
            val y = g.y(bay.depth)
            val k = g.lane(bay.depth)
            val fade = if (bay.mode == CAR_LEAVE) 1f - max(0f, (bay.elapsedMs / Drive.CAR_LEAVE_MS - 0.55f) / 0.45f) else 1f
            car(x, y, k, Color(CarColors[index % CarColors.size]), fade)
            if (bay.mode == CAR_WAIT) {
                // Ce que le conducteur demande (A ou B) et sa patience qui s'use.
                val bx = x
                val by = y - 66f * k - 22f
                val left = max(0f, 1f - bay.elapsedMs / bay.limitMs)
                drawCircle(PaperLight.fade(0.96f), 17f, Offset(bx, by))
                drawCircle(Ink, 17f, Offset(bx, by), style = Stroke(3f))
                val wantGlyph = if (bay.want == 0) GLYPH_A else GLYPH_B
                driveGlyph(wantGlyph, bx, by, 20f, driveLetterColor(wantGlyph))
                drawRect(Ink.fade(0.2f), Offset(bx - 20f, by + 22f), Size(40f, 6f))
                drawRect(Ink, Offset(bx - 20f, by + 22f), Size(40f * left, 6f))
            }
        }
    }
}

private fun DrawScope.belts(game: DriveGame) {
    val g = Geo
    val t = game.clockMs / 1000f
    for (belt in game.belts) {
        val f0 = belt.x - g.HW
        val f1 = belt.x + g.HW
        // Les barres du tapis qui avancent vers les bacs.
        for (k in 0 until 14) {
            val s = Drive.S_NEAR + (Drive.S_FAR - Drive.S_NEAR) * (1f - ((k / 14f + t * 0.18f) % 1f))
            drawLine(Ink, g.at(f0, s, g.HT), g.at(f1, s, g.HT), strokeWidth = 1.2f)
        }
    }
    for (belt in game.belts) {
        for (item in belt.items) {
            val p = g.at(belt.x, item.depth, g.HT)
            driveProduct(item.product, p.x, p.y, 26f * g.k(item.depth))
        }
    }
    for (fall in game.falls) {
        val u = min(1f, fall.elapsedMs / fall.durationMs)
        val fx = fall.fromX + (fall.toX - fall.fromX) * u
        val s = fall.fromDepth + (fall.toDepth - fall.fromDepth) * u
        val h: Float
        val a: Float
        if (fall.lost) {
            h = g.HT * (1f - u * u)
            a = 1f - u * 0.8f
        } else {
            h = g.HT + 10f * sin(u * PI.toFloat()) - 16f * u * u
            a = if (u > 0.85f) (1f - u) / 0.15f else 1f
        }
        val p = g.at(fx, s, h)
        driveProduct(fall.product, p.x, p.y, 26f * g.k(s), a)
    }
}

private fun DrawScope.hero(game: DriveGame, sprites: DriveSprites) {
    if (game.hurtMs > 0f && floor(game.hurtMs / 80f).toInt() % 2 != 0) return // il clignote après un choc
    val image = if (Drive.isAction(game.pos)) sprites.front else if (game.facing > 0) sprites.right else sprites.left
    if (image == null) return
    val w = (image.width * Geo.SPRITE_SCALE).roundToInt()
    val h = (image.height * Geo.SPRITE_SCALE).roundToInt()
    val left = (Drive.zoneX(game.pos) - w / 2f).roundToInt()
    val top = (Geo.H - h - game.lift).roundToInt()
    drawImage(
        image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(left, top),
        dstSize = IntSize(w, h),
        filterQuality = FilterQuality.Medium
    )
}

/** Tout ce qui change pendant la partie, dans le repère de 720 x 405 de l'écran. */
internal fun DrawScope.drawDriveLive(game: DriveGame, sprites: DriveSprites, labels: DriveLabels, measurer: TextMeasurer) {
    cars(game)
    for (lane in game.lanes) orderPanel(lane, labels, measurer)
    belts(game)
    phone(game)
    // Les compteurs, dans le dehors vide : points, commandes préparées, erreurs, puis les commandes prêtes à livrer.
    counter(measurer, game.points, labels.points, 24f)
    counter(measurer, game.orders, labels.orders, 82f)
    counter(measurer, game.errors, labels.errors, 140f)
    word(measurer, labels.stock, 12f, 196f, 12f, Ink.fade(0.65f), leftAligned = true)
    driveGlyph(GLYPH_A, 22f, 220f, 16f, driveLetterColor(GLYPH_A))
    digit(game.stock[0], 36f, 211f, 0.9f, 0.55f)
    driveGlyph(GLYPH_B, 76f, 220f, 16f, driveLetterColor(GLYPH_B))
    digit(game.stock[1], 90f, 211f, 0.9f, 0.55f)
    if (game.mouse.mode != MOUSE_NONE) mouse(game.mouse.x, game.mouse.mode == MOUSE_RUN, game.clockMs)
    hero(game, sprites)
    if (game.flash > 0f) drawRect(Ink.fade(game.flash * 0.22f), size = Size(Geo.W, Geo.H))
}
