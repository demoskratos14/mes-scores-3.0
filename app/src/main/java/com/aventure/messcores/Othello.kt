package com.aventure.messcores

import androidx.compose.runtime.saveable.Saver

/** Identifiant de l'Othello (Reversi) parmi les jeux prédéfinis (écran à part : voir [OthelloScreen]). */
internal const val OTHELLO_ID = "builtin_othello"

/**
 * Règles de l'Othello sur une grille de 8 × 8. Une grille est une liste de 64 cases, lues de gauche à droite et de
 * haut en bas : 0 = vide, 1 = noir (joue en premier), 2 = blanc.
 *
 * Un coup pose un pion sur une case vide, à condition d'encadrer au moins une ligne de pions adverses (en ligne,
 * en colonne ou en diagonale) entre ce pion et un autre pion de sa couleur : tous les pions encadrés changent de
 * couleur. Un joueur qui ne peut rien encadrer passe son tour ; la manche s'arrête quand aucun des deux ne peut jouer,
 * et celui qui a le plus de pions gagne.
 */
internal object Othello {

    const val SIZE = 8
    const val CELLS = SIZE * SIZE

    /** Position de départ : quatre pions au centre, en diagonale (blancs en d4 et e5, noirs en e4 et d5). */
    val START: String = "0".repeat(27) + "21" + "0".repeat(6) + "12" + "0".repeat(27)

    /** Les huit directions : (ligne, colonne). */
    private val DIRECTIONS: List<Pair<Int, Int>> = listOf(
        -1 to -1, -1 to 0, -1 to 1,
        0 to -1, 0 to 1,
        1 to -1, 1 to 0, 1 to 1
    )

    /** Grille lue depuis son texte ; un texte invalide donne la position de départ. */
    fun parse(text: String): List<Int> {
        val valid = if (text.length == CELLS && text.all { it in '0'..'2' }) text else START
        return valid.map { it - '0' }
    }

    fun format(cells: List<Int>): String = cells.joinToString("")

    /** Les cases dont les pions seraient retournés si [player] jouait en [index] ; vide si le coup n'est pas permis. */
    fun flips(cells: List<Int>, index: Int, player: Int): List<Int> {
        if (index !in 0 until CELLS || cells[index] != 0 || (player != 1 && player != 2)) return emptyList()
        val row = index / SIZE
        val col = index % SIZE
        val result = mutableListOf<Int>()
        for ((dr, dc) in DIRECTIONS) {
            val line = mutableListOf<Int>()
            var r = row + dr
            var c = col + dc
            while (r in 0 until SIZE && c in 0 until SIZE && cells[r * SIZE + c] == 3 - player) {
                line.add(r * SIZE + c)
                r += dr
                c += dc
            }
            // La ligne n'est encadrée que si elle se termine par un pion du joueur.
            if (line.isNotEmpty() && r in 0 until SIZE && c in 0 until SIZE && cells[r * SIZE + c] == player) {
                result.addAll(line)
            }
        }
        return result
    }

    /** Les cases où [player] peut jouer. */
    fun validMoves(cells: List<Int>, player: Int): List<Int> =
        (0 until CELLS).filter { flips(cells, it, player).isNotEmpty() }

    fun hasMove(cells: List<Int>, player: Int): Boolean =
        (0 until CELLS).any { flips(cells, it, player).isNotEmpty() }

    /** Grille après le coup de [player] en [index], ou null si le coup n'est pas permis (rien n'est encadré). */
    fun play(cells: List<Int>, index: Int, player: Int): List<Int>? {
        val flipped = flips(cells, index, player)
        if (flipped.isEmpty()) return null
        return cells.toMutableList().also { next ->
            next[index] = player
            flipped.forEach { next[it] = player }
        }
    }

    /** Nombre de pions de [player]. */
    fun count(cells: List<Int>, player: Int): Int = cells.count { it == player }

    /** Vrai quand plus personne ne peut jouer : grille pleine, ou deux joueurs bloqués. */
    fun isOver(cells: List<Int>): Boolean = !hasMove(cells, 1) && !hasMove(cells, 2)

    /** 1 (noir) ou 2 (blanc) pour celui qui a le plus de pions, 0 en cas d'égalité. */
    fun winner(cells: List<Int>): Int {
        val black = count(cells, 1)
        val white = count(cells, 2)
        return when {
            black > white -> 1
            white > black -> 2
            else -> 0
        }
    }
}

/**
 * Une séance d'Othello : la grille de la manche en cours, qui joue les noirs, à qui c'est de jouer et le score.
 * Sert à l'Othello chacun sur son téléphone (voir [OthelloOnline]) : l'organisateur la tient, l'invité en reçoit une
 * copie à chaque coup. L'organisateur est la place 0, l'invité la place 1.
 *
 * Les noirs commencent toujours : pour que ce soit équitable, c'est l'autre joueur qui prend les noirs à chaque
 * manche ([starter] : la place qui joue les noirs).
 */
internal data class OthelloMatch(
    val board: String = Othello.START,
    /** Place qui joue les noirs (et commence) pendant cette manche : 0 ou 1. */
    val starter: Int = 0,
    /** Couleur à qui c'est de jouer : 1 (noir) ou 2 (blanc). */
    val turn: Int = 1,
    /** Dernière case jouée, mise en valeur ; -1 en début de manche. */
    val last: Int = -1,
    /** Couleur qui vient de devoir passer son tour (0 : personne), pour l'annoncer aux joueurs. */
    val passed: Int = 0,
    val wins0: Int = 0,
    val wins1: Int = 0,
    val draws: Int = 0
) {
    val cells: List<Int> get() = Othello.parse(board)

    val isOver: Boolean get() = Othello.isOver(cells)

    /** 1 ou 2 pour la couleur qui a gagné, 0 en cas d'égalité (ou tant que la manche n'est pas finie). */
    val winnerColor: Int get() = if (isOver) Othello.winner(cells) else 0

    val blackCount: Int get() = Othello.count(cells, 1)

    val whiteCount: Int get() = Othello.count(cells, 2)

    /** Couleur (1 noir, 2 blanc) jouée par la place [seat] pendant cette manche. */
    fun colorOf(seat: Int): Int = if (seat == starter) 1 else 2

    /** Place (0 ou 1) qui joue la couleur [color] pendant cette manche. */
    fun seatOf(color: Int): Int = if (color == 1) starter else 1 - starter

    /** Place à qui c'est de jouer. */
    val currentSeat: Int get() = seatOf(turn)

    /** Les cases où la place [seat] peut jouer maintenant (vide si ce n'est pas son tour ou si la manche est finie). */
    fun validMoves(seat: Int): List<Int> =
        if (isOver || seat != currentSeat) emptyList() else Othello.validMoves(cells, turn)

    /**
     * La séance après le coup de la place [seat] en [index], ou null si ce n'est pas son tour ou si le coup n'est pas
     * permis. Si l'adversaire ne peut pas jouer ensuite, il passe son tour et la main reste à [seat] ([passed]).
     */
    fun play(index: Int, seat: Int): OthelloMatch? {
        if (isOver || seat != currentSeat) return null
        val next = Othello.play(cells, index, turn) ?: return null
        val opponent = 3 - turn
        val moved = when {
            Othello.hasMove(next, opponent) -> copy(board = Othello.format(next), turn = opponent, last = index, passed = 0)
            Othello.hasMove(next, turn) -> copy(board = Othello.format(next), last = index, passed = opponent)
            else -> copy(board = Othello.format(next), last = index, passed = 0)
        }
        if (!Othello.isOver(next)) return moved
        val winner = Othello.winner(next)
        return when {
            winner == 0 -> moved.copy(draws = draws + 1)
            seatOf(winner) == 0 -> moved.copy(wins0 = wins0 + 1)
            else -> moved.copy(wins1 = wins1 + 1)
        }
    }

    /** Manche suivante (les couleurs s'échangent), ou null tant que la manche n'est pas finie. */
    fun newRound(): OthelloMatch? =
        if (isOver) copy(board = Othello.START, starter = 1 - starter, turn = 1, last = -1, passed = 0) else null

    /** La séance sous forme de texte, pour la garder quand l'écran tourne (voir [OthelloMatchSaver]). */
    fun encode(): String = listOf(board, starter, turn, last, passed, wins0, wins1, draws).joinToString(",")

    companion object {
        /** La séance lue depuis son texte ; un texte illisible redonne une séance neuve. */
        fun decode(text: String): OthelloMatch {
            val parts = text.split(",")
            if (parts.size != 8) return OthelloMatch()
            val numbers = parts.drop(1).map { it.toIntOrNull() ?: return OthelloMatch() }
            return OthelloMatch(
                board = Othello.format(Othello.parse(parts[0])),
                starter = numbers[0].coerceIn(0, 1),
                turn = numbers[1].coerceIn(1, 2),
                last = numbers[2].coerceIn(-1, Othello.CELLS - 1),
                passed = numbers[3].coerceIn(0, 2),
                wins0 = numbers[4].coerceAtLeast(0),
                wins1 = numbers[5].coerceAtLeast(0),
                draws = numbers[6].coerceAtLeast(0)
            )
        }
    }
}

/** Pour garder une [OthelloMatch] quand l'écran tourne. */
internal val OthelloMatchSaver: Saver<OthelloMatch, String> =
    Saver(save = { it.encode() }, restore = { OthelloMatch.decode(it) })
