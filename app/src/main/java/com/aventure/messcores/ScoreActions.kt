package com.aventure.messcores

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Rangée de boutons en bas des écrans de score : « Annuler » (si [onUndo] est fourni),
 * « Partager » et « Enregistrer ». Les boutons secondaires ont un fond blanc pour rester
 * lisibles sur la photo de fond.
 */
@Composable
fun ScoreActionBar(
    justSaved: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onUndo: (() -> Unit)? = null,
    canUndo: Boolean = false
) {
    val secondaryColors = ButtonDefaults.outlinedButtonColors(containerColor = cardSurface())
    // Marges réduites : trois boutons doivent tenir côte à côte sur un petit écran.
    val contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (onUndo != null) {
            OutlinedButton(
                onClick = onUndo,
                enabled = canUndo,
                colors = secondaryColors,
                contentPadding = contentPadding
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" " + stringResource(R.string.common_cancel))
            }
        }
        OutlinedButton(
            onClick = onShare,
            colors = secondaryColors,
            contentPadding = contentPadding
        ) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(" " + stringResource(R.string.actions_share))
        }
        Button(
            onClick = onSave,
            modifier = Modifier.weight(1f),
            contentPadding = contentPadding
        ) {
            Text(stringResource(if (justSaved) R.string.actions_saved else R.string.actions_save), maxLines = 1)
        }
    }
}
