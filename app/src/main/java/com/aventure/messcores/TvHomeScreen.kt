package com.aventure.messcores

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Les jeux de l'appli pour télé (variante « tv »), dans l'ordre d'affichage : ceux dont l'écran sait se jouer en mode
 * TV (voir [TvMode]), plein écran et à la manette. Un jeu qui est prêt s'ajoute ici ; il doit avoir sa route dans
 * [OWN_SCREEN_ROUTES]. Rythme n'y est pas : il demande de choisir un fichier de musique avec le sélecteur de documents
 * d'Android, qui n'existe pas sur une télé.
 */
internal val TV_GAME_IDS = listOf(
    SNAKE_ID, TETRIS_ID, BREAKOUT_ID, ASTEROIDS_ID, INVADERS_ID, FISHCHASE_ID, SHMUP_ID,
    XONIX_ID, BUBBLE_ID, MARBLE_ID, MINESWEEPER_ID, PANCAKE_ID, DRIVE_ID, DRINK_ID, FISH_ID
)

/** Nombre de vignettes par ligne sur l'accueil TV. */
private const val TV_COLUMNS = 6

/**
 * L'accueil de l'appli pour télé : une grille de grosses vignettes de jeux, qui se parcourt à la croix de la manette ou de
 * la télécommande. La vignette sous la sélection (la première au départ) est agrandie et cernée d'un trait épais ;
 * un appui sur le bouton de validation (ou Start) ouvre le jeu [onGameChosen].
 */
@Composable
internal fun TvHomeScreen(games: List<GameRules>, onGameChosen: (GameRules) -> Unit) {
    val firstTile = remember { FocusRequester() }
    // Le réglage « A et B des manettes Nintendo » doit être lu avant le premier appui (voir [padKeyCode]).
    val context = LocalContext.current
    LaunchedEffect(context) { GamepadHub.attach(context) }
    // La manette doit pouvoir jouer sans toucher à rien : la première vignette a la sélection dès l'ouverture.
    LaunchedEffect(games) { runCatching { firstTile.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.tv_home_title),
            style = MaterialTheme.typography.displaySmall,
            color = Color.White
        )
        Text(
            text = stringResource(R.string.tv_home_hint),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.8f)
        )
        games.chunked(TV_COLUMNS).forEachIndexed { rowIndex, rowGames ->
            // IntrinsicSize.Min : toutes les vignettes d'une ligne ont la hauteur de la plus haute (nom sur deux lignes).
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                rowGames.forEachIndexed { index, game ->
                    TvGameTile(
                        game = game,
                        onClick = { onGameChosen(game) },
                        focusRequester = if (rowIndex == 0 && index == 0) firstTile else null,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Dernière ligne incomplète : on garde la largeur des vignettes.
                repeat(TV_COLUMNS - rowGames.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Une vignette de jeu : son icône et son nom ; agrandie et cernée quand elle a la sélection. */
@Composable
private fun TvGameTile(
    game: GameRules,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                val scale = if (focused) 1.06f else 1f
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(if (focused) colors.primaryContainer else cardSurface())
            .border(if (focused) 4.dp else 1.dp, if (focused) colors.primary else colors.outlineVariant, shape)
            // Valider avec le bouton A ou Start de la manette ; la touche Entrée et le centre de la croix passent par clickable.
            .onKeyEvent { event ->
                // Le bouton est celui qui est écrit sur la manette (A et B échangés sur une manette Nintendo).
                val code = padKeyCode(event.nativeKeyEvent)
                if (event.type == KeyEventType.KeyUp && (code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_BUTTON_START)) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GameIcon(game.id, size = 72.dp)
        Text(
            text = game.name,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = if (focused) colors.onPrimaryContainer else colors.onSurface
        )
    }
}
