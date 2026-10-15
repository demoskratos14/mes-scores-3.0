package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tournoi de poker en cours ([PokerSession]) : minuteur de blinds, annulation et changement de table.
 * L'horloge est remplacée par une variable, que les tests font avancer à la main.
 *
 * Le runner Robolectric n'est là que par précaution (l'état Compose de la classe n'a besoin ni
 * d'un [android.content.Context] ni de ressources) : il peut être retiré si les tests passent sans.
 */
@RunWith(RobolectricTestRunner::class)
class PokerSessionTest {

    private val minute = 60_000L
    private var now = 1_000L
    private fun session() = PokerSession { now }

    private val settings = PokerSettings(
        startingStack = 5000, smallBlind = 50, bigBlind = 100, levelMinutes = 10,
        anteEnabled = false, anteAfterIncreases = 0, firstDealer = 0
    )

    private fun started(players: Int = 3, with: PokerSettings = settings) =
        session().also { it.start(with, players) }

    /** Tout le monde se couche jusqu'à la fin de la main. */
    private fun PokerSession.foldHandAround() {
        while (game?.phase == PokerPhase.BETTING) fold()
    }

    // ---------- Minuteur ----------

    @Test
    fun leMinuteurDemarreAvecLeTournoiEtCompteLeTempsEcoule() {
        val s = started()
        assertTrue(s.clockRunning)
        assertEquals(0L, s.elapsedMs())
        now += 5_000
        assertEquals(5_000L, s.elapsedMs())
    }

    @Test
    fun enPauseLeTempsNeCourtPlusEtLaReprisePartDuTempsDejaEcoule() {
        val s = started()
        now += 5_000
        s.toggleClock()                       // pause à 5 s de jeu
        assertFalse(s.clockRunning)
        now += 100_000
        assertEquals(5_000L, s.elapsedMs())   // la pause ne compte pas
        s.toggleClock()                       // reprise
        assertTrue(s.clockRunning)
        now += 3_000
        assertEquals(8_000L, s.elapsedMs())
    }

    @Test
    fun leNiveauDeBlindsMonteToutesLesLevelMinutesDeJeuEffectif() {
        val s = started()
        assertEquals(1, s.level())
        now += 10 * minute - 1
        assertEquals(1, s.level())
        now += 1
        assertEquals(2, s.level())
        s.toggleClock()
        now += 60 * minute                    // une heure de pause : le niveau ne bouge pas
        assertEquals(2, s.level())
    }

    @Test
    fun passerAuNiveauSuivantSauteAuDebutDuProchainNiveau() {
        val s = started()
        now += 3 * minute
        s.skipToNextLevel()
        assertEquals(10 * minute, s.elapsedMs())
        assertEquals(2, s.level())
        // Le minuteur tournait : il continue à partir de là.
        now += minute
        assertEquals(11 * minute, s.elapsedMs())
    }

    @Test
    fun passerAuNiveauSuivantEnPauseLaisseLeMinuteurEnPause() {
        val s = started()
        now += 3 * minute
        s.toggleClock()
        s.skipToNextLevel()
        assertFalse(s.clockRunning)
        now += 30 * minute
        assertEquals(10 * minute, s.elapsedMs())
    }

    @Test
    fun sansPartieLeNiveauEstLePremierEtRienNeSePasse() {
        val s = session()
        assertNull(s.game)
        assertEquals(1, s.level())
        s.skipToNextLevel()
        s.fold()
        assertNull(s.game)
        assertFalse(s.hasProgress())
        assertNull(s.snapshot())
    }

    // ---------- Annulation ----------

    @Test
    fun uneActionSeRetireEtSeRetablit() {
        val s = started()
        val avant = s.game!!
        s.fold()
        val apres = s.game!!
        assertNotEquals(avant, apres)
        assertTrue(s.canUndo)
        assertFalse(s.canRedo)

        s.undo()
        assertEquals(avant, s.game)
        assertFalse(s.canUndo)
        assertTrue(s.canRedo)

        s.redo()
        assertEquals(apres, s.game)
        assertFalse(s.canRedo)
    }

    @Test
    fun uneNouvelleActionEffaceCeQuiAvaitEteAnnule() {
        val s = started()
        s.fold()
        s.undo()
        assertTrue(s.canRedo)
        s.fold()
        assertFalse(s.canRedo)
    }

    @Test
    fun uneActionSansEffetNEstPasMemorisee() {
        // Avant le premier tour de parole, le premier joueur doit suivre la grosse blind : il ne peut pas checker.
        val s = started(players = 3)
        val avant = s.game
        s.check()
        assertEquals(avant, s.game)
        assertFalse(s.canUndo)
    }

    @Test
    fun lHistoriqueEstLimiteAuxDernieresActions() {
        val s = started(players = 3, with = settings.copy(startingStack = 1_000_000))
        repeat(150) {
            s.foldHandAround()
            s.nextHand()
        }
        var undone = 0
        while (s.canUndo) {
            s.undo()
            undone++
        }
        assertEquals(PokerSession.MAX_UNDO, undone)
    }

    @Test
    fun annulerOuRetablirSansHistoriqueNeFaitRien() {
        val s = started()
        val avant = s.game
        s.undo()
        s.redo()
        assertEquals(avant, s.game)
    }

    // ---------- Enregistrement et reprise ----------

    @Test
    fun lInstantaneContientLeTempsDeJeuEtLaReprisePartEnPause() {
        val s = started()
        s.fold()
        now += 90_000
        val saved = s.snapshot()!!
        assertEquals(90_000L, saved.clockMs)
        assertTrue(s.hasProgress())

        val autre = session()
        now += 10 * minute
        autre.restore(saved)
        assertEquals(saved, autre.game)
        assertFalse(autre.clockRunning)
        assertEquals(90_000L, autre.elapsedMs())
        assertFalse(autre.canUndo)
        assertFalse(autre.canRedo)
        now += minute
        assertEquals(90_000L, autre.elapsedMs())  // toujours en pause
    }

    @Test
    fun reinitialiserEffaceLaPartieLHistoriqueEtLeMinuteur() {
        val s = started()
        s.fold()
        now += minute
        s.reset()
        assertNull(s.game)
        assertFalse(s.canUndo)
        assertFalse(s.clockRunning)
        assertEquals(0L, s.elapsedMs())
    }

    @Test
    fun relancerUnTournoiRepartDeZero() {
        val s = started()
        s.fold()
        now += 5 * minute
        s.start(settings, 4)
        assertEquals(4, s.game!!.seats.size)
        assertFalse(s.canUndo)
        assertEquals(0L, s.elapsedMs())
        assertTrue(s.clockRunning)
    }

    // ---------- Changement de table ----------

    @Test
    fun onNePeutChangerDeTableQuEntreDeuxMains() {
        val s = started(players = 4)
        assertFalse(s.canReseat)
        assertNull(s.reseat(setOf(3), emptyList()))
        s.foldHandAround()
        assertTrue(s.canReseat)
    }

    @Test
    fun ilFautGarderAuMoinsDeuxJoueursEnLice() {
        val s = started(players = 3)
        s.foldHandAround()
        assertEquals(listOf(0, 1, 2), s.aliveIndices())
        assertTrue(s.canRemove(listOf(2)))
        assertFalse(s.canRemove(listOf(1, 2)))   // il n'en resterait qu'un
        assertFalse(s.canRemove(emptyList()))
        assertFalse(s.canRemove(listOf(9)))      // place inconnue
    }

    @Test
    fun unDepartMetAJourLaPartieEtEffaceLHistorique() {
        val s = started(players = 4)
        s.foldHandAround()
        assertTrue(s.canUndo)

        val result = s.reseat(setOf(3), emptyList())!!
        assertEquals(3, s.game!!.seats.size)
        assertEquals(result.game, s.game)
        assertEquals(-1, result.oldToNew[3])
        assertFalse(s.canUndo)
        assertFalse(s.canRedo)
    }

    @Test
    fun uneArriveeAjouteDesJoueursAvecLeurTapis() {
        val s = started(players = 3)
        s.foldHandAround()
        val result = s.reseat(emptySet(), listOf(3000, 1200))!!
        assertEquals(5, s.game!!.seats.size)
        assertEquals(3000, s.game!!.seats[result.arrivals[0]].stack)
        assertEquals(1200, s.game!!.seats[result.arrivals[1]].stack)
    }

    @Test
    fun laTableSAgrandiraSeulementSiLaPlaceManque() {
        val s = started(players = 8)
        assertFalse(s.importNeedsRoom(2, maxPlayers = 10))
        assertTrue(s.importNeedsRoom(3, maxPlayers = 10))
        assertFalse(session().importNeedsRoom(1, maxPlayers = 10))
    }
}
