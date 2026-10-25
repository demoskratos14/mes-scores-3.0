package com.aventure.messcores

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Un téléphone dessiné (jeu pour un seul téléphone) ou deux (le jeu se joue aussi en connectant
 * plusieurs téléphones). [describe] à faux quand un texte voisin dit déjà la même chose (légende).
 */
@Composable
internal fun PhoneIcons(multi: Boolean, modifier: Modifier = Modifier, describe: Boolean = true) {
    val description = stringResource(if (multi) R.string.playable_multi_phone else R.string.playable_one_phone)
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = if (describe) modifier.semantics { contentDescription = description } else modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(if (multi) 2 else 1) { PhoneShape(color) }
    }
}

@Composable
private fun PhoneShape(color: Color) {
    Canvas(modifier = Modifier.size(width = 12.dp, height = 20.dp)) {
        val stroke = 1.6.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(width = stroke)
        )
        // Petit trait en bas : le bouton d'accueil.
        val y = size.height - 4.dp.toPx()
        drawLine(color, Offset(size.width * 0.35f, y), Offset(size.width * 0.65f, y), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}
