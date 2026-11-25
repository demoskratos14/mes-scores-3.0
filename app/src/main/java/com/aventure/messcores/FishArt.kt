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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Les dessins de l'écran de Poissonnerie, dans le repère de l'écran (720 x 405), le même que celui de Drive : le décor qui ne
 * bouge pas (le mur carrelé, l'enseigne, l'étalage et ses six bacs) se dessine une fois avec [drawFishScene] ; tout ce qui bouge
 * (compteurs, tapis et caisses, poissons dans les bacs, poissonnier, client, balance) avec [drawFishLive]. Le poissonnier est le
 * personnage de Drive ([DriveSprites]).
 */
internal class FishLabels(
    val time: String,
    val score: String,
    val sign: String,
    val stageStock: String,
    val stageService: String,
    val opening: String,
    val no: String,
    val gram: String,
    /** Le nom de chaque poisson, dans l'ordre de l'étalage. */
    val species: List<String>,
    /** Le texte de chaque verdict, indexé par FISH_VERDICT_* (la case 0 est vide). */
    val verdicts: List<String>
)

private object FishLook {
    val Ink = Color(0xFF20241F)
    val Red = Color(0xFFE2001A)
    val Blue = Color(0xFF2B6CB0)
    val Ice = Color(0xFFDDEEF4)
    val Metal = Color(0xFFB9C4C9)
    val White = Color(0xFFF8FBFC)
    val Wood = Color(0xFF8A6238)
    val WoodDark = Color(0xFF5E4125)
    val Skin = Color(0xFFD99B79)
    val Green = Color(0xFF2E9E4F)
    val GreenDark = Color(0xFF176B33)
    val Amber = Color(0xFFF0B429)

    // Le tapis.
    const val BELT_TOP = 150f
    const val BELT_H = 16f
    const val CRATE_Y = 130f

    // Les bacs de l'étalage.
    const val BIN_X0 = 18f
    const val BIN_STEP = 116f
    const val BIN_W = 104f
    const val BIN_Y = 270f
    const val BIN_H = 70f
    const val TRAY_Y = 262f

    // Le comptoir : les pieds du poissonnier et du client touchent le haut de l'étalage.
    const val FEET_Y = TRAY_Y
    const val HERO_X = 120f
    const val HERO_SCALE = 1.6f
    const val CUSTOMER_X = 600f

    // La balance.
    const val GAUGE_X0 = 60f
    const val GAUGE_X1 = 660f
    const val GAUGE_Y = 168f
    const val GAUGE_H = 28f
}

private fun Color.fade(a: Float): Color = copy(alpha = alpha * a)

private fun binCenterX(bin: Int): Float = FishLook.BIN_X0 + bin * FishLook.BIN_STEP + FishLook.BIN_W / 2f

/** Un texte centré (ou aligné) en ([x], [y]), de [px] pixels de l'écran de hauteur de police ; [align] : -1 à gauche, 0 centré, 1 à droite. */
private fun DrawScope.fishWord(
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

// ------------------------------------------------------------------------------------------------------------ les poissons

/** Un poisson de profil, tête à droite, de 60 unités de long, dessiné autour de l'origine. */
private fun DrawScope.swimmer(species: Int) {
    val ink = FishLook.Ink
    val body: Color
    val belly: Color
    val h: Float
    when (species) {
        0 -> { body = Color(0xFF8FA6B5); belly = Color(0xFFE6EEF2); h = 9f }
        1 -> { body = Color(0xFF2F7A86); belly = Color(0xFFDCE8E4); h = 11f }
        2 -> { body = Color(0xFFE9806A); belly = Color(0xFFF6C0AE); h = 14f }
        3 -> { body = Color(0xFF9EA8AE); belly = Color(0xFFE4E8EA); h = 14f }
        else -> { body = Color(0xFF2C4A7C); belly = Color(0xFFB9C6D8); h = 16f }
    }
    // La queue, puis le corps, le ventre clair et la nageoire du dos.
    val tail = Path()
    tail.moveTo(-24f, 0f)
    tail.lineTo(-45f, -h * 0.95f)
    tail.lineTo(-40f, 0f)
    tail.lineTo(-45f, h * 0.95f)
    tail.close()
    drawPath(tail, body)
    drawPath(tail, ink, style = Stroke(width = 2.2f, join = StrokeJoin.Round))
    drawOval(body, Offset(-30f, -h), Size(60f, h * 2f))
    drawOval(belly.fade(0.85f), Offset(-23f, h * 0.05f), Size(46f, h * 0.8f))
    val fin = Path()
    fin.moveTo(-8f, -h + 1.5f)
    fin.lineTo(4f, -h - 9f)
    fin.lineTo(14f, -h + 2f)
    fin.close()
    drawPath(fin, body)
    drawPath(fin, ink, style = Stroke(width = 2f, join = StrokeJoin.Round))
    drawOval(ink, Offset(-30f, -h), Size(60f, h * 2f), style = Stroke(width = 2.4f))
    // Ce qui distingue chaque poisson.
    when (species) {
        1 -> for (k in 0 until 4) {
            drawLine(ink.fade(0.75f), Offset(-16f + k * 9f, -h + 2.5f), Offset(-12f + k * 9f, -h * 0.15f), strokeWidth = 2f, cap = StrokeCap.Round)
        }
        2 -> for (k in 0 until 3) {
            drawCircle(Color(0xFFB8503C), 1.8f, Offset(-12f + k * 11f, -h * 0.35f + (k % 2) * 4f))
        }
        3 -> drawCircle(Color(0xFFE0B22A), 4.5f, Offset(9f, -h * 0.72f))
        4 -> {
            drawCircle(Color(0xFFF2D13B), 2.8f, Offset(-27f, -3f))
            drawCircle(Color(0xFFF2D13B), 2.8f, Offset(-27f, 3f))
        }
    }
    drawCircle(Color.White, 4f, Offset(18f, -h * 0.2f))
    drawCircle(ink, 2f, Offset(19f, -h * 0.2f))
    drawLine(ink, Offset(29f, 2f), Offset(24f, 3f), strokeWidth = 1.8f, cap = StrokeCap.Round)
}

/** Un crabe vu de dessus, de 60 unités de large environ, dessiné autour de l'origine. */
private fun DrawScope.crab() {
    val ink = FishLook.Ink
    val shell = Color(0xFFD9432F)
    for (side in floatArrayOf(-1f, 1f)) {
        for (k in 0 until 3) {
            drawLine(ink, Offset(side * 10f, 4f + k * 4f), Offset(side * (23f + k * 2f), 10f + k * 5f), strokeWidth = 2.6f, cap = StrokeCap.Round)
        }
        drawLine(ink, Offset(side * 17f, -2f), Offset(side * 26f, -13f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawCircle(shell, 6.5f, Offset(side * 28f, -18f))
        drawCircle(ink, 6.5f, Offset(side * 28f, -18f), style = Stroke(width = 2.2f))
        drawLine(ink, Offset(side * 28f, -18f), Offset(side * 31f, -25f), strokeWidth = 2.2f, cap = StrokeCap.Round)
    }
    drawOval(shell, Offset(-22f, -12f), Size(44f, 27f))
    drawOval(ink, Offset(-22f, -12f), Size(44f, 27f), style = Stroke(width = 2.4f))
    for (side in floatArrayOf(-1f, 1f)) {
        drawLine(ink, Offset(side * 6f, -11f), Offset(side * 7f, -17f), strokeWidth = 2f)
        drawCircle(Color.White, 3.2f, Offset(side * 7f, -19f))
        drawCircle(ink, 1.5f, Offset(side * 7f, -19f))
    }
    drawArc(ink, 20f, 140f, false, Offset(-7f, -2f), Size(14f, 9f), style = Stroke(width = 1.8f, cap = StrokeCap.Round))
}

/** Un poisson (voir [swimmer] et [crab]) centré en ([cx], [cy]), à l'échelle [scale]. */
private fun DrawScope.fishShape(species: Int, cx: Float, cy: Float, scale: Float) {
    withTransform({
        translate(cx, cy)
        scale(scale, scale, Offset.Zero)
    }) {
        if (species == 5) crab() else swimmer(species)
    }
}

// ------------------------------------------------------------------------------------------------------------ le décor

/** Le décor de l'écran, qui ne change jamais : un mur carrelé, l'enseigne, et l'étalage avec ses six bacs et leurs noms. */
internal fun DrawScope.drawFishScene(labels: FishLabels, measurer: TextMeasurer) {
    val w = Fish.WIDTH
    val h = Fish.HEIGHT
    val ink = FishLook.Ink
    // Le mur carrelé.
    drawRect(
        brush = Brush.verticalGradient(0f to Color(0xFFEAF4F7), 1f to Color(0xFFCFE3EA), startY = 0f, endY = FishLook.TRAY_Y),
        size = Size(w, FishLook.TRAY_Y)
    )
    var x = 0f
    while (x < w) {
        drawLine(ink.fade(0.07f), Offset(x, 0f), Offset(x, FishLook.TRAY_Y), strokeWidth = 1.5f)
        x += 48f
    }
    var y = 0f
    while (y < FishLook.TRAY_Y) {
        drawLine(ink.fade(0.07f), Offset(0f, y), Offset(w, y), strokeWidth = 1.5f)
        y += 36f
    }

    // L'enseigne, au milieu du mur.
    drawRoundRect(Color.White, Offset(w / 2f - 80f, 10f), Size(160f, 42f), CornerRadius(8f))
    drawRoundRect(ink, Offset(w / 2f - 80f, 10f), Size(160f, 42f), CornerRadius(8f), style = Stroke(width = 3f))
    drawRect(FishLook.Blue, Offset(w / 2f - 80f, 44f), Size(160f, 4f))
    fishWord(measurer, labels.sign, w / 2f, 27f, 24f, FishLook.Blue, italic = true, spacing = 4f)

    // L'étalage : le dessus en inox, les six bacs de glace, puis la face blanche avec le nom de chaque poisson.
    drawRect(FishLook.Metal, Offset(0f, FishLook.TRAY_Y), Size(w, 84f))
    drawRect(ink, Offset(0f, FishLook.TRAY_Y), Size(w, 3f))
    for (bin in 0 until Fish.SPECIES) {
        val left = FishLook.BIN_X0 + bin * FishLook.BIN_STEP
        drawRoundRect(FishLook.Ice, Offset(left, FishLook.BIN_Y), Size(FishLook.BIN_W, FishLook.BIN_H), CornerRadius(8f))
        drawRoundRect(ink, Offset(left, FishLook.BIN_Y), Size(FishLook.BIN_W, FishLook.BIN_H), CornerRadius(8f), style = Stroke(width = 2.5f))
    }
    drawRect(FishLook.White, Offset(0f, 346f), Size(w, h - 346f))
    drawRect(FishLook.Blue, Offset(0f, 346f), Size(w, 7f))
    drawRect(ink, Offset(0f, 346f), Size(w, 2.5f))
    for (bin in 0 until Fish.SPECIES) {
        fishWord(measurer, labels.species.getOrElse(bin) { "" }, binCenterX(bin), 378f, 16f, ink)
    }
}

// ------------------------------------------------------------------------------------------------------------ ce qui bouge

/** Le temps qui reste, en « m:ss » ; il ne tombe pas à zéro tant que la dernière seconde n'est pas finie. */
internal fun fishClockText(timeLeftMs: Float): String {
    val seconds = max(0, ceil(timeLeftMs / 1000f).toInt())
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

/** Le tapis roulant d'approvisionnement : la bande qui défile, son bout en rouge où les caisses tombent, et les caisses. */
private fun DrawScope.belt(game: FishGame) {
    val ink = FishLook.Ink
    val top = FishLook.BELT_TOP
    val speed = 0.12f // vitesse du dessin de la bande, en pixels par milliseconde
    drawRect(Color(0xFF4A5057), Offset(0f, top), Size(Fish.WIDTH, FishLook.BELT_H))
    drawRect(ink, Offset(0f, top), Size(Fish.WIDTH, FishLook.BELT_H), style = Stroke(width = 2.5f))
    var x = (game.clockMs * speed) % 30f - 30f
    while (x < Fish.WIDTH) {
        drawLine(Color.White.fade(0.25f), Offset(x, top + 3f), Offset(x + 10f, top + FishLook.BELT_H - 3f), strokeWidth = 2f)
        x += 30f
    }
    // Le bout du tapis : une caisse qui le dépasse tombe.
    drawRect(FishLook.Red.fade(0.8f), Offset(Fish.BELT_END, top - 52f), Size(5f, 52f + FishLook.BELT_H))
    val head = game.head()
    for (crate in game.crates) {
        drawCrate(crate.x, FishLook.CRATE_Y, crate.species)
    }
    if (head != null) {
        // Une flèche rouge au-dessus de la caisse à ranger.
        val bob = sin(game.clockMs / 120f) * 3f
        val arrow = Path()
        arrow.moveTo(head.x, FishLook.CRATE_Y - 30f + bob)
        arrow.lineTo(head.x - 9f, FishLook.CRATE_Y - 44f + bob)
        arrow.lineTo(head.x + 9f, FishLook.CRATE_Y - 44f + bob)
        arrow.close()
        drawPath(arrow, FishLook.Red)
        drawPath(arrow, ink, style = Stroke(width = 2f, join = StrokeJoin.Round))
    }
}

/** Une caisse en bois centrée en ([cx], [cy]) avec son poisson qui dépasse. */
private fun DrawScope.drawCrate(cx: Float, cy: Float, species: Int) {
    val ink = FishLook.Ink
    drawRoundRect(FishLook.WoodDark, Offset(cx - 31f, cy - 20f), Size(62f, 40f), CornerRadius(4f))
    fishShape(species, cx, cy - 6f, 0.6f)
    drawRoundRect(FishLook.Wood, Offset(cx - 31f, cy + 2f), Size(62f, 18f), CornerRadius(4f))
    drawLine(ink.fade(0.55f), Offset(cx - 31f, cy + 11f), Offset(cx + 31f, cy + 11f), strokeWidth = 1.6f)
    drawRoundRect(ink, Offset(cx - 31f, cy - 20f), Size(62f, 40f), CornerRadius(4f), style = Stroke(width = 2.4f))
}

/** Les poissons rangés dans chaque bac (un par kilo), le bac choisi cerné de rouge, les bacs vides grisés pendant le service. */
private fun DrawScope.bins(game: FishGame) {
    val inService = game.stage != FISH_STAGE_STOCK
    for (bin in 0 until Fish.SPECIES) {
        val left = FishLook.BIN_X0 + bin * FishLook.BIN_STEP
        val kilos = min(4, ceil(game.stock[bin] / 1000f).toInt())
        for (n in 0 until kilos) {
            val col = n % 2
            val row = n / 2
            fishShape(bin, left + 30f + col * 42f, FishLook.BIN_Y + 22f + row * 28f, 0.55f)
        }
        if (inService && game.stock[bin] <= 0) {
            drawRoundRect(Color(0xFF8A949A).fade(0.55f), Offset(left, FishLook.BIN_Y), Size(FishLook.BIN_W, FishLook.BIN_H), CornerRadius(8f))
        }
        if (game.placedBin == bin && game.placeMs > 0f) {
            val u = game.placeMs / Fish.PLACE_MS
            drawRoundRect(
                FishLook.Green.fade(u),
                Offset(left - 3f, FishLook.BIN_Y - 3f),
                Size(FishLook.BIN_W + 6f, FishLook.BIN_H + 6f),
                CornerRadius(10f),
                style = Stroke(width = 5f)
            )
        }
    }
    if (game.over) return
    val left = FishLook.BIN_X0 + game.cursor * FishLook.BIN_STEP
    val pulse = 0.7f + 0.3f * sin(game.clockMs / 140f)
    drawRoundRect(
        FishLook.Red.fade(pulse),
        Offset(left - 4f, FishLook.BIN_Y - 4f),
        Size(FishLook.BIN_W + 8f, FishLook.BIN_H + 8f),
        CornerRadius(11f),
        style = Stroke(width = 5f)
    )
    val cx = binCenterX(game.cursor)
    val arrow = Path()
    arrow.moveTo(cx, FishLook.TRAY_Y - 2f)
    arrow.lineTo(cx - 10f, FishLook.TRAY_Y - 16f)
    arrow.lineTo(cx + 10f, FishLook.TRAY_Y - 16f)
    arrow.close()
    drawPath(arrow, FishLook.Red)
    drawPath(arrow, FishLook.Ink, style = Stroke(width = 2f, join = StrokeJoin.Round))
}

/** Le poissonnier (le personnage de Drive, de face) derrière l'étalage : il saute de joie quand la pesée est réussie. */
private fun DrawScope.fishmonger(game: FishGame, sprites: DriveSprites) {
    val image = sprites.front ?: return
    val w = (image.width * FishLook.HERO_SCALE).roundToInt()
    val h = (image.height * FishLook.HERO_SCALE).roundToInt()
    val happy = game.step == FISH_STEP_RESULT && (game.verdict == FISH_VERDICT_PERFECT || game.verdict == FISH_VERDICT_GOOD)
    val hop = if (happy) abs(sin(game.resultMs / 110f)) * 8f else 0f
    val shake = if (game.blockedMs > 0f) sin(game.clockMs / 25f) * 3f else 0f
    val cx = FishLook.HERO_X + shake
    val top = FishLook.FEET_Y - h - hop
    drawOval(Color.Black.copy(alpha = 0.2f), Offset(cx - w * 0.6f, FishLook.FEET_Y - 7f), Size(w * 1.2f, 12f))
    drawImage(
        image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset((cx - w / 2f).roundToInt(), top.roundToInt()),
        dstSize = IntSize(w, h),
        filterQuality = FilterQuality.None
    )
}

/** Un client debout au comptoir en [x] ; [mood] : 1 content, -1 mécontent, 0 neutre. */
private fun DrawScope.customer(look: Int, x: Float, mood: Int) {
    val ink = FishLook.Ink
    val coats = intArrayOf(0xFF4A7BB7.toInt(), 0xFF9B4F8E.toInt(), 0xFFB9783A.toInt(), 0xFF4F9A6B.toInt())
    val hairs = intArrayOf(0xFF3A2A1E.toInt(), 0xFFD9B04A.toInt(), 0xFF8A8A8A.toInt(), 0xFF1E1E22.toInt())
    val coat = Color(coats[look.coerceIn(0, 3)])
    val hair = Color(hairs[look.coerceIn(0, 3)])
    val feet = FishLook.FEET_Y
    drawOval(Color.Black.copy(alpha = 0.2f), Offset(x - 30f, feet - 7f), Size(60f, 12f))
    // Les jambes, le buste et les bras.
    drawRoundRect(Color(0xFF30343C), Offset(x - 16f, feet - 28f), Size(12f, 28f), CornerRadius(3f))
    drawRoundRect(Color(0xFF30343C), Offset(x + 4f, feet - 28f), Size(12f, 28f), CornerRadius(3f))
    drawRoundRect(coat, Offset(x - 24f, feet - 80f), Size(48f, 56f), CornerRadius(12f))
    drawRoundRect(ink, Offset(x - 24f, feet - 80f), Size(48f, 56f), CornerRadius(12f), style = Stroke(width = 2.4f))
    drawRoundRect(coat, Offset(x - 33f, feet - 76f), Size(11f, 40f), CornerRadius(5f))
    drawRoundRect(coat, Offset(x + 22f, feet - 76f), Size(11f, 40f), CornerRadius(5f))
    // La tête, les cheveux ou le chapeau, les lunettes.
    val headY = feet - 98f
    drawCircle(FishLook.Skin, 19f, Offset(x, headY))
    drawArc(hair, 180f, 180f, true, Offset(x - 19f, headY - 19f), Size(38f, 30f))
    if (look == 2) {
        drawRoundRect(Color(0xFF7A5B2E), Offset(x - 24f, headY - 24f), Size(48f, 8f), CornerRadius(3f))
        drawRoundRect(Color(0xFF7A5B2E), Offset(x - 14f, headY - 36f), Size(28f, 14f), CornerRadius(4f))
    }
    drawCircle(ink, 19f, Offset(x, headY), style = Stroke(width = 2.4f))
    drawCircle(ink, 2.2f, Offset(x - 7f, headY - 2f))
    drawCircle(ink, 2.2f, Offset(x + 7f, headY - 2f))
    if (look == 3) {
        drawCircle(ink, 5.5f, Offset(x - 7f, headY - 2f), style = Stroke(width = 1.8f))
        drawCircle(ink, 5.5f, Offset(x + 7f, headY - 2f), style = Stroke(width = 1.8f))
    }
    when {
        mood > 0 -> drawArc(ink, 20f, 140f, false, Offset(x - 8f, headY + 2f), Size(16f, 12f), style = Stroke(width = 2.2f, cap = StrokeCap.Round))
        mood < 0 -> drawArc(ink, 200f, 140f, false, Offset(x - 8f, headY + 8f), Size(16f, 12f), style = Stroke(width = 2.2f, cap = StrokeCap.Round))
        else -> drawLine(ink, Offset(x - 6f, headY + 10f), Offset(x + 6f, headY + 10f), strokeWidth = 2.2f, cap = StrokeCap.Round)
    }
}

/** La bulle du client : le poisson, son nom, le poids demandé, et la barre de patience qui se vide. */
private fun DrawScope.orderBubble(game: FishGame, labels: FishLabels, measurer: TextMeasurer, alpha: Float) {
    val ink = FishLook.Ink
    val left = 498f
    val top = 66f
    val bubble = Path()
    bubble.moveTo(FishLook.CUSTOMER_X - 10f, top + 64f)
    bubble.lineTo(FishLook.CUSTOMER_X, top + 76f)
    bubble.lineTo(FishLook.CUSTOMER_X + 10f, top + 64f)
    bubble.close()
    drawRoundRect(Color.White.fade(alpha), Offset(left, top), Size(204f, 66f), CornerRadius(12f))
    drawPath(bubble, Color.White.fade(alpha))
    drawRoundRect(ink.fade(alpha), Offset(left, top), Size(204f, 66f), CornerRadius(12f), style = Stroke(width = 3f))
    drawLine(Color.White.fade(alpha), Offset(FishLook.CUSTOMER_X - 9f, top + 65f), Offset(FishLook.CUSTOMER_X + 9f, top + 65f), strokeWidth = 4f)
    drawPath(bubble, ink.fade(alpha), style = Stroke(width = 3f, join = StrokeJoin.Round))
    fishShape(game.customerSpecies, left + 36f, top + 28f, 0.6f)
    fishWord(measurer, labels.species.getOrElse(game.customerSpecies) { "" }, left + 72f, top + 18f, 15f, ink.fade(0.7f * alpha), align = -1)
    fishWord(measurer, "${game.wantG} ${labels.gram}", left + 72f, top + 40f, 24f, ink.fade(alpha), align = -1)
    // La patience : la barre passe du vert au rouge en se vidant.
    val share = (game.patienceMs / game.patienceMaxMs).coerceIn(0f, 1f)
    drawRoundRect(ink.fade(0.15f * alpha), Offset(left + 12f, top + 55f), Size(180f, 6f), CornerRadius(3f))
    val bar = if (share > 0.5f) FishLook.Green else if (share > 0.25f) FishLook.Amber else FishLook.Red
    drawRoundRect(bar.fade(alpha), Offset(left + 12f, top + 55f), Size(180f * share, 6f), CornerRadius(3f))
}

/** La balance : la zone du bon poids (parfait, bon, limite), les graduations, le curseur qui va et vient et le poids qu'il indique. */
private fun DrawScope.gauge(game: FishGame, labels: FishLabels, measurer: TextMeasurer) {
    val ink = FishLook.Ink
    val x0 = FishLook.GAUGE_X0
    val x1 = FishLook.GAUGE_X1
    val y = FishLook.GAUGE_Y
    val gh = FishLook.GAUGE_H
    val perG = (x1 - x0) / Fish.GAUGE_MAX_G
    // Le panneau.
    drawRoundRect(Color.White.fade(0.96f), Offset(30f, 74f), Size(660f, 166f), CornerRadius(14f))
    drawRoundRect(ink, Offset(30f, 74f), Size(660f, 166f), CornerRadius(14f), style = Stroke(width = 3f))
    // Ce qui est demandé, à gauche ; le poids indiqué par le curseur, à droite.
    fishShape(game.customerSpecies, 76f, 108f, 0.7f)
    fishWord(measurer, labels.species.getOrElse(game.customerSpecies) { "" }, 116f, 98f, 15f, ink.fade(0.7f), align = -1)
    fishWord(measurer, "${game.wantG} ${labels.gram}", 116f, 122f, 24f, FishLook.GreenDark, align = -1)
    fishWord(measurer, "${game.currentWeightG} ${labels.gram}", 662f, 112f, 34f, ink, align = 1)
    // La piste et les trois zones, de la plus large à la plus étroite.
    drawRoundRect(Color(0xFFD4DADD), Offset(x0, y), Size(x1 - x0, gh), CornerRadius(6f))
    val tolerance = game.toleranceG
    fun zone(ratio: Float, color: Color) {
        val from = (game.wantG - tolerance * ratio).coerceAtLeast(0f)
        val to = (game.wantG + tolerance * ratio).coerceAtMost(Fish.GAUGE_MAX_G.toFloat())
        drawRect(color, Offset(x0 + from * perG, y), Size((to - from) * perG, gh))
    }
    zone(Fish.OFF_RATIO, FishLook.Amber.fade(0.55f))
    zone(1f, FishLook.Green)
    zone(Fish.PERFECT_RATIO, FishLook.GreenDark)
    drawRoundRect(ink, Offset(x0, y), Size(x1 - x0, gh), CornerRadius(6f), style = Stroke(width = 2.4f))
    // Les graduations : un trait tous les 250 g, un nombre tous les 500 g.
    for (g in 0..Fish.GAUGE_MAX_G step 250) {
        val gx = x0 + g * perG
        drawLine(ink, Offset(gx, y + gh), Offset(gx, y + gh + if (g % 500 == 0) 10f else 6f), strokeWidth = 2f)
        if (g % 500 == 0) fishWord(measurer, g.toString(), gx, y + gh + 22f, 14f, ink.fade(0.7f))
    }
    // Le curseur.
    val cx = x0 + game.gaugePos * (x1 - x0)
    val stopped = game.step == FISH_STEP_RESULT
    val color = if (stopped) ink else FishLook.Red
    drawLine(color, Offset(cx, y - 14f), Offset(cx, y + gh + 6f), strokeWidth = 4f, cap = StrokeCap.Round)
    val head = Path()
    head.moveTo(cx, y - 2f)
    head.lineTo(cx - 9f, y - 18f)
    head.lineTo(cx + 9f, y - 18f)
    head.close()
    drawPath(head, color)
}

/** Tout ce qui change pendant la partie, dans le repère de 720 x 405 de l'écran. */
internal fun DrawScope.drawFishLive(game: FishGame, sprites: DriveSprites, labels: FishLabels, measurer: TextMeasurer) {
    val ink = FishLook.Ink
    // Les compteurs : le temps à gauche, le score à droite, le titre de l'étape sous l'enseigne.
    val urgent = !game.over && game.stage != FISH_STAGE_OPENING && game.stageLeftMs <= 10_000f
    hud(measurer, labels, game, urgent)
    val title = if (game.stage == FISH_STAGE_STOCK) labels.stageStock else labels.stageService
    if (!game.over && game.stage != FISH_STAGE_OPENING) {
        fishWord(measurer, title, Fish.WIDTH / 2f, 66f, 14f, ink.fade(0.6f))
    }

    when (game.stage) {
        FISH_STAGE_STOCK -> belt(game)
        FISH_STAGE_SERVICE -> serviceScene(game, sprites, labels, measurer)
        FISH_STAGE_OPENING -> {
            fishmonger(game, sprites)
            val pulse = 1f + 0.06f * sin(game.clockMs / 110f)
            fishWord(measurer, labels.opening, Fish.WIDTH / 2f + 60f, 150f, 40f * pulse, FishLook.Blue, italic = true, spacing = 3f)
        }
        else -> fishmonger(game, sprites)
    }
    bins(game)

    val shade = min(0.25f, game.blockedMs / Fish.BLOCKED_MS * 0.25f)
    if (shade > 0f) {
        drawRect(FishLook.Red.fade(shade), size = Size(Fish.WIDTH, Fish.HEIGHT))
        fishWord(measurer, labels.no, Fish.WIDTH / 2f, 200f, 44f, FishLook.Red, italic = true)
    }
}

/** Les compteurs du haut de l'écran. */
private fun DrawScope.hud(measurer: TextMeasurer, labels: FishLabels, game: FishGame, urgent: Boolean) {
    val ink = FishLook.Ink
    val left = if (game.stage == FISH_STAGE_OPENING) Fish.SERVICE_MS else game.stageLeftMs
    fishWord(measurer, labels.time, 16f, 24f, 14f, ink.fade(0.6f), align = -1)
    fishWord(measurer, fishClockText(if (game.over) 0f else left), 16f, 62f, 46f, if (urgent) FishLook.Red else ink, align = -1)
    fishWord(measurer, labels.score, Fish.WIDTH - 16f, 24f, 14f, ink.fade(0.6f), align = 1)
    fishWord(measurer, game.score.toString(), Fish.WIDTH - 16f, 62f, 46f, ink, align = 1)
}

/** Le service : le poissonnier, le client avec sa bulle, puis la balance et le verdict de la pesée. */
private fun DrawScope.serviceScene(game: FishGame, sprites: DriveSprites, labels: FishLabels, measurer: TextMeasurer) {
    fishmonger(game, sprites)
    if (game.customerSpecies < 0) return
    val arrive = (game.arriveMs / Fish.ARRIVE_MS).coerceIn(0f, 1f)
    val resulting = game.step == FISH_STEP_RESULT
    val mood = when {
        !resulting -> 0
        game.verdict == FISH_VERDICT_PERFECT || game.verdict == FISH_VERDICT_GOOD -> 1
        else -> -1
    }
    customer(game.customerLook, FishLook.CUSTOMER_X + arrive * 170f, mood)
    orderBubble(game, labels, measurer, 1f - arrive)
    val weighed = game.step == FISH_STEP_WEIGH ||
        (resulting && game.verdict >= FISH_VERDICT_PERFECT && game.verdict <= FISH_VERDICT_MISS)
    if (weighed) gauge(game, labels, measurer)
    if (resulting) {
        // Le verdict et les points gagnés, au milieu de l'écran.
        val good = game.verdict == FISH_VERDICT_PERFECT || game.verdict == FISH_VERDICT_GOOD
        val color = when {
            good -> FishLook.GreenDark
            game.verdict == FISH_VERDICT_OFF -> Color(0xFFB87708)
            else -> FishLook.Red
        }
        val y = if (weighed) 108f else 170f
        val rise = (1f - game.resultMs / Fish.RESULT_MS) * 8f
        fishWord(measurer, labels.verdicts.getOrElse(game.verdict) { "" }, Fish.WIDTH / 2f, y - rise, 32f, color, italic = true)
        val points = game.lastPoints
        fishWord(measurer, if (points >= 0) "+$points" else points.toString(), Fish.WIDTH / 2f, y + 30f - rise, 24f, color)
    }
}
