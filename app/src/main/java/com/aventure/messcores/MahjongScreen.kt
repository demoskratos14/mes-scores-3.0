package com.aventure.messcores

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.delay

// Les étapes de la séance : le choix du niveau, puis le plateau (la partie en cours ou, une fois finie, son résultat).
private const val MAHJONG_PHASE_READY = 0
private const val MAHJONG_PHASE_PLAYING = 1

/** Pas du chronomètre en millisecondes, et plus long temps compté d'un coup (après un à-coup, il ne saute pas). */
private const val MAHJONG_TIMER_STEP_MS = 100L
private const val MAHJONG_MAX_TICK_MS = 500L

/** Hauteur d'une tuile par rapport à sa largeur. */
private const val TILE_ASPECT = 1.28f

/** Épaisseur d'une tuile, et décalage d'un étage sur le suivant, en largeurs de demi-tuile. */
private const val TILE_LIFT = 0.32f

// Les couleurs du plateau : le tapis, la tranche et la face des tuiles, et les traits qui les marquent.
private val MahjongTableColor = Color(0xFF1B5E3A)
private val MahjongEdgeColor = Color(0xFF2F8F6B)
private val MahjongFaceColor = Color(0xFFF8F3E6)
private val MahjongSelectedFaceColor = Color(0xFFFFE9A8)
private val MahjongSelectedBorderColor = Color(0xFFFF9800)
private val MahjongHintBorderColor = Color(0xFF00B8D4)

// L'encre des symboles, comme sur les tuiles du jeu photographié : vert, rouge et noir.
private val MjGreen = Color(0xFF2E7D32)
private val MjRed = Color(0xFFD32F2F)
private val MjBlack = Color(0xFF263238)
private val MjPink = Color(0xFFEC6A8E)
private val MjOrange = Color(0xFFF59E0B)
private val MjYellow = Color(0xFFFFD54F)

// Les signes écrits sur les tuiles : ce sont des symboles du jeu, les mêmes dans toutes les langues.
private const val MJ_NUMERALS = "一二三四五六七八九"
private const val MJ_WINDS = "東南西北"
private const val MJ_FLOWERS = "梅蘭菊竹"
private const val MJ_SEASONS = "春夏秋冬"

/** Garde la partie quand l'écran tourne : son état est rangé dans une liste d'entiers (voir [MahjongGame.save]). */
private val MahjongGameSaver = listSaver<MahjongGame, Int>(
    save = { it.save() },
    restore = { MahjongGame.restore(it) }
)

@StringRes
private fun MahjongLevel.labelRes(): Int = when (this) {
    MahjongLevel.EASY -> R.string.mahjong_level_easy
    MahjongLevel.MEDIUM -> R.string.mahjong_level_medium
    MahjongLevel.HARD -> R.string.mahjong_level_hard
}

/**
 * Mahjong solitaire en solo : on retire du plateau les tuiles deux par deux. Une tuile est libre quand rien ne la recouvre et
 * qu'un de ses côtés est dégagé (les tuiles bloquées sont assombries) ; deux tuiles libres identiques forment une paire, et les
 * quatre fleurs vont ensemble comme les quatre saisons. Toucher une tuile libre la choisit, toucher sa jumelle retire la paire.
 * La donne a toujours une solution ; un indice, l'annulation et le mélange des tuiles restantes aident (l'indice et le mélange
 * coûtent des points). Trois niveaux, un record par niveau (le score tient compte du temps). Le chronomètre tourne dès le départ ;
 * la partie se met en pause quand l'appli passe en arrière-plan. Tout est gardé quand l'écran tourne.
 * [players] : le joueur dont le nom s'affiche au départ.
 */
@Composable
internal fun MahjongScreen(players: List<String>) {
    val name = players.firstOrNull().orEmpty().ifEmpty { "1" }
    val context = LocalContext.current
    var levelIndex by rememberSaveable { mutableIntStateOf(0) }
    val level = MahjongLevel.entries[levelIndex.coerceIn(0, MahjongLevel.entries.size - 1)]
    val game = rememberSaveable(levelIndex, saver = MahjongGameSaver) { MahjongGame(level) }
    var phase by rememberSaveable { mutableIntStateOf(MAHJONG_PHASE_READY) }
    var paused by rememberSaveable { mutableStateOf(false) }
    // Change à chaque nouvelle partie : le plateau est alors reconstruit à neuf.
    var round by rememberSaveable { mutableIntStateOf(0) }
    val record = rememberSoloRecord(Mahjong.recordKey(level))
    val currentGame by rememberUpdatedState(game)

    // La partie se met en pause quand l'appli passe en arrière-plan (et donc aussi quand l'écran tourne).
    val lifecycleOwner = remember(context) { findMahjongLifecycleOwner(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && phase == MAHJONG_PHASE_PLAYING &&
                currentGame.state == MahjongGame.PLAYING
            ) {
                paused = true
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    // Le bouton retour met la partie en pause plutôt que de la quitter par mégarde.
    BackHandler(enabled = phase == MAHJONG_PHASE_PLAYING && game.state == MahjongGame.PLAYING && !paused) { paused = true }

    fun startGame() {
        record.clearNew()
        game.reset()
        paused = false
        round++
        phase = MAHJONG_PHASE_PLAYING
    }

    fun backToMenu() {
        record.clearNew()
        game.reset()
        paused = false
        phase = MAHJONG_PHASE_READY
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (phase == MAHJONG_PHASE_PLAYING) {
            key(round) {
                MahjongPlayfield(
                    game = game,
                    record = record,
                    paused = paused,
                    onPausedChange = { paused = it },
                    onFinished = { if (game.state == MahjongGame.WON) record.submit(game.score) },
                    onReplay = { startGame() },
                    onChangeLevel = { backToMenu() }
                )
            }
        } else {
            MahjongCard {
                Text(
                    text = stringResource(R.string.mahjong_ready_title, name),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.mahjong_ready_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
                    MahjongLevel.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = option == level,
                                    onClick = { levelIndex = option.ordinal },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = option == level, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(
                                    R.string.mahjong_level_detail,
                                    stringResource(option.labelRes()),
                                    option.tileCount
                                ),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
                SoloRecordLine(record)
                Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.mahjong_go))
                }
            }
        }
        RecordSparkles(record)
    }
}

/** La carte de départ : le titre du jeu, ses règles, puis [content]. */
@Composable
private fun MahjongCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = cardSurface()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.game_mahjong),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    RulesButton(
                        gameId = MAHJONG_ID,
                        gameName = stringResource(R.string.game_mahjong),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                content()
            }
        }
    }
}

/**
 * La partie : l'en-tête (tuiles restantes, temps, pause), le plateau, puis les commandes (indice, annuler, mélanger) ou, une
 * fois la partie finie, le résultat. [onFinished] est appelé une fois, à la victoire comme à la défaite.
 */
@Composable
private fun MahjongPlayfield(
    game: MahjongGame,
    record: SoloRecord,
    paused: Boolean,
    onPausedChange: (Boolean) -> Unit,
    onFinished: () -> Unit,
    onReplay: () -> Unit,
    onChangeLevel: () -> Unit
) {
    KeepScreenOn()
    // Change à chaque action : le plateau se redessine, sans recomposer tout l'écran.
    var revision by remember { mutableIntStateOf(0) }
    var gameState by remember { mutableIntStateOf(game.state) }
    var remaining by remember { mutableIntStateOf(game.remaining) }
    var seconds by remember { mutableIntStateOf(game.seconds) }
    var canUndo by remember { mutableStateOf(game.canUndo) }
    // Vrai quand il ne reste aucune paire à retirer : il faut mélanger les tuiles.
    var stuck by remember { mutableStateOf(game.state == MahjongGame.PLAYING && !game.hasMove()) }
    // La paire montrée par le dernier indice, jusqu'au geste suivant.
    var hint by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val finishedCallback by rememberUpdatedState(onFinished)

    fun sync() {
        revision++
        gameState = game.state
        remaining = game.remaining
        seconds = game.seconds
        canUndo = game.canUndo
        stuck = game.state == MahjongGame.PLAYING && !game.hasMove()
        if (game.isFinished) finishedCallback()
    }

    fun tapTile(index: Int) {
        if (paused || game.isFinished) return
        val result = game.tap(index)
        // Une tuile bloquée (ou déjà retirée) ne change rien : l'indice reste affiché.
        if (result == MahjongGame.TAP_IGNORED || result == MahjongGame.TAP_BLOCKED) return
        hint = null
        sync()
    }

    fun askHint() {
        if (paused || game.isFinished) return
        val pair = game.hint() ?: return
        hint = pair
        sync()
    }

    fun undo() {
        if (paused || !game.undo()) return
        hint = null
        sync()
    }

    fun shuffle() {
        if (paused || game.isFinished) return
        game.shuffle()
        hint = null
        sync()
    }

    // Le chronomètre ne tourne que partie en cours et hors pause.
    LaunchedEffect(paused, gameState) {
        if (paused || gameState != MahjongGame.PLAYING) return@LaunchedEffect
        var last = System.nanoTime()
        while (true) {
            delay(MAHJONG_TIMER_STEP_MS)
            val now = System.nanoTime()
            game.tick(((now - last) / 1_000_000L).coerceIn(0L, MAHJONG_MAX_TICK_MS))
            last = now
            if (game.seconds != seconds) seconds = game.seconds
        }
    }

    val finished = gameState != MahjongGame.PLAYING

    Column(
        modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.mahjong_tiles_left, remaining),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.mahjong_time, seconds),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { onPausedChange(true) },
                    enabled = !paused && gameState == MahjongGame.PLAYING
                ) {
                    Text(stringResource(R.string.mahjong_pause))
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val layout = game.layout
            val extra = TILE_LIFT * (layout.maxZ + 1)
            // Une demi-tuile de large, la plus grande qui laisse tenir le plateau avec un peu de marge.
            val unit = maxOf(
                minOf(
                    (maxWidth - 16.dp) / (layout.spanX + extra),
                    (maxHeight - 16.dp) / (layout.spanY * TILE_ASPECT + extra)
                ),
                0.dp
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MahjongTableColor),
                contentAlignment = Alignment.Center
            ) {
                if (paused) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.mahjong_paused_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(8.dp)
                        )
                        Button(onClick = { onPausedChange(false) }) {
                            Text(stringResource(R.string.mahjong_resume))
                        }
                    }
                } else {
                    MahjongBoard(
                        game = game,
                        revision = revision,
                        unit = unit,
                        hint = hint,
                        onTap = { tapTile(it) },
                        modifier = Modifier.size(
                            unit * (layout.spanX + extra),
                            unit * (layout.spanY * TILE_ASPECT + extra)
                        )
                    )
                }
            }
        }

        if (finished) {
            MahjongResult(game = game, won = gameState == MahjongGame.WON, record = record, onReplay = onReplay, onChangeLevel = onChangeLevel)
        } else {
            if (stuck) {
                Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.mahjong_stuck_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.mahjong_stuck_hint, Mahjong.SHUFFLE_COST),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { askHint() },
                    enabled = !paused && !stuck,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.mahjong_hint), textAlign = TextAlign.Center)
                }
                OutlinedButton(
                    onClick = { undo() },
                    enabled = !paused && canUndo,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.mahjong_undo), textAlign = TextAlign.Center)
                }
                if (stuck) {
                    Button(onClick = { shuffle() }, enabled = !paused, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.mahjong_shuffle), textAlign = TextAlign.Center)
                    }
                } else {
                    OutlinedButton(onClick = { shuffle() }, enabled = !paused, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.mahjong_shuffle), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** Le résultat d'une partie finie, sous le plateau : la victoire et son score, ou la partie bloquée. */
@Composable
private fun MahjongResult(game: MahjongGame, won: Boolean, record: SoloRecord, onReplay: () -> Unit, onChangeLevel: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(if (won) R.string.mahjong_won_title else R.string.mahjong_lost_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            if (won) {
                Text(
                    text = stringResource(R.string.mahjong_score, game.score),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = highlightContent(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(highlightContainer())
                        .padding(8.dp)
                )
                Text(
                    text = stringResource(R.string.mahjong_won_stats, game.hints, game.shuffles),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = stringResource(R.string.mahjong_lost_stats, game.remaining),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
            SoloRecordLine(record)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onReplay, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.mahjong_play_again), textAlign = TextAlign.Center)
                }
                OutlinedButton(onClick = onChangeLevel, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.mahjong_change_level), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Les mesures d'une tuile à l'échelle [unit] (une demi-tuile de large, en pixels) pour un plateau de [maxZ] + 1 étages. */
private class MahjongGeometry(val unit: Float, maxZ: Int) {
    val tileW: Float = 2f * unit
    val tileH: Float = 2f * unit * TILE_ASPECT

    /** Épaisseur d'une tuile, qui est aussi le décalage vers le haut et la gauche d'un étage sur le précédent. */
    val lift: Float = unit * TILE_LIFT
    private val shift: Float = maxZ * lift

    fun left(spot: MahjongSpot): Float = spot.x * unit + shift - spot.z * lift
    fun top(spot: MahjongSpot): Float = spot.y * unit * TILE_ASPECT + shift - spot.z * lift
}

/** L'emplacement de la tuile touchée en [offset] (celle du dessus quand elles se chevauchent), ou -1. */
private fun tileAt(game: MahjongGame, geometry: MahjongGeometry, offset: Offset): Int {
    val order = game.layout.drawOrder
    for (k in order.indices.reversed()) {
        val i = order[k]
        if (game.isRemoved(i)) continue
        val spot = game.layout.spots[i]
        val left = geometry.left(spot)
        val top = geometry.top(spot)
        if (offset.x >= left && offset.x <= left + geometry.tileW + geometry.lift &&
            offset.y >= top && offset.y <= top + geometry.tileH + geometry.lift
        ) {
            return i
        }
    }
    return -1
}

/**
 * Le plateau : toutes les tuiles dessinées sur un seul Canvas, un geste du doigt qui retrouve la tuile touchée, et, pour
 * TalkBack, une zone par tuile libre (les tuiles bloquées ne se jouent pas). [revision] change à chaque action pour redessiner.
 */
@Composable
private fun MahjongBoard(
    game: MahjongGame,
    revision: Int,
    unit: Dp,
    hint: Pair<Int, Int>?,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val unitPx = with(density) { unit.toPx() }
    val layout = game.layout
    val geometry = remember(unitPx, layout) { MahjongGeometry(unitPx, layout.maxZ) }
    val act by rememberUpdatedState(onTap)
    // Les tuiles ne sont pas un état Compose : on relit leur état à chaque [revision]. Le paramètre doit être réellement lu ici,
    // sinon le compilateur Compose ignore son changement et le plateau ne se redessine pas après un geste.
    val alive = remember(revision, game) { BooleanArray(game.size) { !game.isRemoved(it) } }
    val free = remember(revision, game) { BooleanArray(game.size) { game.isFree(it) } }
    val selected = game.selected

    Box(
        modifier = modifier.pointerInput(geometry, game) {
            detectTapGestures(
                onTap = { offset ->
                    val index = tileAt(game, geometry, offset)
                    if (index >= 0) act(index)
                }
            )
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (i in layout.drawOrder) {
                if (!alive[i]) continue
                val spot = layout.spots[i]
                drawMahjongTile(
                    kind = game.kindAt(i),
                    left = geometry.left(spot),
                    top = geometry.top(spot),
                    w = geometry.tileW,
                    h = geometry.tileH,
                    edge = geometry.lift,
                    free = free[i],
                    selected = i == selected,
                    hinted = hint != null && (i == hint.first || i == hint.second)
                )
            }
        }
        for (i in layout.drawOrder) {
            if (alive[i] && free[i]) {
                val spot = layout.spots[i]
                val tileName = mahjongTileName(game.kindAt(i))
                val description = if (i == selected) stringResource(R.string.mahjong_tile_selected, tileName) else tileName
                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { geometry.left(spot).toDp() },
                            y = with(density) { geometry.top(spot).toDp() }
                        )
                        .size(
                            with(density) { geometry.tileW.toDp() },
                            with(density) { geometry.tileH.toDp() }
                        )
                        .clearAndSetSemantics {
                            contentDescription = description
                            role = Role.Button
                            onClick {
                                act(i)
                                true
                            }
                        }
                )
            }
        }
    }
}

/** Le nom d'une tuile pour TalkBack (« Cercles 3 », « Vent d'est »…). */
@Composable
private fun mahjongTileName(kind: Int): String = when {
    kind < MahjongTiles.BAMBOO -> stringResource(R.string.mahjong_tile_dots, kind - MahjongTiles.DOTS + 1)
    kind < MahjongTiles.CHARACTERS -> stringResource(R.string.mahjong_tile_bamboo, kind - MahjongTiles.BAMBOO + 1)
    kind < MahjongTiles.WINDS -> stringResource(R.string.mahjong_tile_characters, kind - MahjongTiles.CHARACTERS + 1)
    kind < MahjongTiles.DRAGONS -> stringResource(
        when (kind - MahjongTiles.WINDS) {
            0 -> R.string.mahjong_tile_wind_east
            1 -> R.string.mahjong_tile_wind_south
            2 -> R.string.mahjong_tile_wind_west
            else -> R.string.mahjong_tile_wind_north
        }
    )
    kind < MahjongTiles.FLOWERS -> stringResource(
        when (kind - MahjongTiles.DRAGONS) {
            0 -> R.string.mahjong_tile_dragon_red
            1 -> R.string.mahjong_tile_dragon_green
            else -> R.string.mahjong_tile_dragon_white
        }
    )
    kind < MahjongTiles.SEASONS -> stringResource(R.string.mahjong_tile_flower, kind - MahjongTiles.FLOWERS + 1)
    else -> stringResource(R.string.mahjong_tile_season, kind - MahjongTiles.SEASONS + 1)
}

// ---------- Dessin des tuiles ----------

/** Une tuile : sa tranche (en bas à droite, ce qui lui donne du relief), sa face ivoire, son symbole, puis son voile et son contour. */
private fun DrawScope.drawMahjongTile(
    kind: Int,
    left: Float,
    top: Float,
    w: Float,
    h: Float,
    edge: Float,
    free: Boolean,
    selected: Boolean,
    hinted: Boolean
) {
    val corner = CornerRadius(w * 0.14f)
    val side = Path().apply {
        moveTo(left + w, top)
        lineTo(left + w + edge, top + edge)
        lineTo(left + w + edge, top + h + edge)
        lineTo(left + edge, top + h + edge)
        lineTo(left, top + h)
        lineTo(left + w, top + h)
        close()
    }
    drawPath(side, MahjongEdgeColor)
    drawRoundRect(if (selected) MahjongSelectedFaceColor else MahjongFaceColor, Offset(left, top), Size(w, h), corner)
    drawMahjongSymbol(kind, left, top, w, h)
    // Une tuile bloquée est assombrie : on voit d'un coup d'œil celles qu'on peut jouer.
    if (!free) drawRoundRect(Color.Black.copy(alpha = 0.24f), Offset(left, top), Size(w, h), corner)
    val border = when {
        selected -> MahjongSelectedBorderColor
        hinted -> MahjongHintBorderColor
        else -> null
    }
    if (border != null) {
        drawRoundRect(border, Offset(left, top), Size(w, h), corner, style = Stroke(width = w * 0.1f))
    } else {
        drawRoundRect(Color(0x33000000), Offset(left, top), Size(w, h), corner, style = Stroke(width = max(1f, w * 0.02f)))
    }
}

/** Le cadre où se dessine un symbole : un peu en retrait de la face de la tuile. */
private class SymbolBox(val l: Float, val t: Float, val w: Float, val h: Float) {
    val cx: Float get() = l + w / 2f
    val cy: Float get() = t + h / 2f
    fun x(fraction: Float): Float = l + w * fraction
    fun y(fraction: Float): Float = t + h * fraction
}

private fun DrawScope.drawMahjongSymbol(kind: Int, left: Float, top: Float, w: Float, h: Float) {
    val box = SymbolBox(left + w * 0.1f, top + h * 0.08f, w * 0.8f, h * 0.84f)
    when {
        kind < MahjongTiles.BAMBOO -> drawDots(kind - MahjongTiles.DOTS + 1, box)
        kind < MahjongTiles.CHARACTERS -> drawBamboo(kind - MahjongTiles.BAMBOO + 1, box)
        kind < MahjongTiles.WINDS -> {
            mahjongGlyph(MJ_NUMERALS[kind - MahjongTiles.CHARACTERS].toString(), MjBlack, box.cx, box.y(0.26f), box.w * 0.62f)
            mahjongGlyph("萬", MjRed, box.cx, box.y(0.74f), box.w * 0.7f)
        }
        kind < MahjongTiles.DRAGONS ->
            mahjongGlyph(MJ_WINDS[kind - MahjongTiles.WINDS].toString(), MjBlack, box.cx, box.cy, box.w * 1.05f)
        kind < MahjongTiles.FLOWERS -> drawDragon(kind - MahjongTiles.DRAGONS, box)
        kind < MahjongTiles.SEASONS -> drawBonus(kind - MahjongTiles.FLOWERS, season = false, box = box)
        else -> drawBonus(kind - MahjongTiles.SEASONS, season = true, box = box)
    }
}

/** Le crayon des signes écrits : un seul, réutilisé (le dessin se fait sur le fil principal). */
private val MahjongPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = Paint.Align.CENTER
    typeface = Typeface.DEFAULT_BOLD
}

/** Écrit [text] centré en ([cx], [cy]), en lettres de [textSize] pixels. */
private fun DrawScope.mahjongGlyph(text: String, color: Color, cx: Float, cy: Float, textSize: Float) {
    val paint = MahjongPaint
    paint.color = color.toArgb()
    paint.textSize = textSize
    drawContext.canvas.nativeCanvas.drawText(text, cx, cy - (paint.ascent() + paint.descent()) / 2f, paint)
}

// Un disque d'une tuile de cercles : sa place (en part du cadre) et sa couleur.
private class Pip(val x: Float, val y: Float, val color: Color)

// Les disques des tuiles de 2 à 9 (la tuile 1 a son grand disque à part).
private val MJ_DOT_PIPS: List<List<Pip>> = listOf(
    listOf(Pip(0.5f, 0.27f, MjGreen), Pip(0.5f, 0.73f, MjBlack)),
    listOf(Pip(0.24f, 0.2f, MjGreen), Pip(0.5f, 0.5f, MjRed), Pip(0.76f, 0.8f, MjBlack)),
    listOf(Pip(0.28f, 0.27f, MjGreen), Pip(0.72f, 0.27f, MjBlack), Pip(0.28f, 0.73f, MjBlack), Pip(0.72f, 0.73f, MjGreen)),
    listOf(
        Pip(0.28f, 0.2f, MjGreen), Pip(0.72f, 0.2f, MjBlack), Pip(0.5f, 0.5f, MjRed),
        Pip(0.28f, 0.8f, MjBlack), Pip(0.72f, 0.8f, MjGreen)
    ),
    listOf(
        Pip(0.28f, 0.2f, MjGreen), Pip(0.72f, 0.2f, MjGreen), Pip(0.28f, 0.5f, MjRed),
        Pip(0.72f, 0.5f, MjRed), Pip(0.28f, 0.8f, MjRed), Pip(0.72f, 0.8f, MjRed)
    ),
    listOf(
        Pip(0.2f, 0.12f, MjGreen), Pip(0.5f, 0.24f, MjGreen), Pip(0.8f, 0.36f, MjGreen),
        Pip(0.3f, 0.6f, MjRed), Pip(0.7f, 0.6f, MjRed), Pip(0.3f, 0.86f, MjRed), Pip(0.7f, 0.86f, MjRed)
    ),
    listOf(
        Pip(0.3f, 0.14f, MjBlack), Pip(0.7f, 0.14f, MjBlack), Pip(0.3f, 0.38f, MjBlack), Pip(0.7f, 0.38f, MjBlack),
        Pip(0.3f, 0.62f, MjBlack), Pip(0.7f, 0.62f, MjBlack), Pip(0.3f, 0.86f, MjBlack), Pip(0.7f, 0.86f, MjBlack)
    ),
    listOf(
        Pip(0.2f, 0.18f, MjGreen), Pip(0.5f, 0.18f, MjGreen), Pip(0.8f, 0.18f, MjGreen),
        Pip(0.2f, 0.5f, MjRed), Pip(0.5f, 0.5f, MjRed), Pip(0.8f, 0.5f, MjRed),
        Pip(0.2f, 0.82f, MjGreen), Pip(0.5f, 0.82f, MjGreen), Pip(0.8f, 0.82f, MjGreen)
    )
)

// Le rayon d'un disque, en part de la largeur du cadre, selon le nombre de disques (l'indice 0 ne sert pas).
private val MJ_DOT_RADIUS = floatArrayOf(0f, 0.47f, 0.26f, 0.2f, 0.2f, 0.17f, 0.15f, 0.13f, 0.12f, 0.12f)

private fun DrawScope.drawDots(count: Int, box: SymbolBox) {
    if (count == 1) {
        // Le grand disque : une rosace verte et rouge.
        val center = Offset(box.cx, box.cy)
        val radius = box.w * MJ_DOT_RADIUS[1]
        drawCircle(MjGreen, radius, center, style = Stroke(width = radius * 0.22f))
        drawCircle(MjRed, radius * 0.62f, center, style = Stroke(width = radius * 0.16f))
        drawCircle(MjGreen, radius * 0.3f, center)
        return
    }
    val radius = box.w * MJ_DOT_RADIUS[count]
    for (pip in MJ_DOT_PIPS[count - 2]) drawPip(Offset(box.x(pip.x), box.y(pip.y)), radius, pip.color)
}

/** Un disque : un anneau et un point au centre. */
private fun DrawScope.drawPip(center: Offset, radius: Float, color: Color) {
    drawCircle(color, radius, center, style = Stroke(width = radius * 0.3f))
    drawCircle(color, radius * 0.4f, center)
}

// Un bambou d'une tuile de bambous : sa place (en part du cadre), sa couleur et son inclinaison en degrés.
private class Stick(val x: Float, val y: Float, val color: Color, val tilt: Float = 0f)

// Les bambous des tuiles de 2 à 9 (la tuile 1 est un oiseau), et la hauteur d'un bambou en part du cadre.
private val MJ_STICKS: List<List<Stick>> = listOf(
    listOf(Stick(0.5f, 0.27f, MjGreen), Stick(0.5f, 0.73f, MjGreen)),
    listOf(Stick(0.5f, 0.27f, MjGreen), Stick(0.3f, 0.73f, MjGreen), Stick(0.7f, 0.73f, MjGreen)),
    listOf(Stick(0.3f, 0.27f, MjGreen), Stick(0.7f, 0.27f, MjGreen), Stick(0.3f, 0.73f, MjGreen), Stick(0.7f, 0.73f, MjGreen)),
    listOf(
        Stick(0.25f, 0.22f, MjGreen), Stick(0.75f, 0.22f, MjGreen), Stick(0.5f, 0.5f, MjRed),
        Stick(0.25f, 0.78f, MjGreen), Stick(0.75f, 0.78f, MjGreen)
    ),
    listOf(
        Stick(0.22f, 0.27f, MjGreen), Stick(0.5f, 0.27f, MjGreen), Stick(0.78f, 0.27f, MjGreen),
        Stick(0.22f, 0.73f, MjGreen), Stick(0.5f, 0.73f, MjGreen), Stick(0.78f, 0.73f, MjGreen)
    ),
    listOf(
        Stick(0.5f, 0.17f, MjRed),
        Stick(0.22f, 0.5f, MjGreen), Stick(0.5f, 0.5f, MjGreen), Stick(0.78f, 0.5f, MjGreen),
        Stick(0.22f, 0.83f, MjGreen), Stick(0.5f, 0.83f, MjGreen), Stick(0.78f, 0.83f, MjGreen)
    ),
    listOf(
        Stick(0.15f, 0.27f, MjGreen, 15f), Stick(0.38f, 0.27f, MjGreen, -15f), Stick(0.62f, 0.27f, MjGreen, 15f), Stick(0.85f, 0.27f, MjGreen, -15f),
        Stick(0.15f, 0.73f, MjGreen, -15f), Stick(0.38f, 0.73f, MjGreen, 15f), Stick(0.62f, 0.73f, MjGreen, -15f), Stick(0.85f, 0.73f, MjGreen, 15f)
    ),
    listOf(
        Stick(0.22f, 0.2f, MjGreen), Stick(0.5f, 0.2f, MjGreen), Stick(0.78f, 0.2f, MjGreen),
        Stick(0.22f, 0.5f, MjRed), Stick(0.5f, 0.5f, MjRed), Stick(0.78f, 0.5f, MjRed),
        Stick(0.22f, 0.8f, MjGreen), Stick(0.5f, 0.8f, MjGreen), Stick(0.78f, 0.8f, MjGreen)
    )
)
private val MJ_STICK_HEIGHT = floatArrayOf(0f, 0f, 0.4f, 0.4f, 0.4f, 0.34f, 0.4f, 0.3f, 0.4f, 0.28f)

private fun DrawScope.drawBamboo(count: Int, box: SymbolBox) {
    if (count == 1) {
        drawBird(box)
        return
    }
    val height = box.h * MJ_STICK_HEIGHT[count]
    for (stick in MJ_STICKS[count - 2]) drawStick(Offset(box.x(stick.x), box.y(stick.y)), box.w * 0.15f, height, stick.color, stick.tilt)
}

/** Un bambou : un bâton arrondi avec ses nœuds. */
private fun DrawScope.drawStick(center: Offset, width: Float, height: Float, color: Color, tilt: Float) {
    rotate(degrees = tilt, pivot = center) {
        drawRoundRect(color, Offset(center.x - width / 2f, center.y - height / 2f), Size(width, height), CornerRadius(width * 0.5f))
        val knot = lerp(color, Color.Black, 0.4f)
        drawLine(knot, Offset(center.x - width / 2f, center.y), Offset(center.x + width / 2f, center.y), strokeWidth = width * 0.2f)
        drawLine(Color.White.copy(alpha = 0.35f), Offset(center.x - width * 0.18f, center.y - height * 0.4f), Offset(center.x - width * 0.18f, center.y - height * 0.1f), strokeWidth = width * 0.14f)
    }
}

/** L'oiseau de la tuile « un bambou ». */
private fun DrawScope.drawBird(box: SymbolBox) {
    // La queue, le corps, la tête, la crête rouge, le bec et l'œil.
    drawLine(MjGreen, Offset(box.x(0.3f), box.y(0.6f)), Offset(box.x(0.1f), box.y(0.92f)), strokeWidth = box.w * 0.1f)
    drawLine(MjGreen, Offset(box.x(0.38f), box.y(0.64f)), Offset(box.x(0.3f), box.y(0.95f)), strokeWidth = box.w * 0.1f)
    drawOval(MjGreen, Offset(box.x(0.2f), box.y(0.36f)), Size(box.w * 0.56f, box.h * 0.4f))
    drawCircle(MjGreen, box.w * 0.15f, Offset(box.x(0.68f), box.y(0.3f)))
    drawCircle(MjRed, box.w * 0.08f, Offset(box.x(0.64f), box.y(0.16f)))
    val beak = Path().apply {
        moveTo(box.x(0.8f), box.y(0.26f))
        lineTo(box.x(0.98f), box.y(0.32f))
        lineTo(box.x(0.8f), box.y(0.38f))
        close()
    }
    drawPath(beak, MjRed)
    drawCircle(Color.White, box.w * 0.035f, Offset(box.x(0.7f), box.y(0.28f)))
    drawLine(MjBlack, Offset(box.x(0.42f), box.y(0.76f)), Offset(box.x(0.42f), box.y(0.88f)), strokeWidth = box.w * 0.045f)
    drawLine(MjBlack, Offset(box.x(0.58f), box.y(0.76f)), Offset(box.x(0.58f), box.y(0.88f)), strokeWidth = box.w * 0.045f)
    drawCircle(MjRed, box.w * 0.07f, Offset(box.x(0.42f), box.y(0.52f)))
}

/** Un dragon : le rouge (中), le vert (發) ou le blanc, un simple cadre. */
private fun DrawScope.drawDragon(index: Int, box: SymbolBox) {
    when (index) {
        0 -> mahjongGlyph("中", MjRed, box.cx, box.cy, box.w * 1.05f)
        1 -> mahjongGlyph("發", MjGreen, box.cx, box.cy, box.w * 1.05f)
        else -> {
            drawRoundRect(
                MjBlack, Offset(box.x(0.06f), box.y(0.08f)), Size(box.w * 0.88f, box.h * 0.84f),
                CornerRadius(box.w * 0.1f), style = Stroke(width = box.w * 0.09f)
            )
            drawRoundRect(
                MjBlack, Offset(box.x(0.22f), box.y(0.22f)), Size(box.w * 0.56f, box.h * 0.56f),
                CornerRadius(box.w * 0.06f), style = Stroke(width = box.w * 0.04f)
            )
        }
    }
}

/** Une fleur ou une saison : son signe, son numéro de 1 à 4 dans le coin, et une petite fleur. */
private fun DrawScope.drawBonus(index: Int, season: Boolean, box: SymbolBox) {
    val signs = if (season) MJ_SEASONS else MJ_FLOWERS
    mahjongGlyph(signs[index].toString(), if (season) MjRed else MjGreen, box.x(0.6f), box.y(0.2f), box.w * 0.58f)
    mahjongGlyph((index + 1).toString(), MjRed, box.x(0.14f), box.y(0.1f), box.w * 0.3f)
    drawBlossom(Offset(box.cx, box.y(0.7f)), box.w * 0.3f, if (season) MjOrange else MjPink)
}

/** Une petite fleur à cinq pétales, avec sa tige. */
private fun DrawScope.drawBlossom(center: Offset, radius: Float, petal: Color) {
    drawLine(MjGreen, Offset(center.x, center.y + radius * 0.6f), Offset(center.x, center.y + radius * 1.15f), strokeWidth = radius * 0.16f)
    for (k in 0 until 5) {
        val angle = (2.0 * PI * k / 5.0 - PI / 2.0).toFloat()
        drawCircle(petal, radius * 0.42f, Offset(center.x + cos(angle) * radius * 0.62f, center.y + sin(angle) * radius * 0.62f))
    }
    drawCircle(MjYellow, radius * 0.28f, center)
}

private fun findMahjongLifecycleOwner(context: Context): LifecycleOwner? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return null
}
