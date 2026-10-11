package com.aventure.messcores

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Textes composés à partir de plusieurs ressources, pour du code qui n'est pas @Composable
 * (callbacks, fonctions pures). Les textes eux-mêmes sont dans res/values/strings.xml.
 */

/** Message affiché sous les champs de noms quand des doublons vont être numérotés, ou null. */
fun duplicateNoticeText(context: Context, raw: List<String>, fallbackPrefix: String): String? {
    val changes = PlayerNames.duplicateChanges(raw, fallbackPrefix)
    if (changes.isEmpty()) return null
    val list = changes.joinToString(", ") { (before, after) ->
        context.getString(R.string.duplicate_change, before, after)
    }
    return context.getString(R.string.duplicate_notice, list)
}

/** Texte au pluriel (« 1 partie », « 3 parties »…) avec [count] comme seul argument. */
fun Context.quantity(pluralsId: Int, count: Int): String = resources.getQuantityString(pluralsId, count, count)

/**
 * Libellé affiché d'un coefficient. « Normal » est enregistré avec les jeux (identifiant « normal »)
 * mais traduit à l'affichage ; les autres noms (Garde, Coinché, noms saisis par l'utilisateur) sont affichés tels quels.
 */
@Composable
fun ScoreMultiplier.displayLabel(): String =
    if (id == GameRules.NORMAL_MULTIPLIER_ID) stringResource(R.string.multiplier_normal) else label

/** Même chose pour du code qui n'est pas @Composable. */
fun ScoreMultiplier.displayLabel(context: Context): String =
    if (id == GameRules.NORMAL_MULTIPLIER_ID) context.getString(R.string.multiplier_normal) else label
