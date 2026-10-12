package com.aventure.messcores

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Hauteurs fixes pour que toutes les colonnes restent alignées entre elles.
internal val RANK_ROW_HEIGHT = 40.dp
internal val NAME_ROW_HEIGHT = 48.dp
internal val TOTAL_ROW_HEIGHT = 56.dp
internal val LABEL_COLUMN_WIDTH = 80.dp
// Les noms de catégories (« Grande suite ») sont plus longs que « Manche 12 » : colonne plus large.
internal val SHEET_LABEL_COLUMN_WIDTH = 112.dp
internal val BONUS_ROW_HEIGHT = 48.dp
internal val MIN_PLAYER_COLUMN_WIDTH = 28.dp
internal val MAX_PLAYER_COLUMN_WIDTH = 128.dp

/** Renvoie du noir ou du blanc selon le fond donné, pour rester lisible. */
internal fun contentColorFor(background: Color): Color {
    val luminance = 0.299 * background.red + 0.587 * background.green + 0.114 * background.blue
    return if (luminance > 0.6) Color.Black else Color.White
}

/**
 * En-tête fixe du tableau : rang + nom de chaque joueur, toujours visible au-dessus des manches.
 * Un clic sur un nom appelle [onPlayerNameClick] (affichage du nom complet).
 */
@Composable
internal fun ScoreTableHeader(
    viewModel: ScoreViewModel,
    totals: List<Int>,
    ranks: List<Int>,
    labelColumnWidth: Dp,
    playerColumnWidth: Dp,
    horizontalScrollState: ScrollState,
    onPlayerNameClick: (Int) -> Unit
) {
    val players = viewModel.players
    val playerColors = viewModel.playerColors
    Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
        Column {
            Box(Modifier.width(labelColumnWidth).height(RANK_ROW_HEIGHT))
            Box(Modifier.width(labelColumnWidth).height(NAME_ROW_HEIGHT))
        }
        players.forEachIndexed { playerIndex, name ->
            val rank = viewModel.rankOf(playerIndex, ranks)
            val isLeader = viewModel.isLeader(playerIndex, totals, ranks)
            val color = playerColors.getOrElse(playerIndex) { Color.Gray }
            Column {
                Box(
                    modifier = Modifier.width(playerColumnWidth).height(RANK_ROW_HEIGHT),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLeader) {
                            Icon(
                                imageVector = Icons.Filled.EmojiEvents,
                                contentDescription = stringResource(R.string.first_place),
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                        Text("#$rank", fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    modifier = Modifier
                        .width(playerColumnWidth)
                        .height(NAME_ROW_HEIGHT)
                        .background(color)
                        .clickable(onClickLabel = stringResource(R.string.score_show_full_name), role = Role.Button) { onPlayerNameClick(playerIndex) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name,
                        color = contentColorFor(color),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Corps défilant du tableau : une manche (ou une catégorie de feuille) par ligne.
 * Le défilement horizontal est partagé avec l'en-tête et le pied via [horizontalScrollState].
 */
@Composable
internal fun ScoreTableBody(
    viewModel: ScoreViewModel,
    labelColumnWidth: Dp,
    playerColumnWidth: Dp,
    horizontalScrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val players = viewModel.players
    val playerColors = viewModel.playerColors
    val scores = viewModel.scores
    val multipliers = viewModel.gameRules.multipliers
    // Un peu plus de hauteur par case quand le sélecteur de règle est affiché.
    val scoreRowHeight = if (multipliers.size > 1) 92.dp else 72.dp
    // Feuille de catégories (Yams) : une ligne par catégorie au lieu de manches numérotées.
    val sheetRows = viewModel.sheetRows
    // Case en cours de saisie (-1 : aucune). rememberSaveable : la fenêtre reste ouverte après une rotation.
    var entryRound by rememberSaveable { mutableIntStateOf(-1) }
    var entryPlayer by rememberSaveable { mutableIntStateOf(-1) }
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
            Column {
                scores.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier.width(labelColumnWidth).height(scoreRowHeight),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val rowLabel = sheetRows?.getOrNull(index)
                        Text(
                            if (rowLabel != null) rowLabel.title()
                            else stringResource(R.string.score_round_n, index + 1)
                        )
                    }
                }
            }

            players.forEachIndexed { playerIndex, _ ->
                val color = playerColors.getOrElse(playerIndex) { Color.Black }
                Column {
                    // Bowling : score cumulé de ce joueur après chaque frame.
                    val bowlingCumulative = if (viewModel.gameRules.sheet == ScoreSheets.BOWLING) {
                        BowlingScoring.cumulative(scores.map { it.getOrNull(playerIndex)?.baseValue })
                    } else {
                        null
                    }
                    scores.forEachIndexed { roundIndex, round ->
                        Box(
                            modifier = Modifier.width(playerColumnWidth).height(scoreRowHeight),
                            contentAlignment = Alignment.Center
                        ) {
                            ScoreCell(
                                display = bowlingCumulative?.let {
                                    BowlingScoring.display(round[playerIndex].baseValue, it.getOrNull(roundIndex))
                                },
                                cell = round[playerIndex],
                                multipliers = multipliers,
                                playerColor = color,
                                onClick = { entryRound = roundIndex; entryPlayer = playerIndex },
                                onMultiplierPicked = { id ->
                                    viewModel.setMultiplier(roundIndex, playerIndex, id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    if (entryRound >= 0 && entryPlayer >= 0) {
        ScoreEntryDialog(
            viewModel = viewModel,
            round = entryRound,
            player = entryPlayer,
            onSelect = { round, player -> entryRound = round; entryPlayer = player },
            onClose = { entryRound = -1; entryPlayer = -1 }
        )
    }
}
