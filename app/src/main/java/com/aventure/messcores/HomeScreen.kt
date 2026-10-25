package com.aventure.messcores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Première page de l'appli : on choisit entre les feuilles de scores (compter les points d'un jeu
 * qui se joue ailleurs) et les jeux jouables directement dans l'appli.
 */
@Composable
fun HomeScreen(
    onOpenScores: () -> Unit,
    onOpenPlayable: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    colorBlindMode: Boolean,
    onColorBlindModeChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = cardSurface()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.home_title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f).semantics { heading() }
                    )
                    LanguageSelector()
                }
                HomeChoice(
                    title = stringResource(R.string.home_scores),
                    hint = stringResource(R.string.home_scores_hint),
                    onClick = onOpenScores
                )
                HomeChoice(
                    title = stringResource(R.string.home_playable),
                    hint = stringResource(R.string.home_playable_hint),
                    onClick = onOpenPlayable
                )
                AppearanceSettings(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    colorBlindMode = colorBlindMode,
                    onColorBlindModeChange = onColorBlindModeChange
                )
            }
        }
    }
}

@Composable
private fun HomeChoice(title: String, hint: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall)
        }
    }
}
