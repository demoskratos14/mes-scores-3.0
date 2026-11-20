package com.aventure.messcores

import android.content.Context
import android.os.Build
import androidx.core.content.edit

/**
 * Le nom qu'on porte quand on joue chacun sur son téléphone (salle multijoueur, partage d'une partie, jeux à deux téléphones).
 * Il est gardé entre deux lancements : inutile de le retaper à chaque partie.
 */
object MultiplayerName {
    private const val PREFS = "mes_scores_setup"
    private const val KEY_NAME = "multiplayer_name"

    /** Longueur maximale d'un nom. */
    const val MAX_LENGTH = 24

    /** Le nom enregistré, ou null si on n'en a jamais saisi. */
    fun load(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAME, null)?.takeIf { it.isNotBlank() }

    /** Enregistre [name] (espaces retirés autour) ; un nom vide efface l'enregistrement. */
    fun save(context: Context, name: String) {
        val cleaned = name.trim().take(MAX_LENGTH)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            if (cleaned.isEmpty()) remove(KEY_NAME) else putString(KEY_NAME, cleaned)
        }
    }

    /** Le nom proposé tant qu'on n'en a pas saisi : le modèle du téléphone. */
    fun fallback(): String = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"

    /** Le nom à utiliser : celui qu'on a enregistré, sinon celui du téléphone. */
    fun current(context: Context): String = load(context) ?: fallback()
}
