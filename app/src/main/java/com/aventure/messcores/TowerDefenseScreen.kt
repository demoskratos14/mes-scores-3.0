package com.aventure.messcores

import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt

// Les étapes de la séance.
private const val TD_PHASE_READY = 0 // on attend que le joueur lance la partie
private const val TD_PHASE_PLAYING = 1 // les vagues arrivent, le joueur construit
private const val TD_PHASE_OVER = 2 // la dernière vie est perdue : la partie est finie

/** Plus long temps d'une image pris en compte : après un à-coup, les ennemis ne doivent pas sauter plusieurs cases d'un coup. */
private const val TD_MAX_FRAME_MS = 100L

// Les images : une planche par sorte d'image (voir res/drawable-nodpi/td_*.webp).
private const val TD_TILE_PX = 64
private const val TD_TILE_COLS = 8
private const val TD_PROP_PX = 96
private const val TD_PROP_COLS = 8
private const val TD_TOWER_W = 96
private const val TD_TOWER_H = 128
private const val TD_FX_PX = 64
private const val TD_FX_FRAMES = 8
private const val TD_ENEMY_FRAMES = 10
private const val TD_ENEMY_DYING_FRAMES = 5

private val TD_SWAMP_COLOR = Color(0xFF5E8C3A)
private val TD_ROAD_COLOR = Color(0xFFA5C96B)

/** Garde la partie quand l'écran tourne : son état est rangé dans une liste d'entiers (voir [TdGame.save]). */
private val TdGameSaver = listSaver<TdGame, Int>(
    save = { it.save() },
    restore = { TdGame.restore(it) }
)

/** Les images du jeu, chargées une fois ; une image qui ne se charge pas vaut null et son dessin est simplement sauté. */
internal class TdSpriteSet(
    val tiles: ImageBitmap?,
    val towers: ImageBitmap?,
    val props: ImageBitmap?,
    val fx: ImageBitmap?,
    val enemies: Map<TdEnemyKind, ImageBitmap?>
)

internal object TdSprites {
    private var set: TdSpriteSet? = null

    @Synchronized
    fun get(context: Context): TdSpriteSet {
        set?.let { return it }
        fun load(id: Int): ImageBitmap? = try {
            BitmapFactory.decodeResource(context.resources, id)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
        val loaded = TdSpriteSet(
            tiles = load(R.drawable.td_tiles),
            towers = load(R.drawable.td_towers),
            props = load(R.drawable.td_props),
            fx = load(R.drawable.td_fx),
            enemies = mapOf(
                TdEnemyKind.GOBLIN to load(R.drawable.td_enemy_goblin),
                TdEnemyKind.GOBLINA to load(R.drawable.td_enemy_goblina),
                TdEnemyKind.ROBBER to load(R.drawable.td_enemy_robber),
                TdEnemyKind.ASSASSIN to load(R.drawable.td_enemy_assassin),
                TdEnemyKind.THUG to load(R.drawable.td_enemy_thug),
                TdEnemyKind.CHIEF to load(R.drawable.td_enemy_chief)
            )
        )
        set = loaded
        return loaded
    }
}

@StringRes
private fun tdTowerName(kind: TdTowerKind): Int = when (kind) {
    TdTowerKind.WOODEN -> R.string.td_tower_wooden
    TdTowerKind.BRICK -> R.string.td_tower_brick
    TdTowerKind.DEBUFF -> R.string.td_tower_debuff
    TdTowerKind.HEAL -> R.string.td_tower_heal
}

/**
 * Défense de tours en solo : des monstres du marais suivent un chemin vers le drapeau, le joueur construit et améliore des
 * tours sur les cases libres pour les arrêter. La carte change à chaque partie. La partie s'arrête à la dernière vie perdue,
 * et le meilleur score est gardé d'une séance à l'autre. Elle se met en pause quand l'appli passe en arrière-plan. Tout est
 * gardé quand l'écran tourne. [players] : le joueur dont le nom s'affiche au départ.
 */
@Composable
internal fun TowerDefenseScreen(players: List<String>) {
    val name = players.firstOrNull().orEmpty().ifEmpty { "1" }
    val context = LocalContext.current
    val game = rememberSaveable(saver = TdGameSaver) { TdGame() }
    var phase by rememberSaveable { mutableIntStateOf(TD_PHASE_READY) }
    var paused by rememberSaveable { mutableStateOf(false) }
    val record = rememberSoloRecord(TOWER_DEFENSE_ID)

    // La partie se met en pause quand l'appli passe en arrière-plan (et donc aussi quand l'écran tourne).
    val lifecycleOwner = remember(context) { findTdLifecycleOwner(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && phase == TD_PHASE_PLAYING) paused = true
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    // Le bouton retour met la partie en pause plutôt que de la quitter par mégarde.
    BackHandler(enabled = phase == TD_PHASE_PLAYING && !paused) { paused = true }

    fun startGame() {
        record.clearNew()
        game.reset(kotlin.random.Random.nextInt())
        paused = false
        phase = TD_PHASE_PLAYING
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (phase) {
            TD_PHASE_PLAYING -> TdPlayfield(
                game = game,
                paused = paused,
                onPausedChange = { paused = it },
                onOver = {
                    record.submit(game.score)
                    phase = TD_PHASE_OVER
                }
            )
            TD_PHASE_OVER -> TdCard {
                Text(
                    text = stringResource(R.string.td_over_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.td_score, game.score),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = highlightContent(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(highlightContainer())
                        .padding(12.dp)
                )
                Text(
                    text = stringResource(R.string.td_over_stats, (game.wave - 1).coerceAtLeast(0)),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                SoloRecordLine(record)
                Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.td_play_again))
                }
            }
            else -> TdCard {
                Text(
                    text = stringResource(R.string.td_ready_title, name),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.td_ready_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                SoloRecordLine(record)
                Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.td_go))
                }
            }
        }
        RecordSparkles(record)
    }
}

/** La carte des écrans de départ et de fin : le titre du jeu, ses règles, puis [content]. */
@Composable
private fun TdCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
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
                        text = stringResource(R.string.game_towerdefense),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    RulesButton(
                        gameId = TOWER_DEFENSE_ID,
                        gameName = stringResource(R.string.game_towerdefense),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                content()
            }
        }
    }
}

/**
 * La partie en cours : l'en-tête (score, vague, vies, pièces, pause), le plateau, puis le panneau du bas (construire sur une
 * case libre, améliorer ou vendre une tour, lancer la vague suivante). On touche une case pour la choisir. La partie avance à
 * chaque image tant qu'elle n'est pas en [paused] ; à la dernière vie perdue, [onOver] est appelé une fois.
 */
@Composable
private fun TdPlayfield(
    game: TdGame,
    paused: Boolean,
    onPausedChange: (Boolean) -> Unit,
    onOver: () -> Unit
) {
    KeepScreenOn()
    val context = LocalContext.current
    val sprites = remember(context) { TdSprites.get(context) }
    // Change à chaque image ou action : le dessin le lit et se refait, sans recomposer tout l'écran.
    var revision by remember { mutableIntStateOf(0) }
    // Change à chaque construction, amélioration ou vente : le panneau du bas se recompose.
    var structure by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(game.score) }
    var wave by remember { mutableIntStateOf(game.wave) }
    var lives by remember { mutableIntStateOf(game.lives) }
    var gold by remember { mutableIntStateOf(game.gold) }
    var waveActive by remember { mutableStateOf(game.waveActive) }
    var waveCleared by remember { mutableStateOf(game.waveClearMs > 0f) }
    // La case choisie (rangée × colonnes + colonne), −1 si aucune ; gardée quand l'écran tourne.
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    // Le temps de jeu écoulé, pour animer les images (marche des ennemis, projectiles).
    val clock = remember { LongArray(1) }
    val finished by rememberUpdatedState(onOver)
    val tileIndexes = remember(game.map) {
        IntArray(TdMap.COLS * TdMap.ROWS) { game.map.tileIndex(it / TdMap.COLS, it % TdMap.COLS) }
    }

    fun sync() {
        revision++
        score = game.score
        wave = game.wave
        lives = game.lives
        gold = game.gold
        waveActive = game.waveActive
        waveCleared = game.waveClearMs > 0f
    }

    /** Une action du joueur, sans effet en pause ou une fois la partie finie. */
    fun act(action: TdGame.() -> Unit) {
        if (paused || game.isOver) return
        game.action()
        structure++
        sync()
    }

    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (!game.isOver) {
            val now = withFrameNanos { it }
            val elapsed = ((now - last) / 1_000_000L).coerceIn(0L, TD_MAX_FRAME_MS)
            last = now
            clock[0] += elapsed
            game.tick(elapsed)
            sync()
        }
        finished()
    }

    // La marge de la barre de navigation (boutons ou geste) garde les commandes du bas hors de ses touches.
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
                        text = stringResource(R.string.td_score, score),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.td_stats, wave, lives, gold),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { onPausedChange(true) }, enabled = !paused) {
                    Text(stringResource(R.string.td_pause))
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Une case carrée, la plus grande qui laisse tenir le plateau (9 × 14).
            val cell = maxOf(minOf(maxWidth / TdMap.COLS, maxHeight / TdMap.ROWS), 0.dp)
            val boardDescription = stringResource(R.string.td_board_desc)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(cell * TdMap.COLS, cell * TdMap.ROWS)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TD_SWAMP_COLOR)
                    .semantics { contentDescription = boardDescription }
                    // Toucher une case la choisit (une case libre ou une tour) ; toucher la même case, ou ailleurs, la lâche.
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { at ->
                            val col = (at.x / (size.width / TdMap.COLS.toFloat())).toInt().coerceIn(0, TdMap.COLS - 1)
                            val row = (at.y / (size.height / TdMap.ROWS.toFloat())).toInt().coerceIn(0, TdMap.ROWS - 1)
                            val index = row * TdMap.COLS + col
                            val usable = game.map.isBuildable(row, col) || game.towerAt(index) != null
                            selected = if (usable && selected != index) index else -1
                            structure++
                            revision++
                        })
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawTdBoard(game, revision, sprites, tileIndexes, clock[0], selected)
                }
                if (waveCleared && !paused) {
                    Text(
                        text = stringResource(R.string.td_wave_clear),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                if (paused) {
                    Column(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.td_paused_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(8.dp)
                        )
                        Button(onClick = { onPausedChange(false) }) {
                            Text(stringResource(R.string.td_resume))
                        }
                    }
                }
            }
        }

        // Le panneau du bas : de la hauteur de l'info d'une tour, pour que le plateau ne saute pas quand on change de choix.
        Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // « structure » est lu ici pour que le panneau suive les constructions et les changements de choix.
                val tower = if (selected >= 0 && structure >= 0) game.towerAt(selected) else null
                when {
                    tower != null -> TdTowerPanel(
                        game = game,
                        tower = tower,
                        gold = gold,
                        onUpgrade = { act { upgrade(tower) } },
                        onSell = { act { sell(tower) } }
                    )
                    selected >= 0 -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        for (kind in TdTowerKind.values()) {
                            TdBuildButton(
                                kind = kind,
                                gold = gold,
                                modifier = Modifier.weight(1f),
                                onClick = { act { build(selected / TdMap.COLS, selected % TdMap.COLS, kind) } }
                            )
                        }
                    }
                    else -> Text(
                        text = stringResource(R.string.td_build_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Button(
                    onClick = { act { startWave() } },
                    enabled = !waveActive && !paused,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (waveActive) stringResource(R.string.td_wave_running)
                        else stringResource(R.string.td_next_wave, wave + 1)
                    )
                }
            }
        }
    }
}

/** Un bouton de construction : le nom de la tour et son prix ; grisé s'il manque des pièces. */
@Composable
private fun TdBuildButton(kind: TdTowerKind, gold: Int, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = gold >= kind.cost,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(tdTowerName(kind)),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Text(
                text = pluralStringResource(R.plurals.td_cost, kind.cost, kind.cost),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** L'info d'une tour choisie, avec ses boutons Améliorer et Vendre. */
@Composable
private fun TdTowerPanel(game: TdGame, tower: TdTower, gold: Int, onUpgrade: () -> Unit, onSell: () -> Unit) {
    val range = String.format(LocalLocale.current.platformLocale, "%.1f", TowerDefense.rangeOf(tower.kind, tower.level))
    Text(
        text = stringResource(R.string.td_tower_title, stringResource(tdTowerName(tower.kind)), tower.level),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = when (tower.kind) {
            TdTowerKind.HEAL -> stringResource(R.string.td_tower_heal_stats, (TowerDefense.boostOf(tower.level) * 100f).roundToInt())
            TdTowerKind.DEBUFF -> stringResource(R.string.td_tower_slow_stats, range)
            else -> stringResource(R.string.td_tower_stats, game.damageOf(tower).roundToInt(), range)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        val cost = game.upgradeCost(tower)
        Button(onClick = onUpgrade, enabled = cost != null && gold >= cost, modifier = Modifier.weight(1f)) {
            Text(if (cost == null) stringResource(R.string.td_upgrade_max) else stringResource(R.string.td_upgrade, cost))
        }
        OutlinedButton(onClick = onSell, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.td_sell, game.sellValue(tower)))
        }
    }
}

// ---- Dessin du plateau ----

/** Dessine un morceau de planche ([srcX], [srcY], [srcW], [srcH] en pixels de l'image) dans le rectangle ([x], [y], [w], [h]). */
private fun DrawScope.drawPiece(
    image: ImageBitmap,
    srcX: Int, srcY: Int, srcW: Int, srcH: Int,
    x: Float, y: Float, w: Float, h: Float,
    alpha: Float = 1f
) {
    drawImage(
        image = image,
        srcOffset = IntOffset(srcX, srcY),
        srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
        dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
        alpha = alpha,
        filterQuality = FilterQuality.Medium
    )
}

private fun DrawScope.drawTdBoard(
    game: TdGame,
    revision: Int,
    sprites: TdSpriteSet,
    tileIndexes: IntArray,
    clockMs: Long,
    selected: Int
) {
    // Lire la révision suffit à refaire le dessin à chaque image.
    if (revision < 0) return
    val cell = size.width / TdMap.COLS
    val map = game.map

    // Le sol : une tuile par case, sans trait entre elles (les bords sont arrondis à l'entier le plus proche).
    for (r in 0 until TdMap.ROWS) {
        val y0 = (r * cell).roundToInt()
        val y1 = ((r + 1) * cell).roundToInt()
        for (c in 0 until TdMap.COLS) {
            val x0 = (c * cell).roundToInt()
            val x1 = ((c + 1) * cell).roundToInt()
            val tile = tileIndexes[r * TdMap.COLS + c]
            val tiles = sprites.tiles
            if (tiles != null) {
                drawPiece(
                    tiles, (tile % TD_TILE_COLS) * TD_TILE_PX, (tile / TD_TILE_COLS) * TD_TILE_PX, TD_TILE_PX, TD_TILE_PX,
                    x0.toFloat(), y0.toFloat(), (x1 - x0).toFloat(), (y1 - y0).toFloat()
                )
            } else {
                drawRect(
                    if (map.isPath(r, c)) TD_ROAD_COLOR else TD_SWAMP_COLOR,
                    Offset(x0.toFloat(), y0.toFloat()),
                    Size((x1 - x0).toFloat(), (y1 - y0).toFloat())
                )
            }
        }
    }

    // La case choisie : un cadre, et la portée de la tour qui s'y trouve.
    if (selected >= 0) {
        val sr = selected / TdMap.COLS
        val sc = selected % TdMap.COLS
        val tower = game.towerAt(selected)
        if (tower != null) {
            val range = TowerDefense.rangeOf(tower.kind, tower.level) * cell
            val center = Offset((sc + 0.5f) * cell, (sr + 0.5f) * cell)
            drawCircle(Color.White.copy(alpha = 0.18f), range, center)
            drawCircle(Color.White.copy(alpha = 0.7f), range, center, style = Stroke(width = cell * 0.04f))
        }
        drawRect(
            Color.White.copy(alpha = 0.9f),
            Offset(sc * cell + cell * 0.04f, sr * cell + cell * 0.04f),
            Size(cell * 0.92f, cell * 0.92f),
            style = Stroke(width = cell * 0.05f)
        )
    }

    // Les décors et les tours, rangée par rangée : ce qui est plus bas passe devant.
    val props = sprites.props
    val towerSheet = sprites.towers
    for (r in 0 until TdMap.ROWS) {
        for (c in 0 until TdMap.COLS) {
            val decor = map.decor[r * TdMap.COLS + c]
            if (decor >= 0 && props != null) {
                drawPiece(
                    props, (decor % TD_PROP_COLS) * TD_PROP_PX, (decor / TD_PROP_COLS) * TD_PROP_PX, TD_PROP_PX, TD_PROP_PX,
                    c * cell, r * cell, cell, cell
                )
            }
        }
        for (tower in game.towers) {
            if (tower.cell / TdMap.COLS != r) continue
            val c = tower.cell % TdMap.COLS
            val h = cell * 1.45f
            val w = h * TD_TOWER_W / TD_TOWER_H
            if (towerSheet != null) {
                drawPiece(
                    towerSheet, (tower.level - 1) * TD_TOWER_W, tower.kind.ordinal * TD_TOWER_H, TD_TOWER_W, TD_TOWER_H,
                    (c + 0.5f) * cell - w / 2f, (r + 1f) * cell - h - cell * 0.02f, w, h
                )
            } else {
                drawRect(Color(0xFF8D6E63), Offset(c * cell + cell * 0.2f, r * cell + cell * 0.2f), Size(cell * 0.6f, cell * 0.6f))
            }
        }
    }

    // Les ennemis, du plus loin au plus proche du but : le plus avancé passe devant.
    for (enemy in game.enemies.sortedBy { it.dist }) drawTdEnemy(game, enemy, sprites, cell, clockMs)

    // Les projectiles.
    val fx = sprites.fx
    for (p in game.projectiles) {
        val frame = ((clockMs / 60L) % TD_FX_FRAMES).toInt()
        val row = when (p.kind) {
            TdTowerKind.WOODEN -> 0
            TdTowerKind.BRICK -> 2
            else -> 3
        }
        val side = if (p.kind == TdTowerKind.WOODEN) cell * 0.9f else cell * 0.5f
        val px = p.x * cell
        val py = p.y * cell
        if (fx != null) {
            val degrees = if (p.kind == TdTowerKind.WOODEN) {
                Math.toDegrees(atan2((p.lastY - p.y).toDouble(), (p.lastX - p.x).toDouble())).toFloat()
            } else 0f
            rotate(degrees, Offset(px, py)) {
                drawPiece(fx, frame * TD_FX_PX, row * TD_FX_PX, TD_FX_PX, TD_FX_PX, px - side / 2f, py - side / 2f, side, side)
            }
        } else {
            drawCircle(Color(0xFFFFB74D), cell * 0.1f, Offset(px, py))
        }
    }

    // Les éclats des impacts.
    for (f in game.flashes) {
        val k = (f.ms / TowerDefense.FLASH_MS).coerceIn(0f, 1f)
        val color = when (f.kind) {
            TdTowerKind.WOODEN -> Color(0xFFFFB74D)
            TdTowerKind.BRICK -> Color(0xFFFF7043)
            else -> Color(0xFF4FC3F7)
        }
        drawCircle(color.copy(alpha = 0.7f * k), cell * (0.2f + 0.5f * (1f - k)), Offset(f.x * cell, f.y * cell))
    }
}

private fun DrawScope.drawTdEnemy(game: TdGame, enemy: TdEnemy, sprites: TdSpriteSet, cell: Float, clockMs: Long) {
    val sheet = sprites.enemies[enemy.kind]
    val cx = game.enemyX(enemy) * cell
    val feet = game.enemyY(enemy) * cell + cell * 0.4f
    val h = if (enemy.kind == TdEnemyKind.CHIEF) cell * 1.25f else cell * 0.95f
    // Un halo bleu sous les pieds d'un ennemi ralenti.
    if (enemy.alive && enemy.slowMs > 0f) {
        drawCircle(Color(0xFF4FC3F7).copy(alpha = 0.45f), cell * 0.3f, Offset(cx, feet - cell * 0.05f))
    }
    if (sheet != null) {
        val cw = sheet.width / TD_ENEMY_FRAMES
        val ch = sheet.height / 5
        val row: Int
        val frame: Int
        var alpha = 1f
        if (enemy.alive) {
            row = game.enemyDir(enemy)
            frame = (((clockMs / 70L) + enemy.id * 3L) % TD_ENEMY_FRAMES).toInt()
        } else {
            row = 4
            frame = min(TD_ENEMY_DYING_FRAMES - 1, (enemy.dyingMs / (TowerDefense.DYING_MS / TD_ENEMY_DYING_FRAMES)).toInt())
            alpha = (1f - 0.7f * enemy.dyingMs / TowerDefense.DYING_MS).coerceIn(0.2f, 1f)
        }
        val w = h * cw / ch
        drawPiece(sheet, frame * cw, row * ch, cw, ch, cx - w / 2f, feet - h, w, h, alpha)
    } else {
        drawCircle(Color(0xFF43A047), cell * 0.3f, Offset(cx, feet - h / 2f))
    }
    // La barre de vie, seulement pour un ennemi blessé encore en vie.
    if (enemy.alive && enemy.hp < enemy.maxHp) {
        val barW = cell * 0.6f
        val barH = cell * 0.08f
        val left = cx - barW / 2f
        val top = feet - h - barH * 1.5f
        drawRect(Color(0xCC000000), Offset(left, top), Size(barW, barH))
        drawRect(Color(0xFFE53935), Offset(left, top), Size(barW * (enemy.hp / enemy.maxHp).coerceIn(0f, 1f), barH))
    }
}

private fun findTdLifecycleOwner(context: Context): LifecycleOwner? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return null
}
