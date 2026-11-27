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
 * Les dessins de l'écran de Mise en rayon, dans le repère de l'écran (720 x 405), le même que celui de Drive et de Poissonnerie.
 * La scène : le mur d'un magasin, la réserve en haut (d'où tombent les produits), un rayon à chaque bord et, au milieu, le
 * magasinier (le personnage de Drive, [DriveSprites]) qui tient son plateau. Le décor qui ne bouge pas (mur, sol, réserve, cadres
 * des rayons) se dessine une fois avec [drawShelfScene] ; tout ce qui bouge (compteurs, niveau, produits, plateau, rayons garnis)
 * avec [drawShelfLive].
 */
internal class ShelfLabels(
    val time: String,
    val score: String,
    val breaks: String,
    val pile: String,
    val shelf: String,
    val capsized: String
)

private object ShelfLook {
    val Ink = Color(0xFF20241F)
    val Red = Color(0xFFE2001A)
    val Green = Color(0xFF2E9E4F)
    val GreenDark = Color(0xFF176B33)
    val Amber = Color(0xFFF0B429)
    val Steel = Color(0xFFB9C4C9)
    val SteelDark = Color(0xFF7D8A91)
    val Wood = Color(0xFF8A6238)
    val WoodDark = Color(0xFF5E4125)
    val Skin = Color(0xFFD99B79)

    /** Le mur finit ici ; le sol commence. */
    const val WALL_END = 330f

    // Le magasinier (ses pieds sont au sol de la partie, voir Shelf.FLOOR_Y).
    const val HERO_SCALE = 1.1f

    // Un rayon : large de 100, ses planches (le bas de chaque rangée) tous les 66 depuis le sol ; trois produits par rangée, quatre rangées.
    const val SHELF_W = 100f
    const val SHELF_TOP = 124f
    const val ROW_STEP = 66f
    const val COLS = 3
    const val PRODUCT_SCALE = 0.72f
    const val COL_PITCH = 31f

    // Le niveau à bulle du haut de l'écran.
    const val VIAL_W = 170f
    const val VIAL_Y = 40f
}

private fun Color.shelfFade(a: Float): Color = copy(alpha = alpha * a)

/** Un texte centré (ou aligné) en ([x], [y]), de [px] pixels de l'écran de hauteur de police ; [align] : -1 à gauche, 0 centré, 1 à droite. */
private fun DrawScope.shelfWord(
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

/** Un rectangle arrondi plein avec son contour : [x], [y] en haut à gauche. */
private fun DrawScope.shelfBox(x: Float, y: Float, w: Float, h: Float, fill: Color, r: Float = 3f, line: Float = 2f) {
    drawRoundRect(fill, Offset(x, y), Size(w, h), CornerRadius(r))
    drawRoundRect(ShelfLook.Ink, Offset(x, y), Size(w, h), CornerRadius(r), style = Stroke(width = line))
}

// ------------------------------------------------------------------------------------------------------------ les produits

/**
 * Un produit, de [type] (voir SHELF_PASTA et les suivants), dessiné dans son propre repère : ses pieds au centre en (0, 0),
 * il monte vers les y négatifs, à la largeur et à la hauteur de [Shelf.ITEM_W] et [Shelf.ITEM_H].
 */
private fun DrawScope.drawProduct(type: Int) {
    val ink = ShelfLook.Ink
    val w = Shelf.ITEM_W[type]
    val h = Shelf.ITEM_H[type]
    val l = -w / 2f
    when (type) {
        SHELF_PASTA -> {
            // Un paquet de pâtes : sachet jaune aux soudures crantées, une étiquette rouge et une spirale de pâte.
            shelfBox(l, -h, w, h, Color(0xFFF2C94C), r = 3f)
            val seal = Path()
            seal.moveTo(l, -h + 6f)
            var x = l
            var up = true
            while (x < l + w) {
                x += w / 6f
                seal.lineTo(x, -h + if (up) 2f else 6f)
                up = !up
            }
            drawPath(seal, ink.shelfFade(0.7f), style = Stroke(width = 1.6f, join = StrokeJoin.Round))
            drawRect(ShelfLook.Red, Offset(l + 2f, -h * 0.62f), Size(w - 4f, h * 0.26f))
            drawCircle(Color.White, 3.6f, Offset(0f, -h * 0.49f))
            drawArc(ink.shelfFade(0.8f), 20f, 280f, false, Offset(-5f, -h * 0.34f - 9f), Size(10f, 10f), style = Stroke(width = 1.6f, cap = StrokeCap.Round))
        }
        SHELF_RICE -> {
            // Un sac de riz : toile crème, bandeau vert, un grain stylisé.
            shelfBox(l, -h, w, h, Color(0xFFF1E9D0), r = 5f)
            drawRect(ShelfLook.Green, Offset(l + 1f, -h * 0.66f), Size(w - 2f, h * 0.3f))
            drawLine(ink, Offset(l + 1f, -h * 0.66f), Offset(l + w - 1f, -h * 0.66f), strokeWidth = 1.6f)
            drawLine(ink, Offset(l + 1f, -h * 0.36f), Offset(l + w - 1f, -h * 0.36f), strokeWidth = 1.6f)
            drawOval(Color.White, Offset(-5f, -h * 0.58f), Size(10f, 6f))
            drawLine(ink.shelfFade(0.6f), Offset(l + 5f, -h + 5f), Offset(l + w - 5f, -h + 5f), strokeWidth = 1.6f, cap = StrokeCap.Round)
        }
        SHELF_FLOUR -> {
            // Un gros sac de farine : papier beige, haut replié, bandeau bleu avec un épi.
            shelfBox(l, -h, w, h, Color(0xFFE8D8B0), r = 3f)
            val fold = Path()
            fold.moveTo(l, -h + 8f)
            fold.lineTo(l + w, -h + 8f)
            drawPath(fold, ink.shelfFade(0.7f), style = Stroke(width = 1.8f))
            drawRect(Color(0xFF2B6CB0), Offset(l + 1f, -h * 0.64f), Size(w - 2f, h * 0.3f))
            drawLine(Color(0xFFF2D13B), Offset(0f, -h * 0.36f), Offset(0f, -h * 0.6f), strokeWidth = 2f, cap = StrokeCap.Round)
            for (k in 0 until 3) {
                val y = -h * 0.42f - k * 5.5f
                drawLine(Color(0xFFF2D13B), Offset(0f, y), Offset(-5f, y - 4f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                drawLine(Color(0xFFF2D13B), Offset(0f, y), Offset(5f, y - 4f), strokeWidth = 1.8f, cap = StrokeCap.Round)
            }
        }
        SHELF_CAN -> {
            // Une boîte de conserve : le corps rouge, une étiquette blanche, deux anneaux de métal.
            shelfBox(l, -h, w, h, Color(0xFFC9432F), r = 2f)
            drawRect(Color.White, Offset(l + 1f, -h * 0.68f), Size(w - 2f, h * 0.36f))
            drawCircle(ShelfLook.Green, 3.4f, Offset(0f, -h * 0.5f))
            drawOval(Color(0xFFD9DEE2), Offset(l, -h - 3f), Size(w, 7f))
            drawOval(ink, Offset(l, -h - 3f), Size(w, 7f), style = Stroke(width = 1.8f))
            drawLine(ink.shelfFade(0.5f), Offset(l, -3f), Offset(l + w, -3f), strokeWidth = 1.4f)
        }
        SHELF_CEREAL -> {
            // Une boîte de céréales : grande, orange, un soleil et trois collines.
            shelfBox(l, -h, w, h, Color(0xFFEF8A2B), r = 3f)
            drawLine(ink.shelfFade(0.6f), Offset(l, -h + 7f), Offset(l + w, -h + 7f), strokeWidth = 1.6f)
            drawCircle(Color(0xFFFFE066), 8f, Offset(0f, -h * 0.62f))
            drawCircle(ink.shelfFade(0.7f), 8f, Offset(0f, -h * 0.62f), style = Stroke(width = 1.6f))
            drawRect(ShelfLook.Green, Offset(l + 2f, -h * 0.34f), Size(w - 4f, h * 0.18f))
            drawCircle(Color(0xFF3FB868), 6f, Offset(-8f, -h * 0.34f))
            drawCircle(Color(0xFF3FB868), 7f, Offset(8f, -h * 0.34f))
        }
        else -> {
            // Un paquet de café : brique brune, bandeau doré, un grain.
            shelfBox(l, -h, w, h, Color(0xFF6B4226), r = 3f)
            drawRect(ShelfLook.Amber, Offset(l + 1f, -h * 0.64f), Size(w - 2f, h * 0.3f))
            drawOval(ink.shelfFade(0.85f), Offset(-5f, -h * 0.58f), Size(10f, 7f))
            drawLine(Color(0xFFF1E9D0), Offset(-4f, -h * 0.55f), Offset(4f, -h * 0.5f), strokeWidth = 1.2f, cap = StrokeCap.Round)
            drawLine(ink.shelfFade(0.6f), Offset(l + 3f, -h + 4f), Offset(l + w - 3f, -h + 4f), strokeWidth = 1.4f)
        }
    }
}

/** Un produit posé en ([x], [y]) (le bas), à l'échelle [scale], tourné de [angle] radians autour de son centre. */
private fun DrawScope.productAt(type: Int, x: Float, y: Float, scale: Float = 1f, angle: Float = 0f) {
    withTransform({
        translate(x, y)
        if (angle != 0f) rotate(angle * 180f / 3.1415927f, Offset(0f, -Shelf.ITEM_H[type] * scale / 2f))
        scale(scale, scale, Offset.Zero)
    }) {
        drawProduct(type)
    }
}

// ------------------------------------------------------------------------------------------------------------ le décor

/** Le décor de l'écran, qui ne change jamais : le mur du magasin, la réserve (la trappe d'où tombent les produits), le sol et les rayons vides. */
internal fun DrawScope.drawShelfScene(@Suppress("UNUSED_PARAMETER") labels: ShelfLabels, @Suppress("UNUSED_PARAMETER") measurer: TextMeasurer) {
    val w = Shelf.WIDTH
    val h = Shelf.HEIGHT
    val ink = ShelfLook.Ink
    // Le mur : un crépi clair, des panneaux, un bandeau rouge au plafond et une trappe de réserve au-dessus de la zone de chute.
    drawRect(
        brush = Brush.verticalGradient(0f to Color(0xFFF6F1E4), 1f to Color(0xFFE3DAC6), startY = 0f, endY = ShelfLook.WALL_END),
        size = Size(w, ShelfLook.WALL_END)
    )
    var x = 0f
    while (x < w) {
        drawLine(ink.shelfFade(0.05f), Offset(x, 10f), Offset(x, ShelfLook.WALL_END), strokeWidth = 1.5f)
        x += 60f
    }
    drawRect(ShelfLook.Red, Offset(0f, 0f), Size(w, 8f))
    drawRect(ink, Offset(0f, 8f), Size(w, 2f))
    // La trappe : un rail sombre, ses bandes de sécurité jaune et noir.
    val hatchLeft = Shelf.SPAWN_MIN_X - 36f
    val hatchRight = Shelf.SPAWN_MAX_X + 36f
    drawRect(Color(0xFF3A3F45), Offset(hatchLeft, 10f), Size(hatchRight - hatchLeft, 8f))
    var sx = hatchLeft
    var dark = true
    while (sx < hatchRight) {
        val sw = min(14f, hatchRight - sx)
        drawRect(if (dark) ink else ShelfLook.Amber, Offset(sx, 18f), Size(sw, 5f))
        sx += 14f
        dark = !dark
    }

    // Le sol carrelé, avec une plinthe au pied du mur.
    drawRect(
        brush = Brush.verticalGradient(0f to Color(0xFFD9DEE0), 1f to Color(0xFFBFC7CB), startY = ShelfLook.WALL_END, endY = h),
        topLeft = Offset(0f, ShelfLook.WALL_END),
        size = Size(w, h - ShelfLook.WALL_END)
    )
    var fx = 0f
    while (fx < w) {
        drawLine(ink.shelfFade(0.08f), Offset(fx, ShelfLook.WALL_END), Offset(fx, h), strokeWidth = 1.5f)
        fx += 60f
    }
    var fy = ShelfLook.WALL_END + 28f
    while (fy < h) {
        drawLine(ink.shelfFade(0.08f), Offset(0f, fy), Offset(w, fy), strokeWidth = 1.5f)
        fy += 28f
    }
    drawRect(Color(0xFF8E9AA0), Offset(0f, ShelfLook.WALL_END - 6f), Size(w, 6f))
    drawRect(ink, Offset(0f, ShelfLook.WALL_END - 6f), Size(w, 2f))

    shelfFrame(0f)
    shelfFrame(w - ShelfLook.SHELF_W)
}

/** Le cadre d'un rayon (montants et planches en métal) dont le bord gauche est en [left]. */
private fun DrawScope.shelfFrame(left: Float) {
    val ink = ShelfLook.Ink
    val floor = Shelf.FLOOR_Y
    val top = ShelfLook.SHELF_TOP
    // Le fond du rayon, puis les planches et les montants.
    drawRect(Color(0xFFCBD3D8), Offset(left, top), Size(ShelfLook.SHELF_W, floor - top))
    for (row in 0..3) {
        val y = floor - row * ShelfLook.ROW_STEP
        drawRect(ShelfLook.Steel, Offset(left, y), Size(ShelfLook.SHELF_W, 6f))
        drawRect(ink, Offset(left, y), Size(ShelfLook.SHELF_W, 2f))
        drawRect(ShelfLook.SteelDark, Offset(left, y + 4f), Size(ShelfLook.SHELF_W, 2f))
    }
    drawRect(ShelfLook.Steel, Offset(left, top - 6f), Size(ShelfLook.SHELF_W, 6f))
    drawRect(ink, Offset(left, top - 6f), Size(ShelfLook.SHELF_W, 2f))
    drawRect(ShelfLook.SteelDark, Offset(left, top - 6f), Size(4f, floor - top + 6f))
    drawRect(ShelfLook.SteelDark, Offset(left + ShelfLook.SHELF_W - 4f, top - 6f), Size(4f, floor - top + 6f))
}

// ------------------------------------------------------------------------------------------------------------ ce qui bouge

/** Le temps qui reste, en « m:ss » ; il ne tombe pas à zéro tant que la dernière seconde n'est pas finie. */
internal fun shelfClockText(timeLeftMs: Float): String {
    val seconds = max(0, ceil(timeLeftMs / 1000f).toInt())
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

/** Le multiplicateur d'une pile en texte : « 1,5 » s'écrit 1.5, « 2 » s'écrit 2 (toujours avec un point, comme sur un afficheur). */
private fun multiplierText(n: Int): String {
    val m = Shelf.multiplier(n)
    return if (m % 1f == 0f) m.toInt().toString() else m.toString()
}

/** Les produits rangés sur un rayon (le bas d'abord, de gauche à droite), et le cadre vert du rayon où le magasinier se tient. */
private fun DrawScope.shelfStock(
    left: Float,
    stock: List<Int>,
    active: Boolean,
    unload: Float,
    labels: ShelfLabels,
    measurer: TextMeasurer,
    clockMs: Float
) {
    val floor = Shelf.FLOOR_Y
    for ((index, type) in stock.withIndex()) {
        val row = index / ShelfLook.COLS
        val col = index % ShelfLook.COLS
        val cx = left + 4f + ShelfLook.COL_PITCH / 2f + 2f + col * ShelfLook.COL_PITCH
        productAt(type, cx, floor - row * ShelfLook.ROW_STEP, ShelfLook.PRODUCT_SCALE)
    }
    // L'enseigne du rayon, au-dessus : verte et lumineuse quand le magasinier est là.
    val pulse = 0.5f + 0.5f * sin(clockMs / 160f)
    val signTop = ShelfLook.SHELF_TOP - 32f
    val fill = if (active) ShelfLook.Green else Color.White
    drawRoundRect(fill, Offset(left + 8f, signTop), Size(ShelfLook.SHELF_W - 16f, 22f), CornerRadius(6f))
    drawRoundRect(ShelfLook.Ink, Offset(left + 8f, signTop), Size(ShelfLook.SHELF_W - 16f, 22f), CornerRadius(6f), style = Stroke(width = 2.4f))
    shelfWord(measurer, labels.shelf, left + ShelfLook.SHELF_W / 2f, signTop + 11f, 14f, if (active) Color.White else ShelfLook.Ink, spacing = 1f)
    if (active) {
        drawRect(
            ShelfLook.Green.shelfFade(0.5f + 0.4f * pulse),
            Offset(left - 2f, ShelfLook.SHELF_TOP - 8f),
            Size(ShelfLook.SHELF_W + 4f, floor - ShelfLook.SHELF_TOP + 10f),
            style = Stroke(width = 4f)
        )
        if (unload > 0f) {
            // La barre de rangement, sous l'enseigne, qui se remplit tant que le plateau reste à plat.
            val barLeft = left + 8f
            drawRoundRect(Color.White, Offset(barLeft, signTop - 11f), Size(ShelfLook.SHELF_W - 16f, 8f), CornerRadius(4f))
            drawRoundRect(ShelfLook.Green, Offset(barLeft, signTop - 11f), Size((ShelfLook.SHELF_W - 16f) * unload.coerceIn(0f, 1f), 8f), CornerRadius(4f))
            drawRoundRect(ShelfLook.Ink, Offset(barLeft, signTop - 11f), Size(ShelfLook.SHELF_W - 16f, 8f), CornerRadius(4f), style = Stroke(width = 2f))
        }
    }
}

/** L'ombre d'un produit qui tombe sur le sol : elle indique où il va arriver, et grandit en s'approchant. */
private fun DrawScope.fallShadow(item: ShelfItem) {
    if (item.vx != 0f) return
    val near = (item.y / Shelf.FLOOR_Y).coerceIn(0f, 1f)
    val half = item.width * (0.35f + 0.35f * near)
    drawOval(Color.Black.copy(alpha = 0.10f + 0.14f * near), Offset(item.x - half, Shelf.FLOOR_Y - 4f), Size(half * 2f, 8f))
}

/** Le magasinier (le personnage de Drive, de face) : il se balance un peu en marchant et tasse les genoux quand un produit atterrit. */
private fun DrawScope.shopAssistant(game: ShelfGame, sprites: DriveSprites) {
    val image = sprites.front ?: return
    val w = (image.width * ShelfLook.HERO_SCALE).roundToInt()
    val h = (image.height * ShelfLook.HERO_SCALE).roundToInt()
    val bob = if (game.walking) abs(sin(game.clockMs / 70f)) * 3f else 0f
    val dip = game.bumpMs / Shelf.BUMP_MS * 2.5f
    val top = Shelf.FLOOR_Y - h - bob + dip
    drawImage(
        image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset((game.heroX - w / 2f).roundToInt(), top.roundToInt()),
        dstSize = IntSize(w, h),
        filterQuality = FilterQuality.None
    )
}

/** Le plateau : une planche en inox qui pivote autour des mains du magasinier, avec ses mains dessous et la pile posée dessus. */
private fun DrawScope.tray(game: ShelfGame) {
    val ink = ShelfLook.Ink
    withTransform({
        translate(game.heroX, Shelf.PIVOT_Y)
        rotate(game.tilt * 180f / 3.1415927f, Offset.Zero)
    }) {
        // Les mains, sous la planche (dessinées d'abord : la planche passe devant les doigts).
        for (side in floatArrayOf(-15f, 15f)) {
            drawCircle(ShelfLook.Skin, 6.5f, Offset(side, Shelf.TRAY_THICK + 3f))
            drawCircle(ink, 6.5f, Offset(side, Shelf.TRAY_THICK + 3f), style = Stroke(width = 1.8f))
        }
        // La planche : métal, reflet, rebords relevés aux deux bouts.
        drawRoundRect(ShelfLook.Steel, Offset(-Shelf.HALF, 0f), Size(Shelf.HALF * 2f, Shelf.TRAY_THICK), CornerRadius(3f))
        drawRect(Color.White.shelfFade(0.45f), Offset(-Shelf.HALF + 3f, 1.5f), Size(Shelf.HALF * 2f - 6f, 2.5f))
        drawRoundRect(ink, Offset(-Shelf.HALF, 0f), Size(Shelf.HALF * 2f, Shelf.TRAY_THICK), CornerRadius(3f), style = Stroke(width = 2.2f))
        for (end in floatArrayOf(-Shelf.HALF, Shelf.HALF - 4f)) {
            drawRoundRect(ShelfLook.SteelDark, Offset(end, -5f), Size(4f, 7f), CornerRadius(1.5f))
            drawRoundRect(ink, Offset(end, -5f), Size(4f, 7f), CornerRadius(1.5f), style = Stroke(width = 1.6f))
        }
        // La pile : chaque produit repose sur la planche, ou sur ceux qui sont dessous.
        for (item in game.tray) {
            productAt(item.type, item.s, -item.base)
        }
    }
}

/** Les produits en l'air : ceux qui tombent droit, et ceux qui sont tombés du plateau (ils culbutent). */
private fun DrawScope.fallingItems(game: ShelfGame) {
    for (item in game.falling) {
        val tumble = if (item.vx != 0f) item.y * 0.045f * (if (item.vx > 0f) 1f else -1f) else 0f
        productAt(item.type, item.x, item.y, 1f, tumble)
    }
}

/** Tout ce qui change pendant la partie, dans le repère de 720 x 405 de l'écran. */
internal fun DrawScope.drawShelfLive(game: ShelfGame, sprites: DriveSprites, labels: ShelfLabels, measurer: TextMeasurer) {
    val side = if (game.over) 0 else game.shelfSide
    shelfStock(0f, game.leftShelf, side < 0, if (side < 0) game.unload else 0f, labels, measurer, game.clockMs)
    shelfStock(Shelf.WIDTH - ShelfLook.SHELF_W, game.rightShelf, side > 0, if (side > 0) game.unload else 0f, labels, measurer, game.clockMs)
    for (item in game.falling) fallShadow(item)

    shopAssistant(game, sprites)
    tray(game)
    fallingItems(game)
    effects(game, labels, measurer)
    hud(game, labels, measurer)
}

/** Les effets : la casse sur le sol, les points d'un rangement, le plateau renversé. */
private fun DrawScope.effects(game: ShelfGame, labels: ShelfLabels, measurer: TextMeasurer) {
    // Un éclat sur le sol là où un produit s'est brisé.
    if (game.breakMs > 0f) {
        val t = 1f - game.breakMs / Shelf.BREAK_MS
        val reach = 8f + 22f * t
        val alpha = 1f - t
        for (k in 0 until 8) {
            val a = k * 0.7854f
            val dx = kotlin.math.cos(a)
            val dy = kotlin.math.sin(a) * 0.6f
            drawLine(
                ShelfLook.Amber.shelfFade(alpha),
                Offset(game.breakX + dx * reach * 0.4f, Shelf.FLOOR_Y - 8f + dy * reach * 0.4f),
                Offset(game.breakX + dx * reach, Shelf.FLOOR_Y - 8f + dy * reach),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }
    }
    // Les points d'un rangement, qui montent au-dessus du rayon.
    if (game.bankMs > 0f && game.bankSide != 0) {
        val t = 1f - game.bankMs / Shelf.BANK_MS
        val x = if (game.bankSide < 0) ShelfLook.SHELF_W / 2f else Shelf.WIDTH - ShelfLook.SHELF_W / 2f
        val alpha = if (t > 0.7f) (1f - t) / 0.3f else 1f
        val y = ShelfLook.SHELF_TOP - 70f - t * 26f
        shelfWord(measurer, "+${game.bankPoints}", x, y, 30f, ShelfLook.GreenDark.shelfFade(alpha), italic = true)
        if (game.bankCount > 1) {
            shelfWord(measurer, "×${multiplierText(game.bankCount)}", x, y + 26f, 18f, ShelfLook.GreenDark.shelfFade(alpha * 0.85f))
        }
    }
    // Le plateau renversé : un voile rouge et un cri.
    if (game.capsizeMs > 0f) {
        val share = game.capsizeMs / Shelf.CAPSIZE_MS
        drawRect(ShelfLook.Red.shelfFade(0.22f * share), size = Size(Shelf.WIDTH, Shelf.HEIGHT))
        shelfWord(measurer, labels.capsized, Shelf.WIDTH / 2f, 160f, 40f, ShelfLook.Red.shelfFade(min(1f, share * 1.5f)), italic = true)
    }
}

/** Les compteurs du haut : le temps à gauche, le score à droite, au milieu la casse, le niveau à bulle et la valeur de la pile. */
private fun DrawScope.hud(game: ShelfGame, labels: ShelfLabels, measurer: TextMeasurer) {
    val ink = ShelfLook.Ink
    val urgent = !game.over && game.timeLeftMs <= 10_000f
    shelfWord(measurer, labels.time, 16f, 28f, 14f, ink.shelfFade(0.6f), align = -1)
    shelfWord(measurer, shelfClockText(game.timeLeftMs), 16f, 66f, 46f, if (urgent) ShelfLook.Red else ink, align = -1)
    shelfWord(measurer, labels.score, Shelf.WIDTH - 16f, 28f, 14f, ink.shelfFade(0.6f), align = 1)
    shelfWord(measurer, game.score.toString(), Shelf.WIDTH - 16f, 66f, 46f, ink, align = 1)

    // La casse : un carré par produit qu'on peut encore casser, qui devient rouge et barré quand il est cassé.
    val size = 15f
    val gap = 6f
    val total = Shelf.MAX_BREAKS * size + (Shelf.MAX_BREAKS - 1) * gap
    var x = Shelf.WIDTH / 2f - total / 2f
    for (k in 0 until Shelf.MAX_BREAKS) {
        val broken = k < game.broken
        drawRoundRect(if (broken) ShelfLook.Red else ShelfLook.Wood, Offset(x, 28f), Size(size, size), CornerRadius(3f))
        drawRoundRect(ink, Offset(x, 28f), Size(size, size), CornerRadius(3f), style = Stroke(width = 2f))
        if (broken) {
            drawLine(Color.White, Offset(x + 3.5f, 31.5f), Offset(x + size - 3.5f, 28f + size - 3.5f), strokeWidth = 2f, cap = StrokeCap.Round)
            drawLine(Color.White, Offset(x + size - 3.5f, 31.5f), Offset(x + 3.5f, 28f + size - 3.5f), strokeWidth = 2f, cap = StrokeCap.Round)
        }
        x += size + gap
    }
    shelfWord(measurer, labels.breaks, Shelf.WIDTH / 2f, 17f, 12f, ink.shelfFade(0.6f))

    // Le niveau à bulle : la zone verte, au milieu, est celle où le plateau est assez à plat pour ranger.
    val vw = ShelfLook.VIAL_W
    val vx = Shelf.WIDTH / 2f - vw / 2f
    val vy = ShelfLook.VIAL_Y + 16f
    drawRoundRect(Color.White.shelfFade(0.92f), Offset(vx, vy), Size(vw, 14f), CornerRadius(7f))
    val levelHalf = Shelf.UNLOAD_TILT / Shelf.MAX_TILT * (vw / 2f - 8f)
    drawRect(ShelfLook.Green.shelfFade(0.45f), Offset(Shelf.WIDTH / 2f - levelHalf, vy + 2f), Size(levelHalf * 2f, 10f))
    drawLine(ink.shelfFade(0.5f), Offset(Shelf.WIDTH / 2f, vy + 1f), Offset(Shelf.WIDTH / 2f, vy + 13f), strokeWidth = 1.5f)
    drawRoundRect(ink, Offset(vx, vy), Size(vw, 14f), CornerRadius(7f), style = Stroke(width = 2.2f))
    val share = (game.tilt / Shelf.MAX_TILT).coerceIn(-1f, 1f)
    val dangerous = abs(share) > 0.55f
    val bx = Shelf.WIDTH / 2f + share * (vw / 2f - 8f)
    drawCircle(if (dangerous) ShelfLook.Red else ShelfLook.Amber, 6f, Offset(bx, vy + 7f))
    drawCircle(ink, 6f, Offset(bx, vy + 7f), style = Stroke(width = 2f))

    // Ce que vaut la pile qu'on porte : le multiplicateur et les points qu'elle rapportera au rayon.
    if (!game.over && game.tray.isNotEmpty()) {
        val text = "${labels.pile} ×${multiplierText(game.tray.size)} · ${game.trayWorth}"
        shelfWord(measurer, text, Shelf.WIDTH / 2f, vy + 30f, 17f, ShelfLook.GreenDark)
    }
}
