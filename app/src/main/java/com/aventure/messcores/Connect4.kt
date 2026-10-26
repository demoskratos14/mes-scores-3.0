package com.aventure.messcores

/** Identifiant du Puissance 4 parmi les jeux prédéfinis (écran à part : voir [Connect4Screen]). */
internal const val CONNECT4_ID = "builtin_connect4"

/**
 * Règles du Puissance 4 (grille de 7 colonnes × 6 rangées, quatre jetons alignés). Une grille est une liste de
 * 42 cases, lues de gauche à droite et de haut en bas (la rangée 0 est la plus haute) : 0 = vide, 1 = rouge
 * (premier joueur), 2 = jaune (deuxième joueur). Un jeton lâché dans une colonne tombe sur la case libre la plus basse.
 */
internal object Connect4 {

    const val COLUMNS = 7
    const val ROWS = 6
    const val CELLS = COLUMNS * ROWS

    /** Nombre de jetons à aligner pour gagner. */
    const val GOAL = 4

    /** Grille vide, sous forme de texte (se garde tel quel quand l'écran tourne). */
    val EMPTY: String = "0".repeat(CELLS)

    fun index(row: Int, column: Int): Int = row * COLUMNS + column

    /** Grille lue depuis son texte ; un texte invalide donne une grille vide. */
    fun parse(text: String): List<Int> =
        if (text.length == CELLS && text.all { it in '0'..'2' }) text.map { it - '0' } else List(CELLS) { 0 }

    fun format(cells: List<Int>): String = cells.joinToString("")

    /** Les rangées de la case libre la plus basse de [column] (de 0, en haut, à 5), ou -1 si la colonne est pleine. */
    fun landingRow(cells: List<Int>, column: Int): Int {
        if (column !in 0 until COLUMNS) return -1
        for (row in ROWS - 1 downTo 0) if (cells[index(row, column)] == 0) return row
        return -1
    }

    fun isColumnFull(cells: List<Int>, column: Int): Boolean = landingRow(cells, column) < 0

    // Quatre sens suffisent pour toutes les lignes : horizontal, vertical et les deux diagonales.
    private val DIRECTIONS = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)

    /** Les quatre cases de la première ligne gagnante trouvée, ou null si personne n'a encore aligné quatre jetons. */
    fun winningLine(cells: List<Int>): List<Int>? {
        for (row in 0 until ROWS) for (column in 0 until COLUMNS) {
            val player = cells[index(row, column)]
            if (player == 0) continue
            for ((dr, dc) in DIRECTIONS) {
                val endRow = row + dr * (GOAL - 1)
                val endColumn = column + dc * (GOAL - 1)
                if (endRow !in 0 until ROWS || endColumn !in 0 until COLUMNS) continue
                val line = (0 until GOAL).map { index(row + dr * it, column + dc * it) }
                if (line.all { cells[it] == player }) return line
            }
        }
        return null
    }

    /** 1 ou 2 pour le joueur qui a gagné, 0 sinon. */
    fun winner(cells: List<Int>): Int = winningLine(cells)?.let { cells[it[0]] } ?: 0

    fun isFull(cells: List<Int>): Boolean = cells.none { it == 0 }

    /**
     * Grille après le jeton du joueur [player] lâché dans [column], ou null si le coup n'est pas permis
     * (colonne inconnue ou pleine, partie déjà gagnée).
     */
    fun drop(cells: List<Int>, column: Int, player: Int): List<Int>? {
        if (player != 1 && player != 2 || winner(cells) != 0) return null
        val row = landingRow(cells, column)
        if (row < 0) return null
        return cells.toMutableList().also { it[index(row, column)] = player }
    }

    /** Joueur à qui c'est de jouer : [first] (1 ou 2) commence, puis chacun son tour. */
    fun nextPlayer(cells: List<Int>, first: Int): Int =
        if (cells.count { it != 0 } % 2 == 0) first else 3 - first
}

/**
 * Une séance de Puissance 4 : la grille de la manche en cours, qui a commencé (0 : le premier joueur, 1 : le deuxième)
 * et le score. Sert au Puissance 4 chacun sur son téléphone (voir [Connect4Online]) : l'organisateur la tient,
 * l'invité en reçoit une copie à chaque coup. Les rouges (1) sont joués par l'organisateur, les jaunes (2) par l'invité.
 */
internal data class Connect4Match(
    val board: String = Connect4.EMPTY,
    val starter: Int = 0,
    val winsRed: Int = 0,
    val winsYellow: Int = 0,
    val draws: Int = 0
) {
    val cells: List<Int> get() = Connect4.parse(board)

    val winner: Int get() = Connect4.winner(cells)

    val isOver: Boolean get() = winner != 0 || Connect4.isFull(cells)

    /** Joueur (1 ou 2) à qui c'est de jouer. */
    val current: Int get() = Connect4.nextPlayer(cells, starter + 1)

    /** La séance après le jeton de [player] lâché dans [column], ou null si ce n'est pas son tour ou si le coup n'est pas permis. */
    fun play(column: Int, player: Int): Connect4Match? {
        if (player != current) return null
        val next = Connect4.drop(cells, column, player) ?: return null
        return when {
            Connect4.winner(next) == 1 -> copy(board = Connect4.format(next), winsRed = winsRed + 1)
            Connect4.winner(next) == 2 -> copy(board = Connect4.format(next), winsYellow = winsYellow + 1)
            Connect4.isFull(next) -> copy(board = Connect4.format(next), draws = draws + 1)
            else -> copy(board = Connect4.format(next))
        }
    }

    /** Manche suivante (l'autre joueur commence), ou null tant que la manche n'est pas finie. */
    fun newRound(): Connect4Match? = if (isOver) copy(board = Connect4.EMPTY, starter = 1 - starter) else null
}
