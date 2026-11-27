package com.aventure.messcores

import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CashierTest {
    private fun game(seed: Int = 1) = CashierGame(Random(seed))

    /** Une partie dont le premier article, une fois le client arrivé, est un article à code-barres devant le scanner et vérifie [accept]. */
    private fun gameScanning(accept: (CashierItem) -> Boolean = { true }): CashierGame {
        for (seed in 1..3000) {
            val g = game(seed)
            g.tick(Cashier.ARRIVE_MS + 1f)
            val item = g.currentItem
            if (g.step == CASHIER_STEP_SCAN && item != null && accept(item)) return g
        }
        throw AssertionError("aucune partie ne commence par un tel article")
    }

    /** Fait une action juste pour l'article en cours (tourner et scanner, choisir la bonne image, vérifier l'âge). */
    private fun doItemRight(g: CashierGame) {
        val item = g.cart[g.index]
        when (g.step) {
            CASHIER_STEP_SCAN -> {
                while (g.facing != item.codeFace) g.press(CASHIER_RIGHT)
                g.press(CASHIER_A)
            }
            CASHIER_STEP_PICK -> {
                while (g.choices[g.pickCursor] != item.kind) g.press(CASHIER_RIGHT)
                g.press(CASHIER_A)
            }
            CASHIER_STEP_AGE -> g.press(CASHIER_B)
            else -> throw AssertionError("étape inattendue : ${g.step}")
        }
    }

    /** Passe tous les articles du premier client sans la moindre erreur : la partie arrive à l'étape du paiement. */
    private fun scanAll(g: CashierGame) {
        g.tick(Cashier.ARRIVE_MS + 1f)
        var guard = 0
        while (g.step != CASHIER_STEP_PAY && guard++ < 500) doItemRight(g)
        assertEquals(CASHIER_STEP_PAY, g.step)
    }

    /** Une partie arrivée au paiement par carte ([card] vrai) ou en espèces. */
    private fun gamePaying(card: Boolean, accept: (CashierGame) -> Boolean = { true }): CashierGame {
        for (seed in 1..3000) {
            val g = game(seed)
            scanAll(g)
            if (g.payCard == card && accept(g)) return g
        }
        throw AssertionError("aucune partie ne finit par ce paiement")
    }

    /** Paie sans erreur : le curseur de la carte est arrêté au milieu de la zone verte, la monnaie est rendue avec le moins de pièces. */
    private fun payRight(g: CashierGame) {
        var guard = 0
        if (g.payCard) {
            while (abs(g.termPos - g.termCenter) > g.termZoneW / 2f * Cashier.TERM_PERFECT && guard++ < 5000) g.tick(10f)
            g.press(CASHIER_A)
        } else {
            while (g.step == CASHIER_STEP_PAY && guard++ < 100) {
                val left = g.change - g.traySum
                val index = Cashier.DENOMS.indexOfLast { it <= left }
                while (g.denomCursor != index) g.press(CASHIER_RIGHT)
                g.press(CASHIER_A)
            }
        }
        assertEquals(CASHIER_STEP_RESULT, g.step)
    }

    @Test
    fun lePremierClientArriveAvecUnPetitChariot() {
        val g = game()
        assertEquals(CASHIER_STEP_ARRIVE, g.step)
        assertTrue(g.cart.size in 3..4)
        assertEquals(1, g.waiting)
        g.tick(Cashier.ARRIVE_MS + 1f)
        assertTrue(g.step == CASHIER_STEP_SCAN || g.step == CASHIER_STEP_PICK)
        assertEquals(0, g.index)
    }

    @Test
    fun lesArticlesOntUnCodeSurUneFaceEtUnPiegeAuPlusSurUneAutre() {
        for (seed in 1..60) {
            for (item in game(seed).cart) {
                if (Cashier.hasBarcode(item.kind)) {
                    assertTrue(item.codeFace in 1..3)
                    assertTrue(item.decoyFace == -1 || (item.decoyFace in 1..3 && item.decoyFace != item.codeFace))
                    // Un pack a toujours le code d'une bouteille en plus.
                    if (item.kind == Cashier.KIND_PACK) assertTrue(item.decoyFace >= 1)
                } else {
                    assertEquals(-1, item.codeFace)
                    assertEquals(-1, item.decoyFace)
                }
                assertTrue(item.price >= 1)
            }
        }
    }

    @Test
    fun lesFlechesTournentLArticleParQuartsDeTour() {
        val g = gameScanning()
        val start = g.facing
        g.press(CASHIER_RIGHT)
        assertEquals((start + 1) % 4, g.facing)
        g.press(CASHIER_LEFT)
        assertEquals(start, g.facing)
        repeat(4) { g.press(CASHIER_RIGHT) }
        assertEquals(start, g.facing)
        repeat(start + 1) { g.press(CASHIER_LEFT) }
        assertEquals(3, g.facing)
    }

    @Test
    fun scannerLaBonneFacePasseLArticleEtDonneDesPoints() {
        val g = gameScanning { it.kind != Cashier.KIND_WINE }
        val item = g.cart[0]
        while (g.facing != item.codeFace) g.press(CASHIER_RIGHT)
        g.press(CASHIER_A)
        assertEquals(1, g.index)
        assertEquals(1, g.itemsScanned)
        assertEquals(0, g.mistakes)
        assertEquals(item.price, g.total)
        assertEquals(CASHIER_POP_BEEP, g.popCode)
        assertTrue(g.score >= Cashier.POINTS_SCAN)
    }

    @Test
    fun uneEtiquettePiegeNeSeScannePasEtCoupeLaSerie() {
        val g = gameScanning { it.decoyFace >= 0 }
        val item = g.cart[0]
        while (g.facing != item.decoyFace) g.press(CASHIER_RIGHT)
        g.press(CASHIER_A)
        assertEquals(0, g.index)
        assertEquals(1, g.mistakes)
        assertEquals(CASHIER_POP_FAKE, g.popCode)
        assertEquals(-Cashier.PENALTY_FAKE, g.popPoints)
        assertEquals(CASHIER_STEP_SCAN, g.step)
        assertEquals(0, g.streak)
    }

    @Test
    fun scannerUneFaceSansCodeCouteUnPeuDePoints() {
        val g = gameScanning()
        val item = g.cart[0]
        while (g.facing == item.codeFace || g.facing == item.decoyFace) g.press(CASHIER_RIGHT)
        g.press(CASHIER_A)
        assertEquals(0, g.index)
        assertEquals(1, g.mistakes)
        assertEquals(CASHIER_POP_EMPTY, g.popCode)
        assertEquals(-Cashier.PENALTY_EMPTY, g.popPoints)
    }

    @Test
    fun lePrixDuScanPlusRapideRapporteLaPrimeDeRapidite() {
        var seed = 0
        for (candidate in 1..3000) {
            val g = game(candidate)
            g.tick(Cashier.ARRIVE_MS + 1f)
            if (g.step == CASHIER_STEP_SCAN && g.cart[0].kind != Cashier.KIND_WINE) {
                seed = candidate
                break
            }
        }
        assertTrue(seed > 0)
        fun gainAfter(waitMs: Float): Int {
            val g = game(seed)
            g.tick(Cashier.ARRIVE_MS + 1f)
            g.tick(waitMs)
            val item = g.cart[0]
            while (g.facing != item.codeFace) g.press(CASHIER_RIGHT)
            g.press(CASHIER_A)
            return g.popPoints
        }
        assertEquals(Cashier.SPEED_BONUS, gainAfter(0f) - gainAfter(5_000f))
    }

    @Test
    fun unArticleSansCodeSeChoisitParmiQuatreImages() {
        var g: CashierGame? = null
        for (seed in 1..3000) {
            val candidate = game(seed)
            candidate.tick(Cashier.ARRIVE_MS + 1f)
            if (candidate.step == CASHIER_STEP_PICK) {
                g = candidate
                break
            }
        }
        val current = g!!
        val item = current.cart[0]
        // Les quatre images sont différentes, et la bonne est parmi elles.
        assertEquals(Cashier.PICK_CHOICES, current.choices.toSet().size)
        assertTrue(item.kind in current.choices.toList())
        assertTrue(current.choices.all { it >= Cashier.FIRST_LOOSE })
        // Une mauvaise image coûte des points.
        while (current.choices[current.pickCursor] == item.kind) current.press(CASHIER_RIGHT)
        current.press(CASHIER_A)
        assertEquals(CASHIER_POP_WRONG, current.popCode)
        assertEquals(1, current.mistakes)
        assertEquals(0, current.index)
        // La bonne passe l'article.
        while (current.choices[current.pickCursor] != item.kind) current.press(CASHIER_RIGHT)
        current.press(CASHIER_A)
        assertEquals(1, current.index)
        assertEquals(1, current.itemsScanned)
    }

    @Test
    fun leVinDemandeDeVerifierLAgeAvecB() {
        var g: CashierGame? = null
        for (seed in 1..3000) {
            val candidate = game(seed)
            if (candidate.cart.any { it.kind == Cashier.KIND_WINE }) {
                candidate.tick(Cashier.ARRIVE_MS + 1f)
                var guard = 0
                while (candidate.step != CASHIER_STEP_AGE && candidate.step != CASHIER_STEP_PAY && guard++ < 50) doItemRight(candidate)
                if (candidate.step == CASHIER_STEP_AGE) {
                    g = candidate
                    break
                }
            }
        }
        val current = g!!
        val before = current.index
        // A n'est pas la bonne touche : rien ne se passe.
        current.press(CASHIER_A)
        assertEquals(CASHIER_STEP_AGE, current.step)
        assertEquals(before, current.index)
        // B vérifie l'âge et passe à la suite.
        current.press(CASHIER_B)
        assertEquals(CASHIER_POP_AGE_OK, current.popCode)
        assertEquals(before + 1, current.index)
        assertTrue(current.step != CASHIER_STEP_AGE)
    }

    @Test
    fun oublierLAgeCouteDesPointsEtDeLaPatience() {
        var g: CashierGame? = null
        for (seed in 1..3000) {
            val candidate = game(seed)
            if (candidate.cart.any { it.kind == Cashier.KIND_WINE }) {
                candidate.tick(Cashier.ARRIVE_MS + 1f)
                var guard = 0
                while (candidate.step != CASHIER_STEP_AGE && candidate.step != CASHIER_STEP_PAY && guard++ < 50) doItemRight(candidate)
                if (candidate.step == CASHIER_STEP_AGE) {
                    g = candidate
                    break
                }
            }
        }
        val current = g!!
        val patience = current.patienceMs
        current.tick(Cashier.AGE_MS + 1f)
        assertEquals(CASHIER_POP_AGE_MISSED, current.popCode)
        assertEquals(1, current.mistakes)
        assertTrue(current.patienceMs < patience - current.patienceMaxMs * Cashier.AGE_PATIENCE * 0.9f)
        assertTrue(current.step != CASHIER_STEP_AGE)
    }

    @Test
    fun uneNoteEtLaMonnaieSeCalculent() {
        assertEquals(10, Cashier.noteFor(7))
        assertEquals(20, Cashier.noteFor(10))
        assertEquals(50, Cashier.noteFor(49))
        assertEquals(251, Cashier.noteFor(250))
        assertEquals(0, Cashier.minCoins(0))
        assertEquals(5, Cashier.minCoins(38))
        assertEquals(5, Cashier.minCoins(49))
    }

    @Test
    fun uneCartePasseDansLaZoneVerte() {
        val g = gamePaying(card = true)
        val before = g.score
        payRight(g)
        assertEquals(1, g.customersServed)
        assertEquals(1, g.perfectPays)
        assertEquals(CASHIER_POP_PERFECT, g.popCode)
        assertTrue(g.score >= before + Cashier.POINTS_CUSTOMER + Cashier.POINTS_PERFECT_PAY)
    }

    @Test
    fun uneCarteHorsDeLaZoneEstRefuseEtLeCurseurRepart() {
        val g = gamePaying(card = true)
        var guard = 0
        while (abs(g.termPos - g.termCenter) <= g.termZoneW / 2f + 0.05f && guard++ < 5000) g.tick(10f)
        g.press(CASHIER_A)
        assertEquals(CASHIER_POP_DECLINED, g.popCode)
        assertEquals(CASHIER_STEP_PAY, g.step)
        assertEquals(1, g.mistakes)
        assertEquals(0, g.customersServed)
    }

    @Test
    fun enEspecesLaMonnaieExacteAvecLeMoinsDePiecesEstParfaite() {
        val g = gamePaying(card = false)
        assertEquals(Cashier.noteFor(g.total) - g.total, g.change)
        assertTrue(g.change >= 1)
        payRight(g)
        assertEquals(g.change, g.traySum)
        assertEquals(1, g.customersServed)
        assertEquals(1, g.perfectPays)
    }

    @Test
    fun rendreTropDeMonnaieEstRefuse() {
        val g = gamePaying(card = false) { it.change < 20 }
        while (g.denomCursor != Cashier.DENOMS.size - 1) g.press(CASHIER_RIGHT)
        g.press(CASHIER_A)
        assertEquals(CASHIER_POP_OVER, g.popCode)
        assertEquals(0, g.traySum)
        assertEquals(1, g.mistakes)
        assertEquals(CASHIER_STEP_PAY, g.step)
    }

    @Test
    fun bReprendLaDerniereMonnaieRendue() {
        val g = gamePaying(card = false) { it.change >= 3 }
        while (g.denomCursor != 0) g.press(CASHIER_LEFT)
        g.press(CASHIER_A)
        assertEquals(1, g.traySum)
        assertEquals(1, g.tray.size)
        g.press(CASHIER_B)
        assertEquals(0, g.traySum)
        assertTrue(g.tray.isEmpty())
        // Rien à reprendre : sans effet.
        g.press(CASHIER_B)
        assertEquals(0, g.traySum)
    }

    @Test
    fun unClientQuiAttendTropLongtempsSEnVa() {
        val g = game()
        g.tick(Cashier.ARRIVE_MS + 1f)
        var guard = 0
        while (g.walkouts == 0 && guard++ < 1000) g.tick(100f)
        assertEquals(1, g.walkouts)
        assertEquals(CASHIER_STEP_RESULT, g.step)
        assertFalse(g.resultHappy)
        assertEquals(CASHIER_POP_LEFT, g.popCode)
        assertEquals(-Cashier.PENALTY_LEFT, g.lastPoints)
        // Le score ne descend jamais sous zéro.
        assertEquals(0, g.score)
    }

    @Test
    fun laFileSAllongeEtLaDifficulteMonte() {
        val g = game()
        assertEquals(0f, g.difficulty, 0.001f)
        repeat(600) { g.tick(100f) }
        assertEquals(0.5f, g.difficulty, 0.01f)
        assertTrue(g.waiting > 1)
        assertTrue(g.waiting <= Cashier.MAX_WAITING)
    }

    @Test
    fun laPartieSeTermineAuBoutDeDeuxMinutesPuisSeRelance() {
        val g = game()
        var guard = 0
        while (!g.over && guard++ < 2000) g.tick(100f)
        assertTrue(g.over)
        assertEquals(0f, g.timeLeftMs, 0.001f)
        assertFalse(g.canRestart)
        // Plus aucune touche n'agit.
        val index = g.index
        g.press(CASHIER_A)
        assertEquals(index, g.index)
        g.tick(Cashier.RESTART_DELAY_MS + 1f)
        assertTrue(g.canRestart)
        g.reset()
        assertFalse(g.over)
        assertEquals(0, g.score)
        assertEquals(0, g.customersServed)
        assertEquals(Cashier.GAME_MS, g.timeLeftMs, 0.001f)
    }

    @Test
    fun uneSauvegardeRetrouveLaMemePartie() {
        val scanning = gameScanning()
        scanning.press(CASHIER_RIGHT)
        scanning.tick(250f)
        val savedScan = scanning.save()
        assertEquals(savedScan, CashierGame.restore(savedScan).save())

        val card = gamePaying(card = true)
        card.tick(333f)
        val savedCard = card.save()
        assertEquals(savedCard, CashierGame.restore(savedCard).save())

        val cash = gamePaying(card = false) { it.change >= 3 }
        cash.press(CASHIER_RIGHT)
        while (cash.denomCursor != 0) cash.press(CASHIER_LEFT)
        cash.press(CASHIER_A)
        val savedCash = cash.save()
        val restored = CashierGame.restore(savedCash)
        assertEquals(savedCash, restored.save())
        assertEquals(1, restored.traySum)
    }

    @Test
    fun uneSauvegardeInutilisableDonneUnePartieNeuve() {
        val g = CashierGame.restore(listOf(1.0, 2.0, 3.0))
        assertFalse(g.over)
        assertEquals(0, g.score)
        assertTrue(g.cart.isNotEmpty())
        val empty = CashierGame.restore(emptyList())
        assertEquals(CASHIER_STEP_ARRIVE, empty.step)
    }

    @Test
    fun unJoueurParfaitServeBeaucoupDeClientsSansPerdreDeClient() {
        val g = game(7)
        var guard = 0
        while (!g.over && guard++ < 100_000) {
            when (g.step) {
                CASHIER_STEP_ARRIVE, CASHIER_STEP_RESULT -> g.tick(100f)
                CASHIER_STEP_SCAN -> {
                    val item = g.cart[g.index]
                    while (g.facing != item.codeFace && !g.over) {
                        g.press(CASHIER_RIGHT)
                        g.tick(150f)
                    }
                    g.press(CASHIER_A)
                    g.tick(150f)
                }
                CASHIER_STEP_PICK -> {
                    val item = g.cart[g.index]
                    while (g.choices[g.pickCursor] != item.kind && !g.over) {
                        g.press(CASHIER_RIGHT)
                        g.tick(150f)
                    }
                    g.press(CASHIER_A)
                    g.tick(150f)
                }
                CASHIER_STEP_AGE -> {
                    g.press(CASHIER_B)
                    g.tick(100f)
                }
                else -> {
                    if (g.payCard) {
                        while (abs(g.termPos - g.termCenter) > g.termZoneW / 2f * Cashier.TERM_PERFECT && !g.over) g.tick(10f)
                        g.press(CASHIER_A)
                        g.tick(100f)
                    } else {
                        val left = g.change - g.traySum
                        val index = Cashier.DENOMS.indexOfLast { it <= left }
                        while (g.denomCursor != index && !g.over) {
                            g.press(CASHIER_RIGHT)
                            g.tick(150f)
                        }
                        g.press(CASHIER_A)
                        g.tick(200f)
                    }
                }
            }
        }
        assertTrue(g.over)
        assertTrue("clients servis : ${g.customersServed}", g.customersServed >= 8)
        assertTrue(g.perfectPays >= 1)
        assertEquals(0, g.walkouts)
        assertTrue(g.score > 0)
    }
}
