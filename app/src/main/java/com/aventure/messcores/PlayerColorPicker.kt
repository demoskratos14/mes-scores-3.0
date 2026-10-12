package com.aventure.messcores

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * Choix de la couleur d'un joueur : toute la palette est proposée, mais les couleurs déjà
 * prises par un autre joueur ([takenColors]) sont grisées, barrées et non sélectionnables.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerColorPicker(
    currentColor: Color,
    takenColors: Set<Color>,
    onPick: (Color) -> Unit
) {
    val selectedState = stringResource(R.string.score_color_selected)
    val takenState = stringResource(R.string.score_color_taken)
    val pickLabel = stringResource(R.string.score_color_pick)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PLAYER_COLOR_PALETTE.forEachIndexed { index, color ->
            val selected = color == currentColor
            val taken = !selected && color in takenColors
            val description = stringResource(R.string.score_color_option, index + 1)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .alpha(if (taken) 0.3f else 1f)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape)
                        else Modifier
                    )
                    .clickable(enabled = !taken, onClickLabel = pickLabel, role = Role.RadioButton) { onPick(color) }
                    .semantics {
                        contentDescription = description
                        if (selected) stateDescription = selectedState
                        else if (taken) stateDescription = takenState
                    },
                contentAlignment = Alignment.Center
            ) {
                if (selected || taken) {
                    Icon(
                        imageVector = if (selected) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        tint = contentColorFor(color)
                    )
                }
            }
        }
    }
}
