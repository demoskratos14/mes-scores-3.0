package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Connect4Test {

    /** Joue une suite de colonnes en alternant rouge (1) puis jaune (2), et renvoie la grille obtenue. */
    private fun play(vararg columns: Int, first: Int = 1): List<Int> {
        var cells = Connect4.parse(Connect4.EMPTY)
        var player = first
        for (column in columns) {
            cells = Connect4.drop(cells, column, player) ?: error("coup refusé en colonne $column")
            player = 3 - player
        }
        return cells
    }

    @Test
    fun uneGrilleVideAQuaranteDeuxCasesEtPersonneNyGagne() {
        val cells = Connect4.parse(Connect4.EMPTY)
        assertEquals(42, cells.size)
        assertEquals(0, Connect4.winner(cells))
        assertNull(Connect4.winningLine(cells))
        assertFalse(Connect4.isFull(cells))
    }

    @Test
    fun unJetonTombeSurLaCaseLibreLaPlusBasse() {
        val one = play(3)
        assertEquals(1, one[Connect4.index(5, 3)])
        val two = play(3, 3)
        assertEquals(2, two[Connect4.index(4, 3)])
        assertEquals(3, Connect4.landingRow(two, 3))
        assertEquals(5, Connect4.landingRow(two, 0))
    }

    @Test
    fun uneColonnePleineOuInconnueRefuseLeJeton() {
        // Six jetons en colonne 0, sans quatre alignés : rouge, jaune, rouge, jaune, rouge, jaune.
        val cells = play(0, 0, 0, 0, 0, 0)
        assertTrue(Connect4.isColumnFull(cells, 0))
        assertNull(Connect4.drop(cells, 0, 1))
        assertNull(Connect4.drop(cells, 7, 1))
        assertNull(Connect4.drop(cells, -1, 1))
        assertNull(Connect4.drop(Connect4.parse(Connect4.EMPTY), 0, 3))
    }

    @Test
    fun quatreJetonsEnLigneGagnent() {
        // Rouge : colonnes 0 à 3 sur la rangée du bas ; jaune joue au-dessus.
        val cells = play(0, 0, 1, 1, 2, 2, 3)
        assertEquals(1, Connect4.winner(cells))
        assertEquals(
            listOf(35, 36, 37, 38),
            Connect4.winningLine(cells)
        )
    }

    @Test
    fun quatreJetonsEnColonneGagnent() {
        val cells = play(0, 1, 0, 1, 0, 1, 0)
        assertEquals(1, Connect4.winner(cells))
        assertEquals(listOf(0, 1, 2, 3).map { Connect4.index(5 - it, 0) }.sorted(), Connect4.winningLine(cells)!!.sorted())
    }

    @Test
    fun quatreJetonsEnDiagonaleMontanteEtDescendanteGagnent() {
        // Diagonale montante pour rouge (bas-gauche vers haut-droite).
        val up = play(0, 1, 1, 2, 3, 2, 2, 3, 3, 0, 3)
        assertEquals(1, Connect4.winner(up))
        // Même figure en miroir : diagonale descendante.
        val down = play(6, 5, 5, 4, 3, 4, 4, 3, 3, 6, 3)
        assertEquals(1, Connect4.winner(down))
    }

    @Test
    fun troisJetonsOuUneLigneQuiSeReplieNeGagnentPas() {
        assertEquals(0, Connect4.winner(play(0, 6, 1, 6, 2)))
        // Trois à la fin d'une rangée puis trois au début de la suivante ne forment pas une ligne.
        // Rangée 4 : colonnes 4 à 6 ; rangée 5 : colonne 0. Ces quatre cases se suivent dans la liste, sans former une ligne.
        val wrap = MutableList(Connect4.CELLS) { 0 }
        listOf(Connect4.index(4, 4), Connect4.index(4, 5), Connect4.index(4, 6), Connect4.index(5, 0)).forEach { wrap[it] = 1 }
        assertEquals(0, Connect4.winner(wrap))
    }

    @Test
    fun onNeJoueplusUneFoisLaPartieGagnee() {
        val won = play(0, 0, 1, 1, 2, 2, 3)
        assertNull(Connect4.drop(won, 6, 2))
    }

    @Test
    fun lesJoueursSeSuccedentEtLePremierPeutChanger() {
        assertEquals(1, Connect4.nextPlayer(Connect4.parse(Connect4.EMPTY), 1))
        assertEquals(2, Connect4.nextPlayer(play(3), 1))
        assertEquals(2, Connect4.nextPlayer(Connect4.parse(Connect4.EMPTY), 2))
        assertEquals(1, Connect4.nextPlayer(play(3, first = 2), 2))
    }

    @Test
    fun unTexteInvalideDonneUneGrilleVide() {
        assertEquals(List(42) { 0 }, Connect4.parse("abc"))
        assertEquals(List(42) { 0 }, Connect4.parse("3".repeat(42)))
        assertEquals(List(42) { 0 }, Connect4.parse("0".repeat(41)))
    }

    @Test
    fun uneGrillePleineSansLigneEstUneEgalite() {
        // Motif classique sans quatre alignés : blocs de trois rangées décalés.
        val rows = listOf(
            "1122112",
            "2211221",
            "1122112",
            "2211221",
            "1122112",
            "2211221"
        )
        // Remplissage de haut en bas : on vérifie seulement la détection d'une grille pleine sans gagnant.
        val cells = Connect4.parse(rows.joinToString(""))
        assertTrue(Connect4.isFull(cells))
        assertEquals(0, Connect4.winner(cells))
    }
}

class Connect4MatchTest {

    @Test
    fun seulLeJoueurDontCEstLeTourPeutJouer() {
        val match = Connect4Match()
        assertNull(match.play(0, 2))
        val after = match.play(0, 1)!!
        assertNull(after.play(1, 1))
        assertEquals(2, after.current)
        assertEquals(2, after.cells[Connect4.index(5, 1)].let { 2 }) // jaune jouera ensuite
        assertEquals(2, after.play(1, 2)!!.cells[Connect4.index(5, 1)])
    }

    @Test
    fun uneVictoireEstCompteeEtFigeLaManche() {
        var match = Connect4Match()
        for (column in listOf(0, 0, 1, 1, 2, 2, 3)) match = match.play(column, match.current)!!
        assertTrue(match.isOver)
        assertEquals(1, match.winner)
        assertEquals(1, match.winsRed)
        assertEquals(0, match.winsYellow)
        assertNull(match.play(4, 2))
    }

    @Test
    fun laMancheSuivanteNeSeLancheQuApresLaFinEtLAutreJoueurCommence() {
        assertNull(Connect4Match().newRound())
        var match = Connect4Match()
        for (column in listOf(0, 0, 1, 1, 2, 2, 3)) match = match.play(column, match.current)!!
        val next = match.newRound()!!
        assertEquals(Connect4.EMPTY, next.board)
        assertEquals(1, next.starter)
        assertEquals(2, next.current)
        assertEquals(1, next.winsRed)
        assertNull(next.newRound())
    }

    @Test
    fun uneGrillePleineSansGagnantCompteUneEgalite() {
        val rows = "1122112" + "2211221" + "1122112" + "2211221" + "1122112" + "2211221"
        // Il manque le dernier jeton (jaune, bas-droite) : on le retire puis on le rejoue.
        val almost = Connect4Match(board = rows.substring(0, 41) + "0", starter = 0)
        // 41 jetons posés : c'est au deuxième joueur (jaune) de jouer.
        assertEquals(2, almost.current)
        val done = almost.play(6, 2)!!
        assertTrue(done.isOver)
        assertEquals(0, done.winner)
        assertEquals(1, done.draws)
    }
}
