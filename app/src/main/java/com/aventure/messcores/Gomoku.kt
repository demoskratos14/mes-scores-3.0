package com.aventure.messcores

/** Identifiant du Gomoku parmi les jeux prédéfinis (écran à part : voir [DuoGameScreen]). */
internal const val GOMOKU_ID = "builtin_gomoku"

/**
 * Règles du Gomoku : sur une grille de 15 × 15 intersections, le premier qui aligne cinq pierres (ou plus) de sa
 * couleur gagne. Une grille est une liste de 225 cases, lues de gauche à droite et de haut en bas : 0 = vide,
 * 1 = pierre noire (premier joueur), 2 = pierre blanche (deuxième joueur).
 */
internal object Gomoku {

    const val SIZE = 15
    const val CELLS = SIZE * SIZE

    /** Nombre de pierres à aligner. */
    const val WIN = 5

    /** Grille vide, sous forme de texte (se garde tel quel quand l'écran tourne). */
    val EMPTY: String = "0".repeat(CELLS)

    // Ligne, colonne, diagonale descendante et diagonale montante : (variation de ligne, variation de colonne).
    private val DIRECTIONS = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)

    /** Grille lue depuis son texte ; un texte invalide donne une grille vide. */
    fun parse(text: String): List<Int> =
        if (text.length == CELLS && text.all { it in '0'..'2' }) text.map { it - '0' } else List(CELLS) { 0 }

    fun format(cells: List<Int>): String = cells.joinToString("")

    private fun inside(row: Int, col: Int) = row in 0 until SIZE && col in 0 until SIZE

    /** La ligne gagnante (cinq cases ou plus, dans l'ordre), ou null si personne n'a encore aligné cinq pierres. */
    fun winningLine(cells: List<Int>): List<Int>? {
        for (row in 0 until SIZE) {
            for (col in 0 until SIZE) {
                val value = cells[row * SIZE + col]
                if (value == 0) continue
                for ((dRow, dCol) in DIRECTIONS) {
                    // On ne part que du début d'une suite : la case d'avant n'est pas de la même couleur.
                    val before = (row - dRow) * SIZE + (col - dCol)
                    if (inside(row - dRow, col - dCol) && cells[before] == value) continue
                    val run = ArrayList<Int>()
                    var r = row
                    var c = col
                    while (inside(r, c) && cells[r * SIZE + c] == value) {
                        run.add(r * SIZE + c)
                        r += dRow
                        c += dCol
                    }
                    if (run.size >= WIN) return run
                }
            }
        }
        return null
    }

    /** 1 ou 2 pour le joueur qui a gagné, 0 sinon. */
    fun winner(cells: List<Int>): Int = winningLine(cells)?.let { cells[it[0]] } ?: 0

    fun isFull(cells: List<Int>): Boolean = cells.none { it == 0 }

    /** Grille après la pierre du joueur [player] en [index], ou null si le coup n'est pas permis (case prise, partie finie). */
    fun play(cells: List<Int>, index: Int, player: Int): List<Int>? {
        if (index !in 0 until CELLS || cells[index] != 0 || winner(cells) != 0) return null
        if (player != 1 && player != 2) return null
        return cells.toMutableList().also { it[index] = player }
    }

    /** Joueur à qui c'est de jouer : [first] (1 ou 2) commence, puis chacun son tour. */
    fun nextPlayer(cells: List<Int>, first: Int): Int =
        if (cells.count { it != 0 } % 2 == 0) first else 3 - first
}

/**
 * Une séance de Gomoku : la grille de la manche en cours, qui a commencé (0 : le premier joueur, 1 : le deuxième),
 * le score et la dernière pierre posée (-1 : aucune), mise en valeur à l'écran.
 */
internal data class GomokuMatch(
    val board: String = Gomoku.EMPTY,
    val starter: Int = 0,
    override val winsA: Int = 0,
    override val winsB: Int = 0,
    override val draws: Int = 0,
    val last: Int = -1
) : DuoMatch<GomokuMatch> {

    val cells: List<Int> get() = Gomoku.parse(board)

    override val winner: Int get() = Gomoku.winner(cells)

    override val isOver: Boolean get() = cells.let { Gomoku.winner(it) != 0 || Gomoku.isFull(it) }

    override val current: Int get() = Gomoku.nextPlayer(cells, starter + 1)

    override fun play(move: Int, player: Int): GomokuMatch? {
        if (player != current) return null
        val next = Gomoku.play(cells, move, player) ?: return null
        val text = Gomoku.format(next)
        return when {
            Gomoku.winner(next) == 1 -> copy(board = text, winsA = winsA + 1, last = move)
            Gomoku.winner(next) == 2 -> copy(board = text, winsB = winsB + 1, last = move)
            Gomoku.isFull(next) -> copy(board = text, draws = draws + 1, last = move)
            else -> copy(board = text, last = move)
        }
    }

    override fun newRound(): GomokuMatch? =
        if (isOver) copy(board = Gomoku.EMPTY, starter = 1 - starter, last = -1) else null

    override fun encode(): String = listOf(board, starter, winsA, winsB, draws, last).joinToString(";")

    companion object {
        /** La séance lue depuis son texte, ou null s'il est illisible ; un premier joueur ou un score absurde est ramené à une valeur permise. */
        fun decode(text: String): GomokuMatch? {
            val parts = text.split(";")
            if (parts.size != 6) return null
            val board = parts[0]
            if (board.length != Gomoku.CELLS || !board.all { it in '0'..'2' }) return null
            val last = parseDuoInt(parts[5], -1, Gomoku.CELLS - 1)
            return GomokuMatch(
                board = board,
                starter = parseDuoInt(parts[1], 0, 1),
                winsA = parseDuoInt(parts[2], 0),
                winsB = parseDuoInt(parts[3], 0),
                draws = parseDuoInt(parts[4], 0),
                last = last
            )
        }
    }
}
