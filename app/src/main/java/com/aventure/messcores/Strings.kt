package com.aventure.messcores

import android.content.Context

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
