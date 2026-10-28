package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RockPaperScissorsTest {

    private val rock = RockPaperScissors.ROCK
    private val paper = RockPaperScissors.PAPER
    private val scissors = RockPaperScissors.SCISSORS

    // ---------- Règles ----------

    @Test
    fun laFeuilleBatLaPierreLesCiseauxBattentLaFeuilleLaPierreBatLesCiseaux() {
        assertEquals(1, RockPaperScissors.outcome(paper, rock))
        assertEquals(1, RockPaperScissors.outcome(scissors, paper))
        assertEquals(1, RockPaperScissors.outcome(rock, scissors))
        assertEquals(2, RockPaperScissors.outcome(rock, paper))
        assertEquals(2, RockPaperScissors.outcome(paper, scissors))
        assertEquals(2, RockPaperScissors.outcome(scissors, rock))
    }

    @Test
    fun deuxChoixIdentiquesOuUnChoixInvalideDonnentZero() {
        for (choice in RockPaperScissors.CHOICES) assertEquals(0, RockPaperScissors.outcome(choice, choice))
        assertEquals(0, RockPaperScissors.outcome(0, rock))
        assertEquals(0, RockPaperScissors.outcome(rock, RockPaperScissors.HIDDEN))
        assertEquals(0, RockPaperScissors.outcome(9, 2))
    }

    // ---------- Une partie ----------

    @Test
    fun uneMancheNEstRevelee_qu_ApresLesDeuxChoix() {
        val first = RpsMatch().pick(1, rock)!!
        assertFalse(first.revealed)
        assertEquals(0, first.roundWinner)
        assertEquals(0, first.winsA + first.winsB + first.draws)

        val second = first.pick(2, scissors)!!
        assertTrue(second.revealed)
        assertEquals(1, second.roundWinner)
        assertEquals(1, second.winsA)
        assertEquals(0, second.winsB)
    }

    @Test
    fun uneEgaliteEstComptee() {
        val match = RpsMatch().pick(1, paper)!!.pick(2, paper)!!
        assertEquals(0, match.roundWinner)
        assertEquals(1, match.draws)
        assertEquals(0, match.winsA + match.winsB)
    }

    @Test
    fun onNeChoisitPasDeuxFoisNiUnChoixInvalide() {
        val match = RpsMatch().pick(1, rock)!!
        assertNull(match.pick(1, paper))
        assertNull(RpsMatch().pick(1, 0))
        assertNull(RpsMatch().pick(1, RockPaperScissors.HIDDEN))
        assertNull(RpsMatch().pick(3, rock))
    }

    @Test
    fun onPeutChoisirDansLOrdreQueLOnVeut() {
        val match = RpsMatch().pick(2, rock)!!.pick(1, paper)!!
        assertEquals(1, match.roundWinner)
        assertEquals(1, match.winsA)
    }

    @Test
    fun laMancheSuivanteEffaceLesChoixEtGardeLeScore() {
        val revealed = RpsMatch().pick(1, rock)!!.pick(2, scissors)!!
        val next = revealed.nextRound()!!
        assertEquals(0, next.pickA)
        assertEquals(0, next.pickB)
        assertEquals(1, next.winsA)
        // Tant que la manche n'est pas révélée, on ne peut pas passer à la suivante.
        assertNull(RpsMatch().pick(1, rock)!!.nextRound())
    }

    @Test
    fun lePremierAuNombreDeManchesGagneEtLaPartieSeFige() {
        var match = RpsMatch(target = 2)
        repeat(2) {
            match = match.pick(1, paper)!!.pick(2, rock)!!
            if (!match.isOver) match = match.nextRound()!!
        }
        assertTrue(match.isOver)
        assertEquals(1, match.winner)
        assertEquals(2, match.winsA)
        // Plus de choix, plus de manche suivante.
        assertNull(match.pick(1, rock))
        assertNull(match.nextRound())
    }

    @Test
    fun uneNouvellePartieGardeLeNombreDeManchesEtRemetLeScoreAZero() {
        var match = RpsMatch(target = 1).pick(2, scissors)!!.pick(1, rock)!!
        assertTrue(match.isOver)
        match = match.newMatch()!!
        assertEquals(RpsMatch(target = 1), match)
        assertNull(match.newMatch())
    }

    @Test
    fun advanceChoisitEntreMancheSuivanteEtNouvellePartie() {
        val inProgress = RpsMatch(target = 3).pick(1, rock)!!.pick(2, scissors)!!
        assertEquals(RpsMatch(target = 3, winsA = 1), inProgress.advance())
        val over = RpsMatch(target = 1).pick(1, rock)!!.pick(2, scissors)!!
        assertEquals(RpsMatch(target = 1), over.advance())
        assertNull(RpsMatch().pick(1, rock)!!.advance())
    }

    @Test
    fun leNombreDeManchesNeChangeQueTantQueRienNEstJoue() {
        assertEquals(5, RpsMatch().withTarget(5)!!.target)
        assertNull(RpsMatch().withTarget(4))
        assertNull(RpsMatch().pick(1, rock)!!.withTarget(5))
        assertTrue(RpsMatch(target = 5).isFresh)
        assertFalse(RpsMatch().pick(1, rock)!!.isFresh)
    }

    // ---------- Choix cachés ----------

    @Test
    fun laCopieEnvoyeeAUnJoueurCacheLeChoixDeLAutreTantQueLaMancheNEstPasRevelee() {
        val match = RpsMatch().pick(1, rock)!!

        val forB = match.maskedFor(2)
        assertEquals(RockPaperScissors.HIDDEN, forB.pickA)
        assertEquals(0, forB.pickB)
        assertFalse(forB.revealed)
        // Le joueur concerné garde son propre choix.
        assertEquals(rock, match.maskedFor(1).pickA)
        // Rien n'est caché quand l'autre n'a pas encore choisi.
        assertEquals(0, match.maskedFor(1).pickB)
    }

    @Test
    fun uneMancheReveleeNEstPlusCachee() {
        val match = RpsMatch().pick(1, rock)!!.pick(2, paper)!!
        assertEquals(match, match.maskedFor(1))
        assertEquals(match, match.maskedFor(2))
    }

    @Test
    fun uneCopieCacheeNeSeLitPasCommeUneMancheRevelee() {
        val hidden = RpsMatch().pick(2, scissors)!!.maskedFor(1)
        assertEquals(RockPaperScissors.HIDDEN, hidden.pickB)
        assertFalse(hidden.revealed)
        assertTrue(hidden.hasPicked(2))
        assertNull(hidden.pick(2, rock))
    }

    // ---------- Texte ----------

    @Test
    fun laPartieSeRelitTelleQuElleAEteEcrite() {
        val match = RpsMatch(target = 5, winsA = 2, winsB = 1, draws = 3, pickA = rock, pickB = RockPaperScissors.HIDDEN)
        assertEquals(match, RpsMatch.decode(match.encode()))
        assertEquals(RpsMatch(), RpsMatch.decode(RpsMatch().encode()))
    }

    @Test
    fun unTexteIllisibleOuAbsurdeEstRameneAUneValeurPermise() {
        assertEquals(RpsMatch(), RpsMatch.decode("n'importe quoi"))
        assertEquals(RpsMatch(), RpsMatch.decode("1,2,3"))
        val decoded = RpsMatch.decode("7,-1,-2,-3,9,-4")
        assertEquals(RockPaperScissors.DEFAULT_TARGET, decoded.target)
        assertEquals(0, decoded.winsA)
        assertEquals(0, decoded.winsB)
        assertEquals(0, decoded.draws)
        assertEquals(0, decoded.pickA)
        assertEquals(0, decoded.pickB)
        assertNotNull(RpsMatch.decode(""))
    }

    @Test
    fun lesSymbolesSontDistincts() {
        val symbols = (RockPaperScissors.CHOICES + RockPaperScissors.HIDDEN).map { RockPaperScissors.symbol(it) }
        assertEquals(symbols.size, symbols.toSet().size)
        assertEquals("", RockPaperScissors.symbol(0))
    }

    // ---------- Version lézard Spock ----------

    private val lizard = RockPaperScissors.LIZARD
    private val spock = RockPaperScissors.SPOCK

    @Test
    fun lesDixReglesDeLaVersionLezardSpock() {
        val wins = listOf(
            scissors to paper, paper to rock, rock to lizard, lizard to spock, spock to scissors,
            scissors to lizard, lizard to paper, paper to spock, spock to rock, rock to scissors
        )
        for ((winner, loser) in wins) {
            assertEquals(1, RockPaperScissors.outcome(winner, loser))
            assertEquals(2, RockPaperScissors.outcome(loser, winner))
        }
    }

    @Test
    fun chaqueChoixEnBatDeuxEtPerdContreDeux() {
        for (choice in RockPaperScissors.EXTENDED_CHOICES) {
            val others = RockPaperScissors.EXTENDED_CHOICES - choice
            assertEquals(2, others.count { RockPaperScissors.outcome(choice, it) == 1 })
            assertEquals(2, others.count { RockPaperScissors.outcome(choice, it) == 2 })
        }
    }

    @Test
    fun leLezardEtSpockSontRefusesEnVersionClassique() {
        assertNull(RpsMatch().pick(1, lizard))
        assertNull(RpsMatch().pick(1, spock))
        assertNotNull(RpsMatch(extended = true).pick(1, lizard))
        assertNotNull(RpsMatch(extended = true).pick(1, spock))
    }

    @Test
    fun laVersionSeChoisitTantQueRienNEstJoue() {
        assertTrue(RpsMatch().withExtended(true)!!.extended)
        assertTrue(RpsMatch().withExtended(true)!!.isFresh)
        assertNull(RpsMatch(extended = true).pick(1, rock)!!.withExtended(false))
    }

    @Test
    fun laVersionResteLaMemeAuFilDesPartiesEtDesManches() {
        var match = RpsMatch(target = 1, extended = true).pick(1, lizard)!!.pick(2, spock)!!
        assertEquals(1, match.winner)
        assertTrue(match.extended)
        match = match.newMatch()!!
        assertEquals(RpsMatch(target = 1, extended = true), match)
        assertTrue(match.extended)
    }

    @Test
    fun laVersionSeRelitAvecLaPartie() {
        val match = RpsMatch(target = 2, winsA = 1, pickA = lizard, pickB = RockPaperScissors.HIDDEN, extended = true)
        assertEquals(match, RpsMatch.decode(match.encode()))
        // Texte écrit avant la version « lézard Spock » : six nombres, version classique.
        assertFalse(RpsMatch.decode("3,0,0,0,1,0").extended)
        // Un lézard ne peut pas exister dans une partie classique.
        assertEquals(0, RpsMatch.decode("3,0,0,0,4,0,0").pickA)
        assertEquals(lizard, RpsMatch.decode("3,0,0,0,4,0,1").pickA)
    }
}
