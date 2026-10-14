package com.aventure.messcores

import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun TeamRoundsScreen(
    viewModel: ScoreViewModel,
    historyRepository: GameHistoryRepository,
    onAddRound: () -> Unit
) {
    val players = viewModel.players
    // Totaux et rangs recalculés une seule fois par changement de score, pas une fois par ligne.
    val totals by remember(viewModel) { derivedStateOf { viewModel.totals } }
    val ranks by remember(viewModel) { derivedStateOf { viewModel.ranks } }
    val rounds = viewModel.teamRounds

    KeepScreenOn()
    val context = LocalContext.current

    // Joueur dont la fenêtre « nom complet et couleur » est ouverte.
    var optionsIndex by remember { mutableStateOf<Int?>(null) }
    val optionsLabel = stringResource(R.string.score_player_options)

    var justSaved by remember { mutableStateOf(false) }
    LaunchedEffect(justSaved) {
        if (justSaved) {
            delay(2000)
            justSaved = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
                    ScoreChartButton(viewModel)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Card(
            colors = CardDefaults.cardColors(containerColor = cardSurface()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Totaux et classement
                players.forEachIndexed { index, name ->
                    val rank = viewModel.rankOf(index, ranks)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (viewModel.isLeader(index, totals, ranks)) {
                            Icon(
                                imageVector = Icons.Filled.EmojiEvents,
                                contentDescription = stringResource(R.string.first_place),
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClickLabel = optionsLabel, role = Role.Button) { optionsIndex = index },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayerColorDot(
                                color = viewModel.playerColors.getOrElse(index) { Color.Gray },
                                modifier = Modifier.padding(end = 8.dp),
                                size = 24.dp
                            )
                            Text("#$rank  $name", fontWeight = FontWeight.SemiBold)
                        }
                        Text(totals.getOrElse(index) { 0 }.toString(), fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        viewModel.editingTeamRoundIndex = null
                        onAddRound()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.team_new_round))
                }

                if (rounds.isNotEmpty()) {
                    Text(stringResource(R.string.team_history), style = MaterialTheme.typography.titleSmall)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        rounds.forEachIndexed { index, round ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (round.deltas != null) {
                                        stringResource(R.string.team_round_header, index + 1, round.teamALabel) + "\n" +
                                            players.indices.joinToString(" · ") { p ->
                                                val d = round.deltas.getOrElse(p) { 0 }
                                                "${players[p]} ${if (d > 0) "+" else if (d < 0) "−" else ""}${kotlin.math.abs(d)}"
                                            }
                                    } else {
                                        stringResource(R.string.team_round_line, index + 1, round.teamALabel, (if (round.value >= 0) "+" else "") + round.value)
                                    },
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (round.tarotInput != null || round.contractInput != null) {
                                    IconButton(onClick = {
                                        viewModel.editingTeamRoundIndex = index
                                        onAddRound()
                                    }) {
                                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.team_edit_round))
                                    }
                                }
                                IconButton(onClick = { viewModel.removeTeamRound(index) }) {
                                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.team_delete_round))
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        Spacer(modifier = Modifier.height(12.dp))
        // Pas de bouton « Annuler » ici : chaque manche se corrige ou se supprime dans l'historique.
        ScoreActionBar(
            justSaved = justSaved,
            onSave = {
                viewModel.saveToJournal(historyRepository)
                justSaved = true
            },
            onShare = { ResultText.share(context, viewModel.shareText(context)) },
            onShareImage = { ResultImage.share(context, viewModel.shareImageContent(context)) }
        )
    }

    optionsIndex?.let { index ->
        PlayerOptionsDialog(viewModel, index) { optionsIndex = null }
    }
}
