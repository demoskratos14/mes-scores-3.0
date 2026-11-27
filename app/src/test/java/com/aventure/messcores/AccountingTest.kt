package com.aventure.messcores

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountingTest {
    private fun game(seed: Int = 1) = AccountingGame(Random(seed))

    /** Répond juste au client en cours : les bons signes, ou vrai / faux selon la facture. */
    private fun solve(g: AccountingGame) {
        if (g.kind == ACC_KIND_CHECK) {
            g.press(if (g.invoice().isTrue) ACC_A else ACC_B)
        } else {
            for (op in g.solution.copyOf()) g.press(op)
        }
    }

    /** Une touche qui n'est pas la bonne pour le premier signe attendu. */
    private fun wrongSign(g: AccountingGame): Int = (0..3).first { it != g.solution[g.found] }

    /** Fait avancer la partie de [ms] millisecondes, par pas de 50. */
    private fun play(g: AccountingGame, ms: Float) {
        var left = ms
        while (left > 0f) {
            val dt = minOf(50f, left)
            g.tick(dt)
            left -= dt
        }
    }

    /** Joue des clients, tous bien servis, jusqu'à en trouver un du genre [kind] à qui il reste à répondre. */
    private fun untilKind(g: AccountingGame, kind: Int) {
        var guard = 0
        while (!(g.asking && g.kind == kind) && guard++ < 500) {
            if (g.asking) solve(g)
            g.tick(100f)
        }
        assertTrue(g.asking && g.kind == kind)
    }

    // ---------------------------------------------------------------- les calculs

    @Test
    fun lesPrioritesSontCellesDeLArithmetique() {
        assertEquals(14, AccountingMath.evaluate(intArrayOf(2, 3, 4), intArrayOf(ACC_OP_ADD, ACC_OP_MUL)))
        assertEquals(10, AccountingMath.evaluate(intArrayOf(2, 3, 4), intArrayOf(ACC_OP_MUL, ACC_OP_ADD)))
        assertEquals(4, AccountingMath.evaluate(intArrayOf(10, 2, 3), intArrayOf(ACC_OP_SUB, ACC_OP_MUL)))
        assertEquals(12, AccountingMath.evaluate(intArrayOf(8, 2, 3), intArrayOf(ACC_OP_DIV, ACC_OP_MUL)))
        assertEquals(7, AccountingMath.evaluate(intArrayOf(1, 12, 2), intArrayOf(ACC_OP_ADD, ACC_OP_DIV)))
    }

    @Test
    fun uneOperationInvalideEstRefusee() {
        assertNull(AccountingMath.apply(7, ACC_OP_DIV, 2))
        assertNull(AccountingMath.apply(7, ACC_OP_DIV, 0))
        assertNull(AccountingMath.apply(3, ACC_OP_SUB, 5))
        assertEquals(0, AccountingMath.apply(5, ACC_OP_SUB, 5))
        assertNull(AccountingMath.evaluate(intArrayOf(2, 3, 4), intArrayOf(ACC_OP_DIV, ACC_OP_ADD)))
    }

    @Test
    fun lesTouchesPortentLeNumeroDeLeurSigne() {
        assertEquals(ACC_UP, ACC_OP_ADD)
        assertEquals(ACC_DOWN, ACC_OP_SUB)
        assertEquals(ACC_LEFT, ACC_OP_DIV)
        assertEquals(ACC_RIGHT, ACC_OP_MUL)
    }

    @Test
    fun chaqueOperationATrouerAUneSeuleReponse() {
        val random = Random(7)
        for (step in 0..10) {
            val d = step / 10f
            repeat(400) {
                val e = Accounting.sign(random, d)
                val all = AccountingMath.solutions(e.numbers, e.result)
                assertEquals(1, all.size)
                assertEquals(e.solution[0], all[0][0])
                assertEquals(2, e.numbers.size)
                assertTrue(e.numbers.all { it > 0 })
            }
        }
    }

    @Test
    fun chaqueChaineAUneSeuleReponseEtUtiliseUnePriorite() {
        val random = Random(11)
        for (step in 0..10) {
            val d = step / 10f
            repeat(400) {
                val e = Accounting.chain(random, d)
                val all = AccountingMath.solutions(e.numbers, e.result)
                assertEquals(1, all.size)
                assertEquals(e.solution[0], all[0][0])
                assertEquals(e.solution[1], all[0][1])
                assertEquals(3, e.numbers.size)
                assertTrue(e.solution.any { it == ACC_OP_MUL || it == ACC_OP_DIV })
                assertTrue(e.result in 0..200)
            }
        }
    }

    @Test
    fun lesOperationsDeSecoursOntAussiUneSeuleReponse() {
        val sign = AccountingMath.solutions(intArrayOf(7, 5), 12)
        assertEquals(1, sign.size)
        val chain = AccountingMath.solutions(intArrayOf(2, 3, 4), 14)
        assertEquals(1, chain.size)
        assertEquals(ACC_OP_ADD, chain[0][0])
        assertEquals(ACC_OP_MUL, chain[0][1])
    }

    @Test
    fun lesFacturesSontJusteOuFausseUneFoisSurDeux() {
        val random = Random(3)
        var right = 0
        val total = 2000
        repeat(total) { i ->
            val inv = Accounting.invoice(random, (i % 11) / 10f)
            assertTrue(inv.lines.size in 2..4)
            assertTrue(inv.lines.all { it >= 5 })
            assertTrue(inv.shownTotal > 0)
            if (inv.isTrue) {
                right++
                assertEquals(inv.lines.sum(), inv.shownTotal)
            } else {
                assertTrue(inv.shownTotal != inv.lines.sum())
            }
        }
        assertTrue("environ la moitié de factures justes : $right", right in total * 40 / 100..total * 60 / 100)
    }

    // ---------------------------------------------------------------- la partie

    @Test
    fun laPartieCommenceParUneOperationADeuxNombres() {
        val g = game()
        assertEquals(ACC_STAGE_MAIN, g.stage)
        assertEquals(ACC_KIND_SIGN, g.kind)
        assertEquals(2, g.numbers.size)
        assertTrue(g.asking)
        assertEquals(0, g.score)
        assertEquals(Accounting.MAIN_MS, g.stageLeftMs, 0.01f)
    }

    @Test
    fun leBonSigneRapporteDesPointsEtLeClientPart() {
        val g = game()
        g.press(g.solution[0])
        assertEquals(ACC_VERDICT_RIGHT, g.verdict)
        assertEquals(ACC_STEP_RESULT, g.step)
        assertEquals(1, g.solved)
        // Réponse immédiate : points de base, prime de rapidité presque entière, pas encore de série.
        assertTrue(g.score >= Accounting.POINTS_SIGN + Accounting.SPEED_BONUS - 1)
        assertEquals(g.score, g.lastPoints)
        play(g, Accounting.RESULT_MS + 10f)
        assertTrue(g.asking)
        assertEquals(0, g.found)
    }

    @Test
    fun unMauvaisSigneCoutePointsEtPatienceMaisLeClientReste() {
        val g = game()
        val before = g.patienceMs
        g.press(wrongSign(g))
        assertEquals(-Accounting.PENALTY_WRONG_SIGN, g.mainPoints)
        assertEquals(0, g.score) // jamais sous zéro
        assertEquals(1, g.mistakes)
        assertTrue(g.asking)
        assertTrue(g.blockedMs > 0f)
        assertEquals(before * (1f - Accounting.WRONG_PATIENCE), g.patienceMs, 0.5f)
        g.press(g.solution[0])
        assertEquals(ACC_VERDICT_RIGHT, g.verdict)
        assertEquals(1, g.solved)
    }

    @Test
    fun lesBoutonsAEtBNeServentPasPourUneOperation() {
        val g = game()
        assertEquals(ACC_KIND_SIGN, g.kind)
        g.press(ACC_A)
        g.press(ACC_B)
        assertEquals(0, g.mainPoints)
        assertEquals(0, g.mistakes)
        assertEquals(ACC_STEP_ASK, g.step)
    }

    @Test
    fun lesFlechesNeServentPasPourUneFacture() {
        val g = game(5)
        untilKind(g, ACC_KIND_CHECK)
        val points = g.mainPoints
        for (key in ACC_UP..ACC_RIGHT) g.press(key)
        assertEquals(points, g.mainPoints)
        assertEquals(ACC_STEP_ASK, g.step)
    }

    @Test
    fun uneFactureSeValideAvecAPourVraiEtBPourFaux() {
        val g = game(5)
        untilKind(g, ACC_KIND_CHECK)
        val saysTrue = g.invoice().isTrue
        val before = g.mainPoints
        g.press(if (saysTrue) ACC_A else ACC_B)
        assertEquals(ACC_VERDICT_RIGHT, g.verdict)
        assertTrue(g.mainPoints >= before + Accounting.POINTS_CHECK)
        assertEquals(1, g.checked)
    }

    @Test
    fun uneMauvaiseReponseSurUneFactureLaTermineAvecUnePenalite() {
        val g = game(5)
        untilKind(g, ACC_KIND_CHECK)
        val before = g.mainPoints
        g.press(if (g.invoice().isTrue) ACC_B else ACC_A)
        assertEquals(ACC_VERDICT_WRONG, g.verdict)
        assertEquals(ACC_STEP_RESULT, g.step)
        assertEquals(before - Accounting.PENALTY_WRONG_CHECK, g.mainPoints)
        assertEquals(0, g.streak)
        assertEquals(1, g.mistakes)
        // Une fois le verdict passé, le client suivant arrive.
        play(g, Accounting.RESULT_MS + 10f)
        assertTrue(g.asking)
    }

    @Test
    fun unClientQuiAttendTropLongtempsPartEtFaitPerdreDesPoints() {
        val g = game()
        val wait = g.patienceMs
        play(g, wait + 10f)
        assertEquals(ACC_VERDICT_LEFT, g.verdict)
        assertEquals(1, g.walkouts)
        assertEquals(-Accounting.PENALTY_LEFT, g.mainPoints)
        assertEquals(0, g.streak)
        // Il ne peut plus répondre une fois parti.
        g.press(g.solution[0])
        assertEquals(0, g.solved)
    }

    @Test
    fun uneSerieDeBonnesReponsesRapporteDesPrimes() {
        val g = game()
        g.press(g.solution[0])
        val first = g.lastPoints
        play(g, Accounting.RESULT_MS + 10f)
        untilKind(g, ACC_KIND_SIGN)
        g.press(g.solution[0])
        assertEquals(2, g.streak)
        assertTrue(g.lastPoints > first - Accounting.SPEED_BONUS + Accounting.STREAK_BONUS - 2)
        // Une erreur casse la série.
        play(g, Accounting.RESULT_MS + 10f)
        untilKind(g, ACC_KIND_SIGN)
        g.press(wrongSign(g))
        assertEquals(0, g.streak)
        assertTrue(g.bestStreak >= 2)
    }

    @Test
    fun laPrimeDeSerieEstPlafonnee() {
        val g = game(2)
        var guard = 0
        while (g.streak < 10 && guard++ < 400) {
            if (g.asking) solve(g)
            g.tick(100f)
            if (g.stage != ACC_STAGE_MAIN) break
        }
        assertTrue(g.streak >= 7)
        // Pour une opération servie au plus vite : base + rapidité + prime plafonnée.
        untilKind(g, ACC_KIND_SIGN)
        g.press(g.solution[0])
        val most = Accounting.POINTS_SIGN + Accounting.SPEED_BONUS + Accounting.STREAK_BONUS_CAP
        assertTrue(g.lastPoints <= most)
    }

    @Test
    fun sansPointsSuffisantsLaPartieSArreteSansTempsBonus() {
        val g = game()
        play(g, Accounting.MAIN_MS + 10f)
        assertTrue(g.over)
        assertFalse(g.bonusGranted)
        assertEquals(0, g.score)
    }

    @Test
    fun avecAssezDePointsUnTempsBonusEnChaineEstAccorde() {
        val g = game(4)
        var guard = 0
        while (g.stage == ACC_STAGE_MAIN && guard++ < 2000) {
            if (g.asking) solve(g)
            g.tick(100f)
        }
        assertEquals(ACC_STAGE_BONUS_INTRO, g.stage)
        assertTrue(g.bonusGranted)
        assertTrue(g.score >= Accounting.BONUS_SCORE)
        val atTheEnd = g.score
        // Pendant l'annonce, rien ne se joue.
        g.press(ACC_UP)
        assertEquals(atTheEnd, g.score)
        play(g, Accounting.BONUS_INTRO_MS + 10f)
        assertEquals(ACC_STAGE_BONUS, g.stage)
        assertEquals(ACC_KIND_CHAIN, g.kind)
        assertEquals(3, g.numbers.size)
        assertEquals(2, g.solution.size)
        assertEquals(Accounting.BONUS_MS, g.stageLeftMs, 60f)
        // Une chaîne demande deux signes de suite.
        g.press(g.solution[0])
        assertEquals(1, g.found)
        assertEquals(ACC_STEP_ASK, g.step)
        assertEquals(0, g.chains)
        g.press(g.solution[1])
        assertEquals(1, g.chains)
        assertEquals(ACC_VERDICT_RIGHT, g.verdict)
        assertTrue(g.bonusPoints >= Accounting.POINTS_CHAIN)
        // Le temps bonus passé, c'est fini ; le score additionne les deux temps.
        guard = 0
        while (g.stage == ACC_STAGE_BONUS && guard++ < 2000) {
            if (g.asking) solve(g)
            g.tick(100f)
        }
        assertTrue(g.over)
        assertEquals(g.mainPoints + g.bonusPoints, g.score)
        assertTrue(g.chains > 1)
    }

    @Test
    fun uneErreurSurLeDeuxiemeSigneNeFaitPasPerdreLePremier() {
        val g = game(4)
        var guard = 0
        while (g.stage != ACC_STAGE_BONUS && guard++ < 4000) {
            if (g.asking) solve(g)
            g.tick(100f)
        }
        assertEquals(ACC_STAGE_BONUS, g.stage)
        val before = g.bonusPoints
        g.press(g.solution[0])
        assertEquals(1, g.found)
        g.press(wrongSign(g))
        assertEquals(1, g.found)
        assertEquals(before - Accounting.PENALTY_WRONG_SIGN, g.bonusPoints)
        assertEquals(ACC_STEP_ASK, g.step)
        g.press(g.solution[1])
        assertEquals(ACC_VERDICT_RIGHT, g.verdict)
    }

    @Test
    fun lesOperationsGrossissentAvecLeTemps() {
        val g = game(9)
        assertTrue(g.difficulty < 0.01f)
        play(g, Accounting.MAIN_MS / 2f)
        assertEquals(0.5f, g.difficulty, 0.02f)
    }

    @Test
    fun onPeutRejouerApresLeMessageDeFin() {
        val g = game()
        play(g, Accounting.MAIN_MS + 10f)
        assertTrue(g.over)
        assertFalse(g.canRestart)
        play(g, Accounting.RESTART_DELAY_MS + 50f)
        assertTrue(g.canRestart)
        g.reset()
        assertEquals(ACC_STAGE_MAIN, g.stage)
        assertEquals(0, g.score)
        assertTrue(g.asking)
    }

    @Test
    fun laSauvegardeRetrouveLaMemePartie() {
        val g = game(6)
        play(g, 2000f)
        untilKind(g, ACC_KIND_CHECK)
        g.press(if (g.invoice().isTrue) ACC_B else ACC_A)
        play(g, 300f)
        val saved = g.save()
        val restored = AccountingGame.restore(saved)
        assertEquals(saved, restored.save())
        assertEquals(g.score, restored.score)
        assertEquals(g.kind, restored.kind)
        assertEquals(g.shownTotal, restored.shownTotal)
        assertEquals(g.invoice().isTrue, restored.invoice().isTrue)
    }

    @Test
    fun laSauvegardeEnTempsBonusReprendEnChaine() {
        val g = game(4)
        var guard = 0
        while (g.stage != ACC_STAGE_BONUS && guard++ < 4000) {
            if (g.asking) solve(g)
            g.tick(100f)
        }
        g.press(g.solution[0])
        val restored = AccountingGame.restore(g.save())
        assertEquals(ACC_STAGE_BONUS, restored.stage)
        assertEquals(ACC_KIND_CHAIN, restored.kind)
        assertEquals(1, restored.found)
        restored.press(restored.solution[1])
        assertEquals(ACC_VERDICT_RIGHT, restored.verdict)
    }

    @Test
    fun uneSauvegardeInvalideDonneUnePartieNeuve() {
        val fresh = AccountingGame.restore(listOf(1.0, 2.0, 3.0))
        assertEquals(ACC_STAGE_MAIN, fresh.stage)
        assertEquals(0, fresh.score)
        assertTrue(fresh.asking)
        assertNotNull(fresh.solution)
        val empty = AccountingGame.restore(emptyList())
        assertTrue(empty.asking)
    }
}
