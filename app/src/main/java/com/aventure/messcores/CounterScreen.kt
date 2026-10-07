package com.aventure.messcores

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
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
fun CounterScreen(viewModel: ScoreViewModel, historyRepository: GameHistoryRepository) {
    val players = viewModel.players
    // Totaux et rangs recalculés une seule fois par changement de score, pas une fois par ligne.
    val totals by remember(viewModel) { derivedStateOf { viewModel.totals } }
    val ranks by remember(viewModel) { derivedStateOf { viewModel.ranks } }

    KeepScreenOn()
    val context = LocalContext.current

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
                    color = Color.White
                )
                Text(
                    text = viewModel.gameRules.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            players.forEachIndexed { index, name ->
                val rank = viewModel.rankOf(index, ranks)
                Card(
                    colors = CardDefaults.cardColors(containerColor = cardSurface()),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "#$rank",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        FilledIconButton(onClick = { viewModel.incrementCounter(index, -1) }) {
                            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.counter_remove))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = totals.getOrElse(index) { 0 }.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(48.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        FilledIconButton(onClick = { viewModel.incrementCounter(index, 1) }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.counter_add))
                        }
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
            onUndo = { viewModel.undo() },
            canUndo = viewModel.canUndo
        )
    }
}
