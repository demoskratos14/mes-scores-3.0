package com.aventure.messcores

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Les dessins de l'écran de Comptabilité, dans le repère de l'écran (720 x 405), le même que celui de Drive et de Poissonnerie. La
 * scène se lit de haut en bas : le mur du bureau (l'enseigne, les compteurs, et de chaque côté du personnage un rappel des touches :
 * quel signe est sur quelle flèche, et A vrai / B faux), le comptable de Drive derrière son bureau, puis le sol au premier plan, où le
 * client pose sa feuille (une opération à trous, une facture ou une chaîne) devant lui. Le décor qui ne bouge pas se dessine une fois
 * avec [drawAccountingScene] ; tout ce qui bouge avec [drawAccountingLive]. Le comptable est le personnage de Drive ([DriveSprites]).
 */
internal class AccountingLabels(
    val time: String,
    val score: String,
    val bonus: String,
    val sign: String,
    val stageMain: String,
    val stageBonus: String,
    val bonusTitle: String,
    val bonusHint: String,
    val no: String,
    val euro: String,
    val invoice: String,
    val total: String,
    val yes: String,
    val nope: String,
    /** Le texte de chaque verdict, indexé par ACC_VERDICT_* (la case 0 est vide). */
    val verdicts: List<String>
)

private object AccLook {
    val Ink = Color(0xFF20241F)
    val Red = Color(0xFFE2001A)
    val Green = Color(0xFF2E9E4F)
    val GreenDark = Color(0xFF176B33)
    val Amber = Color(0xFFF0B429)
    val Paper = Color(0xFFF6F6EE)
    val Wall = Color(0xFFE3E6D9)
    val Wood = Color(0xFF8A6238)
    val WoodDark = Color(0xFF5E4125)
    val WoodLight = Color(0xFFB0834F)

    // Le mur finit ici ; le sol commence.
    const val WALL_END = 250f

    // Le comptable derrière son bureau, dont le dessus lui cache les jambes ; le client devant, sur le sol.
    const val HERO_X = 360f
    const val HERO_FEET = 210f
    const val HERO_SCALE = 1.1f
    const val DESK_X0 = 246f
    const val DESK_X1 = 474f
    const val DESK_TOP = 198f
    const val CUSTOMER_X = 640f
    const val CUSTOMER_FEET = 396f

    // La feuille posée devant le client.
    const val SHEET_X = 30f
    const val SHEET_Y = 270f
    const val SHEET_W = 480f
    const val SHEET_H = 124f
    const val SHEET_CX = SHEET_X + SHEET_W / 2f

    // Les rappels de touches : la croix des signes à gauche du personnage, les boutons A et B à droite.
    const val CROSS_X = 118f
    const val CROSS_Y = 164f
    const val CROSS_STEP = 40f
    const val CROSS_R = 17f
    const val BTN_X = 566f
}

private fun Color.fade(a: Float): Color = copy(alpha = alpha * a)

/** La largeur d'un texte gras de [px] pixels de l'écran de hauteur de police. */
private fun DrawScope.textWidth(measurer: TextMeasurer, text: String, px: Float): Float {
    val style = TextStyle(fontSize = px.toSp(), fontWeight = FontWeight.Bold)
    return measurer.measure(text = text, style = style, softWrap = false, maxLines = 1).size.width.toFloat()
}

// ------------------------------------------------------------------------------------------------------------ les signes

/** Le signe [op] ([ACC_OP_ADD] à [ACC_OP_MUL]) tracé au trait (pas de police), centré en ([cx], [cy]) dans une boîte de [size] de côté. */
internal fun DrawScope.accSign(op: Int, cx: Float, cy: Float, size: Float, color: Color) {
    val width = size * 0.17f
    val r = size * 0.4f
    fun line(x0: Float, y0: Float, x1: Float, y1: Float) =
        drawLine(color, Offset(cx + x0 * r, cy + y0 * r), Offset(cx + x1 * r, cy + y1 * r), strokeWidth = width, cap = StrokeCap.Round)
    when (op) {
        ACC_OP_ADD -> { line(-1f, 0f, 1f, 0f); line(0f, -1f, 0f, 1f) }
        ACC_OP_SUB -> line(-1f, 0f, 1f, 0f)
        ACC_OP_MUL -> { line(-0.8f, -0.8f, 0.8f, 0.8f); line(-0.8f, 0.8f, 0.8f, -0.8f) }
        else -> {
            line(-1f, 0f, 1f, 0f)
            drawCircle(color, width * 0.75f, Offset(cx, cy - r * 0.62f))
            drawCircle(color, width * 0.75f, Offset(cx, cy + r * 0.62f))
        }
    }
}

// ------------------------------------------------------------------------------------------------------------ le décor

/** Le décor de l'écran, qui ne change jamais : le mur du bureau, l'enseigne, le bureau, le sol et les rappels de touches. */
internal fun DrawScope.drawAccountingScene(labels: AccountingLabels, measurer: TextMeasurer) {
    val w = Accounting.WIDTH
    val h = Accounting.HEIGHT
    val ink = AccLook.Ink
    // Le mur, avec une rangée de classeurs en bas, et le sol.
    drawRect(
        brush = Brush.verticalGradient(0f to Color(0xFFEFF1E6), 1f to AccLook.Wall, startY = 0f, endY = AccLook.WALL_END),
        size = Size(w, AccLook.WALL_END)
    )
    var x = 0f
    while (x < w) {
        drawLine(ink.fade(0.05f), Offset(x, 0f), Offset(x, AccLook.WALL_END), strokeWidth = 1.5f)
        x += 60f
    }
    drawRect(
        brush = Brush.verticalGradient(0f to Color(0xFFCDD2C1), 1f to Color(0xFFB4BAA8), startY = AccLook.WALL_END, endY = h),
        topLeft = Offset(0f, AccLook.WALL_END),
        size = Size(w, h - AccLook.WALL_END)
    )
    var fx = 0f
    while (fx < w) {
        drawLine(ink.fade(0.07f), Offset(fx, AccLook.WALL_END), Offset(fx, h), strokeWidth = 1.5f)
        fx += 60f
    }
    var fy = AccLook.WALL_END + 30f
    while (fy < h) {
        drawLine(ink.fade(0.07f), Offset(0f, fy), Offset(w, fy), strokeWidth = 1.5f)
        fy += 30f
    }
    drawRect(Color(0xFF8E947F), Offset(0f, AccLook.WALL_END - 6f), Size(w, 6f))
    drawRect(ink, Offset(0f, AccLook.WALL_END - 6f), Size(w, 2f))

    // L'enseigne, au milieu du mur.
    drawRoundRect(Color.White, Offset(w / 2f - 90f, 10f), Size(180f, 42f), CornerRadius(8f))
    drawRoundRect(ink, Offset(w / 2f - 90f, 10f), Size(180f, 42f), CornerRadius(8f), style = Stroke(width = 3f))
    drawRect(AccLook.Red, Offset(w / 2f - 90f, 44f), Size(180f, 4f))
    fishWord(measurer, labels.sign, w / 2f, 27f, 22f, AccLook.Red, italic = true, spacing = 3f)

    // Le rappel des signes : une croix sur une plaque, comme les flèches de la console.
    val px = AccLook.CROSS_X
    val py = AccLook.CROSS_Y
    drawRoundRect(Color.White.fade(0.92f), Offset(px - 72f, py - 66f), Size(144f, 132f), CornerRadius(14f))
    drawRoundRect(ink, Offset(px - 72f, py - 66f), Size(144f, 132f), CornerRadius(14f), style = Stroke(width = 3f))
    for (op in ACC_UP..ACC_RIGHT) legendSign(op, Color.White, ink, false)

    // Le rappel de A (vrai) et B (faux), sur une plaque à droite du personnage.
    drawRoundRect(Color.White.fade(0.92f), Offset(AccLook.BTN_X - 36f, 122f), Size(166f, 108f), CornerRadius(14f))
    drawRoundRect(ink, Offset(AccLook.BTN_X - 36f, 122f), Size(166f, 108f), CornerRadius(14f), style = Stroke(width = 3f))
    for (letter in intArrayOf(GLYPH_A, GLYPH_B)) legendLetter(letter, labels, measurer, false, false)

    // Le bureau : un plateau en bois et sa face, avec une calculatrice et une pile de papiers.
    val dx = AccLook.DESK_X0
    val dw = AccLook.DESK_X1 - AccLook.DESK_X0
    drawRoundRect(AccLook.Wood, Offset(dx, AccLook.DESK_TOP + 14f), Size(dw, 52f), CornerRadius(4f))
    drawRect(ink.fade(0.35f), Offset(dx + 14f, AccLook.DESK_TOP + 30f), Size(dw - 28f, 3f))
    drawRoundRect(AccLook.WoodLight, Offset(dx - 8f, AccLook.DESK_TOP), Size(dw + 16f, 16f), CornerRadius(4f))
    drawRoundRect(ink, Offset(dx - 8f, AccLook.DESK_TOP), Size(dw + 16f, 16f), CornerRadius(4f), style = Stroke(width = 2.4f))
    drawRoundRect(ink, Offset(dx, AccLook.DESK_TOP + 14f), Size(dw, 52f), CornerRadius(4f), style = Stroke(width = 2.4f))
    // La calculatrice, posée sur le plateau.
    val cx = dx + 34f
    val cy = AccLook.DESK_TOP - 2f
    drawRoundRect(Color(0xFF3A3F44), Offset(cx, cy - 30f), Size(34f, 30f), CornerRadius(4f))
    drawRect(Color(0xFFB7E3C0), Offset(cx + 4f, cy - 26f), Size(26f, 8f))
    for (row in 0 until 2) for (col in 0 until 4) {
        drawRect(Color(0xFFDADDD2), Offset(cx + 4f + col * 7f, cy - 14f + row * 7f), Size(5f, 5f))
    }
    drawRoundRect(ink, Offset(cx, cy - 30f), Size(34f, 30f), CornerRadius(4f), style = Stroke(width = 2f))
}

/** Une touche de la croix du rappel : son signe sur un disque ([fill] et [line] : couleurs du disque et de son contour). */
private fun DrawScope.legendSign(op: Int, fill: Color, line: Color, lit: Boolean, litColor: Color = AccLook.Green) {
    val c = legendCenter(op)
    drawCircle(if (lit) litColor else fill, AccLook.CROSS_R, c)
    drawCircle(line, AccLook.CROSS_R, c, style = Stroke(width = 2.4f))
    accSign(op, c.x, c.y, AccLook.CROSS_R * 1.6f, if (lit) Color.White else line)
}

private fun legendCenter(op: Int): Offset = when (op) {
    ACC_UP -> Offset(AccLook.CROSS_X, AccLook.CROSS_Y - AccLook.CROSS_STEP)
    ACC_DOWN -> Offset(AccLook.CROSS_X, AccLook.CROSS_Y + AccLook.CROSS_STEP)
    ACC_LEFT -> Offset(AccLook.CROSS_X - AccLook.CROSS_STEP, AccLook.CROSS_Y)
    else -> Offset(AccLook.CROSS_X + AccLook.CROSS_STEP, AccLook.CROSS_Y)
}

/** Un bouton A (rouge, « vrai ») ou B (noir, « faux ») du rappel, avec son mot ; [lit] : il vient d'être enfoncé ([good] : vert, sinon rouge). */
private fun DrawScope.legendLetter(letter: Int, labels: AccountingLabels, measurer: TextMeasurer, lit: Boolean, good: Boolean) {
    val y = if (letter == GLYPH_A) 150f else 202f
    val c = Offset(AccLook.BTN_X, y)
    val base = if (letter == GLYPH_A) AccLook.Red else Color(0xFF25272B)
    val fill = if (lit) (if (good) AccLook.Green else AccLook.Red) else base
    drawCircle(fill, 18f, c)
    drawCircle(AccLook.Ink, 18f, c, style = Stroke(width = 2.4f))
    driveGlyph(letter, c.x, c.y, 18f, Color.White)
    fishWord(measurer, if (letter == GLYPH_A) labels.yes else labels.nope, c.x + 30f, y, 22f, AccLook.Ink, align = -1)
}

// ------------------------------------------------------------------------------------------------------------ ce qui bouge

/** Le temps qui reste, en « m:ss » ; il ne tombe pas à zéro tant que la dernière seconde n'est pas finie. */
internal fun accountingClockText(timeLeftMs: Float): String = fishClockText(timeLeftMs)

/** Tout ce qui change pendant la partie, dans le repère de 720 x 405 de l'écran. */
internal fun DrawScope.drawAccountingLive(game: AccountingGame, sprites: DriveSprites, labels: AccountingLabels, measurer: TextMeasurer) {
    val ink = AccLook.Ink
    val w = Accounting.WIDTH
    val bonusStage = game.stage == ACC_STAGE_BONUS
    hud(measurer, labels, game)
    if (!game.over && game.stage != ACC_STAGE_BONUS_INTRO) {
        val title = if (bonusStage) labels.stageBonus else labels.stageMain
        fishWord(measurer, title, w / 2f, 66f, 14f, if (bonusStage) Color(0xFFB87708) else ink.fade(0.6f))
    }

    // Les rappels de touches qui s'allument : vert quand le geste est bon, rouge quand il est refusé.
    val lit = game.stampMs > 0f && game.stampKey >= 0
    val good = game.blockedMs <= 0f
    if (lit && game.stampKey <= ACC_RIGHT) legendSign(game.stampKey, Color.White, ink, true, if (good) AccLook.Green else AccLook.Red)
    if (lit && game.stampKey >= ACC_A) legendLetter(if (game.stampKey == ACC_A) GLYPH_A else GLYPH_B, labels, measurer, true, good)

    accountant(game, sprites)
    deskFront()
    when (game.stage) {
        ACC_STAGE_MAIN, ACC_STAGE_BONUS -> service(game, labels, measurer)
        ACC_STAGE_BONUS_INTRO -> {
            val pulse = 1f + 0.06f * sin(game.clockMs / 110f)
            fishWord(measurer, labels.bonusTitle, w / 2f, 318f, 42f * pulse, AccLook.Amber, italic = true, spacing = 3f)
            fishWord(measurer, labels.bonusHint, w / 2f, 358f, 18f, ink.fade(0.8f))
        }
        else -> {}
    }
    if (bonusStage || game.stage == ACC_STAGE_BONUS_INTRO) {
        // Un cadre doré pendant tout le temps bonus.
        drawRect(AccLook.Amber, Offset.Zero, Size(w, Accounting.HEIGHT), style = Stroke(width = 8f))
    }
    val shade = min(0.25f, game.blockedMs / Accounting.BLOCKED_MS * 0.25f)
    if (shade > 0f) {
        drawRect(AccLook.Red.fade(shade), size = Size(w, Accounting.HEIGHT))
        fishWord(measurer, labels.no, w / 2f, 124f, 44f, AccLook.Red, italic = true)
    }
}

/** Le dessus du bureau, par-dessus les jambes du comptable : le plateau et sa face, déjà dessinés dans le décor, sont redessinés en haut. */
private fun DrawScope.deskFront() {
    val dx = AccLook.DESK_X0
    val dw = AccLook.DESK_X1 - AccLook.DESK_X0
    drawRoundRect(AccLook.WoodLight, Offset(dx - 8f, AccLook.DESK_TOP), Size(dw + 16f, 16f), CornerRadius(4f))
    drawRoundRect(AccLook.Ink, Offset(dx - 8f, AccLook.DESK_TOP), Size(dw + 16f, 16f), CornerRadius(4f), style = Stroke(width = 2.4f))
}

/** Les compteurs du haut de l'écran : le temps à gauche, le score à droite avec la jauge qui mène au temps bonus. */
private fun DrawScope.hud(measurer: TextMeasurer, labels: AccountingLabels, game: AccountingGame) {
    val ink = AccLook.Ink
    val w = Accounting.WIDTH
    val left = if (game.stage == ACC_STAGE_BONUS_INTRO) Accounting.BONUS_MS else game.stageLeftMs
    val urgent = game.playing && game.stageLeftMs <= 10_000f
    fishWord(measurer, labels.time, 16f, 24f, 14f, ink.fade(0.6f), align = -1)
    fishWord(measurer, accountingClockText(if (game.over) 0f else left), 16f, 62f, 46f, if (urgent) AccLook.Red else ink, align = -1)
    fishWord(measurer, labels.score, w - 16f, 24f, 14f, ink.fade(0.6f), align = 1)
    fishWord(measurer, game.score.toString(), w - 16f, 62f, 46f, ink, align = 1)
    // La jauge du temps bonus : elle se remplit jusqu'au seuil ; une fois atteint, elle est dorée.
    if (game.stage == ACC_STAGE_MAIN || game.bonusGranted) {
        val reached = game.bonusGranted || game.score >= Accounting.BONUS_SCORE
        val share = if (reached) 1f else (game.score.toFloat() / Accounting.BONUS_SCORE).coerceIn(0f, 1f)
        val x1 = w - 16f
        val x0 = x1 - 150f
        fishWord(measurer, labels.bonus, x0, 96f, 12f, if (reached) Color(0xFFB87708) else ink.fade(0.6f), align = -1)
        drawRoundRect(ink.fade(0.15f), Offset(x0, 104f), Size(150f, 7f), CornerRadius(3.5f))
        drawRoundRect(if (reached) AccLook.Amber else AccLook.Green, Offset(x0, 104f), Size(150f * share, 7f), CornerRadius(3.5f))
    }
}

/** Le comptable (le personnage de Drive, de face) derrière son bureau : il saute de joie à chaque bonne réponse et se secoue à chaque erreur. */
private fun DrawScope.accountant(game: AccountingGame, sprites: DriveSprites) {
    val image = sprites.front ?: return
    val w = (image.width * AccLook.HERO_SCALE).roundToInt()
    val h = (image.height * AccLook.HERO_SCALE).roundToInt()
    val happy = game.playing && game.step == ACC_STEP_RESULT && game.verdict == ACC_VERDICT_RIGHT
    val hop = if (happy) abs(sin(game.resultMs / 110f)) * 8f else 0f
    val shake = if (game.blockedMs > 0f) sin(game.clockMs / 25f) * 3f else 0f
    val cx = AccLook.HERO_X + shake
    val top = AccLook.HERO_FEET - h - hop
    drawImage(
        image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset((cx - w / 2f).roundToInt(), top.roundToInt()),
        dstSize = IntSize(w, h),
        filterQuality = FilterQuality.None
    )
}

/** Le client sur le sol avec sa feuille posée devant lui, la barre de patience qui se vide, et le verdict une fois qu'il a répondu. */
private fun DrawScope.service(game: AccountingGame, labels: AccountingLabels, measurer: TextMeasurer) {
    val arrive = (game.arriveMs / Accounting.ARRIVE_MS).coerceIn(0f, 1f)
    val resulting = game.step == ACC_STEP_RESULT
    val mood = when {
        !resulting -> 0
        game.verdict == ACC_VERDICT_RIGHT -> 1
        else -> -1
    }
    customer(game.customerLook, AccLook.CUSTOMER_X + arrive * 170f, AccLook.CUSTOMER_FEET, mood)
    val alpha = 1f - arrive
    sheet(alpha)
    when (game.kind) {
        ACC_KIND_CHECK -> invoiceContent(game, labels, measurer, alpha)
        else -> equationContent(game, measurer, alpha)
    }
    footer(game, labels, measurer, alpha)
}

/** La feuille de papier du client. */
private fun DrawScope.sheet(alpha: Float) {
    val ink = AccLook.Ink
    val x = AccLook.SHEET_X
    val y = AccLook.SHEET_Y
    drawRoundRect(Color.Black.fade(0.12f * alpha), Offset(x + 4f, y + 5f), Size(AccLook.SHEET_W, AccLook.SHEET_H), CornerRadius(8f))
    drawRoundRect(AccLook.Paper.fade(alpha), Offset(x, y), Size(AccLook.SHEET_W, AccLook.SHEET_H), CornerRadius(8f))
    drawRoundRect(ink.fade(alpha), Offset(x, y), Size(AccLook.SHEET_W, AccLook.SHEET_H), CornerRadius(8f), style = Stroke(width = 3f))
}

/** Le bas de la feuille : la barre de patience (du vert au rouge) ou, une fois le client servi, le verdict et les points. */
private fun DrawScope.footer(game: AccountingGame, labels: AccountingLabels, measurer: TextMeasurer, alpha: Float) {
    val ink = AccLook.Ink
    val x0 = AccLook.SHEET_X + 20f
    val y = AccLook.SHEET_Y + AccLook.SHEET_H - 16f
    if (game.step == ACC_STEP_ASK) {
        val share = (game.patienceMs / game.patienceMaxMs).coerceIn(0f, 1f)
        drawRoundRect(ink.fade(0.15f * alpha), Offset(x0, y - 3f), Size(AccLook.SHEET_W - 40f, 7f), CornerRadius(3.5f))
        val bar = if (share > 0.5f) AccLook.Green else if (share > 0.25f) AccLook.Amber else AccLook.Red
        drawRoundRect(bar.fade(alpha), Offset(x0, y - 3f), Size((AccLook.SHEET_W - 40f) * share, 7f), CornerRadius(3.5f))
        return
    }
    val color = if (game.verdict == ACC_VERDICT_RIGHT) AccLook.GreenDark else AccLook.Red
    val rise = (1f - game.resultMs / Accounting.RESULT_MS) * 4f
    fishWord(measurer, labels.verdicts.getOrElse(game.verdict) { "" }, x0, y - rise, 20f, color, align = -1, italic = true)
    val points = game.lastPoints
    fishWord(measurer, if (points >= 0) "+$points" else points.toString(), AccLook.SHEET_X + AccLook.SHEET_W - 20f, y - rise, 20f, color, align = 1)
}

/**
 * Une opération à trous, sur une ligne : les nombres, un trou par signe à trouver, puis « = » et le résultat. Le trou attendu pulse ;
 * un signe trouvé s'y inscrit en vert ; un signe refusé y clignote en rouge ; quand le client part, la bonne réponse apparaît en rouge.
 */
private fun DrawScope.equationContent(game: AccountingGame, measurer: TextMeasurer, alpha: Float) {
    val ink = AccLook.Ink
    val chain = game.kind == ACC_KIND_CHAIN
    val px = if (chain) 40f else 48f
    val gap = if (chain) 10f else 14f
    val box = if (chain) 44f else 52f
    val cy = AccLook.SHEET_Y + 54f
    val equals = "="
    // Les éléments de la ligne, dans l'ordre : nombre, trou, nombre, (trou, nombre,) « = », résultat.
    val texts = ArrayList<String>()
    for (i in game.numbers.indices) {
        texts.add(game.numbers[i].toString())
        if (i < game.solution.size) texts.add("")
    }
    texts.add(equals)
    texts.add(game.result.toString())
    var total = gap * (texts.size - 1)
    for (t in texts) total += if (t.isEmpty()) box else textWidth(measurer, t, px)
    var x = AccLook.SHEET_CX - total / 2f
    var hole = 0
    val pulse = 0.5f + 0.5f * sin(game.clockMs / 140f)
    for (t in texts) {
        if (t.isNotEmpty()) {
            val width = textWidth(measurer, t, px)
            fishWord(measurer, t, x + width / 2f, cy, px, ink.fade(alpha))
            x += width + gap
            continue
        }
        val index = hole++
        val current = index == game.found && game.step == ACC_STEP_ASK
        val top = cy - box * 0.56f
        drawRoundRect(Color.White.fade(alpha), Offset(x, top), Size(box, box * 1.12f), CornerRadius(8f))
        val edge = if (current) AccLook.Red.fade(0.5f + 0.5f * pulse) else ink.fade(0.6f * alpha)
        drawRoundRect(edge.fade(alpha), Offset(x, top), Size(box, box * 1.12f), CornerRadius(8f), style = Stroke(width = if (current) 4f else 2.6f))
        val mx = x + box / 2f
        val sign = when {
            index < game.found -> game.solution[index] to AccLook.GreenDark
            game.verdict == ACC_VERDICT_LEFT && game.step == ACC_STEP_RESULT -> game.solution[index] to AccLook.Red
            current && game.stampMs > 0f && game.blockedMs > 0f && game.stampKey in ACC_UP..ACC_RIGHT -> game.stampKey to AccLook.Red
            else -> null
        }
        if (sign != null) {
            accSign(sign.first, mx, cy, box * 0.8f, sign.second.fade(alpha))
        } else if (current) {
            fishWord(measurer, "?", mx, cy, px * 0.9f, AccLook.Red.fade((0.55f + 0.45f * pulse) * alpha))
        }
        x += box + gap
    }
}

/**
 * Une facture : à gauche son titre et ses montants, à droite le total écrit en bas, qui est juste ou non. Une fois la réponse donnée, un
 * tampon « vrai » ou « faux » est posé en travers ; si la réponse était fausse, le vrai total s'écrit en vert sous le total affiché.
 */
private fun DrawScope.invoiceContent(game: AccountingGame, labels: AccountingLabels, measurer: TextMeasurer, alpha: Float) {
    val ink = AccLook.Ink
    val left = AccLook.SHEET_X + 28f
    val top = AccLook.SHEET_Y
    fishWord(measurer, labels.invoice, left, top + 18f, 14f, ink.fade(0.55f * alpha), align = -1, spacing = 2f)
    val lines = game.invoiceLines
    for (i in lines.indices) {
        val y = top + 38f + i * 17f
        if (i > 0) fishWord(measurer, "+", left, y, 15f, ink.fade(0.5f * alpha), align = -1)
        fishWord(measurer, "${lines[i]} ${labels.euro}", left + 150f, y, 17f, ink.fade(alpha), align = 1)
    }
    val lineY = top + 38f + lines.size * 17f - 6f
    drawLine(ink.fade(0.6f * alpha), Offset(left, lineY), Offset(left + 150f, lineY), strokeWidth = 2f)
    // Le total, grand, à droite.
    val cx = AccLook.SHEET_X + AccLook.SHEET_W - 130f
    fishWord(measurer, labels.total, cx, top + 24f, 15f, ink.fade(0.6f * alpha), spacing = 3f)
    val resulting = game.step == ACC_STEP_RESULT
    val wrongAnswer = resulting && game.verdict == ACC_VERDICT_WRONG
    drawRoundRect(Color.White.fade(alpha), Offset(cx - 96f, top + 38f), Size(192f, 50f), CornerRadius(8f))
    drawRoundRect(ink.fade(alpha), Offset(cx - 96f, top + 38f), Size(192f, 50f), CornerRadius(8f), style = Stroke(width = 3f))
    fishWord(measurer, "${game.shownTotal} ${labels.euro}", cx, top + 63f, 38f, ink.fade(alpha))
    if (wrongAnswer || (resulting && game.verdict == ACC_VERDICT_LEFT)) {
        fishWord(measurer, "${game.invoice().trueTotal} ${labels.euro}", cx, top + 99f, 20f, AccLook.GreenDark.fade(alpha))
    }
    if (resulting && game.verdict != ACC_VERDICT_LEFT) {
        // Le tampon posé en travers : ce que le joueur a répondu, vert si c'est juste, rouge sinon.
        val saidTrue = game.stampKey == ACC_A
        val good = game.verdict == ACC_VERDICT_RIGHT
        val color = if (good) AccLook.GreenDark else AccLook.Red
        val grow = 1f + 0.25f * (game.resultMs / Accounting.RESULT_MS)
        withTransform({ rotate(-10f, Offset(cx - 6f, top + 40f)); scale(grow, grow, Offset(cx - 6f, top + 40f)) }) {
            val text = if (saidTrue) labels.yes else labels.nope
            val width = textWidth(measurer, text, 26f)
            drawRoundRect(color, Offset(cx - 6f - width / 2f - 10f, top + 22f), Size(width + 20f, 36f), CornerRadius(6f), style = Stroke(width = 4f))
            fishWord(measurer, text, cx - 6f, top + 40f, 26f, color, spacing = 2f)
        }
    }
}

/** Un chemin pour une petite flèche ([direction] : 0 haut, 1 bas, 2 gauche, 3 droite) centrée en ([cx], [cy]) : l'ergot des touches de la croix. */
internal fun accArrowPath(direction: Int, cx: Float, cy: Float, size: Float): Path {
    val p = Path()
    when (direction) {
        ACC_UP -> { p.moveTo(cx, cy - size); p.lineTo(cx - size, cy + size * 0.6f); p.lineTo(cx + size, cy + size * 0.6f) }
        ACC_DOWN -> { p.moveTo(cx, cy + size); p.lineTo(cx - size, cy - size * 0.6f); p.lineTo(cx + size, cy - size * 0.6f) }
        ACC_LEFT -> { p.moveTo(cx - size, cy); p.lineTo(cx + size * 0.6f, cy - size); p.lineTo(cx + size * 0.6f, cy + size) }
        else -> { p.moveTo(cx + size, cy); p.lineTo(cx - size * 0.6f, cy - size); p.lineTo(cx - size * 0.6f, cy + size) }
    }
    p.close()
    return p
}
