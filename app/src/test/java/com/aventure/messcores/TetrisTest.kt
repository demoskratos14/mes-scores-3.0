package com.aventure.messcores

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TetrisTest {

    private val i = Tetromino.I.ordinal
    private val o = Tetromino.O.ordinal
    private val t = Tetromino.T.ordinal

    private fun newGame(seed: Int = 1) = TetrisGame(Random(seed))

    /** Une partie vide où [kind] est la pièce en cours, tout en haut. */
    private fun gameWith(kind: Int) = newGame().also { it.spawn(kind) }

    private fun filled(game: TetrisGame, col: Int, row: Int) = game.cell(col, row) != 0

    // --- Les pièces ---

    @Test
    fun chaquePieceAQuatreCasesDistinctesALaRotationDeChaqueQuartDeTour() {
        for (piece in Tetromino.entries) {
            for (rotation in 0..3) {
                assertEquals("$piece $rotation", 4, piece.cells(rotation).toSet().size)
            }
        }
    }

    @Test
    fun leCarreNeChangePasEnTournantEtLeITourneAutourDeSaBoite() {
        for (rotation in 0..3) {
            assertEquals(Tetromino.O.cells(0).toSet(), Tetromino.O.cells(rotation).toSet())
        }
        assertEquals(listOf(2 to 0, 2 to 1, 2 to 2, 2 to 3), Tetromino.I.cells(1))
        assertEquals(setOf(1 to 0, 1 to 1, 1 to 2, 2 to 1), Tetromino.T.cells(1).toSet())
        // Quatre quarts de tour ramènent la pièce où elle était.
        assertEquals(Tetromino.T.cells(0), Tetromino.T.cells(4))
    }

    @Test
    fun unSacContientLesSeptPiecesUneFoisChacune() {
        repeat(20) { seed ->
            assertEquals((0 until 7).toList(), Tetris.newBag(Random(seed)).sorted())
        }
    }

    @Test
    fun uneNouvellePartieMontreToujoursLesPiecesAVenirEtDemarreEnHaut() {
        val game = newGame()
        assertEquals(Tetris.NEXT_COUNT, game.next().size)
        assertEquals(0, game.y)
        assertEquals(0, game.score)
        assertEquals(0, game.lines)
        assertEquals(1, game.level)
        assertEquals(TetrisGame.NONE, game.held)
        assertFalse(game.isOver)
    }

    @Test
    fun lesPiecesSontCentreesALApparition() {
        assertEquals(3, gameWith(i).x)
        assertEquals(4, gameWith(o).x)
        assertEquals(3, gameWith(t).x)
    }

    // --- Déplacements ---

    @Test
    fun uneNePieceNeSortPasDuPlateauParLesCotes() {
        val game = gameWith(t)
        repeat(20) { game.moveLeft() }
        assertEquals(0, game.x)
        assertFalse(game.moveLeft())
        repeat(20) { game.moveRight() }
        assertEquals(Tetris.COLS - 3, game.x)
        assertFalse(game.moveRight())
    }

    @Test
    fun laChuteDirecteDepose_laPieceEnBasEtRapporteDeuxPointsParCase() {
        val game = gameWith(t)
        game.hardDrop()
        // Le T (deux lignes de haut) descend de 18 cases : de la ligne 0 à la ligne 18.
        assertEquals(36, game.score)
        assertTrue(filled(game, 4, 18))
        assertTrue(filled(game, 3, 19) && filled(game, 4, 19) && filled(game, 5, 19))
        assertEquals(0, game.lines)
        assertFalse(game.isOver)
    }

    @Test
    fun laPieceFantomeEstLaOuLaPieceTomberait() {
        val game = gameWith(t)
        assertEquals(18, game.ghostY())
        assertEquals(game.pieceCells().map { (c, r) -> c to (r + 18) }, game.ghostCells())
        game.hardDrop()
        // La pièce suivante tombe sur la pile (au centre, colonnes 3 à 5) : son fantôme s'arrête au-dessus du T.
        assertTrue(game.ghostY() < 18)
    }

    @Test
    fun laDescenteRapideRapporteUnPointParCaseEtS_arreteAuFond() {
        val game = gameWith(o)
        repeat(30) { game.softDrop() }
        assertEquals(18, game.y)
        assertEquals(18, game.score)
        assertFalse(game.softDrop())
    }

    // --- Rotation ---

    @Test
    fun laRotationContreUnMurDecaleLaPieceSiCelaLuiPermetDeTourner() {
        val game = gameWith(i)
        assertTrue(game.rotate())
        assertEquals(1, game.rotation)
        while (game.moveLeft()) { /* jusqu'au mur */ }
        // Le I debout est dans la colonne 0 : la boîte déborde de deux colonnes à gauche.
        assertEquals(-2, game.x)
        // À plat, il ne tient pas à cet endroit : il est repoussé de deux colonnes.
        assertTrue(game.rotate())
        assertEquals(2, game.rotation)
        assertEquals(0, game.x)
    }

    @Test
    fun leCarreNeTournePas() {
        val game = gameWith(o)
        assertFalse(game.rotate())
        assertEquals(0, game.rotation)
    }

    @Test
    fun uneRotationImpossibleLaisseLaPieceOuElleEst() {
        val game = gameWith(i)
        // On bouche tout autour : ni la rotation ni ses décalages n'ont de place.
        for (row in 0..3) for (col in 0 until Tetris.COLS) game.setCell(col, row, o)
        val before = game.x to game.y
        assertFalse(game.rotate())
        assertEquals(0, game.rotation)
        assertEquals(before, game.x to game.y)
    }

    // --- Lignes et points ---

    /** Une partie où le carré, une fois lâché dans les colonnes 6 et 7, complète la ligne 19. */
    private fun gameAboutToClearOneLine(): TetrisGame {
        val game = newGame()
        for (col in listOf(0, 1, 2, 3, 4, 5, 8, 9)) game.setCell(col, 19, t)
        game.spawn(o)
        game.moveRight()
        game.moveRight()
        assertEquals(6, game.x)
        return game
    }

    @Test
    fun uneLigneCompleteDisparaitEtLesCasesDuDessusDescendentApresL_animation() {
        val game = gameAboutToClearOneLine()
        game.hardDrop()
        game.tick(Tetris.LINE_CLEAR_MS)
        assertEquals(1, game.lines)
        assertEquals(1, game.lastCleared)
        // 18 cases de chute directe (36 points) plus une ligne au niveau 1 (100 points).
        assertEquals(136, game.score)
        // La moitié haute du carré est tombée sur la ligne du bas ; le reste est vide.
        assertTrue(filled(game, 6, 19) && filled(game, 7, 19))
        assertFalse(filled(game, 0, 19))
        assertFalse(filled(game, 6, 18))
    }

    @Test
    fun quatreLignesEnUnCoupRapportentHuitCentsPoints() {
        val game = newGame()
        for (row in 16..19) for (col in 0 until Tetris.COLS - 1) game.setCell(col, row, t)
        game.spawn(i)
        assertTrue(game.rotate())
        repeat(4) { assertTrue(game.moveRight()) }
        assertEquals(7, game.x)
        game.hardDrop()
        assertEquals(listOf(16, 17, 18, 19), game.clearingRows)
        game.tick(Tetris.LINE_CLEAR_MS)
        assertEquals(4, game.lines)
        assertEquals(4, game.lastCleared)
        // 16 cases de chute directe (32 points) plus un « Tetris » (800 points).
        assertEquals(832, game.score)
        for (row in 0 until Tetris.ROWS) for (col in 0 until Tetris.COLS) assertFalse(filled(game, col, row))
    }

    // --- Animation d'une ligne complète ---

    @Test
    fun uneLigneCompleteResteVisibleEtFigeLeJeuPendantL_animation() {
        val game = gameAboutToClearOneLine()
        game.hardDrop()
        assertTrue(game.isClearing)
        assertEquals(listOf(19), game.clearingRows)
        // La ligne est toujours là, et ni les points ni le compte de lignes n'ont encore bougé.
        assertTrue(filled(game, 0, 19) && filled(game, 6, 19))
        assertEquals(0, game.lines)
        assertEquals(36, game.score)
        // Le joueur ne peut rien faire : la pièce suivante n'est pas encore là.
        assertFalse(game.moveLeft())
        assertFalse(game.moveRight())
        assertFalse(game.rotate())
        assertFalse(game.softDrop())
        assertFalse(game.hold())
        game.hardDrop()
        assertEquals(36, game.score)
        assertTrue(game.isClearing)
    }

    @Test
    fun lAnimationDureLeTempsPrevuPuisLaPieceSuivanteArrive() {
        val game = gameAboutToClearOneLine()
        game.hardDrop()
        game.tick(Tetris.LINE_CLEAR_MS - 1)
        assertTrue(game.isClearing)
        assertEquals(0, game.lines)
        game.tick(1)
        assertFalse(game.isClearing)
        assertTrue(game.clearingRows.isEmpty())
        assertEquals(1, game.lines)
        // Une nouvelle pièce est en haut du plateau, et le joueur reprend la main.
        assertEquals(0, game.y)
        assertFalse(game.isOver)
        assertTrue(game.moveLeft())
    }

    @Test
    fun lesLignesClignotentPuisS_eteignentEtAinsiDeSuite() {
        val game = gameAboutToClearOneLine()
        assertFalse(game.isFlashing)
        game.hardDrop()
        assertTrue(game.isFlashing)
        game.tick(Tetris.FLASH_MS)
        assertFalse(game.isFlashing)
        game.tick(Tetris.FLASH_MS)
        assertTrue(game.isFlashing)
        game.tick(Tetris.FLASH_MS)
        assertFalse(game.isFlashing)
        // Une fois l'animation finie, plus rien ne clignote.
        game.tick(Tetris.LINE_CLEAR_MS)
        assertFalse(game.isFlashing)
    }

    @Test
    fun uneLongueImagePendantL_animationLaTermineSansPerdreDePoints() {
        val game = gameAboutToClearOneLine()
        game.hardDrop()
        game.tick(10_000)
        assertFalse(game.isClearing)
        assertEquals(1, game.lines)
        assertEquals(136, game.score)
    }

    @Test
    fun uneAPoseSansLigneCompleteNAPasDAnimation() {
        val game = gameWith(t)
        game.hardDrop()
        assertFalse(game.isClearing)
        assertFalse(game.isFlashing)
        assertEquals(0, game.y)
        assertEquals(0, game.lastCleared)
    }

    @Test
    fun uneNouvellePartieInterrompUneAnimationEnCours() {
        val game = gameAboutToClearOneLine()
        game.hardDrop()
        game.reset()
        assertFalse(game.isClearing)
        assertTrue(game.clearingRows.isEmpty())
        assertEquals(0, game.score)
        assertTrue(game.moveLeft())
    }

    @Test
    fun uneAnimationEnCoursSeRetrouveApresSauvegarde() {
        val game = gameAboutToClearOneLine()
        game.hardDrop()
        game.tick(200)
        val copy = requireNotNull(TetrisGame.restore(game.save(), Random(3)))
        assertTrue(copy.isClearing)
        assertEquals(listOf(19), copy.clearingRows)
        assertEquals(game.isFlashing, copy.isFlashing)
        assertTrue(filled(copy, 0, 19))
        // Le temps déjà écoulé est gardé : il reste 160 ms avant la fin.
        copy.tick(Tetris.LINE_CLEAR_MS - 200 - 1)
        assertTrue(copy.isClearing)
        copy.tick(1)
        assertFalse(copy.isClearing)
        assertEquals(1, copy.lines)
        assertEquals(136, copy.score)
    }

    @Test
    fun desLignesEnAnimationAbimeesNeDonnentPasDePartie() {
        val game = gameAboutToClearOneLine()
        game.hardDrop()
        val saved = game.save()
        assertNotNull(TetrisGame.restore(saved))
        // Une ligne qui n'existe pas.
        assertNull(TetrisGame.restore(saved.dropLast(1) + 25))
        // La même ligne deux fois (le nombre de lignes annoncé suit).
        val twice = saved.toMutableList().also { it[16] = 2; it.add(19) }
        assertNull(TetrisGame.restore(twice))
    }

    @Test
    fun lesPointsDesLignesGrandissentAvecLeNiveau() {
        assertEquals(0, Tetris.lineClearPoints(0, 5))
        assertEquals(100, Tetris.lineClearPoints(1, 1))
        assertEquals(300, Tetris.lineClearPoints(2, 1))
        assertEquals(500, Tetris.lineClearPoints(3, 1))
        assertEquals(800, Tetris.lineClearPoints(4, 1))
        assertEquals(2_400, Tetris.lineClearPoints(4, 3))
    }

    @Test
    fun leNiveauMonteToutesLesDixLignesJusqu_AuPlafond() {
        assertEquals(1, Tetris.levelFor(0))
        assertEquals(1, Tetris.levelFor(9))
        assertEquals(2, Tetris.levelFor(10))
        assertEquals(3, Tetris.levelFor(20))
        assertEquals(Tetris.MAX_LEVEL, Tetris.levelFor(10_000))
    }

    @Test
    fun lesPiecesTombentDePlusEnPlusViteSansDescendreSousLePlancher() {
        assertEquals(800L, Tetris.gravityMs(1))
        var previous = Tetris.gravityMs(1)
        for (level in 2..Tetris.MAX_LEVEL) {
            val now = Tetris.gravityMs(level)
            assertTrue(now <= previous)
            assertTrue(now >= Tetris.MIN_GRAVITY_MS)
            previous = now
        }
        assertEquals(Tetris.MIN_GRAVITY_MS, Tetris.gravityMs(Tetris.MAX_LEVEL))
    }

    // --- Réserve ---

    @Test
    fun laReserveEchangeUneFoisParPiece() {
        val game = newGame()
        val first = game.current
        assertTrue(game.hold())
        assertEquals(first, game.held)
        val second = game.current
        // Déjà utilisée pour cette pièce.
        assertFalse(game.hold())
        assertEquals(second, game.current)
        // Une fois la pièce posée, on peut de nouveau échanger : la pièce de la réserve revient.
        game.hardDrop()
        assertTrue(game.hold())
        assertEquals(first, game.current)
    }

    // --- Temps ---

    @Test
    fun laPieceDescendDUneCaseAChaqueTempsDeChute() {
        val game = gameWith(t)
        val interval = Tetris.gravityMs(1)
        game.tick(interval)
        assertEquals(1, game.y)
        game.tick(interval - 1)
        assertEquals(1, game.y)
        game.tick(1)
        assertEquals(2, game.y)
    }

    @Test
    fun uneIMagePasseePlusLonguePeutFaireTomberPlusieursCases() {
        val game = gameWith(t)
        game.tick(Tetris.gravityMs(1) * 3)
        assertEquals(3, game.y)
    }

    @Test
    fun uneAPiecePoseeSeFigeApresLeDelaiDeVerrouillage() {
        val game = gameWith(o)
        repeat(30) { game.softDrop() }
        game.tick(Tetris.LOCK_DELAY_MS - 1)
        assertFalse(filled(game, 4, 19))
        game.tick(1)
        assertTrue(filled(game, 4, 19))
    }

    @Test
    fun deplacerUnePiecePoseeRelanceLeDelai() {
        val game = gameWith(o)
        repeat(30) { game.softDrop() }
        game.tick(Tetris.LOCK_DELAY_MS - 1)
        assertTrue(game.moveLeft())
        game.tick(Tetris.LOCK_DELAY_MS - 1)
        assertFalse(filled(game, 3, 19))
        game.tick(1)
        assertTrue(filled(game, 3, 19))
    }

    @Test
    fun leDelaiNePeutPasEtreRelanceSansFin() {
        val game = gameWith(o)
        repeat(30) { game.softDrop() }
        // Quinze déplacements relancent le délai chaque fois ; le seizième n'y change plus rien.
        repeat(Tetris.MAX_LOCK_RESETS) { index ->
            assertTrue(if (index % 2 == 0) game.moveLeft() else game.moveRight())
        }
        assertEquals(3, game.x)
        game.tick(300)
        assertTrue(game.moveLeft())
        assertEquals(2, game.x)
        game.tick(Tetris.LOCK_DELAY_MS - 301)
        assertFalse(filled(game, 2, 19))
        game.tick(1)
        assertTrue(filled(game, 2, 19))
    }

    // --- Fin de partie ---

    @Test
    fun laPartieFinitQuandUneNouvellePieceNAPasDePlace() {
        val game = newGame()
        for (col in 3..6) game.setCell(col, 1, t)
        game.spawn(t)
        assertTrue(game.isOver)
        // Plus rien ne bouge une fois la partie finie.
        val score = game.score
        assertFalse(game.moveLeft())
        assertFalse(game.rotate())
        assertFalse(game.softDrop())
        assertFalse(game.hold())
        game.hardDrop()
        game.tick(10_000)
        assertEquals(score, game.score)
        assertTrue(game.isOver)
    }

    @Test
    fun unePartieSeVideQuandOnRecommence() {
        val game = newGame()
        game.hardDrop()
        game.reset()
        assertEquals(0, game.score)
        assertEquals(0, game.lines)
        assertFalse(game.isOver)
        assertEquals(TetrisGame.NONE, game.held)
        for (row in 0 until Tetris.ROWS) for (col in 0 until Tetris.COLS) assertFalse(filled(game, col, row))
    }

    // --- Sauvegarde ---

    @Test
    fun uneAPartieSauvegardeeSeRetrouveIdentique() {
        val game = newGame(5)
        game.moveLeft()
        game.rotate()
        game.hardDrop()
        game.hold()
        game.moveRight()
        game.softDrop()
        game.hardDrop()
        game.moveLeft()
        game.tick(300)

        val copy = requireNotNull(TetrisGame.restore(game.save(), Random(9)))
        assertEquals(game.current, copy.current)
        assertEquals(game.rotation, copy.rotation)
        assertEquals(game.x, copy.x)
        assertEquals(game.y, copy.y)
        assertEquals(game.held, copy.held)
        assertEquals(game.holdUsed, copy.holdUsed)
        assertEquals(game.score, copy.score)
        assertEquals(game.lines, copy.lines)
        assertEquals(game.isOver, copy.isOver)
        assertEquals(game.next(), copy.next())
        for (row in 0 until Tetris.ROWS) for (col in 0 until Tetris.COLS) {
            assertEquals(game.cell(col, row), copy.cell(col, row))
        }
        // La copie continue de jouer comme l'original.
        game.tick(Tetris.gravityMs(1))
        copy.tick(Tetris.gravityMs(1))
        assertEquals(game.y, copy.y)
    }

    @Test
    fun desDonneesAbimeesNeDonnentPasDePartie() {
        val saved = newGame().save()
        assertNull(TetrisGame.restore(emptyList()))
        assertNull(TetrisGame.restore(saved.dropLast(1)))
        assertNull(TetrisGame.restore(saved + 0))
        // Mauvaise version.
        assertNull(TetrisGame.restore(listOf(99) + saved.drop(1)))
        // Une pièce qui n'existe pas.
        assertNull(TetrisGame.restore(saved.toMutableList().also { it[1] = 42 }))
        // Une case du plateau qui ne correspond à aucune pièce.
        assertNull(TetrisGame.restore(saved.toMutableList().also { it[it.size - 1] = 42 }))
        assertNotNull(TetrisGame.restore(saved))
    }
}
