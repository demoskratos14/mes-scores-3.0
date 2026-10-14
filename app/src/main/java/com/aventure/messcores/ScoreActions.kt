package com.aventure.messcores

import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * « Rétablir » (si [onRedo] est fourni), « Partager » et « Enregistrer ». Les boutons secondaires ont un fond blanc pour rester
 * lisibles sur la photo de fond.
 */
@Composable
fun ScoreActionBar(
    justSaved: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onShareImage: (() -> Unit)? = null,
    onUndo: (() -> Unit)? = null,
    canUndo: Boolean = false,
    onRedo: (() -> Unit)? = null,
    canRedo: Boolean = false
) {
    val secondaryColors = ButtonDefaults.outlinedButtonColors(containerColor = cardSurface())
    // Marges réduites : trois boutons doivent tenir côte à côte sur un petit écran.
    val contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Annuler / Rétablir : boutons-icônes (leur description est lue par TalkBack) pour laisser
        // de la place à « Partager » et « Enregistrer » sur un petit écran.
        if (onUndo != null) {
            OutlinedIconButton(
                onClick = onUndo,
                enabled = canUndo,
                colors = IconButtonDefaults.outlinedIconButtonColors(containerColor = cardSurface())
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.actions_undo))
            }
        }
        if (onRedo != null) {
            OutlinedIconButton(
                onClick = onRedo,
                enabled = canRedo,
                colors = IconButtonDefaults.outlinedIconButtonColors(containerColor = cardSurface())
            ) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.actions_redo))
            }
        }
        // Avec une image possible, « Partager » ouvre un petit menu : texte ou image.
        var shareMenuOpen by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(
                onClick = { if (onShareImage == null) onShare() else shareMenuOpen = true },
                colors = secondaryColors,
                contentPadding = contentPadding
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" " + stringResource(R.string.actions_share))
            }
            if (onShareImage != null) {
                DropdownMenu(expanded = shareMenuOpen, onDismissRequest = { shareMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share_as_text)) },
                        onClick = { shareMenuOpen = false; onShare() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share_as_image)) },
                        onClick = { shareMenuOpen = false; onShareImage() }
                    )
                }
            }
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
