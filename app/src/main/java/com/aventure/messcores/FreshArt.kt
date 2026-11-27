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
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Les dessins de l'écran de Rayon frais, dans le repère de l'écran (720 x 405), le même que celui de Drive. La scène se lit de haut
 * en bas : les compteurs et l'enseigne, le meuble réfrigéré (cinq colonnes de trois paquets, le paquet de devant en bas) entre le
 * chariot à gauche et la porte du frigo à droite, puis l'allée où se tiennent le joueur, les clients et le contrôleur d'hygiène.
 * Le décor qui ne bouge pas se dessine une fois avec [drawFreshScene] ; tout ce qui bouge (paquets et leurs compteurs, chariot,
 * porte, personnages, messages) avec [drawFreshLive]. Le joueur est le personnage de Drive ([DriveSprites]).
 *
 * Un compteur n'est jamais lu à la seule couleur : un nombre de secondes, un « ! » quand il est bas, une croix quand le paquet est
 * périmé (voir [ColorBlind] pour le reste de l'appli).
 */
internal class FreshLabels(
    val time: String,
    val score: String,
    val faults: String,
    val sign: String,
    val opening: String,
    val no: String,
    val cart: String,
    val doorAlert: String,
    val inspector: String,
    /** Le nom de chaque produit, dans l'ordre des colonnes. */
    val products: List<String>,
    /** Le texte de chaque fait marquant, indexé par FRESH_EVENT_* (la case 0 est vide). */
    val events: List<String>
)

private object FreshLook {
    val Ink = Color(0xFF20241F)
    val Red = Color(0xFFE2001A)
    val Green = Color(0xFF2E9E4F)
    val GreenDark = Color(0xFF176B33)
    val Amber = Color(0xFFF0B429)
    val Blue = Color(0xFF2B6CB0)
    val Metal = Color(0xFFB9C4C9)
    val White = Color(0xFFF8FBFC)
    val Skin = Color(0xFFD99B79)
    val Expired = Color(0xFF9A8F8F)

    /** La couleur de fond du paquet de chaque produit : yaourt, jambon, fromage, salade, poulet. */
    val Tray = intArrayOf(0xFFF4B6C8.toInt(), 0xFFF2A99A.toInt(), 0xFFF7DE8A.toInt(), 0xFFB7E2A8.toInt(), 0xFFE6C28F.toInt())

    // Le mur finit ici ; le sol commence.
    const val WALL_END = 290f

    // Le meuble : cinq colonnes de [COL_W] côte à côte, du haut (le fond) au bas (le devant).
    const val CASE_X0 = 108f
    const val COL_W = 104f
    const val CASE_X1 = CASE_X0 + COL_W * Fresh.PRODUCTS
    const val CASE_TOP = 108f
    const val CASE_BOTTOM = 290f
    const val TAG_Y = 112f
    const val TAG_H = 18f
    const val LIP_Y = 280f

    // Un paquet du meuble, et la rangée de chaque rang (0 devant, en bas).
    const val TRAY_W = 92f
    const val TRAY_H = 36f
    const val ROW_STEP = 46f
    const val ROW0_TOP = 238f

    // Le chariot, à gauche ; ses paquets sont empilés, le paquet du dessus en haut.
    const val CART_X0 = 6f
    const val CART_W = 96f
    const val CART_ITEM_W = 84f
    const val CART_ITEM_H = 22f
    const val CART_ITEM_STEP = 26f
    const val CART_ITEM_TOP = 126f

    // La porte du frigo, à droite du meuble.
    const val DOOR_X0 = 632f
    const val DOOR_W = 80f

    // L'allée : les pieds des clients et du joueur, et la taille des clients (ils sont plus petits, plus loin).
    const val CUSTOMER_FEET = 362f
    const val CUSTOMER_SCALE = 0.7f
    const val HERO_FEET = Fresh.HEIGHT - 3f
}

private fun Color.fade(a: Float): Color = copy(alpha = alpha * a)

/** Le centre d'une position du joueur (0 le chariot, 1 à 5 les colonnes, 6 la porte). */
private fun posX(position: Int): Float = when (position) {
    Fresh.CART_POS -> FreshLook.CART_X0 + FreshLook.CART_W / 2f
    Fresh.DOOR_POS -> FreshLook.DOOR_X0 + FreshLook.DOOR_W / 2f
    else -> slotX(position - 1)
}

/** Le centre de la colonne [slot] (0 à 4). */
private fun slotX(slot: Int): Float = FreshLook.CASE_X0 + FreshLook.COL_W * slot + FreshLook.COL_W / 2f

/** Le haut d'un paquet du meuble au rang [row] (0 devant, en bas). */
private fun rowTop(row: Int): Float = FreshLook.ROW0_TOP - FreshLook.ROW_STEP * row

/** Un texte centré (ou aligné) en ([x], [y]), de [px] pixels de l'écran de hauteur de police ; [align] : -1 à gauche, 0 centré, 1 à droite. */
private fun DrawScope.freshWord(
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

/** Une croix centrée en ([cx], [cy]), de demi-côté [r]. */
private fun DrawScope.freshCross(cx: Float, cy: Float, r: Float, color: Color, width: Float) {
    drawLine(color, Offset(cx - r, cy - r), Offset(cx + r, cy + r), strokeWidth = width, cap = StrokeCap.Round)
    drawLine(color, Offset(cx - r, cy + r), Offset(cx + r, cy - r), strokeWidth = width, cap = StrokeCap.Round)
}

/** Une coche centrée en ([cx], [cy]), de demi-côté [r]. */
private fun DrawScope.freshCheck(cx: Float, cy: Float, r: Float, color: Color, width: Float) {
    val path = Path()
    path.moveTo(cx - r, cy)
    path.lineTo(cx - r * 0.3f, cy + r * 0.7f)
    path.lineTo(cx + r, cy - r * 0.7f)
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ------------------------------------------------------------------------------------------------------------ les produits

/** L'image d'un produit, centrée en ([cx], [cy]) dans une boîte de [s] de côté : un pot, une tranche, un coin, une feuille, une cuisse. */
private fun DrawScope.freshIcon(product: Int, cx: Float, cy: Float, s: Float) {
    val ink = FreshLook.Ink
    val h = s / 2f
    val line = max(1.2f, s * 0.07f)
    when (product) {
        0 -> {
            val pot = Path()
            pot.moveTo(cx - h * 0.8f, cy - h * 0.5f)
            pot.lineTo(cx + h * 0.8f, cy - h * 0.5f)
            pot.lineTo(cx + h * 0.55f, cy + h * 0.8f)
            pot.lineTo(cx - h * 0.55f, cy + h * 0.8f)
            pot.close()
            drawPath(pot, Color.White)
            drawRect(Color(0xFFE0568A), Offset(cx - h * 0.8f, cy - h * 0.5f), Size(h * 1.6f, h * 0.5f))
            drawPath(pot, ink, style = Stroke(width = line, join = StrokeJoin.Round))
        }
        1 -> {
            drawOval(Color(0xFFE9806A), Offset(cx - h, cy - h * 0.7f), Size(h * 2f, h * 1.4f))
            drawOval(Color(0xFFF7C9BE), Offset(cx - h * 0.55f, cy - h * 0.3f), Size(h * 1.1f, h * 0.6f))
            drawOval(ink, Offset(cx - h, cy - h * 0.7f), Size(h * 2f, h * 1.4f), style = Stroke(width = line))
        }
        2 -> {
            val wedge = Path()
            wedge.moveTo(cx - h, cy + h * 0.6f)
            wedge.lineTo(cx + h, cy + h * 0.6f)
            wedge.lineTo(cx + h * 0.7f, cy - h * 0.6f)
            wedge.close()
            drawPath(wedge, FreshLook.Amber)
            drawCircle(Color(0xFFFFF2B8), h * 0.2f, Offset(cx + h * 0.2f, cy + h * 0.15f))
            drawCircle(Color(0xFFFFF2B8), h * 0.14f, Offset(cx - h * 0.35f, cy + h * 0.35f))
            drawPath(wedge, ink, style = Stroke(width = line, join = StrokeJoin.Round))
        }
        3 -> {
            drawCircle(FreshLook.Green, h * 0.85f, Offset(cx, cy))
            drawCircle(Color(0xFF8ED18C), h * 0.45f, Offset(cx - h * 0.2f, cy - h * 0.2f))
            drawCircle(ink, h * 0.85f, Offset(cx, cy), style = Stroke(width = line))
        }
        else -> {
            drawOval(Color(0xFFB9783A), Offset(cx - h, cy - h * 0.55f), Size(h * 1.5f, h * 1.2f))
            drawOval(ink, Offset(cx - h, cy - h * 0.55f), Size(h * 1.5f, h * 1.2f), style = Stroke(width = line))
            drawLine(Color.White, Offset(cx + h * 0.4f, cy + h * 0.1f), Offset(cx + h * 0.95f, cy + h * 0.6f), strokeWidth = line * 1.6f, cap = StrokeCap.Round)
            drawCircle(Color.White, h * 0.22f, Offset(cx + h * 0.95f, cy + h * 0.6f))
        }
    }
}

/** La couleur du compteur d'un paquet : vert (frais), orange (moyen), rouge (bas ou périmé). */
private fun lifeColor(pack: FreshPack): Color = when {
    pack.expired -> FreshLook.Red
    pack.ratio <= Fresh.LOW_RATIO -> FreshLook.Red
    pack.ratio <= Fresh.MID_RATIO -> FreshLook.Amber
    else -> FreshLook.Green
}

/**
 * Un paquet : sa barquette de la couleur du produit, son image, son compteur (les secondes qu'il lui reste, « ! » devant quand c'est
 * bas, une croix quand il est périmé), une barre de vie, et la pastille jaune de la démarque. [w] et [h] : taille du paquet ;
 * [px] : taille des chiffres.
 */
private fun DrawScope.tray(measurer: TextMeasurer, cx: Float, top: Float, w: Float, h: Float, pack: FreshPack, px: Float) {
    val ink = FreshLook.Ink
    val left = cx - w / 2f
    val corner = CornerRadius(5f)
    val base = if (pack.expired) FreshLook.Expired else Color(FreshLook.Tray[pack.product.coerceIn(0, Fresh.PRODUCTS - 1)])
    drawRoundRect(base, Offset(left, top), Size(w, h), corner)
    drawRoundRect(ink, Offset(left, top), Size(w, h), corner, style = Stroke(width = 1.8f))
    freshIcon(pack.product, left + h * 0.55f, top + h * 0.48f, h * 0.62f)

    // Le compteur, à droite.
    val color = lifeColor(pack)
    val chipW = w * 0.46f
    val chipH = h * 0.6f
    val chipLeft = left + w - chipW - 4f
    val chipTop = top + (h - chipH) / 2f - 1f
    drawRoundRect(color, Offset(chipLeft, chipTop), Size(chipW, chipH), CornerRadius(4f))
    drawRoundRect(ink, Offset(chipLeft, chipTop), Size(chipW, chipH), CornerRadius(4f), style = Stroke(width = 1.3f))
    val chipX = chipLeft + chipW / 2f
    val chipY = chipTop + chipH / 2f
    if (pack.expired) {
        freshCross(chipX, chipY, chipH * 0.26f, Color.White, 2.2f)
    } else {
        val seconds = ceil(pack.lifeMs / 1000f).toInt()
        val text = if (pack.ratio <= Fresh.LOW_RATIO) "!$seconds" else seconds.toString()
        val onAmber = pack.ratio > Fresh.LOW_RATIO && pack.ratio <= Fresh.MID_RATIO
        freshWord(measurer, text, chipX, chipY, px, if (onAmber) ink else Color.White)
    }
    // La barre de vie, au bas du paquet.
    drawRect(color, Offset(left + 3f, top + h - 5f), Size((w - 6f) * pack.ratio, 3f))
    // La pastille jaune de la démarque, sur le coin du paquet.
    if (pack.marked) {
        drawCircle(FreshLook.Amber, h * 0.26f, Offset(left + 4f, top + 3f))
        drawCircle(ink, h * 0.26f, Offset(left + 4f, top + 3f), style = Stroke(width = 1.3f))
        freshWord(measurer, "%", left + 4f, top + 3f, h * 0.3f, ink)
    }
}

/** Un petit badge rond avec la lettre d'un bouton (A ou B) ; [alpha] le grise quand le geste est impossible. */
private fun DrawScope.badge(glyph: Int, cx: Float, cy: Float, alpha: Float) {
    drawCircle(Color.White.fade(alpha), 9f, Offset(cx, cy))
    drawCircle(driveLetterColor(glyph).fade(alpha), 9f, Offset(cx, cy), style = Stroke(width = 1.8f))
    driveGlyph(glyph, cx, cy, 11f, driveLetterColor(glyph).fade(alpha))
}

// ------------------------------------------------------------------------------------------------------------ le décor

/** Le décor qui ne bouge jamais : le mur, l'enseigne, le meuble vide avec ses étiquettes, le chariot, le cadre de la porte, le sol. */
internal fun DrawScope.drawFreshScene(labels: FreshLabels, measurer: TextMeasurer) {
    val w = Fresh.WIDTH
    val h = Fresh.HEIGHT
    val ink = FreshLook.Ink
    // Le mur carrelé.
    drawRect(
        brush = Brush.verticalGradient(0f to Color(0xFFEAF6F1), 1f to Color(0xFFD2E8E0), startY = 0f, endY = FreshLook.WALL_END),
        size = Size(w, FreshLook.WALL_END)
    )
    var x = 0f
    while (x < w) {
        drawLine(ink.fade(0.06f), Offset(x, 0f), Offset(x, FreshLook.WALL_END), strokeWidth = 1.5f)
        x += 48f
    }
    var y = 0f
    while (y < FreshLook.WALL_END) {
        drawLine(ink.fade(0.06f), Offset(0f, y), Offset(w, y), strokeWidth = 1.5f)
        y += 36f
    }
    // L'enseigne, au milieu du mur.
    drawRoundRect(Color.White, Offset(w / 2f - 110f, 8f), Size(220f, 40f), CornerRadius(8f))
    drawRoundRect(ink, Offset(w / 2f - 110f, 8f), Size(220f, 40f), CornerRadius(8f), style = Stroke(width = 3f))
    drawRect(FreshLook.Green, Offset(w / 2f - 110f, 40f), Size(220f, 5f))
    freshWord(measurer, labels.sign, w / 2f, 24f, 21f, FreshLook.GreenDark, italic = true, spacing = 3f)

    // Le meuble réfrigéré : le cadre en inox, le fond froid, les séparations et l'étiquette de chaque colonne.
    val caseW = FreshLook.CASE_X1 - FreshLook.CASE_X0
    val caseH = FreshLook.CASE_BOTTOM - FreshLook.CASE_TOP
    drawRoundRect(FreshLook.Metal, Offset(FreshLook.CASE_X0 - 6f, FreshLook.CASE_TOP - 5f), Size(caseW + 12f, caseH + 5f), CornerRadius(8f))
    drawRoundRect(ink, Offset(FreshLook.CASE_X0 - 6f, FreshLook.CASE_TOP - 5f), Size(caseW + 12f, caseH + 5f), CornerRadius(8f), style = Stroke(width = 2.5f))
    drawRect(
        brush = Brush.verticalGradient(0f to Color(0xFFE9F5FA), 1f to Color(0xFFC6E0EC), startY = FreshLook.CASE_TOP, endY = FreshLook.LIP_Y),
        topLeft = Offset(FreshLook.CASE_X0, FreshLook.CASE_TOP),
        size = Size(caseW, FreshLook.LIP_Y - FreshLook.CASE_TOP)
    )
    for (i in 1 until Fresh.PRODUCTS) {
        val lineX = FreshLook.CASE_X0 + FreshLook.COL_W * i
        drawLine(ink.fade(0.22f), Offset(lineX, FreshLook.CASE_TOP), Offset(lineX, FreshLook.LIP_Y), strokeWidth = 2f)
    }
    for (i in 0 until Fresh.PRODUCTS) {
        val cx = slotX(i)
        drawRoundRect(Color.White, Offset(cx - 44f, FreshLook.TAG_Y), Size(88f, FreshLook.TAG_H), CornerRadius(4f))
        drawRoundRect(ink.fade(0.5f), Offset(cx - 44f, FreshLook.TAG_Y), Size(88f, FreshLook.TAG_H), CornerRadius(4f), style = Stroke(width = 1.2f))
        freshWord(measurer, labels.products.getOrElse(i) { "" }, cx - 4f, FreshLook.TAG_Y + FreshLook.TAG_H / 2f, 12f, ink)
    }
    // Le rebord avant du meuble.
    drawRect(FreshLook.Metal, Offset(FreshLook.CASE_X0 - 6f, FreshLook.LIP_Y), Size(caseW + 12f, FreshLook.CASE_BOTTOM - FreshLook.LIP_Y))
    drawRect(ink, Offset(FreshLook.CASE_X0 - 6f, FreshLook.LIP_Y), Size(caseW + 12f, 2.5f))
    drawRect(ink, Offset(FreshLook.CASE_X0 - 6f, FreshLook.CASE_BOTTOM - 2f), Size(caseW + 12f, 2f))

    // Le chariot : un cadre en inox avec son nom, et ses roues.
    drawRoundRect(FreshLook.White, Offset(FreshLook.CART_X0, FreshLook.CASE_TOP), Size(FreshLook.CART_W, FreshLook.CASE_BOTTOM - FreshLook.CASE_TOP), CornerRadius(6f))
    drawRoundRect(ink, Offset(FreshLook.CART_X0, FreshLook.CASE_TOP), Size(FreshLook.CART_W, FreshLook.CASE_BOTTOM - FreshLook.CASE_TOP), CornerRadius(6f), style = Stroke(width = 2.5f))
    freshWord(measurer, labels.cart, posX(Fresh.CART_POS), FreshLook.CASE_TOP + 10f, 11f, ink.fade(0.7f))
    drawCircle(ink, 6f, Offset(FreshLook.CART_X0 + 16f, FreshLook.CASE_BOTTOM + 4f))
    drawCircle(ink, 6f, Offset(FreshLook.CART_X0 + FreshLook.CART_W - 16f, FreshLook.CASE_BOTTOM + 4f))

    // La porte du frigo : l'ouverture dans le mur (sa vitre se dessine avec ce qui bouge).
    drawRoundRect(Color(0xFFDCEBF2), Offset(FreshLook.DOOR_X0, FreshLook.CASE_TOP), Size(FreshLook.DOOR_W, FreshLook.CASE_BOTTOM - FreshLook.CASE_TOP), CornerRadius(6f))
    drawRoundRect(ink, Offset(FreshLook.DOOR_X0, FreshLook.CASE_TOP), Size(FreshLook.DOOR_W, FreshLook.CASE_BOTTOM - FreshLook.CASE_TOP), CornerRadius(6f), style = Stroke(width = 2.5f))

    // Le sol carrelé de l'allée, avec une plinthe au pied du mur.
    drawRect(
        brush = Brush.verticalGradient(0f to Color(0xFFD9DEE0), 1f to Color(0xFFBFC7CB), startY = FreshLook.WALL_END, endY = h),
        topLeft = Offset(0f, FreshLook.WALL_END),
        size = Size(w, h - FreshLook.WALL_END)
    )
    var fx = 0f
    while (fx < w) {
        drawLine(ink.fade(0.08f), Offset(fx, FreshLook.WALL_END), Offset(fx, h), strokeWidth = 1.5f)
        fx += 60f
    }
    var fy = FreshLook.WALL_END + 30f
    while (fy < h) {
        drawLine(ink.fade(0.08f), Offset(0f, fy), Offset(w, fy), strokeWidth = 1.5f)
        fy += 30f
    }
}

// ------------------------------------------------------------------------------------------------------------ ce qui bouge

/** Les compteurs du haut de l'écran : le temps à gauche, le score à droite, les fautes graves sous l'enseigne. */
private fun DrawScope.hud(measurer: TextMeasurer, labels: FreshLabels, game: FreshGame) {
    val ink = FreshLook.Ink
    val left = if (game.stage == FRESH_STAGE_OPENING) Fresh.DAY_MS else game.stageLeftMs
    val urgent = game.stage == FRESH_STAGE_PLAY && game.stageLeftMs <= 10_000f
    freshWord(measurer, labels.time, 16f, 24f, 14f, ink.fade(0.6f), align = -1)
    freshWord(measurer, fishClockText(if (game.over) 0f else left), 16f, 62f, 46f, if (urgent) FreshLook.Red else ink, align = -1)
    freshWord(measurer, labels.score, Fresh.WIDTH - 16f, 24f, 14f, ink.fade(0.6f), align = 1)
    freshWord(measurer, game.score.toString(), Fresh.WIDTH - 16f, 62f, 46f, ink, align = 1)
    // Les fautes graves : trois cases, une croix rouge dans chaque case de faute.
    freshWord(measurer, labels.faults, Fresh.WIDTH / 2f, 60f, 12f, ink.fade(0.6f))
    for (i in 0 until Fresh.MAX_ERRORS) {
        val cx = Fresh.WIDTH / 2f + (i - 1) * 30f
        drawCircle(Color.White, 10f, Offset(cx, 80f))
        drawCircle(ink, 10f, Offset(cx, 80f), style = Stroke(width = 2f))
        if (i < game.errors) freshCross(cx, 80f, 5f, FreshLook.Red, 3f)
    }
}

/** Le meuble : la colonne où se tient le joueur, les paquets avec leurs compteurs, le paquet visé et les gestes possibles. */
private fun DrawScope.display(game: FreshGame, measurer: TextMeasurer) {
    val ink = FreshLook.Ink
    val pulse = 0.5f + 0.5f * sin(game.clockMs / 140f)
    val colH = FreshLook.LIP_Y - FreshLook.CASE_TOP
    val inGame = !game.over && game.stage == FRESH_STAGE_PLAY
    val atSlot = inGame && game.pos >= 1 && game.pos <= Fresh.PRODUCTS
    for (i in 0 until Fresh.PRODUCTS) {
        val cx = slotX(i)
        val flash = game.placeSlot == i && game.placeMs > 0f
        if (flash) {
            drawRect(FreshLook.Green.fade(0.3f * game.placeMs / Fresh.PLACE_MS), Offset(cx - FreshLook.COL_W / 2f, FreshLook.CASE_TOP), Size(FreshLook.COL_W, colH))
        }
        if (atSlot && game.pos - 1 == i) {
            drawRect(ink.fade(0.1f), Offset(cx - FreshLook.COL_W / 2f, FreshLook.CASE_TOP), Size(FreshLook.COL_W, colH))
            drawRect(FreshLook.Red.fade(0.5f + 0.4f * pulse), Offset(cx - FreshLook.COL_W / 2f, FreshLook.CASE_TOP), Size(FreshLook.COL_W, colH), style = Stroke(width = 2.5f))
        }
    }
    // Les paquets, avec le compteur de chacun.
    for (i in 0 until Fresh.PRODUCTS) {
        val queue = game.slots[i]
        for (row in queue.indices) tray(measurer, slotX(i), rowTop(row), FreshLook.TRAY_W, FreshLook.TRAY_H, queue[row], 14f)
    }
    if (!atSlot) return
    val slot = game.pos - 1
    val cx = slotX(slot)
    val queue = game.slots[slot]
    val inHand = game.held
    if (inHand != null) {
        // Un paquet en main : A le range au fond (en haut), B devant (en bas), s'il va dans cette colonne et qu'il y a de la place.
        if (inHand.product == slot && queue.size < Fresh.SLOT_CAP) {
            badge(GLYPH_A, cx + 33f, FreshLook.TAG_Y + FreshLook.TAG_H / 2f, 1f)
            badge(GLYPH_B, cx, FreshLook.LIP_Y + 5f, 1f)
        }
    } else {
        // Les mains libres : un crochet sur le paquet visé, avec A (le retirer) et B (le démarquer, si c'est possible).
        val aim = game.target(slot)
        if (aim != null) {
            val row = queue.indexOf(aim)
            if (row >= 0) {
                val top = rowTop(row)
                drawRoundRect(
                    ink.fade(0.55f + 0.45f * pulse),
                    Offset(cx - FreshLook.TRAY_W / 2f - 3f, top - 3f),
                    Size(FreshLook.TRAY_W + 6f, FreshLook.TRAY_H + 6f),
                    CornerRadius(7f),
                    style = Stroke(width = 3f)
                )
                badge(GLYPH_A, cx - 12f, top - 5f, 1f)
                badge(GLYPH_B, cx + 12f, top - 5f, if (aim.expired || aim.marked) 0.3f else 1f)
            }
        }
    }
}

/** Le chariot : les paquets à ranger, celui du dessus en haut (c'est celui qu'on prend). */
private fun DrawScope.cart(game: FreshGame, measurer: TextMeasurer) {
    val ink = FreshLook.Ink
    val pulse = 0.5f + 0.5f * sin(game.clockMs / 140f)
    val cx = posX(Fresh.CART_POS)
    val inGame = !game.over && game.stage == FRESH_STAGE_PLAY
    if (inGame && game.pos == Fresh.CART_POS) {
        drawRoundRect(
            FreshLook.Red.fade(0.5f + 0.4f * pulse),
            Offset(FreshLook.CART_X0, FreshLook.CASE_TOP),
            Size(FreshLook.CART_W, FreshLook.CASE_BOTTOM - FreshLook.CASE_TOP),
            CornerRadius(6f),
            style = Stroke(width = 3f)
        )
    }
    for (i in game.cart.indices) {
        val top = FreshLook.CART_ITEM_TOP + FreshLook.CART_ITEM_STEP * i
        tray(measurer, cx, top, FreshLook.CART_ITEM_W, FreshLook.CART_ITEM_H, game.cart[i], 11f)
        if (i == 0 && inGame && game.held == null) {
            drawRoundRect(
                ink.fade(0.55f + 0.45f * pulse),
                Offset(cx - FreshLook.CART_ITEM_W / 2f - 3f, top - 3f),
                Size(FreshLook.CART_ITEM_W + 6f, FreshLook.CART_ITEM_H + 6f),
                CornerRadius(6f),
                style = Stroke(width = 2.5f)
            )
        }
    }
}

/** La porte du frigo : fermée, une vitre avec sa poignée ; ouverte, battante, avec « ×3 » et la barre du temps avant la faute. */
private fun DrawScope.door(game: FreshGame, measurer: TextMeasurer) {
    val ink = FreshLook.Ink
    val pulse = 0.5f + 0.5f * sin(game.clockMs / 120f)
    val x0 = FreshLook.DOOR_X0
    val top = FreshLook.CASE_TOP
    val height = FreshLook.CASE_BOTTOM - FreshLook.CASE_TOP
    val inGame = !game.over && game.stage == FRESH_STAGE_PLAY
    if (!game.doorOpen) {
        drawRoundRect(FreshLook.Blue.fade(0.25f), Offset(x0 + 4f, top + 4f), Size(FreshLook.DOOR_W - 8f, height - 8f), CornerRadius(4f))
        drawRoundRect(FreshLook.Metal, Offset(x0 + FreshLook.DOOR_W - 16f, top + height / 2f - 22f), Size(6f, 44f), CornerRadius(3f))
        drawRoundRect(ink, Offset(x0 + FreshLook.DOOR_W - 16f, top + height / 2f - 22f), Size(6f, 44f), CornerRadius(3f), style = Stroke(width = 1.5f))
    } else {
        // Le dedans du frigo, sombre et froid, et la porte qui bat vers l'allée.
        drawRoundRect(Color(0xFF2A3A45), Offset(x0 + 4f, top + 4f), Size(FreshLook.DOOR_W - 8f, height - 8f), CornerRadius(4f))
        val panel = Path()
        panel.moveTo(x0 + FreshLook.DOOR_W - 4f, top + 4f)
        panel.lineTo(x0 + FreshLook.DOOR_W - 4f, top + height - 4f)
        panel.lineTo(x0 + 26f, top + height + 8f)
        panel.lineTo(x0 + 26f, top - 4f)
        panel.close()
        drawPath(panel, FreshLook.Blue.fade(0.45f))
        drawPath(panel, ink, style = Stroke(width = 2f, join = StrokeJoin.Round))
        freshWord(measurer, "×3", x0 + FreshLook.DOOR_W / 2f, top + height / 2f, 26f + 4f * pulse, FreshLook.Red)
        // La barre du temps avant que le froid soit rompu.
        val left = (1f - game.doorOpenMs / Fresh.DOOR_FAULT_MS).coerceIn(0f, 1f)
        drawRect(Color.White, Offset(x0, top - 10f), Size(FreshLook.DOOR_W, 6f))
        drawRect(FreshLook.Red, Offset(x0, top - 10f), Size(FreshLook.DOOR_W * left, 6f))
        drawRect(ink, Offset(x0, top - 10f), Size(FreshLook.DOOR_W, 6f), style = Stroke(width = 1.2f))
        if (inGame && game.pos == Fresh.DOOR_POS) badge(GLYPH_A, x0 + FreshLook.DOOR_W / 2f, top + height - 22f, 1f)
    }
    if (inGame && game.pos == Fresh.DOOR_POS) {
        drawRoundRect(
            FreshLook.Red.fade(0.5f + 0.4f * pulse),
            Offset(x0, top),
            Size(FreshLook.DOOR_W, height),
            CornerRadius(6f),
            style = Stroke(width = 3f)
        )
    }
}

// ------------------------------------------------------------------------------------------------------------ les personnages

/** Le joueur (le personnage de Drive) dans l'allée, de profil quand il marche, de face sinon ; il tient son paquet au-dessus de lui. */
private fun DrawScope.hero(game: FreshGame, sprites: DriveSprites, measurer: TextMeasurer) {
    val image = when {
        game.moveMs > 0f -> if (game.facing > 0) sprites.right else sprites.left
        else -> sprites.front
    } ?: return
    val w = image.width
    val h = image.height
    val cx = posX(game.pos)
    val hop = if (game.placeMs > 0f) sin(game.placeMs / Fresh.PLACE_MS * 3.1416f) * 6f else 0f
    val shake = if (game.blockedMs > 0f) sin(game.clockMs / 25f) * 3f else 0f
    val top = FreshLook.HERO_FEET - h - hop
    drawImage(
        image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(w, h),
        dstOffset = IntOffset((cx + shake - w / 2f).roundToInt(), top.roundToInt()),
        dstSize = IntSize(w, h),
        filterQuality = FilterQuality.None
    )
    val inHand = game.held
    if (inHand != null) tray(measurer, cx + shake, top - 24f, 66f, 24f, inHand, 12f)
}

/** Un client debout, les pieds en (0, 0), de la taille normale (la scène le réduit) ; [mood] : 1 content, -1 mécontent, 0 neutre. */
private fun DrawScope.shopper(look: Int, mood: Int) {
    val ink = FreshLook.Ink
    val coats = intArrayOf(0xFF4A7BB7.toInt(), 0xFF9B4F8E.toInt(), 0xFFB9783A.toInt(), 0xFF4F9A6B.toInt())
    val hairs = intArrayOf(0xFF3A2A1E.toInt(), 0xFFD9B04A.toInt(), 0xFF8A8A8A.toInt(), 0xFF1E1E22.toInt())
    val coat = Color(coats[look.coerceIn(0, 3)])
    val hair = Color(hairs[look.coerceIn(0, 3)])
    drawOval(Color.Black.copy(alpha = 0.2f), Offset(-30f, -7f), Size(60f, 12f))
    drawRoundRect(Color(0xFF30343C), Offset(-16f, -28f), Size(12f, 28f), CornerRadius(3f))
    drawRoundRect(Color(0xFF30343C), Offset(4f, -28f), Size(12f, 28f), CornerRadius(3f))
    drawRoundRect(coat, Offset(-24f, -80f), Size(48f, 56f), CornerRadius(12f))
    drawRoundRect(ink, Offset(-24f, -80f), Size(48f, 56f), CornerRadius(12f), style = Stroke(width = 2.4f))
    drawRoundRect(coat, Offset(-33f, -76f), Size(11f, 40f), CornerRadius(5f))
    drawRoundRect(coat, Offset(22f, -76f), Size(11f, 40f), CornerRadius(5f))
    val headY = -98f
    drawCircle(FreshLook.Skin, 19f, Offset(0f, headY))
    drawArc(hair, 180f, 180f, true, Offset(-19f, headY - 19f), Size(38f, 30f))
    drawCircle(ink, 19f, Offset(0f, headY), style = Stroke(width = 2.4f))
    drawCircle(ink, 2.2f, Offset(-7f, headY - 2f))
    drawCircle(ink, 2.2f, Offset(7f, headY - 2f))
    when {
        mood > 0 -> drawArc(ink, 20f, 140f, false, Offset(-8f, headY + 2f), Size(16f, 12f), style = Stroke(width = 2.2f, cap = StrokeCap.Round))
        mood < 0 -> drawArc(ink, 200f, 140f, false, Offset(-8f, headY + 8f), Size(16f, 12f), style = Stroke(width = 2.2f, cap = StrokeCap.Round))
        else -> drawLine(ink, Offset(-6f, headY + 10f), Offset(6f, headY + 10f), strokeWidth = 2.2f, cap = StrokeCap.Round)
    }
}

/** Le contrôleur d'hygiène, les pieds en (0, 0) : blouse blanche, charlotte bleue, presse-papiers. */
private fun DrawScope.hygieneInspector() {
    val ink = FreshLook.Ink
    drawOval(Color.Black.copy(alpha = 0.2f), Offset(-30f, -7f), Size(60f, 12f))
    drawRoundRect(Color(0xFF30343C), Offset(-16f, -28f), Size(12f, 28f), CornerRadius(3f))
    drawRoundRect(Color(0xFF30343C), Offset(4f, -28f), Size(12f, 28f), CornerRadius(3f))
    drawRoundRect(Color.White, Offset(-26f, -82f), Size(52f, 60f), CornerRadius(10f))
    drawRoundRect(ink, Offset(-26f, -82f), Size(52f, 60f), CornerRadius(10f), style = Stroke(width = 2.4f))
    drawRoundRect(Color.White, Offset(-35f, -78f), Size(11f, 40f), CornerRadius(5f))
    drawRoundRect(ink, Offset(-35f, -78f), Size(11f, 40f), CornerRadius(5f), style = Stroke(width = 1.8f))
    // Le presse-papiers, tenu devant lui.
    drawRoundRect(Color(0xFF8A6238), Offset(12f, -66f), Size(24f, 32f), CornerRadius(3f))
    drawRect(Color.White, Offset(15f, -62f), Size(18f, 24f))
    drawLine(ink.fade(0.6f), Offset(17f, -56f), Offset(31f, -56f), strokeWidth = 1.5f)
    drawLine(ink.fade(0.6f), Offset(17f, -50f), Offset(31f, -50f), strokeWidth = 1.5f)
    drawLine(ink.fade(0.6f), Offset(17f, -44f), Offset(27f, -44f), strokeWidth = 1.5f)
    val headY = -100f
    drawCircle(FreshLook.Skin, 19f, Offset(0f, headY))
    drawArc(FreshLook.Blue, 180f, 180f, true, Offset(-21f, headY - 22f), Size(42f, 34f))
    drawCircle(ink, 19f, Offset(0f, headY), style = Stroke(width = 2.4f))
    // Des lunettes et une bouche sérieuse.
    drawCircle(ink, 5f, Offset(-7f, headY - 2f), style = Stroke(width = 1.8f))
    drawCircle(ink, 5f, Offset(7f, headY - 2f), style = Stroke(width = 1.8f))
    drawLine(ink, Offset(-6f, headY + 10f), Offset(6f, headY + 10f), strokeWidth = 2.2f, cap = StrokeCap.Round)
}

/** Les clients du meuble, avec dans leur bulle le produit qu'ils veulent (puis une coche ou une croix une fois servis). */
private fun DrawScope.shoppers(game: FreshGame) {
    val ink = FreshLook.Ink
    for (customer in game.customers) {
        val target = slotX(customer.product)
        val entry = if (customer.fromLeft) -30f else Fresh.WIDTH + 30f
        val x = if (customer.ms < Fresh.WALK_MS) {
            val t = (customer.ms / Fresh.WALK_MS).coerceIn(0f, 1f)
            entry + (target - entry) * t
        } else {
            val t = ((customer.ms - Fresh.WALK_MS) / Fresh.LEAVE_MS).coerceIn(0f, 1f)
            target + (entry - target) * t
        }
        withTransform({
            translate(x, FreshLook.CUSTOMER_FEET)
            scale(FreshLook.CUSTOMER_SCALE, FreshLook.CUSTOMER_SCALE, pivot = Offset.Zero)
        }) {
            shopper(customer.look, customer.mood)
            // La bulle au-dessus de la tête.
            drawCircle(Color.White, 20f, Offset(0f, -146f))
            drawCircle(ink, 20f, Offset(0f, -146f), style = Stroke(width = 2.4f))
            when {
                !customer.done -> freshIcon(customer.product, 0f, -146f, 26f)
                customer.mood > 0 -> freshCheck(0f, -146f, 10f, FreshLook.GreenDark, 4f)
                else -> freshCross(0f, -146f, 9f, FreshLook.Red, 4f)
            }
        }
    }
}

/** Le contrôleur d'hygiène : il entre par la droite pendant le compte à rebours, puis reste le temps du verdict. */
private fun DrawScope.inspectorFigure(game: FreshGame) {
    if (game.inspect == FRESH_INSPECT_NONE) return
    val ink = FreshLook.Ink
    val stop = Fresh.WIDTH - 110f
    val x = if (game.inspect == FRESH_INSPECT_WARN) {
        val t = ((Fresh.INSPECT_WARN_MS - game.inspectMs) / 1_200f).coerceIn(0f, 1f)
        Fresh.WIDTH + 40f + (stop - Fresh.WIDTH - 40f) * t
    } else {
        stop
    }
    withTransform({
        translate(x, FreshLook.CUSTOMER_FEET)
        scale(FreshLook.CUSTOMER_SCALE, FreshLook.CUSTOMER_SCALE, pivot = Offset.Zero)
    }) {
        hygieneInspector()
        if (game.inspect == FRESH_INSPECT_VERDICT) {
            drawCircle(Color.White, 20f, Offset(0f, -146f))
            drawCircle(ink, 20f, Offset(0f, -146f), style = Stroke(width = 2.4f))
            if (game.inspectPass) freshCheck(0f, -146f, 10f, FreshLook.GreenDark, 4f) else freshCross(0f, -146f, 9f, FreshLook.Red, 4f)
        }
    }
}

/** Le bandeau du contrôle d'hygiène, sur l'enseigne : le compte à rebours, puis le verdict. */
private fun DrawScope.inspectionBanner(game: FreshGame, labels: FreshLabels, measurer: TextMeasurer) {
    if (game.inspect == FRESH_INSPECT_NONE) return
    val ink = FreshLook.Ink
    val flash = 0.5f + 0.5f * sin(game.clockMs / 120f)
    val verdict = game.inspect == FRESH_INSPECT_VERDICT
    val border = when {
        !verdict -> FreshLook.Red.fade(0.6f + 0.4f * flash)
        game.inspectPass -> FreshLook.GreenDark
        else -> FreshLook.Red
    }
    val left = Fresh.WIDTH / 2f - 160f
    drawRoundRect(Color.White, Offset(left, 6f), Size(320f, 48f), CornerRadius(10f))
    drawRoundRect(border, Offset(left, 6f), Size(320f, 48f), CornerRadius(10f), style = Stroke(width = 3.5f))
    if (verdict) {
        val index = if (game.inspectPass) FRESH_EVENT_INSPECT_OK else FRESH_EVENT_INSPECT_KO
        freshWord(measurer, labels.events.getOrElse(index) { "" }, Fresh.WIDTH / 2f, 30f, 22f, border, italic = true)
    } else {
        freshWord(measurer, labels.inspector, Fresh.WIDTH / 2f, 19f, 14f, ink)
        val seconds = max(0, ceil(game.inspectMs / 1000f).toInt())
        freshWord(measurer, seconds.toString(), Fresh.WIDTH / 2f, 40f, 22f, FreshLook.Red)
    }
}

/** La ligne de message sous les fautes : le dernier fait marquant et ses points ; sinon l'alerte de la porte ouverte. */
private fun DrawScope.message(game: FreshGame, labels: FreshLabels, measurer: TextMeasurer) {
    if (game.stage != FRESH_STAGE_PLAY) return
    if (game.eventMs > 0f && game.lastEvent != FRESH_EVENT_NONE) {
        val points = game.lastPoints
        val good = points >= 0
        val rise = (1f - game.eventMs / Fresh.EVENT_MS) * 6f
        val text = labels.events.getOrElse(game.lastEvent) { "" } + " " + (if (good) "+$points" else points.toString())
        freshWord(measurer, text, Fresh.WIDTH / 2f, 100f - rise, 16f, if (good) FreshLook.GreenDark else FreshLook.Red, italic = true)
    } else if (game.doorOpen) {
        val flash = 0.6f + 0.4f * sin(game.clockMs / 130f)
        freshWord(measurer, labels.doorAlert, Fresh.WIDTH / 2f, 100f, 16f, FreshLook.Red.fade(flash), italic = true)
    }
}

/** Tout ce qui change pendant la partie, dans le repère de 720 x 405 de l'écran. */
internal fun DrawScope.drawFreshLive(game: FreshGame, sprites: DriveSprites, labels: FreshLabels, measurer: TextMeasurer) {
    hud(measurer, labels, game)
    display(game, measurer)
    cart(game, measurer)
    door(game, measurer)
    // Un meuble ouvert à tous les vents : un liseré rouge qui clignote autour de lui.
    if (game.doorOpen && game.stage == FRESH_STAGE_PLAY) {
        val flash = 0.4f + 0.4f * sin(game.clockMs / 150f)
        drawRoundRect(
            FreshLook.Red.fade(flash),
            Offset(FreshLook.CASE_X0 - 6f, FreshLook.CASE_TOP - 5f),
            Size(FreshLook.CASE_X1 - FreshLook.CASE_X0 + 12f, FreshLook.CASE_BOTTOM - FreshLook.CASE_TOP + 5f),
            CornerRadius(8f),
            style = Stroke(width = 4f)
        )
    }
    // L'allée, du fond vers l'avant : le contrôleur, les clients, puis le joueur.
    inspectorFigure(game)
    shoppers(game)
    hero(game, sprites, measurer)
    inspectionBanner(game, labels, measurer)
    message(game, labels, measurer)

    if (game.stage == FRESH_STAGE_OPENING) {
        val pulse = 1f + 0.06f * sin(game.clockMs / 110f)
        freshWord(measurer, labels.opening, Fresh.WIDTH / 2f, 200f, 40f * pulse, FreshLook.GreenDark, italic = true, spacing = 3f)
    }
    val shade = min(0.25f, game.blockedMs / Fresh.BLOCKED_MS * 0.25f)
    if (shade > 0f) {
        drawRect(FreshLook.Red.fade(shade), size = Size(Fresh.WIDTH, Fresh.HEIGHT))
        freshWord(measurer, labels.no, Fresh.WIDTH / 2f, 200f, 44f, FreshLook.Red, italic = true)
    }
    if (game.faultMs > 0f) {
        drawRect(FreshLook.Red.fade(0.22f * game.faultMs / Fresh.FAULT_MS), size = Size(Fresh.WIDTH, Fresh.HEIGHT))
    }
}
