package com.aventure.messcores

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinesweeperTest {

    private val level = MineLevel.EASY
    private val cols = level.cols

    private fun cell(col: Int, row: Int) = row * cols + col

    /** Les 10 mines du niveau facile : toute la ligne du bas (9 mines) et la case (0, 7) au-dessus de son coin. */
    private fun cornerGame(): MinesweeperGame {
        val mines = (0 until 9).map { cell(it, 8) } + cell(0, 7)
        return MinesweeperGame.withMines(level, mines)
    }

    /**
     * Une partie sur le plateau de [cornerGame] qui ne peut pas être gagnée d'un seul dévoilement : ce plateau n'a que 71 cases
     * sans mine et la cascade depuis le haut les ouvre toutes, ce qui gagne la partie (et pose alors les drapeaux). Le drapeau
     * posé sur la case (0, 0) la garde fermée : la cascade ne l'ouvre jamais.
     */
    private fun unfinishedCornerGame(): MinesweeperGame = cornerGame().also { it.toggleFlag(0, 0) }

    private fun countMines(game: MinesweeperGame): Int =
        (0 until game.rows).sumOf { r -> (0 until game.cols).count { c -> game.isMine(c, r) } }

    // --- Le départ ---

    @Test
    fun lesMinesNeSontPosees_QueAuPremierDevoilement() {
        val game = MinesweeperGame(level, Random(1))
        assertEquals(MinesweeperGame.READY, game.state)
        assertEquals(0, countMines(game))
        assertTrue(game.reveal(4, 4))
        assertEquals(MinesweeperGame.PLAYING, game.state)
        assertEquals(level.mines, countMines(game))
    }

    @Test
    fun laPremiereCaseEtSesVoisinesSontToujoursSansMine() {
        for (seed in 1..40) {
            for ((c, r) in listOf(0 to 0, 4 to 4, 8 to 8, 0 to 5)) {
                val game = MinesweeperGame(level, Random(seed))
                game.reveal(c, r)
                assertFalse(game.isFinished)
                for (dr in -1..1) for (dc in -1..1) {
                    if (game.inBounds(c + dc, r + dr)) assertFalse(game.isMine(c + dc, r + dr))
                }
            }
        }
    }

    @Test
    fun chaqueNiveauPlaceSonNombreDeMines() {
        for (lv in MineLevel.entries) {
            val game = MinesweeperGame(lv, Random(7))
            game.reveal(lv.cols / 2, lv.rows / 2)
            assertEquals(lv.mines, countMines(game))
            assertEquals(lv.mines, game.minesLeft)
        }
    }

    // --- Les nombres et l'ouverture en cascade ---

    @Test
    fun unNombreCompteLesHuitVoisines() {
        val game = cornerGame()
        assertEquals(1, game.adjacentMines(0, 6)) // touche la seule mine (0, 7)
        assertEquals(4, game.adjacentMines(1, 7)) // touche (0, 7), (0, 8), (1, 8) et (2, 8)
        assertEquals(0, game.adjacentMines(4, 3))
        assertEquals(2, game.adjacentMines(0, 7)) // les mines (0, 8) et (1, 8) ; une mine ne compte pas elle-même
    }

    @Test
    fun uneCaseSansMineVoisineOuvreSesVoisinesDeProcheEnProche() {
        val game = unfinishedCornerGame()
        game.reveal(8, 0)
        // Tout le haut est vide : la cascade s'arrête aux cases numérotées (rangée 7), sans jamais atteindre les mines.
        assertTrue(game.isOpen(8, 0))
        assertTrue(game.isOpen(4, 3))
        assertTrue(game.isOpen(8, 7))
        assertFalse(game.isOpen(0, 8))
        assertEquals(MinesweeperGame.PLAYING, game.state)
    }

    @Test
    fun uneCaseDrapeauNEstJamaisOuverteParLaCascade() {
        val game = cornerGame()
        game.toggleFlag(4, 3)
        game.reveal(8, 0)
        assertFalse(game.isOpen(4, 3))
        assertTrue(game.isFlagged(4, 3))
    }

    // --- Les drapeaux ---

    @Test
    fun leDrapeauSePoseEtSeRetireSurUneCaseFermee() {
        val game = cornerGame()
        assertTrue(game.toggleFlag(2, 2))
        assertEquals(1, game.flags)
        assertEquals(level.mines - 1, game.minesLeft)
        assertFalse(game.reveal(2, 2)) // une case marquée ne se dévoile pas
        assertTrue(game.toggleFlag(2, 2))
        assertEquals(0, game.flags)
    }

    @Test
    fun onNePosePasDeDrapeauSurUneCaseOuverte() {
        val game = unfinishedCornerGame()
        game.reveal(8, 0)
        assertTrue(game.isOpen(8, 0))
        assertFalse(game.toggleFlag(8, 0))
        // Seul le drapeau posé au départ sur la case (0, 0) existe.
        assertEquals(1, game.flags)
    }

    // --- Perdre et gagner ---

    @Test
    fun devoilerUneMineFaitPerdreEtMarqueLaCase() {
        val game = cornerGame()
        game.reveal(3, 8)
        assertEquals(MinesweeperGame.LOST, game.state)
        assertTrue(game.isBlast(3, 8))
        assertEquals(0, game.score)
        assertFalse(game.reveal(8, 0))
        assertFalse(game.toggleFlag(8, 0))
    }

    @Test
    fun devoilerToutesLesCasesSansMineGagneEtPoseLesDrapeaux() {
        val game = cornerGame()
        for (r in 0 until game.rows) for (c in 0 until game.cols) {
            if (!game.isMine(c, r)) game.reveal(c, r)
        }
        assertEquals(MinesweeperGame.WON, game.state)
        assertEquals(level.mines, game.flags)
        assertEquals(0, game.minesLeft)
        assertTrue(game.score > 0)
    }

    // --- Le double-toucher sur un nombre ---

    @Test
    fun toucherUnNombreEntoureDAutantDeDrapeauxOuvreLesAutresVoisines() {
        val game = unfinishedCornerGame()
        game.reveal(1, 6)
        // (1, 6) touche la seule mine (0, 7) ; avec un drapeau dessus, ses autres voisines s'ouvrent.
        assertEquals(1, game.adjacentMines(1, 6))
        assertFalse(game.isOpen(0, 6))
        game.toggleFlag(0, 7)
        assertTrue(game.reveal(1, 6))
        assertTrue(game.isOpen(0, 6))
        assertTrue(game.isOpen(2, 7))
        assertEquals(MinesweeperGame.PLAYING, game.state)
    }

    @Test
    fun unMauvaisDrapeauFaitPerdreAuDoubleToucher() {
        val game = cornerGame()
        game.reveal(1, 6)
        assertEquals(1, game.adjacentMines(1, 6))
        game.toggleFlag(1, 5) // pas une mine
        game.reveal(1, 6)
        // (1, 6) a autant de drapeaux que de mines, mais la vraie mine (0, 7) n'est pas marquée : elle s'ouvre.
        assertEquals(MinesweeperGame.LOST, game.state)
    }

    @Test
    fun toucherUnNombreSansAssezDeDrapeauxNeFaitRien() {
        val game = cornerGame()
        game.reveal(1, 6)
        assertFalse(game.reveal(1, 6))
    }

    // --- Le chronomètre et le score ---

    @Test
    fun leChronometreNeTourneQuePartieEnCours() {
        val game = cornerGame()
        game.tick(2500)
        assertEquals(2, game.seconds)
        val fresh = MinesweeperGame(level, Random(1))
        fresh.tick(5000)
        assertEquals(0, fresh.seconds)
        game.reveal(3, 8)
        game.tick(5000)
        assertEquals(2, game.seconds)
    }

    @Test
    fun leScoreRecompenseLaVitesse() {
        assertEquals(1000 + 1000, Minesweeper.scoreFor(level, 50)) // référence 150 s : 100 s gagnées
        assertEquals(1000, Minesweeper.scoreFor(level, 150))
        assertEquals(1000, Minesweeper.scoreFor(level, 900))
        assertTrue(Minesweeper.scoreFor(level, 30) > Minesweeper.scoreFor(level, 60))
        assertTrue(Minesweeper.scoreFor(MineLevel.HARD, 300) > Minesweeper.scoreFor(MineLevel.EASY, 30))
    }

    @Test
    fun chaqueNiveauALeSienRecord() {
        val keys = MineLevel.entries.map { Minesweeper.recordKey(it) }
        assertEquals(keys.size, keys.toSet().size)
    }

    // --- Recommencer et garder la partie ---

    @Test
    fun recommencerVideLePlateau() {
        val game = cornerGame()
        game.reveal(8, 0)
        game.toggleFlag(0, 7)
        game.tick(3000)
        game.reset()
        assertEquals(MinesweeperGame.READY, game.state)
        assertEquals(0, game.flags)
        assertEquals(0, game.opened)
        assertEquals(0, game.seconds)
        assertEquals(0, countMines(game))
    }

    @Test
    fun laPartieSeRetrouveApresUneRotationDeLEcran() {
        val game = cornerGame()
        game.reveal(8, 0)
        game.toggleFlag(0, 7)
        game.tick(4200)
        val copy = MinesweeperGame.restore(game.save())
        assertEquals(game.level, copy.level)
        assertEquals(game.state, copy.state)
        assertEquals(game.flags, copy.flags)
        assertEquals(game.opened, copy.opened)
        assertEquals(game.elapsedMs, copy.elapsedMs)
        for (r in 0 until game.rows) for (c in 0 until game.cols) {
            assertEquals(game.isOpen(c, r), copy.isOpen(c, r))
            assertEquals(game.isFlagged(c, r), copy.isFlagged(c, r))
            assertEquals(game.isMine(c, r), copy.isMine(c, r))
            assertEquals(game.adjacentMines(c, r), copy.adjacentMines(c, r))
        }
    }

    @Test
    fun uneSauvegardeInvalideDonneUnePartieNeuve() {
        val game = MinesweeperGame.restore(listOf(99, 7))
        assertEquals(MineLevel.EASY, game.level)
        assertEquals(MinesweeperGame.READY, game.state)
    }
}
