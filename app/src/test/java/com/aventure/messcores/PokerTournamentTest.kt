package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Montée des blinds, pauses programmées, infos de table et gains du tournoi de poker. */
class PokerTournamentTest {

    private val minute = 60_000L

    private fun settings(
        small: Int = 25,
        big: Int = 50,
        levelMinutes: Int = 10,
        pace: PokerPace = PokerPace.REGULAR,
        breakEvery: Int = 0,
        breakMinutes: Int = 0,
        buyIn: Int = 0,
        payouts: List<Int> = emptyList(),
        rebuyUntil: Int = 0
    ) = PokerSettings(
        startingStack = 5000, smallBlind = small, bigBlind = big, levelMinutes = levelMinutes,
        anteEnabled = false, anteAfterIncreases = 0, firstDealer = 0,
        pace = pace, breakEveryLevels = breakEvery, breakMinutes = breakMinutes,
        buyIn = buyIn, payoutPercents = payouts, rebuyUntilLevel = rebuyUntil
    )

    // ---------- Blinds ----------

    @Test
    fun structureRegulieresansSautDe60Pour100() {
        val s = settings()
        val bigs = (1..8).map { PokerBlinds.bigBlindAt(s, it) }
        assertEquals(listOf(50, 60, 80, 100, 120, 150, 200, 250), bigs)
        // 25/50 puis 30/60 puis 40/80 : la petite blind garde la proportion du départ.
        assertEquals(30, PokerBlinds.smallBlindAt(s, 2))
        assertEquals(40, PokerBlinds.smallBlindAt(s, 3))
    }

    @Test
    fun turboEtRapideMontentPlusVite() {
        val regular = PokerBlinds.bigBlindAt(settings(), 4)
        val fast = PokerBlinds.bigBlindAt(settings(pace = PokerPace.FAST), 4)
        val turbo = PokerBlinds.bigBlindAt(settings(pace = PokerPace.TURBO), 4)
        assertTrue(regular < fast && fast < turbo)
        assertEquals(400, turbo)
    }

    @Test
    fun laBlindMonteToujoursMemeAuxPetitesValeurs() {
        listOf(PokerPace.REGULAR, PokerPace.FAST, PokerPace.TURBO).forEach { pace ->
            var bb = 1
            repeat(30) {
                val next = PokerBlinds.nextBigBlind(bb, pace)
                assertTrue("$pace : $bb -> $next", next > bb)
                bb = next
            }
        }
    }

    // ---------- Pauses ----------

    @Test
    fun sansPauseLeTempsSeComporteCommeAvant() {
        val s = settings()
        assertEquals(1, PokerBlinds.levelAt(s, 0))
        assertEquals(2, PokerBlinds.levelAt(s, 10 * minute))
        assertFalse(PokerBlinds.isBreak(s, 500 * minute))
        assertFalse(PokerBlinds.breakStartsNext(s, 35 * minute))
        assertEquals(10 * minute, PokerBlinds.elapsedAtNextLevel(s, 5 * minute))
        assertEquals(5 * minute, PokerBlinds.remainingMs(s, 5 * minute))
    }

    @Test
    fun pauseApresQuatreNiveaux() {
        // 4 niveaux de 10 min, puis 15 min de pause : cycle de 55 min.
        val s = settings(breakEvery = 4, breakMinutes = 15)
        assertEquals(4, PokerBlinds.levelAt(s, 39 * minute))
        assertFalse(PokerBlinds.isBreak(s, 39 * minute))
        assertTrue(PokerBlinds.breakStartsNext(s, 39 * minute))
        assertFalse(PokerBlinds.breakStartsNext(s, 29 * minute))

        // Pendant la pause : même niveau, et la reprise est dans 15 min au début.
        assertTrue(PokerBlinds.isBreak(s, 40 * minute))
        assertEquals(4, PokerBlinds.levelAt(s, 54 * minute))
        assertEquals(15 * minute, PokerBlinds.remainingMs(s, 40 * minute))
        assertFalse(PokerBlinds.breakStartsNext(s, 45 * minute))

        // Après la pause : niveau 5, puis le cycle recommence.
        assertEquals(5, PokerBlinds.levelAt(s, 55 * minute))
        assertEquals(8, PokerBlinds.levelAt(s, 94 * minute))
        assertTrue(PokerBlinds.isBreak(s, 95 * minute))
        assertEquals(9, PokerBlinds.levelAt(s, 110 * minute))
    }

    @Test
    fun lesBoutonsSautentUnChangementALaFois() {
        val s = settings(breakEvery = 2, breakMinutes = 5)
        // Dans le 2e niveau (10..20 min) : le suivant changement est la pause à 20 min.
        assertEquals(20 * minute, PokerBlinds.elapsedAtNextLevel(s, 12 * minute))
        // Pendant la pause (20..25 min) : on saute à la reprise.
        assertEquals(25 * minute, PokerBlinds.elapsedAtNextLevel(s, 21 * minute))
        assertEquals(3, PokerBlinds.levelAt(s, 25 * minute))
    }

    @Test
    fun pauseIgnoreeSiBlindsFixesOuDureeNulle() {
        assertFalse(PokerBlinds.isBreak(settings(levelMinutes = 0, breakEvery = 2, breakMinutes = 5), 100 * minute))
        assertFalse(PokerBlinds.isBreak(settings(breakEvery = 2, breakMinutes = 0), 100 * minute))
        assertFalse(PokerBlinds.breakAfterLevel(settings(breakEvery = 0, breakMinutes = 5), 4))
        assertTrue(PokerBlinds.breakAfterLevel(settings(breakEvery = 4, breakMinutes = 5), 8))
    }

    // ---------- Infos de table ----------

    private fun gameOf(vararg stacks: Int, s: PokerSettings = settings(small = 50, big = 100)): PokerGame {
        val base = PokerEngine.newGame(s, stacks.size)
        return PokerEngine.startHand(
            base.copy(seats = stacks.map { PokerSeat(stack = it) }, handNumber = 0, phase = PokerPhase.HAND_OVER),
            level = 1,
            keepDealer = true
        )
    }

    @Test
    fun infosDeTableEnDebutDeMain() {
        val g = gameOf(3000, 5000, 1000, 1000)
        val info = PokerEngine.tableInfo(g)!!
        assertEquals(4, info.alive)
        assertEquals(4, info.total)
        // Les blinds déjà postées comptent pour leur propriétaire : total de 10 000 jetons.
        assertEquals(2500, info.averageStack)
        assertEquals(25, info.averageBigBlinds)
        assertEquals(1, info.leader)
        assertEquals(5000, info.leaderChips)
        assertEquals(50, info.leaderBigBlinds)
    }

    @Test
    fun infosDeTableIgnorentLesElimines() {
        val g = gameOf(3000, 5000, 1000)
        val over = g.copy(
            phase = PokerPhase.HAND_OVER,
            seats = g.seats.mapIndexed { i, seat ->
                if (i == 2) seat.copy(stack = 0, out = true, totalBet = 0) else seat.copy(totalBet = 0, roundBet = 0)
            }
        )
        val info = PokerEngine.tableInfo(over)!!
        assertEquals(2, info.alive)
        assertEquals(3, info.total)
        assertEquals((over.seats[0].stack + over.seats[1].stack) / 2, info.averageStack)
    }

    @Test
    fun infosDeTableVideSiPersonneNEstEnLice() {
        val g = gameOf(1000, 1000)
        assertNull(PokerEngine.tableInfo(g.copy(seats = g.seats.map { it.copy(out = true) })))
    }

    // ---------- Gains ----------

    /** Tournoi fini : [order] = joueurs éliminés du premier au dernier, [champion] = vainqueur. */
    private fun finished(s: PokerSettings, players: Int, order: List<Int>, champion: Int): PokerGame {
        val base = PokerEngine.newGame(s, players)
        return base.copy(
            seats = List(players) { i ->
                if (i == champion) PokerSeat(stack = players * s.startingStack) else PokerSeat(stack = 0, out = true)
            },
            phase = PokerPhase.HAND_OVER,
            eliminated = order
        )
    }

    @Test
    fun gagnantEmpocheToutLaCagnotte() {
        val s = settings(buyIn = 10, payouts = listOf(100))
        val g = finished(s, 4, order = listOf(2, 0, 3), champion = 1)
        assertEquals(listOf(0, 40, 0, 0), PokerPrizes.payouts(g))
        assertEquals(40, PokerPrizes.pool(s, 4))
    }

    @Test
    fun repartitionSelonLeClassementFinal() {
        val s = settings(buyIn = 20, payouts = listOf(50, 30, 20))
        // Éliminés dans l'ordre 4, 0, 3, 5, 2 puis vainqueur 1 : classement 1er = 1, 2e = 2, 3e = 5.
        val g = finished(s, 6, order = listOf(4, 0, 3, 5, 2), champion = 1)
        val payouts = PokerPrizes.payouts(g)
        assertEquals(120, payouts.sum())
        assertEquals(60, payouts[1])
        assertEquals(36, payouts[2])
        assertEquals(24, payouts[5])
        assertEquals(0, payouts[0])
    }

    @Test
    fun lesJetonsIndivisiblesVontAuVainqueurEtLeTotalEstConserve() {
        val s = settings(buyIn = 7, payouts = listOf(65, 35))
        val g = finished(s, 3, order = listOf(0, 2), champion = 1)
        val payouts = PokerPrizes.payouts(g)
        assertEquals(21, payouts.sum())
        assertTrue(payouts[1] >= payouts[2])
    }

    @Test
    fun pasDeGainsSansBuyIn() {
        val s = settings(buyIn = 0, payouts = listOf(100))
        assertEquals(listOf(0, 0, 0), PokerPrizes.payouts(finished(s, 3, order = listOf(0, 2), champion = 1)))
        val noSplit = settings(buyIn = 10, payouts = emptyList())
        assertEquals(listOf(0, 0, 0), PokerPrizes.payouts(finished(noSplit, 3, order = listOf(0, 2), champion = 1)))
    }

    @Test
    fun reglagesSontSauvegardesEtAnciennesPartiesRestentLisibles() {
        val s = settings(breakEvery = 3, breakMinutes = 8, buyIn = 15, payouts = listOf(65, 35), pace = PokerPace.TURBO)
        assertEquals(s, PokerSettings.fromJson(s.toJson()))
        // Ancien enregistrement sans les nouveaux champs.
        val old = s.toJson().apply {
            remove("pace"); remove("breakEveryLevels"); remove("breakMinutes")
            remove("levelAlert"); remove("buyIn"); remove("payoutPercents")
        }
        val loaded = PokerSettings.fromJson(old)
        assertEquals(PokerPace.REGULAR, loaded.pace)
        assertEquals(0, loaded.breakEveryLevels)
        assertEquals(0, loaded.buyIn)
        assertTrue(loaded.levelAlert)
        assertTrue(loaded.payoutPercents.isEmpty())
    }

    // ---------- Recaves ----------

    /** Partie à 3 joueurs : le joueur 2 vient d'être éliminé et la main est payée. */
    private fun withBustedPlayer(s: PokerSettings): PokerGame {
        val base = PokerEngine.newGame(s, 3)
        return base.copy(
            seats = listOf(PokerSeat(stack = 6000), PokerSeat(stack = 9000), PokerSeat(stack = 0, out = true, inHand = true)),
            phase = PokerPhase.HAND_OVER,
            eliminated = listOf(2),
            handNumber = 5
        )
    }

    @Test
    fun recaveRemetLeJoueurEnJeuAvecLeTapisDeDepart() {
        val g = withBustedPlayer(settings(rebuyUntil = 3, buyIn = 10, payouts = listOf(100)))
        assertTrue(PokerEngine.canRebuy(g, 2, level = 3))
        val r = PokerEngine.rebuy(g, 2, level = 3)
        assertFalse(r.seats[2].out)
        assertEquals(5000, r.seats[2].stack)
        assertEquals(listOf(0, 0, 1), r.rebuys)
        assertTrue(r.eliminated.isEmpty())
        // La cagnotte compte la recave : 3 buy-ins + 1 recave.
        assertEquals(40, PokerPrizes.pool(r.settings, 3, r.rebuys.sum()))
        // La main suivante se joue à trois.
        assertEquals(3, PokerEngine.nextHand(r, level = 3).seats.count { it.inHand })
    }

    @Test
    fun recaveRefuseeApresLaPeriodeOuSansReglage() {
        val g = withBustedPlayer(settings(rebuyUntil = 3))
        assertFalse(PokerEngine.canRebuy(g, 2, level = 4))
        assertEquals(g, PokerEngine.rebuy(g, 2, level = 4))
        assertFalse(PokerEngine.canRebuy(withBustedPlayer(settings(rebuyUntil = 0)), 2, level = 1))
        // Un joueur encore en lice ne peut pas racheter.
        assertFalse(PokerEngine.canRebuy(g, 0, level = 1))
    }

    @Test
    fun recaveImpossiblePendantUneMain() {
        val g = withBustedPlayer(settings(rebuyUntil = 3)).copy(phase = PokerPhase.BETTING)
        assertFalse(PokerEngine.canRebuy(g, 2, level = 1))
    }

    @Test
    fun recavePossibleMemeSiUnSeulJoueurRestait() {
        val s = settings(rebuyUntil = 2)
        val g = withBustedPlayer(s).let {
            it.copy(
                seats = listOf(PokerSeat(stack = 15000), PokerSeat(stack = 0, out = true), PokerSeat(stack = 0, out = true)),
                eliminated = listOf(2, 1)
            )
        }
        assertEquals(0, g.champion)
        val r = PokerEngine.rebuy(g, 1, level = 1)
        assertNull(r.champion)
        // Le joueur 2 reste éliminé en dernière position.
        assertEquals(listOf(2), r.eliminated)
        assertEquals(3, PokerEngine.ranks(r)[2])
    }

    @Test
    fun uneRecaveNEstPasUnClassementEtLesGainsSuiventLaCagnotte() {
        val s = settings(buyIn = 10, payouts = listOf(100), rebuyUntil = 5)
        // Joueur 2 éliminé, recave, puis éliminé de nouveau ; le joueur 0 gagne.
        val g = PokerEngine.newGame(s, 3).copy(
            seats = listOf(PokerSeat(stack = 15000), PokerSeat(stack = 0, out = true), PokerSeat(stack = 0, out = true)),
            phase = PokerPhase.HAND_OVER,
            eliminated = listOf(1, 2),
            rebuys = listOf(0, 0, 1)
        )
        assertEquals(listOf(40, 0, 0), PokerPrizes.payouts(g))
    }

    @Test
    fun recavesSauvegardeesEtSuiventLesJoueursAuChangementDeTable() {
        val g = withBustedPlayer(settings(rebuyUntil = 3)).copy(rebuys = listOf(1, 0, 2))
        assertEquals(g, PokerGame.fromJson(g.toJson()))
        // Ancien enregistrement sans compteur de recaves.
        val old = g.toJson().apply { remove("rebuys") }
        assertEquals(listOf(0, 0, 0), PokerGame.fromJson(old).rebuys)

        val reseat = PokerEngine.reseat(g.copy(seats = g.seats.map { it.copy(out = false, stack = 1000) }, eliminated = emptyList()),
            remove = setOf(1), arrivalStacks = listOf(2000), random = kotlin.random.Random(1))!!
        val newRebuys = reseat.game.rebuys
        assertEquals(3, newRebuys.size)
        assertEquals(1, newRebuys[reseat.oldToNew[0]])
        assertEquals(2, newRebuys[reseat.oldToNew[2]])
        assertEquals(0, newRebuys[reseat.arrivals[0]])
    }
}
