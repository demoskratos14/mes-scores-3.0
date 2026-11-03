package com.aventure.messcores

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReflexTest {

    @Test
    fun lePremierQuiToucheApresLeSignalGagneLaManche() {
        val next = ReflexMatch().tapAfterSignal(player = 1, reactionMs = 243)!!
        assertEquals(1, next.wins1)
        assertEquals(0, next.wins2)
        assertEquals(1, next.lastWinner)
        assertEquals(243, next.lastReactionMs)
        assertFalse(next.falseStart)
    }

    @Test
    fun unFauxDepartDonneLaMancheAL_autreJoueur() {
        val next = ReflexMatch().tapTooEarly(player = 1)!!
        assertEquals(0, next.wins1)
        assertEquals(1, next.wins2)
        assertEquals(2, next.lastWinner)
        assertEquals(0, next.lastReactionMs)
        assertTrue(next.falseStart)

        assertEquals(1, ReflexMatch().tapTooEarly(player = 2)!!.wins1)
    }

    @Test
    fun lePremierAuxManchesCiblesGagneLaPartie() {
        var match = ReflexMatch()
        repeat(Reflex.TARGET - 1) {
            match = match.tapAfterSignal(2, 300)!!
            assertFalse(match.isOver)
            assertEquals(0, match.winner)
        }
        // L'autre joueur marque aussi : rien n'est joué tant que personne n'a atteint la cible.
        match = match.tapAfterSignal(1, 280)!!
        assertFalse(match.isOver)
        match = match.tapTooEarly(1)!!
        assertTrue(match.isOver)
        assertEquals(2, match.winner)
        assertEquals(Reflex.TARGET, match.winsOf(2))
        assertEquals(1, match.winsOf(1))
    }

    @Test
    fun uneFoisLaPartieFinieOnNeJoueePlus() {
        var match = ReflexMatch()
        repeat(Reflex.TARGET) { match = match.tapAfterSignal(1, 250)!! }
        assertTrue(match.isOver)
        assertNull(match.tapAfterSignal(2, 200))
        assertNull(match.tapTooEarly(2))
    }

    @Test
    fun unJoueurInconnuEstRefuse() {
        assertNull(ReflexMatch().tapAfterSignal(0, 200))
        assertNull(ReflexMatch().tapAfterSignal(3, 200))
        assertNull(ReflexMatch().tapTooEarly(0))
        assertNull(ReflexMatch().tapTooEarly(3))
    }

    @Test
    fun leTempsDeReactionEstBorne() {
        assertEquals(1, ReflexMatch().tapAfterSignal(1, 0)!!.lastReactionMs)
        assertEquals(1, ReflexMatch().tapAfterSignal(1, -50)!!.lastReactionMs)
        assertEquals(9_999, ReflexMatch().tapAfterSignal(1, 120_000)!!.lastReactionMs)
    }

    @Test
    fun uneNouvellePartieRemetLesScoresAZero() {
        var match = ReflexMatch()
        repeat(Reflex.TARGET) { match = match.tapAfterSignal(2, 250)!! }
        val fresh = match.newMatch()
        assertEquals(ReflexMatch(), fresh)
        assertFalse(fresh.isOver)
        assertEquals(0, fresh.lastWinner)
    }

    @Test
    fun l_attenteAvantLeSignalResteDansLaPlageAnnoncee() {
        val random = Random(42)
        repeat(500) {
            val delay = Reflex.randomDelayMs(random)
            assertTrue("attente hors plage : $delay", delay in Reflex.MIN_DELAY_MS..Reflex.MAX_DELAY_MS)
        }
    }

    // ---------- Le temps du perdant et les moyennes (un seul téléphone) ----------

    @Test
    fun surUnSeulTelephoneLeTempsDuPerdantEstNoteApresLeVainqueur() {
        val won = ReflexMatch().tapAfterSignal(player = 1, reactionMs = 210)!!
        val done = won.loserTapped(335)
        assertEquals(1, done.wins1)
        assertEquals(0, done.wins2)
        assertEquals(1, done.lastWinner)
        assertEquals(210, done.lastReactionMs)
        assertEquals(335, done.lastLoserReactionMs)
        // Un second toucher ne change plus rien, et le score non plus.
        assertEquals(done, done.loserTapped(900))
        // Le temps est ramené à une valeur permise.
        assertEquals(1, won.loserTapped(-20).lastLoserReactionMs)
        assertEquals(9_999, won.loserTapped(120_000).lastLoserReactionMs)
    }

    @Test
    fun surUnSeulTelephoneAucunTempsPourUnFauxDepartOuSansManche() {
        val early = ReflexMatch().tapTooEarly(1)!!
        assertEquals(early, early.loserTapped(300))
        assertEquals(ReflexMatch(), ReflexMatch().loserTapped(300))
    }

    @Test
    fun leTempsMoyenSeCalculeSurLesManchesJouees() {
        var match = ReflexMatch()
        assertNull(match.averageMsOf(1))
        assertFalse(match.hasAverages)

        // Un seul téléphone : le vainqueur touche, puis le perdant est chronométré.
        match = match.tapAfterSignal(1, 200)!!.loserTapped(300)
        assertEquals(200, match.averageMsOf(1))
        assertEquals(300, match.averageMsOf(2))
        // Une seule manche : la moyenne ne dit rien de plus que le temps lui-même.
        assertFalse(match.hasAverages)

        match = match.tapAfterSignal(2, 250)!!.loserTapped(400)
        assertEquals((200 + 400) / 2, match.averageMsOf(1))
        assertEquals((300 + 250) / 2, match.averageMsOf(2))
        assertTrue(match.hasAverages)
        assertEquals(2, match.timedRoundsOf(1))
        assertEquals(2, match.timedRoundsOf(2))
    }

    @Test
    fun leTempsMoyenEstArrondiAuPlusProche() {
        // 201 + 202 = 403, moyenne 201,5 : arrondie à 202.
        val match = ReflexMatch().tapAfterSignal(1, 201)!!.tapAfterSignal(1, 202)!!
        assertEquals(202, match.averageMsOf(1))
    }

    @Test
    fun unFauxDepartNeComptePasDansLaMoyenne() {
        var match = ReflexMatch().tapAfterSignal(1, 220)!!
        match = match.tapTooEarly(2)!!
        assertEquals(220, match.averageMsOf(1))
        assertEquals(1, match.timedRoundsOf(1))
        assertNull(match.averageMsOf(2))
    }

    @Test
    fun uneNouvellePartieRemetLesMoyennesAZero() {
        var match = ReflexMatch()
        repeat(Reflex.TARGET) { match = match.tapAfterSignal(1, 200)!!.loserTapped(300) }
        assertTrue(match.isOver)
        assertEquals(200, match.averageMsOf(1))
        assertEquals(300, match.averageMsOf(2))
        val fresh = match.newMatch()
        assertNull(fresh.averageMsOf(1))
        assertNull(fresh.averageMsOf(2))
        assertFalse(fresh.hasAverages)
    }
}
