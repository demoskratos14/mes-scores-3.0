package com.aventure.messcores

import androidx.compose.ui.res.stringResource
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Statistiques tirées du journal des parties : victoires par joueur (tous jeux confondus ou jeu par jeu),
 * et, pour un jeu précis, meilleur score et moyenne. Le calcul est dans [StatsCalculator].
 */
@Composable
fun StatsScreen(
    repository: GameHistoryRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val report = remember { StatsCalculator.compute(repository.listGames()) }
    // null = tous les jeux. rememberSaveable : le choix survit à une rotation de l'écran.
    var selectedGameId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedGame = report.games.firstOrNull { it.gameId == selectedGameId }
    val rows = if (selectedGame == null) report.overall else report.byGame[selectedGame.gameId].orEmpty()

    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(16.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }

        Text(
            text = stringResource(R.string.stats_title),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        if (report.finishedCount == 0) {
            Text(
                text = noFinishedGameMessage(context, report.inProgressCount),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
            return@Column
        }

        Text(
            text = scopeText(context, report),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Choix du jeu : « Tous les jeux » ou un jeu précis (alors meilleur score et moyenne apparaissent).
        Card(
            colors = CardDefaults.cardColors(containerColor = cardSurface()),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedGame == null,
                    onClick = { selectedGameId = null },
                    label = { Text(stringResource(R.string.stats_all_games)) }
                )
                report.games.forEach { game ->
                    FilterChip(
                        selected = selectedGame?.gameId == game.gameId,
                        onClick = { selectedGameId = game.gameId },
                        label = { Text(stringResource(R.string.stats_game_chip, game.gameName, game.finishedGames)) }
                    )
                }
            }
        }

        if (selectedGame != null) {
            Text(
                text = stringResource(
                    R.string.stats_game_header,
                    context.quantity(R.plurals.stats_finished_count, selectedGame.finishedGames),
                    stringResource(if (selectedGame.lowestWins) R.string.stats_lowest_wins else R.string.stats_highest_wins)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(rows, key = { _, stats -> stats.name.lowercase() }) { index, stats ->
                PlayerStatsCard(position = index + 1, stats = stats)
            }
        }
    }
}

@Composable
private fun PlayerStatsCard(position: Int, stats: PlayerStats) {
    val context = LocalContext.current
    val summary = stringResource(
        R.string.stats_summary,
        context.quantity(R.plurals.games_count, stats.played),
        context.quantity(R.plurals.victories_count, stats.wins),
        stats.winRatePercent
    )
    val scores = if (stats.bestScore != null && stats.averageScore != null) {
        stringResource(R.string.stats_scores, stats.bestScore, formatAverage(stats.averageScore))
    } else {
        null
    }
    val cardDescription = if (scores != null) {
        stringResource(R.string.stats_card_description_scores, position, stats.name, summary, scores)
    } else {
        stringResource(R.string.stats_card_description, position, stats.name, summary)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        // Une seule phrase pour TalkBack plutôt que quatre textes séparés.
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = cardDescription
            }
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.stats_position_name, position, stats.name),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(text = summary, style = MaterialTheme.typography.bodyMedium)
            if (scores != null) {
                Text(
                    text = scores,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Moyenne avec une décimale et le séparateur de la langue (12,5 en français), sans « ,0 » inutile (12). */
private fun formatAverage(value: Double): String {
    val rounded = Math.round(value * 10) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else String.format(Locale.getDefault(), "%.1f", rounded)
}

private fun scopeText(context: Context, report: StatsReport): String {
    val res = context.resources
    val finished = res.getQuantityString(R.plurals.stats_finished_count, report.finishedCount, report.finishedCount)
    val ignored = if (report.inProgressCount > 0) {
        res.getQuantityString(R.plurals.stats_in_progress_ignored, report.inProgressCount, report.inProgressCount)
    } else {
        ""
    }
    return context.getString(R.string.stats_scope, finished, ignored, GameHistoryRepository.MAX_SAVED_GAMES)
}

private fun noFinishedGameMessage(context: Context, inProgress: Int): String =
    context.getString(if (inProgress > 0) R.string.stats_none_with_ongoing else R.string.stats_none)
