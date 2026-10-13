package com.aventure.messcores

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val ROUND_COLUMN_WIDTH = 190.dp

@Composable
fun TournamentBracketScreen(
    viewModel: TournamentViewModel,
    onBack: () -> Unit,
    onNewTournament: () -> Unit
) {
    val players = viewModel.participants
    val rounds = viewModel.rounds
    val context = LocalContext.current

    // Résultat à corriger (tour, match) en attente de confirmation, car il fait perdre les tours suivants.
    var pendingCorrection by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var confirmNew by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
            TextButton(onClick = { if (viewModel.finished) onNewTournament() else confirmNew = true }) {
                Text(stringResource(R.string.new_championship))
            }
        }

        Text(
            text = stringResource(R.string.bracket_title),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Text(
            text = stringResource(viewModel.format.labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (viewModel.finished && viewModel.format == TournamentFormat.ROUND_ROBIN) {
            val standings = viewModel.standings()
            PouleFinalCard(
                players = players,
                rows = standings,
                onShare = { ResultText.share(context, ResultText.poules(context, players, standings)) }
            )
        } else if (viewModel.finished) {
            val podium = viewModel.podium()
            Card(
                colors = CardDefaults.cardColors(containerColor = highlightContainer(), contentColor = highlightContent()),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.final_ranking), style = MaterialTheme.typography.labelMedium)
                    podium.forEachIndexed { place, playerIndex ->
                        val name = players.getOrNull(playerIndex) ?: "?"
                        Text(
                            text = stringResource(
                                when (place) {
                                    0 -> R.string.podium_champion
                                    1 -> R.string.podium_second
                                    2 -> R.string.podium_third
                                    else -> R.string.podium_fourth
                                },
                                name
                            ),
                            fontWeight = if (place == 0) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    TextButton(
                        onClick = { ResultText.share(context, ResultText.tournament(context, players, podium)) }
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" " + stringResource(R.string.share_ranking))
                    }
                }
            }
        } else {
            Text(
                text = stringResource(
                    if (viewModel.format == TournamentFormat.ROUND_ROBIN) R.string.bracket_hint_round_robin
                    else R.string.bracket_hint_knockout
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        if (viewModel.format == TournamentFormat.ROUND_ROBIN) {
            RoundRobinBody(
                players = players,
                rounds = rounds,
                standings = viewModel.standings(),
                onPick = { roundIndex, matchIndex, winner -> viewModel.setWinner(roundIndex, matchIndex, winner) },
                onCorrect = { roundIndex, matchIndex -> viewModel.resetMatch(roundIndex, matchIndex) }
            )
        } else Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            rounds.forEachIndexed { roundIndex, round ->
                Column(
                    modifier = Modifier
                        .width(ROUND_COLUMN_WIDTH)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val title = roundTitle(round, roundIndex)
                    if (title.isNotEmpty()) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    round.forEachIndexed { matchIndex, match ->
                        MatchCard(
                            match = match,
                            players = players,
                            onPick = { winner -> viewModel.setWinner(roundIndex, matchIndex, winner) },
                            onCorrect = {
                                if (viewModel.hasPlayedAfter(roundIndex)) {
                                    pendingCorrection = roundIndex to matchIndex
                                } else {
                                    viewModel.resetMatch(roundIndex, matchIndex)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    pendingCorrection?.let { (roundIndex, matchIndex) ->
        AlertDialog(
            onDismissRequest = { pendingCorrection = null },
            title = { Text(stringResource(R.string.bracket_fix_title)) },
            text = { Text(stringResource(R.string.bracket_fix_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetMatch(roundIndex, matchIndex)
                    pendingCorrection = null
                }) { Text(stringResource(R.string.common_fix)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingCorrection = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (confirmNew) {
        AlertDialog(
            onDismissRequest = { confirmNew = false },
            title = { Text(stringResource(R.string.bracket_new_title)) },
            text = { Text(stringResource(R.string.bracket_new_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmNew = false
                    onNewTournament()
                }) { Text(stringResource(R.string.new_championship)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmNew = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@Composable
private fun roundTitle(round: List<TournamentMatch>, roundIndex: Int): String = when {
    round.any { it.label != null } -> ""
    round.size == 1 -> stringResource(R.string.bracket_final)
    round.size == 2 -> stringResource(R.string.bracket_semi_finals)
    round.size == 4 -> stringResource(R.string.bracket_quarter_finals)
    else -> stringResource(R.string.bracket_round, roundIndex + 1)
}

/**
 * Libellé affiché d'un match à étiquette. « Finale » et « Petite finale » sont aussi des
 * identifiants enregistrés avec le championnat (voir TournamentEngine) : on les traduit
 * à l'affichage seulement.
 */
@Composable
private fun matchLabelText(label: String): String = when (label) {
    "Finale" -> stringResource(R.string.bracket_final)
    "Petite finale" -> stringResource(R.string.bracket_small_final)
    else -> label
}

@Composable
private fun MatchCard(
    match: TournamentMatch,
    players: List<String>,
    onPick: (Int) -> Unit,
    onCorrect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            match.label?.let {
                Text(
                    text = matchLabelText(it),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            PlayerRow(
                name = match.playerAIndex?.let { players.getOrNull(it) },
                isWinner = match.winnerIndex != null && match.winnerIndex == match.playerAIndex,
                clickable = match.winnerIndex == null && !match.isBye,
                onClick = { match.playerAIndex?.let(onPick) }
            )
            PlayerRow(
                name = match.playerBIndex?.let { players.getOrNull(it) },
                isWinner = match.winnerIndex != null && match.winnerIndex == match.playerBIndex,
                clickable = match.winnerIndex == null && !match.isBye,
                onClick = { match.playerBIndex?.let(onPick) }
            )
            // Un "bye" n'a pas de résultat à corriger : seul un vrai match déjà joué est annulable.
            if (match.winnerIndex != null && !match.isBye) {
                val a = match.playerAIndex?.let { players.getOrNull(it) } ?: "?"
                val b = match.playerBIndex?.let { players.getOrNull(it) } ?: "?"
                val fixDescription = stringResource(R.string.bracket_fix_description, a, b)
                TextButton(
                    onClick = onCorrect,
                    // « Corriger » seul est ambigu quand TalkBack parcourt vingt matchs.
                    modifier = Modifier.semantics { contentDescription = fixDescription }
                ) { Text(stringResource(R.string.common_fix), style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
private fun PlayerRow(
    name: String?,
    isWinner: Boolean,
    clickable: Boolean,
    onClick: () -> Unit
) {
    val winnerState = stringResource(R.string.bracket_winner)
    val pickLabel = stringResource(R.string.bracket_pick_winner, name ?: "")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isWinner) winnerContainer() else Color.Transparent)
            .semantics(mergeDescendants = true) { if (isWinner) stateDescription = winnerState }
            .then(
                if (clickable) {
                    Modifier.clickable(onClickLabel = pickLabel, role = Role.Button) { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = name ?: "—",
            fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
            color = if (name == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Poules : classement en haut, puis les matchs regroupés par journée (deux cartes par ligne). */
@Composable
private fun RoundRobinBody(
    players: List<String>,
    rounds: List<List<TournamentMatch>>,
    standings: List<PouleRow>,
    onPick: (roundIndex: Int, matchIndex: Int, winner: Int) -> Unit,
    onCorrect: (roundIndex: Int, matchIndex: Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StandingsCard(players = players, rows = standings)
        rounds.forEachIndexed { roundIndex, round ->
            Text(
                text = stringResource(R.string.bracket_day, roundIndex + 1),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            // Nombre impair de participants : celui qui n'a pas de match cette journée se repose.
            val resting = players.indices.filter { p -> round.none { it.playerAIndex == p || it.playerBIndex == p } }
            if (resting.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.bracket_resting, resting.joinToString(", ") { players[it] }),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            round.withIndex().chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { (matchIndex, match) ->
                        MatchCard(
                            match = match,
                            players = players,
                            onPick = { winner -> onPick(roundIndex, matchIndex, winner) },
                            onCorrect = { onCorrect(roundIndex, matchIndex) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Classement provisoire (ou final) d'une poule : rang, nom, victoires / matchs joués. */
@Composable
private fun StandingsCard(players: List<String>, rows: List<PouleRow>) {
    val context = LocalContext.current
    // Tant qu'aucun match n'est joué, tout le monde est à égalité : pas de rang à afficher.
    val started = rows.any { it.played > 0 }
    Card(
        colors = CardDefaults.cardColors(containerColor = cardSurface()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.standings_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            rows.forEach { row ->
                val name = players.getOrNull(row.playerIndex) ?: "?"
                val description = if (started) {
                    stringResource(
                        R.string.bracket_rank_description, row.rank, name,
                        context.quantity(R.plurals.victories_count, row.wins),
                        context.quantity(R.plurals.matches_count, row.played)
                    )
                } else {
                    name
                }
                Row(
                    // Une seule phrase par ligne pour TalkBack plutôt que trois textes séparés.
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics { contentDescription = description },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (started) "${row.rank}" else "–",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(28.dp)
                    )
                    Text(text = name, modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.bracket_wins_played, row.wins, row.played),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Bandeau de fin de poules : champion(s) et places suivantes (les ex æquo sont signalés). */
@Composable
private fun PouleFinalCard(players: List<String>, rows: List<PouleRow>, onShare: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = highlightContainer(), contentColor = highlightContent()),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.final_ranking), style = MaterialTheme.typography.labelMedium)
            rows.filter { it.rank <= 3 }.forEach { row ->
                val name = players.getOrNull(row.playerIndex) ?: "?"
                val tied = rows.count { it.rank == row.rank } > 1
                val text = stringResource(
                    when (row.rank) {
                        1 -> if (tied) R.string.podium_tied_first else R.string.podium_champion
                        2 -> if (tied) R.string.podium_second_tied else R.string.podium_second
                        else -> if (tied) R.string.podium_third_tied else R.string.podium_third
                    },
                    name
                )
                Text(
                    text = text,
                    fontWeight = if (row.rank == 1) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            TextButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" " + stringResource(R.string.share_ranking))
            }
        }
    }
}
