package com.aventure.messcores

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.edit
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.runtime.withFrameNanos
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** La règle des records des jeux qu'on joue seul : plus le score est haut, mieux c'est, et 0 n'est pas un record. */
internal object RecordRules {
    /** Vrai si [score] bat le [previous] record (le premier score de la table, s'il est supérieur à 0, est un record). */
    fun isNewRecord(previous: Int, score: Int): Boolean = score > 0 && score > previous
}

/**
 * Le record du jeu de rythme : un par morceau **et** par difficulté (le même morceau en facile et en difficile n'a pas le même
 * score possible). Le morceau est reconnu par le nom de son fichier et sa taille, qui ne changent pas d'un choix à l'autre
 * (l'adresse du fichier, elle, peut changer).
 */
internal object RhythmRecords {
    /** L'identité d'un morceau : le nom du fichier sans extension, et sa taille en octets quand le téléphone la donne (0 : inconnue). */
    fun songId(name: String, sizeBytes: Long): String = if (sizeBytes > 0) "$name|$sizeBytes" else name

    /** La clé du record du morceau [songId] à la difficulté [difficultyIndex] (voir [GameRecords]). */
    fun key(songId: String?, difficultyIndex: Int): String = "$RHYTHM_ID|${songId.orEmpty()}|$difficultyIndex"
}

/** Ce que donne l'enregistrement d'un score : le record après coup et si ce score vient de le battre. */
internal data class RecordResult(val record: Int, val isNew: Boolean)

/**
 * Les records des jeux jouables seul (Ronds, Simon…), gardés d'une séance à l'autre dans les SharedPreferences, un par
 * identifiant de jeu. Seules les parties à un joueur comptent (voir [rememberSoloRecord]).
 */
internal object GameRecords {
    private const val PREFS = "mes_scores_records"

    /** Le record du jeu [gameId] ; 0 s'il n'y en a pas encore. */
    fun load(context: Context, gameId: String): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(gameId, 0).coerceAtLeast(0)

    /** Retient [score] s'il bat le record du jeu [gameId], puis renvoie le record qui vaut maintenant. */
    fun submit(context: Context, gameId: String, score: Int): RecordResult {
        val previous = load(context, gameId)
        if (!RecordRules.isNewRecord(previous, score)) return RecordResult(previous, false)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putInt(gameId, score) }
        return RecordResult(score, true)
    }
}

/** Le record du jeu en cours, tenu à jour pendant la séance : [best] (0 : aucun), [isNew] quand la dernière série vient de le battre. */
internal class SoloRecord(
    val best: Int,
    val isNew: Boolean,
    private val onSubmit: (Int) -> Unit,
    private val onClearNew: () -> Unit
) {
    /** Enregistre le score d'une partie finie (à appeler seulement quand on joue seul). */
    fun submit(score: Int) = onSubmit(score)

    /** Efface l'annonce « nouveau record » (nouvelle partie). */
    fun clearNew() = onClearNew()
}

/** Le record du jeu [gameId] pour un joueur seul : relu au départ, mis à jour par [SoloRecord.submit], « nouveau » gardé quand l'écran tourne. */
@Composable
internal fun rememberSoloRecord(gameId: String): SoloRecord {
    val context = LocalContext.current.applicationContext
    var best by remember(gameId) { mutableIntStateOf(GameRecords.load(context, gameId)) }
    var isNew by rememberSaveable(gameId) { mutableStateOf(false) }
    return SoloRecord(
        best = best,
        isNew = isNew,
        onSubmit = { score ->
            val result = GameRecords.submit(context, gameId, score)
            best = result.record
            isNew = result.isNew
        },
        onClearNew = { isNew = false }
    )
}

/** La ligne « Record : N » (ou « Nouveau record : N ! », en gras) ; rien tant qu'il n'y a pas de record. */
@Composable
internal fun SoloRecordLine(record: SoloRecord) {
    if (record.best <= 0) return
    Text(
        text = stringResource(if (record.isNew) R.string.record_new else R.string.record_best, record.best),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = if (record.isNew) FontWeight.Bold else FontWeight.Normal,
        color = if (record.isNew) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

/** Une paillette qui tombe : où, à quelle vitesse, après quel délai, de quelle taille et de quelle couleur. */
private class Sparkle(
    /** Position de départ en part de la largeur, et balancement de côté (part de la largeur, vitesse, décalage). */
    val x: Float,
    val sway: Float,
    val swayRate: Float,
    val phase: Float,
    /** Délai avant d'apparaître (secondes) et vitesse de chute (part de la hauteur par seconde). */
    val delay: Float,
    val fall: Float,
    /** Taille en dp, rotation en degrés par seconde et vitesse de scintillement. */
    val size: Float,
    val spin: Float,
    val twinkleRate: Float,
    val color: Color,
    /** Un petit point rond plutôt qu'une étoile. */
    val dot: Boolean
)

private val SparkleColors = listOf(
    Color(0xFFFFC107), Color(0xFFFFD54F), Color(0xFFFF7043), Color(0xFFEC407A),
    Color(0xFFAB47BC), Color(0xFF42A5F5), Color(0xFF26C6DA), Color(0xFF66BB6A)
)

/** Nombre de paillettes et durée totale de la pluie, en secondes. */
private const val SPARKLE_COUNT = 110
private const val SPARKLE_SECONDS = 5f

/** Le temps pendant lequel la pluie s'estompe à la fin, en secondes. */
private const val SPARKLE_FADE_SECONDS = 0.6f

private fun makeSparkles(random: Random): List<Sparkle> = List(SPARKLE_COUNT) {
    Sparkle(
        x = random.nextFloat(),
        sway = 0.01f + random.nextFloat() * 0.03f,
        swayRate = 1.5f + random.nextFloat() * 2.5f,
        phase = random.nextFloat() * (2f * PI.toFloat()),
        delay = random.nextFloat() * 1.8f,
        fall = 0.35f + random.nextFloat() * 0.35f,
        size = 4f + random.nextFloat() * 8f,
        spin = (random.nextFloat() - 0.5f) * 360f,
        twinkleRate = 4f + random.nextFloat() * 6f,
        color = SparkleColors[random.nextInt(SparkleColors.size)],
        dot = random.nextInt(4) == 0
    )
}

/** Une étoile à quatre branches de rayon 1, centrée sur l'origine : mise à l'échelle et déplacée à chaque paillette. */
private fun unitStar(): Path = Path().apply {
    for (i in 0 until 8) {
        val radius = if (i % 2 == 0) 1f else 0.22f
        val angle = (PI / 4.0 * i - PI / 2.0).toFloat()
        val px = cos(angle) * radius
        val py = sin(angle) * radius
        if (i == 0) moveTo(px, py) else lineTo(px, py)
    }
    close()
}

/**
 * La pluie de paillettes qui fête un nouveau record : elle se joue une fois, dès que [record] annonce un nouveau record, par-dessus
 * l'écran du jeu (à poser dans un conteneur qui remplit l'écran). Elle ne prend aucun toucher, ne rejoue pas quand l'écran tourne,
 * et reste éteinte quand l'utilisateur a coupé les animations du téléphone.
 */
@Composable
internal fun RecordSparkles(record: SoloRecord) {
    val context = LocalContext.current
    // Le record pour lequel la pluie a déjà été jouée (-1 : aucune) : gardé quand l'écran tourne, pour ne pas la rejouer.
    var playedFor by rememberSaveable { mutableIntStateOf(-1) }
    // Temps écoulé depuis le début de la pluie en secondes ; négatif quand elle ne joue pas.
    var time by remember { mutableFloatStateOf(-1f) }
    var sparkles by remember { mutableStateOf(emptyList<Sparkle>()) }
    val star = remember { unitStar() }

    LaunchedEffect(record.isNew, record.best) {
        if (!record.isNew) {
            playedFor = -1
            time = -1f
            return@LaunchedEffect
        }
        if (playedFor == record.best) return@LaunchedEffect
        playedFor = record.best
        val animationScale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        if (animationScale == 0f) return@LaunchedEffect
        sparkles = makeSparkles(Random.Default)
        val start = withFrameNanos { it }
        var elapsed = 0f
        while (elapsed < SPARKLE_SECONDS) {
            time = elapsed
            elapsed = (withFrameNanos { it } - start) / 1_000_000_000f
        }
        time = -1f
    }

    if (time < 0f) return
    Canvas(modifier = Modifier.fillMaxSize()) { drawSparkles(sparkles, time, star) }
}

/** Dessine les paillettes à l'instant [time] (en secondes depuis le début de la pluie). */
private fun DrawScope.drawSparkles(sparkles: List<Sparkle>, time: Float, star: Path) {
    val fadeOut = ((SPARKLE_SECONDS - time) / SPARKLE_FADE_SECONDS).coerceIn(0f, 1f)
    sparkles.forEach { p ->
        val t = time - p.delay
        if (t < 0f) return@forEach
        val y = (-0.04f + t * p.fall) * size.height
        if (y > size.height + 24f * density) return@forEach
        val x = (p.x + sin(t * p.swayRate + p.phase) * p.sway) * size.width
        val twinkle = 0.55f + 0.45f * sin(t * p.twinkleRate + p.phase)
        val color = p.color.copy(alpha = (twinkle * fadeOut).coerceIn(0f, 1f))
        val s = p.size * density
        if (p.dot) {
            drawCircle(color, radius = s * 0.35f, center = Offset(x, y))
        } else {
            withTransform({
                translate(x, y)
                rotate(p.spin * t, Offset.Zero)
                scale(s, s, Offset.Zero)
            }) {
                drawPath(star, color)
            }
        }
    }
}

/**
 * La pluie de paillettes jouée une fois à chaque fois que [playCount] augmente (par exemple à chaque plateau rempli au Snake),
 * par-dessus l'écran du jeu. Comme [RecordSparkles], elle ne prend aucun toucher, ne rejoue pas quand l'écran tourne et reste
 * éteinte quand les animations du téléphone sont coupées. La valeur de [playCount] à l'arrivée de l'écran ne la déclenche pas.
 */
@Composable
internal fun SparkleRain(playCount: Int) {
    val context = LocalContext.current
    var playedFor by rememberSaveable { mutableIntStateOf(playCount) }
    var time by remember { mutableFloatStateOf(-1f) }
    var sparkles by remember { mutableStateOf(emptyList<Sparkle>()) }
    val star = remember { unitStar() }

    LaunchedEffect(playCount) {
        // Le compteur qui retombe (nouvelle partie) ne déclenche rien : seule une hausse joue la pluie.
        if (playCount <= playedFor) {
            playedFor = playCount
            return@LaunchedEffect
        }
        playedFor = playCount
        val animationScale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        if (animationScale == 0f) return@LaunchedEffect
        sparkles = makeSparkles(Random.Default)
        val start = withFrameNanos { it }
        var elapsed = 0f
        while (elapsed < SPARKLE_SECONDS) {
            time = elapsed
            elapsed = (withFrameNanos { it } - start) / 1_000_000_000f
        }
        time = -1f
    }

    if (time < 0f) return
    Canvas(modifier = Modifier.fillMaxSize()) { drawSparkles(sparkles, time, star) }
}
