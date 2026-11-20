package com.aventure.messcores

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Le « mode TV » d'un jeu : pensé pour quand on caste l'écran du téléphone sur une télé (Chromecast…) et qu'on joue à la
 * manette. Le jeu passe en paysage, cache les barres du système, n'affiche plus les commandes tactiles et agrandit son
 * plateau pour remplir tout l'écran.
 *
 * Pour l'ajouter à un autre jeu :
 *  1. lire le réglage avec [TvMode.load] (avec l'identifiant du jeu) et le garder dans un état ;
 *  2. appeler [TvModeEffect] tant que le mode est actif ;
 *  3. afficher un [TvModeToggle] sur l'écran d'accueil du jeu, qui enregistre le choix avec [TvMode.save] ;
 *  4. dans la partie, remplacer la mise en page par une version plein écran, sans boutons tactiles.
 */
internal object TvMode {
    private const val PREFS = "tv_mode"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Vrai si le mode TV est choisi pour le jeu [gameId] (désactivé tant qu'on ne l'a pas allumé). */
    fun load(context: Context, gameId: String): Boolean = prefs(context).getBoolean(gameId, false)

    fun save(context: Context, gameId: String, enabled: Boolean) {
        prefs(context).edit { putBoolean(gameId, enabled) }
    }
}

/**
 * Tant que cet élément est affiché : l'écran est forcé en paysage et les barres du système (état, navigation) sont
 * cachées ; elles reviennent d'un balayage depuis le bord. L'orientation et les barres sont rendues à la sortie.
 */
@Composable
internal fun TvModeEffect() {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(context, view) {
        val activity = findTvActivity(context)
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (previous != null) activity?.requestedOrientation = previous
        }
    }
}

/** La ligne « Mode TV » avec son interrupteur, à mettre sur l'écran d'accueil d'un jeu. */
@Composable
internal fun TvModeToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.tv_mode_title), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(R.string.tv_mode_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

private fun findTvActivity(context: Context): Activity? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
