package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Les gestionnaires de mode ([TableMode], [CounterMode], [TeamsMode], [RacketMode], [PhasesState])
 * testés seuls, sans [ScoreViewModel] : c'est tout l'intérêt de les avoir séparés.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class ModeStateTest {

    private val tableRules = GameRules(id = "t", name = "T")
    private val teamsRules = GameRules(id = "v", name = "V", scoreMode = ScoreMode.VARIABLE_TEAMS)

    // ---------- TableMode ----------

    private fun table(isOver: () -> Boolean = { false }) =
        TableMode(rules = { tableRules }, isGameOver = isOver).also { it.start(2) }

    @Test
    fun leTableauCommenceAvecSesManchesVides() {
        val table = table()
        assertEquals(TableMode.INITIAL_ROUNDS, table.scores.size)
        assertFalse(table.hasProgress())
    }

    @Test
    fun saisirDansLaDerniereMancheEnAjouteUneEtAnnulerLaRetire() {
        val table = table()
        val last = TableMode.INITIAL_ROUNDS - 1
        table.setCell(last, 0, 10, false)
        assertEquals(TableMode.INITIAL_ROUNDS + 1, table.scores.size)
        assertEquals(10, table.total(0))

        table.undo()
        assertEquals(TableMode.INITIAL_ROUNDS, table.scores.size)
        assertNull(table.scores[last][0].baseValue)
        assertTrue(table.canRedo)

        table.redo()
        assertEquals(TableMode.INITIAL_ROUNDS + 1, table.scores.size)
        assertEquals(10, table.scores[last][0].baseValue)
    }

    @Test
    fun uneNouvelleSaisieVideLesSaisiesRetablissables() {
        val table = table()
        table.setCell(0, 0, 1, false)
        table.undo()
        assertTrue(table.canRedo)
        table.setCell(0, 1, 2, false)
        assertFalse(table.canRedo)
    }

    @Test
    fun uneSaisieSansChangementNEstPasAnnulable() {
        val table = table()
        table.setCell(0, 0, 5, false)
        table.undo()
        table.setCell(0, 0, null, false)
        assertFalse(table.canUndo)
    }

    @Test
    fun aucuneMancheNEstAjouteeQuandLaPartieEstTerminee() {
        val table = table(isOver = { true })
        table.setCell(TableMode.INITIAL_ROUNDS - 1, 0, 10, false)
        assertEquals(TableMode.INITIAL_ROUNDS, table.scores.size)
    }

    @Test
    fun laReprisePartDesCasesEnregistrees() {
        val saved = SavedGame(
            id = "x",
            savedAt = 0L,
            gameRules = tableRules,
            players = listOf("A", "B"),
            playerColorsArgb = emptyList(),
            cellSnapshots = listOf(
                listOf(CellSnapshot(3, false, GameRules.NORMAL_MULTIPLIER_ID), CellSnapshot(4, true, GameRules.NORMAL_MULTIPLIER_ID))
            ),
            isFinished = false
        )
        val table = TableMode(rules = { tableRules }, isGameOver = { false })
        table.restore(saved, 2)
        assertEquals(1, table.scores.size)
        assertEquals(3, table.total(0))
        assertEquals(-4, table.total(1))
        assertEquals(1, table.roundsPlayed())
        assertEquals(saved.cellSnapshots, table.applyTo(saved.copy(cellSnapshots = null)).cellSnapshots)
    }

    // ---------- CounterMode ----------

    @Test
    fun leCompteurSAjouteSAnnuleEtSeRetablit() {
        val counter = CounterMode(isGameOver = { false }).also { it.start(2) }
        counter.increment(1, 5)
        counter.increment(1, -2)
        assertEquals(3, counter.total(1))

        counter.undo()
        assertEquals(5, counter.total(1))
        counter.redo()
        assertEquals(3, counter.total(1))
        assertFalse(counter.canRedo)
    }

    @Test
    fun uneFoisLaPartieTermineeLeCompteurNeBougePlus() {
        var over = false
        val counter = CounterMode(isGameOver = { over }).also { it.start(2) }
        counter.increment(0, 1)
        over = true
        counter.increment(0, 1)
        assertEquals(1, counter.total(0))
    }

    @Test
    fun fermerLaPartieVideLHistoriqueMaisPasLesCompteurs() {
        val counter = CounterMode(isGameOver = { false }).also { it.start(1) }
        counter.increment(0, 4)
        counter.close()
        assertFalse(counter.canUndo)
        assertEquals(4, counter.total(0))
    }

    // ---------- TeamsMode ----------

    @Test
    fun lEquipeAGagneLaValeurEtLesAutresSonOppose() {
        val teams = TeamsMode(rules = { teamsRules })
        teams.add("A", setOf(0), 10, false, GameRules.NORMAL_MULTIPLIER_ID)
        val value = teamsRules.effectiveValue(10, false, GameRules.NORMAL_MULTIPLIER_ID)
        assertEquals(value, teams.total(0))
        assertEquals(-value, teams.total(1))
        assertEquals(1, teams.roundsPlayed())
    }

    @Test
    fun lesPointsExactsRemplacentLaRegleSimple() {
        val teams = TeamsMode(rules = { teamsRules })
        teams.append(TeamRound("A", setOf(0), 5, deltas = listOf(3, -1, -2)))
        assertEquals(listOf(3, -1, -2), (0..2).map { teams.total(it) })
    }

    @Test
    fun onCorrigeEtOnSupprimeUneManche() {
        val teams = TeamsMode(rules = { teamsRules })
        teams.append(TeamRound("A", setOf(0), 5))
        teams.replace(0, TeamRound("A", setOf(1), 7))
        assertEquals(7, teams.total(1))
        teams.remove(0)
        assertFalse(teams.hasProgress())
        // Index hors limites : sans effet.
        teams.replace(3, TeamRound("A", setOf(0), 1))
        teams.remove(3)
        assertEquals(0, teams.roundsPlayed())
        assertFalse(teams.canUndo)
    }

    // ---------- RacketMode ----------

    private fun racket(players: Int = 2) =
        RacketMode(rules = { GameRules(id = RacketConfig.TENNIS_ID, name = "Tennis", scoreMode = ScoreMode.RACKET) })
            .also { it.start(players) }

    private fun RacketMode.winGame(side: Int) = repeat(4) { point(side) }

    private fun RacketMode.winSet(side: Int) = repeat(6) { winGame(side) }

    @Test
    fun lesPointsSAnnulentEtSeRetablissent() {
        val racket = racket()
        racket.point(0)
        racket.point(1)
        assertTrue(racket.canUndo)
        racket.undo()
        assertTrue(racket.canRedo)
        racket.redo()
        assertFalse(racket.canRedo)
        racket.point(0)
        assertFalse(racket.canRedo)
    }

    @Test
    fun unCampInvalideEstIgnore() {
        val racket = racket()
        racket.point(2)
        racket.point(-1)
        assertFalse(racket.hasProgress())
    }

    @Test
    fun lesSetsGagnesSontLesTotauxEtLeMatchSArreteALaVictoire() {
        val racket = racket()
        racket.winSet(0)
        assertEquals(1, racket.total(0))
        assertEquals(0, racket.total(1))
        assertFalse(racket.gameOver())
        racket.winSet(0)
        assertTrue(racket.gameOver())
        // Match terminé : plus de point accepté.
        racket.point(1)
        assertEquals(0, racket.total(1))
        assertEquals(2, racket.roundsPlayed())
    }

    @Test
    fun lesReglagesNeChangentPlusDesQueLePremierPointEstJoue() {
        val racket = racket()
        assertTrue(racket.setConfig(RacketConfig(setsToWin = 1)))
        assertEquals(1, racket.config.setsToWin)
        racket.point(0)
        assertFalse(racket.setConfig(RacketConfig(setsToWin = 3)))
        assertEquals(1, racket.config.setsToWin)
    }

    @Test
    fun lOrdreDesCampsEstValideAvantDeChangerQuoiQueCeSoit() {
        val racket = racket(players = 4)
        assertNotNull(racket.teamOrder(4, listOf(0, 2)))
        // Déjà dans cet ordre, mauvaise taille, index en double ou hors limites : refusé.
        assertNull(racket.teamOrder(4, listOf(0, 1)))
        assertNull(racket.teamOrder(4, listOf(0)))
        assertNull(racket.teamOrder(4, listOf(2, 2)))
        assertNull(racket.teamOrder(4, listOf(0, 7)))
        racket.point(0)
        assertNull(racket.teamOrder(4, listOf(0, 2)))
    }

    @Test
    fun fermerLaPartieRemetLeMatchAZero() {
        val racket = racket()
        racket.setConfig(RacketConfig(setsToWin = 3))
        racket.point(0)
        racket.close()
        assertFalse(racket.hasProgress())
        assertEquals(RacketConfig(), racket.config)
    }

    // ---------- PhasesState ----------

    @Test
    fun laPhaseResteEntreLUneEtLaDerniereEnCompterLaFin() {
        val phases = PhasesState()
        phases.start(GameRules(id = "p", name = "P", scoreMode = ScoreMode.COUNTER, phases = 10), 2)
        assertEquals(listOf(1, 1), phases.values)
        phases.set(0, 99, 10)
        assertEquals(11, phases.phaseFor(0))
        phases.set(0, 0, 10)
        assertEquals(1, phases.phaseFor(0))
        // Joueur inexistant : sans effet.
        phases.set(5, 3, 10)
        assertEquals(listOf(1, 1), phases.values)
    }

    @Test
    fun unJeuSansPhasesNEnSuitAucune() {
        val phases = PhasesState()
        phases.start(tableRules, 3)
        assertTrue(phases.values.isEmpty())
    }
}
