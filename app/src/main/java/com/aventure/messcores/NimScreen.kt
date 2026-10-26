package com.aventure.messcores

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Jeu des allumettes (Nim) à deux joueurs : sur un seul téléphone (on se passe l'appareil) ou chacun sur le sien
 * (voir [NimOnline]). On retire à tour de rôle 1, 2 ou 3 allumettes et celui qui prend la dernière perd. Les manches
 * s'enchaînent, le premier à jouer change à chaque manche, et le score de la séance reste affiché.
 */
@Composable
internal fun NimScreen(
    players: List<String>,
    online: NimOnline,
    onExit: () -> Unit,
    onHostOnline: () -> Unit
) {
    var showQuit by rememberSaveable { mutableStateOf(false) }
    // Hors partie à deux téléphones, le retour système quitte simplement l'écran.
    BackHandler(enabled = online.active) {
        if (online.notice == NimNotice.HOST_LEFT) {
            online.leave()
            onExit()
        } else {
            showQuit = true
        }
    }

    if (online.active) {
        OnlineNim(online = online, onQuit = { showQuit = true }, onClose = {
            online.leave()
            onExit()
        })
    } else {
        LocalNim(players = players, onPlayOnline = onHostOnline)
    }

    if (showQuit && online.active) {
        AlertDialog(
            onDismissRequest = { showQuit = false },
            title = { Text(stringResource(R.string.nim_quit_title)) },
            text = { Text(stringResource(R.string.nim_quit_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showQuit = false
                    online.leave()
                    onExit()
                }) {
                    Text(stringResource(R.string.mm_quit_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuit = false }) { Text(stringResource(R.string.quit_continue)) }
            }
        )
    }
}

// ---------- Un seul téléphone ----------

@Composable
private fun LocalNim(players: List<String>, onPlayOnline: () -> Unit) {
    val name1 = players.getOrElse(0) { "1" }
    val name2 = players.getOrElse(1) { "2" }

    // Tout est gardé quand l'écran tourne.
    var remaining by rememberSaveable { mutableIntStateOf(Nim.START) }
    var turn by rememberSaveable { mutableIntStateOf(1) }
    var starter by rememberSaveable { mutableIntStateOf(0) }
    var loser by rememberSaveable { mutableIntStateOf(0) }
    var wins1 by rememberSaveable { mutableIntStateOf(0) }
    var wins2 by rememberSaveable { mutableIntStateOf(0) }

    val match = NimMatch(remaining, turn, starter, loser, wins1, wins2)
    // Proposé tant que rien n'a été joué : passer à deux téléphones en pleine séance perdrait le score.
    val fresh = match == NimMatch()

    fun applyMatch(next: NimMatch) {
        remaining = next.remaining
        turn = next.turn
        starter = next.starter
        loser = next.loser
        wins1 = next.wins1
        wins2 = next.wins2
    }

    MatchCard {
        NimTitle()
        Text(
            text = stringResource(R.string.nim_score, name1, wins1, name2, wins2),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = if (match.isOver) {
                stringResource(R.string.nim_over, if (match.loser == 1) name1 else name2, if (match.winner == 1) name1 else name2)
            } else {
                stringResource(R.string.nim_turn, if (match.turn == 1) name1 else name2)
            },
            style = MaterialTheme.typography.titleMedium
        )
        Matches(remaining = match.remaining)
        if (match.isOver) {
            Button(onClick = { match.newRound()?.let(::applyMatch) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.nim_new_round))
            }
        } else {
            TakeButtons(remaining = match.remaining, enabled = true, onTake = { count -> match.play(count, match.turn)?.let(::applyMatch) })
        }
        if (fresh) {
            OutlinedButton(onClick = onPlayOnline, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.nim_online_button))
            }
        }
    }
}

// ---------- Chacun sur son téléphone ----------

/** La partie à deux téléphones : attente de l'autre joueur, puis le tas d'allumettes et le score de la séance. */
@Composable
private fun OnlineNim(online: NimOnline, onQuit: () -> Unit, onClose: () -> Unit) {
    if (online.notice == NimNotice.HOST_LEFT) {
        MatchCard {
            NimTitle()
            Text(text = stringResource(R.string.nim_online_host_left), style = MaterialTheme.typography.titleMedium)
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_close)) }
        }
        return
    }
    if (!online.ready) {
        MatchCard {
            NimTitle()
            if (online.notice == NimNotice.PLAYER_LEFT) {
                Text(
                    text = stringResource(R.string.nim_online_notice_left),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(text = stringResource(R.string.nim_online_waiting), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.nim_online_leave))
            }
        }
        return
    }

    val match = online.match
    val name1 = online.lobby.getOrElse(0) { "1" }
    val name2 = online.lobby.getOrElse(1) { "2" }
    val me = online.myNumber
    val opponent = if (me == 1) name2 else name1

    MatchCard {
        NimTitle()
        Text(text = stringResource(R.string.nim_online_vs, opponent), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(R.string.nim_score, name1, match.wins1, name2, match.wins2),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = when {
                match.winner == me -> stringResource(R.string.nim_online_you_win, opponent)
                match.isOver -> stringResource(R.string.nim_online_you_lose)
                match.turn == me -> stringResource(R.string.nim_online_your_turn)
                else -> stringResource(R.string.nim_turn, opponent)
            },
            style = MaterialTheme.typography.titleMedium
        )
        Matches(remaining = match.remaining)
        if (match.isOver) {
            Button(onClick = { online.newRound() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.nim_new_round))
            }
        } else {
            TakeButtons(remaining = match.remaining, enabled = online.myTurn, onTake = online::take)
        }
        OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.nim_online_leave))
        }
    }
}

// ---------- Éléments communs ----------

/** La carte centrée de l'écran, qui défile si le téléphone est petit ou tourné. */
@Composable
private fun MatchCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = cardSurface()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content
            )
        }
    }
}

@Composable
private fun NimTitle() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.game_nim),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f, fill = false).semantics { heading() }
        )
        Spacer(modifier = Modifier.width(12.dp))
        // Sur la carte claire, le bouton prend la couleur principale (il est blanc sur l'en-tête des autres parties).
        RulesButton(
            gameId = NIM_ID,
            gameName = stringResource(R.string.game_nim),
            contentColor = MaterialTheme.colorScheme.primary
        )
    }
}

/** Les boutons « 1 », « 2 » et « 3 » : on retire autant d'allumettes ; ceux qui dépassent le tas restent grisés. */
@Composable
private fun TakeButtons(remaining: Int, enabled: Boolean, onTake: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.nim_take_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (count in 1..Nim.MAX_TAKE) {
                val description = pluralStringResource(R.plurals.nim_take_description, count, count)
                Button(
                    onClick = { onTake(count) },
                    enabled = enabled && Nim.isLegal(remaining, count),
                    modifier = Modifier.weight(1f).height(64.dp).semantics { contentDescription = description }
                ) {
                    Text(text = count.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Le tas d'allumettes, rangées de [PER_ROW] : les allumettes restantes sont dessinées pleines, celles déjà prises
 * en pâle à leur place (le tas ne se redessine pas à chaque coup). Lu par TalkBack comme le nombre d'allumettes restantes.
 */
@Composable
private fun Matches(remaining: Int) {
    val rows = (Nim.START + PER_ROW - 1) / PER_ROW
    val description = pluralStringResource(R.plurals.nim_remaining, remaining, remaining)
    val wood = Color(0xFFD9B27C)
    val head = MaterialTheme.colorScheme.error
    val taken = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(PER_ROW / (rows * CELL_RATIO))
            .semantics { contentDescription = description }
    ) {
        val cellWidth = size.width / PER_ROW
        val cellHeight = cellWidth * CELL_RATIO
        val stickWidth = cellWidth * 0.2f
        val headSize = stickWidth * 1.9f
        for (index in 0 until Nim.START) {
            val left = (index % PER_ROW) * cellWidth + (cellWidth - stickWidth) / 2f
            val top = (index / PER_ROW) * cellHeight + cellHeight * 0.06f
            val present = index < remaining
            val radius = CornerRadius(stickWidth / 2f)
            // Le bois, puis la tête colorée en haut.
            drawRoundRect(
                color = if (present) wood else taken,
                topLeft = Offset(left, top + headSize / 2f),
                size = Size(stickWidth, cellHeight * 0.82f - headSize / 2f),
                cornerRadius = radius
            )
            drawRoundRect(
                color = if (present) head else taken,
                topLeft = Offset(left + stickWidth / 2f - headSize / 2f, top),
                size = Size(headSize, headSize * 1.15f),
                cornerRadius = CornerRadius(headSize / 2f)
            )
        }
    }
}

private const val PER_ROW = 7

/** Hauteur d'une case de la grille d'allumettes, en largeurs de case. */
private const val CELL_RATIO = 1.7f
