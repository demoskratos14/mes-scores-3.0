package com.aventure.messcores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Le Simon chacun sur son téléphone (voir [SimonOnline]) : la table où les joueurs se rassemblent, puis le jeu dans le mode
 * choisi par l'organisateur.
 *
 * - « Chacun sa série » : tous les téléphones montrent en même temps l'écran de jeu de la série de leur joueur (sa propre
 *   suite, voir [SimonPlayfield]) ; en attendant les autres, on suit qui a fini et avec quel score.
 * - « Chacun son tour » : le téléphone du joueur dont c'est le tour montre la suite commune et la fait reproduire (voir
 *   [SimonTurnPlayfield]) ; les autres suivent la partie et attendent leur tour.
 */
@Composable
internal fun OnlineSimon(online: SimonOnline, onQuit: () -> Unit, onClose: () -> Unit) {
    if (online.notice == SimonNotice.HOST_LEFT) {
        SimonCard {
            Text(
                text = stringResource(R.string.simon_online_host_left),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_close)) }
        }
        return
    }
    if (!online.ready) {
        SimonCard {
            if (online.notice == SimonNotice.PLAYER_LEFT || online.notice == SimonNotice.GAME_CANCELLED) {
                Text(
                    text = stringResource(R.string.simon_online_notice_left),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                text = stringResource(R.string.simon_online_waiting),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.simon_online_leave))
            }
        }
        return
    }
    KeepScreenOn()

    // C'est à moi de jouer : l'écran de jeu prend toute la place. `key` repart d'un écran neuf à chaque étape.
    if (online.myPlay) {
        val me = online.lobby.getOrElse(online.seat) { (online.seat + 1).toString() }
        when (online.mode) {
            SimonMode.SERIES -> key(online.round) {
                SimonPlayfield(name = me, onFinished = { score -> online.finishSeries(score) })
            }
            SimonMode.TURNS -> key(online.round) {
                SimonTurnPlayfield(name = me, sequence = online.sequence, onResult = { reproduced -> online.reportTurn(reproduced) })
            }
            null -> Unit
        }
        return
    }

    SimonCard {
        when (online.notice) {
            SimonNotice.PLAYER_LEFT -> Text(
                text = stringResource(R.string.simon_online_notice_left_some),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            SimonNotice.GAME_CANCELLED -> Text(
                text = stringResource(R.string.simon_online_notice_cancelled),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            else -> Unit
        }
        when {
            online.mode == null -> OnlineGathering(online)
            online.over -> OnlineResults(online)
            online.mode == SimonMode.SERIES -> OnlineSeriesProgress(online)
            else -> OnlineTurnsProgress(online)
        }
        OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.simon_online_leave))
        }
    }
}

private fun SimonOnline.nameOf(place: Int): String = lobby.getOrElse(place) { (place + 1).toString() }

/** Les joueurs se rassemblent : l'organisateur choisit le mode de jeu, les invités attendent. */
@Composable
private fun OnlineGathering(online: SimonOnline) {
    Text(
        text = stringResource(R.string.simon_table_players, online.lobby.joinToString(", ")),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    if (!online.isHost) {
        Text(
            text = stringResource(R.string.simon_gather_guest),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        return
    }
    Text(
        text = stringResource(R.string.simon_gather_host),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center
    )
    Button(onClick = { online.start(SimonMode.SERIES) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.simon_mode_series))
    }
    Text(
        text = stringResource(R.string.simon_online_series_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Button(onClick = { online.start(SimonMode.TURNS) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.simon_mode_turns))
    }
    Text(
        text = stringResource(R.string.simon_mode_turns_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

/** « Chacun sa série » : ma série est finie, on suit les autres. */
@Composable
private fun OnlineSeriesProgress(online: SimonOnline) {
    val mine = online.scores.getOrElse(online.seat) { SimonOnline.PENDING }
    Text(
        text = stringResource(R.string.simon_online_series_waiting, mine.coerceAtLeast(0)),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        online.lobby.indices.forEach { place ->
            val score = online.scores.getOrElse(place) { SimonOnline.PENDING }
            Text(
                text = if (score >= 0) stringResource(R.string.simon_online_series_done, online.nameOf(place), score)
                else stringResource(R.string.simon_online_series_playing, online.nameOf(place)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** « Chacun son tour » : ce n'est pas à moi de jouer, on suit la partie. */
@Composable
private fun OnlineTurnsProgress(online: SimonOnline) {
    val game = online.turns()
    Text(
        text = stringResource(R.string.simon_ready_title, online.nameOf(online.current)),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
    Text(
        text = stringResource(R.string.simon_online_turn_watch, online.sequence.size),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    if (online.lastOut >= 0) {
        Text(
            text = stringResource(R.string.simon_turns_out, online.nameOf(online.lastOut)),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
    }
    if (online.seat in online.eliminated) {
        Text(
            text = stringResource(R.string.simon_online_eliminated),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
    }
    Text(
        text = stringResource(R.string.simon_turns_alive, game.alive.joinToString(", ") { online.nameOf(it) }),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

/** La partie est finie : le gagnant et le classement ; l'organisateur peut relancer. */
@Composable
private fun OnlineResults(online: SimonOnline) {
    when (online.mode) {
        SimonMode.SERIES -> {
            val game = online.series()
            val winners = game.winners
            SimonResultsHeader(
                when {
                    winners.isEmpty() -> stringResource(R.string.simon_nobody)
                    winners.size == 1 -> stringResource(R.string.simon_winner, online.nameOf(winners.single()), game.best)
                    else -> stringResource(R.string.simon_tie, winners.joinToString(", ") { online.nameOf(it) }, game.best)
                }
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                game.ranking().forEachIndexed { index, place ->
                    Text(
                        text = stringResource(R.string.simon_rank_entry, index + 1, online.nameOf(place), game.scores[place]),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        SimonMode.TURNS -> {
            val game = online.turns()
            SimonResultsHeader(stringResource(R.string.simon_turns_winner, online.nameOf(game.winner ?: 0)))
            Text(
                text = stringResource(R.string.simon_turns_reached, game.reached),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                game.ranking().forEachIndexed { index, place ->
                    Text(
                        text = stringResource(R.string.simon_turns_rank_entry, index + 1, online.nameOf(place)),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        null -> Unit
    }
    if (online.isHost) {
        Button(onClick = { online.newGame() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.simon_new_game))
        }
    } else {
        Text(
            text = stringResource(R.string.simon_online_wait_host),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
