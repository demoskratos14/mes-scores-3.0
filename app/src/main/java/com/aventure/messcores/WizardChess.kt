package com.aventure.messcores

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.core.content.edit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Échecs, style « sorcier » : des statues de pierre (marbre pour les blancs, obsidienne pour les noirs) sur un damier
 * de dalles, et une animation quand une pièce en prend une autre (la pièce prise se brise en éclats). Le style est
 * purement visuel : il ne change rien aux règles ni à ce qui voyage entre les deux téléphones, chaque joueur choisit
 * donc le sien. Les pièces sont dessinées en vectoriel (tracés dans un carré de 100 × 100), sans aucune image.
 */

/** Mémorise le style choisi (classique ou sorcier), pour le retrouver au prochain lancement. */
internal object ChessStylePreference {
    private const val PREFS = "mes_scores_setup"
    private const val KEY_WIZARD = "chess_wizard_style"

    fun load(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_WIZARD, false)

    fun save(context: Context, wizard: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_WIZARD, wizard) }
    }
}

// ---------- Ce qui se passe sur l'échiquier quand un coup est joué ----------

/**
 * Un coup vu pour l'animation : la pièce [mover] va de [from] à [to] ; si elle en prend une, [captured] est la pièce
 * prise (« . » sinon) et [capturedSquare] sa case (différente de [to] pour une prise en passant).
 */
internal data class MoveFx(val from: Int, val to: Int, val mover: Char, val captured: Char, val capturedSquare: Int) {
    val isCapture: Boolean get() = captured != '.'
}

internal object WizardFx {

    /** Le coup [move] joué dans la position [before] (les 64 cases), ou null si la case de départ est vide. */
    fun detect(before: String, move: ChessMove): MoveFx? {
        if (move.from !in 0..63 || move.to !in 0..63 || before.length < 64) return null
        val mover = before[move.from]
        if (mover == '.') return null
        val target = before[move.to]
        // Le roque se note comme un coup du roi : la case d'arrivée est vide, ou occupée par un allié, jamais prise.
        if (target != '.' && Chess.isWhite(target) != Chess.isWhite(mover)) {
            return MoveFx(move.from, move.to, mover, target, move.to)
        }
        // Prise en passant : un pion change de colonne vers une case vide, la pièce prise est sur sa rangée de départ.
        if (target == '.' && mover.uppercaseChar() == 'P' && move.from % 8 != move.to % 8) {
            val square = (move.from / 8) * 8 + move.to % 8
            val taken = before[square]
            if (taken != '.' && Chess.isWhite(taken) != Chess.isWhite(mover)) {
                return MoveFx(move.from, move.to, mover, taken, square)
            }
        }
        return MoveFx(move.from, move.to, mover, '.', move.to)
    }
}

// ---------- Les pièces ----------

private class WizardArtData(
    val body: List<String>,
    val accents: List<String>,
    val lines: List<String>,
    val shield: String? = null,
    val shieldLines: List<String> = emptyList(),
    val eye: String? = null
)

private class WizardArt(
    val body: List<Path>,
    val accents: List<Path>,
    val lines: List<Path>,
    val shield: Path?,
    val shieldLines: List<Path>,
    val eye: Path?
)

private val WIZARD_BASE = listOf(
    "M16,92 H84 Q87,92 87,89 V86.5 Q87,83.5 84,83.5 H16 Q13,83.5 13,86.5 V89 Q13,92 16,92 Z",
    "M21,83.5 H79 L76.5,77 H23.5 Z"
)

private val WIZARD_ART_DATA: Map<Char, WizardArtData> = mapOf(
    'P' to WizardArtData(
        body = listOf(
            "M35,41 C32,24 41,14 50,14 C59,14 68,24 65,41 Z",
            "M32,43 Q50,37 68,43 L72,62 Q70,72 66,77 H34 Q30,72 28,62 Z"
        ),
        accents = listOf(
            "M50,14 C46,8 50,3 59,5 C55,8 55,12 57,16 Z"
        ),
        lines = listOf(
            "M41,32 H59",
            "M50,24 V41",
            "M62,45 V70",
            "M58,52 H66"
        ),
        shield = "M30,49 H49 V64 Q49,73 39.5,77 Q30,73 30,64 Z",
        shieldLines = listOf(
            "M39.5,51 V73",
            "M32,59 H47"
        )
    ),
    'R' to WizardArtData(
        body = listOf(
            "M25,12 H37 V21 H44 V12 H56 V21 H63 V12 H75 V32 L69,37 V68 L76,77 H24 L31,68 V37 L25,32 Z"
        ),
        accents = listOf(
            "M43,68 V57 Q50,47 57,57 V68 Z"
        ),
        lines = listOf(
            "M48,36 V44",
            "M25,32 H75",
            "M31,52 H43",
            "M57,52 H69",
            "M31,68 H43",
            "M57,68 H69"
        )
    ),
    'N' to WizardArtData(
        body = listOf(
            "M72,77 C73,62 74,50 69,38 C65,30 62,22 58,16 L58.5,4 L52,14 C44,18 36,28 28,41 C23,48 17,53 17,58 C17,63 22,65 26,63 C31,61 35,57 40,55 C42,60 40,68 33,77 Z"
        ),
        accents = listOf(
            "M50,15 C44,20 36,28 29,40 L34,44 C40,34 46,27 53,22 Z"
        ),
        lines = listOf(
            "M60,19 C66,29 70,41 68,58",
            "M63,22 C68,32 71,44 70,60",
            "M26,63 C32,52 38,46 44,40",
            "M17,58 L22,58"
        ),
        eye = "M38.2,36 C38.2,35.01 39.01,34.2 40,34.2 C40.99,34.2 41.8,35.01 41.8,36 C41.8,36.99 40.99,37.8 40,37.8 C39.01,37.8 38.2,36.99 38.2,36 Z"
    ),
    'B' to WizardArtData(
        body = listOf(
            "M69,32 C69,21 62,21 60,28 L64,30 C64,27 66,27 66,32 V77 H69 Z",
            "M50,6 C53,6 54,9 54,11 C54,13 52,14 50,14 C48,14 46,13 46,11 C46,9 47,6 50,6 Z",
            "M50,15 C63,24 66,38 59,47 H41 C34,38 37,24 50,15 Z",
            "M36,47 H64 L62,53 H38 Z",
            "M41,53 H59 C60,62 66,70 71,77 H29 C34,70 40,62 41,53 Z"
        ),
        accents = emptyList(),
        lines = listOf(
            "M44,38 L56,26",
            "M50,27 V33",
            "M47,30 H53",
            "M50,56 V74",
            "M44,60 Q50,66 56,60"
        )
    ),
    'Q' to WizardArtData(
        body = listOf(
            "M36,33 C31,45 33,58 38,67 L42,52 H58 L62,67 C67,58 69,45 64,33 Z",
            "M35,31 L31,13 L41,24 L50,9 L59,24 L69,13 L65,31 Z",
            "M40,31 H60 C60,39 56,45 50,45 C44,45 40,39 40,31 Z",
            "M37,46 H63 L61,52 H39 Z",
            "M40,52 H60 C61,62 68,70 74,77 H26 C32,70 39,62 40,52 Z"
        ),
        accents = listOf(
            "M30,10 C30,8.34 31.34,7 33,7 C34.66,7 36,8.34 36,10 C36,11.66 34.66,13 33,13 C31.34,13 30,11.66 30,10 Z",
            "M47,6 C47,4.34 48.34,3 50,3 C51.66,3 53,4.34 53,6 C53,7.66 51.66,9 50,9 C48.34,9 47,7.66 47,6 Z",
            "M64,10 C64,8.34 65.34,7 67,7 C68.66,7 70,8.34 70,10 C70,11.66 68.66,13 67,13 C65.34,13 64,11.66 64,10 Z"
        ),
        lines = listOf(
            "M37,28 H63",
            "M50,56 V75",
            "M43,58 Q50,64 57,58"
        )
    ),
    'K' to WizardArtData(
        body = listOf(
            "M30,52 Q50,45 70,52 L81,77 H19 Z",
            "M46.5,3 H53.5 V8 H58 V14 H53.5 V20 H46.5 V14 H42 V8 H46.5 Z",
            "M34,36 C34,26 42,21 50,21 C58,21 66,26 66,36 Z",
            "M39,36 H61 C61,44 57,49 50,49 C43,49 39,44 39,36 Z",
            "M41,45 C42,54 46,58 50,60 C54,58 58,54 59,45 C57,49 54,51 50,51 C46,51 43,49 41,45 Z"
        ),
        accents = listOf(
            "M43,56 H57 L59,77 H41 Z"
        ),
        lines = listOf(
            "M34,36 H66",
            "M50,21 V36",
            "M50,58 V76",
            "M43,64 H57",
            "M30,56 L26,77",
            "M70,56 L74,77"
        )
    )

)

private fun parsePath(data: String): Path = PathParser().parsePathString(data).toPath()

private val WIZARD_BASE_PATHS: List<Path> by lazy { WIZARD_BASE.map(::parsePath) }

private val WIZARD_ARTS: Map<Char, WizardArt> by lazy {
    WIZARD_ART_DATA.mapValues { (_, data) ->
        WizardArt(
            body = data.body.map(::parsePath),
            accents = data.accents.map(::parsePath),
            lines = data.lines.map(::parsePath),
            shield = data.shield?.let(::parsePath),
            shieldLines = data.shieldLines.map(::parsePath),
            eye = data.eye?.let(::parsePath)
        )
    }
}

// Marbre pour les blancs, obsidienne pour les noirs.
private val WHITE_FILL = Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFB9BFCC)), Offset.Zero, Offset(100f, 100f))
private val BLACK_FILL = Brush.linearGradient(listOf(Color(0xFF6B7186), Color(0xFF12141A)), Offset.Zero, Offset(100f, 100f))
private val WHITE_SHIELD = Brush.linearGradient(listOf(Color(0xFFB9BFCC), Color(0xFFFFFFFF)), Offset.Zero, Offset(100f, 100f))
private val BLACK_SHIELD = Brush.linearGradient(listOf(Color(0xFF2A2E3A), Color(0xFF555B70)), Offset.Zero, Offset(100f, 100f))
private val WHITE_EDGE = Color(0xFF4D5262)
private val BLACK_EDGE = Color(0xFF05060A)
private val BLACK_LINE = Color(0xFF9AA1B8)

/**
 * Dessine la pièce [piece] (lettre de la position, majuscule pour les blancs) dans la case dont le coin haut gauche est
 * [origin] et dont le côté est [cell]. [alpha] sert à la faire disparaître ; [shadow] ajoute l'ombre au pied. Le
 * cavalier noir regarde dans l'autre sens, vers les blancs.
 */
internal fun DrawScope.drawWizardPiece(piece: Char, origin: Offset, cell: Float, alpha: Float = 1f, shadow: Boolean = true) {
    val art = WIZARD_ARTS[piece.uppercaseChar()] ?: return
    val white = Chess.isWhite(piece)
    val flip = piece == 'n'
    val unit = cell / 100f * 0.94f
    val inset = (cell - 100f * unit) / 2f
    val fill = if (white) WHITE_FILL else BLACK_FILL
    val edge = if (white) WHITE_EDGE else BLACK_EDGE
    val lineColor = if (white) WHITE_EDGE else BLACK_LINE
    if (shadow) {
        drawOval(
            color = Color.Black.copy(alpha = 0.3f * alpha),
            topLeft = Offset(origin.x + cell * 0.1f, origin.y + cell * 0.86f),
            size = Size(cell * 0.8f, cell * 0.11f)
        )
    }
    withTransform({
        translate(origin.x + inset + (if (flip) 100f * unit else 0f), origin.y + inset)
        scale(if (flip) -unit else unit, unit, pivot = Offset.Zero)
    }) {
        val outline = Stroke(width = 2f, join = StrokeJoin.Round)
        for (path in WIZARD_BASE_PATHS + art.body) {
            drawPath(path, fill, alpha = alpha)
            drawPath(path, edge, alpha = alpha, style = outline)
        }
        for (path in art.accents) drawPath(path, edge, alpha = 0.55f * alpha)
        art.shield?.let { shield ->
            drawPath(shield, if (white) WHITE_SHIELD else BLACK_SHIELD, alpha = alpha)
            drawPath(shield, edge, alpha = alpha, style = outline)
            for (path in art.shieldLines) {
                drawPath(path, edge, alpha = 0.7f * alpha, style = Stroke(width = 1.6f, cap = StrokeCap.Round))
            }
        }
        for (path in art.lines) {
            drawPath(path, lineColor, alpha = 0.75f * alpha, style = Stroke(width = 1.6f, cap = StrokeCap.Round))
        }
        art.eye?.let { drawPath(it, if (white) WHITE_EDGE else Color(0xFFE8E8FF), alpha = alpha) }
    }
}

// ---------- Les dalles ----------

/** Donne à la case numéro [square] un air de dalle de pierre : léger dégradé, fines fissures, joints. */
internal fun DrawScope.drawWizardTile(square: Int) {
    drawRect(Brush.linearGradient(listOf(Color(0x26FFFFFF), Color(0x26000000)), Offset.Zero, Offset(size.width, size.height)))
    val random = Random(square * 31 + 5)
    repeat(2) {
        var x = random.nextFloat() * size.width
        var y = random.nextFloat() * size.height
        repeat(3) {
            val nx = (x + (random.nextFloat() - 0.5f) * size.width * 0.5f).coerceIn(0f, size.width)
            val ny = (y + (random.nextFloat() - 0.5f) * size.height * 0.5f).coerceIn(0f, size.height)
            drawLine(Color(0x22000000), Offset(x, y), Offset(nx, ny), strokeWidth = 1.2f)
            x = nx
            y = ny
        }
    }
    drawRect(Color(0x33000000), style = Stroke(width = 1f))
}

// ---------- L'animation d'un coup ----------

// Un éclat : un triangle du carré de la case (coordonnées de 0 à 1), sa vitesse d'envol (en côtés de case) et sa rotation.
private class Shard(val points: FloatArray, val cx: Float, val cy: Float, val vx: Float, val vy: Float, val spin: Float)

// La case découpée en 3 × 3 carrés, chacun en deux triangles, aux sommets intérieurs légèrement décalés (une brisure
// toujours la même, pour que l'animation soit identique d'un coup à l'autre).
private val SHARDS: List<Shard> by lazy {
    val random = Random(7)
    val grid = Array(4) { iy ->
        Array(4) { ix ->
            val interior = ix in 1..2 && iy in 1..2
            val jitter = if (interior) 0.05f else 0f
            Offset(
                ix / 3f + (random.nextFloat() - 0.5f) * 2f * jitter,
                iy / 3f + (random.nextFloat() - 0.5f) * 2f * jitter
            )
        }
    }
    val result = ArrayList<Shard>()
    for (iy in 0 until 3) {
        for (ix in 0 until 3) {
            val a = grid[iy][ix]
            val b = grid[iy][ix + 1]
            val c = grid[iy + 1][ix + 1]
            val d = grid[iy + 1][ix]
            for (triangle in listOf(listOf(a, b, c), listOf(a, c, d))) {
                val cx = triangle.sumOf { it.x.toDouble() }.toFloat() / 3f
                val cy = triangle.sumOf { it.y.toDouble() }.toFloat() / 3f
                var dx = cx - 0.5f + (random.nextFloat() - 0.5f) * 0.3f
                var dy = cy - 0.6f + (random.nextFloat() - 0.5f) * 0.3f
                val length = sqrt(dx * dx + dy * dy).coerceAtLeast(0.05f)
                dx /= length
                dy /= length
                val speed = 0.5f + random.nextFloat() * 0.9f
                result += Shard(
                    points = floatArrayOf(
                        triangle[0].x, triangle[0].y, triangle[1].x, triangle[1].y, triangle[2].x, triangle[2].y
                    ),
                    cx = cx,
                    cy = cy,
                    vx = dx * speed,
                    vy = dy * speed - 0.25f,
                    spin = (random.nextFloat() - 0.5f) * 260f
                )
            }
        }
    }
    result
}

private fun smooth(x: Float): Float = x * x * (3f - 2f * x)

/** Durée de l'animation d'un coup, en millisecondes : courte pour un déplacement, plus longue pour une prise. */
internal fun wizardMoveDuration(fx: MoveFx): Int = if (fx.isCapture) 1100 else 280

/**
 * Dessine, par-dessus l'échiquier (côté [size].width), le coup [fx] à l'avancement [progress] (de 0 à 1) : la pièce glisse
 * d'une case à l'autre ; si elle en prend une, la pièce prise se brise en éclats dans un éclair, avec de la poussière
 * et des étincelles, pendant que la pièce qui prend rebondit. [flipped] : les noirs sont en bas.
 */
internal fun DrawScope.drawMoveFx(fx: MoveFx, progress: Float, flipped: Boolean) {
    val cell = size.width / 8f
    fun origin(square: Int): Offset {
        val row = square / 8
        val col = square % 8
        return Offset((if (flipped) 7 - col else col) * cell, (if (flipped) 7 - row else row) * cell)
    }
    val from = origin(fx.from)
    val to = origin(fx.to)
    val slideEnd = if (fx.isCapture) 0.26f else 1f
    val slide = smooth((progress / slideEnd).coerceIn(0f, 1f))
    // Après l'impact : 0 à 1.
    val after = if (fx.isCapture) ((progress - slideEnd) / (1f - slideEnd)).coerceIn(0f, 1f) else 0f
    val capturedOrigin = origin(fx.capturedSquare)

    if (fx.isCapture) {
        if (progress < slideEnd) {
            drawWizardPiece(fx.captured, capturedOrigin, cell)
        } else {
            drawShatter(fx, capturedOrigin, cell, after)
        }
    }

    // La pièce qui bouge : elle se soulève un peu en glissant, puis rebondit à l'arrivée si elle a pris une pièce.
    val position = Offset(from.x + (to.x - from.x) * slide, from.y + (to.y - from.y) * slide)
    val bounce = if (progress < slideEnd) {
        1f + 0.1f * sin(PI.toFloat() * slide)
    } else {
        1f + 0.18f * sin(PI.toFloat() * (after * 2.5f).coerceIn(0f, 1f))
    }
    val center = Offset(position.x + cell / 2f, position.y + cell / 2f)
    withTransform({ scale(bounce, bounce, pivot = center) }) {
        drawWizardPiece(fx.mover, position, cell)
    }
}

// La pièce prise éclate : éclair, éclats qui volent et tombent en s'effaçant, poussière, étincelles.
private fun DrawScope.drawShatter(fx: MoveFx, origin: Offset, cell: Float, after: Float) {
    val center = Offset(origin.x + cell / 2f, origin.y + cell / 2f)
    val flash = (1f - after) * (1f - after)
    val flashRadius = cell * (0.35f + 0.75f * after)
    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0xCCFFF6C8), Color(0x00FFF6C8)), center = center, radius = flashRadius),
        radius = flashRadius,
        center = center,
        alpha = flash
    )

    val ease = 1f - (1f - after) * (1f - after)
    val fade = (1f - smooth(after)).coerceIn(0f, 1f)
    if (fade > 0.01f) {
        for (shard in SHARDS) {
            val triangle = Path().apply {
                moveTo(origin.x + shard.points[0] * cell, origin.y + shard.points[1] * cell)
                lineTo(origin.x + shard.points[2] * cell, origin.y + shard.points[3] * cell)
                lineTo(origin.x + shard.points[4] * cell, origin.y + shard.points[5] * cell)
                close()
            }
            val dx = shard.vx * ease * cell
            val dy = shard.vy * ease * cell + 0.9f * after * after * cell
            val pivot = Offset(origin.x + shard.cx * cell, origin.y + shard.cy * cell)
            withTransform({
                translate(dx, dy)
                rotate(shard.spin * after, pivot)
            }) {
                clipPath(triangle) { drawWizardPiece(fx.captured, origin, cell, alpha = fade, shadow = false) }
            }
        }
    }

    // De la poussière de pierre qui s'étend et se dissipe.
    val dust = if (Chess.isWhite(fx.captured)) Color(0xFFD8D8DE) else Color(0xFF3A3D48)
    repeat(10) { i ->
        val angle = (i * 36f + (i % 3) * 9f) * PI.toFloat() / 180f
        val distance = cell * (0.15f + 0.75f * ease) * (0.7f + 0.3f * ((i * 7) % 5) / 4f)
        drawCircle(
            color = dust,
            radius = cell * (0.06f + 0.1f * after),
            center = Offset(center.x + cos(angle) * distance, center.y + sin(angle) * distance * 0.8f),
            alpha = (1f - after) * 0.55f
        )
    }

    // Des étincelles magiques qui partent en rayons.
    repeat(8) { i ->
        val angle = (i * 45f + 12f) * PI.toFloat() / 180f
        val inner = cell * (0.1f + 0.25f * ease)
        val outer = cell * (0.2f + 0.55f * ease)
        drawLine(
            color = Color(0xFFFFE08A),
            start = Offset(center.x + cos(angle) * inner, center.y + sin(angle) * inner),
            end = Offset(center.x + cos(angle) * outer, center.y + sin(angle) * outer),
            strokeWidth = 3f,
            cap = StrokeCap.Round,
            alpha = (1f - after) * (1f - after)
        )
    }
}
