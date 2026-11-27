package com.aventure.messcores

import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ButcherTest {
    private fun game(seed: Int = 1) = ButcherGame(Random(seed))

    /** Une partie dont la première carcasse est de la viande [meat] (on cherche une graine qui convient). */
    private fun gameWithFirstCarcass(meat: Int): ButcherGame {
        for (seed in 0 until 500) {
            val g = ButcherGame(Random(seed))
            if (g.carcassMeat == meat) return g
        }
        error("aucune graine ne donne cette viande")
    }

    private fun advance(g: ButcherGame, ms: Float, stepMs: Float = 50f) {
        var left = ms
        while (left > 0f) {
            val dt = minOf(stepMs, left)
            g.tick(dt)
            left -= dt
        }
    }

    /** Attend que la carcasse soit en place, déplace le couteau sur le pointillé le plus proche, puis coupe. Faux s'il n'y a rien à couper. */
    private fun aimAndCut(g: ButcherGame, autoClean: Boolean = false): Boolean {
        var guard = 0
        while ((g.carcassArriveMs > 0f || g.carcassLeaveMs > 0f) && g.stage == BUTCHER_STAGE_CUT && guard++ < 400) g.tick(25f)
        if (g.stage != BUTCHER_STAGE_CUT) return false
        val line = g.nearestLine()
        if (line < 0) return false
        if (autoClean && g.dirty && g.carcassMeat != BUTCHER_POULTRY) clean(g)
        var steps = 0
        while (steps++ < 600) {
            val gap = g.lineX(line) - g.knifeX
            if (abs(gap) < 3f) break
            g.press(if (gap > 0f) BUTCHER_RIGHT else BUTCHER_LEFT)
            g.tick(10f)
            g.release(BUTCHER_LEFT)
            g.release(BUTCHER_RIGHT)
        }
        g.press(BUTCHER_A)
        g.release(BUTCHER_A)
        return true
    }

    private fun clean(g: ButcherGame) {
        g.press(BUTCHER_B)
        g.tick(Butcher.CLEAN_MS + 10f)
        assertFalse(g.dirty)
    }

    /** Joue toute la découpe sans faute : le plan est nettoyé avant chaque viande qui n'est pas de la volaille. */
    private fun cutEverything(g: ButcherGame) {
        var guard = 0
        while (g.stage == BUTCHER_STAGE_CUT && guard++ < 600) {
            if (!aimAndCut(g, autoClean = true)) g.tick(50f)
        }
        assertTrue(g.stage != BUTCHER_STAGE_CUT)
    }

    /** Une partie en plein service, avec un client au comptoir qui a fini d'arriver. */
    private fun serving(seed: Int = 1): ButcherGame {
        val g = game(seed)
        cutEverything(g)
        var guard = 0
        while (g.stage == BUTCHER_STAGE_OPENING && guard++ < 200) g.tick(50f)
        assertEquals(BUTCHER_STAGE_SERVICE, g.stage)
        g.tick(Butcher.ARRIVE_MS)
        return g
    }

    /** Tient A jusqu'à [want] grammes puis relâche : une tranche coupée au poids demandé. */
    private fun sliceTo(g: ButcherGame, want: Int) {
        g.press(BUTCHER_A)
        var guard = 0
        while (g.sliceG < want && guard++ < 5000) g.tick(2f)
        g.release(BUTCHER_A)
    }

    /** Prend la bonne pièce (en nettoyant le plan si besoin) et coupe toutes les tranches au bon poids. */
    private fun serve(g: ButcherGame) {
        val need = g.slicesTotal * g.sliceWantG
        val slot = g.pieces.indices.first { i ->
            val p = g.pieces[i]
            p != null && p.meat == g.customerMeat && p.weightG >= need
        }
        if (g.dirty && g.customerMeat != BUTCHER_POULTRY) clean(g)
        var guard = 0
        while (g.cursor != slot && guard++ < Butcher.SLOTS) {
            g.press(BUTCHER_RIGHT)
            g.release(BUTCHER_RIGHT)
        }
        assertEquals(slot, g.cursor)
        g.press(BUTCHER_A)
        g.release(BUTCHER_A)
        assertEquals(BUTCHER_STEP_SLICE, g.step)
        val total = g.slicesTotal
        val want = g.sliceWantG
        repeat(total) { sliceTo(g, want) }
    }

    /** La même partie avec [points] points (le moteur n'a pas de réglage de score : on passe par la sauvegarde, où les points sont le 5e nombre). */
    private fun withPoints(g: ButcherGame, points: Int): ButcherGame {
        val values = g.save().toMutableList()
        values[4] = points.toDouble()
        return ButcherGame.restore(values)
    }

    // ---------------------------------------------------------------- la découpe

    @Test
    fun startsByCuttingACarcass() {
        val g = game()
        assertEquals(BUTCHER_STAGE_CUT, g.stage)
        assertEquals(0, g.score)
        assertEquals(0, g.pieceCount)
        assertEquals(1, g.carcassNumber)
        assertTrue(g.carcassCut.none { it })
        assertEquals(0f, g.difficulty, 0.001f)
    }

    @Test
    fun theLinesAreInsideTheCarcassAndInOrder() {
        for (seed in 0 until 30) {
            val g = game(seed)
            for (i in 0 until Butcher.CUTS_PER_CARCASS) {
                assertTrue(g.carcassLines[i] > Butcher.CARCASS_X0)
                assertTrue(g.carcassLines[i] < Butcher.CARCASS_X1)
                if (i > 0) assertTrue(g.carcassLines[i] - g.carcassLines[i - 1] > 40f)
            }
        }
    }

    @Test
    fun theKnifeMovesWhileAnArrowIsHeld() {
        val g = game()
        val start = g.knifeX
        g.press(BUTCHER_RIGHT)
        advance(g, 500f)
        assertTrue(g.knifeX > start + 100f)
        g.release(BUTCHER_RIGHT)
        val stopped = g.knifeX
        advance(g, 500f)
        assertEquals(stopped, g.knifeX, 0.001f)
    }

    @Test
    fun theKnifeStaysInsideItsLimits() {
        val g = game()
        g.press(BUTCHER_LEFT)
        advance(g, 10_000f)
        assertEquals(Butcher.KNIFE_MIN, g.knifeX, 0.001f)
    }

    @Test
    fun aCutOnALineGivesAPiece() {
        val g = game()
        assertTrue(aimAndCut(g))
        assertEquals(1, g.pieceCount)
        assertEquals(1, g.cuts)
        assertEquals(1, g.carcassCut.count { it })
        assertTrue(g.score > 0)
        val piece = checkNotNull(g.pieces[0])
        assertEquals(g.carcassMeat, piece.meat)
        assertEquals(Butcher.pieceWeightG(piece.meat), piece.weightG)
        assertEquals(1f, piece.fresh, 0.05f)
    }

    @Test
    fun aCutAwayFromEveryLineHitsTheBone() {
        val g = game()
        g.tick(Butcher.CARCASS_ARRIVE_MS + 10f)
        g.press(BUTCHER_LEFT)
        advance(g, 2_000f)
        g.release(BUTCHER_LEFT)
        assertEquals(Butcher.KNIFE_MIN, g.knifeX, 0.001f)
        g.press(BUTCHER_A)
        g.release(BUTCHER_A)
        assertEquals(1, g.boneHits)
        assertEquals(0, g.pieceCount)
        assertEquals(0, g.score) // le score ne descend pas sous zéro
        assertTrue(g.stuckMs > 0f)
        assertEquals(BUTCHER_BLOCK_WRONG, g.blockedKind)
    }

    @Test
    fun theKnifeCannotCutWhileACarcassArrives() {
        val g = game()
        g.press(BUTCHER_A)
        g.release(BUTCHER_A)
        assertEquals(0, g.cuts)
        assertEquals(0, g.boneHits)
    }

    @Test
    fun cuttingEveryCarcassEndsTheMorningEarlyWithABonus() {
        val g = game()
        cutEverything(g)
        assertEquals(BUTCHER_STAGE_OPENING, g.stage)
        assertEquals(Butcher.SLOTS, g.pieceCount)
        assertEquals(Butcher.CARCASSES, g.carcassesDone)
        assertEquals(Butcher.SLOTS, g.cuts)
        // Douze coupes à 10 points au moins, plus des secondes de reste à 2 points.
        assertTrue(g.score >= Butcher.SLOTS * Butcher.POINTS_CUT_GOOD + 2 * Butcher.EARLY_BONUS_PER_S)
    }

    @Test
    fun twoCarcassesInARowAreNeverTheSameMeat() {
        val g = game(5)
        var previous = g.carcassMeat
        var checked = 0
        var guard = 0
        while (g.stage == BUTCHER_STAGE_CUT && guard++ < 600 && checked < 2) {
            val number = g.carcassNumber
            if (!aimAndCut(g, autoClean = true)) g.tick(50f)
            if (g.carcassNumber != number) {
                assertTrue(g.carcassMeat != previous)
                previous = g.carcassMeat
                checked++
            }
        }
        assertEquals(2, checked)
    }

    @Test
    fun theMorningEndsAtTimeOutAndAnEmptyColdRoomMeansNoMeat() {
        val g = game()
        advance(g, Butcher.CUT_MS + 100f)
        assertTrue(g.over)
        assertEquals(BUTCHER_END_SOLDOUT, g.endReason)
    }

    // ---------------------------------------------------------------- l'hygiène

    @Test
    fun poultryDirtiesThePlanAndBCleansIt() {
        val g = gameWithFirstCarcass(BUTCHER_POULTRY)
        assertFalse(g.dirty)
        assertTrue(aimAndCut(g))
        assertTrue(g.dirty)
        g.press(BUTCHER_B)
        assertTrue(g.cleanMs > 0f)
        assertTrue(g.dirty) // pas encore propre : le nettoyage prend un instant
        g.tick(Butcher.CLEAN_MS + 10f)
        assertFalse(g.dirty)
        assertEquals(0f, g.cleanMs, 0.001f)
    }

    @Test
    fun cleaningACleanPlanDoesNothing() {
        val g = game()
        g.press(BUTCHER_B)
        assertEquals(0f, g.cleanMs, 0.001f)
    }

    @Test
    fun theKnifeCannotCutWhileThePlanIsBeingCleaned() {
        val g = gameWithFirstCarcass(BUTCHER_POULTRY)
        assertTrue(aimAndCut(g))
        g.press(BUTCHER_B)
        val cuts = g.cuts
        g.press(BUTCHER_A)
        g.release(BUTCHER_A)
        assertEquals(cuts, g.cuts)
        assertEquals(BUTCHER_BLOCK_BUSY, g.blockedKind)
    }

    @Test
    fun cuttingAnotherMeatWithADirtyPlanIsAHygieneFault() {
        val g = gameWithFirstCarcass(BUTCHER_POULTRY)
        // Les quatre coupes de la première carcasse (de la volaille), sans nettoyer.
        repeat(Butcher.CUTS_PER_CARCASS) { assertTrue(aimAndCut(g)) }
        assertEquals(1, g.carcassNumber)
        assertTrue(g.dirty)
        assertEquals(0, g.faults)
        // La carcasse suivante arrive (elle n'est jamais de la volaille : deux de suite ne sont pas la même viande).
        advance(g, Butcher.CARCASS_LEAVE_MS + Butcher.CARCASS_ARRIVE_MS + 100f)
        assertEquals(2, g.carcassNumber)
        assertTrue(g.dirty)
        val before = g.points
        assertTrue(aimAndCut(g))
        assertEquals(1, g.faults)
        assertFalse(g.dirty)
        assertTrue(g.points < before + Butcher.POINTS_CUT_PERFECT) // la faute coûte plus que la coupe ne rapporte
        assertTrue(g.faultMs > 0f)
    }

    // ---------------------------------------------------------------- les pièces

    @Test
    fun piecesLoseFreshnessAtTheRateOfTheirMeat() {
        val g = game()
        cutEverything(g)
        val before = g.pieces.map { it?.fresh }
        advance(g, 10_000f)
        var compared = 0
        for (i in 0 until Butcher.SLOTS) {
            val b = before[i] ?: continue
            val a = g.pieces[i] ?: continue
            assertEquals(b - Butcher.freshLossPerMs(a.meat) * 10_000f, a.fresh, 0.001f)
            compared++
        }
        assertTrue(compared > 0)
    }

    @Test
    fun aPieceThatIsNotSoldSpoilsAndCostsPoints() {
        val g = gameWithFirstCarcass(BUTCHER_POULTRY)
        assertTrue(aimAndCut(g))
        assertEquals(1, g.pieceCount)
        val before = g.points
        // La volaille tient 80 s : au bout de 82 s la pièce est jetée.
        advance(g, 82_000f)
        assertEquals(1, g.spoiled)
        assertEquals(0, g.pieceCount)
        assertTrue(g.points <= before - Butcher.PENALTY_SPOILED)
    }

    @Test
    fun freshnessLossIsFasterForPoultryThanForBeef() {
        assertTrue(Butcher.freshLossPerMs(BUTCHER_POULTRY) > Butcher.freshLossPerMs(BUTCHER_PORK))
        assertTrue(Butcher.freshLossPerMs(BUTCHER_PORK) > Butcher.freshLossPerMs(BUTCHER_BEEF))
        assertTrue(Butcher.freshLossPerMs(BUTCHER_LAMB) > Butcher.freshLossPerMs(BUTCHER_PORK))
    }

    // ---------------------------------------------------------------- le service

    @Test
    fun theServiceStartsWithAnOrderTheColdRoomCanFill() {
        for (seed in 1..8) {
            val g = serving(seed)
            assertEquals(BUTCHER_STAGE_SERVICE, g.stage)
            assertEquals(BUTCHER_STEP_PICK, g.step)
            assertTrue(g.customerMeat in 0 until Butcher.MEATS)
            assertTrue(g.slicesTotal in 1..Butcher.MAX_SLICES)
            assertEquals(g.slicesTotal, g.slicesLeft)
            assertTrue(g.sliceWantG in Butcher.MIN_SLICE_G..Butcher.MAX_SLICE_G)
            assertEquals(0, g.sliceWantG % Butcher.SLICE_STEP_G)
            val need = g.slicesTotal * g.sliceWantG
            assertTrue(g.pieces.any { it != null && it.meat == g.customerMeat && it.weightG >= need })
            assertNotNull(g.pieces[g.cursor]) // le choix démarre sur une pièce
        }
    }

    /** Un service avec seulement trois pièces dans la chambre froide (les neuf autres cases sont vides). */
    private fun servingWithThreePieces(): ButcherGame {
        val g = game()
        repeat(3) { assertTrue(aimAndCut(g, autoClean = true)) }
        var guard = 0
        while (g.stage != BUTCHER_STAGE_SERVICE && guard++ < 2_000) g.tick(50f)
        assertEquals(BUTCHER_STAGE_SERVICE, g.stage)
        g.tick(Butcher.ARRIVE_MS)
        assertEquals(3, g.pieceCount)
        return g
    }

    @Test
    fun theCursorSkipsEmptySlots() {
        val g = servingWithThreePieces()
        val seen = HashSet<Int>()
        repeat(6) {
            g.press(BUTCHER_RIGHT)
            g.release(BUTCHER_RIGHT)
            assertNotNull(g.pieces[g.cursor])
            seen.add(g.cursor)
        }
        assertEquals(3, seen.size)
        // Et dans l'autre sens.
        repeat(6) {
            g.press(BUTCHER_LEFT)
            g.release(BUTCHER_LEFT)
            assertNotNull(g.pieces[g.cursor])
        }
    }

    @Test
    fun aHeldArrowKeepsMovingTheCursorAndReleasingStopsIt() {
        val g = serving()
        g.press(BUTCHER_RIGHT)
        var moves = 0
        var last = g.cursor
        repeat(50) {
            g.tick(20f)
            if (g.cursor != last) {
                moves++
                last = g.cursor
            }
        }
        g.release(BUTCHER_RIGHT)
        assertTrue(moves >= 3)
        val stopped = g.cursor
        advance(g, 500f)
        assertEquals(stopped, g.cursor)
    }

    @Test
    fun theWrongMeatCostsPointsAndPatience() {
        val g = serving()
        val wrong = g.pieces.indices.first { g.pieces[it] != null && g.pieces[it]!!.meat != g.customerMeat }
        var guard = 0
        while (g.cursor != wrong && guard++ < Butcher.SLOTS) {
            g.press(BUTCHER_RIGHT)
            g.release(BUTCHER_RIGHT)
        }
        val patience = g.patienceMs
        g.press(BUTCHER_A)
        g.release(BUTCHER_A)
        assertEquals(1, g.wrongMeat)
        assertEquals(BUTCHER_STEP_PICK, g.step)
        assertTrue(g.patienceMs < patience)
        assertEquals(BUTCHER_BLOCK_WRONG, g.blockedKind)
    }

    /** La même partie dont la pièce de la case [slot] ne pèse plus que [weight] grammes (toutes les cases sont pleines : quatre nombres chacune, à la fin). */
    private fun withPieceWeight(g: ButcherGame, slot: Int, weight: Int): ButcherGame {
        assertEquals(Butcher.SLOTS, g.pieceCount)
        val values = g.save().toMutableList()
        values[values.size - Butcher.SLOTS * 4 + slot * 4 + 2] = weight.toDouble()
        return ButcherGame.restore(values)
    }

    @Test
    fun aPieceTooSmallForTheOrderIsRefused() {
        val base = serving()
        val need = base.slicesTotal * base.sliceWantG
        val slot = base.pieces.indices.first { i ->
            val p = base.pieces[i]
            p != null && p.meat == base.customerMeat
        }
        val g = withPieceWeight(base, slot, need - 1)
        assertEquals(need - 1, g.pieces[slot]!!.weightG)
        var guard = 0
        while (g.cursor != slot && guard++ < Butcher.SLOTS) {
            g.press(BUTCHER_RIGHT)
            g.release(BUTCHER_RIGHT)
        }
        assertEquals(slot, g.cursor)
        g.press(BUTCHER_A)
        g.release(BUTCHER_A)
        assertEquals(BUTCHER_STEP_PICK, g.step)
        assertEquals(BUTCHER_BLOCK_SMALL, g.blockedKind)
        assertEquals(0, g.wrongMeat)
    }

    @Test
    fun takingTheRightPieceStartsSlicing() {
        val g = serving()
        val need = g.slicesTotal * g.sliceWantG
        val slot = g.pieces.indices.first { i ->
            val p = g.pieces[i]
            p != null && p.meat == g.customerMeat && p.weightG >= need
        }
        if (g.dirty && g.customerMeat != BUTCHER_POULTRY) clean(g)
        var guard = 0
        while (g.cursor != slot && guard++ < Butcher.SLOTS) {
            g.press(BUTCHER_RIGHT)
            g.release(BUTCHER_RIGHT)
        }
        g.press(BUTCHER_A)
        g.release(BUTCHER_A)
        assertEquals(BUTCHER_STEP_SLICE, g.step)
        assertEquals(slot, g.pickedSlot)
        assertEquals(0f, g.sliceG, 0.001f)
    }

    @Test
    fun holdingAMovesTheMeatAndReleasingCutsTheSlice() {
        val g = serving()
        serveFirstSlice(g)
        assertTrue(g.sliceVerdict == BUTCHER_VERDICT_PERFECT || g.sliceVerdict == BUTCHER_VERDICT_GOOD)
    }

    /** Prend la bonne pièce puis coupe une seule tranche au bon poids. */
    private fun serveFirstSlice(g: ButcherGame) {
        val need = g.slicesTotal * g.sliceWantG
        val slot = g.pieces.indices.first { i ->
            val p = g.pieces[i]
            p != null && p.meat == g.customerMeat && p.weightG >= need
        }
        if (g.dirty && g.customerMeat != BUTCHER_POULTRY) clean(g)
        var guard = 0
        while (g.cursor != slot && guard++ < Butcher.SLOTS) {
            g.press(BUTCHER_RIGHT)
            g.release(BUTCHER_RIGHT)
        }
        g.press(BUTCHER_A)
        g.release(BUTCHER_A)
        val before = g.pieces[slot]!!.weightG
        val want = g.sliceWantG
        sliceTo(g, want)
        assertTrue(g.sliceVerdict == BUTCHER_VERDICT_PERFECT || g.sliceVerdict == BUTCHER_VERDICT_GOOD)
        // La tranche est enlevée de la pièce, au poids coupé.
        val after = g.pieces[slot]
        if (after != null) assertEquals(before - g.weighedG, after.weightG)
    }

    @Test
    fun aTapTooShortCutsNothing() {
        val g = serving()
        val need = g.slicesTotal * g.sliceWantG
        val slot = g.pieces.indices.first { i ->
            val p = g.pieces[i]
            p != null && p.meat == g.customerMeat && p.weightG >= need
        }
        if (g.dirty && g.customerMeat != BUTCHER_POULTRY) clean(g)
        while (g.cursor != slot) {
            g.press(BUTCHER_RIGHT)
            g.release(BUTCHER_RIGHT)
        }
        g.press(BUTCHER_A)
        g.release(BUTCHER_A)
        g.press(BUTCHER_A)
        g.tick(2f)
        g.release(BUTCHER_A)
        assertEquals(BUTCHER_STEP_SLICE, g.step)
        assertEquals(g.slicesTotal, g.slicesLeft)
    }

    @Test
    fun servingACustomerWellScoresAndCountsHimServed() {
        val g = serving()
        val before = g.points
        serve(g)
        assertEquals(BUTCHER_STEP_RESULT, g.step)
        assertEquals(1, g.served)
        assertEquals(1, g.streak)
        assertTrue(g.verdict == BUTCHER_VERDICT_PERFECT || g.verdict == BUTCHER_VERDICT_GOOD)
        assertTrue(g.points > before)
        assertEquals(g.points - before, g.lastPoints)
    }

    @Test
    fun slicingTooLongIsAMiss() {
        val g = serving()
        val need = g.slicesTotal * g.sliceWantG
        val slot = g.pieces.indices.first { i ->
            val p = g.pieces[i]
            p != null && p.meat == g.customerMeat && p.weightG >= need
        }
        if (g.dirty && g.customerMeat != BUTCHER_POULTRY) clean(g)
        while (g.cursor != slot) {
            g.press(BUTCHER_RIGHT)
            g.release(BUTCHER_RIGHT)
        }
        g.press(BUTCHER_A)
        g.release(BUTCHER_A)
        g.press(BUTCHER_A)
        var guard = 0
        // La trancheuse s'arrête toute seule à 600 g : la tranche est coupée et ratée.
        while (g.holdA && guard++ < 10_000) g.tick(5f)
        assertEquals(BUTCHER_VERDICT_MISS, g.sliceVerdict)
        assertEquals(Butcher.SLICE_MAX_G.toInt(), g.weighedG)
    }

    @Test
    fun aCustomerWhoWaitsTooLongLeaves() {
        val g = serving()
        val before = g.points
        advance(g, Butcher.PATIENCE_START_MS + 500f)
        assertEquals(1, g.walkouts)
        assertEquals(BUTCHER_VERDICT_LEFT, g.verdict)
        assertEquals(0, g.streak)
        assertEquals(before - Butcher.PENALTY_LEFT, g.points)
    }

    @Test
    fun everyOrderCanBeFilledFromTheColdRoom() {
        val g = serving()
        var customers = 0
        var guard = 0
        while (customers < 6 && guard++ < 50) {
            if (g.stage != BUTCHER_STAGE_SERVICE) break
            val need = g.slicesTotal * g.sliceWantG
            assertTrue(g.pieces.any { it != null && it.meat == g.customerMeat && it.weightG >= need })
            serve(g)
            customers++
            advance(g, Butcher.RESULT_MS + 100f)
            g.tick(Butcher.ARRIVE_MS)
        }
        // Le bonus (500 points) ne peut pas s'ouvrir avant le troisième client : au moins deux sont servis.
        assertTrue(customers >= 2)
    }

    @Test
    fun theDayEndsAtClosingTime() {
        val g = serving(2)
        advance(g, Butcher.SERVICE_MS + 5_000f)
        assertTrue(g.over)
        // La fermeture, ou plus de viande si les pièces les plus fragiles ont toutes été jetées avant l'heure.
        assertTrue(g.endReason == BUTCHER_END_CLOSING || g.endReason == BUTCHER_END_SOLDOUT)
    }

    @Test
    fun theGameCanBeRestartedAfterAShortDelay() {
        val g = game()
        advance(g, Butcher.CUT_MS + 100f)
        assertTrue(g.over)
        assertFalse(g.canRestart)
        advance(g, Butcher.RESTART_DELAY_MS + 100f)
        assertTrue(g.canRestart)
        g.reset()
        assertEquals(BUTCHER_STAGE_CUT, g.stage)
        assertEquals(0, g.score)
        assertEquals(0, g.pieceCount)
    }

    // ---------------------------------------------------------------- l'inspecteur

    @Test
    fun theInspectorCallsWithAWarningThenChecks() {
        val g = serving()
        var warned = false
        var guard = 0
        while (g.inspections == 0 && guard++ < 2_000) {
            g.tick(50f)
            if (g.inspectorState == BUTCHER_INSPECT_WARN) warned = true
            if (g.over) break
        }
        assertTrue(warned)
        assertTrue(g.inspections >= 1 || g.over)
        if (g.inspections >= 1) assertTrue(g.inspectResult == 1 || g.inspectResult == -1)
    }

    @Test
    fun aCleanPlanPassesTheInspectionAndEarnsPoints() {
        val g = serving(3)
        if (g.dirty) clean(g)
        // On laisse passer le temps jusqu'au premier contrôle, sans toucher à la viande, en gardant le plan propre.
        var guard = 0
        while (g.inspections == 0 && guard++ < 2_000 && !g.over) {
            g.tick(50f)
            if (g.dirty) clean(g)
        }
        assertEquals(1, g.inspections)
        assertEquals(1, g.inspectionsPassed)
        assertEquals(1, g.inspectResult)
        assertTrue(g.inspectShowMs > 0f)
        assertEquals(0, g.faults)
    }

    // ---------------------------------------------------------------- le bonus saucisses

    /** Un service avec [points] points, au moment où un client vient d'être servi : le prochain client est l'occasion du bonus. */
    private fun servedWithPoints(points: Int): ButcherGame {
        val base = serving()
        val g = withPoints(base, points)
        assertEquals(BUTCHER_STAGE_SERVICE, g.stage)
        serve(g)
        assertEquals(BUTCHER_STEP_RESULT, g.step)
        return g
    }

    @Test
    fun belowTheThresholdThereIsNoBonus() {
        val g = servedWithPoints(100)
        advance(g, Butcher.RESULT_MS + 100f)
        assertTrue(g.stage == BUTCHER_STAGE_SERVICE || g.over)
        assertFalse(g.bonusDone)
    }

    @Test
    fun reachingTheThresholdOpensTheSausageWorkshopBetweenTwoCustomers() {
        val g = servedWithPoints(Butcher.BONUS_SCORE + 50)
        advance(g, Butcher.RESULT_MS + 100f)
        assertEquals(BUTCHER_STAGE_BONUS, g.stage)
        assertTrue(g.bonusDone)
        assertEquals(Butcher.BONUS_MS, g.bonusLeftMs, 0.001f)
        // Le temps du service est suspendu pendant l'atelier.
        val frozen = g.stageLeftMs
        advance(g, Butcher.BONUS_INTRO_MS + 3_000f)
        assertEquals(frozen, g.stageLeftMs, 0.001f)
    }

    @Test
    fun noBonusWhenTheDayIsAboutToEnd() {
        val base = serving()
        val values = base.save().toMutableList()
        values[4] = (Butcher.BONUS_SCORE + 100).toDouble()
        values[2] = (Butcher.BONUS_MIN_LEFT_MS - 100f).toDouble() // stageLeftMs : le deuxième nombre de la sauvegarde
        val g = ButcherGame.restore(values)
        serve(g)
        advance(g, Butcher.RESULT_MS + 100f)
        assertFalse(g.bonusDone)
        assertTrue(g.stage != BUTCHER_STAGE_BONUS)
    }

    /** Un atelier de saucisses ouvert, intro passée. */
    private fun workshop(): ButcherGame {
        val g = servedWithPoints(Butcher.BONUS_SCORE + 50)
        advance(g, Butcher.RESULT_MS + 100f)
        assertEquals(BUTCHER_STAGE_BONUS, g.stage)
        advance(g, Butcher.BONUS_INTRO_MS + 50f)
        assertEquals(0f, g.bonusIntroMs, 0.001f)
        return g
    }

    @Test
    fun fillingTheCasingAndTyingAtTheRightLengthMakesASausage() {
        val g = workshop()
        val before = g.points
        g.press(BUTCHER_A)
        var guard = 0
        while (g.linkLen < g.linkTarget && guard++ < 2_000) g.tick(10f)
        g.press(BUTCHER_B)
        assertEquals(1, g.sausages)
        assertTrue(g.points >= before + Butcher.POINTS_LINK_GOOD)
        assertEquals(0f, g.linkLen, 0.001f)
        assertEquals(1, g.chainLen.size)
    }

    @Test
    fun tyingTooSoonIsABadSausageAndTooEarlyAnAccidentalTapIsIgnored() {
        val g = workshop()
        g.press(BUTCHER_B) // rien n'a coulé : ignoré
        assertEquals(0, g.chainLen.size)
        val before = g.points
        g.press(BUTCHER_A)
        advance(g, 200f, 10f)
        g.release(BUTCHER_A)
        assertTrue(g.linkLen >= Butcher.MIN_LINK_MM)
        g.press(BUTCHER_B)
        assertEquals(0, g.sausages)
        assertEquals(before - Butcher.PENALTY_LINK_BAD, g.points)
    }

    @Test
    fun fillingTooLongBurstsTheCasing() {
        val g = workshop()
        val before = g.points
        g.press(BUTCHER_A)
        var guard = 0
        while (g.bursts == 0 && guard++ < 1_000) g.tick(10f)
        assertEquals(1, g.bursts)
        assertEquals(before - Butcher.PENALTY_BURST, g.points)
        assertEquals(0f, g.linkLen, 0.001f)
        assertFalse(g.holdA) // il faut relâcher puis presser de nouveau
    }

    @Test
    fun successiveSausagesBuildACombo() {
        val g = workshop()
        val gains = ArrayList<Int>()
        repeat(3) {
            val before = g.points
            g.press(BUTCHER_A)
            var guard = 0
            while (g.linkLen < g.linkTarget && guard++ < 2_000) g.tick(10f)
            g.release(BUTCHER_A)
            g.press(BUTCHER_B)
            gains.add(g.points - before)
        }
        assertEquals(3, g.sausages)
        assertEquals(3, g.combo)
        assertTrue(gains[2] > gains[0]) // la série rapporte une prime
    }

    @Test
    fun theWorkshopLastsAFewSecondsThenServiceResumesWithANewCustomer() {
        val g = workshop()
        advance(g, Butcher.BONUS_MS + 500f)
        assertEquals(BUTCHER_STAGE_SERVICE, g.stage)
        assertTrue(g.bonusDone)
        assertEquals(BUTCHER_STEP_PICK, g.step)
        assertTrue(g.customerMeat >= 0)
    }

    @Test
    fun theWorkshopOpensOnlyOnce() {
        val g = workshop()
        advance(g, Butcher.BONUS_MS + 500f)
        assertEquals(BUTCHER_STAGE_SERVICE, g.stage)
        g.tick(Butcher.ARRIVE_MS)
        serve(g)
        advance(g, Butcher.RESULT_MS + 100f)
        assertTrue(g.stage != BUTCHER_STAGE_BONUS)
    }

    // ---------------------------------------------------------------- sauvegarde

    @Test
    fun aSavedGameComesBackIdentical() {
        val g = serving(4)
        serve(g)
        val back = ButcherGame.restore(g.save())
        assertEquals(g.stage, back.stage)
        assertEquals(g.points, back.points)
        assertEquals(g.pieceCount, back.pieceCount)
        assertEquals(g.cursor, back.cursor)
        assertEquals(g.served, back.served)
        assertEquals(g.step, back.step)
        assertEquals(g.customerMeat, back.customerMeat)
        assertEquals(g.dirty, back.dirty)
        for (i in 0 until Butcher.SLOTS) {
            val a = g.pieces[i]
            val b = back.pieces[i]
            assertEquals(a == null, b == null)
            if (a != null && b != null) {
                assertEquals(a.meat, b.meat)
                assertEquals(a.weightG, b.weightG)
                assertEquals(a.fresh, b.fresh, 0.0001f)
            }
        }
    }

    @Test
    fun aSavedMorningComesBackIdentical() {
        val g = game(7)
        aimAndCut(g)
        g.tick(1_000f)
        val back = ButcherGame.restore(g.save())
        assertEquals(g.carcassMeat, back.carcassMeat)
        assertEquals(g.carcassNumber, back.carcassNumber)
        for (i in 0 until Butcher.CUTS_PER_CARCASS) {
            assertEquals(g.carcassLines[i], back.carcassLines[i], 0.0001f)
            assertEquals(g.carcassCut[i], back.carcassCut[i])
        }
        assertEquals(g.knifeX, back.knifeX, 0.0001f)
        assertEquals(g.stageLeftMs, back.stageLeftMs, 0.001f)
    }

    @Test
    fun anUnusableSaveGivesAFreshGame() {
        val back = ButcherGame.restore(listOf(99.0, 1.0))
        assertEquals(BUTCHER_STAGE_CUT, back.stage)
        assertEquals(0, back.score)
        assertEquals(0, back.pieceCount)
        val empty = ButcherGame.restore(emptyList())
        assertEquals(BUTCHER_STAGE_CUT, empty.stage)
    }

    @Test
    fun releasingEverythingCancelsASliceInProgress() {
        val g = serving()
        val need = g.slicesTotal * g.sliceWantG
        val slot = g.pieces.indices.first { i ->
            val p = g.pieces[i]
            p != null && p.meat == g.customerMeat && p.weightG >= need
        }
        if (g.dirty && g.customerMeat != BUTCHER_POULTRY) clean(g)
        while (g.cursor != slot) {
            g.press(BUTCHER_RIGHT)
            g.release(BUTCHER_RIGHT)
        }
        g.press(BUTCHER_A)
        g.release(BUTCHER_A)
        g.press(BUTCHER_A)
        advance(g, 400f, 10f)
        assertTrue(g.sliceG > 0f)
        g.releaseAll()
        assertFalse(g.holdA)
        assertEquals(0f, g.sliceG, 0.001f)
        assertEquals(g.slicesTotal, g.slicesLeft)
        // Le relâchement qui arrive après coup ne coupe rien.
        g.release(BUTCHER_A)
        assertEquals(g.slicesTotal, g.slicesLeft)
    }
}
