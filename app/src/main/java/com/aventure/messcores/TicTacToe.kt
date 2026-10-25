package com.aventure.messcores

/** Identifiant du Morpion parmi les jeux prédéfinis (écran à part : voir [TicTacToeScreen]). */
internal const val MORPION_ID = "builtin_morpion"

/**
 * Règles du Morpion (3 × 3, trois symboles alignés). Une grille est une liste de 9 cases, lues de gauche à droite
 * et de haut en bas : 0 = vide, 1 = croix (premier joueur), 2 = rond (deuxième joueur).
 */
internal object TicTacToe {

    const val CELLS = 9

    /** Grille vide, sous forme de texte (se garde tel quel quand l'écran tourne). */
    const val EMPTY = "000000000"

    /** Les huit lignes gagnantes : trois lignes, trois colonnes, deux diagonales. */
    val LINES: List<List<Int>> = listOf(
        listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
        listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
        listOf(0, 4, 8), listOf(2, 4, 6)
    )

    /** Grille lue depuis son texte ; un texte invalide donne une grille vide. */
    fun parse(text: String): List<Int> =
        if (text.length == CELLS && text.all { it in '0'..'2' }) text.map { it - '0' } else List(CELLS) { 0 }

    fun format(cells: List<Int>): String = cells.joinToString("")

    /** La ligne gagnante, ou null si personne n'a encore aligné trois symboles. */
    fun winningLine(cells: List<Int>): List<Int>? =
        LINES.firstOrNull { line -> cells[line[0]] != 0 && line.all { cells[it] == cells[line[0]] } }

    /** 1 ou 2 pour le joueur qui a gagné, 0 sinon. */
    fun winner(cells: List<Int>): Int = winningLine(cells)?.let { cells[it[0]] } ?: 0

    fun isFull(cells: List<Int>): Boolean = cells.none { it == 0 }

    /** Grille après le coup du joueur [player] en [index], ou null si le coup n'est pas permis (case prise, partie finie). */
    fun play(cells: List<Int>, index: Int, player: Int): List<Int>? {
        if (index !in 0 until CELLS || cells[index] != 0 || winner(cells) != 0) return null
        if (player != 1 && player != 2) return null
        return cells.toMutableList().also { it[index] = player }
    }

    /** Joueur à qui c'est de jouer : [first] (1 ou 2) commence, puis chacun son tour. */
    fun nextPlayer(cells: List<Int>, first: Int): Int =
        if (cells.count { it != 0 } % 2 == 0) first else 3 - first
}
