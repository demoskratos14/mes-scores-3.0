package com.aventure.messcores

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Les étapes d'une manche.
private const val PHASE_READY = 0 // on attend que quelqu'un lance la manche
private const val PHASE_WAITING = 1 // la zone est rouge : toucher maintenant est un faux départ
private const val PHASE_GO = 2 // le signal est donné : le premier qui touche gagne
private const val PHASE_DONE = 3 // la manche est jouée
private const val PHASE_LOSER = 4 // un seul téléphone : le vainqueur a touché, on note encore le temps du perdant

/**
 * Duel de réflexes à deux joueurs : sur un seul téléphone (voir [LocalReflex]) ou chacun sur le sien (voir [ReflexOnline]).
 */
@Composable
internal fun ReflexScreen(
    players: List<String>,
    online: ReflexOnline,
    onExit: () -> Unit,
    onHostOnline: () -> Unit
) {
    var showQuit by rememberSaveable { mutableStateOf(false) }
    // Hors partie à deux téléphones, le retour système quitte simplement l'écran.
    BackHandler(enabled = online.active) {
        if (online.notice == ReflexNotice.HOST_LEFT) {
            online.leave()
            onExit()
        } else {
            showQuit = true
        }
    }

    if (online.active) {
        OnlineReflex(online = online, onQuit = { showQuit = true }, onClose = {
            online.leave()
            onExit()
        })
    } else {
        LocalReflex(players = players, onPlayOnline = onHostOnline)
    }

    if (showQuit && online.active) {
        AlertDialog(
            onDismissRequest = { showQuit = false },
            title = { Text(stringResource(R.string.reflex_quit_title)) },
            text = { Text(stringResource(R.string.reflex_quit_message)) },
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

/**
 * Duel de réflexes à deux joueurs sur un seul téléphone, face à face : chacun a sa moitié d'écran (celle du haut est
 * retournée). Quand les zones passent du rouge au vert, le premier qui touche la sienne gagne la manche ; toucher
 * trop tôt la donne à l'autre. Le premier à [Reflex.TARGET] manches gagne la partie. Tout est gardé quand l'écran tourne.
 */
@Composable
private fun LocalReflex(players: List<String>, onPlayOnline: () -> Unit) {
    KeepScreenOn()
    val name1 = players.getOrElse(0) { "1" }
    val name2 = players.getOrElse(1) { "2" }
    val colorBlind = LocalColorBlindMode.current

    var wins1 by rememberSaveable { mutableIntStateOf(0) }
    var wins2 by rememberSaveable { mutableIntStateOf(0) }
    var lastWinner by rememberSaveable { mutableIntStateOf(0) }
    var lastReaction by rememberSaveable { mutableIntStateOf(0) }
    var lastLoserReaction by rememberSaveable { mutableIntStateOf(0) }
    var falseStart by rememberSaveable { mutableStateOf(false) }
    var totalMs1 by rememberSaveable { mutableIntStateOf(0) }
    var timedRounds1 by rememberSaveable { mutableIntStateOf(0) }
    var totalMs2 by rememberSaveable { mutableIntStateOf(0) }
    var timedRounds2 by rememberSaveable { mutableIntStateOf(0) }
    var phase by rememberSaveable { mutableIntStateOf(PHASE_READY) }
    var goAt by rememberSaveable { mutableLongStateOf(0L) }

    // La séance relue dans les états à chaque appel : un toucher n'est pas traité sur la photo prise à la dernière
    // recomposition. Sinon, quand le second joueur touche quelques millisecondes après le premier (avant que l'écran
    // ne soit redessiné), sa zone verrait encore la manche d'avant et son temps serait perdu.
    fun readMatch() = ReflexMatch(
        wins1 = wins1,
        wins2 = wins2,
        lastWinner = lastWinner,
        lastReactionMs = lastReaction,
        falseStart = falseStart,
        lastLoserReactionMs = lastLoserReaction,
        totalMs1 = totalMs1,
        timedRounds1 = timedRounds1,
        totalMs2 = totalMs2,
        timedRounds2 = timedRounds2
    )

    val match = readMatch()

    fun applyMatch(next: ReflexMatch, nextPhase: Int = PHASE_DONE) {
        wins1 = next.wins1
        wins2 = next.wins2
        lastWinner = next.lastWinner
        lastReaction = next.lastReactionMs
        lastLoserReaction = next.lastLoserReactionMs
        falseStart = next.falseStart
        totalMs1 = next.totalMs1
        timedRounds1 = next.timedRounds1
        totalMs2 = next.totalMs2
        timedRounds2 = next.timedRounds2
        phase = nextPhase
    }

    // Après une attente aléatoire, le signal est donné et le chrono part.
    LaunchedEffect(phase) {
        if (phase == PHASE_WAITING) {
            delay(Reflex.randomDelayMs())
            goAt = SystemClock.elapsedRealtime()
            phase = PHASE_GO
        }
        // Le vainqueur a touché : le perdant a encore un peu de temps pour toucher, et son temps est noté.
        if (phase == PHASE_LOSER) {
            val left = Reflex.RESPONSE_TIMEOUT_MS - (SystemClock.elapsedRealtime() - goAt)
            delay(left.coerceAtLeast(Reflex.LOSER_MIN_WINDOW_MS))
            phase = PHASE_DONE
        }
    }

    fun onTap(player: Int) {
        // Le moment du toucher est pris tout de suite, avant tout calcul.
        val reaction = SystemClock.elapsedRealtime() - goAt
        val current = readMatch()
        when (phase) {
            PHASE_WAITING -> current.tapTooEarly(player)?.let { applyMatch(it) }
            PHASE_GO -> current.tapAfterSignal(player, reaction)
                ?.let { applyMatch(it, nextPhase = PHASE_LOSER) }
            PHASE_LOSER -> if (player == 3 - current.lastWinner) {
                applyMatch(current.loserTapped(reaction))
            }
        }
    }

    fun nameOf(player: Int) = if (player == 1) name1 else name2

    val statusText = when {
        phase == PHASE_READY -> pluralStringResource(R.plurals.reflex_hint, Reflex.TARGET, Reflex.TARGET)
        phase == PHASE_WAITING -> stringResource(R.string.reflex_status_wait)
        phase == PHASE_GO -> stringResource(R.string.reflex_go)
        match.isOver -> stringResource(
            R.string.reflex_match_won,
            nameOf(match.winner),
            match.winsOf(match.winner),
            match.winsOf(3 - match.winner)
        )
        match.falseStart -> stringResource(R.string.reflex_false_start, nameOf(3 - match.lastWinner), nameOf(match.lastWinner))
        match.lastLoserReactionMs > 0 -> stringResource(
            R.string.reflex_round_won_both,
            nameOf(match.lastWinner),
            match.lastReactionMs,
            nameOf(3 - match.lastWinner),
            match.lastLoserReactionMs
        )
        else -> stringResource(R.string.reflex_round_won, nameOf(match.lastWinner), match.lastReactionMs)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Le joueur 2 est en haut, face à son adversaire : sa zone est retournée.
        ReflexZone(
            player = 2,
            name = name2,
            phase = phase,
            match = match,
            colorBlind = colorBlind,
            upsideDown = true,
            onTap = ::onTap,
            modifier = Modifier.weight(1f)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = cardSurface()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.game_reflex),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    RulesButton(
                        gameId = REFLEX_ID,
                        gameName = stringResource(R.string.game_reflex),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = stringResource(R.string.reflex_score, name1, wins1, name2, wins2),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ReflexAverages(
                    entries = listOf(name1 to match.averageMsOf(1), name2 to match.averageMsOf(2)),
                    show = match.hasAverages
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { liveRegion = LiveRegionMode.Polite }
                )
                when {
                    phase == PHASE_READY -> Button(
                        onClick = { phase = PHASE_WAITING },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.reflex_start)) }
                    phase == PHASE_DONE && match.isOver -> Button(
                        onClick = {
                            applyMatch(match.newMatch())
                            phase = PHASE_READY
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.reflex_new_match)) }
                    phase == PHASE_DONE -> Button(
                        onClick = { phase = PHASE_WAITING },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.reflex_next_round)) }
                }
                // Proposé tant que rien n'a été joué : passer à deux téléphones en pleine séance perdrait le score.
                if (phase == PHASE_READY && match == ReflexMatch()) {
                    OutlinedButton(onClick = onPlayOnline, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.reflex_online_button))
                    }
                }
            }
        }
        ReflexZone(
            player = 1,
            name = name1,
            phase = phase,
            match = match,
            colorBlind = colorBlind,
            upsideDown = false,
            onTap = ::onTap,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Le temps de réaction moyen de chaque joueur sur les manches de la partie : un couple (nom, moyenne) par joueur. N'apparaît
 * qu'à partir de deux temps pour un joueur ([show]) : avant, la moyenne ne dirait rien de plus que le temps de la
 * manche. Un joueur sans temps (que des faux départs, par exemple) montre un tiret.
 */
@Composable
private fun ReflexAverages(entries: List<Pair<String, Int?>>, show: Boolean) {
    if (!show) return
    val none = stringResource(R.string.reflex_avg_none)
    // map est « inline » : les ressources peuvent y être lues, contrairement à joinToString.
    val text = entries.map { (name, ms) ->
        stringResource(R.string.reflex_entry, name, ms?.let { stringResource(R.string.reflex_avg_value, it) } ?: none)
    }.joinToString(" · ")
    Text(
        text = stringResource(R.string.reflex_avg, text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

/**
 * La moitié d'écran d'un joueur. Elle réagit dès que le doigt se pose (pas au relâchement), pour ne pas perdre
 * quelques dizaines de millisecondes. Le texte dit la même chose que la couleur : rouge/vert ne portent pas seuls
 * le sens (et le mode daltonien les remplace par de l'orange et du bleu).
 */
@Composable
private fun ReflexZone(
    player: Int,
    name: String,
    phase: Int,
    match: ReflexMatch,
    colorBlind: Boolean,
    upsideDown: Boolean,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnTap by rememberUpdatedState(onTap)
    val stop = if (colorBlind) Color(0xFFD55E00) else Color(0xFFC62828)
    val go = if (colorBlind) Color(0xFF0072B2) else Color(0xFF2E7D32)
    val idle = Color(0xFF455A64)
    val won = (phase == PHASE_DONE || phase == PHASE_LOSER) && match.lastWinner == player
    // Le vainqueur a touché mais le perdant peut encore le faire : sa zone reste verte avec « TOUCHEZ ! ».
    val loserStillCanTap = phase == PHASE_LOSER && !won

    val background = when (phase) {
        PHASE_WAITING -> stop
        PHASE_GO -> go
        PHASE_LOSER -> go
        PHASE_DONE -> if (won) go else stop
        else -> idle
    }
    val label = when {
        phase == PHASE_READY -> stringResource(R.string.reflex_zone_ready)
        phase == PHASE_WAITING -> stringResource(R.string.reflex_wait)
        phase == PHASE_GO || loserStillCanTap -> stringResource(R.string.reflex_go)
        won && match.lastReactionMs > 0 -> stringResource(R.string.reflex_zone_win_ms, match.lastReactionMs)
        won -> stringResource(R.string.reflex_zone_win)
        match.falseStart -> stringResource(R.string.reflex_zone_early)
        match.lastLoserReactionMs > 0 -> stringResource(R.string.reflex_zone_lose_ms, match.lastLoserReactionMs)
        else -> stringResource(R.string.reflex_zone_lose)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    currentOnTap(player)
                    waitForUpOrCancellation()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(16.dp).then(if (upsideDown) Modifier.rotate(180f) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ---------- Chacun sur son téléphone ----------

/**
 * La partie chacun sur son téléphone, de deux à huit joueurs : attente des autres joueurs, rassemblement à la table, puis
 * une grande zone à toucher. Ce téléphone mesure lui-même le temps de réaction de son joueur depuis son propre signal ;
 * l'organisateur compare les temps de tout le monde.
 */
@Composable
private fun OnlineReflex(online: ReflexOnline, onQuit: () -> Unit, onClose: () -> Unit) {
    if (online.notice == ReflexNotice.HOST_LEFT) {
        InfoCard {
            Text(text = stringResource(R.string.game_reflex), style = MaterialTheme.typography.headlineMedium)
            Text(text = stringResource(R.string.reflex_online_host_left), style = MaterialTheme.typography.titleMedium)
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_close)) }
        }
        return
    }
    if (!online.ready) {
        InfoCard {
            Text(text = stringResource(R.string.game_reflex), style = MaterialTheme.typography.headlineMedium)
            if (online.notice == ReflexNotice.PLAYER_LEFT) {
                Text(
                    text = stringResource(R.string.reflex_online_notice_left),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(text = stringResource(R.string.reflex_online_waiting), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reflex_online_leave))
            }
        }
        return
    }
    KeepScreenOn()

    val match = online.match
    val me = online.seat
    val names = online.lobby
    fun nameOf(seat: Int) = names.getOrElse(seat) { (seat + 1).toString() }
    val others = names.filterIndexed { index, _ -> index != me }.joinToString(", ")
    val colorBlind = LocalColorBlindMode.current

    // Étape de MA manche, suivie sur ce téléphone seulement ; [phaseRound] dit à quelle manche elle correspond.
    var phase by rememberSaveable { mutableIntStateOf(PHASE_READY) }
    var phaseRound by rememberSaveable { mutableIntStateOf(-1) }
    var goAt by rememberSaveable { mutableLongStateOf(0L) }

    // La manche est lancée : j'attends le délai, je donne le signal, puis je laisse un temps pour toucher.
    LaunchedEffect(online.running, online.round) {
        if (online.running) {
            phaseRound = online.round
            phase = PHASE_WAITING
            delay(online.delayMs)
            goAt = SystemClock.elapsedRealtime()
            phase = PHASE_GO
            delay(Reflex.RESPONSE_TIMEOUT_MS)
            online.report(Reflex.MISS)
        } else {
            phase = PHASE_READY
        }
    }

    fun onTap() {
        if (!online.running || online.reported || phaseRound != online.round) return
        when (phase) {
            PHASE_WAITING -> online.report(Reflex.EARLY)
            PHASE_GO -> online.report((SystemClock.elapsedRealtime() - goAt).coerceAtLeast(1L).toInt())
        }
    }

    val stop = if (colorBlind) Color(0xFFD55E00) else Color(0xFFC62828)
    val go = if (colorBlind) Color(0xFF0072B2) else Color(0xFF2E7D32)
    val idle = Color(0xFF455A64)
    val roundDone = match.roundPlayed
    val iWon = match.lastWinner == me
    val myResult = match.lastResults.getOrNull(me) ?: Reflex.MISS

    val background = when {
        online.running -> if (online.reported) idle else if (phase == PHASE_GO) go else stop
        !roundDone -> idle
        match.lastDraw -> idle
        iWon -> go
        else -> stop
    }
    val label = when {
        online.running && online.reported -> stringResource(R.string.reflex_sent)
        online.running && phase == PHASE_GO -> stringResource(R.string.reflex_go)
        online.running -> stringResource(R.string.reflex_wait)
        !roundDone -> stringResource(R.string.reflex_zone_ready)
        match.lastDraw -> stringResource(R.string.reflex_zone_draw)
        iWon && match.lastReactionMs > 0 -> stringResource(R.string.reflex_zone_win_ms, match.lastReactionMs)
        iWon -> stringResource(R.string.reflex_zone_win)
        myResult == Reflex.EARLY -> stringResource(R.string.reflex_zone_early)
        myResult > 0 -> stringResource(R.string.reflex_zone_lose_ms, myResult)
        else -> stringResource(R.string.reflex_zone_lose)
    }
    val status = when {
        online.running && online.reported -> stringResource(R.string.reflex_sent_status)
        online.running && phase == PHASE_GO -> stringResource(R.string.reflex_go)
        online.running -> stringResource(R.string.reflex_status_wait)
        online.gathering && online.isHost -> stringResource(R.string.reflex_gather_host)
        online.gathering -> stringResource(R.string.reflex_gather_guest)
        !roundDone -> pluralStringResource(R.plurals.reflex_hint, Reflex.TARGET, Reflex.TARGET)
        match.lastDraw -> stringResource(R.string.reflex_draw)
        match.isOver -> stringResource(R.string.reflex_match_won_n, nameOf(match.winner))
        match.lastReactionMs > 0 -> stringResource(R.string.reflex_round_won, nameOf(match.lastWinner), match.lastReactionMs)
        else -> stringResource(
            R.string.reflex_win_default,
            nameOf(match.lastWinner),
            match.falseStarters.joinToString(", ") { nameOf(it) }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.game_reflex),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    RulesButton(
                        gameId = REFLEX_ID,
                        gameName = stringResource(R.string.game_reflex),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                Text(text = stringResource(R.string.reflex_online_vs, others), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                if (online.notice == ReflexNotice.PLAYER_LEFT) {
                    Text(
                        text = stringResource(R.string.reflex_online_notice_left_some),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
                if (online.gathering) {
                    Text(
                        text = stringResource(R.string.reflex_table_players, names.joinToString(", ")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = names.indices.map {
                            stringResource(R.string.reflex_entry, nameOf(it), match.winsOf(it).toString())
                        }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    ReflexAverages(
                        entries = names.indices.map { nameOf(it) to match.averageMsOf(it) },
                        show = match.hasAverages
                    )
                    // Le détail de la dernière manche : le temps de chacun (ou « trop tôt »), une fois la manche jouée.
                    if (!online.running && roundDone) {
                        val none = stringResource(R.string.reflex_avg_none)
                        val early = stringResource(R.string.reflex_zone_early)
                        val detail = match.lastResults.indices.map { seat ->
                            val result = match.lastResults[seat]
                            stringResource(
                                R.string.reflex_entry,
                                nameOf(seat),
                                when {
                                    result > 0 -> stringResource(R.string.reflex_avg_value, result)
                                    result == Reflex.EARLY -> early
                                    else -> none
                                }
                            )
                        }.joinToString(" · ")
                        Text(
                            text = stringResource(R.string.reflex_last_round, detail),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { liveRegion = LiveRegionMode.Polite }
                )
                if (!online.running) {
                    when {
                        match.isOver -> Button(onClick = { online.newMatch() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.reflex_new_match))
                        }
                        // Tant que les joueurs arrivent, seul l'organisateur lance la partie.
                        online.gathering && !online.isHost -> Unit
                        else -> Button(onClick = { online.startRound() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (online.round > 0) R.string.reflex_next_round else R.string.reflex_start))
                        }
                    }
                }
                OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.reflex_online_leave))
                }
            }
        }
        // Toute la place restante est ma zone : le doigt se pose, le temps est pris tout de suite.
        ReflexPad(background = background, name = nameOf(me), label = label, onTap = ::onTap, modifier = Modifier.weight(1f))
    }
}

/** Une grande zone à toucher, qui réagit dès que le doigt se pose. */
@Composable
private fun ReflexPad(background: Color, name: String, label: String, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val currentOnTap by rememberUpdatedState(onTap)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    currentOnTap()
                    waitForUpOrCancellation()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = name, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.85f))
            Text(
                text = label,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Une carte centrée pour les écrans d'attente. */
@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
    }
}
