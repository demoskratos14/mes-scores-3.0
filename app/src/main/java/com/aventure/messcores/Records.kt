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
