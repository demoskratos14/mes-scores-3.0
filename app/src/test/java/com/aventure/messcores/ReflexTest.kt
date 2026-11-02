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

    // ---------- Deux téléphones : comparaison des temps ----------

    @Test
    fun leTempsLePlusCourtGagneLaManche() {
        val one = ReflexMatch().resolve(210, 260)!!
        assertEquals(1, one.lastWinner)
        assertEquals(210, one.lastReactionMs)
        assertEquals(1, one.wins1)
        assertFalse(one.falseStart)
        assertFalse(one.lastDraw)

        val two = ReflexMatch().resolve(300, 255)!!
        assertEquals(2, two.lastWinner)
        assertEquals(255, two.lastReactionMs)
        assertEquals(1, two.wins2)
    }

    @Test
    fun unFauxDepartPerdMemeContreUnTempsLent() {
        val one = ReflexMatch().resolve(Reflex.EARLY, 900)!!
        assertEquals(2, one.lastWinner)
        assertTrue(one.falseStart)
        assertEquals(0, one.lastReactionMs)

        val two = ReflexMatch().resolve(900, Reflex.EARLY)!!
        assertEquals(1, two.lastWinner)
        assertTrue(two.falseStart)

        // Faux départ contre « pas touché » : le faux départ perd quand même.
        assertEquals(2, ReflexMatch().resolve(Reflex.EARLY, Reflex.MISS)!!.lastWinner)
    }

    @Test
    fun toucherVautMieuxQueNePasToucher() {
        val one = ReflexMatch().resolve(800, Reflex.MISS)!!
        assertEquals(1, one.lastWinner)
        assertEquals(800, one.lastReactionMs)
        assertFalse(one.falseStart)

        assertEquals(2, ReflexMatch().resolve(Reflex.MISS, 700)!!.lastWinner)
    }

    @Test
    fun egaliteDeuxFauxDepartsOuPersonneNeToucheDonneUneMancheNulle() {
        for ((a, b) in listOf(250 to 250, Reflex.EARLY to Reflex.EARLY, Reflex.MISS to Reflex.MISS)) {
            val next = ReflexMatch(wins1 = 1, wins2 = 1).resolve(a, b)!!
            assertTrue("$a/$b", next.lastDraw)
            assertEquals(0, next.lastWinner)
            assertEquals(1, next.wins1)
            assertEquals(1, next.wins2)
        }
    }

    @Test
    fun uneMancheJoueeEffaceLeNul() {
        val draw = ReflexMatch().resolve(250, 250)!!
        assertTrue(draw.lastDraw)
        assertFalse(draw.tapAfterSignal(1, 200)!!.lastDraw)
        assertFalse(draw.resolve(100, 200)!!.lastDraw)
    }

    @Test
    fun lesResultatsAbsurdesSontRamenes() {
        // Un résultat négatif est « pas touché » ; un temps énorme est plafonné.
        assertEquals(2, ReflexMatch().resolve(-500, 300)!!.lastWinner)
        assertEquals(9_999, ReflexMatch().resolve(500_000, Reflex.MISS)!!.lastReactionMs)
    }

    @Test
    fun onNeComparePlusUneFoisLaPartieFinie() {
        var match = ReflexMatch()
        repeat(Reflex.TARGET) { match = match.resolve(100, 200)!! }
        assertTrue(match.isOver)
        assertNull(match.resolve(100, 200))
    }

    @Test
    fun aDeuxTelephonesLeTempsDuPerdantEstNote() {
        val one = ReflexMatch().resolve(210, 340)!!
        assertEquals(1, one.lastWinner)
        assertEquals(210, one.lastReactionMs)
        assertEquals(340, one.lastLoserReactionMs)

        val two = ReflexMatch().resolve(500, 255)!!
        assertEquals(2, two.lastWinner)
        assertEquals(255, two.lastReactionMs)
        assertEquals(500, two.lastLoserReactionMs)

        // Le temps du perdant est plafonné comme celui du vainqueur.
        assertEquals(9_999, ReflexMatch().resolve(100, 500_000)!!.lastLoserReactionMs)
    }

    @Test
    fun sansTempsDuPerdantOnNeNoteRien() {
        // Le perdant n'a pas touché, ou a fait un faux départ, ou la manche est nulle : pas de temps pour lui.
        assertEquals(0, ReflexMatch().resolve(300, Reflex.MISS)!!.lastLoserReactionMs)
        assertEquals(0, ReflexMatch().resolve(Reflex.EARLY, 300)!!.lastLoserReactionMs)
        assertEquals(0, ReflexMatch().resolve(250, 250)!!.lastLoserReactionMs)
        assertEquals(0, ReflexMatch().resolve(Reflex.MISS, Reflex.MISS)!!.lastLoserReactionMs)
        assertEquals(0, ReflexMatch().tapAfterSignal(1, 200)!!.lastLoserReactionMs)
        assertEquals(0, ReflexMatch().tapTooEarly(1)!!.lastLoserReactionMs)
    }

    @Test
    fun leTempsDuPerdantNeSurvitPasALaMancheSuivante() {
        val first = ReflexMatch().resolve(210, 340)!!
        val second = first.resolve(Reflex.MISS, 280)!!
        assertEquals(2, second.lastWinner)
        assertEquals(0, second.lastLoserReactionMs)
        assertEquals(0, first.resolve(100, 100)!!.lastLoserReactionMs)
    }

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
}
