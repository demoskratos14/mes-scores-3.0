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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Les dessins de l'écran de la Boucherie, dans le repère de l'écran (720 x 405), le même que celui de Drive et de Poissonnerie.
 * De haut en bas : l'auvent et l'enseigne, un panneau (la carcasse pendant la découpe, la trancheuse pendant le service, tout
 * l'atelier pendant le bonus), le boucher derrière la chambre froide vitrée (douze cases, une pièce par case, avec sa fraîcheur
 * et son poids), puis le sol : le plan de travail à gauche (avec la trancheuse et la saleté de la volaille), le client à droite.
 * Le décor qui ne bouge pas se dessine une fois avec [drawButcherScene] ; tout ce qui bouge avec [drawButcherLive].
 * Le boucher est le personnage de Drive ([DriveSprites]).
 */
internal class ButcherLabels(
    val time: String,
    val score: String,
    val sign: String,
    val hygiene: String,
    val stageCut: String,
    val stageService: String,
    val stageBonus: String,
    val opening: String,
    /** Le message de chaque geste refusé, indexé par BUTCHER_BLOCK_* (la case 0 est vide). */
    val blocks: List<String>,
    val gram: String,
    val carcass: String,
    val slice: String,
    val dirty: String,
    val cleaning: String,
    val inspection: String,
    val inspectOk: String,
    val inspectFine: String,
    val fault: String,
    val bone: String,
    val burst: String,
    val bonusHelp: String,
    /** Le nom de chaque viande : bœuf, porc, agneau, volaille. */
    val meats: List<String>,
    /** Le texte de chaque verdict, indexé par BUTCHER_VERDICT_* (la case 0 est vide). */
    val verdicts: List<String>
)

private object ButcherLook {
    val Ink = Color(0xFF20241F)
    val Red = Color(0xFFC8102E)
    val RedDark = Color(0xFF8E0B20)
    val Metal = Color(0xFFB9C4C9)
    val Glass = Color(0xFFE6F1F4)
    val Wood = Color(0xFF8A6238)
    val WoodDark = Color(0xFF5E4125)
    val Skin = Color(0xFFD99B79)
    val Green = Color(0xFF2E9E4F)
    val GreenDark = Color(0xFF176B33)
    val Amber = Color(0xFFF0B429)
    val Bone = Color(0xFFF7F1E3)

    // Le mur finit ici ; le sol commence.
    const val WALL_END = 250f

    // La chambre froide : un fond vitré (douze cases), sa face en inox avec le poids de chaque pièce.
    const val CASE_Y = 164f
    const val CASE_H = 62f
    const val FRONT_Y = 226f
    const val FRONT_H = 36f
    const val SLOT_X0 = 12f
    const val SLOT_STEP = 58f
    const val SLOT_W = 54f
    const val TAG_Y = 233f
    const val TAG_H = 22f

    // Le panneau sous les compteurs (carcasse, trancheuse).
    const val PANEL_Y = 86f
    const val PANEL_H = 74f

    // Le boucher derrière la chambre froide (ses jambes sont cachées) ; le client devant, sur le sol.
    const val HERO_X = 360f
    const val HERO_FEET = 200f
    const val HERO_SCALE = 1.1f
    const val CUSTOMER_X = 640f
    const val CUSTOMER_FEET = 396f

    // Le plan de travail, au premier plan à gauche.
    const val BLOCK_X0 = 28f
    const val BLOCK_X1 = 348f
    const val BLOCK_TOP = 338f

    // L'atelier de saucisses : la sortie du boyau et l'échelle (pixels par millimètre).
    const val NOZZLE_X = 598f
    const val ROPE_Y = 262f
    const val MM = 4.4f
}

private fun Color.bFade(a: Float): Color = copy(alpha = alpha * a)

private fun slotCenterX(slot: Int): Float = ButcherLook.SLOT_X0 + ButcherLook.SLOT_W / 2f + slot * ButcherLook.SLOT_STEP

private fun meatColor(meat: Int): Color = when (meat) {
    BUTCHER_BEEF -> Color(0xFFC8454B)
    BUTCHER_PORK -> Color(0xFFF0A8A8)
    BUTCHER_LAMB -> Color(0xFFA84B46)
    else -> Color(0xFFEBC27E)
}

/** Un texte centré (ou aligné) en ([x], [y]), de [px] pixels de l'écran de hauteur de police ; [align] : -1 à gauche, 0 centré, 1 à droite. */
private fun DrawScope.bWord(
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

// ------------------------------------------------------------------------------------------------------------ les viandes

/** Un morceau de [meat] centré en ([cx], [cy]), à l'échelle [s] (1 : environ 60 unités de large) : steak, côte de porc, côtelette d'agneau, pilon. */
private fun DrawScope.meatShape(meat: Int, cx: Float, cy: Float, s: Float) {
    val ink = ButcherLook.Ink
    val color = meatColor(meat)
    when (meat) {
        BUTCHER_BEEF -> {
            val body = Path().apply {
                moveTo(cx - 28f * s, cy)
                cubicTo(cx - 28f * s, cy - 20f * s, cx - 6f * s, cy - 24f * s, cx + 12f * s, cy - 20f * s)
                cubicTo(cx + 30f * s, cy - 16f * s, cx + 32f * s, cy + 8f * s, cx + 16f * s, cy + 18f * s)
                cubicTo(cx + 2f * s, cy + 26f * s, cx - 26f * s, cy + 22f * s, cx - 28f * s, cy)
                close()
            }
            drawPath(body, color)
            drawPath(body, Color(0xFFF3D9D0), style = Stroke(width = 3.4f * s, join = StrokeJoin.Round))
            drawLine(Color.White.bFade(0.55f), Offset(cx - 10f * s, cy - 6f * s), Offset(cx + 6f * s, cy - 2f * s), strokeWidth = 2f * s, cap = StrokeCap.Round)
            drawLine(Color.White.bFade(0.55f), Offset(cx - 4f * s, cy + 8f * s), Offset(cx + 14f * s, cy + 6f * s), strokeWidth = 2f * s, cap = StrokeCap.Round)
            drawPath(body, ink, style = Stroke(width = 2.2f, join = StrokeJoin.Round))
        }
        BUTCHER_PORK -> {
            drawOval(color, Offset(cx - 26f * s, cy - 18f * s), Size(52f * s, 36f * s))
            drawOval(Color(0xFFFFE0E0), Offset(cx - 26f * s, cy - 18f * s), Size(52f * s, 36f * s), style = Stroke(width = 3.4f * s))
            drawOval(ink, Offset(cx - 26f * s, cy - 18f * s), Size(52f * s, 36f * s), style = Stroke(width = 2.2f))
            drawCircle(ButcherLook.Bone, 7f * s, Offset(cx - 8f * s, cy))
            drawCircle(ink, 7f * s, Offset(cx - 8f * s, cy), style = Stroke(width = 2f))
        }
        BUTCHER_LAMB -> {
            drawRoundRect(ButcherLook.Bone, Offset(cx + 8f * s, cy - 4f * s), Size(26f * s, 8f * s), CornerRadius(4f * s))
            drawRoundRect(ink, Offset(cx + 8f * s, cy - 4f * s), Size(26f * s, 8f * s), CornerRadius(4f * s), style = Stroke(width = 2f))
            drawCircle(ButcherLook.Bone, 6f * s, Offset(cx + 34f * s, cy))
            drawCircle(ink, 6f * s, Offset(cx + 34f * s, cy), style = Stroke(width = 2f))
            drawOval(color, Offset(cx - 28f * s, cy - 15f * s), Size(44f * s, 30f * s))
            drawOval(Color(0xFFF0CFC8), Offset(cx - 28f * s, cy - 15f * s), Size(44f * s, 30f * s), style = Stroke(width = 3f * s))
            drawOval(ink, Offset(cx - 28f * s, cy - 15f * s), Size(44f * s, 30f * s), style = Stroke(width = 2.2f))
        }
        else -> {
            drawRoundRect(ButcherLook.Bone, Offset(cx + 6f * s, cy - 4f * s), Size(26f * s, 8f * s), CornerRadius(4f * s))
            drawRoundRect(ink, Offset(cx + 6f * s, cy - 4f * s), Size(26f * s, 8f * s), CornerRadius(4f * s), style = Stroke(width = 2f))
            drawCircle(ButcherLook.Bone, 5f * s, Offset(cx + 33f * s, cy - 5f * s))
            drawCircle(ButcherLook.Bone, 5f * s, Offset(cx + 33f * s, cy + 5f * s))
            drawCircle(ink, 5f * s, Offset(cx + 33f * s, cy - 5f * s), style = Stroke(width = 2f))
            drawCircle(ink, 5f * s, Offset(cx + 33f * s, cy + 5f * s), style = Stroke(width = 2f))
            drawOval(color, Offset(cx - 30f * s, cy - 18f * s), Size(46f * s, 36f * s))
            drawOval(Color(0xFFF7DDB0), Offset(cx - 22f * s, cy - 12f * s), Size(26f * s, 14f * s))
            drawOval(ink, Offset(cx - 30f * s, cy - 18f * s), Size(46f * s, 36f * s), style = Stroke(width = 2.2f))
        }
    }
}

// ------------------------------------------------------------------------------------------------------------ le décor

/** Le décor fixe : le mur carrelé, l'auvent rayé, l'enseigne et le sol. */
internal fun DrawScope.drawButcherScene(labels: ButcherLabels, measurer: TextMeasurer) {
    val w = Butcher.WIDTH
    val h = Butcher.HEIGHT
    val ink = ButcherLook.Ink
    // Le mur carrelé, crème.
    drawRect(
        brush = Brush.verticalGradient(0f to Color(0xFFF8F4EC), 1f to Color(0xFFE6DDCC), startY = 0f, endY = ButcherLook.WALL_END),
        size = Size(w, ButcherLook.WALL_END)
    )
    var x = 0f
    while (x < w) {
        drawLine(ink.bFade(0.07f), Offset(x, 0f), Offset(x, ButcherLook.WALL_END), strokeWidth = 1.5f)
        x += 48f
    }
    var y = 0f
    while (y < ButcherLook.WALL_END) {
        drawLine(ink.bFade(0.07f), Offset(0f, y), Offset(w, y), strokeWidth = 1.5f)
        y += 36f
    }
    // L'auvent rayé rouge et blanc, avec ses festons.
    var stripe = 0
    var ax = 0f
    while (ax < w) {
        val color = if (stripe % 2 == 0) ButcherLook.Red else Color.White
        drawRect(color, Offset(ax, 0f), Size(30f, 7f))
        drawArc(color, 0f, 180f, true, Offset(ax, 0f), Size(30f, 14f))
        stripe++
        ax += 30f
    }
    drawLine(ink.bFade(0.5f), Offset(0f, 0f), Offset(w, 0f), strokeWidth = 2f)
    // L'enseigne, au milieu du mur.
    drawRoundRect(Color.White, Offset(w / 2f - 90f, 18f), Size(180f, 38f), CornerRadius(8f))
    drawRoundRect(ink, Offset(w / 2f - 90f, 18f), Size(180f, 38f), CornerRadius(8f), style = Stroke(width = 3f))
    drawRect(ButcherLook.Red, Offset(w / 2f - 90f, 48f), Size(180f, 4f))
    bWord(measurer, labels.sign, w / 2f, 34f, 22f, ButcherLook.Red, italic = true, spacing = 3f)

    // Le sol carrelé, avec une plinthe au pied du mur.
    drawRect(
        brush = Brush.verticalGradient(0f to Color(0xFFDDD5C8), 1f to Color(0xFFC4B9A8), startY = ButcherLook.WALL_END, endY = h),
        topLeft = Offset(0f, ButcherLook.WALL_END),
        size = Size(w, h - ButcherLook.WALL_END)
    )
    var fx = 0f
    while (fx < w) {
        drawLine(ink.bFade(0.08f), Offset(fx, ButcherLook.WALL_END), Offset(fx, h), strokeWidth = 1.5f)
        fx += 60f
    }
    var fy = ButcherLook.WALL_END + 30f
    while (fy < h) {
        drawLine(ink.bFade(0.08f), Offset(0f, fy), Offset(w, fy), strokeWidth = 1.5f)
        fy += 30f
    }
    drawRect(Color(0xFF9A8F80), Offset(0f, ButcherLook.WALL_END - 6f), Size(w, 6f))
    drawRect(ink, Offset(0f, ButcherLook.WALL_END - 6f), Size(w, 2f))
}

// ------------------------------------------------------------------------------------------------------------ ce qui bouge

/** Tout ce qui change pendant la partie, dans le repère de 720 x 405 de l'écran. */
internal fun DrawScope.drawButcherLive(game: ButcherGame, sprites: DriveSprites, labels: ButcherLabels, measurer: TextMeasurer) {
    val ink = ButcherLook.Ink
    val playing = game.stage == BUTCHER_STAGE_CUT || game.stage == BUTCHER_STAGE_SERVICE || game.stage == BUTCHER_STAGE_BONUS
    hud(measurer, labels, game, urgent = playing && game.displayLeftMs <= 10_000f)
    if (playing) {
        val title = when (game.stage) {
            BUTCHER_STAGE_CUT -> labels.stageCut
            BUTCHER_STAGE_BONUS -> labels.stageBonus
            else -> labels.stageService
        }
        bWord(measurer, title, Butcher.WIDTH / 2f, 68f, 14f, ink.bFade(0.6f))
    }
    if (game.stage != BUTCHER_STAGE_OVER) hygieneMeter(game, labels, measurer)

    // Du fond vers l'avant : le boucher, la chambre froide, le plan de travail, puis le panneau et le client.
    butcherHero(game, sprites)
    chamber(game, measurer)
    if (game.stage == BUTCHER_STAGE_CUT || game.stage == BUTCHER_STAGE_SERVICE || game.stage == BUTCHER_STAGE_OPENING) {
        workBlock(game, labels, measurer)
    }
    when (game.stage) {
        BUTCHER_STAGE_CUT -> cutPanel(game, labels, measurer)
        BUTCHER_STAGE_SERVICE -> serviceFront(game, labels, measurer)
        BUTCHER_STAGE_BONUS -> workshop(game, labels, measurer)
        BUTCHER_STAGE_OPENING -> {
            val pulse = 1f + 0.06f * sin(game.clockMs / 110f)
            bWord(measurer, labels.opening, Butcher.WIDTH / 2f, 120f, 40f * pulse, ButcherLook.Red, italic = true, spacing = 3f)
        }
        else -> {}
    }
    if (game.stage == BUTCHER_STAGE_SERVICE) inspector(game, labels, measurer)

    // Un geste refusé, ou une faute : un voile rouge et le message.
    if (game.stage != BUTCHER_STAGE_BONUS) {
        val shade = min(0.25f, game.blockedMs / Butcher.BLOCKED_MS * 0.25f)
        if (shade > 0f) {
            drawRect(ButcherLook.Red.bFade(shade), size = Size(Butcher.WIDTH, Butcher.HEIGHT))
            val text = if (game.stage == BUTCHER_STAGE_CUT && game.blockedKind == BUTCHER_BLOCK_WRONG) {
                labels.bone
            } else {
                labels.blocks.getOrElse(game.blockedKind) { "" }
            }
            bWord(measurer, text, Butcher.WIDTH / 2f, 124f, 40f, ButcherLook.Red, italic = true)
        }
    }
    if (game.faultMs > 0f) {
        val fade = (game.faultMs / Butcher.FAULT_MS).coerceIn(0f, 1f)
        drawRect(ButcherLook.Red.bFade(0.2f * fade), size = Size(Butcher.WIDTH, Butcher.HEIGHT))
        bWord(measurer, labels.fault, 190f, 306f, 22f, ButcherLook.Red.bFade(fade), italic = true)
    }
}

/** Les compteurs du haut de l'écran : le temps à gauche, le score à droite. */
private fun DrawScope.hud(measurer: TextMeasurer, labels: ButcherLabels, game: ButcherGame, urgent: Boolean) {
    val ink = ButcherLook.Ink
    bWord(measurer, labels.time, 16f, 24f, 14f, ink.bFade(0.6f), align = -1)
    bWord(measurer, fishClockText(game.displayLeftMs), 16f, 62f, 46f, if (urgent) ButcherLook.Red else ink, align = -1)
    bWord(measurer, labels.score, Butcher.WIDTH - 16f, 24f, 14f, ink.bFade(0.6f), align = 1)
    bWord(measurer, game.score.toString(), Butcher.WIDTH - 16f, 62f, 46f, ink, align = 1)
}

/** Les fautes d'hygiène, à droite de l'enseigne : trois cases, une qui se remplit à chaque faute. */
private fun DrawScope.hygieneMeter(game: ButcherGame, labels: ButcherLabels, measurer: TextMeasurer) {
    val ink = ButcherLook.Ink
    val left = 478f
    bWord(measurer, labels.hygiene, left + 36f, 15f, 12f, ink.bFade(0.6f))
    for (i in 0 until Butcher.MAX_FAULTS) {
        val x = left + i * 26f
        val filled = i < game.faults
        drawRoundRect(if (filled) ButcherLook.Red else Color.White.bFade(0.8f), Offset(x, 26f), Size(20f, 20f), CornerRadius(5f))
        drawRoundRect(ink.bFade(if (filled) 1f else 0.45f), Offset(x, 26f), Size(20f, 20f), CornerRadius(5f), style = Stroke(width = 2f))
        if (filled) {
            drawLine(Color.White, Offset(x + 5f, 31f), Offset(x + 15f, 41f), strokeWidth = 2.6f, cap = StrokeCap.Round)
            drawLine(Color.White, Offset(x + 15f, 31f), Offset(x + 5f, 41f), strokeWidth = 2.6f, cap = StrokeCap.Round)
        }
    }
}

/** Le boucher (le personnage de Drive, de face) derrière la chambre froide, qui lui cache les jambes : il saute de joie quand le client est content. */
private fun DrawScope.butcherHero(game: ButcherGame, sprites: DriveSprites) {
    val image = sprites.front ?: return
    val w = (image.width * ButcherLook.HERO_SCALE).roundToInt()
    val h = (image.height * ButcherLook.HERO_SCALE).roundToInt()
    val happy = game.step == BUTCHER_STEP_RESULT && game.stage == BUTCHER_STAGE_SERVICE &&
        (game.verdict == BUTCHER_VERDICT_PERFECT || game.verdict == BUTCHER_VERDICT_GOOD)
    val hop = if (happy) abs(sin(game.resultMs / 110f)) * 8f else 0f
    val shake = if (game.blockedMs > 0f) sin(game.clockMs / 25f) * 3f else 0f
    val cx = ButcherLook.HERO_X + shake
    val top = ButcherLook.HERO_FEET - h - hop
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
 * La chambre froide : un fond vitré avec une case par pièce (la viande, une barre de fraîcheur verte, orange puis rouge), et sur la
 * face en inox le poids de chaque pièce en grammes. Pendant le service, la pièce choisie est repérée par une flèche rouge, une
 * lueur et son étiquette rouge ; la pièce en train d'être tranchée est verte ; une pièce jetée clignote en rouge.
 */
private fun DrawScope.chamber(game: ButcherGame, measurer: TextMeasurer) {
    val ink = ButcherLook.Ink
    val w = Butcher.WIDTH
    val inService = game.stage == BUTCHER_STAGE_SERVICE
    val picking = inService && game.step == BUTCHER_STEP_PICK
    val pulse = 0.5f + 0.5f * sin(game.clockMs / 140f)
    // Le rebord en inox, puis le fond vitré.
    drawRect(ButcherLook.Metal, Offset(0f, ButcherLook.CASE_Y - 10f), Size(w, 12f))
    drawRect(ink, Offset(0f, ButcherLook.CASE_Y - 10f), Size(w, 2.5f))
    drawRect(
        brush = Brush.verticalGradient(0f to Color(0xFFF2F8FA), 1f to ButcherLook.Glass, startY = ButcherLook.CASE_Y, endY = ButcherLook.CASE_Y + ButcherLook.CASE_H),
        topLeft = Offset(0f, ButcherLook.CASE_Y),
        size = Size(w, ButcherLook.CASE_H)
    )
    for (slot in 0 until Butcher.SLOTS) {
        val cx = slotCenterX(slot)
        val left = cx - ButcherLook.SLOT_W / 2f
        val piece = game.pieces[slot]
        val selected = inService && !game.over && slot == game.cursor
        val taken = inService && game.step == BUTCHER_STEP_SLICE && slot == game.pickedSlot
        if (piece == null) {
            drawRoundRect(ink.bFade(0.12f), Offset(left, ButcherLook.CASE_Y + 4f), Size(ButcherLook.SLOT_W, ButcherLook.CASE_H - 8f), CornerRadius(8f), style = Stroke(width = 1.5f))
        } else {
            if (taken) {
                drawRoundRect(ButcherLook.Green.bFade(0.35f), Offset(left, ButcherLook.CASE_Y), Size(ButcherLook.SLOT_W, ButcherLook.CASE_H), CornerRadius(8f))
            } else if (selected && picking) {
                drawRoundRect(ButcherLook.Red.bFade(0.10f + 0.12f * pulse), Offset(left, ButcherLook.CASE_Y), Size(ButcherLook.SLOT_W, ButcherLook.CASE_H), CornerRadius(8f))
            }
            // Plus la pièce est légère, plus elle est petite.
            val share = piece.weightG.toFloat() / Butcher.pieceWeightG(piece.meat)
            val scale = 0.42f + 0.2f * share.coerceIn(0f, 1f)
            meatShape(piece.meat, cx - 4f, ButcherLook.CASE_Y + 24f, scale)
            // La fraîcheur : une barre qui se vide et passe du vert au rouge.
            val fresh = piece.fresh.coerceIn(0f, 1f)
            val bar = if (fresh > 0.5f) ButcherLook.Green else if (fresh > Butcher.SAVE_FRESH) ButcherLook.Amber else ButcherLook.Red
            drawRoundRect(ink.bFade(0.15f), Offset(left + 6f, ButcherLook.CASE_Y + 46f), Size(ButcherLook.SLOT_W - 12f, 7f), CornerRadius(3.5f))
            drawRoundRect(bar, Offset(left + 6f, ButcherLook.CASE_Y + 46f), Size((ButcherLook.SLOT_W - 12f) * fresh, 7f), CornerRadius(3.5f))
        }
        if (game.spoilSlot == slot && game.spoilMs > 0f) {
            val fade = game.spoilMs / Butcher.SPOIL_MS
            drawRoundRect(ButcherLook.Red.bFade(0.45f * fade), Offset(left, ButcherLook.CASE_Y), Size(ButcherLook.SLOT_W, ButcherLook.CASE_H), CornerRadius(8f))
            drawLine(Color.White.bFade(fade), Offset(cx - 12f, ButcherLook.CASE_Y + 14f), Offset(cx + 12f, ButcherLook.CASE_Y + 40f), strokeWidth = 4f, cap = StrokeCap.Round)
            drawLine(Color.White.bFade(fade), Offset(cx + 12f, ButcherLook.CASE_Y + 14f), Offset(cx - 12f, ButcherLook.CASE_Y + 40f), strokeWidth = 4f, cap = StrokeCap.Round)
        }
    }
    // La face en inox et, dessus, une étiquette de poids par pièce.
    drawRect(ButcherLook.Metal, Offset(0f, ButcherLook.FRONT_Y), Size(w, ButcherLook.FRONT_H))
    drawRect(Color.White.bFade(0.35f), Offset(0f, ButcherLook.FRONT_Y), Size(w, 5f))
    drawRect(ink, Offset(0f, ButcherLook.FRONT_Y), Size(w, 2.5f))
    drawRect(ink, Offset(0f, ButcherLook.FRONT_Y + ButcherLook.FRONT_H - 2.5f), Size(w, 2.5f))
    for (slot in 0 until Butcher.SLOTS) {
        val piece = game.pieces[slot] ?: continue
        val cx = slotCenterX(slot)
        val selected = picking && !game.over && slot == game.cursor
        val taken = inService && game.step == BUTCHER_STEP_SLICE && slot == game.pickedSlot
        val fill = when {
            selected -> ButcherLook.Red
            taken -> ButcherLook.Green
            else -> Color.White
        }
        val textColor = if (selected || taken) Color.White else ink
        val tagLeft = cx - ButcherLook.SLOT_W / 2f + 2f
        drawRoundRect(fill, Offset(tagLeft, ButcherLook.TAG_Y), Size(ButcherLook.SLOT_W - 4f, ButcherLook.TAG_H), CornerRadius(5f))
        drawRoundRect(ink, Offset(tagLeft, ButcherLook.TAG_Y), Size(ButcherLook.SLOT_W - 4f, ButcherLook.TAG_H), CornerRadius(5f), style = Stroke(width = 2f))
        bWord(measurer, piece.weightG.toString(), cx, ButcherLook.TAG_Y + ButcherLook.TAG_H / 2f, 13f, textColor)
    }
    // La flèche rouge au-dessus de la pièce choisie.
    if (picking && !game.over && game.pieces[game.cursor] != null) {
        val cx = slotCenterX(game.cursor)
        val bob = sin(game.clockMs / 140f) * 2f
        val arrow = Path()
        arrow.moveTo(cx, ButcherLook.CASE_Y - 14f + bob)
        arrow.lineTo(cx - 10f, ButcherLook.CASE_Y - 30f + bob)
        arrow.lineTo(cx + 10f, ButcherLook.CASE_Y - 30f + bob)
        arrow.close()
        drawPath(arrow, ButcherLook.Red)
        drawPath(arrow, ink, style = Stroke(width = 2f, join = StrokeJoin.Round))
    }
}

// ------------------------------------------------------------------------------------------------------------ le plan de travail

/**
 * Le plan de travail, au premier plan à gauche : un billot en bois. Un plan sale (après la volaille) se couvre d'une tache verte
 * et de mouches ; le nettoyage passe une éponge dessus. Pendant le service, la trancheuse est posée dessus : la pièce avance
 * vers la lame tant qu'on tient A, et une tranche tombe quand on relâche.
 */
private fun DrawScope.workBlock(game: ButcherGame, labels: ButcherLabels, measurer: TextMeasurer) {
    val ink = ButcherLook.Ink
    val x0 = ButcherLook.BLOCK_X0
    val x1 = ButcherLook.BLOCK_X1
    val top = ButcherLook.BLOCK_TOP
    // Les pieds, puis le plateau en bois.
    drawRoundRect(ButcherLook.WoodDark, Offset(x0 + 18f, top + 18f), Size(26f, 46f), CornerRadius(3f))
    drawRoundRect(ButcherLook.WoodDark, Offset(x1 - 44f, top + 18f), Size(26f, 46f), CornerRadius(3f))
    drawRoundRect(ButcherLook.Wood, Offset(x0, top), Size(x1 - x0, 22f), CornerRadius(6f))
    drawLine(ink.bFade(0.35f), Offset(x0 + 8f, top + 8f), Offset(x1 - 8f, top + 8f), strokeWidth = 1.5f)
    drawRoundRect(ink, Offset(x0, top), Size(x1 - x0, 22f), CornerRadius(6f), style = Stroke(width = 2.6f))

    // La saleté : une tache verte sur le plateau et trois mouches qui tournent.
    if (game.dirty && game.cleanMs <= 0f) {
        drawOval(ButcherLook.Green.bFade(0.5f), Offset(x0 + 40f, top - 3f), Size(150f, 12f))
        for (i in 0 until 3) {
            val angle = game.clockMs / 260f + i * 2.1f
            val fx = x0 + 110f + sin(angle) * (30f + i * 14f)
            val fy = top - 20f + sin(angle * 1.7f) * 8f - i * 6f
            drawCircle(ink, 2.6f, Offset(fx, fy))
            drawLine(ink.bFade(0.5f), Offset(fx - 4f, fy - 3f), Offset(fx + 4f, fy - 3f), strokeWidth = 1.4f)
        }
        bWord(measurer, labels.dirty, x0 + 115f, top - 44f, 15f, ButcherLook.GreenDark)
    }
    // Le nettoyage : l'éponge va et vient sur le plateau, avec des bulles.
    if (game.cleanMs > 0f) {
        val progress = 1f - game.cleanMs / Butcher.CLEAN_MS
        val sx = x0 + 40f + (0.5f + 0.5f * sin(progress * 18f)) * 130f
        drawRoundRect(Color(0xFFF2D04A), Offset(sx - 20f, top - 14f), Size(40f, 16f), CornerRadius(5f))
        drawRoundRect(ink, Offset(sx - 20f, top - 14f), Size(40f, 16f), CornerRadius(5f), style = Stroke(width = 2f))
        for (i in 0 until 4) {
            val bx = sx - 26f + i * 18f
            val by = top - 24f - ((progress * 60f + i * 13f) % 22f)
            drawCircle(Color.White.bFade(0.9f), 3.6f, Offset(bx, by))
            drawCircle(ink.bFade(0.5f), 3.6f, Offset(bx, by), style = Stroke(width = 1.2f))
        }
        bWord(measurer, labels.cleaning, x0 + 115f, top - 44f, 15f, ink.bFade(0.7f))
    }

    // La trancheuse, pendant le service : la lame ronde à droite, le chariot qui amène la pièce.
    if (game.stage != BUTCHER_STAGE_SERVICE || game.customerMeat < 0) return
    val slicing = game.step == BUTCHER_STEP_SLICE
    val flashing = game.sliceFlashMs > 0f
    if (!slicing && !flashing) return
    val bladeX = x1 - 62f
    val bladeY = top - 34f
    drawRoundRect(ButcherLook.Metal, Offset(bladeX - 34f, top - 52f), Size(96f, 54f), CornerRadius(10f))
    drawRoundRect(ink, Offset(bladeX - 34f, top - 52f), Size(96f, 54f), CornerRadius(10f), style = Stroke(width = 2.4f))
    drawCircle(Color.White, 25f, Offset(bladeX, bladeY))
    drawCircle(ink, 25f, Offset(bladeX, bladeY), style = Stroke(width = 2.6f))
    drawCircle(ButcherLook.Metal, 7f, Offset(bladeX, bladeY))
    val spin = game.clockMs / 55f * (if (game.holdA) 1f else 0.25f)
    for (i in 0 until 4) {
        val a = spin + i * 1.5708f
        drawLine(ink.bFade(0.35f), Offset(bladeX, bladeY), Offset(bladeX + kotlin.math.cos(a) * 24f, bladeY + sin(a) * 24f), strokeWidth = 1.6f)
    }
    // Le rail du chariot, la pièce qui avance, et la tranche qui tombe.
    drawLine(ink.bFade(0.6f), Offset(x0 + 20f, top - 2f), Offset(bladeX - 34f, top - 2f), strokeWidth = 3f, cap = StrokeCap.Round)
    val progress = (game.sliceG / Butcher.SLICE_MAX_G).coerceIn(0f, 1f)
    if (slicing) meatShape(game.customerMeat, x0 + 70f + progress * 100f, top - 22f, 0.7f)
    if (flashing) {
        val fall = 1f - game.sliceFlashMs / Butcher.SLICE_FLASH_MS
        drawOval(meatColor(game.customerMeat), Offset(bladeX - 10f, bladeY + 18f + fall * 14f), Size(22f, 8f))
        drawOval(ink, Offset(bladeX - 10f, bladeY + 18f + fall * 14f), Size(22f, 8f), style = Stroke(width = 1.6f))
    }
}

// ------------------------------------------------------------------------------------------------------------ la découpe

/** Le panneau de la découpe : la carcasse pendue à son rail (qui se balance), ses pointillés, et le couteau qui se déplace. */
private fun DrawScope.cutPanel(game: ButcherGame, labels: ButcherLabels, measurer: TextMeasurer) {
    val ink = ButcherLook.Ink
    val top = ButcherLook.PANEL_Y
    drawRoundRect(Color.White.bFade(0.96f), Offset(30f, top), Size(660f, ButcherLook.PANEL_H), CornerRadius(12f))
    drawRoundRect(ink, Offset(30f, top), Size(660f, ButcherLook.PANEL_H), CornerRadius(12f), style = Stroke(width = 3f))
    bWord(
        measurer,
        "${labels.carcass} ${min(game.carcassNumber, Butcher.CARCASSES)}/${Butcher.CARCASSES} · ${labels.meats.getOrElse(game.carcassMeat) { "" }}",
        46f,
        top + 14f,
        14f,
        ink.bFade(0.7f),
        align = -1
    )
    // Le rail.
    drawLine(ButcherLook.Metal, Offset(40f, top + 22f), Offset(680f, top + 22f), strokeWidth = 4f, cap = StrokeCap.Round)

    // La carcasse : elle descend en arrivant, glisse et s'efface en partant.
    val leaving = game.carcassLeaveMs > 0f
    val alpha = if (leaving) (game.carcassLeaveMs / Butcher.CARCASS_LEAVE_MS).coerceIn(0f, 1f) else 1f
    val drop = -(game.carcassArriveMs / Butcher.CARCASS_ARRIVE_MS) * 40f
    val sway = game.sway
    val bodyX = Butcher.CARCASS_X0 + sway
    val bodyY = top + 32f + drop
    val bodyW = Butcher.CARCASS_X1 - Butcher.CARCASS_X0
    val bodyH = 36f
    val color = meatColor(game.carcassMeat)
    drawLine(ink.bFade(0.7f * alpha), Offset(bodyX + 40f, top + 22f), Offset(bodyX + 40f, bodyY), strokeWidth = 2.6f)
    drawLine(ink.bFade(0.7f * alpha), Offset(bodyX + bodyW - 40f, top + 22f), Offset(bodyX + bodyW - 40f, bodyY), strokeWidth = 2.6f)
    drawRoundRect(color.bFade(alpha), Offset(bodyX, bodyY), Size(bodyW, bodyH), CornerRadius(14f))
    drawRoundRect(Color(0xFFF8E6DC).bFade(alpha), Offset(bodyX, bodyY), Size(bodyW, bodyH), CornerRadius(14f), style = Stroke(width = 4f))
    drawRoundRect(ink.bFade(alpha), Offset(bodyX, bodyY), Size(bodyW, bodyH), CornerRadius(14f), style = Stroke(width = 2.4f))
    // Les os : quelques traits clairs sur la viande.
    for (i in 0 until 5) {
        val bx = bodyX + 60f + i * 100f
        drawLine(ButcherLook.Bone.bFade(0.7f * alpha), Offset(bx, bodyY + 10f), Offset(bx + 26f, bodyY + 10f), strokeWidth = 3f, cap = StrokeCap.Round)
    }
    // Les pointillés : en pointillé tant qu'ils ne sont pas coupés, une fente ouverte après la coupe.
    for (i in 0 until Butcher.CUTS_PER_CARCASS) {
        val lx = game.lineX(i)
        if (game.carcassCut[i]) {
            drawRect(Color.White.bFade(0.95f * alpha), Offset(lx - 3f, bodyY - 2f), Size(6f, bodyH + 4f))
        } else {
            var y = bodyY - 3f
            while (y < bodyY + bodyH + 3f) {
                drawLine(ink.bFade(alpha), Offset(lx, y), Offset(lx, min(y + 4f, bodyY + bodyH + 3f)), strokeWidth = 2.6f)
                y += 8f
            }
        }
    }

    // Le couteau : un guide vertical (vert quand la coupe est bonne) et un fendoir au-dessus.
    if (!leaving) {
        val kx = game.knifeX
        val near = game.nearestLine()
        val distance = if (near >= 0) abs(kx - game.lineX(near)) else Float.MAX_VALUE
        val tolerance = game.cutTolerance
        val guide = when {
            game.stuckMs > 0f -> ButcherLook.Red
            distance <= tolerance * Butcher.PERFECT_RATIO -> ButcherLook.GreenDark
            distance <= tolerance -> ButcherLook.Green
            else -> ButcherLook.Red.bFade(0.7f)
        }
        drawLine(guide, Offset(kx, top + 26f), Offset(kx, top + ButcherLook.PANEL_H - 2f), strokeWidth = 3f, cap = StrokeCap.Round)
        val tilt = if (game.stuckMs > 0f) sin(game.clockMs / 30f) * 3f else 0f
        drawRoundRect(ButcherLook.WoodDark, Offset(kx - 4f + tilt, top - 10f), Size(8f, 18f), CornerRadius(3f))
        drawRoundRect(ButcherLook.Metal, Offset(kx - 11f + tilt, top + 6f), Size(22f, 26f), CornerRadius(4f))
        drawRoundRect(ink, Offset(kx - 11f + tilt, top + 6f), Size(22f, 26f), CornerRadius(4f), style = Stroke(width = 2.2f))
    }

    // Le résultat d'une coupe : des étincelles et le verdict.
    if (game.cutFlashMs > 0f) {
        val fade = game.cutFlashMs / Butcher.CUT_FLASH_MS
        val flash = if (game.cutFlashVerdict == BUTCHER_VERDICT_PERFECT) ButcherLook.GreenDark else ButcherLook.Green
        for (i in 0 until 6) {
            val a = i * 1.0472f
            val r1 = 8f + (1f - fade) * 10f
            val r2 = r1 + 8f
            drawLine(
                flash.bFade(fade),
                Offset(game.cutFlashX + kotlin.math.cos(a) * r1, top + 50f + sin(a) * r1),
                Offset(game.cutFlashX + kotlin.math.cos(a) * r2, top + 50f + sin(a) * r2),
                strokeWidth = 2.6f,
                cap = StrokeCap.Round
            )
        }
        bWord(measurer, labels.verdicts.getOrElse(game.cutFlashVerdict) { "" }, game.cutFlashX, top + ButcherLook.PANEL_H + 10f - (1f - fade) * 8f, 20f, flash.bFade(fade), italic = true)
    }
}

// ------------------------------------------------------------------------------------------------------------ le service

/** Un client debout sur le sol en [x], les pieds en [feet] ; [mood] : 1 content, -1 mécontent, 0 neutre. */
private fun DrawScope.bCustomer(look: Int, x: Float, feet: Float, mood: Int) {
    val ink = ButcherLook.Ink
    val coats = intArrayOf(0xFF4A7BB7.toInt(), 0xFF9B4F8E.toInt(), 0xFFB9783A.toInt(), 0xFF4F9A6B.toInt())
    val hairs = intArrayOf(0xFF3A2A1E.toInt(), 0xFFD9B04A.toInt(), 0xFF8A8A8A.toInt(), 0xFF1E1E22.toInt())
    val coat = Color(coats[look.coerceIn(0, 3)])
    val hair = Color(hairs[look.coerceIn(0, 3)])
    drawOval(Color.Black.copy(alpha = 0.2f), Offset(x - 30f, feet - 7f), Size(60f, 12f))
    drawRoundRect(Color(0xFF30343C), Offset(x - 16f, feet - 28f), Size(12f, 28f), CornerRadius(3f))
    drawRoundRect(Color(0xFF30343C), Offset(x + 4f, feet - 28f), Size(12f, 28f), CornerRadius(3f))
    drawRoundRect(coat, Offset(x - 24f, feet - 80f), Size(48f, 56f), CornerRadius(12f))
    drawRoundRect(ink, Offset(x - 24f, feet - 80f), Size(48f, 56f), CornerRadius(12f), style = Stroke(width = 2.4f))
    drawRoundRect(coat, Offset(x - 33f, feet - 76f), Size(11f, 40f), CornerRadius(5f))
    drawRoundRect(coat, Offset(x + 22f, feet - 76f), Size(11f, 40f), CornerRadius(5f))
    val headY = feet - 98f
    drawCircle(ButcherLook.Skin, 19f, Offset(x, headY))
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

/** La bulle du client : la viande, son nom, le nombre de tranches et le poids de chacune, et la barre de patience qui se vide. */
private fun DrawScope.orderBubble(game: ButcherGame, labels: ButcherLabels, measurer: TextMeasurer, alpha: Float) {
    val ink = ButcherLook.Ink
    val left = 392f
    val top = 282f
    val right = left + 204f
    val tailFill = Path()
    tailFill.moveTo(right - 2f, top + 18f)
    tailFill.lineTo(right + 18f, top + 30f)
    tailFill.lineTo(right - 2f, top + 42f)
    tailFill.close()
    val tailLine = Path()
    tailLine.moveTo(right, top + 18f)
    tailLine.lineTo(right + 18f, top + 30f)
    tailLine.lineTo(right, top + 42f)
    drawRoundRect(Color.White.bFade(alpha), Offset(left, top), Size(204f, 66f), CornerRadius(12f))
    drawPath(tailFill, Color.White.bFade(alpha))
    drawRoundRect(ink.bFade(alpha), Offset(left, top), Size(204f, 66f), CornerRadius(12f), style = Stroke(width = 3f))
    drawLine(Color.White.bFade(alpha), Offset(right, top + 19.5f), Offset(right, top + 40.5f), strokeWidth = 4f)
    drawPath(tailLine, ink.bFade(alpha), style = Stroke(width = 3f, join = StrokeJoin.Round))
    meatShape(game.customerMeat, left + 34f, top + 28f, 0.55f)
    bWord(measurer, labels.meats.getOrElse(game.customerMeat) { "" }, left + 72f, top + 18f, 15f, ink.bFade(0.7f * alpha), align = -1)
    val order = if (game.slicesTotal > 1) {
        "${game.slicesTotal} × ${game.sliceWantG} ${labels.gram}"
    } else {
        "${game.sliceWantG} ${labels.gram}"
    }
    bWord(measurer, order, left + 72f, top + 40f, 22f, ink.bFade(alpha), align = -1)
    val share = (game.patienceMs / game.patienceMaxMs).coerceIn(0f, 1f)
    drawRoundRect(ink.bFade(0.15f * alpha), Offset(left + 12f, top + 55f), Size(180f, 6f), CornerRadius(3f))
    val bar = if (share > 0.5f) ButcherLook.Green else if (share > 0.25f) ButcherLook.Amber else ButcherLook.Red
    drawRoundRect(bar.bFade(alpha), Offset(left + 12f, top + 55f), Size(180f * share, 6f), CornerRadius(3f))
}

/**
 * La balance de la trancheuse, dans le panneau : ce qui est demandé, le poids déjà coupé, la piste (0 à 600 g) avec la zone du bon
 * poids (parfait, bon, limite) et un curseur qui avance tant qu'on tient A. Une trace reste à l'endroit de la tranche coupée.
 */
private fun DrawScope.slicerGauge(game: ButcherGame, labels: ButcherLabels, measurer: TextMeasurer) {
    val ink = ButcherLook.Ink
    val x0 = 60f
    val x1 = 660f
    val y = 118f
    val gh = 18f
    val perG = (x1 - x0) / Butcher.SLICE_MAX_G
    val top = ButcherLook.PANEL_Y
    drawRoundRect(Color.White.bFade(0.96f), Offset(30f, top), Size(660f, ButcherLook.PANEL_H), CornerRadius(12f))
    drawRoundRect(ink, Offset(30f, top), Size(660f, ButcherLook.PANEL_H), CornerRadius(12f), style = Stroke(width = 3f))
    // Ce qui est demandé, à gauche ; le poids coupé, à droite.
    val headY = top + 15f
    meatShape(game.customerMeat, 66f, headY, 0.34f)
    bWord(measurer, labels.meats.getOrElse(game.customerMeat) { "" }, 94f, headY, 14f, ink.bFade(0.7f), align = -1)
    val number = min(game.slicesTotal, game.slicesTotal - game.slicesLeft + (if (game.step == BUTCHER_STEP_SLICE && game.sliceFlashMs <= 0f) 1 else 0))
    bWord(measurer, "${labels.slice} ${max(1, number)}/${game.slicesTotal}", 210f, headY, 14f, ink.bFade(0.7f), align = -1)
    bWord(measurer, "${game.sliceWantG} ${labels.gram}", 350f, headY, 20f, ButcherLook.GreenDark, align = -1)
    bWord(measurer, "${game.currentWeightG} ${labels.gram}", 662f, headY, 22f, ink, align = 1)
    // La piste et les trois zones, de la plus large à la plus étroite.
    drawRoundRect(Color(0xFFD4DADD), Offset(x0, y), Size(x1 - x0, gh), CornerRadius(5f))
    val tolerance = game.sliceTolG
    fun zone(ratio: Float, color: Color) {
        val from = (game.sliceWantG - tolerance * ratio).coerceAtLeast(0f)
        val to = (game.sliceWantG + tolerance * ratio).coerceAtMost(Butcher.SLICE_MAX_G)
        drawRect(color, Offset(x0 + from * perG, y), Size((to - from) * perG, gh))
    }
    zone(Butcher.OFF_RATIO, ButcherLook.Amber.bFade(0.55f))
    zone(1f, ButcherLook.Green)
    zone(Butcher.PERFECT_RATIO, ButcherLook.GreenDark)
    drawRoundRect(ink, Offset(x0, y), Size(x1 - x0, gh), CornerRadius(5f), style = Stroke(width = 2.4f))
    for (g in 0..Butcher.SLICE_MAX_G.toInt() step 100) {
        val gx = x0 + g * perG
        drawLine(ink, Offset(gx, y + gh), Offset(gx, y + gh + if (g % 200 == 0) 8f else 5f), strokeWidth = 2f)
        if (g % 200 == 0) bWord(measurer, g.toString(), gx, y + gh + 17f, 12f, ink.bFade(0.7f))
    }
    // La trace de la tranche qui vient d'être coupée.
    if (game.sliceFlashMs > 0f && game.step == BUTCHER_STEP_SLICE) {
        val fade = game.sliceFlashMs / Butcher.SLICE_FLASH_MS
        val gx = x0 + game.weighedG * perG
        drawLine(ink.bFade(0.7f * fade), Offset(gx, y - 6f), Offset(gx, y + gh + 4f), strokeWidth = 3f, cap = StrokeCap.Round)
    }
    // Le curseur : une barre rouge qui avance, ou grise une fois la dernière tranche coupée.
    val shown = if (game.step == BUTCHER_STEP_RESULT) game.weighedG.toFloat() else game.sliceG
    val cx = x0 + shown.coerceIn(0f, Butcher.SLICE_MAX_G) * perG
    val color = if (game.step == BUTCHER_STEP_RESULT) ink else ButcherLook.Red
    drawRect(ButcherLook.Red.bFade(0.3f), Offset(x0, y + gh - 5f), Size(cx - x0, 5f))
    drawLine(color, Offset(cx, y - 8f), Offset(cx, y + gh + 5f), strokeWidth = 4f, cap = StrokeCap.Round)
    val head = Path()
    head.moveTo(cx, y - 1f)
    head.lineTo(cx - 7f, y - 10f)
    head.lineTo(cx + 7f, y - 10f)
    head.close()
    drawPath(head, color)
}

/** Le service : le client sur le sol avec sa bulle, la balance de la trancheuse, le verdict et les points sous la bulle. */
private fun DrawScope.serviceFront(game: ButcherGame, labels: ButcherLabels, measurer: TextMeasurer) {
    if (game.customerMeat < 0) return
    val arrive = (game.arriveMs / Butcher.ARRIVE_MS).coerceIn(0f, 1f)
    val resulting = game.step == BUTCHER_STEP_RESULT
    val mood = when {
        !resulting -> 0
        game.verdict == BUTCHER_VERDICT_PERFECT || game.verdict == BUTCHER_VERDICT_GOOD -> 1
        else -> -1
    }
    bCustomer(game.customerLook, ButcherLook.CUSTOMER_X + arrive * 170f, ButcherLook.CUSTOMER_FEET, mood)
    orderBubble(game, labels, measurer, 1f - arrive)
    val showGauge = game.step == BUTCHER_STEP_SLICE ||
        (resulting && game.verdict >= BUTCHER_VERDICT_PERFECT && game.verdict <= BUTCHER_VERDICT_MISS)
    if (showGauge) slicerGauge(game, labels, measurer)
    if (resulting) {
        val good = game.verdict == BUTCHER_VERDICT_PERFECT || game.verdict == BUTCHER_VERDICT_GOOD
        val color = when {
            good -> ButcherLook.GreenDark
            game.verdict == BUTCHER_VERDICT_OFF -> Color(0xFFB87708)
            else -> ButcherLook.Red
        }
        val rise = (1f - game.resultMs / Butcher.RESULT_MS) * 8f
        bWord(measurer, labels.verdicts.getOrElse(game.verdict) { "" }, 494f, 368f - rise, 28f, color, italic = true)
        val points = game.lastPoints
        bWord(measurer, if (points >= 0) "+$points" else points.toString(), 494f, 392f - rise, 20f, color)
    } else if (game.step == BUTCHER_STEP_SLICE && game.sliceFlashMs > 0f) {
        val color = when (game.sliceVerdict) {
            BUTCHER_VERDICT_PERFECT, BUTCHER_VERDICT_GOOD -> ButcherLook.GreenDark
            BUTCHER_VERDICT_OFF -> Color(0xFFB87708)
            else -> ButcherLook.Red
        }
        bWord(measurer, labels.verdicts.getOrElse(game.sliceVerdict) { "" }, 494f, 368f, 24f, color.bFade(game.sliceFlashMs / Butcher.SLICE_FLASH_MS), italic = true)
    }
}

/** L'inspecteur sanitaire : une bannière clignotante avec un compte à rebours, puis le résultat du contrôle (conforme ou amende). */
private fun DrawScope.inspector(game: ButcherGame, labels: ButcherLabels, measurer: TextMeasurer) {
    val ink = ButcherLook.Ink
    val left = 24f
    val top = 264f
    val width = 348f
    if (game.inspectorState == BUTCHER_INSPECT_WARN) {
        val flash = sin(game.clockMs / 120f) > 0f
        val fill = if (flash) ButcherLook.Red else Color.White
        val text = if (flash) Color.White else ButcherLook.Red
        drawRoundRect(fill, Offset(left, top), Size(width, 28f), CornerRadius(8f))
        drawRoundRect(ink, Offset(left, top), Size(width, 28f), CornerRadius(8f), style = Stroke(width = 2.6f))
        bWord(measurer, labels.inspection, left + width / 2f, top + 14f, 18f, text, italic = true)
        val share = (game.inspectorWarnMs / Butcher.INSPECT_WARN_MS).coerceIn(0f, 1f)
        drawRect(ButcherLook.Red, Offset(left + 6f, top + 30f), Size((width - 12f) * share, 4f))
    } else if (game.inspectShowMs > 0f) {
        val fade = (game.inspectShowMs / Butcher.INSPECT_SHOW_MS).coerceIn(0f, 1f)
        val ok = game.inspectResult > 0
        val color = if (ok) ButcherLook.GreenDark else ButcherLook.Red
        val text = if (ok) "${labels.inspectOk} +${Butcher.POINTS_INSPECT_OK}" else "${labels.inspectFine} −${Butcher.PENALTY_INSPECT}"
        bWord(measurer, text, left + width / 2f, top + 14f, 24f, color.bFade(min(1f, fade * 2f)), italic = true)
    }
}

// ------------------------------------------------------------------------------------------------------------ le bonus saucisses

/** Un boyau (une saucisse) en capsule, de [xLeft] à [xRight] à la hauteur [y], de couleur [fill]. */
private fun DrawScope.sausageLink(xLeft: Float, xRight: Float, y: Float, fill: Color, outline: Color) {
    val width = max(6f, xRight - xLeft)
    drawRoundRect(fill, Offset(xLeft, y - 13f), Size(width, 26f), CornerRadius(13f))
    drawRoundRect(Color.White.bFade(0.35f), Offset(xLeft + 6f, y - 9f), Size(max(2f, width - 12f), 5f), CornerRadius(3f))
    drawRoundRect(outline, Offset(xLeft, y - 13f), Size(width, 26f), CornerRadius(13f), style = Stroke(width = 2.4f))
}

/**
 * L'atelier de saucisses (bonus) : un grand panneau avec la machine à droite, le boyau qui sort vers la gauche, la longueur voulue
 * (une zone verte à viser pour la queue de la saucisse), le temps qui reste et la série de saucisses réussies.
 */
private fun DrawScope.workshop(game: ButcherGame, labels: ButcherLabels, measurer: TextMeasurer) {
    val ink = ButcherLook.Ink
    val top = ButcherLook.PANEL_Y
    drawRoundRect(Color.White.bFade(0.97f), Offset(30f, top), Size(660f, 300f), CornerRadius(14f))
    drawRoundRect(ink, Offset(30f, top), Size(660f, 300f), CornerRadius(14f), style = Stroke(width = 3f))
    bWord(measurer, labels.stageBonus, Butcher.WIDTH / 2f, top + 24f, 28f, ButcherLook.Red, italic = true, spacing = 2f)
    // La table.
    drawRoundRect(ButcherLook.Wood, Offset(44f, 300f), Size(632f, 16f), CornerRadius(5f))
    drawRoundRect(ink, Offset(44f, 300f), Size(632f, 16f), CornerRadius(5f), style = Stroke(width = 2.4f))
    // La machine : la trémie rouge, le corps en inox, le bec.
    val mx = ButcherLook.NOZZLE_X
    val my = ButcherLook.ROPE_Y
    val hopper = Path().apply {
        moveTo(mx + 6f, my - 100f)
        lineTo(mx + 92f, my - 100f)
        lineTo(mx + 74f, my - 62f)
        lineTo(mx + 24f, my - 62f)
        close()
    }
    drawPath(hopper, ButcherLook.Red)
    drawPath(hopper, ink, style = Stroke(width = 2.6f, join = StrokeJoin.Round))
    drawRoundRect(ButcherLook.Metal, Offset(mx + 6f, my - 62f), Size(86f, 106f), CornerRadius(8f))
    drawRoundRect(ink, Offset(mx + 6f, my - 62f), Size(86f, 106f), CornerRadius(8f), style = Stroke(width = 2.6f))
    drawRoundRect(ButcherLook.Metal, Offset(mx - 6f, my - 8f), Size(16f, 16f), CornerRadius(4f))
    drawRoundRect(ink, Offset(mx - 6f, my - 8f), Size(16f, 16f), CornerRadius(4f), style = Stroke(width = 2.2f))
    // La manivelle tourne quand on remplit.
    val crank = game.clockMs / 90f * (if (game.holdA) 1f else 0f)
    drawCircle(ink, 12f, Offset(mx + 50f, my + 18f), style = Stroke(width = 2.6f))
    drawLine(ink, Offset(mx + 50f, my + 18f), Offset(mx + 50f + kotlin.math.cos(crank) * 12f, my + 18f + sin(crank) * 12f), strokeWidth = 3.4f, cap = StrokeCap.Round)

    if (game.bonusIntroMs > 0f) {
        bWord(measurer, labels.bonusHelp, Butcher.WIDTH / 2f, 200f, 18f, ink.bFade(0.8f))
        return
    }
    // Le temps du bonus, et le nombre de saucisses réussies.
    val share = (game.bonusLeftMs / Butcher.BONUS_MS).coerceIn(0f, 1f)
    drawRoundRect(ink.bFade(0.15f), Offset(60f, top + 52f), Size(600f, 8f), CornerRadius(4f))
    drawRoundRect(if (share > 0.25f) ButcherLook.Green else ButcherLook.Red, Offset(60f, top + 52f), Size(600f * share, 8f), CornerRadius(4f))
    sausageLink(60f, 92f, top + 82f, Color(0xFFE8A0A0), ink)
    bWord(measurer, "× ${game.sausages}", 102f, top + 82f, 22f, ink, align = -1)
    if (game.combo >= 2) bWord(measurer, "x${game.combo}", 660f, top + 82f, 22f, ButcherLook.GreenDark, align = 1, italic = true)

    // La zone à viser : la queue de la saucisse en cours doit s'arrêter dans la bande verte.
    val s = ButcherLook.MM
    val targetX = mx - game.linkTarget * s
    val tol = Butcher.BONUS_TOL_MM * s
    drawRect(ButcherLook.Green.bFade(0.4f), Offset(targetX - tol, my - 44f), Size(tol * 2f, 88f))
    drawRect(ButcherLook.GreenDark.bFade(0.55f), Offset(targetX - tol * Butcher.BONUS_PERFECT_RATIO, my - 44f), Size(tol * Butcher.BONUS_PERFECT_RATIO * 2f, 88f))
    val marker = Path().apply {
        moveTo(targetX, my - 34f)
        lineTo(targetX - 8f, my - 48f)
        lineTo(targetX + 8f, my - 48f)
        close()
    }
    drawPath(marker, ButcherLook.GreenDark)

    // Le boyau : la saucisse en cours, puis les saucisses finies qui s'éloignent vers la gauche.
    val current = game.linkLen
    val stress = (current / (game.linkTarget + Butcher.BURST_OVER_MM)).coerceIn(0f, 1f)
    val fill = if (stress > 0.85f) ButcherLook.Red.bFade(0.6f) else Color(0xFFEFB0B0)
    var edge = mx
    if (current > 0f) {
        sausageLink(edge - current * s, edge, my, fill, ink)
        edge -= current * s
    }
    for (i in game.chainLen.indices.reversed()) {
        if (edge < 56f) break
        val len = game.chainLen[i] * s
        val color = when (game.chainVerdict[i]) {
            BUTCHER_VERDICT_PERFECT -> Color(0xFFE88B8B)
            BUTCHER_VERDICT_GOOD -> Color(0xFFEFB0B0)
            else -> Color(0xFFB9A79A)
        }
        sausageLink(max(56f, edge - len), edge, my, color, ink)
        edge -= len
    }
    // L'éclatement du boyau.
    if (game.burstMs > 0f) {
        val fade = game.burstMs / Butcher.BURST_MS
        for (i in 0 until 7) {
            val a = i * 0.9f
            val r = 16f + (1f - fade) * 34f
            drawCircle(Color(0xFFE8A0A0).bFade(fade), 5f, Offset(mx - 20f + kotlin.math.cos(a) * r, my + sin(a) * r))
        }
        bWord(measurer, labels.burst, 360f, 200f, 34f, ButcherLook.Red.bFade(fade), italic = true)
    } else if (game.linkFlashMs > 0f) {
        val fade = game.linkFlashMs / Butcher.LINK_FLASH_MS
        val good = game.linkFlashVerdict == BUTCHER_VERDICT_PERFECT || game.linkFlashVerdict == BUTCHER_VERDICT_GOOD
        bWord(measurer, labels.verdicts.getOrElse(game.linkFlashVerdict) { "" }, 360f, 200f, 34f, (if (good) ButcherLook.GreenDark else ButcherLook.Red).bFade(fade), italic = true)
    }
    bWord(measurer, labels.bonusHelp, Butcher.WIDTH / 2f, 352f, 14f, ink.bFade(0.7f))
}
