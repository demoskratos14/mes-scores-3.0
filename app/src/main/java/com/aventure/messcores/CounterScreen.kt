package com.aventure.messcores

import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
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
fun CounterScreen(
    viewModel: ScoreViewModel,
    historyRepository: GameHistoryRepository,
    onOpenDice: (() -> Unit)? = null,
    onOpenShare: (() -> Unit)? = null,
    shareActive: Boolean = false
) {
    val players = viewModel.players
    // Totaux et rangs recalculés une seule fois par changement de score, pas une fois par ligne.
    val totals by remember(viewModel) { derivedStateOf { viewModel.totals } }
    val ranks by remember(viewModel) { derivedStateOf { viewModel.ranks } }

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
                    if (onOpenDice != null && viewModel.gameRules.hasDice()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        DiceButton(onClick = onOpenDice)
                    }
                }
                if (onOpenShare != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    ShareButton(active = shareActive, onClick = onOpenShare)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            players.forEachIndexed { index, name ->
                val rank = viewModel.rankOf(index, ranks)
                // Lu par TalkBack à chaque changement de score : nom, score et rang du joueur.
                val scoreDescription = stringResource(
                    R.string.counter_player_summary, name, totals.getOrElse(index) { 0 }, rank
                )
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
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClickLabel = optionsLabel, role = Role.Button) { optionsIndex = index },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayerColorDot(
                                color = viewModel.playerColors.getOrElse(index) { Color.Gray },
                                modifier = Modifier.padding(end = 10.dp)
                            )
                            Column {
                                Text(name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "#$rank",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        FilledIconButton(onClick = { viewModel.incrementCounter(index, -1) }) {
                            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.counter_remove_for, name))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = totals.getOrElse(index) { 0 }.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(48.dp).semantics {
                                contentDescription = scoreDescription
                                liveRegion = LiveRegionMode.Polite
                            },
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        FilledIconButton(onClick = { viewModel.incrementCounter(index, 1) }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.counter_add_for, name))
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
            onShareImage = { ResultImage.share(context, viewModel.shareImageContent(context)) },
            onUndo = { viewModel.undo() },
            canUndo = viewModel.canUndo,
            onRedo = { viewModel.redo() },
            canRedo = viewModel.canRedo
        )
    }

    optionsIndex?.let { index ->
        PlayerOptionsDialog(viewModel, index) { optionsIndex = null }
    }
}
