package com.aventure.messcores

import android.content.Context
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.core.content.edit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Échecs, style « sorcier » : des statues de pierre (marbre pour les blancs, obsidienne pour les noirs) sur un damier
 * de marbre blanc et noir, et une animation quand une pièce en prend une autre (la pièce prise se brise en éclats). Le style est
 * purement visuel : il ne change rien aux règles ni à ce qui voyage entre les deux téléphones, chaque joueur choisit
 * donc le sien. Les pièces sont dessinées en vectoriel (tracés dans un carré de 100 × 100), sans aucune image.
 * Quand une pièce en prend une autre, elle s'en prend avec son arme, à sa façon (voir [drawMoveFx]).
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

/*
 * Chaque pièce est une petite statue dessinée en couches, de l'arrière vers l'avant :
 *  - backThick, back : ce qui est derrière le corps (queue et pattes du cheval, cape, bouclier du roi) ;
 *  - body : le corps lui-même, rempli de pierre et cerné d'un trait ;
 *  - shades, lights : ombres et reflets (formes translucides) qui donnent du volume ;
 *  - shield, shieldLines : un bouclier devant le corps (tour, cavalier), dont la pierre est inversée ;
 *  - thick : des tubes cernés (bras, arbalète, crosse, masse) dessinés par un trait épais ;
 *  - front : ce qui passe devant (mains, garde de l'épée, tête de la masse) ;
 *  - lines : les gravures fines (briques, mailles, plis) ; eye : l'œil du cheval.
 * Tous les tracés sont dans un carré de 100 × 100, la pièce posée sur le socle octogonal commun à tous les camps.
 */
private class WizardArtData(
    val backThick: List<Pair<String, Float>> = emptyList(),
    val back: List<String> = emptyList(),
    val body: List<String>,
    val shades: List<String> = emptyList(),
    val lights: List<String> = emptyList(),
    val shield: String? = null,
    val shieldLines: List<String> = emptyList(),
    val thick: List<Pair<String, Float>> = emptyList(),
    val weaponThick: List<Pair<String, Float>> = emptyList(),
    val weaponFront: List<String> = emptyList(),
    val front: List<String> = emptyList(),
    val lines: List<String> = emptyList(),
    val eye: String? = null
)

private class WizardArt(
    val backThick: List<Pair<Path, Float>>,
    val back: List<Path>,
    val body: List<Path>,
    val shades: List<Path>,
    val lights: List<Path>,
    val shield: Path?,
    val shieldLines: List<Path>,
    val thick: List<Pair<Path, Float>>,
    val weaponThick: List<Pair<Path, Float>>,
    val weaponFront: List<Path>,
    val front: List<Path>,
    val lines: List<Path>,
    val eye: Path?
)

// Une teinte translucide (ombre ou reflet) posée par-dessus la pierre.
private class Tint(val color: Color, val alpha: Float)

// Le socle, commun à toutes les pièces : une dalle octogonale, une rangée de colonnettes, puis une dalle plus large.
private val WIZARD_BASE_LOW = listOf(
    "M14,93 H86 L90,90 V86.5 H10 V90 Z"
)

private val WIZARD_BASE_GAP = listOf(
    "M15,79 H85 V86.5 H15 Z"
)

private val WIZARD_BASE_PILLARS = listOf(
    "M16,79 H23 V86.5 H16 Z",
    "M31,79 H38 V86.5 H31 Z",
    "M46.5,79 H53.5 V86.5 H46.5 Z",
    "M62,79 H69 V86.5 H62 Z",
    "M77,79 H84 V86.5 H77 Z"
)

private val WIZARD_BASE_TOP = listOf(
    "M11,79.5 H89 L91,77 V74 H9 V77 Z"
)

private val WIZARD_BASE_LIGHT = listOf(
    "M9.6,74.6 H90.4 V76 H9.6 Z"
)

// Les pièces qui regardent de côté : les noires sont retournées, pour faire face aux blanches.
private val WIZARD_MIRRORED = setOf('P', 'N', 'R')

/**
 * Le point d'attache d'une arme dans le carré de 100 × 100 de la pièce ([px], [py] : la main, autour de laquelle
 * elle pivote) et sa direction au repos ([rx], [ry]), pour une pièce qui regarde à gauche (blanche).
 */
internal class WeaponRig(val px: Float, val py: Float, val rx: Float, val ry: Float)

private val WIZARD_RIGS = mapOf(
    'R' to WeaponRig(28f, 28f, -18f, 0f),    // l'arbalète, pointée vers la gauche
    'B' to WeaponRig(70f, 76f, 0f, -56f),    // la crosse, dressée
    'N' to WeaponRig(66f, 28f, 16f, -18f),   // la masse, levée en arrière
    'K' to WeaponRig(50f, 47f, 0f, 28f)      // l'épée, plantée devant lui
)

internal fun wizardRig(piece: Char): WeaponRig? = WIZARD_RIGS[piece.uppercaseChar()]

/**
 * La pièce [piece] est-elle dessinée retournée ? Les pièces de profil (pion, cavalier, tour) regardent vers la gauche
 * pour les blanches et vers la droite pour les noires, sauf si [faceRight] impose un côté (pour frapper une pièce).
 */
internal fun wizardFlip(piece: Char, faceRight: Boolean? = null): Boolean =
    piece.uppercaseChar() in WIZARD_MIRRORED && (faceRight ?: !Chess.isWhite(piece))

/** Où tombe, sur l'écran, le point ([x], [y]) du carré 100 × 100 d'une pièce posée dans la case [origin]. */
internal fun wizardSpritePoint(origin: Offset, cell: Float, flip: Boolean, x: Float, y: Float): Offset {
    val unit = cell / 100f * 0.94f
    val inset = (cell - 100f * unit) / 2f
    return Offset(origin.x + inset + (if (flip) 100f - x else x) * unit, origin.y + inset + y * unit)
}

private val WIZARD_ART_DATA: Map<Char, WizardArtData> = mapOf(
    // Pion : un soldat agenouillé, la tête baissée sous un chapeau à bord large, le dos couvert d'une cape de mailles.
    'P' to WizardArtData(
        body = listOf(
            "M40,38 C50,31 66,35 72,48 C76,58 76,68 73,75 H30 C26,69 27,62 31,58 C28,52 31,44 " +
                "40,38 Z",
            "M31,36 C30,41 33,45 38,45.5 C43,45.5 46,41 45,36 Z",
            "M23,75 C22,67 26,61 33,60 C40,59 46,62 48,68 L51,75 Z",
            "M38,45 C33,50 34,57 39,60 C44,58 47,53 46,47 Z",
            "M30,24 C34,7 54,6 58,24 C52,15 38,14 30,24 Z",
            "M29,31 C28,22 35,17 43,17 C52,17 57,23 55,31 Z",
            "M18,35 C19,30 30,28 40,28 C50,28 59,30 60,35 C54,39 42,38.5 33,38.5 C26,38.5 19,38 " +
                "18,35 Z"
        ),
        shades = listOf(
            "M60,42 C70,48 75,60 73,75 H63 C65,63 63,52 60,42 Z",
            "M31,38.5 C37,42 43,42 46,38.5 C43,39.5 36,39.5 31,38.5 Z",
            "M44,25 C50,24 54,26 55,31 H46 Z"
        ),
        lights = listOf(
            "M42,35 C52,31 62,34 68,42 C60,37 51,37 42,40 Z",
            "M31,28 C32,22 37,19 42,18 C38,21 35,24 34,30 Z"
        ),
        lines = listOf(
            "M52,42 C60,44 66,50 68,58",
            "M50,50 C58,52 63,58 64,67",
            "M48,58 C54,60 58,66 58,73",
            "M40,15 C44,13 49,14 52,17",
            "M30,64 C36,62 42,63 46,67",
            "M23,35.5 C32,34 46,34 56,35.5"
        )
    ),

    // Tour : une tour de briques, un arbalétrier à son sommet et un grand bouclier à losanges sur le flanc.
    'R' to WizardArtData(
        body = listOf(
            "M23,75 L28,36 H68 L73,75 Z",
            "M19,69 H77 V75 H19 Z",
            "M25,37 V21 H35 V27 H43 V21 H53 V27 H61 V21 H71 V37 Z",
            "M33,40 C32,28 36,22 43,22 C50,22 54,28 53,40 Z",
            "M36,22 C35,13 39,8 44,8 C49,8 52,13 51,22 Z"
        ),
        shades = listOf(
            "M41.5,60 V50 Q44.5,45 47.5,50 V60 Z",
            "M54,37 H68 L73,69 H58 Z",
            "M47,10 C50,12 51,16 51,22 H48 Z"
        ),
        lights = listOf(
            "M29,37 H36 L33,69 H26 Z"
        ),
        shield =
            "M59,42 H83 V55 C83,61 77,65 71,68 C65,65 59,61 59,55 Z",
        shieldLines = listOf(
            "M65,43 L77,61",
            "M77,43 L65,61",
            "M60,52 H82"
        ),
        weaponThick = listOf(
            "M10,28 H47" to 4f,
            "M14,19 C8,25 8,33 14,38" to 2.4f
        ),
        lines = listOf(
            "M26,46 H66",
            "M25.5,54 H69",
            "M25,62 H70",
            "M36,38 V46",
            "M52,38 V46",
            "M44,46 V54",
            "M32,54 V62",
            "M40,16 H48"
        )
    ),

    // Cavalier : un cheval cabré, caparaçonné d'un bouclier, et son cavalier qui brandit une masse d'armes.
    'N' to WizardArtData(
        backThick = listOf(
            "M72,50 C83,52 85,64 77,73" to 5.5f,
            "M57,59 L48,67" to 9f,
            "M48,67 L44,75" to 6f,
            "M68,58 L74,66" to 9f,
            "M74,66 L68,75" to 6f,
            "M39,50 L28,54" to 7f,
            "M28,54 L25,65" to 5f
        ),
        body = listOf(
            "M72,52 C70,44 62,36 54,30 C50,26 46,18 42,11 L41,3.5 L36.5,8 C30,11 22,18 17,26 " +
                "C14.5,29 15.5,33 19,33 C23,33 26,30 28,27 C30,29 31,32 31,36 C31,40 32,43 36,46 " +
                "C42,52 46,58 50,64 C54,70 62,70 66,66 C72,62 74,58 72,52 Z",
            "M50,44 C46,34 50,24 58,22 C66,21 71,28 70,38 C69,42 66,44 62,44 Z",
            "M54,21 C54,12 58,8 62,8 C67,8 70,13 69,21 C67,24 57,24 54,21 Z"
        ),
        shades = listOf(
            "M60,60 C66,58 70,56 72,52 C74,58 72,62 66,66 C62,68 58,66 56,64 Z",
            "M13,50 C13,48 19,48 19,50 C19,54 13,54 13,50 Z",
            "M22,63 C22,61 28,61 28,63 C28,67 22,67 22,63 Z"
        ),
        lights = listOf(
            "M52,32 C46,24 44,18 42,13 L45,13 C47,20 50,25 56,30 Z"
        ),
        shield =
            "M51,45 H67 V56 C67,62 62,66 59,68 C56,66 51,62 51,56 Z",
        shieldLines = listOf(
            "M55,46 L63,62",
            "M63,46 L55,62",
            "M52,53 H66"
        ),
        thick = listOf(
            "M36,44 L21,40" to 7.5f,
            "M21,40 L16,51" to 5.2f
        ),
        weaponThick = listOf(
            "M66,28 L77,18" to 5f,
            "M77,18 L82,10" to 2.4f
        ),
        weaponFront = listOf(
            "M89.0,9.3 L85.7,9.3 L85.8,12.6 L83.5,10.2 L81.2,12.5 L81.2,9.2 L77.9,9.3 L80.3,7.0 " +
                "L78.0,4.7 L81.3,4.7 L81.2,1.4 L83.5,3.8 L85.8,1.5 L85.8,4.8 L89.1,4.7 L86.7,7.0 Z"
        ),
        lines = listOf(
            "M57,15 H67",
            "M43,13 L38.5,15",
            "M45,19 L40,21",
            "M48,25 L43,27",
            "M21,28 L28,23",
            "M36,10 L30,16"
        ),
        eye =
            "M26,16.6 C26,15.6 26.8,14.8 27.8,14.8 C28.8,14.8 29.6,15.6 29.6,16.6 C29.6,17.6 " +
                "28.8,18.4 27.8,18.4 C26.8,18.4 26,17.6 26,16.6 Z"
    ),

    // Fou : un évêque en mitre, une main levée pour bénir, l'autre sur une crosse.
    'B' to WizardArtData(
        body = listOf(
            "M33,76 C35,64 38,54 40,44 H60 C62,54 65,64 67,76 Z",
            "M32,52 C32,42 40,38 50,38 C60,38 68,42 68,52 C60,50 40,50 32,52 Z",
            "M45,30 C45,26 47,24 50,24 C53,24 55,26 55,30 C55,35 53,38 50,38 C47,38 45,35 45,30 Z",
            "M41,27 C41,16 45,8 50,3 C55,8 59,16 59,27 Z",
            "M46,48 H54 L56,76 H44 Z"
        ),
        shades = listOf(
            "M56,46 C60,54 64,64 67,76 H58 C58,64 57,56 56,46 Z"
        ),
        lights = listOf(
            "M40,46 C38,54 36,64 34,74 L38,74 C39,64 41,54 43,46 Z"
        ),
        thick = listOf(
            "M38,47 C31,43 27,37 25,30" to 5f
        ),
        weaponThick = listOf(
            "M70,76 V20" to 3f,
            "M70,20 C70,8 82,8 82,16 C82,22 76,23 74,19" to 3f
        ),
        front = listOf(
            "M20.5,32 C19.5,25 23,21 27,23.5 L31,29 C29,33 24,34 20.5,32 Z"
        ),
        lines = listOf(
            "M50,8 V24",
            "M45,16 H55",
            "M50,50 V74",
            "M47,56 H53",
            "M47,64 H53"
        )
    ),

    // Dame : une guerrière casquée et couronnée de pointes, les bras croisés sous une longue cape de mailles.
    'Q' to WizardArtData(
        back = listOf(
            "M30,38 C26,50 26,64 28,76 H72 C74,64 74,50 70,38 Z"
        ),
        body = listOf(
            "M33,76 C36,66 38,56 39,46 H61 C62,56 64,66 67,76 Z",
            "M29,48 C27,40 34,35 42,35 H58 C66,35 73,40 71,48 C65,46 58,45 50,45 C42,45 35,46 " +
                "29,48 Z",
            "M40,34 C39,22 43,13 50,13 C57,13 61,22 60,34 Z",
            "M41,19 L43,9 L47,19 L50,6 L53,19 L57,9 L59,19 Z"
        ),
        shades = listOf(
            "M58,46 C62,56 64,66 67,76 H60 Z"
        ),
        lights = listOf(
            "M32,40 C36,37 42,36 46,36 C40,38 36,42 33,46 Z"
        ),
        thick = listOf(
            "M34,47 L60,57" to 6f,
            "M66,47 L40,57" to 6f
        ),
        front = listOf(
            "M57,53 C61,53 63,56 62,59 C60,61 56,60 55,57 Z",
            "M43,53 C39,53 37,56 38,59 C40,61 44,60 45,57 Z"
        ),
        lines = listOf(
            "M44,28 H56",
            "M50,60 V75",
            "M43,62 V74",
            "M57,62 V74"
        )
    ),

    // Roi : un chevalier couronné, les deux mains sur le pommeau de son épée plantée, un bouclier derrière lui.
    'K' to WizardArtData(
        back = listOf(
            "M30,40 C27,52 26,64 25,76 H75 C74,64 73,52 70,40 Z",
            "M17,44 C17,41 19,40 22,40 H35 V70 C25,66 17,58 17,44 Z"
        ),
        body = listOf(
            "M38,76 L40,66 H49 V76 Z",
            "M51,76 V66 H60 L62,76 Z",
            "M36,56 H64 L68,68 H32 Z",
            "M35,40 C37,37 43,36 50,36 C57,36 63,37 65,40 L62,57 H38 Z",
            "M29,45 C29,39 33,36 38,37 L38,46 C34,48 31,48 29,45 Z",
            "M71,45 C71,39 67,36 62,37 L62,46 C66,48 69,48 71,45 Z",
            "M42,37 C41,29 42,19 50,17 C58,19 59,29 58,37 Z",
            "M42,22 L43,13 L46.5,17 L50,10 L53.5,17 L57,13 L58,22 Z",
            "M49.2,1.5 H50.8 V4.5 H53.5 V6.3 H50.8 V10 H49.2 V6.3 H46.5 V4.5 H49.2 Z"
        ),
        shades = listOf(
            "M60,40 L64,56 H58 Z",
            "M26,56 C30,62 33,68 35,70 V56 Z",
            "M58,22 L58,37 H54.5 C56,31 56,26 54.5,19 Z"
        ),
        lights = listOf(
            "M37,40 C40,38 44,37 47,37 C43,40 41,46 41,54 H39 Z",
            "M30,44 C31,40 34,38 37,38 C34,41 32,44 32,47 Z"
        ),
        weaponFront = listOf(
            "M47.4,52 H52.6 L51,75.5 H49 Z",
            "M38,58 C38,56 41,56 43,56.5 H57 C59,56 62,56 62,58 C62,61 59,61.6 57,61 H43 " +
                "C41,61.6 38,61 38,58 Z"
        ),
        front = listOf(
            "M44.5,45 C44.5,41.5 55.5,41.5 55.5,45 C55.5,50 53,52.5 50,52.5 C47,52.5 44.5,50 " +
                "44.5,45 Z",
            "M48,38 C48,36.5 52,36.5 52,38 C52,41.5 48,41.5 48,38 Z"
        ),
        lines = listOf(
            "M45,29 H55",
            "M35,64 H65",
            "M27,48 L35,62",
            "M35,48 L27,62",
            "M22,46 V58",
            "M50,53 V56"
        )
    )
)

private fun parsePath(data: String): Path = PathParser().parsePathString(data).toPath()

private fun parsePaths(data: List<String>): List<Path> = data.map(::parsePath)

private fun parseTubes(data: List<Pair<String, Float>>): List<Pair<Path, Float>> =
    data.map { (path, width) -> parsePath(path) to width }

private val WIZARD_BASE_LOW_PATHS: List<Path> by lazy { parsePaths(WIZARD_BASE_LOW) }
private val WIZARD_BASE_GAP_PATHS: List<Path> by lazy { parsePaths(WIZARD_BASE_GAP) }
private val WIZARD_BASE_PILLAR_PATHS: List<Path> by lazy { parsePaths(WIZARD_BASE_PILLARS) }
private val WIZARD_BASE_TOP_PATHS: List<Path> by lazy { parsePaths(WIZARD_BASE_TOP) }
private val WIZARD_BASE_LIGHT_PATHS: List<Path> by lazy { parsePaths(WIZARD_BASE_LIGHT) }

private val WIZARD_ARTS: Map<Char, WizardArt> by lazy {
    WIZARD_ART_DATA.mapValues { (_, data) ->
        WizardArt(
            backThick = parseTubes(data.backThick),
            back = parsePaths(data.back),
            body = parsePaths(data.body),
            shades = parsePaths(data.shades),
            lights = parsePaths(data.lights),
            shield = data.shield?.let(::parsePath),
            shieldLines = parsePaths(data.shieldLines),
            thick = parseTubes(data.thick),
            weaponThick = parseTubes(data.weaponThick),
            weaponFront = parsePaths(data.weaponFront),
            front = parsePaths(data.front),
            lines = parsePaths(data.lines),
            eye = data.eye?.let(::parsePath)
        )
    }
}

// Pierre claire pour les blancs, pierre sombre (gris d'obsidienne) pour les noirs : le dégradé va du coin haut gauche
// (éclairé) au coin bas droit (dans l'ombre), comme les statues des photos.
private val WHITE_FILL = Brush.linearGradient(listOf(Color(0xFFF4F4F1), Color(0xFFA9ADB6)), Offset.Zero, Offset(100f, 100f))
private val BLACK_FILL = Brush.linearGradient(listOf(Color(0xFF848996), Color(0xFF1B1D23)), Offset.Zero, Offset(100f, 100f))
private val WHITE_SHIELD = Brush.linearGradient(listOf(Color(0xFFA9ADB6), Color(0xFFF4F4F1)), Offset.Zero, Offset(100f, 100f))
private val BLACK_SHIELD = Brush.linearGradient(listOf(Color(0xFF2C303A), Color(0xFF5C6274)), Offset.Zero, Offset(100f, 100f))
private val WHITE_EDGE = Color(0xFF4A4E5C)
private val BLACK_EDGE = Color(0xFF050608)
private val BLACK_LINE = Color(0xFFA4ABC2)
private val WHITE_SHADE = Tint(Color(0xFF3E4250), 0.30f)
private val BLACK_SHADE = Tint(Color(0xFF000000), 0.45f)
private val WHITE_LIGHT = Tint(Color(0xFFFFFFFF), 0.60f)
private val BLACK_LIGHT = Tint(Color(0xFFD0D5E8), 0.24f)

private val OUTLINE = Stroke(width = 2f, join = StrokeJoin.Round)
private val PILLAR_OUTLINE = Stroke(width = 1.4f, join = StrokeJoin.Round)
private val ENGRAVING = Stroke(width = 1.6f, cap = StrokeCap.Round)

// Une forme pleine : remplie de pierre, puis cernée d'un trait.
private fun DrawScope.solid(path: Path, fill: Brush, edge: Color, alpha: Float, outline: Stroke = OUTLINE) {
    drawPath(path, fill, alpha = alpha)
    drawPath(path, edge, alpha = alpha, style = outline)
}

// Un tube cerné (bras, arbalète, crosse, patte) : un trait épais de pierre, posé sur un trait un peu plus large.
private fun DrawScope.tube(path: Path, width: Float, fill: Brush, edge: Color, alpha: Float) {
    drawPath(path, edge, alpha = alpha, style = Stroke(width = width + 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, fill, alpha = alpha, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * Dessine la pièce [piece] (lettre de la position, majuscule pour les blancs) dans la case dont le coin haut gauche est
 * [origin] et dont le côté est [cell]. [alpha] sert à la faire disparaître ; [shadow] ajoute l'ombre au pied. Le pion,
 * le cavalier et la tour noirs regardent dans l'autre sens, vers les blancs (voir [wizardFlip] pour [faceRight]).
 * [weaponDeg] et [weaponScale] font pivoter (en degrés, dans le sens des aiguilles d'une montre à l'écran) et grandir
 * l'arme de la pièce autour de sa main : arbalète, crosse, masse, épée (voir [WeaponRig]).
 */
internal fun DrawScope.drawWizardPiece(
    piece: Char,
    origin: Offset,
    cell: Float,
    alpha: Float = 1f,
    shadow: Boolean = true,
    faceRight: Boolean? = null,
    weaponDeg: Float = 0f,
    weaponScale: Float = 1f
) {
    val art = WIZARD_ARTS[piece.uppercaseChar()] ?: return
    val white = Chess.isWhite(piece)
    val flip = wizardFlip(piece, faceRight)
    val unit = cell / 100f * 0.94f
    val inset = (cell - 100f * unit) / 2f
    val fill = if (white) WHITE_FILL else BLACK_FILL
    val plate = if (white) WHITE_SHIELD else BLACK_SHIELD
    val edge = if (white) WHITE_EDGE else BLACK_EDGE
    val lineColor = if (white) WHITE_EDGE else BLACK_LINE
    val shade = if (white) WHITE_SHADE else BLACK_SHADE
    val light = if (white) WHITE_LIGHT else BLACK_LIGHT
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
        // Le socle.
        for (path in WIZARD_BASE_LOW_PATHS) solid(path, fill, edge, alpha)
        for (path in WIZARD_BASE_GAP_PATHS) drawPath(path, edge, alpha = 0.9f * alpha)
        for (path in WIZARD_BASE_PILLAR_PATHS) solid(path, fill, edge, alpha, PILLAR_OUTLINE)
        for (path in WIZARD_BASE_TOP_PATHS) solid(path, fill, edge, alpha)
        for (path in WIZARD_BASE_LIGHT_PATHS) drawPath(path, light.color, alpha = light.alpha * alpha)

        // Derrière le corps.
        for ((path, width) in art.backThick) tube(path, width, fill, edge, alpha)
        for (path in art.back) solid(path, fill, edge, alpha)

        // Le corps, avec son relief.
        for (path in art.body) solid(path, fill, edge, alpha)
        for (path in art.shades) drawPath(path, shade.color, alpha = shade.alpha * alpha)
        for (path in art.lights) drawPath(path, light.color, alpha = light.alpha * alpha)

        // Devant le corps : bouclier, bras et armes, mains.
        art.shield?.let { shield ->
            solid(shield, plate, edge, alpha)
            for (path in art.shieldLines) drawPath(path, edge, alpha = 0.7f * alpha, style = ENGRAVING)
        }
        for ((path, width) in art.thick) tube(path, width, fill, edge, alpha)
        val rig = WIZARD_RIGS[piece.uppercaseChar()]
        if (rig != null && (art.weaponThick.isNotEmpty() || art.weaponFront.isNotEmpty())) {
            val pivot = Offset(rig.px, rig.py)
            // Dans le repère de la pièce retournée, les angles de l'écran changent de sens.
            withTransform({
                rotate(if (flip) -weaponDeg else weaponDeg, pivot)
                scale(weaponScale, weaponScale, pivot)
            }) {
                for ((path, width) in art.weaponThick) tube(path, width, fill, edge, alpha)
                for (path in art.weaponFront) solid(path, fill, edge, alpha)
            }
        }
        for (path in art.front) solid(path, fill, edge, alpha)

        // Les gravures fines et l'œil.
        for (path in art.lines) drawPath(path, lineColor, alpha = 0.75f * alpha, style = ENGRAVING)
        art.eye?.let { drawPath(it, if (white) WHITE_EDGE else Color(0xFFE8E8FF), alpha = alpha) }
    }
}

// ---------- Les dalles et le cadre ----------

/*
 * Le plateau, comme celui des photos : des dalles de marbre blanc givré et de marbre noir veiné de gris, dans un cadre
 * sombre (bordure d'argent tressée, frise de petits rectangles clairs, gemmes rouges, boule de verre à chaque angle).
 */

/** Les cases claires et sombres, vues de loin (la dalle les recouvre de son motif). */
internal val WIZARD_LIGHT_TILE = Color(0xFFE4EAF1)
internal val WIZARD_DARK_TILE = Color(0xFF151921)

private val FROST_VEIN = Color(0xFF7F93AB)
private val MARBLE_VEIN = Color(0xFFC3CAD9)

// Une veine : une ligne brisée qui traverse la dalle, avec une branche plus courte de temps en temps.
private fun DrawScope.drawVein(random: Random, color: Color, width: Float, alpha: Float) {
    var x = if (random.nextBoolean()) 0f else size.width * random.nextFloat()
    var y = if (x == 0f) size.height * random.nextFloat() else 0f
    var dx = size.width * (0.12f + 0.1f * random.nextFloat())
    var dy = size.height * (0.12f + 0.1f * random.nextFloat())
    repeat(6) { step ->
        val nx = (x + dx + (random.nextFloat() - 0.5f) * size.width * 0.18f).coerceIn(0f, size.width)
        val ny = (y + dy + (random.nextFloat() - 0.5f) * size.height * 0.18f).coerceIn(0f, size.height)
        drawLine(color, Offset(x, y), Offset(nx, ny), strokeWidth = width, alpha = alpha)
        if (step == 1 || step == 3) {
            val bx = (nx + (random.nextFloat() - 0.2f) * size.width * 0.3f).coerceIn(0f, size.width)
            val by = (ny - (random.nextFloat() - 0.2f) * size.height * 0.3f).coerceIn(0f, size.height)
            drawLine(color, Offset(nx, ny), Offset(bx, by), strokeWidth = width * 0.7f, alpha = alpha * 0.7f)
        }
        x = nx
        y = ny
    }
}

/**
 * Donne à la case numéro [square] son air de dalle de marbre : blanc givré ou noir selon [dark], veines, léger reflet,
 * joint. [highlight] : la couleur d'une case marquée (sélection, dernier coup, échec), posée en voile sur le marbre.
 */
internal fun DrawScope.drawWizardTile(square: Int, dark: Boolean, highlight: Color? = null) {
    val random = Random(square * 31 + 5)
    if (dark) {
        drawRect(Brush.linearGradient(listOf(Color(0xFF2A303C), Color(0xFF0C0E13)), Offset.Zero, Offset(size.width, size.height)))
        repeat(2) { drawVein(random, MARBLE_VEIN, 1.1f, 0.5f) }
        drawVein(random, MARBLE_VEIN, 0.7f, 0.3f)
    } else {
        drawRect(Brush.linearGradient(listOf(Color(0xFFF7FAFD), Color(0xFFC9D5E3)), Offset.Zero, Offset(size.width, size.height)))
        repeat(2) { drawVein(random, FROST_VEIN, 0.9f, 0.35f) }
        drawVein(random, Color.White, 1.6f, 0.7f)
    }
    if (highlight != null) drawRect(highlight, alpha = 0.6f)
    // Le reflet du verre, en haut à gauche, et le joint entre deux dalles.
    drawRect(Brush.linearGradient(listOf(Color(0x1FFFFFFF), Color(0x00FFFFFF)), Offset.Zero, Offset(size.width, size.height)))
    drawRect(Color(0x55000000), style = Stroke(width = 1f))
}

/**
 * Dessine le cadre du plateau (épaisseur [frame]) sur toute la surface : le plateau lui-même occupe l'intérieur.
 * De l'extérieur vers l'intérieur : un liseré d'argent, une bande d'argent tressée, une frise noire à petits rectangles
 * clairs ornée de gemmes rouges, puis un filet rouge ; une boule de verre sur son socle marque chaque angle.
 */
internal fun DrawScope.drawWizardFrame(frame: Float) {
    val w = size.width
    val h = size.height
    val silver = Color(0xFFB9BFCB)
    val corner = CornerRadius(frame * 0.35f)

    // Le corps du cadre.
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF242832), Color(0xFF08090C))),
        cornerRadius = corner
    )
    drawRoundRect(silver, topLeft = Offset(0.75f, 0.75f), size = Size(w - 1.5f, h - 1.5f), cornerRadius = corner, style = Stroke(1.5f), alpha = 0.8f)

    // La bande tressée : un fond d'argent sombre, puis de petits traits obliques.
    val bandInset = frame * 0.16f
    val bandWidth = frame * 0.24f
    drawRect(
        Color(0xFF5B6272),
        topLeft = Offset(bandInset + bandWidth / 2, bandInset + bandWidth / 2),
        size = Size(w - 2 * (bandInset + bandWidth / 2), h - 2 * (bandInset + bandWidth / 2)),
        style = Stroke(bandWidth)
    )
    val tick = bandWidth * 0.9f
    val step = bandWidth * 0.8f
    val bandStart = bandInset + bandWidth
    var t = bandStart
    while (t < w - bandStart) {
        for (y in listOf(bandInset, h - bandInset - bandWidth)) {
            drawLine(silver, Offset(t, y), Offset(t + tick * 0.5f, y + bandWidth), strokeWidth = 1.3f)
        }
        t += step
    }
    t = bandStart
    while (t < h - bandStart) {
        for (x in listOf(bandInset, w - bandInset - bandWidth)) {
            drawLine(silver, Offset(x, t), Offset(x + bandWidth, t + tick * 0.5f), strokeWidth = 1.3f)
        }
        t += step
    }

    // La frise : une bande noire marquée de petits rectangles clairs.
    val stripInset = frame * 0.5f
    val stripWidth = frame * 0.3f
    drawRect(
        Color(0xFF050608),
        topLeft = Offset(stripInset + stripWidth / 2, stripInset + stripWidth / 2),
        size = Size(w - 2 * (stripInset + stripWidth / 2), h - 2 * (stripInset + stripWidth / 2)),
        style = Stroke(stripWidth)
    )
    val dash = stripWidth * 1.1f
    val stripStart = frame * 0.9f
    t = stripStart
    while (t < w - stripStart) {
        for (y in listOf(stripInset + stripWidth * 0.2f, h - stripInset - stripWidth * 0.8f)) {
            drawRect(Color(0xFFDDE1E8), Offset(t, y), Size(dash, stripWidth * 0.6f), alpha = 0.9f)
        }
        t += dash * 2f
    }
    t = stripStart
    while (t < h - stripStart) {
        for (x in listOf(stripInset + stripWidth * 0.2f, w - stripInset - stripWidth * 0.8f)) {
            drawRect(Color(0xFFDDE1E8), Offset(x, t), Size(stripWidth * 0.6f, dash), alpha = 0.9f)
        }
        t += dash * 2f
    }

    // Le filet rouge, contre le plateau.
    drawRect(
        Color(0xFF9B1C1C),
        topLeft = Offset(frame - 1.5f, frame - 1.5f),
        size = Size(w - 2 * frame + 3f, h - 2 * frame + 3f),
        style = Stroke(1.5f)
    )

    // Les gemmes rouges : trois par côté, au milieu de la frise.
    val gemRadius = frame * 0.13f
    val mid = stripInset + stripWidth / 2
    fun gem(center: Offset) {
        drawCircle(Color(0xFF050608), gemRadius * 1.5f, center)
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFF8A80), Color(0xFFC62828), Color(0xFF6B0F0F)), center - Offset(gemRadius * 0.3f, gemRadius * 0.3f), gemRadius * 1.3f),
            gemRadius,
            center
        )
    }
    for (f in listOf(0.25f, 0.5f, 0.75f)) {
        gem(Offset(w * f, mid))
        gem(Offset(w * f, h - mid))
        gem(Offset(mid, h * f))
        gem(Offset(w - mid, h * f))
    }

    // Les angles : un socle sombre cerclé d'argent, et une boule de verre.
    val post = frame * 0.52f
    for (c in listOf(
        Offset(frame * 0.55f, frame * 0.55f), Offset(w - frame * 0.55f, frame * 0.55f),
        Offset(frame * 0.55f, h - frame * 0.55f), Offset(w - frame * 0.55f, h - frame * 0.55f)
    )) {
        drawCircle(Color(0xFF0A0B0E), post, c)
        drawCircle(silver, post, c, style = Stroke(1.6f), alpha = 0.9f)
        val dome = post * 0.78f
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFFFFFFF), Color(0xFFB4BCC9), Color(0xFF4A5160)),
                center = c - Offset(dome * 0.3f, dome * 0.35f),
                radius = dome * 1.5f
            ),
            dome,
            c
        )
    }
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

/*
 * Le déroulé d'une prise (avancement de 0 à 1) :
 *  - 0 → ATTACK_START : la pièce s'approche de sa victime (la tour et le fou restent à distance : ils tirent) ;
 *  - ATTACK_START → IMPACT : elle arme son geste (le coup part vite, juste avant l'impact) ;
 *  - IMPACT → FOLLOW_END : l'arme revient au repos, la victime est tranchée, écrasée, percée ou foudroyée ;
 *  - FOLLOW_END → STEP_END : la pièce prend la place de sa victime ; les débris, eux, retombent jusqu'à la fin.
 */
private const val ATTACK_START = 0.22f
private const val IMPACT = 0.46f
private const val FOLLOW_END = 0.62f
private const val STEP_END = 0.95f

/** Durée de l'animation d'un coup, en millisecondes : courte pour un déplacement, plus longue pour une prise. */
internal fun wizardMoveDuration(fx: MoveFx): Int = if (fx.isCapture) 2100 else 280

private fun lerpFloat(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun lerpOffset(a: Offset, b: Offset, t: Float): Offset = Offset(lerpFloat(a.x, b.x, t), lerpFloat(a.y, b.y, t))

private val RAD = PI.toFloat() / 180f

/** L'écart signé le plus court (de -180 à 180 degrés) pour aller de l'angle [a] à l'angle [b]. */
private fun angleDelta(a: Float, b: Float): Float {
    var d = (b - a) % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

private fun lerpAngle(a: Float, b: Float, t: Float): Float = a + angleDelta(a, b) * t

private fun rotateAround(pivot: Offset, point: Offset, degrees: Float): Offset {
    val dx = point.x - pivot.x
    val dy = point.y - pivot.y
    val c = cos(degrees * RAD)
    val s = sin(degrees * RAD)
    return Offset(pivot.x + dx * c - dy * s, pivot.y + dx * s + dy * c)
}

/**
 * L'angle (absolu, en degrés) et l'échelle d'une arme qui frappe : du repos [rest] elle se lève jusqu'à [windup]
 * (en grandissant jusqu'à [s1]), puis le coup part jusqu'à [strike] ([s2]), puis elle revient au repos.
 */
private fun swingPose(
    progress: Float, rest: Float, windup: Float, strike: Float, s1: Float, s2: Float
): Pair<Float, Float> {
    val a = ((progress - ATTACK_START) / (IMPACT - ATTACK_START)).coerceIn(0f, 1f)
    val b = ((progress - IMPACT) / (FOLLOW_END - IMPACT)).coerceIn(0f, 1f)
    return when {
        progress < ATTACK_START -> rest to 1f
        progress < IMPACT -> if (a < 0.6f) {
            val t = smooth(a / 0.6f)
            lerpAngle(rest, windup, t) to lerpFloat(1f, s1, t)
        } else {
            val t = (a - 0.6f) / 0.4f
            lerpAngle(windup, strike, t * t) to lerpFloat(s1, s2, t * t)
        }
        progress < FOLLOW_END -> {
            val t = smooth(b)
            lerpAngle(strike, rest, t) to lerpFloat(s2, 1f, t)
        }
        else -> rest to 1f
    }
}

/** L'angle d'une lame conjurée (pion, dame) : levée en [windup], puis balayée jusqu'à [strike] au moment du coup. */
private fun bladeAngle(progress: Float, windup: Float, strike: Float): Float {
    val a = ((progress - ATTACK_START) / (IMPACT - ATTACK_START)).coerceIn(0f, 1f)
    return when {
        progress < ATTACK_START -> windup
        progress < IMPACT -> if (a < 0.6f) windup else {
            val t = (a - 0.6f) / 0.4f
            lerpAngle(windup, strike, t * t)
        }
        else -> strike
    }
}

/**
 * Dessine, par-dessus l'échiquier (côté [size].width), le coup [fx] à l'avancement [progress] (de 0 à 1) : la pièce glisse
 * d'une case à l'autre ; si elle en prend une, chaque pièce frappe avec son arme, à sa façon (voir [drawCapture]).
 * [flipped] : les noirs sont en bas.
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
    if (fx.isCapture) {
        drawCapture(fx, progress, cell, from, to, origin(fx.capturedSquare))
        return
    }
    val slide = smooth(progress.coerceIn(0f, 1f))
    val position = Offset(from.x + (to.x - from.x) * slide, from.y + (to.y - from.y) * slide)
    val bounce = 1f + 0.1f * sin(PI.toFloat() * slide)
    val center = Offset(position.x + cell / 2f, position.y + cell / 2f)
    withTransform({ scale(bounce, bounce, pivot = center) }) {
        drawWizardPiece(fx.mover, position, cell)
    }
}

/**
 * Une prise. Chaque pièce se sert de son arme :
 *  - le pion fait surgir deux épées courtes qu'il croise sur sa victime, coupée en deux ;
 *  - la tour recule d'un cran et tire un carreau d'arbalète en plein cœur : la victime se fissure et vole en éclats ;
 *  - le cavalier bondit et abat sa masse d'armes : la victime est écrasée, puis brisée ;
 *  - le fou lève sa crosse, qui s'embrase : un éclair d'or frappe la victime, qui se dissout en lumière ;
 *  - la dame dégaine une longue lame et la balaie d'un grand revers : la victime est tranchée en biais ;
 *  - le roi lève son épée à deux mains et l'abat : la victime est fendue en deux, de haut en bas.
 * Puis la pièce rengaine et prend la place de sa victime.
 */
private fun DrawScope.drawCapture(fx: MoveFx, progress: Float, cell: Float, from: Offset, to: Offset, targetOrigin: Offset) {
    val kind = fx.mover.uppercaseChar()
    val half = Offset(cell / 2f, cell / 2f)
    val fromCenter = from + half
    val toCenter = to + half
    val targetCenter = targetOrigin + half
    val dx = targetCenter.x - fromCenter.x
    val dy = targetCenter.y - fromCenter.y
    val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
    val u = Offset(dx / distance, dy / distance)
    val angleU = atan2(u.y, u.x) / RAD
    // Du côté où l'arme part en arrière : on la lève par-dessus l'épaule, qu'on vise à gauche ou à droite.
    val side = if (u.x >= 0f) -1f else 1f
    val ranged = kind == 'R' || kind == 'B'
    val reach = when (kind) {
        'P' -> 0.8f
        'N' -> 0.85f
        'Q' -> 1.0f
        'K' -> 0.85f
        else -> 0f
    } * cell
    val stopCenter = if (ranged) fromCenter else targetCenter - u * minOf(reach, distance)

    val approach = smooth((progress / ATTACK_START).coerceIn(0f, 1f))
    val stepIn = smooth(((progress - FOLLOW_END) / (STEP_END - FOLLOW_END)).coerceIn(0f, 1f))
    val a = ((progress - ATTACK_START) / (IMPACT - ATTACK_START)).coerceIn(0f, 1f)
    val b = ((progress - IMPACT) / (FOLLOW_END - IMPACT)).coerceIn(0f, 1f)
    val after = ((progress - IMPACT) / (1f - IMPACT)).coerceIn(0f, 1f)

    // La pièce : elle s'approche (le cavalier bondit), frappe, puis prend la place de sa victime.
    val lift = (if (kind == 'N') 0.3f * sin(PI.toFloat() * approach) else 0f) * cell + 0.08f * cell * sin(PI.toFloat() * stepIn)
    val base = lerpOffset(fromCenter, stopCenter, approach)
    val moved = lerpOffset(base, toCenter, stepIn)
    val center = Offset(moved.x, moved.y - lift)
    val moverOrigin = center - half
    val bounce = 1f + 0.12f * sin(PI.toFloat() * stepIn)

    // Elle se tourne vers sa victime pour frapper, puis reprend son regard habituel en avançant.
    val faceRight: Boolean? = if (progress < FOLLOW_END && abs(dx) > cell * 0.15f) dx > 0f else null
    val flip = wizardFlip(fx.mover, faceRight)
    val unit = cell / 100f * 0.94f

    // L'arme de la pièce : son angle et sa taille.
    val rig = wizardRig(fx.mover)
    val restAngle = if (rig != null) atan2(rig.ry, if (flip) -rig.rx else rig.rx) / RAD else 0f
    var weaponDeg = 0f
    var weaponScale = 1f
    var swingAngle = restAngle
    val kingWind = angleU + side * 115f
    val kingStrike = angleU - side * 5f
    val knightWind = angleU + side * 150f
    val knightStrike = angleU - side * 10f
    when (kind) {
        'K' -> {
            val (angle, scale) = swingPose(progress, restAngle, kingWind, kingStrike, 1.5f, 1.9f)
            swingAngle = angle
            weaponDeg = angle - restAngle
            weaponScale = scale
        }
        'N' -> {
            val (angle, scale) = swingPose(progress, restAngle, knightWind, knightStrike, 1.5f, 1.9f)
            swingAngle = angle
            weaponDeg = angle - restAngle
            weaponScale = scale
        }
        'R' -> {
            // L'arbalète se braque sur la victime, puis recule au tir.
            val (angle, scale) = when {
                progress < ATTACK_START -> restAngle to 1f
                progress < IMPACT -> {
                    val t = smooth((a / 0.5f).coerceIn(0f, 1f))
                    lerpAngle(restAngle, angleU, t) to lerpFloat(1f, 1.15f, t)
                }
                progress < FOLLOW_END -> lerpAngle(angleU, restAngle, smooth(b)) to lerpFloat(0.85f, 1f, smooth(b))
                else -> restAngle to 1f
            }
            weaponDeg = angle - restAngle
            weaponScale = scale
        }
        'B' -> {
            // La crosse se penche vers la victime.
            val hold = when {
                progress < ATTACK_START -> 0f
                progress < IMPACT -> smooth((a / 0.4f).coerceIn(0f, 1f))
                progress < FOLLOW_END -> 1f - smooth(b)
                else -> 0f
            }
            weaponDeg = (if (u.x >= 0f) 1f else -1f) * 24f * hold
        }
    }

    fun drawMover() {
        withTransform({ scale(bounce, bounce, pivot = center) }) {
            drawWizardPiece(
                fx.mover, moverOrigin, cell,
                faceRight = faceRight, weaponDeg = weaponDeg, weaponScale = weaponScale
            )
        }
    }

    fun drawTarget() = drawCaptured(kind, fx.captured, targetOrigin, cell, progress, a, after, angleU, side)

    // Celui qui est le plus bas à l'écran passe devant.
    if (targetCenter.y > stopCenter.y + 1f) {
        drawMover()
        drawTarget()
    } else {
        drawTarget()
        drawMover()
    }

    // ----- Les armes et leurs effets, par-dessus les deux pièces -----
    val attacking = progress in ATTACK_START..FOLLOW_END
    if (!attacking) return
    // Le geste s'efface après l'impact.
    val fadeOut = if (progress < IMPACT) 1f else 1f - smooth(b)

    when (kind) {
        'P' -> {
            // Deux épées courtes surgissent des mains du pion et se croisent sur la victime.
            val grow = if (progress < IMPACT) smooth((a / 0.25f).coerceIn(0f, 1f)) else 1f - smooth(b)
            val length = cell * 0.62f
            val pivotA = wizardSpritePoint(moverOrigin, cell, flip, 38f, 56f)
            val pivotB = wizardSpritePoint(moverOrigin, cell, flip, 46f, 62f)
            val windA = angleU + side * 80f
            val endA = angleU - side * 30f
            val windB = angleU - side * 80f
            val endB = angleU + side * 30f
            val angleA = bladeAngle(progress, windA, endA)
            val angleB = bladeAngle(progress, windB, endB)
            drawConjureFlash(pivotA, cell, grow, progress)
            drawConjureFlash(pivotB, cell, grow, progress)
            if (a >= 0.6f || progress >= IMPACT) {
                drawSwingTrail(pivotA, length * 0.8f, angleA, windA, endA, fadeOut)
                drawSwingTrail(pivotB, length * 0.8f, angleB, windB, endB, fadeOut)
            }
            drawBlade(pivotA, angleA, length * grow, fadeOut, Color(0xFF8A94A8))
            drawBlade(pivotB, angleB, length * grow, fadeOut, Color(0xFF8A94A8))
        }
        'Q' -> {
            // La dame dégaine une longue lame, levée par-dessus l'épaule puis balayée d'un grand revers.
            val grow = if (progress < IMPACT) smooth((a / 0.3f).coerceIn(0f, 1f)) else 1f - smooth(b)
            val length = cell * 0.95f
            val pivot = wizardSpritePoint(moverOrigin, cell, flip, 62f, 56f)
            val wind = angleU + side * 105f
            val end = angleU - side * 75f
            val angle = bladeAngle(progress, wind, end)
            drawConjureFlash(pivot, cell, grow, progress)
            if (a >= 0.6f || progress >= IMPACT) drawSwingTrail(pivot, length * 0.75f, angle, wind, end, fadeOut)
            drawBlade(pivot, angle, length * grow, fadeOut, Color(0xFFD9B44A))
        }
        'K' -> {
            val pivot = wizardSpritePoint(moverOrigin, cell, flip, rig!!.px, rig.py)
            val radius = 28f * unit * weaponScale * 0.85f
            if (a >= 0.6f || progress >= IMPACT) {
                val head = if (progress < IMPACT) swingAngle else kingStrike
                drawSwingTrail(pivot, radius, head, kingWind, kingStrike, fadeOut)
            }
        }
        'N' -> {
            val pivot = wizardSpritePoint(moverOrigin, cell, flip, rig!!.px, rig.py)
            val radius = 24f * unit * weaponScale * 0.9f
            if (a >= 0.6f || progress >= IMPACT) {
                val head = if (progress < IMPACT) swingAngle else knightStrike
                drawSwingTrail(pivot, radius, head, knightWind, knightStrike, fadeOut)
            }
        }
        'R' -> {
            // Le carreau part de l'arbalète à la moitié du geste et file jusqu'à la victime.
            val pivot = wizardSpritePoint(moverOrigin, cell, flip, rig!!.px, rig.py)
            val aim = (restAngle + weaponDeg) * RAD
            val muzzle = pivot + Offset(cos(aim), sin(aim)) * (18f * unit * weaponScale)
            if (progress < IMPACT && a >= 0.5f) {
                val flash = (1f - abs(a - 0.55f) / 0.2f).coerceIn(0f, 1f)
                drawCircle(Color(0xFFFFE08A), radius = cell * 0.12f * flash, center = muzzle, alpha = flash)
            }
            if (progress < IMPACT && a >= 0.55f) {
                val t = ((a - 0.55f) / 0.45f).coerceIn(0f, 1f)
                val ex = targetCenter.x - muzzle.x
                val ey = targetCenter.y - muzzle.y
                val length = sqrt(ex * ex + ey * ey).coerceAtLeast(1f)
                val dir = Offset(ex / length, ey / length)
                val head = muzzle + Offset(ex, ey) * t
                val tail = head - dir * (cell * 0.34f)
                drawLine(Color(0x66FFFFFF), tail - dir * (cell * 0.2f), head, strokeWidth = cell * 0.09f, cap = StrokeCap.Round)
                drawLine(Color(0xFF7A5A32), tail, head, strokeWidth = cell * 0.045f, cap = StrokeCap.Round)
                drawLine(Color(0xFFDDE3EE), head - dir * (cell * 0.08f), head + dir * (cell * 0.06f), strokeWidth = cell * 0.07f, cap = StrokeCap.Round)
            }
        }
        'B' -> {
            // La crosse s'embrase, puis un éclair d'or file jusqu'à la victime.
            val pivot = wizardSpritePoint(moverOrigin, cell, false, rig!!.px, rig.py)
            val head = rotateAround(pivot, wizardSpritePoint(moverOrigin, cell, false, 76f, 13f), weaponDeg)
            val charge = if (progress < IMPACT) smooth((a / 0.55f).coerceIn(0f, 1f)) else 1f - smooth(b)
            val pulse = 0.85f + 0.15f * sin(progress * 90f)
            val radius = cell * (0.04f + 0.22f * charge) * pulse
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xCCFFE08A), Color(0x00FFE08A)), head, radius.coerceAtLeast(1f)),
                radius = radius.coerceAtLeast(1f),
                center = head
            )
            if (a >= 0.55f || progress >= IMPACT) drawLightning(head, targetCenter, cell, fadeOut, progress)
        }
    }
}

// Un éclat de lumière à l'endroit où une arme surgit de nulle part.
private fun DrawScope.drawConjureFlash(pivot: Offset, cell: Float, grow: Float, progress: Float) {
    if (grow <= 0f || grow >= 1f || progress >= IMPACT) return
    val radius = cell * 0.2f
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFFF3C0), Color(0x00FFF3C0)), pivot, radius),
        radius = radius,
        center = pivot,
        alpha = (1f - grow) * 0.9f
    )
}

/**
 * Une lame dessinée de la poignée ([pivot]) vers la pointe, dans la direction [angle] (degrés), sur la longueur [length] :
 * un pommeau, une poignée, une garde (de la couleur [guard]) et une lame d'acier à pointe.
 */
private fun DrawScope.drawBlade(pivot: Offset, angle: Float, length: Float, alpha: Float, guard: Color) {
    if (length < 2f || alpha <= 0f) return
    val w = length * 0.07f
    withTransform({
        translate(pivot.x, pivot.y)
        rotate(angle, Offset.Zero)
    }) {
        drawRect(Color(0xFF3A3F4B), Offset(-length * 0.16f, -w * 0.35f), Size(length * 0.16f, w * 0.7f), alpha = alpha)
        drawCircle(guard, radius = w * 0.55f, center = Offset(-length * 0.17f, 0f), alpha = alpha)
        drawRect(guard, Offset(-w * 0.25f, -w * 1.7f), Size(w * 0.5f, w * 3.4f), alpha = alpha)
        val blade = Path().apply {
            moveTo(0f, -w)
            lineTo(length * 0.84f, -w * 0.85f)
            lineTo(length, 0f)
            lineTo(length * 0.84f, w * 0.85f)
            lineTo(0f, w)
            close()
        }
        drawPath(
            blade,
            Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFF9AA3B5)), startY = -w, endY = w),
            alpha = alpha
        )
        drawPath(blade, Color(0xFF2E323D), alpha = alpha, style = Stroke(width = 1.3f, join = StrokeJoin.Round))
        drawLine(Color.White, Offset(w, 0f), Offset(length * 0.9f, 0f), strokeWidth = 1.2f, alpha = alpha * 0.7f)
    }
}

/**
 * La traînée lumineuse d'une arme qui balaie l'air autour de [pivot] : un arc de rayon [radius], qui finit à l'angle
 * [head] (degrés) et remonte vers l'angle de départ [wind] ; [end] donne le sens du balayage.
 */
private fun DrawScope.drawSwingTrail(pivot: Offset, radius: Float, head: Float, wind: Float, end: Float, alpha: Float) {
    if (alpha <= 0f) return
    val direction = if (angleDelta(wind, end) >= 0f) 1f else -1f
    val span = abs(angleDelta(wind, head)).coerceIn(0f, 110f)
    if (span < 3f) return
    val start = if (direction > 0f) head - span else head
    val topLeft = Offset(pivot.x - radius, pivot.y - radius)
    val size = Size(radius * 2f, radius * 2f)
    drawArc(Color(0xFFBFD6FF), start, span, false, topLeft, size, alpha = alpha * 0.25f, style = Stroke(radius * 0.5f, cap = StrokeCap.Round))
    drawArc(Color(0xFFE8F0FF), start, span, false, topLeft, size, alpha = alpha * 0.55f, style = Stroke(radius * 0.22f, cap = StrokeCap.Round))
    drawArc(Color.White, start, span, false, topLeft, size, alpha = alpha * 0.9f, style = Stroke(radius * 0.07f, cap = StrokeCap.Round))
}

/** L'éclair d'or du fou : une ligne brisée, qui vacille, de la crosse à la victime. */
private fun DrawScope.drawLightning(start: Offset, end: Offset, cell: Float, alpha: Float, progress: Float) {
    if (alpha <= 0f) return
    val seed = floor(progress * 45f)
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
    val nx = -dy / length
    val ny = dx / length
    val path = Path().apply {
        moveTo(start.x, start.y)
        for (i in 1..7) {
            val t = i / 7f
            val jitter = if (i == 7) 0f else sin(i * 12.9898f + seed * 78.233f) * cell * 0.07f
            lineTo(start.x + dx * t + nx * jitter, start.y + dy * t + ny * jitter)
        }
    }
    drawPath(path, Color(0xFFFFD25A), alpha = alpha * 0.3f, style = Stroke(cell * 0.18f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, Color(0xFFFFE08A), alpha = alpha * 0.8f, style = Stroke(cell * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, Color.White, alpha = alpha, style = Stroke(cell * 0.025f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * La pièce prise : intacte jusqu'à l'impact (le fou la fait rougeoyer d'or avant l'éclair), puis elle subit le coup de
 * celui qui la prend : tranchée (pion, dame, roi), écrasée (cavalier), percée (tour) ou dissoute en lumière (fou).
 */
private fun DrawScope.drawCaptured(
    kind: Char, piece: Char, origin: Offset, cell: Float, progress: Float, a: Float, after: Float, angleU: Float, side: Float
) {
    if (progress < IMPACT) {
        drawWizardPiece(piece, origin, cell)
        if (kind == 'B' && a > 0.55f) {
            val center = Offset(origin.x + cell / 2f, origin.y + cell / 2f)
            val glow = smooth(((a - 0.55f) / 0.45f).coerceIn(0f, 1f))
            drawCircle(
                Brush.radialGradient(listOf(Color(0xCCFFF2B0), Color(0x00FFF2B0)), center, cell * 0.6f),
                radius = cell * 0.6f,
                center = center,
                alpha = glow
            )
        }
        return
    }
    when (kind) {
        'P' -> drawSliced(piece, origin, cell, after, angleU + 90f)
        'Q' -> drawSliced(piece, origin, cell, after, angleU + 90f + side * 35f)
        'K' -> drawSliced(piece, origin, cell, after, angleU)
        'N' -> drawCrushed(piece, origin, cell, after)
        'R' -> drawPierced(piece, origin, cell, after)
        else -> drawShatter(piece, origin, cell, after, golden = true)
    }
}

// Tranchée : la pièce est coupée en deux le long d'une ligne (angle [cutDeg]) ; les deux moitiés s'écartent, basculent et tombent.
private fun DrawScope.drawSliced(piece: Char, origin: Offset, cell: Float, after: Float, cutDeg: Float) {
    val center = Offset(origin.x + cell / 2f, origin.y + cell / 2f)
    val d = Offset(cos(cutDeg * RAD), sin(cutDeg * RAD))
    val n = Offset(-d.y, d.x)
    val ease = 1f - (1f - after) * (1f - after)
    val fade = 1f - smooth(((after - 0.4f) / 0.6f).coerceIn(0f, 1f))
    val big = cell * 2f
    for (sign in listOf(1f, -1f)) {
        val halfPlane = Path().apply {
            moveTo(center.x - d.x * big, center.y - d.y * big)
            lineTo(center.x + d.x * big, center.y + d.y * big)
            lineTo(center.x + d.x * big + n.x * sign * big, center.y + d.y * big + n.y * sign * big)
            lineTo(center.x - d.x * big + n.x * sign * big, center.y - d.y * big + n.y * sign * big)
            close()
        }
        val shift = (0.04f + 0.5f * ease) * cell * sign
        withTransform({
            translate(n.x * shift, n.y * shift + 0.8f * after * after * cell)
            rotate(sign * 28f * after, center)
        }) {
            clipPath(halfPlane) { drawWizardPiece(piece, origin, cell, alpha = fade, shadow = false) }
        }
    }
    // Le fil de la lame : un trait de lumière sur la coupe, et un éclair à l'impact.
    val glow = (1f - after * 3f).coerceIn(0f, 1f)
    if (glow > 0f) {
        drawLine(Color(0xFFFFE08A), center - d * (cell * 0.8f), center + d * (cell * 0.8f), strokeWidth = 8f, cap = StrokeCap.Round, alpha = glow * 0.5f)
        drawLine(Color.White, center - d * (cell * 0.8f), center + d * (cell * 0.8f), strokeWidth = 3f, cap = StrokeCap.Round, alpha = glow)
        drawCircle(
            Brush.radialGradient(listOf(Color(0xCCFFFFFF), Color(0x00FFFFFF)), center, cell * 0.5f),
            radius = cell * 0.5f,
            center = center,
            alpha = glow
        )
    }
}

// Écrasée : la pièce s'aplatit sous la masse, une onde de choc court au sol, puis elle vole en éclats.
private fun DrawScope.drawCrushed(piece: Char, origin: Offset, cell: Float, after: Float) {
    val foot = Offset(origin.x + cell / 2f, origin.y + cell * 0.93f)
    val squash = 0.2f
    if (after < squash) {
        val t = smooth(after / squash)
        withTransform({ scale(1f + 0.18f * t, 1f - 0.5f * t, pivot = foot) }) {
            drawWizardPiece(piece, origin, cell)
        }
    } else {
        drawShatter(piece, origin, cell, (after - squash) / (1f - squash))
    }
    val radius = cell * (0.25f + 0.9f * after)
    drawOval(
        Color(0xFFFFF0C0),
        topLeft = Offset(foot.x - radius, foot.y - radius * 0.35f),
        size = Size(radius * 2f, radius * 0.7f),
        alpha = (1f - after) * 0.8f,
        style = Stroke(width = 3f)
    )
}

// Percée : la pièce tressaille, des fissures partent du point d'impact, puis elle vole en éclats.
private fun DrawScope.drawPierced(piece: Char, origin: Offset, cell: Float, after: Float) {
    val crack = 0.14f
    if (after < crack) {
        val center = Offset(origin.x + cell / 2f, origin.y + cell / 2f)
        withTransform({ translate(sin(after * 140f) * 0.03f * cell, 0f) }) {
            drawWizardPiece(piece, origin, cell)
        }
        val color = if (Chess.isWhite(piece)) Color(0xFF2A2D38) else Color(0xFFE6E9F5)
        val length = cell * 0.45f * smooth(after / crack)
        repeat(6) { i ->
            val angle = (i * 60f + 20f) * RAD
            drawLine(color, center, center + Offset(cos(angle), sin(angle)) * length, strokeWidth = 2.5f, cap = StrokeCap.Round)
        }
    } else {
        drawShatter(piece, origin, cell, (after - crack) / (1f - crack))
    }
}

// La pièce prise éclate : éclair, éclats qui volent et tombent en s'effaçant, poussière, étincelles.
private fun DrawScope.drawShatter(piece: Char, origin: Offset, cell: Float, after: Float, golden: Boolean = false) {
    val center = Offset(origin.x + cell / 2f, origin.y + cell / 2f)
    val flash = (1f - after) * (1f - after)
    val flashRadius = cell * (0.35f + 0.75f * after)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(if (golden) Color(0xEEFFE9A0) else Color(0xCCFFF6C8), Color(0x00FFF6C8)),
            center = center,
            radius = flashRadius
        ),
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
                clipPath(triangle) { drawWizardPiece(piece, origin, cell, alpha = fade, shadow = false) }
            }
        }
    }

    // Des grains de lumière qui montent (la pièce touchée par la magie).
    if (golden) {
        repeat(14) { i ->
            val px = center.x + (((i * 37) % 100) - 50) / 100f * cell * 0.9f
            val py = center.y + (((i * 53) % 100) - 50) / 100f * cell * 0.6f - ease * cell * (0.5f + 0.06f * (i % 5))
            drawCircle(
                color = Color(0xFFFFE9A6),
                radius = cell * (0.03f + 0.02f * (i % 3)),
                center = Offset(px, py),
                alpha = (1f - after).coerceIn(0f, 1f)
            )
        }
    }

    // De la poussière de pierre qui s'étend et se dissipe.
    val dust = if (Chess.isWhite(piece)) Color(0xFFD8D8DE) else Color(0xFF3A3D48)
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
