package com.aventure.messcores

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 *  4. dans la partie, remplacer la mise en page par une version plein écran, sans boutons tactiles : une bande de score
 *     [TvHud] à la place de l'en-tête, le terrain qui prend tout le reste, et un rappel [TvStartHint] / [TvResumeHint] ;
 *  5. ajouter l'identifiant du jeu à `TV_GAME_IDS` (TvHomeScreen.kt) pour l'accueil de l'appli pour télé.
 */
internal object TvMode {
    private const val PREFS = "tv_mode"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Vrai si l'appli tourne sur une télé (Google TV, Android TV) et non sur un téléphone. */
    fun isTelevision(context: Context): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION

    /**
     * Vrai si le mode TV est choisi pour le jeu [gameId]. Tant qu'on n'a pas touché à l'interrupteur, il est allumé sur
     * une télé (où il n'y a ni écran tactile ni intérêt pour les flèches à l'écran) et éteint sur un téléphone.
     * Dans l'appli pour télé (variante « tv »), il est toujours allumé.
     */
    fun load(context: Context, gameId: String): Boolean =
        BuildConfig.IS_TV || prefs(context).getBoolean(gameId, isTelevision(context))

    fun save(context: Context, gameId: String, enabled: Boolean) {
        prefs(context).edit { putBoolean(gameId, enabled) }
    }
}

/**
 * Tant que cet élément est affiché : l'écran est forcé en paysage (sauf avec [forceLandscape] à faux, pour un jeu qui s'y
 * met déjà tout seul) et les barres du système (état, navigation) sont cachées ; elles reviennent d'un balayage depuis le
 * bord. L'orientation et les barres sont rendues à la sortie.
 */
@Composable
internal fun TvModeEffect(forceLandscape: Boolean = true) {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(context, view, forceLandscape) {
        val activity = findTvActivity(context)
        val previous = if (forceLandscape) activity?.requestedOrientation else null
        if (forceLandscape) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        // La touche Select (« − ») de la manette revient en arrière, comme le bouton Retour de la télécommande : on n'a ainsi
        // plus besoin de la télécommande pour quitter un jeu (voir [GamepadHub]).
        val goBack: () -> Unit = { (activity as? ComponentActivity)?.onBackPressedDispatcher?.onBackPressed() }
        GamepadHub.registerBack(goBack)
        onDispose {
            GamepadHub.unregisterBack(goBack)
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (previous != null) activity?.requestedOrientation = previous
        }
    }
}

/** La ligne « Mode TV » avec son interrupteur, à mettre sur l'écran d'accueil d'un jeu (absente de l'appli pour télé). */
@Composable
internal fun TvModeToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    // Dans l'appli pour télé, le mode est toujours allumé : pas d'interrupteur.
    if (BuildConfig.IS_TV) return
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

/** Hauteur (en dp) de la bande de score des jeux en mode TV : le terrain prend tout le reste de l'écran. */
internal const val TV_HUD_DP = 36

/** Le fond des jeux en mode TV, derrière le terrain : presque noir, comme une borne d'arcade. */
internal val TvBackground = Color(0xFF0B0F14)

/**
 * La bande de score d'un jeu en mode TV : [primary] en gras (le score), puis [secondary] et éventuellement [tertiary]
 * (vies, niveau…), et à droite le rappel que Start met en pause.
 */
@Composable
internal fun TvHud(primary: String, secondary: String, tertiary: String? = null) {
    val dim = Color.White.copy(alpha = 0.8f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TV_HUD_DP.dp)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = primary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(text = secondary, style = MaterialTheme.typography.bodyMedium, color = dim)
        if (tertiary != null) {
            Text(text = tertiary, style = MaterialTheme.typography.bodyMedium, color = dim)
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(text = stringResource(R.string.tv_pause_hint), style = MaterialTheme.typography.bodyMedium, color = dim)
    }
}

/** Le rappel, en mode TV, que Start lance la partie (la manette n'a pas besoin de toucher l'écran), et que Select revient en arrière. */
@Composable
internal fun TvStartHint() {
    Text(
        text = stringResource(R.string.tv_start_hint),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = highlightContent(),
        textAlign = TextAlign.Center
    )
    TvBackHint()
}

/**
 * Le rappel, en mode TV, que Select (« − ») revient au choix des jeux. [onDark] : pour un écran à fond sombre (la pause),
 * où le texte est blanc.
 */
@Composable
internal fun TvBackHint(onDark: Boolean = false) {
    Text(
        text = stringResource(R.string.tv_back_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = if (onDark) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = if (onDark) 8.dp else 0.dp)
    )
}

/** Le rappel, en mode TV, que Start reprend la partie et que Select revient en arrière : à mettre sous le titre de l'écran de pause (fond sombre). */
@Composable
internal fun TvResumeHint() {
    Text(
        text = stringResource(R.string.tv_resume_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White,
        textAlign = TextAlign.Center
    )
    TvBackHint(onDark = true)
}
