package com.aventure.messcores

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Les Ronds chacun sur son téléphone (voir [CirclesOnline]) : la table où les joueurs se rassemblent, puis le jeu au
 * niveau choisi par l'organisateur.
 *
 * Tous les téléphones montrent en même temps l'écran de jeu de la série de leur joueur (voir [CirclesPlayfield]) ; les
 * ronds sont tirés au hasard sur chaque téléphone. Quand ma série est finie, on suit qui a fini et avec quelle série,
 * puis le classement s'affiche quand tout le monde a fini.
 */
@Composable
internal fun OnlineCircles(online: CirclesOnline, onQuit: () -> Unit, onClose: () -> Unit) {
    // Ma dernière série et, si elle s'est arrêtée sur une tête de mort, de quoi le dire : gardés après la fin de la série.
    var lastStreak by rememberSaveable { mutableIntStateOf(0) }
    var lastSkull by rememberSaveable { mutableStateOf(false) }

    if (online.notice == CirclesNotice.HOST_LEFT) {
        CirclesCard {
            Text(
                text = stringResource(R.string.circles_online_host_left),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_close)) }
        }
        return
    }
    if (!online.ready) {
        CirclesCard {
            if (online.notice == CirclesNotice.PLAYER_LEFT) {
                Text(
                    text = stringResource(R.string.circles_online_notice_left),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                text = stringResource(R.string.circles_online_waiting),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.circles_online_leave))
            }
        }
        return
    }
    KeepScreenOn()

    // C'est à moi de jouer : l'écran de jeu prend toute la place. `key` repart d'un écran neuf à chaque partie.
    if (online.myPlay) {
        key(online.round) {
            OnlineRun(
                name = online.nameOf(online.seat),
                level = online.level ?: CirclesLevel.NORMAL,
                onFinished = { streak, hitSkull ->
                    lastStreak = streak
                    lastSkull = hitSkull
                    online.finishRun(streak)
                }
            )
        }
        return
    }

    CirclesCard {
        if (online.notice == CirclesNotice.PLAYER_LEFT) {
            Text(
                text = stringResource(R.string.circles_online_notice_left_some),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
        when {
            online.level == null -> OnlineGathering(online)
            online.over -> OnlineResults(online)
            else -> OnlineProgress(online, lastStreak = lastStreak, lastSkull = lastSkull)
        }
        OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.circles_online_leave))
        }
    }
}

private fun CirclesOnline.nameOf(place: Int): String = lobby.getOrElse(place) { (place + 1).toString() }

/**
 * Ma série : d'abord la carte de départ (chacun lance sa série quand il est prêt : seule compte la longueur de la série,
 * pas l'instant où elle commence), puis le plateau. Un nouvel écran (un nouveau `key`) repart de la carte de départ.
 */
@Composable
private fun OnlineRun(name: String, level: CirclesLevel, onFinished: (streak: Int, hitSkull: Boolean) -> Unit) {
    var started by rememberSaveable { mutableStateOf(false) }
    if (started) {
        CirclesPlayfield(name = name, level = level, onFinished = onFinished)
        return
    }
    CirclesCard {
        Text(
            text = stringResource(R.string.circles_ready_title, name),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.circles_ready_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.circles_online_level, stringResource(levelName(level))),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        if (level == CirclesLevel.HARD) {
            Text(
                text = stringResource(R.string.circles_hard_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Button(onClick = { started = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.circles_go))
        }
    }
}

/** Les joueurs se rassemblent : l'organisateur choisit le niveau et lance la partie, les invités attendent. */
@Composable
private fun OnlineGathering(online: CirclesOnline) {
    Text(
        text = stringResource(R.string.circles_table_players, online.lobby.joinToString(", ")),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    if (!online.isHost) {
        Text(
            text = stringResource(R.string.circles_gather_guest),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        return
    }
    val context = LocalContext.current.applicationContext
    var levelIndex by rememberSaveable { mutableIntStateOf(CirclesPreferences.load(context)) }
    val level = CirclesLevel.fromIndex(levelIndex)
    Text(
        text = stringResource(R.string.circles_gather_host),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center
    )
    Text(
        text = stringResource(R.string.circles_level_title),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.semantics { heading() }
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CirclesLevel.entries.forEach { option ->
            FilterChip(
                selected = option == level,
                onClick = {
                    levelIndex = option.ordinal
                    CirclesPreferences.save(context, option.ordinal)
                },
                label = { Text(stringResource(levelName(option))) }
            )
        }
    }
    if (level == CirclesLevel.HARD) {
        Text(
            text = stringResource(R.string.circles_hard_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
    Text(
        text = stringResource(R.string.circles_online_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Button(onClick = { online.start(level) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.circles_online_start))
    }
}

/** Ma série est finie : on suit les autres joueurs, qui finissent la leur. */
@Composable
private fun OnlineProgress(online: CirclesOnline, lastStreak: Int, lastSkull: Boolean) {
    if (lastSkull) {
        Text(
            text = stringResource(R.string.circles_skull_hit, online.nameOf(online.seat), lastStreak),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
    }
    Text(
        text = stringResource(R.string.circles_online_series_waiting, lastStreak),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        online.lobby.indices.forEach { place ->
            // Ma propre série est connue ici tout de suite, avant que l'organisateur ne la renvoie dans l'état de la table.
            val streak = if (place == online.seat && online.reported) lastStreak
            else online.scores.getOrElse(place) { CirclesOnline.PENDING }
            Text(
                text = if (streak >= 0) stringResource(R.string.circles_online_series_done, online.nameOf(place), streak)
                else stringResource(R.string.circles_online_series_playing, online.nameOf(place)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** La partie est finie : le gagnant (ou les ex æquo) et le classement ; l'organisateur peut relancer. */
@Composable
private fun OnlineResults(online: CirclesOnline) {
    val game = online.game()
    val winners = game.winners
    val headline = when {
        winners.isEmpty() -> stringResource(R.string.circles_nobody)
        winners.size == 1 -> stringResource(R.string.circles_winner, online.nameOf(winners.single()), game.best)
        else -> stringResource(R.string.circles_tie, winners.joinToString(", ") { online.nameOf(it) }, game.best)
    }
    Text(
        text = stringResource(R.string.circles_results_title),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() }
    )
    Text(
        text = headline,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = highlightContent(),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(highlightContainer())
            .padding(12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        game.ranking().forEachIndexed { index, place ->
            Text(
                text = stringResource(R.string.circles_rank_entry, index + 1, online.nameOf(place), game.streaks[place]),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
    if (online.isHost) {
        Button(onClick = { online.newGame() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.circles_new_game))
        }
    } else {
        Text(
            text = stringResource(R.string.circles_online_wait_host),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
