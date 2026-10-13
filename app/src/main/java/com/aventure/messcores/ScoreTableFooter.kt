package com.aventure.messcores

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp

/**
 * Pied fixe du tableau : phase (Phase 10), bonus (feuille de catégories) et total,
 * toujours visibles sous les manches qui défilent.
 */
@Composable
internal fun ScoreTableFooter(
    viewModel: ScoreViewModel,
    totals: List<Int>,
    labelColumnWidth: Dp,
    playerColumnWidth: Dp,
    horizontalScrollState: ScrollState
) {
    val players = viewModel.players
    val playerColors = viewModel.playerColors
    HorizontalDivider()
    Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
        Column {
            if (viewModel.hasPhases) {
                Box(
                    modifier = Modifier.width(labelColumnWidth).height(BONUS_ROW_HEIGHT),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(stringResource(R.string.phase_label), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (viewModel.sheetHasBonus) {
                Box(
                    modifier = Modifier.width(labelColumnWidth).height(BONUS_ROW_HEIGHT),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(stringResource(R.string.sheet_bonus), style = MaterialTheme.typography.bodySmall)
                }
            }
            Box(
                modifier = Modifier.width(labelColumnWidth).height(TOTAL_ROW_HEIGHT),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(stringResource(R.string.score_total), fontWeight = FontWeight.Bold)
            }
        }
        players.forEachIndexed { playerIndex, _ ->
            val color = playerColors.getOrElse(playerIndex) { Color.Black }
            Column {
                if (viewModel.hasPhases) {
                    PhaseCell(viewModel, playerIndex, playerColumnWidth, color)
                }
                if (viewModel.sheetHasBonus) {
                    val upper = viewModel.sheetUpperTotalFor(playerIndex)
                    val bonus = viewModel.sheetBonusFor(playerIndex)
                    Box(
                        modifier = Modifier.width(playerColumnWidth).height(BONUS_ROW_HEIGHT),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (bonus > 0) "+$bonus" else "$upper/${ScoreSheets.UPPER_BONUS_THRESHOLD}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = playerTextColor(color)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .width(playerColumnWidth)
                        .height(TOTAL_ROW_HEIGHT)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = totals.getOrElse(playerIndex) { 0 }.toString(),
                        fontWeight = FontWeight.Bold,
                        color = playerTextColor(color)
                    )
                }
            }
        }
    }
}

/** Phase atteinte par un joueur (Phase 10) : un clic ouvre le choix de la phase. */
@Composable
private fun PhaseCell(
    viewModel: ScoreViewModel,
    playerIndex: Int,
    playerColumnWidth: Dp,
    color: Color
) {
    val phase = viewModel.phaseFor(playerIndex)
    val phaseText = if (phase > viewModel.phaseCount) "✓" else phase.toString()
    val phaseDescription = stringResource(R.string.phase_description, viewModel.players[playerIndex], phaseText)
    val phaseChangeLabel = stringResource(R.string.phase_change)
    var showPhaseDialog by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(playerColumnWidth)
            .height(BONUS_ROW_HEIGHT)
            .clickable(onClickLabel = phaseChangeLabel) { showPhaseDialog = true }
            .semantics { contentDescription = phaseDescription },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = phaseText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = playerTextColor(color)
        )
    }
    if (showPhaseDialog) {
        ScoreChoiceDialog(
            title = stringResource(R.string.phase_dialog_title, viewModel.players[playerIndex]),
            options = (1..viewModel.phaseCount + 1).toList(),
            hint = stringResource(R.string.phase_hint),
            zeroCrossesOut = false,
            current = phase,
            onPick = { value ->
                viewModel.setPhase(playerIndex, value ?: 1)
                showPhaseDialog = false
            },
            onDismiss = { showPhaseDialog = false },
            optionLabel = { if (it > viewModel.phaseCount) "✓" else it.toString() }
        )
    }
}
