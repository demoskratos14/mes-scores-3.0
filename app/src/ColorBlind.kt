package com.aventure.messcores

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.core.content.edit

/** Vrai quand le mode daltonien est actif : un petit code (A, B, C…) s'affiche sur chaque couleur. */
val LocalColorBlindMode = compositionLocalOf { false }

/** Mémorise le mode daltonien, pour le retrouver au prochain lancement. */
object ColorBlindPreference {
    private const val PREFS = "mes_scores_setup"
    private const val KEY_COLOR_BLIND = "color_blind_mode"

    fun load(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_COLOR_BLIND, false)

    fun save(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_COLOR_BLIND, enabled) }
    }
}
