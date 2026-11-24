package com.aventure.messcores

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuTest {

    private fun isValidSolution(grid: IntArray): Boolean {
        val full = (1..9).toSet()
        for (k in 0 until 9) {
            val row = (0 until 9).map { grid[k * 9 + it] }.toSet()
            val col = (0 until 9).map { grid[it * 9 + k] }.toSet()
            val box = (0 until 9).map { grid[(k / 3 * 3 + it / 3) * 9 + (k % 3 * 3 + it % 3)] }.toSet()
            if (row != full || col != full || box != full) return false
        }
        return true
    }

    private fun newGame(level: SudokuLevel = SudokuLevel.EASY, seed: Int = 1): SudokuGame {
        val puzzle = SudokuEngine.generate(level, Random(seed))
        return SudokuGame(level, puzzle.grid, puzzle.solution)
    }

    @Test
    fun uneGrilleGenereeAUneSolutionUniqueEtValide() {
        for (level in SudokuLevel.entries) {
            for (seed in 1..3) {
                val puzzle = SudokuEngine.generate(level, Random(seed))
                assertTrue(isValidSolution(puzzle.solution))
                assertTrue(SudokuEngine.hasUniqueSolution(puzzle.grid))
                for (i in 0 until 81) if (puzzle.grid[i] != 0) assertEquals(puzzle.solution[i], puzzle.grid[i])
                val clues = puzzle.grid.count { it != 0 }
                assertTrue("$level : $clues chiffres donnés", clues >= level.clues && clues <= level.clues + 6)
            }
        }
    }

    @Test
    fun lesNiveauxDonnentMoinsDeChiffresQuandIlsMontent() {
        assertTrue(SudokuLevel.EASY.clues > SudokuLevel.MEDIUM.clues)
        assertTrue(SudokuLevel.MEDIUM.clues > SudokuLevel.HARD.clues)
    }

    @Test
    fun leSolveurCompteLesSolutions() {
        assertEquals(2, SudokuEngine.countSolutions(IntArray(81), 2))
        val grid = SudokuEngine.generate(SudokuLevel.EASY, Random(5)).grid
        assertEquals(1, SudokuEngine.countSolutions(grid, 2))
        // Deux chiffres identiques sur une ligne : aucune solution.
        val broken = IntArray(81).also { it[0] = 5; it[1] = 5 }
        assertEquals(0, SudokuEngine.countSolutions(broken, 2))
        assertNull(SudokuEngine.solve(broken))
    }

    @Test
    fun uneCaseDonneeNeChangePas() {
        val game = newGame()
        val given = (0 until 81).first { game.isGiven(it) }
        val before = game.value(given)
        assertFalse(game.enterDigit(given, if (before == 9) 1 else before + 1))
        assertFalse(game.toggleNote(given, 3))
        assertEquals(before, game.value(given))
    }

    @Test
    fun poserUnChiffreEfface_lesNotes_etLePoserDeNouveauLEfface() {
        val game = newGame()
        val i = (0 until 81).first { !game.isGiven(it) }
        assertTrue(game.toggleNote(i, 2))
        assertTrue(game.toggleNote(i, 7))
        assertTrue(game.hasNote(i, 2) && game.hasNote(i, 7))
        assertTrue(game.enterDigit(i, 4))
        assertEquals(4, game.value(i))
        assertEquals(0, game.noteMask(i))
        assertTrue(game.enterDigit(i, 4))
        assertEquals(0, game.value(i))
    }

    @Test
    fun lesNotesSeBasculentEtRemplacentUnChiffrePose() {
        val game = newGame()
        val i = (0 until 81).first { !game.isGiven(it) }
        game.toggleNote(i, 1)
        game.toggleNote(i, 3)
        game.toggleNote(i, 5)
        game.toggleNote(i, 3)
        assertEquals((1 shl 1) or (1 shl 5), game.noteMask(i))
        game.enterDigit(i, 8)
        assertTrue(game.toggleNote(i, 2))
        assertEquals(0, game.value(i))
        assertEquals(1 shl 2, game.noteMask(i))
    }

    @Test
    fun unChiffreEnDoubleEstSignale() {
        val game = newGame()
        val given = (0 until 81).first { game.isGiven(it) }
        val peer = (0 until 81).first { !game.isGiven(it) && Sudoku.sees(given, it) }
        assertTrue(game.enterDigit(peer, game.value(given)))
        assertTrue(game.hasConflict(peer))
        assertTrue(game.hasConflict(given))
    }

    @Test
    fun remplirToutesLesCasesJustesGagneLaPartie() {
        val puzzle = SudokuEngine.generate(SudokuLevel.EASY, Random(3))
        val game = SudokuGame(SudokuLevel.EASY, puzzle.grid, puzzle.solution)
        val empty = (0 until 81).filter { !game.isGiven(it) }
        empty.dropLast(1).forEach { game.enterDigit(it, puzzle.solution[it]) }
        assertFalse(game.isFinished)
        game.tick(65_000)
        game.enterDigit(empty.last(), puzzle.solution[empty.last()])
        assertTrue(game.isFinished)
        assertEquals(Sudoku.scoreFor(SudokuLevel.EASY, 65), game.score)
        // Une fois gagnée, la grille ne bouge plus et le chrono s'arrête.
        assertFalse(game.enterDigit(empty.last(), 1))
        game.tick(1_000)
        assertEquals(65, game.seconds)
    }

    @Test
    fun leScoreRecompenseLaRapiditeEtLeNiveau() {
        val level = SudokuLevel.MEDIUM
        assertTrue(Sudoku.scoreFor(level, 100) > Sudoku.scoreFor(level, 400))
        assertEquals(level.basePoints, Sudoku.scoreFor(level, level.parSeconds + 500))
        assertTrue(Sudoku.scoreFor(SudokuLevel.HARD, 1800) > Sudoku.scoreFor(SudokuLevel.EASY, 600))
        val keys = SudokuLevel.entries.map { Sudoku.recordKey(it) }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun laPartieSeSauvegardeEtSeRestaure() {
        val game = newGame(SudokuLevel.MEDIUM, 9)
        val empty = (0 until 81).filter { !game.isGiven(it) }
        game.enterDigit(empty[0], 5)
        game.toggleNote(empty[1], 2)
        game.toggleNote(empty[1], 9)
        game.tick(12_345)
        val copy = SudokuGame.restore(game.save())
        assertNotNull(copy)
        copy!!
        assertEquals(game.level, copy.level)
        assertEquals(game.seconds, copy.seconds)
        for (i in 0 until 81) {
            assertEquals(game.value(i), copy.value(i))
            assertEquals(game.noteMask(i), copy.noteMask(i))
            assertEquals(game.isGiven(i), copy.isGiven(i))
        }
    }

    @Test
    fun uneSauvegardeAbimeeEstRefusee() {
        assertNull(SudokuGame.restore(listOf(99, 7)))
        val data = newGame().save().toMutableList()
        data[0] = 42
        assertNull(SudokuGame.restore(data))
    }
}
