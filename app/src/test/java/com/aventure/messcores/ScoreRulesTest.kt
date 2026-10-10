package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class ScoreRulesTest {

    private fun game(id: String) = GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == id }

    private fun newGame(id: String, vararg names: String) = ScoreViewModel().also { it.initGame(names.toList(), game(id)) }

    // ---------- Mölkky ----------

    @Test
    fun auMolkkyUnDepassementRameneA25() {
        assertEquals(45, ScoreRules.exactTarget(listOf(12, 9, 24), 50, 25))
        // 45 + 12 = 57 > 50 : retour à 25, puis +5 = 30.
        assertEquals(30, ScoreRules.exactTarget(listOf(12, 9, 24, 12, 5), 50, 25))
        assertEquals(50, ScoreRules.exactTarget(listOf(30, 20), 50, 25))
        assertEquals(0, ScoreRules.exactTarget(listOf(null, null), 50, 25))
    }

    @Test
    fun laPartieDeMolkkySArreteA50Pile() {
        val vm = newGame("builtin_molkky", "Anna", "Ben")
        vm.setCell(0, 0, 40, false)
        vm.setCell(1, 0, 20, false) // 60 > 50 : retombe à 25
        assertEquals(25, vm.totalFor(0))
        assertFalse(vm.isGameOver())
        vm.setCell(2, 0, 25, false)
        assertEquals(50, vm.totalFor(0))
        assertTrue(vm.isGameOver())
        assertEquals(listOf(0), vm.winners())
    }

    // ---------- Fléchettes ----------

    @Test
    fun auxFlechettesLeScoreSeDecompteSansPasserSous0NiSArreterA1() {
        assertEquals(301, ScoreRules.countdown(listOf(null), 301))
        assertEquals(241, ScoreRules.countdown(listOf(60), 301))
        // Un tour qui ferait passer sous 0 est ignoré.
        assertEquals(40, ScoreRules.countdown(listOf(261, 100), 301))
        // Laisser 1 point est impossible à finir sur un double : tour annulé.
        assertEquals(41, ScoreRules.countdown(listOf(260, 40), 301))
        assertEquals(0, ScoreRules.countdown(listOf(261, 40), 301))
    }

    @Test
    fun laPartieDeFlechettesSArreteQuandUnJoueurArriveA0() {
        val vm = newGame("builtin_darts301", "Anna", "Ben")
        assertEquals(301, vm.totalFor(0))
        vm.setCell(0, 0, 180, false)
        vm.setCell(0, 1, 100, false)
        assertEquals(121, vm.totalFor(0))
        assertEquals(201, vm.totalFor(1))
        assertFalse(vm.isGameOver())
        vm.setCell(1, 0, 121, false)
        assertEquals(0, vm.totalFor(0))
        assertTrue(vm.isGameOver())
        assertEquals(listOf(0), vm.winners())
    }

    // ---------- Bowling ----------

    private fun strike() = BowlingScoring.encode(listOf(10))
    private fun frame(vararg rolls: Int) = BowlingScoring.encode(rolls.toList())

    @Test
    fun lesLancersSontEncodesEtDecodesSansPerte() {
        assertEquals(listOf(10), BowlingScoring.decode(strike()))
        assertEquals(listOf(7, 3), BowlingScoring.decode(frame(7, 3)))
        assertEquals(listOf(10, 10, 10), BowlingScoring.decode(frame(10, 10, 10)))
        assertEquals(listOf(0, 0), BowlingScoring.decode(frame(0, 0)))
    }

    @Test
    fun unePartieParfaiteFait300() {
        val codes = List(9) { strike() } + frame(10, 10, 10)
        assertEquals(300, BowlingScoring.total(codes))
        assertEquals(30, BowlingScoring.cumulative(codes)[0])
        assertEquals(270, BowlingScoring.cumulative(codes)[8])
    }

    @Test
    fun uneSuiteDeSparesAvec5FaitCentCinquante() {
        val codes = List(9) { frame(5, 5) } + frame(5, 5, 5)
        assertEquals(150, BowlingScoring.total(codes))
    }

    @Test
    fun unePartieSansQuilleFaitZero() {
        assertEquals(0, BowlingScoring.total(List(10) { frame(0, 0) }))
    }

    @Test
    fun leBonusDuStrikeAttendLesDeuxLancersSuivants() {
        // Frame 1 : strike, frame 2 : 3 et 4 -> frame 1 = 17, frame 2 = 7, total 24.
        val codes = listOf(strike(), frame(3, 4))
        assertEquals(listOf(17, 24), BowlingScoring.cumulative(codes).take(2))
        // Strike seul : le score de la frame n'est pas encore connu.
        assertNull(BowlingScoring.cumulative(listOf(strike())).first())
        assertEquals(0, BowlingScoring.total(listOf(strike())))
        // Spare suivi d'un seul lancer connu.
        assertEquals(listOf(14, 19), BowlingScoring.cumulative(listOf(frame(4, 6), frame(4, 1))).take(2))
    }

    @Test
    fun laNotationUtiliseXSlashEtTiret() {
        assertEquals("X", BowlingScoring.notation(listOf(10)))
        assertEquals("7 /", BowlingScoring.notation(listOf(7, 3)))
        assertEquals("- 5", BowlingScoring.notation(listOf(0, 5)))
        assertEquals("X X X", BowlingScoring.notation(listOf(10, 10, 10)))
        assertEquals("X 5 /", BowlingScoring.notation(listOf(10, 5, 5)))
        assertEquals("9 / X", BowlingScoring.notation(listOf(9, 1, 10)))
        // Un zéro puis 10 quilles : c'est un spare, pas un strike.
        assertEquals("- /", BowlingScoring.notation(listOf(0, 10)))
    }

    @Test
    fun lesLancersProposesDependentDeLaFrame() {
        assertEquals(10, BowlingScoring.maxNextRoll(0, emptyList()))
        assertEquals(3, BowlingScoring.maxNextRoll(0, listOf(7)))
        assertNull(BowlingScoring.maxNextRoll(0, listOf(10)))
        assertNull(BowlingScoring.maxNextRoll(0, listOf(7, 2)))
        // Dixième frame : strike puis 4 -> il reste 6 quilles au troisième lancer.
        assertEquals(6, BowlingScoring.maxNextRoll(9, listOf(10, 4)))
        assertEquals(10, BowlingScoring.maxNextRoll(9, listOf(10, 10)))
        assertEquals(10, BowlingScoring.maxNextRoll(9, listOf(6, 4)))
        assertNull(BowlingScoring.maxNextRoll(9, listOf(6, 3)))
        assertNull(BowlingScoring.maxNextRoll(9, listOf(10, 10, 10)))
    }

    @Test
    fun laPartieDeBowlingSeTermineQuandLesDixFramesSontSaisies() {
        val vm = newGame("builtin_bowling", "Anna")
        assertEquals(10, vm.scores.size)
        for (frame in 0 until 9) {
            assertFalse(vm.isGameOver())
            vm.setCell(frame, 0, strike(), false)
        }
        assertFalse(vm.isGameOver())
        vm.setCell(9, 0, frame(10, 10, 10), false)
        assertTrue(vm.isGameOver())
        assertEquals(300, vm.totalFor(0))
    }

    @Test
    fun lesTotauxEnregistresSontCeuxDeLaPartieEnCours() {
        val vm = newGame("builtin_molkky", "Anna", "Ben")
        vm.setCell(0, 0, 40, false)
        vm.setCell(1, 0, 20, false)
        val saved = vm.snapshot()
        assertEquals(vm.totals, saved.totals())
        val darts = newGame("builtin_darts501", "Anna", "Ben")
        darts.setCell(0, 0, 100, false)
        assertEquals(darts.totals, darts.snapshot().totals())
        val bowling = newGame("builtin_bowling", "Anna")
        bowling.setCell(0, 0, strike(), false)
        bowling.setCell(1, 0, frame(3, 4), false)
        assertEquals(24, bowling.snapshot().totals().first())
    }

    @Test
    fun lesReglesParticulieresSontConserveesDansLeJournal() {
        val molkky = GameRules.fromJson(game("builtin_molkky").toJson())
        assertEquals(50, molkky.exactTarget)
        assertEquals(25, molkky.exactReset)
        assertEquals(301, GameRules.fromJson(game("builtin_darts301").toJson()).countdownFrom)
        assertEquals(ScoreSheets.BOWLING, GameRules.fromJson(game("builtin_bowling").toJson()).sheet)
        assertNull(GameRules.fromJson(game("builtin_pingpong").toJson()).exactTarget)
    }

    @Test
    fun lesJeuxDeCompteurSArretentAuSeuil() {
        val trivial = newGame("builtin_trivial", "Anna", "Ben")
        repeat(5) { trivial.incrementCounter(0, 1) }
        assertFalse(trivial.isGameOver())
        trivial.incrementCounter(0, 1)
        assertTrue(trivial.isGameOver())
        val catane = game("builtin_catane")
        assertEquals(10, catane.endCondition.scoreThreshold)
        assertEquals(13, game("builtin_petanque").endCondition.scoreThreshold)
        assertEquals(11, game("builtin_pingpong").endCondition.scoreThreshold)
        assertEquals(21, game("builtin_badminton").endCondition.scoreThreshold)
        assertEquals(25, game("builtin_volley").endCondition.scoreThreshold)
        assertEquals(10, game("builtin_babyfoot").endCondition.scoreThreshold)
        assertTrue(game("builtin_golf").lowestWins)
    }

    @Test
    fun laSuecaSArreteAQuatrePointsDePartie() {
        val sueca = game("builtin_sueca")
        assertEquals(GameCategory.CARDS, sueca.category())
        assertEquals(4, sueca.endCondition.scoreThreshold)
        val vm = newGame("builtin_sueca", "Nous", "Eux")
        vm.setCell(0, 0, 2, false)
        vm.setCell(1, 1, 1, false)
        vm.setCell(2, 0, 1, false)
        assertFalse(vm.isGameOver())
        // La « bandeira » (tous les plis) rapporte 4 points : l'équipe gagne la partie.
        vm.setCell(3, 0, 1, false)
        assertTrue(vm.isGameOver())
        assertEquals(listOf(0), vm.winners())
    }
}
