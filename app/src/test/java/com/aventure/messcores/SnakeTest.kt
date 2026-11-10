package com.aventure.messcores

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnakeTest {

    private val cols = Snake.COLS
    private val rows = Snake.ROWS

    private fun cell(col: Int, row: Int) = row * cols + col

    private fun newGame(seed: Int = 1) = SnakeGame(Random(seed))

    // --- Le départ ---

    @Test
    fun leSerpentPartAuMilieuVersLaDroiteAvecTroisCasesEtUneSourisLibre() {
        val game = newGame()
        val row = rows / 2
        assertEquals(Snake.START_LENGTH, game.length)
        assertEquals(cell(2, row), game.head)
        assertEquals(listOf(cell(2, row), cell(1, row), cell(0, row)), game.bodyCells())
        assertEquals(SnakeDir.RIGHT, game.direction)
        assertTrue(game.mouse in 0 until cols * rows)
        assertFalse(game.isSnake(game.mouse % cols, game.mouse / cols))
        assertEquals(0, game.score)
        assertFalse(game.isOver)
    }

    // --- Les déplacements ---

    @Test
    fun leSerpentAvanceD_uneCaseParPasSansGrandir() {
        val game = newGame()
        game.placeMouseAt(cell(5, 0))
        val row = rows / 2
        game.step()
        assertEquals(listOf(cell(3, row), cell(2, row), cell(1, row)), game.bodyCells())
        assertEquals(Snake.START_LENGTH, game.length)
    }

    @Test
    fun leTempsFaitAvancerLeSerpentDAutantDeCasesQuePossible() {
        val game = newGame()
        game.placeMouseAt(cell(5, 0))
        val row = rows / 2
        game.tick(Snake.stepMs(1) * 2 + 5)
        assertEquals(cell(4, row), game.head)
        game.tick(Snake.stepMs(1) - 10)
        assertEquals(cell(4, row), game.head)
    }

    @Test
    fun sortirADroiteFaitRevenirAGauche() {
        val game = newGame()
        game.placeMouseAt(cell(5, 0))
        repeat(cols - 3) { game.step() } // la tête part de la colonne 2 : elle atteint la colonne 11
        assertEquals(cell(cols - 1, rows / 2), game.head)
        game.step()
        assertEquals(cell(0, rows / 2), game.head)
        assertFalse(game.isOver)
    }

    @Test
    fun sortirAGaucheFaitRevenirADroite() {
        assertEquals(cell(cols - 1, 4), Snake.wrap(0, 4, SnakeDir.LEFT).let { (c, r) -> cell(c, r) })
    }

    @Test
    fun sortirEnHautFaitRevenirEnBasEtInversement() {
        assertEquals(cell(3, rows - 1), Snake.wrap(3, 0, SnakeDir.UP).let { (c, r) -> cell(c, r) })
        assertEquals(cell(3, 0), Snake.wrap(3, rows - 1, SnakeDir.DOWN).let { (c, r) -> cell(c, r) })
    }

    @Test
    fun leSerpentPeutTraverserLeBordDuHautEnJouant() {
        val game = newGame()
        game.placeMouseAt(cell(5, 0))
        assertTrue(game.turn(SnakeDir.UP))
        repeat(rows / 2 + 1) { game.step() } // du milieu jusqu'en haut puis de l'autre côté
        assertEquals(cell(2, rows - 1), game.head)
        assertFalse(game.isOver)
    }

    // --- Les virages ---

    @Test
    fun unDemiTourEstRefuse() {
        val game = newGame()
        assertFalse(game.turn(SnakeDir.LEFT))
        assertFalse(game.turn(SnakeDir.RIGHT))
        assertTrue(game.turn(SnakeDir.UP))
        assertFalse(game.turn(SnakeDir.DOWN)) // opposé au virage déjà demandé
    }

    @Test
    fun deuxVirageRapidesSePrennentUnParCase() {
        val game = newGame()
        game.placeMouseAt(cell(5, 0))
        val row = rows / 2
        assertTrue(game.turn(SnakeDir.UP))
        assertTrue(game.turn(SnakeDir.LEFT)) // pas un demi-tour : il suit le virage vers le haut
        game.step()
        assertEquals(cell(2, row - 1), game.head)
        game.step()
        assertEquals(cell(1, row - 1), game.head)
        assertFalse(game.isOver)
    }

    @Test
    fun auPlusDeuxVirageSontGardesEnAttente() {
        val game = newGame()
        assertTrue(game.turn(SnakeDir.UP))
        assertTrue(game.turn(SnakeDir.LEFT))
        assertFalse(game.turn(SnakeDir.DOWN))
    }

    // --- La souris ---

    @Test
    fun mangerLaSourisFaitGrandirD_uneCaseEtRapporteDesPoints() {
        val game = newGame()
        val row = rows / 2
        game.placeMouseAt(cell(3, row))
        game.step()
        assertEquals(Snake.START_LENGTH + 1, game.length)
        assertEquals(cell(3, row), game.head)
        assertEquals(listOf(cell(3, row), cell(2, row), cell(1, row), cell(0, row)), game.bodyCells())
        assertEquals(1, game.eaten)
        assertEquals(Snake.MOUSE_POINTS, game.score)
    }

    @Test
    fun uneNouvelleSourisApparaitSurUneCaseLibre() {
        val game = newGame()
        val row = rows / 2
        game.placeMouseAt(cell(3, row))
        game.step()
        assertTrue(game.mouse != cell(3, row))
        assertFalse(game.isSnake(game.mouse % cols, game.mouse / cols))
    }

    @Test
    fun laSourisPeutEtreMangeeDeL_autreCoteDuBord() {
        val game = newGame()
        val row = rows / 2
        game.placeMouseAt(cell(cols - 1, 0))
        repeat(cols - 3) { game.step() } // la tête est à la colonne 11
        game.placeMouseAt(cell(0, row))
        game.step() // elle ressort à gauche, sur la souris
        assertEquals(cell(0, row), game.head)
        assertEquals(1, game.eaten)
        assertEquals(Snake.START_LENGTH + 1, game.length)
    }

    @Test
    fun uneSourisRapporteDixPointsParNiveau() {
        val row = rows / 2
        val game = SnakeGame.restore(
            listOf(2, SnakeDir.RIGHT.ordinal, cell(3, row), 0, Snake.MICE_PER_LEVEL, 0, 0, 0, 0, 0, 3,
                cell(2, row), cell(1, row), cell(0, row)),
            Random(1)
        )!!
        assertEquals(2, game.level)
        game.step()
        assertEquals(Snake.MOUSE_POINTS * 2, game.score)
    }

    @Test
    fun lesPointsMontentAvecLeNiveau() {
        assertEquals(1, Snake.levelFor(0))
        assertEquals(1, Snake.levelFor(Snake.MICE_PER_LEVEL - 1))
        assertEquals(2, Snake.levelFor(Snake.MICE_PER_LEVEL))
        assertEquals(Snake.MAX_LEVEL, Snake.levelFor(10_000))
        assertTrue(Snake.stepMs(2) < Snake.stepMs(1))
        assertTrue(Snake.stepMs(Snake.MAX_LEVEL) >= Snake.MIN_STEP_MS)
    }

    // --- La fin de partie ---

    @Test
    fun seMordreFinitLaPartie() {
        val game = newGame()
        val row = rows / 2
        // Grandit à 5 cases, puis fait un tour sur lui-même (haut, gauche, bas).
        game.placeMouseAt(cell(3, row)); game.step()
        game.placeMouseAt(cell(4, row)); game.step()
        game.placeMouseAt(cell(0, 0))
        assertEquals(5, game.length)
        game.turn(SnakeDir.UP); game.step()
        game.turn(SnakeDir.LEFT); game.step()
        game.turn(SnakeDir.DOWN); game.step()
        assertTrue(game.isOver)
    }

    @Test
    fun laTetePeutPrendreLaCaseQueLaQueueVientDeLiberer() {
        // Un carré de quatre cases : la tête (6,5) va vers le bas, sur la case (6,6) que la queue quitte au même pas.
        val game = SnakeGame.restore(
            listOf(2, SnakeDir.RIGHT.ordinal, cell(8, 8), 0, 0, 0, 0, 0, 0, 0, 4,
                cell(6, 5), cell(5, 5), cell(5, 6), cell(6, 6)),
            Random(1)
        )!!
        assertTrue(game.turn(SnakeDir.DOWN))
        game.step()
        assertFalse(game.isOver)
        assertEquals(cell(6, 6), game.head)
    }

    /** Un serpent qui occupe tout le plateau sauf la dernière case du parcours, où se trouve la souris (le prochain pas la mange). */
    private fun almostFullBoard(boardsCleared: Int = 0, score: Int = 0): SnakeGame {
        val all = ArrayList<Int>()
        // Parcours « boustrophédon » : chaque case est voisine de la suivante.
        for (r in 0 until rows) {
            val range = if (r % 2 == 0) 0 until cols else (cols - 1 downTo 0)
            for (c in range) all.add(cell(c, r))
        }
        val last = all.last()
        // La tête est à l'avant-dernière case du parcours, tournée vers la dernière ; la queue est la première case.
        val bodyCells = all.dropLast(1).reversed()
        val headCell = bodyCells.first()
        val dir = SnakeDir.entries.first { Snake.wrap(headCell % cols, headCell / cols, it) == (last % cols to last / cols) }
        return SnakeGame.restore(
            listOf(2, dir.ordinal, last, score, 0, 0, boardsCleared, 0, 0, 0, bodyCells.size) + bodyCells,
            Random(1)
        )!!
    }

    @Test
    fun remplirToutLePlateauRelanceUnePartieAvecUnBonusEtPlusDeVitesse() {
        val game = almostFullBoard(score = 40)
        game.step()
        assertFalse(game.isOver)
        assertEquals(1, game.boardsCleared)
        assertEquals(40 + Snake.MOUSE_POINTS * game.level + Snake.BOARD_BONUS, game.score)
        // Nouveau plateau : serpent de départ, une souris libre, et une vitesse supérieure à celle du départ.
        assertEquals(Snake.START_LENGTH, game.length)
        assertTrue(game.mouse in 0 until cols * rows)
        assertFalse(game.isSnake(game.mouse % cols, game.mouse / cols))
        assertEquals(Snake.stepMs(1 + Snake.SPEED_LEVELS_PER_BOARD, 1), game.stepMs)
        assertTrue(game.stepMs < Snake.stepMs(1))
    }

    @Test
    fun chaquePlateauRempliAbaisseLePlancherDeVitesse() {
        // Au plancher, un plateau de plus fait encore baisser le temps entre deux cases (jusqu'à la limite absolue).
        assertTrue(Snake.stepMs(100, 1) < Snake.stepMs(100, 0))
        assertTrue(Snake.stepMs(100, 3) < Snake.stepMs(100, 2))
        assertTrue(Snake.stepMs(100, 50) > 0)
    }

    @Test
    fun laVitesseRepartAuDepartDuNouveauPlateauPuisMonteAvecLesSouris() {
        val game = almostFullBoard(boardsCleared = 1)
        game.step()
        val start = game.speedLevel
        assertEquals(1 + Snake.SPEED_LEVELS_PER_BOARD * 2, start)
        // Chaque série de souris mangées sur ce plateau fait encore monter la vitesse.
        repeat(Snake.MICE_PER_LEVEL) {
            val head = game.head
            val next = Snake.wrap(head % cols, head / cols, game.direction).let { (c, r) -> cell(c, r) }
            game.placeMouseAt(next)
            game.step()
        }
        assertEquals(start + 1, game.speedLevel)
    }

    @Test
    fun unePartieFinieNeBougePlus() {
        val game = newGame()
        val row = rows / 2
        game.placeMouseAt(cell(3, row)); game.step()
        game.placeMouseAt(cell(4, row)); game.step()
        game.placeMouseAt(cell(0, 0))
        game.turn(SnakeDir.UP); game.step()
        game.turn(SnakeDir.LEFT); game.step()
        game.turn(SnakeDir.DOWN); game.step()
        assertTrue(game.isOver)
        val before = game.save()
        game.tick(10_000)
        game.step()
        assertFalse(game.turn(SnakeDir.UP))
        assertEquals(before, game.save())
    }

    @Test
    fun reset_remetLaPartieAZero() {
        val game = newGame()
        game.placeMouseAt(cell(3, rows / 2))
        game.step()
        game.reset()
        assertEquals(0, game.score)
        assertEquals(0, game.eaten)
        assertEquals(Snake.START_LENGTH, game.length)
        assertFalse(game.isOver)
    }

    // --- La sauvegarde ---

    @Test
    fun laSauvegardeSeRestaureIdentiquement() {
        val game = newGame(7)
        game.placeMouseAt(cell(3, rows / 2))
        game.step()
        game.turn(SnakeDir.UP)
        game.tick(100)
        val restored = SnakeGame.restore(game.save(), Random(7))
        assertNotNull(restored)
        assertEquals(game.save(), restored!!.save())
        assertEquals(game.bodyCells(), restored.bodyCells())
        assertEquals(game.mouse, restored.mouse)
        assertEquals(game.score, restored.score)
    }

    @Test
    fun desDonneesAbimeesSontRefusees() {
        val good = newGame().save()
        assertNull(SnakeGame.restore(emptyList()))
        assertNull(SnakeGame.restore(listOf(99) + good.drop(1)))
        assertNull(SnakeGame.restore(good.dropLast(1)))
        // La souris sur le corps.
        assertNull(SnakeGame.restore(good.toMutableList().also { it[2] = good.last() }))
        // Une case en dehors du plateau.
        assertNull(SnakeGame.restore(good.toMutableList().also { it[it.lastIndex] = cols * rows }))
        // Deux cases du corps qui ne se touchent pas.
        assertNull(SnakeGame.restore(good.toMutableList().also { it[it.lastIndex] = cell(7, 2) }))
    }
}
