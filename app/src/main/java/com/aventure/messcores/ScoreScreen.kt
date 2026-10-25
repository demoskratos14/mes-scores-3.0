package com.aventure.messcores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun ScoreScreen(
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
    // Feuille de catégories (Yams) : une ligne par catégorie au lieu de manches numérotées.
    val labelColumnWidth = if (viewModel.sheetRows != null) SHEET_LABEL_COLUMN_WIDTH else LABEL_COLUMN_WIDTH

    KeepScreenOn()
    val context = LocalContext.current

    // Nom complet et choix de couleur, en popup, quand une case de nom est cliquée.
    var expandedNameIndex by remember { mutableStateOf<Int?>(null) }

    // Un seul état de scroll horizontal partagé entre l'en-tête fixe et le corps
    // du tableau, pour que les colonnes restent alignées quand on défile latéralement.
    val horizontalScrollState = rememberScrollState()

    // Confirmation temporaire affichée après un clic sur "Enregistrer".
    var justSaved by remember { mutableStateOf(false) }
    LaunchedEffect(justSaved) {
        if (justSaved) {
            delay(2000)
            justSaved = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        ScoreHeader(viewModel, onOpenDice, onOpenShare, shareActive)
        Spacer(modifier = Modifier.height(12.dp))

        if (viewModel.isGameOver()) {
            GameOverBanner(players = players, winners = viewModel.winners())
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = cardSurface()
            ),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val playerColumnWidth: Dp = if (players.isEmpty()) {
                    MAX_PLAYER_COLUMN_WIDTH
                } else {
                    val available = maxWidth - labelColumnWidth - 16.dp
                    (available / players.size).coerceIn(MIN_PLAYER_COLUMN_WIDTH, MAX_PLAYER_COLUMN_WIDTH)
                }

                Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    ScoreTableHeader(
                        viewModel = viewModel,
                        totals = totals,
                        ranks = ranks,
                        labelColumnWidth = labelColumnWidth,
                        playerColumnWidth = playerColumnWidth,
                        horizontalScrollState = horizontalScrollState,
                        onPlayerNameClick = { expandedNameIndex = it }
                    )
                    ScoreTableBody(
                        viewModel = viewModel,
                        labelColumnWidth = labelColumnWidth,
                        playerColumnWidth = playerColumnWidth,
                        horizontalScrollState = horizontalScrollState,
                        modifier = Modifier.weight(1f)
                    )
                    ScoreTableFooter(
                        viewModel = viewModel,
                        totals = totals,
                        labelColumnWidth = labelColumnWidth,
                        playerColumnWidth = playerColumnWidth,
                        horizontalScrollState = horizontalScrollState
                    )
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

    expandedNameIndex?.let { index ->
        PlayerOptionsDialog(viewModel, index) { expandedNameIndex = null }
    }
}

/** Titre de l'écran : « Tableau des scores », nom du jeu, fiche de règles, courbe d'évolution et dés. */
@Composable
private fun ScoreHeader(
    viewModel: ScoreViewModel,
    onOpenDice: (() -> Unit)?,
    onOpenShare: (() -> Unit)?,
    shareActive: Boolean
) {
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
}

/** Bandeau affiché quand la partie est terminée : vainqueur(s) ou simple fin de partie. */
@Composable
private fun GameOverBanner(players: List<String>, winners: List<Int>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = highlightContainer(), contentColor = highlightContent()),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    ) {
        Text(
            text = if (winners.isEmpty()) {
                stringResource(R.string.score_game_over)
            } else {
                stringResource(
                    if (winners.size > 1) R.string.score_game_over_winners else R.string.score_game_over_winner,
                    winners.joinToString(stringResource(R.string.result_and)) { players[it] }
                )
            },
            modifier = Modifier.padding(12.dp),
            fontWeight = FontWeight.Bold
        )
    }
}
