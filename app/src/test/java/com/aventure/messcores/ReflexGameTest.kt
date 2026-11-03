package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le Duel de réflexes chacun sur son téléphone : comparaison des résultats d'une manche, de deux à huit joueurs. */
class ReflexGameTest {

    private fun game(players: Int) = ReflexGame.of(players)

    // ---------- Deux joueurs ----------

    @Test
    fun leTempsLePlusCourtGagneLaManche() {
        val one = game(2).resolve(listOf(210, 260))!!
        assertEquals(0, one.lastWinner)
        assertEquals(210, one.lastReactionMs)
        assertEquals(listOf(1, 0), one.wins)
        assertFalse(one.lastDraw)
        assertTrue(one.roundPlayed)

        val two = game(2).resolve(listOf(300, 255))!!
        assertEquals(1, two.lastWinner)
        assertEquals(255, two.lastReactionMs)
        assertEquals(listOf(0, 1), two.wins)
    }

    @Test
    fun unFauxDepartNePeutPasGagnerMemeContreUnTempsLent() {
        val one = game(2).resolve(listOf(Reflex.EARLY, 900))!!
        assertEquals(1, one.lastWinner)
        assertEquals(listOf(0), one.falseStarters)

        val two = game(2).resolve(listOf(900, Reflex.EARLY))!!
        assertEquals(0, two.lastWinner)
        assertEquals(listOf(1), two.falseStarters)
    }

    @Test
    fun unFauxDepartContrePasToucheDonneLaMancheAuSeulSansFauxDepart() {
        val next = game(2).resolve(listOf(Reflex.EARLY, Reflex.MISS))!!
        assertEquals(1, next.lastWinner)
        // Il a gagné sans temps de réaction.
        assertEquals(0, next.lastReactionMs)
        assertNull(next.averageMsOf(1))
    }

    @Test
    fun toucherVautMieuxQueNePasToucher() {
        val one = game(2).resolve(listOf(800, Reflex.MISS))!!
        assertEquals(0, one.lastWinner)
        assertEquals(800, one.lastReactionMs)
        assertEquals(1, game(2).resolve(listOf(Reflex.MISS, 700))!!.lastWinner)
    }

    @Test
    fun egaliteDeuxFauxDepartsOuPersonneNeToucheDonneUneMancheNulle() {
        for ((a, b) in listOf(250 to 250, Reflex.EARLY to Reflex.EARLY, Reflex.MISS to Reflex.MISS)) {
            val next = game(2).copy(wins = listOf(1, 1)).resolve(listOf(a, b))!!
            assertTrue("$a/$b", next.lastDraw)
            assertTrue(next.roundPlayed)
            assertEquals(ReflexGame.NONE, next.lastWinner)
            assertEquals(listOf(1, 1), next.wins)
        }
    }

    @Test
    fun uneMancheJoueeEffaceLeNul() {
        val draw = game(2).resolve(listOf(250, 250))!!
        assertTrue(draw.lastDraw)
        assertFalse(draw.resolve(listOf(100, 200))!!.lastDraw)
    }

    @Test
    fun lesResultatsAbsurdesSontRamenes() {
        // Un résultat négatif est « pas touché » ; un temps énorme est plafonné.
        assertEquals(1, game(2).resolve(listOf(-500, 300))!!.lastWinner)
        assertEquals(9_999, game(2).resolve(listOf(500_000, Reflex.MISS))!!.lastReactionMs)
    }

    @Test
    fun onNeComparePlusUneFoisLaPartieFinie() {
        var match = game(2)
        repeat(Reflex.TARGET) { match = match.resolve(listOf(100, 200))!! }
        assertTrue(match.isOver)
        assertEquals(0, match.winner)
        assertNull(match.resolve(listOf(100, 200)))
    }

    @Test
    fun unNombreDeResultatsQuiNeCorrespondPasEstRefuse() {
        assertNull(game(3).resolve(listOf(100, 200)))
        assertNull(game(2).resolve(listOf(100, 200, 300)))
        assertNull(game(1).resolve(listOf(100)))
        assertNull(game(0).resolve(emptyList()))
    }

    // ---------- Plus de deux joueurs ----------

    @Test
    fun avecTroisJoueursLePlusRapideGagne() {
        val next = game(3).resolve(listOf(310, 240, 275))!!
        assertEquals(1, next.lastWinner)
        assertEquals(240, next.lastReactionMs)
        assertEquals(listOf(0, 1, 0), next.wins)
        assertEquals(listOf(310, 240, 275), next.lastResults)
    }

    @Test
    fun lesFauxDepartsSontEcartesEtLesAutresSeDepartagent() {
        // Le plus rapide a fait un faux départ : il est écarté, le plus rapide des autres gagne.
        val next = game(4).resolve(listOf(Reflex.EARLY, 400, Reflex.MISS, 350))!!
        assertEquals(3, next.lastWinner)
        assertEquals(350, next.lastReactionMs)
        assertEquals(listOf(0), next.falseStarters)
    }

    @Test
    fun egaliteAuMeilleurTempsEstNulleMemeSiD_autresOntTouche() {
        val next = game(3).resolve(listOf(200, 200, 500))!!
        assertTrue(next.lastDraw)
        assertEquals(listOf(0, 0, 0), next.wins)
    }

    @Test
    fun egaliteExacteHorsMeilleurTempsNeChangeRien() {
        val next = game(3).resolve(listOf(500, 200, 500))!!
        assertEquals(1, next.lastWinner)
    }

    @Test
    fun personneNeToucheLeSeulSansFauxDepartGagneSinonMancheNulle() {
        // Tous les autres ont fait un faux départ : le dernier gagne sans temps.
        val win = game(3).resolve(listOf(Reflex.EARLY, Reflex.MISS, Reflex.EARLY))!!
        assertEquals(1, win.lastWinner)
        assertEquals(0, win.lastReactionMs)

        // Deux joueurs sans faux départ mais sans temps : on ne peut pas les départager.
        assertTrue(game(3).resolve(listOf(Reflex.EARLY, Reflex.MISS, Reflex.MISS))!!.lastDraw)
        assertTrue(game(3).resolve(listOf(Reflex.MISS, Reflex.MISS, Reflex.MISS))!!.lastDraw)
        assertTrue(game(3).resolve(listOf(Reflex.EARLY, Reflex.EARLY, Reflex.EARLY))!!.lastDraw)
    }

    @Test
    fun huitJoueursSontPermis() {
        val results = listOf(300, 290, 280, 270, 260, 250, 240, 230)
        val next = game(Reflex.MAX_PLAYERS).resolve(results)!!
        assertEquals(7, next.lastWinner)
        assertEquals(Reflex.MAX_PLAYERS, next.players)
    }

    // ---------- Moyennes ----------

    @Test
    fun tousLesTempsDeToucheCompterontDansLesMoyennes() {
        val match = game(3).resolve(listOf(210, 340, 500))!!.resolve(listOf(500, 260, Reflex.MISS))!!
        assertEquals((210 + 500) / 2, match.averageMsOf(0))
        assertEquals((340 + 260) / 2, match.averageMsOf(1))
        // Le troisième n'a qu'un seul temps.
        assertEquals(500, match.averageMsOf(2))
        assertEquals(1, match.timedRoundsOf(2))
        assertTrue(match.hasAverages)
    }

    @Test
    fun uneSeuleMancheNeMontrePasDeMoyenne() {
        val match = game(3).resolve(listOf(210, 340, 500))!!
        assertFalse(match.hasAverages)
        assertEquals(210, match.averageMsOf(0))
    }

    @Test
    fun leTempsMoyenEstArrondiAuPlusProche() {
        // 201 + 202 = 403, moyenne 201,5 : arrondie à 202.
        val match = game(2).resolve(listOf(201, 900))!!.resolve(listOf(202, 900))!!
        assertEquals(202, match.averageMsOf(0))
    }

    @Test
    fun fauxDepartsMancheNullesEtPasTouchesNeComptentPas() {
        var match = game(2).resolve(listOf(Reflex.EARLY, 300))!!
        assertNull(match.averageMsOf(0))
        assertEquals(300, match.averageMsOf(1))

        val before = match
        match = match.resolve(listOf(150, 150))!!
        assertEquals(before.timedRounds, match.timedRounds)
        assertEquals(before.totalMs, match.totalMs)

        match = match.resolve(listOf(Reflex.MISS, 310))!!
        assertNull(match.averageMsOf(0))
        assertEquals(2, match.timedRoundsOf(1))
    }

    @Test
    fun uneNouvellePartieRemetLesScoresEtLesMoyennesAZero() {
        var match = game(3)
        repeat(Reflex.TARGET) { match = match.resolve(listOf(200, 300, 400))!! }
        assertTrue(match.isOver)
        val fresh = match.newMatch()
        assertEquals(ReflexGame.of(3), fresh)
        assertNull(fresh.averageMsOf(0))
        assertFalse(fresh.hasAverages)
        assertFalse(fresh.roundPlayed)
    }

    // ---------- Un joueur part ----------

    @Test
    fun unJoueurQuiPartLaisseLesAutresGarderLeurScore() {
        val match = game(3).resolve(listOf(300, 200, 250))!!.resolve(listOf(300, 400, 250))!!
        assertEquals(listOf(0, 1, 1), match.wins)

        val after = match.removePlayer(1)
        assertEquals(2, after.players)
        assertEquals(listOf(0, 1), after.wins)
        assertEquals(250, after.averageMsOf(1))
        // Le détail de la dernière manche n'a plus de sens : places changées.
        assertFalse(after.roundPlayed)
        assertEquals(emptyList<Int>(), after.lastResults)

        // Une place inconnue ne change rien.
        assertEquals(match, match.removePlayer(5))
        assertEquals(match, match.removePlayer(-1))
    }
}
