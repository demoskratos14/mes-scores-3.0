package com.aventure.messcores

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

/**
 * Photo de fond (jeux de société) en plein écran, avec un voile semi-transparent par-dessus
 * pour garder le texte et le tableau lisibles. Elle est affichée une seule fois, dans
 * [ScoreApp], derrière tous les écrans : l'image n'est décodée qu'une fois (et ne clignote
 * plus d'un écran à l'autre).
 */
@Composable
fun AppBackground() {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.bg_board_games),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (isDarkTheme()) 0.72f else 0.55f))
        )
    }
}
