package com.aventure.messcores

import androidx.annotation.StringRes
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.core.content.edit

/** Choix d'apparence : suivre le réglage du téléphone, ou forcer le clair / le sombre. */
enum class ThemeMode(@StringRes val labelRes: Int) {
    SYSTEM(R.string.theme_auto),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark)
}

/** Mémorise le [ThemeMode] choisi, pour le retrouver au prochain lancement. */
object ThemePreference {
    private const val PREFS = "mes_scores_setup"
    private const val KEY_THEME_MODE = "theme_mode"

    fun load(context: Context): ThemeMode {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_THEME_MODE, null)
        return ThemeMode.values().firstOrNull { it.name == saved } ?: ThemeMode.SYSTEM
    }

    fun save(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_THEME_MODE, mode.name) }
    }
}

/** Thème de l'appli : palette Material claire ou sombre selon [mode] (et le réglage du téléphone en mode Auto). */
@Composable
fun MesScoresTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
        content = content
    )
}

/** Vrai si la palette active est la palette sombre. */
@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * Fond des cartes posées sur la photo : légèrement transparent pour rester lisible sans cacher
 * complètement l'image. Suit le thème (blanc en clair, gris foncé en sombre).
 */
@Composable
fun cardSurface(): Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f)

/** Bandeau jaune (partie terminée, podium) et la couleur de son texte, lisibles dans les deux thèmes. */
@Composable
fun highlightContainer(): Color = if (isDarkTheme()) Color(0xFF5C5200) else Color(0xFFFFF59D)

@Composable
fun highlightContent(): Color = if (isDarkTheme()) Color(0xFFFFF59D) else Color(0xFF1C1B1F)

/** Fond d'un vainqueur (ligne d'un match, case gagnante). */
@Composable
fun winnerContainer(): Color = if (isDarkTheme()) Color(0xFF2E5E33) else Color(0xFFC8E6C9)

/** Fonds de l'aperçu « contrat réussi / chuté » du Tarot. */
@Composable
fun successContainer(): Color = if (isDarkTheme()) Color(0xFF1F4D2B) else Color(0xFFE8F5E9)

@Composable
fun failureContainer(): Color = if (isDarkTheme()) Color(0xFF5C2A2A) else Color(0xFFFFEBEE)

/**
 * Couleur d'un joueur utilisée comme couleur de texte : éclaircie en thème sombre, sinon les teintes
 * foncées de la palette (marron, indigo, violet…) deviennent illisibles sur le fond des cartes.
 */
@Composable
fun playerTextColor(color: Color): Color = if (isDarkTheme()) lerp(color, Color.White, 0.5f) else color
