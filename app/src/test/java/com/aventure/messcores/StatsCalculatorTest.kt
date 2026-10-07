package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsCalculatorTest {

    /** Partie en mode compteur : [totals] sont directement les scores finaux, dans l'ordre de [players]. */
    private fun game(
        savedAt: Long,
        players: List<String>,
        totals: List<Int>,
        gameId: String = "belote",
        gameName: String = "Belote",
        lowestWins: Boolean = false,
        finished: Boolean = true
    ) = SavedGame(
        id = "id-$savedAt-$gameId",
        savedAt = savedAt,
        gameRules = GameRules(id = gameId, name = gameName, lowestWins = lowestWins, scoreMode = ScoreMode.COUNTER),
        players = players,
        playerColorsArgb = emptyList(),
        counters = totals,
        isFinished = finished
    )

    @Test
    fun victoiresEtPartiesParJoueur() {
        val report = StatsCalculator.compute(
            listOf(
                game(1, listOf("Anna", "Ben"), listOf(100, 80)),
                game(2, listOf("Anna", "Ben"), listOf(50, 90))
            )
        )
        assertEquals(2, report.finishedCount)
        assertEquals(listOf("Anna", "Ben"), report.overall.map { it.name })
        assertEquals(listOf(2, 2), report.overall.map { it.played })
        assertEquals(listOf(1, 1), report.overall.map { it.wins })
        assertEquals(listOf(50, 50), report.overall.map { it.winRatePercent })
    }

    @Test
    fun meilleurScoreEtMoyenneParJeu_surLeJeuSeulement() {
        val report = StatsCalculator.compute(
            listOf(
                game(1, listOf("Anna", "Ben"), listOf(100, 80)),
                game(2, listOf("Anna", "Ben"), listOf(50, 90))
            )
        )
        val belote = report.byGame.getValue("belote")
        val anna = belote.first { it.name == "Anna" }
        val ben = belote.first { it.name == "Ben" }
        assertEquals(100, anna.bestScore)
        assertEquals(75.0, anna.averageScore!!, 0.0001)
        assertEquals(90, ben.bestScore)
        assertEquals(85.0, ben.averageScore!!, 0.0001)
        // Tous jeux confondus, les scores ne se comparent pas : rien à afficher.
        assertTrue(report.overall.all { it.bestScore == null && it.averageScore == null })
    }

    @Test
    fun jeuOuLeScoreLePlusBasGagne_meilleurScoreEstLePlusBas() {
        val report = StatsCalculator.compute(
            listOf(game(1, listOf("Anna", "Ben"), listOf(30, 20), gameId = "skyjo", gameName = "Skyjo", lowestWins = true))
        )
        val skyjo = report.byGame.getValue("skyjo")
        assertEquals(listOf("Ben", "Anna"), skyjo.map { it.name })
        assertEquals(1, skyjo[0].wins)
        assertEquals(20, skyjo[0].bestScore)
        assertEquals(30, skyjo[1].bestScore)
    }

    @Test
    fun partiesEnCoursIgnoreesMaisComptees() {
        val report = StatsCalculator.compute(
            listOf(
                game(1, listOf("Anna", "Ben"), listOf(100, 80)),
                game(2, listOf("Anna", "Ben"), listOf(5, 500), finished = false)
            )
        )
        assertEquals(1, report.finishedCount)
        assertEquals(1, report.inProgressCount)
        assertEquals(1, report.overall.first { it.name == "Ben" }.played)
        assertEquals(0, report.overall.first { it.name == "Ben" }.wins)
    }

    @Test
    fun egaliteEnTete_chaqueJoueurAEgaliteGagne() {
        val game = game(1, listOf("Anna", "Ben", "Cléo"), listOf(50, 50, 30))
        assertEquals(listOf(0, 1), StatsCalculator.winnersOf(game))
        val report = StatsCalculator.compute(listOf(game))
        assertEquals(1, report.overall.first { it.name == "Anna" }.wins)
        assertEquals(1, report.overall.first { it.name == "Ben" }.wins)
        assertEquals(0, report.overall.first { it.name == "Cléo" }.wins)
    }

    @Test
    fun toutLeMondeAEgalite_personneNeGagne() {
        val game = game(1, listOf("Anna", "Ben"), listOf(40, 40))
        assertTrue(StatsCalculator.winnersOf(game).isEmpty())
        val report = StatsCalculator.compute(listOf(game))
        assertEquals(listOf(0, 0), report.overall.map { it.wins })
        assertEquals(listOf(1, 1), report.overall.map { it.played })
    }

    @Test
    fun partieNonTerminee_pasDeVainqueur() {
        assertTrue(StatsCalculator.winnersOf(game(1, listOf("Anna", "Ben"), listOf(9, 1), finished = false)).isEmpty())
    }

    @Test
    fun nomsReconnusSansTenirCompteDeLaCasseNiDesEspaces() {
        val report = StatsCalculator.compute(
            listOf(
                game(1, listOf("anna ", "Ben"), listOf(10, 5)),
                game(2, listOf("Anna", "Ben"), listOf(10, 5))
            )
        )
        assertEquals(2, report.overall.size)
        val anna = report.overall.first()
        assertEquals("Anna", anna.name) // orthographe de la partie la plus récente
        assertEquals(2, anna.played)
        assertEquals(2, anna.wins)
    }

    @Test
    fun memeNomDeuxFoisDansUnePartie_compteUneSeuleFois() {
        val report = StatsCalculator.compute(listOf(game(1, listOf("Anna", "anna"), listOf(10, 5))))
        assertEquals(1, report.overall.size)
        assertEquals(1, report.overall[0].played)
    }

    @Test
    fun jeuxTriesDuPlusJoueAuMoinsJoue() {
        val report = StatsCalculator.compute(
            listOf(
                game(1, listOf("Anna", "Ben"), listOf(1, 2), gameId = "tarot", gameName = "Tarot"),
                game(2, listOf("Anna", "Ben"), listOf(1, 2), gameId = "belote", gameName = "Belote"),
                game(3, listOf("Anna", "Ben"), listOf(2, 1), gameId = "belote", gameName = "Belote")
            )
        )
        assertEquals(listOf("Belote", "Tarot"), report.games.map { it.gameName })
        assertEquals(listOf(2, 1), report.games.map { it.finishedGames })
    }

    @Test
    fun journalVide_aucuneStatistique() {
        val report = StatsCalculator.compute(emptyList())
        assertEquals(0, report.finishedCount)
        assertTrue(report.overall.isEmpty())
        assertTrue(report.games.isEmpty())
        assertNull(report.byGame["belote"])
    }

    @Test
    fun classementDesJoueurs_victoiresPuisPourcentagePuisParties() {
        val report = StatsCalculator.compute(
            listOf(
                // Ben gagne 1 sur 1 (100 %), Anna gagne 1 sur 2 (50 %), Cléo ne gagne jamais.
                game(1, listOf("Anna", "Ben", "Cléo"), listOf(10, 5, 1)),
                game(2, listOf("Anna", "Cléo"), listOf(1, 10)),
                game(3, listOf("Ben", "Cléo"), listOf(10, 1))
            )
        )
        // Victoires : Anna 1 (partie 1), Cléo 1 (partie 2), Ben 1 (partie 3).
        val byName = report.overall.associateBy { it.name }
        assertEquals(2, byName.getValue("Anna").played)
        assertEquals(1, byName.getValue("Anna").wins)
        assertEquals(3, byName.getValue("Cléo").played)
        assertEquals(2, byName.getValue("Ben").played)
        // À 1 victoire chacun : le meilleur pourcentage d'abord (Ben 50 %, Anna 50 %, Cléo 33 %), puis le moins de parties.
        assertEquals("Cléo", report.overall.last().name)
    }
}
