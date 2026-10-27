package com.aventure.messcores

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Ce qui distingue un jeu à deux joueurs au tour par tour d'un autre pour [DuoGameScreen] : son nom, ses règles, sa
 * séance de jeu, la couleur de chaque joueur et le dessin du plateau. Tout le reste (score, tour, manches, partie à
 * deux téléphones) est commun au Gomoku et à la Pipopipette.
 */
internal class DuoGameSpec<M : DuoMatch<M>>(
    /** Identifiant du jeu parmi les jeux prédéfinis (pour le bouton « Règles »). */
    val rulesId: String,
    @StringRes val title: Int,
    val initial: () -> M,
    val decode: (String) -> M?,
    /** Couleur du joueur 1 ou 2, dans le thème courant. */
    val colorOf: (ColorScheme, Int) -> Color,
    /** Le plateau : [enabled] dit si on peut jouer ; [onPlay] reçoit le coup choisi (voir [DuoMatch.play]). */
    val board: @Composable (match: M, enabled: Boolean, onPlay: (Int) -> Unit) -> Unit
)

/**
 * Jeu à deux joueurs : sur un seul téléphone (on se passe l'appareil) ou chacun sur le sien (voir [DuoOnline]). Les
 * manches s'enchaînent, le premier à jouer change à chaque manche, et le score de la séance (victoires de chacun,
 * égalités) reste affiché. Le premier joueur est l'organisateur, le deuxième l'invité.
 */
@Composable
internal fun <M : DuoMatch<M>> DuoGameScreen(
    spec: DuoGameSpec<M>,
    players: List<String>,
    online: DuoOnline<M>,
    onExit: () -> Unit,
    onHostOnline: () -> Unit
) {
    var showQuit by rememberSaveable { mutableStateOf(false) }
    // Hors partie à deux téléphones, le retour système quitte simplement l'écran.
    BackHandler(enabled = online.active) {
        if (online.notice == DuoNotice.HOST_LEFT) {
            online.leave()
            onExit()
        } else {
            showQuit = true
        }
    }

    if (online.active) {
        OnlineDuo(spec = spec, online = online, onQuit = { showQuit = true }, onClose = {
            online.leave()
            onExit()
        })
    } else {
        LocalDuo(spec = spec, players = players, onPlayOnline = onHostOnline)
    }

    if (showQuit && online.active) {
        AlertDialog(
            onDismissRequest = { showQuit = false },
            title = { Text(stringResource(R.string.duo_quit_title)) },
            text = { Text(stringResource(R.string.duo_quit_message)) },
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
private fun <M : DuoMatch<M>> LocalDuo(spec: DuoGameSpec<M>, players: List<String>, onPlayOnline: () -> Unit) {
    val defaultA = stringResource(R.string.duo_player_default, 1)
    val defaultB = stringResource(R.string.duo_player_default, 2)
    val nameA = players.getOrElse(0) { defaultA }
    val nameB = players.getOrElse(1) { defaultB }

    // Toute la séance est gardée quand l'écran tourne, sous forme de texte (voir DuoMatch.encode).
    val startText = remember(spec) { spec.initial().encode() }
    var text by rememberSaveable { mutableStateOf(startText) }
    val match = remember(text) { spec.decode(text) ?: spec.initial() }
    // Proposé tant que rien n'a été joué : passer à deux téléphones en pleine séance perdrait le score.
    val fresh = text == startText

    fun play(move: Int) {
        val next = match.play(move, match.current) ?: return
        text = next.encode()
    }

    DuoCard {
        DuoTitle(spec)
        DuoScoreRow(spec, nameA, nameB, match, mine = 0)
        Text(
            text = when {
                match.winner != 0 -> stringResource(R.string.duo_win, if (match.winner == 1) nameA else nameB)
                match.isOver -> stringResource(R.string.duo_draw)
                else -> stringResource(R.string.duo_turn, if (match.current == 1) nameA else nameB)
            },
            style = MaterialTheme.typography.titleMedium
        )
        spec.board(match, !match.isOver, ::play)
        if (match.isOver) {
            Button(
                onClick = { match.newRound()?.let { text = it.encode() } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.duo_new_round))
            }
        }
        if (fresh) {
            OutlinedButton(onClick = onPlayOnline, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.duo_online_button))
            }
        }
    }
}

// ---------- Chacun sur son téléphone ----------

/** La partie à deux téléphones : attente de l'autre joueur, puis le plateau et le score de la séance. */
@Composable
private fun <M : DuoMatch<M>> OnlineDuo(spec: DuoGameSpec<M>, online: DuoOnline<M>, onQuit: () -> Unit, onClose: () -> Unit) {
    if (online.notice == DuoNotice.HOST_LEFT) {
        DuoCard {
            DuoTitle(spec)
            Text(text = stringResource(R.string.duo_online_host_left), style = MaterialTheme.typography.titleMedium)
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_close)) }
        }
        return
    }
    if (!online.ready) {
        DuoCard {
            DuoTitle(spec)
            if (online.notice == DuoNotice.PLAYER_LEFT) {
                Text(
                    text = stringResource(R.string.duo_online_notice_left),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(text = stringResource(R.string.duo_online_waiting), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.duo_online_leave))
            }
        }
        return
    }

    val match = online.match
    val defaultA = stringResource(R.string.duo_player_default, 1)
    val defaultB = stringResource(R.string.duo_player_default, 2)
    val nameA = online.lobby.getOrElse(0) { defaultA }
    val nameB = online.lobby.getOrElse(1) { defaultB }
    val me = online.myPlayer
    val opponent = if (me == 1) nameB else nameA

    DuoCard {
        DuoTitle(spec)
        Text(
            text = stringResource(R.string.duo_online_opponent, opponent),
            style = MaterialTheme.typography.bodyLarge
        )
        DuoScoreRow(spec, nameA, nameB, match, mine = me)
        Text(
            text = when {
                match.winner == me -> stringResource(R.string.duo_online_you_win)
                match.winner != 0 -> stringResource(R.string.duo_win, opponent)
                match.isOver -> stringResource(R.string.duo_draw)
                match.current == me -> stringResource(R.string.duo_online_your_turn)
                else -> stringResource(R.string.duo_turn, opponent)
            },
            style = MaterialTheme.typography.titleMedium
        )
        spec.board(match, online.myTurn, online::play)
        if (match.isOver) {
            Button(onClick = { online.newRound() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.duo_new_round))
            }
        }
        OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.duo_online_leave))
        }
    }
}

// ---------- Éléments communs ----------

/** La carte centrée de l'écran, qui défile si le téléphone est petit ou tourné. Peu de marge : le plateau doit être grand. */
@Composable
private fun DuoCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = cardSurface()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
private fun <M : DuoMatch<M>> DuoTitle(spec: DuoGameSpec<M>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(spec.title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f, fill = false).semantics { heading() }
        )
        Spacer(modifier = Modifier.width(12.dp))
        // Sur la carte claire, le bouton prend la couleur principale (il est blanc sur l'en-tête des autres parties).
        RulesButton(
            gameId = spec.rulesId,
            gameName = stringResource(spec.title),
            contentColor = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Le score de la séance : chaque joueur avec sa couleur et ses victoires (celui à qui c'est de jouer est mis en
 * valeur), puis les égalités. [mine] : ma place (1 ou 2) quand on joue chacun sur son téléphone, 0 sinon.
 */
@Composable
private fun <M : DuoMatch<M>> DuoScoreRow(spec: DuoGameSpec<M>, nameA: String, nameB: String, match: M, mine: Int) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DuoPlayerTag(
                name = if (mine == 1) stringResource(R.string.duo_you_tag, nameA) else nameA,
                wins = match.winsA,
                color = spec.colorOf(scheme, 1),
                active = !match.isOver && match.current == 1,
                modifier = Modifier.weight(1f)
            )
            DuoPlayerTag(
                name = if (mine == 2) stringResource(R.string.duo_you_tag, nameB) else nameB,
                wins = match.winsB,
                color = spec.colorOf(scheme, 2),
                active = !match.isOver && match.current == 2,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = stringResource(R.string.duo_draws, match.draws),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DuoPlayerTag(name: String, wins: Int, color: Color, active: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) scheme.primaryContainer else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(color)
                // Le contour garde la pierre blanche visible sur une carte claire.
                .border(1.dp, scheme.onSurface.copy(alpha = 0.6f), CircleShape)
        )
        Text(
            text = stringResource(R.string.duo_player_score, name, wins),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
