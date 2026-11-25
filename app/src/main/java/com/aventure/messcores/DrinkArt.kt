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
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Les dessins de l'écran de La descente, dans le repère de l'écran (720 x 405), le même que celui de Drive : le décor qui ne
 * bouge pas (le bar, ses étagères, ses pompes) se dessine une fois avec [drawDrinkScene] ; tout ce qui bouge (compteurs, personnage,
 * verre) avec [drawDrinkLive]. Le personnage est celui de Drive : de face quand il boit, de profil (vers la droite ou vers la
 * gauche) quand il chancelle ([DriveSprites]).
 */
internal class DrinkLabels(
    val time: String,
    val volume: String,
    val glasses: String,
    val sign: String
)

private object DrinkLook {
    val Ink = Color(0xFF20241F)
    val Red = Color(0xFFE2001A)
    val Paper = Color(0xFFE3E6D9)
    val PaperLight = Color(0xFFF6F6EE)
    val Beer = Color(0xFFE8A317)
    val BeerDark = Color(0xFFB87708)
    val Foam = Color(0xFFFFF6D8)
    val Glass = Color(0xFFDCE8EE)
    val Skin = Color(0xFFD99B79)
    val Wood = Color(0xFF6E4A2E)
    val WoodDark = Color(0xFF55381F)

    /** Le personnage de Drive (40 x 104 pixels) est agrandi : il est seul à l'écran. */
    const val HERO_SCALE = 2f

    /** Les pieds du personnage touchent presque le bas de l'écran. */
    const val FEET_Y = Drink.HEIGHT - 11f

    /** Le personnage se décale vers le côté où il penche. */
    const val LEAN_SHIFT = 40f

    /** Inclinaison du corps, en degrés, quand il penche à fond. */
    const val LEAN_DEGREES = 32f
}

private fun Color.fade(a: Float): Color = copy(alpha = alpha * a)

/** Un texte centré (ou aligné) en ([x], [y]), de [px] pixels de l'écran de hauteur de police ; [align] : -1 à gauche, 0 centré, 1 à droite. */
private fun DrawScope.drinkWord(
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

// ------------------------------------------------------------------------------------------------------------ le décor

/** Une bouteille posée sur une étagère : le pied en ([x], [y]), [h] de haut ; [kind] choisit sa couleur. */
private fun DrawScope.bottle(x: Float, y: Float, h: Float, kind: Int) {
    val body = when (kind % 4) {
        0 -> Color(0xFF3B7A4A)
        1 -> Color(0xFF8A5A2B)
        2 -> Color(0xFFBFD7E6)
        else -> Color(0xFFB3261E)
    }
    val w = 16f
    val neckW = 6f
    val bodyH = h * 0.62f
    drawRoundRect(body, Offset(x - w / 2f, y - bodyH), Size(w, bodyH), CornerRadius(3f))
    drawRect(body, Offset(x - neckW / 2f, y - h), Size(neckW, h - bodyH + 2f))
    drawRoundRect(DrinkLook.Ink, Offset(x - w / 2f, y - bodyH), Size(w, bodyH), CornerRadius(3f), style = Stroke(width = 1.6f))
    drawRect(DrinkLook.Ink, Offset(x - neckW / 2f, y - h), Size(neckW, h - bodyH + 2f), style = Stroke(width = 1.6f))
    drawRect(DrinkLook.PaperLight, Offset(x - w / 2f + 2f, y - bodyH * 0.7f), Size(w - 4f, bodyH * 0.3f))
}

/** Une pompe à bière posée sur le comptoir, au-dessus de [x]. */
private fun DrawScope.beerTap(x: Float, counterY: Float) {
    drawRect(DrinkLook.Ink, Offset(x - 5f, counterY - 40f), Size(10f, 40f))
    drawRect(DrinkLook.Ink, Offset(x - 11f, counterY - 40f), Size(22f, 8f))
    drawRoundRect(DrinkLook.Red, Offset(x - 4f, counterY - 66f), Size(8f, 28f), CornerRadius(3f))
    drawRoundRect(DrinkLook.Ink, Offset(x - 4f, counterY - 66f), Size(8f, 28f), CornerRadius(3f), style = Stroke(width = 1.6f))
    drawRect(DrinkLook.Ink, Offset(x - 2f, counterY - 14f), Size(4f, 14f))
}

/** Le décor de l'écran, qui ne change jamais : un bar, vu de face, avec ses étagères, son comptoir et sa pompe à bière. */
internal fun DrawScope.drawDrinkScene(labels: DrinkLabels, measurer: TextMeasurer) {
    val w = Drink.WIDTH
    val h = Drink.HEIGHT
    // Le mur, avec son soubassement, puis le sol.
    drawRect(
        brush = Brush.verticalGradient(0f to Color(0xFFEFEBDD), 1f to Color(0xFFD9D6C2), startY = 0f, endY = 300f),
        size = Size(w, 300f)
    )
    drawRect(DrinkLook.Ink.fade(0.08f), Offset(0f, 215f), Size(w, 90f))
    drawRect(DrinkLook.Red, Offset(0f, 205f), Size(w, 10f))
    drawRect(DrinkLook.Ink, Offset(0f, 215f), Size(w, 2.5f))
    drawRect(
        brush = Brush.verticalGradient(0f to Color(0xFFB7BCAE), 1f to Color(0xFF9DA393), startY = 300f, endY = h),
        topLeft = Offset(0f, 300f),
        size = Size(w, h - 300f)
    )
    for (y in floatArrayOf(326f, 352f, 382f)) {
        drawLine(DrinkLook.Ink.fade(0.18f), Offset(0f, y), Offset(w, y), strokeWidth = 1.6f)
    }

    // L'enseigne, au milieu du mur.
    drawRoundRect(Color.White, Offset(w / 2f - 70f, 16f), Size(140f, 44f), CornerRadius(8f))
    drawRoundRect(DrinkLook.Ink, Offset(w / 2f - 70f, 16f), Size(140f, 44f), CornerRadius(8f), style = Stroke(width = 3f))
    drawRect(DrinkLook.Red, Offset(w / 2f - 70f, 52f), Size(140f, 4f))
    drinkWord(measurer, labels.sign, w / 2f + 3f, 35f, 28f, DrinkLook.Red, italic = true, spacing = 8f)

    // Deux étagères de bouteilles, derrière le comptoir.
    for (row in 0 until 2) {
        val shelfY = 120f + row * 62f
        for (k in 0 until 7) {
            val x = 214f + k * 48f
            bottle(x, shelfY, 34f + ((k * 5 + row * 3) % 4) * 6f, k + row)
        }
        drawRect(DrinkLook.Wood, Offset(190f, shelfY), Size(340f, 7f))
        drawRect(DrinkLook.Ink, Offset(190f, shelfY), Size(340f, 7f), style = Stroke(width = 1.8f))
    }

    // Le comptoir : le dessus clair, la face en bois avec un liseré rouge, et les pompes.
    val counterTop = 268f
    beerTap(250f, counterTop + 2f)
    beerTap(470f, counterTop + 2f)
    drawRect(DrinkLook.PaperLight, Offset(0f, counterTop), Size(w, 16f))
    drawRect(DrinkLook.Ink, Offset(0f, counterTop), Size(w, 16f), style = Stroke(width = 2.5f))
    drawRect(
        brush = Brush.verticalGradient(0f to DrinkLook.Wood, 1f to DrinkLook.WoodDark, startY = counterTop + 16f, endY = 340f),
        topLeft = Offset(0f, counterTop + 16f),
        size = Size(w, 56f)
    )
    var plank = 60f
    while (plank < w) {
        drawLine(DrinkLook.Ink.fade(0.35f), Offset(plank, counterTop + 16f), Offset(plank, 340f), strokeWidth = 1.6f)
        plank += 60f
    }
    drawRect(DrinkLook.Red, Offset(0f, counterTop + 34f), Size(w, 6f))
    drawRect(DrinkLook.Ink, Offset(0f, 340f), Size(w, 3f))
}

// ------------------------------------------------------------------------------------------------------------ le verre

/**
 * Un verre de bière centré en ([cx], [cy]), incliné de [tilt] degrés, à l'échelle [mugScale] : le niveau [level] (1 plein, 0 vide), sa
 * mousse et ses bulles. [clockMs] fait monter les bulles.
 */
private fun DrawScope.mug(cx: Float, cy: Float, tilt: Float, mugScale: Float, level: Float, clockMs: Float) {
    withTransform({
        translate(cx, cy)
        rotate(tilt, Offset.Zero)
        scale(mugScale, mugScale, Offset.Zero)
    }) {
        val half = 22f
        val tall = 28f
        // Le verre lui-même, légèrement bleuté.
        drawRoundRect(DrinkLook.Glass.fade(0.55f), Offset(-half, -tall), Size(half * 2f, tall * 2f), CornerRadius(6f))
        val filled = level.coerceIn(0f, 1f)
        if (filled > 0.02f) {
            val liquidTop = tall - 2f - (tall * 2f - 4f) * filled
            drawRoundRect(
                DrinkLook.Beer,
                Offset(-half + 2f, liquidTop),
                Size(half * 2f - 4f, tall - liquidTop - 2f),
                CornerRadius(4f)
            )
            // Les bulles montent dans la bière.
            val depth = tall - 2f - liquidTop
            if (depth > 8f) {
                for (k in 0 until 4) {
                    val rise = ((clockMs * 0.03f + k * 13f) % depth)
                    drawCircle(DrinkLook.Foam.fade(0.7f), 1.6f + (k % 2), Offset(-14f + k * 9f, tall - 4f - rise))
                }
            }
            // La mousse, qui dépasse du verre quand il est plein.
            drawRoundRect(DrinkLook.Foam, Offset(-half + 1f, liquidTop - 7f), Size(half * 2f - 2f, 9f), CornerRadius(4f))
            drawCircle(DrinkLook.Foam, 5f, Offset(-12f, liquidTop - 6f))
            drawCircle(DrinkLook.Foam, 6f, Offset(2f, liquidTop - 8f))
            drawCircle(DrinkLook.Foam, 5f, Offset(14f, liquidTop - 6f))
        }
        drawRoundRect(DrinkLook.Ink, Offset(-half, -tall), Size(half * 2f, tall * 2f), CornerRadius(6f), style = Stroke(width = 2.6f))
        // L'anse, à droite, et un reflet.
        drawRoundRect(DrinkLook.Ink, Offset(half - 1f, -16f), Size(14f, 32f), CornerRadius(8f), style = Stroke(width = 4f))
        drawLine(Color.White.fade(0.6f), Offset(-14f, -20f), Offset(-14f, 14f), strokeWidth = 2.4f, cap = StrokeCap.Round)
    }
}

// ------------------------------------------------------------------------------------------------------------ le personnage

private fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

/** Le personnage et son verre : de face quand il est d'aplomb, de profil et penché quand il chancelle, couché quand il est tombé. */
private fun DrawScope.drinkHero(game: DrinkGame, sprites: DriveSprites) {
    val lean = game.lean
    val steady = lean == 0f
    val fallen = game.fallen
    val busy = game.busy
    val side = game.peeSide.toFloat()
    // Le sens de sa marche vers l'urinoir (puis de son retour) : il est de profil.
    val walkDirection = when (game.peePhase) {
        Drink.PEE_GOING, Drink.PEE_RELIEF -> side
        Drink.PEE_BACK -> -side
        else -> 0f
    }
    val image = when {
        busy -> if (walkDirection > 0f) sprites.right else sprites.left
        steady -> sprites.front
        lean > 0f -> sprites.right
        else -> sprites.left
    } ?: return
    val w = (image.width * DrinkLook.HERO_SCALE).roundToInt()
    val h = (image.height * DrinkLook.HERO_SCALE).roundToInt()
    val feetY = DrinkLook.FEET_Y
    val cx = Drink.WIDTH / 2f + lean * DrinkLook.LEAN_SHIFT + side * game.walk * Drink.URINAL_DISTANCE
    val top = feetY - h
    val clock = game.clockMs
    val walking = game.peePhase == Drink.PEE_GOING || game.peePhase == Drink.PEE_BACK

    // L'ombre sous ses pieds (plus longue quand il est couché).
    val shadowWidth = if (fallen) h * 0.95f else w * 1.2f
    val shadowCenter = if (fallen) cx + lean * h * 0.4f else cx
    drawOval(Color.Black.copy(alpha = 0.22f), Offset(shadowCenter - shadowWidth / 2f, feetY - 7f), Size(shadowWidth, 14f))

    // Le corps oscille doucement ; quand il chancelle il penche et tremble ; par terre, il est couché (il tombe vite, se relève vite).
    val angle = when {
        game.over -> 0f
        fallen -> {
            val down = min(1f, (Drink.FALL_MS - game.fallMs) / 260f)
            val up = min(1f, game.fallMs / 360f)
            lean * 90f * min(down, up)
        }
        busy -> if (walking) sin(clock / 110f) * 2f else 0f
        steady -> sin(clock / 420f) * 1.2f
        else -> lean * DrinkLook.LEAN_DEGREES + sin(clock / 55f) * abs(lean) * 3f
    }
    val bob = if (walking) -abs(sin(clock / 110f)) * 6f else 0f
    val shake = if (game.blockedMs > 0f && !fallen) sin(clock / 25f) * 3f else 0f
    withTransform({ translate(0f, bob); rotate(angle, Offset(cx, feetY)) }) {
        drawImage(
            image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset((cx - w / 2f).roundToInt(), top.roundToInt()),
            dstSize = IntSize(w, h),
            filterQuality = FilterQuality.None
        )
        if (steady && !busy) {
            // Il tient son verre de la main droite ; chaque gorgée le lève à la bouche, le verre penché.
            val t = if (game.over) 0f else (game.sipMs / Drink.SIP_MS).coerceIn(0f, 1f)
            val mugX = lerp(cx + 34f, cx + 22f, t) + shake
            val mugY = lerp(top + 112f, top + 52f, t)
            val elbow = Offset(cx + 36f, top + 104f)
            val hand = Offset(mugX - 2f, mugY + 14f)
            drawLine(DrinkLook.Skin, elbow, hand, strokeWidth = 11f, cap = StrokeCap.Round)
            mug(mugX, mugY, lerp(0f, -40f, t), 0.9f, game.level, clock)
            drawCircle(DrinkLook.Skin, 6.5f, hand)
            if (game.refillMs > 0f) {
                val u = game.refillMs / Drink.REFILL_MS
                drawCircle(DrinkLook.Foam.fade(u), 22f + (1f - u) * 34f, Offset(mugX, mugY), style = Stroke(width = 3f))
            }
        } else {
            // De profil : le verre au bout du bras ballant, qui se renverse à mesure qu'il penche (ou qu'il marche).
            val direction = if (busy) walkDirection else if (lean > 0f) 1f else -1f
            val tilt = if (busy) direction * 8f else direction * (20f + abs(lean) * 30f)
            val mugX = cx + direction * 16f + shake
            val mugY = top + 122f
            mug(mugX, mugY, tilt, 0.9f, game.level, clock)
        }
    }
    if (fallen && !game.over) {
        // Des étoiles tournent autour de sa tête.
        val headX = cx + lean * h * 0.92f
        val headY = feetY - 36f
        for (k in 0 until 3) {
            val a = clock / 260f + k * 2.094f
            drawCircle(DrinkLook.Beer, 5f, Offset(headX + cos(a) * 34f, headY - 22f + sin(a) * 10f))
            drawCircle(DrinkLook.Ink, 5f, Offset(headX + cos(a) * 34f, headY - 22f + sin(a) * 10f), style = Stroke(width = 1.6f))
        }
    }
}

// ------------------------------------------------------------------------------------------------------------ l'urinoir

/** Le côté de l'écran où se trouve l'urinoir : un pan de mur carrelé, avec sa cuvette en porcelaine. */
private fun DrawScope.drinkUrinal(game: DrinkGame) {
    val side = game.peeSide.toFloat()
    val w = Drink.WIDTH
    val panelW = 74f
    val left = if (side > 0f) w - panelW else 0f
    val top = 150f
    val tile = Color(0xFFCFE4EC)
    drawRect(tile, Offset(left, top), Size(panelW, Drink.HEIGHT - top))
    var tx = left
    while (tx <= left + panelW) {
        drawLine(DrinkLook.Ink.fade(0.25f), Offset(tx, top), Offset(tx, Drink.HEIGHT), strokeWidth = 1.4f)
        tx += 18.5f
    }
    var ty = top
    while (ty <= Drink.HEIGHT) {
        drawLine(DrinkLook.Ink.fade(0.25f), Offset(left, ty), Offset(left + panelW, ty), strokeWidth = 1.4f)
        ty += 20f
    }
    // Le bord du pan de mur, côté salle.
    val edge = if (side > 0f) left else left + panelW
    drawLine(DrinkLook.Ink, Offset(edge, top), Offset(edge, Drink.HEIGHT), strokeWidth = 3f)
    // La cuvette, contre le mur du fond : un tuyau, la porcelaine, la bonde.
    val bowlX = if (side > 0f) w - 20f else 20f
    val bowlTop = Drink.HEIGHT - 128f
    drawRect(DrinkLook.Ink.fade(0.55f), Offset(bowlX - 3f, top + 6f), Size(6f, bowlTop - top - 6f))
    val bowl = Path()
    bowl.moveTo(bowlX - side * 20f, bowlTop)
    bowl.lineTo(bowlX + side * 20f, bowlTop - 8f)
    bowl.lineTo(bowlX + side * 20f, bowlTop + 62f)
    bowl.quadraticTo(bowlX - side * 6f, bowlTop + 74f, bowlX - side * 22f, bowlTop + 50f)
    bowl.close()
    drawPath(bowl, DrinkLook.PaperLight)
    drawPath(bowl, DrinkLook.Ink, style = Stroke(width = 2.8f, join = StrokeJoin.Round))
    drawOval(DrinkLook.Ink.fade(0.35f), Offset(bowlX - 10f, bowlTop + 22f), Size(20f, 22f))
}

/** Le filet et la jauge de soulagement (A ou B à marteler) devant l'urinoir. */
private fun DrawScope.drinkRelief(game: DrinkGame) {
    val side = game.peeSide.toFloat()
    val feetY = DrinkLook.FEET_Y
    val handX = Drink.WIDTH / 2f + side * Drink.URINAL_DISTANCE
    if (game.peeProgress > 0) {
        // Un petit jet jaune de la main à la cuvette, qui coule en tremblant.
        val bowlX = if (side > 0f) Drink.WIDTH - 20f else 20f
        val y0 = feetY - 108f
        val flow = sin(game.clockMs / 50f) * 2f
        val stream = Path()
        stream.moveTo(handX + side * 36f, y0)
        stream.quadraticTo((handX + bowlX) / 2f + side * 4f, y0 - 6f + flow, bowlX - side * 6f, y0 + 40f)
        drawPath(stream, DrinkLook.Beer, style = Stroke(width = 4f, cap = StrokeCap.Round))
    }
    // La jauge au milieu de l'écran, avec la touche à marteler (A puis B) au-dessus.
    val barW = 200f
    val barH = 18f
    val x = Drink.WIDTH / 2f - barW / 2f
    val y = 224f
    drawRoundRect(DrinkLook.PaperLight, Offset(x, y), Size(barW, barH), CornerRadius(8f))
    if (game.relief > 0f) drawRoundRect(DrinkLook.Beer, Offset(x, y), Size(barW * game.relief, barH), CornerRadius(8f))
    drawRoundRect(DrinkLook.Ink, Offset(x, y), Size(barW, barH), CornerRadius(8f), style = Stroke(width = 3f))
    val glyph = if (((game.clockMs / 160f).toInt() % 2) == 0) GLYPH_A else GLYPH_B
    driveGlyph(glyph, Drink.WIDTH / 2f, y - 26f, 34f, DrinkLook.Red)
}

/** Le signe d'une envie pressante, avant le départ : un point d'exclamation qui clignote au-dessus du personnage. */
private fun DrawScope.drinkUrge(game: DrinkGame) {
    val pulse = 0.6f + 0.4f * sin(game.clockMs / 90f)
    driveGlyph(GLYPH_BANG, Drink.WIDTH / 2f, DrinkLook.FEET_Y - 208f - 22f, 34f, DrinkLook.Beer.fade(pulse))
}

/** Les flèches qui montrent de quel côté il faut appuyer pour redresser le personnage, plus grandes quand il penche davantage. */
private fun DrawScope.balanceHint(game: DrinkGame) {
    val lean = game.lean
    if (lean == 0f || game.over || game.fallen) return
    // Pencher à droite se corrige avec la flèche de gauche, et inversement.
    val direction = if (lean > 0f) -1f else 1f
    val pulse = 0.6f + 0.4f * sin(game.clockMs / 90f)
    val size = 26f + abs(lean) * 26f
    val baseX = Drink.WIDTH / 2f + direction * 150f
    val y = 236f
    for (k in 0 until 2) {
        val x = baseX + direction * k * size * 0.9f
        val arrow = Path()
        arrow.moveTo(x + direction * size * 0.6f, y)
        arrow.lineTo(x - direction * size * 0.4f, y - size * 0.6f)
        arrow.lineTo(x - direction * size * 0.4f, y + size * 0.6f)
        arrow.close()
        drawPath(arrow, DrinkLook.Red.fade(pulse * (1f - k * 0.35f)))
        drawPath(arrow, DrinkLook.Ink, style = Stroke(width = 3f, join = StrokeJoin.Round))
    }
    driveGlyph(GLYPH_BANG, Drink.WIDTH / 2f, DrinkLook.FEET_Y - 208f - 22f, 34f, DrinkLook.Red.fade(pulse))
}

/** Le temps qui reste, en « m:ss » ; il ne tombe pas à zéro tant que la dernière seconde n'est pas finie. */
internal fun drinkClockText(timeLeftMs: Float): String {
    val seconds = max(0, ceil(timeLeftMs / 1000f).toInt())
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

/** Tout ce qui change pendant la partie, dans le repère de 720 x 405 de l'écran. */
internal fun DrawScope.drawDrinkLive(game: DrinkGame, sprites: DriveSprites, labels: DrinkLabels, measurer: TextMeasurer) {
    // Les compteurs : le temps à gauche, la quantité bue et les verres vidés à droite.
    val urgent = !game.over && game.timeLeftMs <= 10_000f
    val ink = DrinkLook.Ink
    drinkWord(measurer, labels.time, 16f, 24f, 14f, ink.fade(0.6f), align = -1)
    drinkWord(measurer, drinkClockText(game.timeLeftMs), 16f, 62f, 46f, if (urgent) DrinkLook.Red else ink, align = -1)
    drinkWord(measurer, labels.volume, Drink.WIDTH - 16f, 24f, 14f, ink.fade(0.6f), align = 1)
    drinkWord(measurer, game.cl.toString(), Drink.WIDTH - 16f, 62f, 46f, ink, align = 1)
    drinkWord(measurer, labels.glasses, Drink.WIDTH - 16f, 108f, 14f, ink.fade(0.6f), align = 1)
    drinkWord(measurer, game.glasses.toString(), Drink.WIDTH - 16f, 142f, 34f, ink, align = 1)

    if (game.peePhase != Drink.PEE_NONE && !game.over) drinkUrinal(game)
    drinkHero(game, sprites)
    balanceHint(game)
    if (!game.over) {
        if (game.peePhase == Drink.PEE_URGE) drinkUrge(game)
        if (game.peePhase == Drink.PEE_RELIEF) drinkRelief(game)
    }
    val shade = min(0.25f, game.blockedMs / Drink.BLOCKED_MS * 0.25f)
    if (shade > 0f) drawRect(DrinkLook.Red.fade(shade), size = Size(Drink.WIDTH, Drink.HEIGHT))
}
