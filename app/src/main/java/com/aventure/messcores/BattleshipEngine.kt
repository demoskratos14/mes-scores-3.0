package com.aventure.messcores

import kotlin.random.Random

/** Identifiant du jeu dans la liste : il a son propre écran, comme le Mastermind (voir [BattleshipScreen]). */
internal const val BATTLESHIP_ID = "builtin_battleship"

/** Réglages d'une partie de touché coulé : un seul pour l'instant, la règle du tir supplémentaire. */
data class BattleshipSettings(
    /** Vrai : un tir qui touche (ou coule) permet de tirer encore. Faux : on joue chacun son tour. */
    val shootAgainOnHit: Boolean = false
)

/** Réponse à un tir : dans l'eau, touché, ou touché et coulé (le bateau est alors entièrement touché). */
enum class ShotOutcome { MISS, HIT, SUNK }

/**
 * Un tir et la réponse qu'il a reçue. [sunk] : les cases du bateau coulé par ce tir (trié, vide sinon), pour que
 * le tireur sache lesquelles de ses cases touchées forment un bateau coulé sans en connaître la flotte.
 */
data class BattleshipShot(val cell: Int, val outcome: ShotOutcome, val sunk: List<Int> = emptyList())

enum class BattleshipPhase {
    /** Chaque joueur place sa flotte. */
    PLACEMENT,

    /** On tire à tour de rôle. */
    PLAYING,

    /** Une flotte est coulée : résultat. */
    FINISHED
}

/**
 * Une partie à deux sur le même téléphone, flottes comprises (voir [BattleshipViewModel]). Chacun sur son téléphone,
 * aucun téléphone ne connaît la flotte de l'autre : voir [BattleshipOnline].
 *
 * @param fleets flotte de chaque joueur (null tant qu'elle n'est pas placée) ; un bateau = la liste de ses cases.
 * @param shots tirs faits PAR chaque joueur, dans l'ordre, avec la réponse reçue : `shots[0]` vise la flotte du joueur 1.
 * @param turn joueur qui tire (phase [BattleshipPhase.PLAYING]).
 * @param winner joueur qui a coulé toute la flotte adverse, ou -1.
 */
data class BattleshipGame(
    val players: List<String>,
    val settings: BattleshipSettings,
    val phase: BattleshipPhase = BattleshipPhase.PLACEMENT,
    val fleets: List<List<List<Int>>?> = listOf(null, null),
    val shots: List<List<BattleshipShot>> = listOf(emptyList(), emptyList()),
    val turn: Int = 0,
    val winner: Int = -1
)

/**
 * Règles du touché coulé : grille de 10 × 10, flotte classique, réponse à un tir, fin de partie.
 * Une case se désigne par un entier : `ligne × 10 + colonne` (A1 = 0, J1 = 9, A2 = 10, J10 = 99).
 */
object Battleship {
    const val SIZE = 10
    const val CELLS = SIZE * SIZE

    /** Longueur de chaque bateau : porte-avions, croiseur, contre-torpilleur, sous-marin, torpilleur. */
    val FLEET: List<Int> = listOf(5, 4, 3, 3, 2)

    fun column(cell: Int): Int = cell % SIZE

    fun row(cell: Int): Int = cell / SIZE

    fun cellOf(row: Int, column: Int): Int = row * SIZE + column

    fun columnLetter(column: Int): Char = 'A' + column

    /** Nom d'une case, comme sur la grille : colonne en lettre puis ligne en chiffre (« C4 »). */
    fun cellName(cell: Int): String = "${columnLetter(column(cell))}${row(cell) + 1}"

    /**
     * Les cases d'un bateau de [length] cases dont la première est [start] : vers la droite si [horizontal], vers le
     * bas sinon. Null si le bateau sortirait de la grille.
     */
    fun shipCells(start: Int, length: Int, horizontal: Boolean): List<Int>? {
        if (start !in 0 until CELLS || length < 1) return null
        if (horizontal && column(start) + length > SIZE) return null
        if (!horizontal && row(start) + length > SIZE) return null
        return List(length) { if (horizontal) start + it else start + it * SIZE }
    }

    /** Vrai si [cells] forment une ligne droite, d'un seul tenant, dans la grille. */
    fun isStraightShip(cells: List<Int>): Boolean {
        if (cells.isEmpty() || cells.any { it !in 0 until CELLS }) return false
        val sorted = cells.sorted()
        if (sorted.distinct().size != sorted.size) return false
        if (sorted.size == 1) return true
        val horizontal = sorted.all { row(it) == row(sorted[0]) } && (1 until sorted.size).all { sorted[it] == sorted[it - 1] + 1 }
        val vertical = sorted.all { column(it) == column(sorted[0]) } && (1 until sorted.size).all { sorted[it] == sorted[it - 1] + SIZE }
        return horizontal || vertical
    }

    /** Vrai si [ships] est une flotte complète : les bons bateaux, en ligne droite, sans se chevaucher (ils peuvent se toucher). */
    fun isValidFleet(ships: List<List<Int>>): Boolean {
        if (ships.map { it.size }.sorted() != FLEET.sorted()) return false
        if (!ships.all { isStraightShip(it) }) return false
        val all = ships.flatten()
        return all.distinct().size == all.size
    }

    /** Une flotte placée au hasard, dans l'ordre de [FLEET] ; chaque bateau a ses cases triées. */
    fun randomFleet(random: Random = Random.Default): List<List<Int>> {
        while (true) {
            val occupied = HashSet<Int>()
            val ships = ArrayList<List<Int>>()
            for (length in FLEET) {
                val candidates = ArrayList<List<Int>>()
                for (horizontal in listOf(true, false)) {
                    for (start in 0 until CELLS) {
                        val cells = shipCells(start, length, horizontal) ?: continue
                        if (cells.none { it in occupied }) candidates.add(cells)
                    }
                }
                // Impasse (très rare) : on recommence la flotte depuis le début.
                if (candidates.isEmpty()) break
                val chosen = candidates[random.nextInt(candidates.size)]
                ships.add(chosen)
                occupied.addAll(chosen)
            }
            if (ships.size == FLEET.size) return ships
        }
    }

    /**
     * La réponse de la flotte [ships] à un tir sur [cell], sachant les tirs [previous] déjà reçus. Null si la case est
     * hors grille ou déjà jouée.
     */
    fun resolve(ships: List<List<Int>>, previous: List<BattleshipShot>, cell: Int): BattleshipShot? {
        if (cell !in 0 until CELLS || previous.any { it.cell == cell }) return null
        val ship = ships.firstOrNull { cell in it } ?: return BattleshipShot(cell, ShotOutcome.MISS)
        val fired = previous.map { it.cell }.toSet() + cell
        return if (fired.containsAll(ship)) {
            BattleshipShot(cell, ShotOutcome.SUNK, ship.sorted())
        } else {
            BattleshipShot(cell, ShotOutcome.HIT)
        }
    }

    /** Vrai si tous les bateaux de [ships] sont entièrement touchés par les tirs [shots]. */
    fun allSunk(ships: List<List<Int>>, shots: List<BattleshipShot>): Boolean {
        val fired = shots.map { it.cell }.toSet()
        return ships.isNotEmpty() && ships.all { fired.containsAll(it) }
    }

    /** Toutes les cases des bateaux coulés par [shots] (y compris celles touchées par des tirs précédents). */
    fun sunkCells(shots: List<BattleshipShot>): Set<Int> = shots.flatMap { it.sunk }.toSet()

    /** Le joueur qui tire après le tir [shot] de [shooter] : le même s'il touche et que la règle le permet, l'autre sinon. */
    fun nextShooter(settings: BattleshipSettings, shooter: Int, shot: BattleshipShot): Int =
        if (settings.shootAgainOnHit && shot.outcome != ShotOutcome.MISS) shooter else 1 - shooter

    /**
     * Vrai si toutes les réponses [shots] sont celles qu'aurait données la flotte [ships] : sert, en fin de partie, à
     * vérifier que l'adversaire a répondu honnêtement aux tirs.
     */
    fun isConsistent(ships: List<List<Int>>, shots: List<BattleshipShot>): Boolean {
        val previous = ArrayList<BattleshipShot>()
        for (shot in shots) {
            val expected = resolve(ships, previous, shot.cell) ?: return false
            if (expected != shot) return false
            previous.add(shot)
        }
        return true
    }

    // ---------- Partie à deux sur le même téléphone ----------

    fun newGame(players: List<String>, settings: BattleshipSettings, first: Int): BattleshipGame =
        BattleshipGame(players = players, settings = settings, turn = first.coerceIn(0, 1))

    /** La partie une fois la flotte de [player] placée ; la phase de tir commence quand les deux le sont. Null si refusée. */
    fun withFleet(game: BattleshipGame, player: Int, ships: List<List<Int>>): BattleshipGame? {
        if (game.phase != BattleshipPhase.PLACEMENT || player !in 0..1) return null
        if (game.fleets[player] != null || !isValidFleet(ships)) return null
        val fleets = game.fleets.toMutableList()
        fleets[player] = ships.map { it.sorted() }
        val ready = fleets.all { it != null }
        return game.copy(fleets = fleets, phase = if (ready) BattleshipPhase.PLAYING else BattleshipPhase.PLACEMENT)
    }

    /** La partie après le tir du joueur dont c'est le tour sur [cell]. Null si ce tir n'est pas permis. */
    fun fire(game: BattleshipGame, cell: Int): BattleshipGame? {
        if (game.phase != BattleshipPhase.PLAYING) return null
        val shooter = game.turn
        val fleet = game.fleets[1 - shooter] ?: return null
        val shot = resolve(fleet, game.shots[shooter], cell) ?: return null
        val shots = game.shots.toMutableList()
        shots[shooter] = shots[shooter] + shot
        return if (allSunk(fleet, shots[shooter])) {
            game.copy(shots = shots, phase = BattleshipPhase.FINISHED, winner = shooter)
        } else {
            game.copy(shots = shots, turn = nextShooter(game.settings, shooter, shot))
        }
    }
}
