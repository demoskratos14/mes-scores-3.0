package com.aventure.messcores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Largeurs des colonnes du tableau de score (sets terminés, jeux du set en cours, points du jeu en cours).
private val SET_CELL_WIDTH = 36.dp
private val GAMES_CELL_WIDTH = 56.dp
private val POINTS_CELL_WIDTH = 64.dp

/**
 * Écran de score d'un match de tennis ou de padel (mode [ScoreMode.RACKET]) : un bouton par camp pour
 * donner le point, le tableau des sets, des jeux et des points, et les réglages du match (modifiables
 * avant le premier point). Le calcul est dans [RacketScoring].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RacketScreen(viewModel: ScoreViewModel, historyRepository: GameHistoryRepository) {
    KeepScreenOn()
    val context = LocalContext.current
    val state = viewModel.racketState
    val config = viewModel.racketConfig
    val players = viewModel.players
    val started = viewModel.hasProgress()

    var justSaved by remember { mutableStateOf(false) }
    LaunchedEffect(justSaved) {
        if (justSaved) {
            delay(2000)
            justSaved = false
        }
    }

    fun teamName(side: Int): String =
        viewModel.racketPlayers(side).joinToString(" / ") { players.getOrElse(it) { "" } }

    // Textes d'état du jeu en cours, lus une fois ici (stringResource n'est pas appelable dans un « when »).
    val setLabel = stringResource(R.string.racket_set_number, state.currentSetNumber)
    val tiebreakLabel = stringResource(R.string.racket_tiebreak)
    val superTiebreakLabel = stringResource(R.string.racket_super_tiebreak)
    val goldenLabel = stringResource(R.string.racket_golden_now)
    val deuceLabel = stringResource(R.string.racket_deuce)
    val statusLabel: String? = when {
        state.winner != null -> null
        state.superTiebreak -> superTiebreakLabel
        state.inTiebreak -> tiebreakLabel
        state.isGoldenPoint -> goldenLabel
        state.isDeuce -> deuceLabel
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = stringResource(R.string.scoreboard),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                modifier = Modifier.semantics { heading() }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = viewModel.gameRules.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(10.dp))
                RulesButton(rules = viewModel.gameRules)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.winner?.let { winnerSide ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = highlightContainer(), contentColor = highlightContent()),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.racket_winner, teamName(winnerSide)),
                        modifier = Modifier.padding(12.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Tableau : une ligne par camp, une colonne par set terminé, puis jeux et points du set en cours.
            Card(
                colors = CardDefaults.cardColors(containerColor = cardSurface()),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (state.winner == null) setLabel else stringResource(R.string.racket_sets),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        state.sets.indices.forEach { index ->
                            Text(
                                text = (index + 1).toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(SET_CELL_WIDTH)
                            )
                        }
                        if (state.winner == null) {
                            Text(
                                text = stringResource(R.string.racket_games),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(GAMES_CELL_WIDTH)
                            )
                            Text(
                                text = stringResource(R.string.racket_points),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(POINTS_CELL_WIDTH)
                            )
                        }
                    }
                    for (side in 0..1) {
                        RacketTeamRow(
                            name = teamName(side),
                            dotColors = viewModel.racketPlayers(side).map { viewModel.playerColors.getOrElse(it) { Color.Gray } },
                            state = state,
                            side = side
                        )
                    }
                    statusLabel?.let { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                }
            }

            // Un gros bouton par camp : appuyer pour donner le point.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (side in 0..1) {
                    Button(
                        onClick = { viewModel.racketPoint(side) },
                        enabled = state.winner == null,
                        modifier = Modifier.weight(1f).height(84.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.racket_point_for, teamName(side)),
                            textAlign = TextAlign.Center,
                            maxLines = 3
                        )
                    }
                }
            }

            // Réglages : possibles tant qu'aucun point n'est joué.
            Card(
                colors = CardDefaults.cardColors(containerColor = cardSurface()),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.racket_format_title), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1 to R.string.racket_sets_1, 2 to R.string.racket_sets_3, 3 to R.string.racket_sets_5)
                            .forEach { (setsToWin, label) ->
                                FilterChip(
                                    selected = config.setsToWin == setsToWin,
                                    enabled = !started,
                                    onClick = { viewModel.setRacketConfig(config.copy(setsToWin = setsToWin)) },
                                    label = { Text(stringResource(label)) }
                                )
                            }
                    }
                    Text(stringResource(R.string.racket_final_set_title), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            FinalSetMode.TIEBREAK to R.string.racket_final_tiebreak,
                            FinalSetMode.SUPER_TIEBREAK to R.string.racket_final_super,
                            FinalSetMode.ADVANTAGE to R.string.racket_final_advantage
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = config.finalSet == mode,
                                enabled = !started,
                                onClick = { viewModel.setRacketConfig(config.copy(finalSet = mode)) },
                                label = { Text(stringResource(label)) }
                            )
                        }
                    }
                    // La ligne entière est l'interrupteur (une seule cible, avec son texte, pour TalkBack).
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = config.goldenPoint,
                                enabled = !started,
                                role = Role.Switch,
                                onValueChange = { viewModel.setRacketConfig(config.copy(goldenPoint = it)) }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.racket_golden_point),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = config.goldenPoint, onCheckedChange = null, enabled = !started)
                    }
                    if (started) {
                        Text(
                            text = stringResource(R.string.racket_config_locked),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        ScoreActionBar(
            justSaved = justSaved,
            onSave = {
                viewModel.saveToJournal(historyRepository)
                justSaved = true
            },
            onShare = { ResultText.share(context, viewModel.shareText(context)) },
            onShareImage = { ResultImage.share(context, viewModel.shareImageContent(context)) },
            onUndo = { viewModel.undo() },
            canUndo = viewModel.canUndo,
            onRedo = { viewModel.redo() },
            canRedo = viewModel.canRedo
        )
    }
}

/** Ligne d'un camp : pastilles de couleur et noms, jeux de chaque set terminé, puis jeux et points du set en cours. */
@Composable
private fun RacketTeamRow(name: String, dotColors: List<Color>, state: RacketState, side: Int) {
    val points = if (state.hasAdvantage(side)) stringResource(R.string.racket_advantage) else state.pointText(side)
    // Lu par TalkBack à chaque point : le camp, ses sets, ses jeux et ses points.
    val sets = state.setsWon(side)
    val games = state.games(side)
    val summary = stringResource(
        R.string.racket_team_summary,
        name,
        pluralStringResource(R.plurals.racket_sets_count, sets, sets),
        pluralStringResource(R.plurals.racket_games_count, games, games),
        points
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = summary
                liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            dotColors.forEach { color ->
                PlayerColorDot(color = color, size = 18.dp, modifier = Modifier.padding(end = 4.dp))
            }
            Text(
                text = name,
                fontWeight = if (state.winner == side) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 2,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        state.sets.forEach { set ->
            Text(
                text = (if (side == 0) set.a else set.b).toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (set.winner == side) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(SET_CELL_WIDTH)
            )
        }
        if (state.winner == null) {
            Text(
                text = state.games(side).toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(GAMES_CELL_WIDTH)
            )
            Text(
                text = points,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(POINTS_CELL_WIDTH)
            )
        }
    }
}
