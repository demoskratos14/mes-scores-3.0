package com.aventure.messcores

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

internal const val TOWER_DEFENSE_ID = "builtin_towerdefense"

/** Les quatre tours : leur coût, leur portée (en cases), leurs dégâts, leur cadence et leur effet. */
internal enum class TdTowerKind(
    val cost: Int,
    val range: Float,
    val damage: Float,
    val cooldownMs: Float,
    /** Rayon des dégâts de zone, en cases (0 : un seul ennemi touché). */
    val splash: Float,
    /** Vitesse des ennemis touchés (1 : aucun ralentissement) et durée du ralentissement. */
    val slowFactor: Float,
    val slowMs: Float
) {
    /** Tour de guet en bois : tire vite des flèches. */
    WOODEN(50, 2.6f, 9f, 550f, 0f, 1f, 0f),

    /** Tour de guet en brique : boules de feu lentes, qui blessent aussi les voisins de la cible. */
    BRICK(90, 2.3f, 32f, 1500f, 0.9f, 1f, 0f),

    /** Tour magique violette : ses bulles d'eau ralentissent les ennemis. */
    DEBUFF(70, 2.4f, 4f, 800f, 0f, 0.55f, 1800f),

    /** Tour magique verte : n'attaque pas, renforce les tours voisines (portée = rayon du renfort). */
    HEAL(80, 1.6f, 0f, 0f, 0f, 1f, 0f);

    val attacks: Boolean get() = this != HEAL
}

/** Les ennemis : points de vie de base, vitesse (cases par seconde), pièces gagnées et vies perdues à l'arrivée. */
internal enum class TdEnemyKind(val hp: Float, val speed: Float, val reward: Int, val livesLost: Int) {
    GOBLIN(60f, 1.4f, 9, 1),
    GOBLINA(50f, 1.7f, 9, 1),
    ROBBER(70f, 1.3f, 10, 1),
    ASSASSIN(40f, 1.9f, 8, 1),
    THUG(180f, 0.85f, 18, 1),
    CHIEF(900f, 0.7f, 120, 5)
}

internal class TdTower(val cell: Int, val kind: TdTowerKind, var level: Int, var cooldown: Float = 0f) {
    /** Argent déjà dépensé pour cette tour (achat et améliorations) : la vente en rend une part. */
    var invested: Int = kind.cost
}

internal class TdEnemy(val id: Int, val kind: TdEnemyKind, val maxHp: Float) {
    var hp: Float = maxHp
    /** Distance parcourue le long du chemin, en cases (0 : la première case). */
    var dist: Float = 0f
    var slowMs: Float = 0f
    var slowFactor: Float = 1f
    /** Négatif tant que l'ennemi est en vie ; sinon, temps écoulé depuis sa mort. */
    var dyingMs: Float = -1f
    val alive: Boolean get() = dyingMs < 0f
}

internal class TdProjectile(
    val kind: TdTowerKind,
    var x: Float,
    var y: Float,
    val targetId: Int,
    val damage: Float,
    var lastX: Float,
    var lastY: Float
) {
    /** Le cap du projectile, en degrés (0 : vers la droite, 90 : vers le bas), pour tourner l'image et l'éclat de l'impact. */
    var angle: Float = Math.toDegrees(atan2((lastY - y).toDouble(), (lastX - x).toDouble())).toFloat()
}

/**
 * Un effet qui s'éteint vite : l'impact d'un tir de la tour [kind] (tourné selon le cap [angle] du tir), ou, avec [death],
 * le nuage d'un ennemi qui meurt. Pur décor pour l'écran : [ms] reste à jouer sur [total].
 */
internal class TdFlash(
    val x: Float,
    val y: Float,
    val kind: TdTowerKind,
    var ms: Float,
    val total: Float,
    val angle: Float = 0f,
    val death: Boolean = false
)

/** Direction d'un ennemi à l'écran : l'ordre des lignes de marche dans les images (dos, face, gauche, droite). */
internal object TdDir {
    const val BACK = 0
    const val FRONT = 1
    const val LEFT = 2
    const val RIGHT = 3
}

/**
 * Une carte du tower defense : un plateau de [COLS] × [ROWS] cases, un chemin (liste de cases, de l'entrée en haut à la
 * sortie en bas), des cases libres où construire et des décors qui bloquent. Une même graine donne toujours la même carte.
 */
internal class TdMap private constructor(
    val seed: Int,
    /** Les cases du chemin, dans l'ordre (numéro = rangée × [COLS] + colonne). */
    val path: IntArray,
    /** Par case : −1 (rien) ou le rang du décor dans l'image des décors. Une case avec décor est bloquée. */
    val decor: IntArray
) {
    /** Le thème de la carte ([THEME_SWAMP] ou [THEME_SUMMER]) : il ne change que les images. Une graine donne toujours le même. */
    val theme: Int = themeOf(seed)

    private val onPath = BooleanArray(COLS * ROWS).also { grid -> path.forEach { grid[it] = true } }

    val entryCol: Int get() = path.first() % COLS
    val exitCol: Int get() = path.last() % COLS

    fun isPath(row: Int, col: Int): Boolean = row in 0 until ROWS && col in 0 until COLS && onPath[row * COLS + col]

    /** Vrai si on peut y construire : ni chemin ni décor. */
    fun isBuildable(row: Int, col: Int): Boolean =
        row in 0 until ROWS && col in 0 until COLS && !onPath[row * COLS + col] && decor[row * COLS + col] < 0

    /** Comme [isPath], mais le chemin se prolonge hors du plateau à l'entrée (en haut) et à la sortie (en bas). */
    private fun pathLike(row: Int, col: Int): Boolean = when {
        row < 0 -> col == entryCol
        row >= ROWS -> col == exitCol
        col < 0 || col >= COLS -> false
        else -> onPath[row * COLS + col]
    }

    /**
     * La signature de la tuile à poser sur la case : neuf bits (bit 0 coin haut-gauche, 1 haut, 2 coin haut-droit,
     * 3 gauche, 4 centre, 5 droite, 6 coin bas-gauche, 7 bas, 8 coin bas-droit), à 1 quand ce point est de la terre
     * claire (le chemin) et à 0 quand c'est du marais. Un coin n'est clair que si ses trois cases voisines le sont.
     */
    fun tileSignature(row: Int, col: Int): Int {
        if (!isPath(row, col)) return 0
        val up = pathLike(row - 1, col)
        val down = pathLike(row + 1, col)
        val left = pathLike(row, col - 1)
        val right = pathLike(row, col + 1)
        val bits = booleanArrayOf(
            up && left && pathLike(row - 1, col - 1), up, up && right && pathLike(row - 1, col + 1),
            left, true, right,
            down && left && pathLike(row + 1, col - 1), down, down && right && pathLike(row + 1, col + 1)
        )
        var sig = 0
        for (k in bits.indices) if (bits[k]) sig = sig or (1 shl k)
        return sig
    }

    /** Le rang (0 à 55) de la tuile du sol de la case dans l'image des tuiles. */
    fun tileIndex(row: Int, col: Int): Int {
        if (!isPath(row, col)) return TdTiles.SWAMP[(row + col) and 1]
        return TdTiles.best(tileSignature(row, col), theme)
    }

    companion object {
        const val COLS = 9
        const val ROWS = 14

        /** Les rangées horizontales du chemin : une toutes les trois rangées, pour laisser deux rangées de marais entre elles. */
        private val LANES = intArrayOf(2, 5, 8, 11)

        /** Le nombre de décors de l'image des décors qui servent de décor posé au hasard (les autres sont l'entrée et la sortie). */
        const val SCENERY_COUNT = 12
        /** L'entrée et la sortie : la grotte et le drapeau au marais, la tente et le château l'été. */
        const val DECOR_CAVE = 12
        const val DECOR_FLAG = 13

        const val THEME_SWAMP = 0
        const val THEME_SUMMER = 1

        /** Le thème de la graine : un bit de son mélange, pour que les graines voisines ne se suivent pas. */
        fun themeOf(seed: Int): Int = (seed * -1640531527) ushr 31

        fun generate(seed: Int): TdMap {
            val random = Random(seed)
            val cells = ArrayList<Int>()
            var col = 1 + random.nextInt(COLS - 2)
            for (row in 0..LANES[0]) cells.add(row * COLS + col)
            for (k in LANES.indices) {
                val lane = LANES[k]
                // Un nouveau virage au moins trois colonnes plus loin : le chemin ne frôle jamais un autre morceau de lui-même.
                val candidates = (1..COLS - 2).filter { abs(it - col) >= 3 }
                val target = candidates[random.nextInt(candidates.size)]
                val step = if (target > col) 1 else -1
                var c = col
                while (c != target) {
                    c += step
                    cells.add(lane * COLS + c)
                }
                col = target
                val next = if (k == LANES.lastIndex) ROWS - 1 else LANES[k + 1]
                for (row in lane + 1..next) cells.add(row * COLS + col)
            }
            val path = cells.toIntArray()
            val onPath = BooleanArray(COLS * ROWS).also { grid -> path.forEach { grid[it] = true } }
            val decor = IntArray(COLS * ROWS) { -1 }
            // L'entrée et la sortie portent leur repère à côté du chemin.
            decor[path.first() + 1] = DECOR_CAVE
            decor[path.last() - 1] = DECOR_FLAG
            // Des décors au hasard, jamais collés au chemin : il reste toujours des places pour les tours.
            for (row in 0 until ROWS) for (c in 0 until COLS) {
                val cell = row * COLS + c
                if (onPath[cell] || decor[cell] >= 0) continue
                val nearPath = (-1..1).any { dr -> (-1..1).any { dc ->
                    val r = row + dr
                    val cc = c + dc
                    r in 0 until ROWS && cc in 0 until COLS && onPath[r * COLS + cc]
                } }
                if (!nearPath && random.nextInt(100) < 14) decor[cell] = random.nextInt(SCENERY_COUNT)
            }
            return TdMap(seed, path, decor)
        }
    }
}

/** Les tuiles du sol : la signature (voir [TdMap.tileSignature]) de chacune des 56 tuiles de l'image des tuiles. */
internal object TdTiles {
    val SIGNATURES = intArrayOf(
        255, 319, 447, 219, 16, 438, 507, 504, 510, 432, 506, 216, 446, 511, 251, 54,
        191, 27, 416, 504, 200, 438, 511, 219, 38, 63, 11, 176, 184, 152, 178, 186,
        154, 50, 314, 26, 144, 146, 18, 304, 312, 88, 0, 248, 440, 315, 318, 160,
        56, 136, 146, 0, 146, 34, 56, 10
    )

    /** Les mêmes signatures pour les tuiles d'été (terre sèche = chemin, herbe = fond). */
    val SIGNATURES_SUMMER = intArrayOf(
        255, 63, 447, 219, 16, 438, 507, 504, 510, 432, 506, 216, 446, 511, 251, 54,
        191, 27, 416, 504, 200, 438, 511, 219, 38, 63, 11, 176, 184, 152, 178, 186,
        154, 50, 58, 26, 144, 146, 18, 48, 56, 24, 0, 248, 440, 59, 62, 160,
        56, 136, 146, 0, 146, 34, 56, 10
    )

    /** Les deux tuiles de fond unies (marais ou herbe), posées en alternance : les mêmes rangs dans les deux planches. */
    val SWAMP = intArrayOf(42, 51)

    /** La tuile dont la signature ressemble le plus à [signature] (le moins de points différents ; à égalité, la première). */
    fun best(signature: Int, theme: Int = TdMap.THEME_SWAMP): Int {
        val signatures = if (theme == TdMap.THEME_SUMMER) SIGNATURES_SUMMER else SIGNATURES
        var bestIndex = 0
        var bestScore = Int.MAX_VALUE
        for (i in signatures.indices) {
            val score = Integer.bitCount(signatures[i] xor signature)
            if (score < bestScore) {
                bestScore = score
                bestIndex = i
            }
        }
        return bestIndex
    }
}

internal object TowerDefense {
    const val START_GOLD = 150
    const val START_LIVES = 20
    const val MAX_LEVEL = 4
    const val SPAWN_MS = 900f
    const val DYING_MS = 500f
    const val FLASH_MS = 450f
    const val DEATH_FLASH_MS = 600f

    /** Durée du flash noir quand un ennemi atteint la base. */
    const val BASE_FLASH_MS = 500f
    const val PROJECTILE_SPEED = 9f

    /** Part de l'argent dépensé qu'on récupère en vendant une tour. */
    const val SELL_PERCENT = 60

    /** Plafond du renfort d'une tour par les tours vertes autour d'elle. */
    const val MAX_BOOST = 1f

    /** Les ennemis d'une vague : de plus en plus nombreux, de plus en plus variés ; un chef toutes les cinq vagues. */
    fun waveEnemies(wave: Int): List<TdEnemyKind> {
        val w = wave.coerceAtLeast(1)
        val pool = listOf(TdEnemyKind.GOBLIN, TdEnemyKind.GOBLINA, TdEnemyKind.ROBBER, TdEnemyKind.ASSASSIN, TdEnemyKind.THUG)
            .take(min(5, w))
        val list = ArrayList<TdEnemyKind>()
        repeat(min(40, 5 + 2 * w)) { list.add(pool[it % pool.size]) }
        if (w % 5 == 0) repeat(w / 5) { list.add(TdEnemyKind.CHIEF) }
        return list
    }

    /** Multiplicateur de points de vie à la vague [wave]. */
    fun hpFactor(wave: Int): Float {
        val n = (wave - 1).coerceAtLeast(0)
        return 1f + 0.2f * n + 0.02f * n * n
    }

    /** Coût de la tour [kind] à son niveau 1 : [TdTowerKind.cost]. Coût du passage du niveau [level] au suivant. */
    fun upgradeCost(kind: TdTowerKind, level: Int): Int = kind.cost * level

    fun damageOf(kind: TdTowerKind, level: Int): Float = kind.damage * (1f + 0.6f * (level - 1))

    fun rangeOf(kind: TdTowerKind, level: Int): Float = kind.range + 0.15f * (level - 1)

    /** Le renfort donné à une tour voisine par une tour verte de niveau [level]. */
    fun boostOf(level: Int): Float = 0.15f * level

    /** Les points d'un ennemi tué : dix fois sa récompense en pièces. */
    fun killPoints(kind: TdEnemyKind): Int = kind.reward * 10

    /** Argent et points de fin de vague. */
    fun waveBonusGold(wave: Int): Int = 20 + 5 * wave

    fun waveBonusPoints(wave: Int): Int = 100 * wave
}

internal class TdGame(random: Random = Random.Default, seed: Int = random.nextInt()) {

    var map: TdMap = TdMap.generate(seed)
        private set

    var wave = 0
        private set
    var gold = TowerDefense.START_GOLD
        private set
    var lives = TowerDefense.START_LIVES
        private set
    var score = 0
        private set

    /** Vrai pendant qu'une vague est lancée et pas finie (ennemis à venir ou en vie). */
    var waveActive = false
        private set

    /** Vrai pendant 1,5 s environ après la fin d'une vague, pour l'annoncer à l'écran. */
    var waveClearMs = 0f
        private set

    /** Temps restant du flash noir qui signale un ennemi arrivé à la base (0 : pas de flash), sur [TowerDefense.BASE_FLASH_MS]. */
    var baseFlashMs = 0f
        private set

    val towers = ArrayList<TdTower>()
    val enemies = ArrayList<TdEnemy>()
    val projectiles = ArrayList<TdProjectile>()
    val flashes = ArrayList<TdFlash>()

    /** Les ennemis qui restent à sortir dans la vague en cours, dans l'ordre. */
    internal val queue = ArrayList<TdEnemyKind>()
    private var spawnTimer = 0f
    private var nextId = 1

    val isOver: Boolean get() = lives <= 0

    /** Combien d'ennemis restent à sortir dans cette vague. */
    val pending: Int get() = queue.size

    fun reset(seed: Int) {
        map = TdMap.generate(seed)
        wave = 0
        gold = TowerDefense.START_GOLD
        lives = TowerDefense.START_LIVES
        score = 0
        waveActive = false
        waveClearMs = 0f
        baseFlashMs = 0f
        towers.clear()
        enemies.clear()
        projectiles.clear()
        flashes.clear()
        queue.clear()
        spawnTimer = 0f
        nextId = 1
    }

    fun towerAt(cell: Int): TdTower? = towers.firstOrNull { it.cell == cell }

    fun canBuild(row: Int, col: Int, kind: TdTowerKind): Boolean =
        !isOver && map.isBuildable(row, col) && towerAt(row * TdMap.COLS + col) == null && gold >= kind.cost

    fun build(row: Int, col: Int, kind: TdTowerKind): Boolean {
        if (!canBuild(row, col, kind)) return false
        gold -= kind.cost
        towers.add(TdTower(row * TdMap.COLS + col, kind, 1))
        return true
    }

    /** Le coût du prochain niveau de la tour, ou null si elle est au maximum. */
    fun upgradeCost(tower: TdTower): Int? =
        if (tower.level >= TowerDefense.MAX_LEVEL) null else TowerDefense.upgradeCost(tower.kind, tower.level)

    fun upgrade(tower: TdTower): Boolean {
        val cost = upgradeCost(tower) ?: return false
        if (isOver || gold < cost || tower !in towers) return false
        gold -= cost
        tower.invested += cost
        tower.level++
        return true
    }

    fun sellValue(tower: TdTower): Int = tower.invested * TowerDefense.SELL_PERCENT / 100

    fun sell(tower: TdTower): Boolean {
        if (isOver || !towers.remove(tower)) return false
        gold += sellValue(tower)
        return true
    }

    /** Lance la vague suivante ; sans effet si une vague est en cours ou si la partie est finie. */
    fun startWave(): Boolean {
        if (waveActive || isOver) return false
        wave++
        queue.clear()
        queue.addAll(TowerDefense.waveEnemies(wave))
        spawnTimer = 0f
        waveActive = true
        waveClearMs = 0f
        return true
    }

    /** Le renfort (0 à [TowerDefense.MAX_BOOST]) que reçoit la tour des tours vertes qui l'entourent. */
    fun boostFor(tower: TdTower): Float {
        if (tower.kind == TdTowerKind.HEAL) return 0f
        val tx = tower.cell % TdMap.COLS
        val ty = tower.cell / TdMap.COLS
        var boost = 0f
        for (other in towers) {
            if (other.kind != TdTowerKind.HEAL) continue
            val d = hypot((other.cell % TdMap.COLS - tx).toFloat(), (other.cell / TdMap.COLS - ty).toFloat())
            if (d <= TdTowerKind.HEAL.range) boost += TowerDefense.boostOf(other.level)
        }
        return min(boost, TowerDefense.MAX_BOOST)
    }

    /** Dégâts d'un tir de la tour, renfort compris. */
    fun damageOf(tower: TdTower): Float = TowerDefense.damageOf(tower.kind, tower.level) * (1f + boostFor(tower))

    // Position d'un ennemi, en cases (le centre de la case (rangée, colonne) est (colonne + 0,5 ; rangée + 0,5)).
    fun enemyX(e: TdEnemy): Float = pathPoint(e.dist).first
    fun enemyY(e: TdEnemy): Float = pathPoint(e.dist).second

    private fun pathPoint(dist: Float): Pair<Float, Float> {
        val path = map.path
        val last = path.size - 1
        val d = dist.coerceIn(0f, last.toFloat())
        val i = min(d.toInt(), last - 1)
        val f = d - i
        val ax = path[i] % TdMap.COLS + 0.5f
        val ay = path[i] / TdMap.COLS + 0.5f
        val bx = path[i + 1] % TdMap.COLS + 0.5f
        val by = path[i + 1] / TdMap.COLS + 0.5f
        return Pair(ax + (bx - ax) * f, ay + (by - ay) * f)
    }

    /** Vers où regarde l'ennemi (voir [TdDir]) : celle du morceau de chemin qu'il parcourt. */
    fun enemyDir(e: TdEnemy): Int {
        val path = map.path
        val i = min(e.dist.toInt(), path.size - 2)
        val dc = path[i + 1] % TdMap.COLS - path[i] % TdMap.COLS
        val dr = path[i + 1] / TdMap.COLS - path[i] / TdMap.COLS
        return when {
            dr > 0 -> TdDir.FRONT
            dr < 0 -> TdDir.BACK
            dc < 0 -> TdDir.LEFT
            else -> TdDir.RIGHT
        }
    }

    fun tick(ms: Long) {
        if (isOver) return
        val dt = ms.toFloat().coerceAtLeast(0f)
        if (waveClearMs > 0f) waveClearMs = max(0f, waveClearMs - dt)
        if (baseFlashMs > 0f) baseFlashMs = max(0f, baseFlashMs - dt)

        // Les ennemis sortent un par un.
        if (queue.isNotEmpty()) {
            spawnTimer -= dt
            if (spawnTimer <= 0f) {
                val kind = queue.removeAt(0)
                enemies.add(TdEnemy(nextId++, kind, (kind.hp * TowerDefense.hpFactor(wave)).roundToInt().toFloat()))
                spawnTimer += TowerDefense.SPAWN_MS
            }
        }

        // Les ennemis avancent, arrivent ou finissent de mourir.
        val last = (map.path.size - 1).toFloat()
        val iterator = enemies.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next()
            if (!e.alive) {
                e.dyingMs += dt
                if (e.dyingMs >= TowerDefense.DYING_MS) iterator.remove()
                continue
            }
            if (e.slowMs > 0f) {
                e.slowMs = max(0f, e.slowMs - dt)
                if (e.slowMs == 0f) e.slowFactor = 1f
            }
            e.dist += e.kind.speed * e.slowFactor * dt / 1000f
            if (e.dist >= last) {
                lives = max(0, lives - e.kind.livesLost)
                baseFlashMs = TowerDefense.BASE_FLASH_MS
                iterator.remove()
            }
        }

        // Les tours tirent.
        for (tower in towers) {
            if (!tower.kind.attacks) continue
            tower.cooldown = max(0f, tower.cooldown - dt)
            if (tower.cooldown > 0f) continue
            val boost = boostFor(tower)
            val tx = tower.cell % TdMap.COLS + 0.5f
            val ty = tower.cell / TdMap.COLS + 0.5f
            val range = TowerDefense.rangeOf(tower.kind, tower.level)
            // La cible : l'ennemi en vie le plus avancé dans la portée.
            var target: TdEnemy? = null
            for (e in enemies) {
                if (!e.alive) continue
                if (hypot(enemyX(e) - tx, enemyY(e) - ty) > range) continue
                if (target == null || e.dist > target.dist) target = e
            }
            if (target != null) {
                projectiles.add(
                    TdProjectile(tower.kind, tx, ty, target.id, TowerDefense.damageOf(tower.kind, tower.level) * (1f + boost), enemyX(target), enemyY(target))
                )
                tower.cooldown = tower.kind.cooldownMs / (1f + boost * 0.5f)
            }
        }

        // Les projectiles volent vers leur cible.
        val shots = projectiles.iterator()
        while (shots.hasNext()) {
            val p = shots.next()
            val target = enemies.firstOrNull { it.id == p.targetId && it.alive }
            if (target != null) {
                p.lastX = enemyX(target)
                p.lastY = enemyY(target)
            }
            val dx = p.lastX - p.x
            val dy = p.lastY - p.y
            val d = hypot(dx, dy)
            if (d > 0.001f) p.angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            val step = TowerDefense.PROJECTILE_SPEED * dt / 1000f
            if (d <= step) {
                if (target != null) hit(p, target)
                shots.remove()
            } else {
                p.x += dx / d * step
                p.y += dy / d * step
            }
        }

        // Les éclats s'éteignent.
        val sparks = flashes.iterator()
        while (sparks.hasNext()) {
            val f = sparks.next()
            f.ms -= dt
            if (f.ms <= 0f) sparks.remove()
        }

        // Fin de vague : plus rien à sortir et plus un ennemi en vie.
        if (waveActive && queue.isEmpty() && enemies.none { it.alive } && !isOver) {
            waveActive = false
            gold += TowerDefense.waveBonusGold(wave)
            score += TowerDefense.waveBonusPoints(wave)
            waveClearMs = 1500f
        }
    }

    private fun hit(p: TdProjectile, target: TdEnemy) {
        val kind = p.kind
        flashes.add(TdFlash(p.lastX, p.lastY, kind, TowerDefense.FLASH_MS, TowerDefense.FLASH_MS, p.angle))
        damage(target, p.damage)
        if (kind.splash > 0f) {
            for (e in enemies) {
                if (e === target || !e.alive) continue
                if (hypot(enemyX(e) - p.lastX, enemyY(e) - p.lastY) <= kind.splash) damage(e, p.damage * 0.6f)
            }
        }
        if (kind.slowMs > 0f && target.alive) {
            target.slowMs = kind.slowMs
            target.slowFactor = kind.slowFactor
        }
    }

    private fun damage(e: TdEnemy, amount: Float) {
        if (!e.alive) return
        e.hp -= amount
        if (e.hp <= 0f) {
            e.hp = 0f
            e.dyingMs = 0f
            flashes.add(TdFlash(enemyX(e), enemyY(e), TdTowerKind.WOODEN, TowerDefense.DEATH_FLASH_MS, TowerDefense.DEATH_FLASH_MS, death = true))
            gold += e.kind.reward
            score += TowerDefense.killPoints(e.kind)
        }
    }

    // ---- Sauvegarde : une liste d'entiers, pour garder la partie quand l'écran tourne. ----

    fun save(): List<Int> {
        val out = ArrayList<Int>()
        out.add(SAVE_VERSION)
        out.add(map.seed)
        out.add(wave)
        out.add(gold)
        out.add(lives)
        out.add(score)
        out.add(spawnTimer.toBits())
        out.add(if (waveActive) 1 else 0)
        out.add(nextId)
        out.add(queue.size)
        queue.forEach { out.add(it.ordinal) }
        out.add(towers.size)
        for (t in towers) {
            out.add(t.cell)
            out.add(t.kind.ordinal)
            out.add(t.level)
            out.add(t.cooldown.toBits())
            out.add(t.invested)
        }
        out.add(enemies.size)
        for (e in enemies) {
            out.add(e.id)
            out.add(e.kind.ordinal)
            out.add(e.maxHp.toBits())
            out.add(e.hp.toBits())
            out.add(e.dist.toBits())
            out.add(e.slowMs.toBits())
            out.add(e.slowFactor.toBits())
            out.add(e.dyingMs.toBits())
        }
        return out
    }

    companion object {
        private const val SAVE_VERSION = 1

        /** Relit une partie gardée par [save] ; null si la liste est abîmée (tailles, rangs ou valeurs impossibles). */
        fun restore(data: List<Int>, random: Random = Random.Default): TdGame? {
            var at = 0
            fun next(): Int = if (at < data.size) data[at++] else throw IllegalArgumentException("fin de la liste")
            return try {
                if (next() != SAVE_VERSION) return null
                val game = TdGame(random, next())
                game.wave = next()
                game.gold = next()
                game.lives = next()
                game.score = next()
                game.spawnTimer = Float.fromBits(next())
                game.waveActive = next() == 1
                game.nextId = next()
                if (game.wave < 0 || game.gold < 0 || game.lives !in 0..TowerDefense.START_LIVES || game.score < 0 || game.nextId < 1) return null
                if (!game.spawnTimer.isFinite()) return null
                val kinds = TdEnemyKind.values()
                val towerKinds = TdTowerKind.values()
                val queued = next()
                if (queued !in 0..200) return null
                repeat(queued) { game.queue.add(kinds[next()]) }
                val towerCount = next()
                if (towerCount !in 0..TdMap.COLS * TdMap.ROWS) return null
                repeat(towerCount) {
                    val cell = next()
                    val kind = towerKinds[next()]
                    val level = next()
                    val cooldown = Float.fromBits(next())
                    val invested = next()
                    if (cell !in 0 until TdMap.COLS * TdMap.ROWS || !game.map.isBuildable(cell / TdMap.COLS, cell % TdMap.COLS)) return null
                    if (game.towerAt(cell) != null || level !in 1..TowerDefense.MAX_LEVEL || !cooldown.isFinite() || invested < kind.cost) return null
                    game.towers.add(TdTower(cell, kind, level, cooldown).also { it.invested = invested })
                }
                val enemyCount = next()
                if (enemyCount !in 0..400) return null
                val last = (game.map.path.size - 1).toFloat()
                repeat(enemyCount) {
                    val id = next()
                    val kind = kinds[next()]
                    val maxHp = Float.fromBits(next())
                    val hp = Float.fromBits(next())
                    val dist = Float.fromBits(next())
                    val slowMs = Float.fromBits(next())
                    val slowFactor = Float.fromBits(next())
                    val dyingMs = Float.fromBits(next())
                    if (id !in 1 until game.nextId || game.enemies.any { it.id == id }) return null
                    if (!(maxHp > 0f && hp in 0f..maxHp && dist in 0f..last && slowMs >= 0f && slowFactor in 0.1f..1f && dyingMs.isFinite())) return null
                    game.enemies.add(TdEnemy(id, kind, maxHp).also {
                        it.hp = hp
                        it.dist = dist
                        it.slowMs = slowMs
                        it.slowFactor = slowFactor
                        it.dyingMs = dyingMs
                    })
                }
                if (at != data.size) return null
                game
            } catch (e: IllegalArgumentException) {
                null
            } catch (e: IndexOutOfBoundsException) {
                null
            }
        }
    }
}
