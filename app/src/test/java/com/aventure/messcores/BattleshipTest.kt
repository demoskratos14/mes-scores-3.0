package com.aventure.messcores

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les règles du touché coulé : placement, réponse à un tir, fin de partie, partie sur un seul téléphone. */
class BattleshipTest {

    // Une flotte valide : un bateau par ligne, de la colonne A vers la droite.
    private val fleetA = listOf(
        listOf(0, 1, 2, 3, 4), listOf(10, 11, 12, 13), listOf(20, 21, 22), listOf(30, 31, 32), listOf(40, 41)
    )
    private val fleetB = listOf(
        listOf(5, 15, 25, 35, 45), listOf(6, 16, 26, 36), listOf(7, 17, 27), listOf(8, 18, 28), listOf(9, 19)
    )

    @Test
    fun lesCasesSeNommentColonneEtLigne() {
        assertEquals("A1", Battleship.cellName(0))
        assertEquals("J1", Battleship.cellName(9))
        assertEquals("C4", Battleship.cellName(32))
        assertEquals("J10", Battleship.cellName(99))
    }

    @Test
    fun unBateauNeSortPasDeLaGrille() {
        assertEquals(listOf(0, 1, 2), Battleship.shipCells(0, 3, horizontal = true))
        assertEquals(listOf(0, 10, 20), Battleship.shipCells(0, 3, horizontal = false))
        assertNull(Battleship.shipCells(8, 3, horizontal = true))
        assertNull(Battleship.shipCells(95, 2, horizontal = false))
    }

    @Test
    fun uneFlotteValideEstAcceptee() {
        assertTrue(Battleship.isValidFleet(fleetA))
        assertTrue(Battleship.isValidFleet(fleetB))
    }

    @Test
    fun uneFlotteIncorrecteEstRefusee() {
        // Mauvaises longueurs.
        assertFalse(Battleship.isValidFleet(fleetA.dropLast(1)))
        // Chevauchement.
        assertFalse(Battleship.isValidFleet(fleetA.dropLast(1) + listOf(listOf(0, 10))))
        // Bateau coudé.
        assertFalse(Battleship.isValidFleet(fleetA.dropLast(1) + listOf(listOf(40, 41, 51))))
        assertFalse(Battleship.isStraightShip(listOf(0, 1, 11)))
        // Bateau qui passe à la ligne suivante.
        assertFalse(Battleship.isStraightShip(listOf(8, 9, 10)))
    }

    @Test
    fun uneFlotteAuHasardEstToujoursValide() {
        repeat(50) { seed -> assertTrue(Battleship.isValidFleet(Battleship.randomFleet(Random(seed)))) }
    }

    @Test
    fun laReponseAUnTirEstALEauToucheOuCoule() {
        assertEquals(ShotOutcome.MISS, Battleship.resolve(fleetA, emptyList(), 99)?.outcome)
        val first = Battleship.resolve(fleetA, emptyList(), 40)
        assertEquals(ShotOutcome.HIT, first?.outcome)
        val second = Battleship.resolve(fleetA, listOf(first!!), 41)
        assertEquals(ShotOutcome.SUNK, second?.outcome)
        assertEquals(listOf(40, 41), second?.sunk)
    }

    @Test
    fun uneCaseDejaJoueeOuHorsGrilleEstRefusee() {
        val shot = Battleship.resolve(fleetA, emptyList(), 99)!!
        assertNull(Battleship.resolve(fleetA, listOf(shot), 99))
        assertNull(Battleship.resolve(fleetA, emptyList(), 100))
        assertNull(Battleship.resolve(fleetA, emptyList(), -1))
    }

    @Test
    fun laFlotteEstCouleQuandToutesSesCasesSontTouchees() {
        val shots = ArrayList<BattleshipShot>()
        for (cell in fleetA.flatten()) {
            assertFalse(Battleship.allSunk(fleetA, shots))
            shots.add(Battleship.resolve(fleetA, shots, cell)!!)
        }
        assertTrue(Battleship.allSunk(fleetA, shots))
    }

    @Test
    fun leTirSupplementaireNeVautQueSiLeReglageEstActif() {
        val hit = BattleshipShot(0, ShotOutcome.HIT)
        val miss = BattleshipShot(99, ShotOutcome.MISS)
        assertEquals(1, Battleship.nextShooter(BattleshipSettings(false), 0, hit))
        assertEquals(0, Battleship.nextShooter(BattleshipSettings(true), 0, hit))
        assertEquals(1, Battleship.nextShooter(BattleshipSettings(true), 0, miss))
        assertEquals(0, Battleship.nextShooter(BattleshipSettings(true), 1, miss))
    }

    @Test
    fun lesReponsesHonnetesSontCoherentesAvecLaFlotte() {
        val shots = ArrayList<BattleshipShot>()
        for (cell in listOf(40, 41, 99, 0)) shots.add(Battleship.resolve(fleetA, shots, cell)!!)
        assertTrue(Battleship.isConsistent(fleetA, shots))
    }

    @Test
    fun unMensongeEstDetecte() {
        // L'adversaire répond « à l'eau » sur une case où il a un bateau.
        assertFalse(Battleship.isConsistent(fleetA, listOf(BattleshipShot(0, ShotOutcome.MISS))))
        // Ou « touché » sur de l'eau.
        assertFalse(Battleship.isConsistent(fleetA, listOf(BattleshipShot(99, ShotOutcome.HIT))))
        // Ou « coulé » trop tôt.
        assertFalse(Battleship.isConsistent(fleetA, listOf(BattleshipShot(40, ShotOutcome.SUNK, listOf(40, 41)))))
    }

    // ---------- Partie sur le même téléphone ----------

    private fun placed(first: Int = 0, shootAgain: Boolean = false): BattleshipGame {
        var game = Battleship.newGame(listOf("Anna", "Ben"), BattleshipSettings(shootAgain), first)
        game = Battleship.withFleet(game, 0, fleetA)!!
        assertEquals(BattleshipPhase.PLACEMENT, game.phase)
        game = Battleship.withFleet(game, 1, fleetB)!!
        assertEquals(BattleshipPhase.PLAYING, game.phase)
        return game
    }

    @Test
    fun laFlotteNePeutEtrePlaceeQuUneFoisEtDoitEtreValide() {
        val game = Battleship.newGame(listOf("Anna", "Ben"), BattleshipSettings(), 0)
        assertNull(Battleship.withFleet(game, 0, fleetA.dropLast(1)))
        val one = Battleship.withFleet(game, 0, fleetA)
        assertNotNull(one)
        assertNull(Battleship.withFleet(one!!, 0, fleetA))
    }

    @Test
    fun lesJoueursTirentChacunSonTourEtUnMissPasseLaMain() {
        var game = placed(first = 0)
        assertNull(Battleship.fire(Battleship.newGame(listOf("A", "B"), BattleshipSettings(), 0), 5))
        game = Battleship.fire(game, 99)!!
        assertEquals(ShotOutcome.MISS, game.shots[0].single().outcome)
        assertEquals(1, game.turn)
        game = Battleship.fire(game, 99)!!
        assertEquals(0, game.turn)
        // Case déjà jouée par ce joueur : refusé.
        assertNull(Battleship.fire(game, 99))
    }

    @Test
    fun avecLeTirSupplementaireUnTouchePermetDeRejouer() {
        var game = placed(first = 0, shootAgain = true)
        game = Battleship.fire(game, 5)!!
        assertEquals(0, game.turn)
        game = Battleship.fire(game, 99)!!
        assertEquals(1, game.turn)
    }

    @Test
    fun laPartieSeTermineQuandUneFlotteEstCoulee() {
        var game = placed(first = 0, shootAgain = true)
        for (cell in fleetB.flatten()) game = Battleship.fire(game, cell)!!
        assertEquals(BattleshipPhase.FINISHED, game.phase)
        assertEquals(0, game.winner)
        assertNull(Battleship.fire(game, 99))
    }

    @Test
    fun leViewModelFaitPasserLeTelephoneEntreLesJoueurs() {
        val viewModel = BattleshipViewModel()
        viewModel.open(GameRules(id = BATTLESHIP_ID, name = "Touché coulé"), listOf("Anna", "Ben"))
        viewModel.start(Random(0))
        val first = viewModel.game!!.turn
        // Anna place d'abord, puis Ben : le téléphone passe d'abord à Anna.
        assertEquals(0, viewModel.handoffTo)
        viewModel.takePhone()
        viewModel.submitFleet(fleetA)
        assertEquals(1, viewModel.handoffTo)
        viewModel.takePhone()
        viewModel.submitFleet(fleetB)
        assertEquals(first, viewModel.handoffTo)
        viewModel.takePhone()
        assertEquals(first, viewModel.viewer)
        // Un tir dans l'eau : on lit le résultat, puis le téléphone passe à l'autre.
        viewModel.fire(99)
        assertEquals(ShotOutcome.MISS, viewModel.lastShot?.outcome)
        viewModel.afterShot()
        assertEquals(1 - first, viewModel.handoffTo)
        assertNull(viewModel.lastShot)
    }
}
