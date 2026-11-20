package com.aventure.messcores

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// Les étapes de la séance seul.
private const val SOLO_READY = 0 // on attend que le joueur lance sa série
private const val SOLO_PLAYING = 1 // les plateaux se suivent
private const val SOLO_OVER = 2 // le temps est écoulé

// Les étapes de MA manche à plusieurs téléphones.
private const val ONLINE_IDLE = 0 // pas de manche en cours
private const val ONLINE_WAITING = 1 // la manche est lancée : on attend l'affichage du plateau
private const val ONLINE_PLAYING = 2 // le plateau est affiché : on cherche

// Les étapes du duel à deux sur un même téléphone.
private const val DUEL_IDLE = 0 // pas de manche en cours
private const val DUEL_WAITING = 1 // la manche est lancée : attente avant l'affichage du plateau
private const val DUEL_PLAYING = 2 // le plateau est affiché : les deux joueurs cherchent

/** Taille d'un dessin à l'écran : un peu moins que sa case de départ, bornée pour rester lisible et touchable (en dp). */
private const val MIN_EMOJI_DP = 28f
private const val MAX_EMOJI_DP = 64f

/** Portée du doigt autour du centre d'un dessin, en part de sa taille. */
private const val REACH_FRACTION = 0.55f

/**
 * Chasse à l'émoji : un dessin à retrouver parmi des dessins qui lui ressemblent (voir [EmojiHunt]). Seul sur son téléphone,
 * on enchaîne les niveaux contre la montre ; à deux sur le même téléphone (duel face à face, voir [DuelEmoji]) ou chacun sur
 * le sien (voir [EmojiOnline]), de 2 à 8 joueurs, le plus rapide à toucher le bon dessin gagne la manche. [players] : les
 * noms choisis avant la partie, repris pour le duel.
 */
@Composable
internal fun EmojiScreen(
    players: List<String> = emptyList(),
    online: EmojiOnline,
    onExit: () -> Unit
) {
    var showQuit by rememberSaveable { mutableStateOf(false) }
    // Hors partie à plusieurs téléphones, le retour système quitte simplement l'écran.
    BackHandler(enabled = online.active) {
        if (online.notice == EmojiNotice.HOST_LEFT) {
            online.leave()
            onExit()
        } else {
            showQuit = true
        }
    }

    if (online.active) {
        OnlineEmoji(online = online, onQuit = { showQuit = true }, onClose = {
            online.leave()
            onExit()
        })
    } else {
        SoloEmoji(players = players)
    }

    if (showQuit && online.active) {
        AlertDialog(
            onDismissRequest = { showQuit = false },
            title = { Text(stringResource(R.string.emoji_quit_title)) },
            text = { Text(stringResource(R.string.emoji_quit_message)) },
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

// ---------- Seul ----------

/**
 * La partie seul : chaque dessin trouvé ouvre le niveau suivant (plus de pièges, moins de temps, puis des dessins qui
 * bougent). La série s'arrête quand le temps est écoulé ; le nombre de niveaux réussis est le score, et le record est gardé.
 * Tout est gardé quand l'écran tourne.
 */
@Composable
private fun SoloEmoji(players: List<String>) {
    val record = rememberSoloRecord(EMOJI_ID)
    // Le duel à deux sur ce téléphone remplace la séance seul tant qu'il dure.
    var duel by rememberSaveable { mutableStateOf(false) }
    if (duel) {
        DuelEmoji(players = players, onStop = { duel = false })
        return
    }
    var phase by rememberSaveable { mutableIntStateOf(SOLO_READY) }
    var level by rememberSaveable { mutableIntStateOf(1) }
    var seed by rememberSaveable { mutableLongStateOf(0L) }
    var cleared by rememberSaveable { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        when (phase) {
            SOLO_PLAYING -> {
                val board = remember(seed, level) { EmojiHunt.generate(seed, level) }
                EmojiPlay(
                    board = board,
                    title = stringResource(R.string.emoji_level, level),
                    onFound = {
                        cleared = level
                        level += 1
                        seed = kotlin.random.Random.nextLong()
                    },
                    onTimeout = {
                        record.submit(cleared)
                        phase = SOLO_OVER
                    }
                )
            }
            else -> EmojiCard {
                if (phase == SOLO_OVER) {
                    Text(
                        text = stringResource(R.string.emoji_over_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.emoji_over_levels, cleared),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = highlightContent(),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(highlightContainer())
                            .padding(12.dp)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.emoji_ready_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                SoloRecordLine(record)
                Button(
                    onClick = {
                        record.clearNew()
                        cleared = 0
                        level = 1
                        seed = kotlin.random.Random.nextLong()
                        phase = SOLO_PLAYING
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(if (phase == SOLO_OVER) R.string.emoji_new_game else R.string.emoji_start))
                }
                if (phase == SOLO_READY) {
                    OutlinedButton(onClick = { duel = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.emoji_duel_button))
                    }
                }
            }
        }
        RecordSparkles(record)
    }
}

// ---------- Le plateau ----------

/**
 * Un plateau en cours : le dessin à trouver en haut avec le temps qui reste, puis tous les dessins. Le temps part dès
 * l'affichage. Toucher le bon dessin appelle [onFound] avec le temps du joueur (pénalités comprises) ; un mauvais dessin
 * s'estompe et coûte [EmojiHunt.PENALTY_MS] ; quand le temps restant est écoulé, [onTimeout] est appelé. Un seul des deux.
 */
@Composable
private fun EmojiPlay(board: EmojiBoard, title: String, onFound: (Int) -> Unit, onTimeout: () -> Unit) {
    KeepScreenOn()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val currentFound by rememberUpdatedState(onFound)
    val currentTimeout by rememberUpdatedState(onTimeout)

    var areaWidth by remember { mutableFloatStateOf(0f) }
    var areaHeight by remember { mutableFloatStateOf(0f) }
    // Le temps part à la composition du plateau et ne dépend que de l'horloge du téléphone.
    val startedAt = remember(board) { SystemClock.elapsedRealtime() }
    var elapsedMs by remember(board) { mutableLongStateOf(0L) }
    var wrong by remember(board) { mutableStateOf(emptySet<Int>()) }
    var finished by remember(board) { mutableStateOf(false) }

    LaunchedEffect(board) {
        while (!finished) {
            withFrameNanos { it }
            elapsedMs = SystemClock.elapsedRealtime() - startedAt
            if (!finished && EmojiHunt.remainingMs(board.level, elapsedMs, wrong.size) <= 0L) {
                finished = true
                currentTimeout()
            }
        }
    }

    val remaining = EmojiHunt.remainingMs(board.level, elapsedMs, wrong.size).coerceIn(0L, board.level.timeLimitMs)
    val minPx = with(density) { MIN_EMOJI_DP.dp.toPx() }
    val maxPx = with(density) { MAX_EMOJI_DP.dp.toPx() }
    val diameter = if (areaWidth <= 0f || areaHeight <= 0f) minPx
    else (minOf(areaWidth / board.columns, areaHeight / board.rows) * 0.8f).coerceIn(minPx, maxPx)
    val fontSize = with(density) { (diameter * 0.8f).toSp() }
    val layouts = remember(board, fontSize) {
        board.items.map { textMeasurer.measure(AnnotatedString(it.symbol), TextStyle(fontSize = fontSize)) }
    }
    val fieldDescription = stringResource(R.string.emoji_field_description)

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = stringResource(R.string.emoji_find), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = board.target.symbol, fontSize = 40.sp)
                    }
                    Text(
                        text = stringResource(R.string.emoji_mistakes, wrong.size),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (wrong.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                    )
                }
                LinearProgressIndicator(
                    progress = { remaining.toFloat() / board.level.timeLimitMs.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(cardSurface())
                .onSizeChanged {
                    areaWidth = it.width.toFloat()
                    areaHeight = it.height.toFloat()
                }
                .semantics { contentDescription = fieldDescription }
                .pointerInput(board, areaWidth, areaHeight, diameter) {
                    // Dès le doigt posé : attendre qu'il se lève ferait perdre un temps précieux.
                    detectTapGestures(onPress = { tap ->
                        if (!finished && areaWidth > 0f && areaHeight > 0f) {
                            val index = board.hit(
                                tapX = tap.x / areaWidth,
                                tapY = tap.y / areaHeight,
                                elapsedMs = elapsedMs,
                                reachX = diameter * REACH_FRACTION / areaWidth,
                                reachY = diameter * REACH_FRACTION / areaHeight,
                                ignored = wrong
                            )
                            if (index == board.targetIndex) {
                                finished = true
                                currentFound(EmojiHunt.resultMs(SystemClock.elapsedRealtime() - startedAt, wrong.size))
                            } else if (index >= 0) {
                                wrong = wrong + index
                            }
                        }
                    })
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                board.items.indices.forEach { index ->
                    val (x, y) = board.positionAt(index, elapsedMs)
                    val layout = layouts[index]
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(x * size.width - layout.size.width / 2f, y * size.height - layout.size.height / 2f),
                        alpha = if (index in wrong) 0.2f else 1f
                    )
                }
            }
        }
    }
}

/** Le plateau des cartes de la séance seul : le titre du jeu, ses règles, puis [content]. */
@Composable
private fun EmojiCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.game_emoji),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    RulesButton(
                        gameId = EMOJI_ID,
                        gameName = stringResource(R.string.game_emoji),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                content()
            }
        }
    }
}

// ---------- À deux sur le même téléphone ----------

/** Sauvegarde du score du duel (deux joueurs) quand l'écran tourne. */
private val EmojiDuelSaver = listSaver<EmojiGame, Int>(
    save = { g ->
        listOf(
            g.winsOf(0), g.winsOf(1), g.totalMs.getOrElse(0) { 0 }, g.totalMs.getOrElse(1) { 0 },
            g.timedRoundsOf(0), g.timedRoundsOf(1), g.lastWinner, if (g.lastDraw) 1 else 0,
            g.lastResults.getOrElse(0) { 0 }, g.lastResults.getOrElse(1) { 0 }, if (g.lastResults.isEmpty()) 0 else 1
        )
    },
    restore = { v ->
        EmojiGame(
            wins = listOf(v[0], v[1]),
            totalMs = listOf(v[2], v[3]),
            timedRounds = listOf(v[4], v[5]),
            lastResults = if (v[10] == 1) listOf(v[8], v[9]) else emptyList(),
            lastWinner = v[6],
            lastDraw = v[7] == 1
        )
    }
)

/**
 * Le duel face à face sur un seul téléphone posé à plat : l'écran est coupé en deux, chacun joue sur sa moitié (celle du
 * haut est retournée) avec le même plateau. Le plus rapide à toucher le bon dessin gagne la manche (pénalités comprises) ;
 * le premier à [EmojiHunt.TARGET] manches gagnées remporte la partie. Le score est le même [EmojiGame] que chacun sur son
 * téléphone.
 */
@Composable
private fun DuelEmoji(players: List<String>, onStop: () -> Unit) {
    BackHandler(onBack = onStop)
    val first = players.getOrNull(0)?.takeIf { it.isNotBlank() } ?: stringResource(R.string.duo_player_default, 1)
    val second = players.getOrNull(1)?.takeIf { it.isNotBlank() } ?: stringResource(R.string.duo_player_default, 2)
    val names = listOf(first, second)

    var match by rememberSaveable(stateSaver = EmojiDuelSaver) { mutableStateOf(EmojiGame.of(2)) }
    var phase by rememberSaveable { mutableIntStateOf(DUEL_IDLE) }
    var round by rememberSaveable { mutableIntStateOf(0) }
    var seed by rememberSaveable { mutableLongStateOf(0L) }
    var delayMs by rememberSaveable { mutableLongStateOf(0L) }

    // La manche est lancée : on attend le délai au hasard, puis le plateau s'affiche pour les deux joueurs.
    LaunchedEffect(phase, round) {
        if (phase == DUEL_WAITING) {
            delay(delayMs)
            phase = DUEL_PLAYING
        }
    }

    if (phase == DUEL_PLAYING) {
        val board = remember(seed, round) { EmojiHunt.generate(seed, round) }
        EmojiDuelPlay(board = board, names = names, onDone = { results ->
            match = match.resolve(results) ?: match
            phase = DUEL_IDLE
        })
        return
    }

    KeepScreenOn()
    val roundDone = match.roundPlayed
    val status = when {
        phase == DUEL_WAITING -> stringResource(R.string.emoji_get_ready)
        !roundDone -> stringResource(R.string.emoji_duel_hint, match.target)
        match.lastDraw -> stringResource(R.string.emoji_draw)
        match.isOver -> stringResource(R.string.emoji_match_won_n, names[match.winner])
        else -> stringResource(R.string.emoji_round_won, names[match.lastWinner], match.lastTimeMs)
    }
    EmojiCard {
        Text(
            text = names.indices.map { stringResource(R.string.emoji_entry, names[it], match.winsOf(it).toString()) }.joinToString(" · "),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        if (phase == DUEL_IDLE && roundDone) {
            val none = stringResource(R.string.emoji_none)
            Text(
                text = stringResource(
                    R.string.emoji_last_round,
                    match.lastResults.indices.map { seat ->
                        val result = match.lastResults[seat]
                        stringResource(R.string.emoji_entry, names[seat], if (result > 0) stringResource(R.string.emoji_ms, result) else none)
                    }.joinToString(" · ")
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = status,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.heightIn(min = 48.dp).semantics { liveRegion = LiveRegionMode.Polite }
        )
        if (phase == DUEL_IDLE) {
            Button(
                onClick = {
                    if (match.isOver) {
                        match = match.newMatch()
                        round = 0
                    }
                    round += 1
                    seed = kotlin.random.Random.nextLong()
                    delayMs = EmojiHunt.randomDelayMs()
                    phase = DUEL_WAITING
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        when {
                            match.isOver -> R.string.emoji_new_match
                            round > 0 -> R.string.emoji_next_round
                            else -> R.string.emoji_start_round
                        }
                    )
                )
            }
        }
        OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.emoji_duel_stop))
        }
    }
}

/**
 * Une manche du duel : le même plateau sur les deux moitiés de l'écran, une seule horloge. Chaque joueur a son résultat
 * (son temps, pénalités comprises, ou [EmojiHunt.MISS]). La manche s'arrête quand les deux ont fini, ou dès que le temps
 * écoulé dépasse le meilleur résultat : l'autre ne pourrait plus faire mieux. [onDone] reçoit un résultat par joueur
 * (celui qui n'a pas fini compte comme « pas trouvé »).
 */
@Composable
private fun EmojiDuelPlay(board: EmojiBoard, names: List<String>, onDone: (List<Int>) -> Unit) {
    KeepScreenOn()
    val currentDone by rememberUpdatedState(onDone)
    val startedAt = remember(board) { SystemClock.elapsedRealtime() }
    var elapsedMs by remember(board) { mutableLongStateOf(0L) }
    val wrong = remember(board) { mutableStateListOf<Set<Int>>(emptySet(), emptySet()) }
    val results = remember(board) { mutableStateListOf<Int?>(null, null) }

    LaunchedEffect(board) {
        var done = false
        while (!done) {
            withFrameNanos { it }
            val now = SystemClock.elapsedRealtime() - startedAt
            elapsedMs = now
            for (p in 0..1) {
                if (results[p] == null && EmojiHunt.remainingMs(board.level, now, wrong[p].size) <= 0L) results[p] = EmojiHunt.MISS
            }
            val best = results.filterNotNull().filter { it > 0 }.minOrNull()
            val settled = best != null && (0..1).all { results[it] != null || now > best }
            if (settled || results.all { it != null }) {
                done = true
                currentDone(results.map { it ?: EmojiHunt.MISS })
            }
        }
    }

    fun onTap(player: Int, index: Int) {
        if (results[player] != null) return
        if (index == board.targetIndex) {
            results[player] = EmojiHunt.resultMs(SystemClock.elapsedRealtime() - startedAt, wrong[player].size)
        } else if (index >= 0) {
            wrong[player] = wrong[player] + index
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // La moitié du haut est retournée : le joueur assis en face la voit à l'endroit.
        EmojiDuelPanel(
            board = board, elapsedMs = elapsedMs, name = names[1], wrong = wrong[1], result = results[1],
            onTap = { onTap(1, it) }, modifier = Modifier.weight(1f).fillMaxWidth().rotate(180f)
        )
        Spacer(modifier = Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.outline))
        EmojiDuelPanel(
            board = board, elapsedMs = elapsedMs, name = names[0], wrong = wrong[0], result = results[0],
            onTap = { onTap(0, it) }, modifier = Modifier.weight(1f).fillMaxWidth()
        )
    }
}

/** La moitié d'écran d'un joueur : son nom, le dessin à trouver, ses erreurs, le temps qui lui reste et le plateau. */
@Composable
private fun EmojiDuelPanel(
    board: EmojiBoard,
    elapsedMs: Long,
    name: String,
    wrong: Set<Int>,
    result: Int?,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val currentTap by rememberUpdatedState(onTap)
    var areaWidth by remember { mutableFloatStateOf(0f) }
    var areaHeight by remember { mutableFloatStateOf(0f) }

    val remaining = EmojiHunt.remainingMs(board.level, elapsedMs, wrong.size).coerceIn(0L, board.level.timeLimitMs)
    val minPx = with(density) { MIN_EMOJI_DP.dp.toPx() }
    val maxPx = with(density) { MAX_EMOJI_DP.dp.toPx() }
    val diameter = if (areaWidth <= 0f || areaHeight <= 0f) minPx
    else (minOf(areaWidth / board.columns, areaHeight / board.rows) * 0.8f).coerceIn(minPx, maxPx)
    val fontSize = with(density) { (diameter * 0.8f).toSp() }
    val layouts = remember(board, fontSize) {
        board.items.map { textMeasurer.measure(AnnotatedString(it.symbol), TextStyle(fontSize = fontSize)) }
    }
    val fieldDescription = stringResource(R.string.emoji_field_description)
    val none = stringResource(R.string.emoji_duel_missed)

    Column(modifier = modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.emoji_find), style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = board.target.symbol, fontSize = 28.sp)
            }
            Text(
                text = stringResource(R.string.emoji_mistakes, wrong.size),
                style = MaterialTheme.typography.titleSmall,
                color = if (wrong.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
            )
        }
        LinearProgressIndicator(
            progress = { remaining.toFloat() / board.level.timeLimitMs.toFloat() },
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(cardSurface())
                .onSizeChanged {
                    areaWidth = it.width.toFloat()
                    areaHeight = it.height.toFloat()
                }
                .semantics { contentDescription = fieldDescription }
                .pointerInput(board, areaWidth, areaHeight, diameter, result) {
                    detectTapGestures(onPress = { tap ->
                        if (result == null && areaWidth > 0f && areaHeight > 0f) {
                            currentTap(
                                board.hit(
                                    tapX = tap.x / areaWidth,
                                    tapY = tap.y / areaHeight,
                                    elapsedMs = elapsedMs,
                                    reachX = diameter * REACH_FRACTION / areaWidth,
                                    reachY = diameter * REACH_FRACTION / areaHeight,
                                    ignored = wrong
                                )
                            )
                        }
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                board.items.indices.forEach { index ->
                    val (x, y) = board.positionAt(index, elapsedMs)
                    val layout = layouts[index]
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(x * size.width - layout.size.width / 2f, y * size.height - layout.size.height / 2f),
                        alpha = if (result != null || index in wrong) 0.2f else 1f
                    )
                }
            }
            // Ce joueur a fini : son temps (ou « temps écoulé ») reste affiché en attendant l'autre.
            if (result != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (result > 0) stringResource(R.string.emoji_ms, result) else none,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                    Text(text = stringResource(R.string.emoji_duel_wait), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ---------- Chacun sur son téléphone ----------

/**
 * La partie chacun sur son téléphone, de deux à huit joueurs : attente des autres joueurs, rassemblement à la table, puis
 * à chaque manche une attente au hasard et le même plateau sur tous les téléphones. Ce téléphone mesure le temps de son
 * joueur depuis l'affichage ; l'organisateur compare les temps de tout le monde.
 */
@Composable
private fun OnlineEmoji(online: EmojiOnline, onQuit: () -> Unit, onClose: () -> Unit) {
    if (online.notice == EmojiNotice.HOST_LEFT) {
        EmojiInfoCard {
            Text(text = stringResource(R.string.game_emoji), style = MaterialTheme.typography.headlineMedium)
            Text(text = stringResource(R.string.emoji_online_host_left), style = MaterialTheme.typography.titleMedium)
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_close)) }
        }
        return
    }
    if (!online.ready) {
        EmojiInfoCard {
            Text(text = stringResource(R.string.game_emoji), style = MaterialTheme.typography.headlineMedium)
            if (online.notice == EmojiNotice.PLAYER_LEFT) {
                Text(
                    text = stringResource(R.string.emoji_online_notice_left),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(text = stringResource(R.string.emoji_online_waiting), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.emoji_online_leave))
            }
        }
        return
    }
    KeepScreenOn()

    // Étape de MA manche, suivie sur ce téléphone seulement.
    var phase by rememberSaveable { mutableIntStateOf(ONLINE_IDLE) }

    // La manche est lancée : j'attends le délai, puis le plateau s'affiche et mon temps part.
    LaunchedEffect(online.running, online.round) {
        if (online.running) {
            phase = ONLINE_WAITING
            delay(online.delayMs)
            phase = ONLINE_PLAYING
        } else {
            phase = ONLINE_IDLE
        }
    }

    if (online.running && phase == ONLINE_PLAYING && !online.reported) {
        val board = remember(online.seed, online.round) { EmojiHunt.generate(online.seed, online.round) }
        EmojiPlay(
            board = board,
            title = stringResource(R.string.emoji_round, online.round),
            onFound = { online.report(it) },
            onTimeout = { online.report(EmojiHunt.MISS) }
        )
        return
    }

    val match = online.match
    val me = online.seat
    val names = online.lobby
    fun nameOf(seat: Int) = names.getOrElse(seat) { (seat + 1).toString() }
    val others = names.filterIndexed { index, _ -> index != me }.joinToString(", ")
    val roundDone = match.roundPlayed

    val status = when {
        online.running && online.reported -> stringResource(R.string.emoji_sent_status)
        online.running -> stringResource(R.string.emoji_get_ready)
        online.gathering && online.isHost -> stringResource(R.string.emoji_gather_host)
        online.gathering -> stringResource(R.string.emoji_gather_guest)
        !roundDone -> stringResource(R.string.emoji_hint_online, match.target)
        match.lastDraw -> stringResource(R.string.emoji_draw)
        match.isOver -> stringResource(R.string.emoji_match_won_n, nameOf(match.winner))
        else -> stringResource(R.string.emoji_round_won, nameOf(match.lastWinner), match.lastTimeMs)
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.game_emoji),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    RulesButton(
                        gameId = EMOJI_ID,
                        gameName = stringResource(R.string.game_emoji),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                Text(text = stringResource(R.string.emoji_online_vs, others), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                if (online.notice == EmojiNotice.PLAYER_LEFT) {
                    Text(
                        text = stringResource(R.string.emoji_online_notice_left_some),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
                if (online.gathering) {
                    Text(
                        text = stringResource(R.string.emoji_table_players, names.joinToString(", ")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = names.indices.map {
                            stringResource(R.string.emoji_entry, nameOf(it), match.winsOf(it).toString())
                        }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (match.hasAverages) {
                        val none = stringResource(R.string.emoji_none)
                        val average = names.indices.map { seat ->
                            stringResource(
                                R.string.emoji_entry,
                                nameOf(seat),
                                match.averageMsOf(seat)?.let { stringResource(R.string.emoji_ms, it) } ?: none
                            )
                        }.joinToString(" · ")
                        Text(
                            text = stringResource(R.string.emoji_avg, average),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    // Le détail de la dernière manche : le temps de chacun (ou « pas trouvé »), une fois la manche jouée.
                    if (!online.running && roundDone) {
                        val none = stringResource(R.string.emoji_none)
                        val detail = match.lastResults.indices.map { seat ->
                            val result = match.lastResults[seat]
                            stringResource(
                                R.string.emoji_entry,
                                nameOf(seat),
                                if (result > 0) stringResource(R.string.emoji_ms, result) else none
                            )
                        }.joinToString(" · ")
                        Text(
                            text = stringResource(R.string.emoji_last_round, detail),
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
                            Text(stringResource(R.string.emoji_new_match))
                        }
                        // Tant que les joueurs arrivent, seul l'organisateur lance la partie.
                        online.gathering && !online.isHost -> Unit
                        else -> Button(onClick = { online.startRound() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (online.round > 0) R.string.emoji_next_round else R.string.emoji_start_round))
                        }
                    }
                }
                OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.emoji_online_leave))
                }
            }
        }
    }
}

/** Une carte centrée pour les écrans d'attente. */
@Composable
private fun EmojiInfoCard(content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
    }
}
