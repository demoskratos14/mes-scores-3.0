package com.aventure.messcores

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreshTest {
    /** Une partie en plein jeu (l'ouverture est passée), sans client, livraison, porte ni contrôle imminents. */
    private fun playing(seed: Int = 1): FreshGame {
        val g = FreshGame(Random(seed))
        g.tick(Fresh.OPENING_MS + 1f)
        assertEquals(FRESH_STAGE_PLAY, g.stage)
        return g
    }

    private fun pack(product: Int, lifeMs: Float, marked: Boolean = false) =
        FreshPack(product, lifeMs, Fresh.lifeMs(product), marked)

    /** Place le joueur devant la colonne [slot] (0 à 4) en partant du chariot. */
    private fun goTo(g: FreshGame, position: Int) {
        var guard = 0
        while (g.pos < position && guard++ < Fresh.POSITIONS) g.press(FRESH_RIGHT)
        while (g.pos > position && guard++ < 2 * Fresh.POSITIONS) g.press(FRESH_LEFT)
        assertEquals(position, g.pos)
    }

    /** Avance de [steps] pas de 100 ms en gardant le magasin calme : pas de client, de livraison utile ni de porte ouverte. */
    private fun waitCalmly(g: FreshGame, steps: Int, untilInspection: Boolean = false) {
        var done = 0
        while (done < steps && !g.over) {
            if (untilInspection && g.inspect == FRESH_INSPECT_WARN) return
            g.cart.clear()
            g.customers.clear()
            if (g.doorOpen) {
                goTo(g, Fresh.DOOR_POS)
                g.press(FRESH_A)
            }
            g.tick(100f)
            done++
        }
    }

    @Test
    fun laPartieCommencePleinePuisLOuvertureLaisseLaPlaceAuJeu() {
        val g = FreshGame(Random(1))
        assertEquals(FRESH_STAGE_OPENING, g.stage)
        assertEquals(Fresh.START_CART, g.cart.size)
        assertEquals(0, g.score)
        g.press(FRESH_A) // pendant l'ouverture, les touches ne font rien
        assertEquals(Fresh.START_CART, g.cart.size)
        g.tick(Fresh.OPENING_MS + 1f)
        assertEquals(FRESH_STAGE_PLAY, g.stage)
        assertEquals(Fresh.DAY_MS, g.stageLeftMs, 0.01f)
    }

    @Test
    fun auChariotOnPrendLePaquetDuDessusEtOnLeRemet() {
        val g = playing()
        val first = g.cart[0]
        g.press(FRESH_A)
        assertEquals(first, g.held)
        assertEquals(Fresh.START_CART - 1, g.cart.size)
        g.press(FRESH_B) // avec un paquet en main, au chariot : on le remet
        assertNull(g.held)
        assertEquals(first, g.cart[0])
    }

    @Test
    fun unPaquetDansLaMauvaiseColonneEstRefuseEtCoute() {
        val g = playing()
        g.press(FRESH_A)
        val held = checkNotNull(g.held)
        val wrong = (held.product + 1) % Fresh.PRODUCTS
        goTo(g, wrong + 1)
        g.press(FRESH_A)
        assertEquals(held, g.held)
        assertTrue(g.slots[wrong].isEmpty())
        assertEquals(FRESH_EVENT_WRONG, g.lastEvent)
        assertEquals(-Fresh.PENALTY_WRONG, g.points)
        assertEquals(1, g.wrongSlots)
    }

    @Test
    fun AEnfileAuFondEtBDevantAvecLeBonOrdreRecompense() {
        val g = playing()
        g.slots[0].add(pack(0, 20_000f))
        // Un paquet plus long à vivre : au fond, c'est dans l'ordre ; devant, c'est dans le désordre.
        g.cart.add(0, pack(0, 30_000f))
        g.press(FRESH_A)
        goTo(g, 1)
        g.press(FRESH_A)
        assertEquals(2, g.slots[0].size)
        assertEquals(30_000f, g.slots[0][1].lifeMs, 0.01f)
        assertEquals(FRESH_EVENT_STOCK_OK, g.lastEvent)
        assertEquals(Fresh.POINTS_STOCK, g.points)

        // Un paquet plus court à vivre : devant, c'est dans l'ordre.
        goTo(g, Fresh.CART_POS)
        g.cart.add(0, pack(0, 10_000f))
        g.press(FRESH_A)
        goTo(g, 1)
        g.press(FRESH_B)
        assertEquals(10_000f, g.slots[0][0].lifeMs, 0.01f)
        assertEquals(FRESH_EVENT_STOCK_OK, g.lastEvent)
        assertEquals(0, g.misordered)

        // La colonne est maintenant pleine (trois paquets) : un quatrième paquet ne se pose pas.
        goTo(g, Fresh.CART_POS)
        g.cart.clear()
        g.cart.add(pack(0, 40_000f))
        g.press(FRESH_A)
        goTo(g, 1)
        g.press(FRESH_B)
        assertEquals(Fresh.SLOT_CAP, g.slots[0].size)
        assertNotNull(g.held)
    }

    @Test
    fun retirerUnPaquetPerimeRapporteEtJeterUnBonPaquetCoute() {
        val g = playing()
        g.slots[2].add(pack(2, 0f))
        goTo(g, 3)
        g.press(FRESH_A)
        assertTrue(g.slots[2].isEmpty())
        assertEquals(Fresh.POINTS_REMOVE, g.points)
        assertEquals(1, g.removedExpired)
        assertEquals(FRESH_EVENT_REMOVED, g.lastEvent)

        // Un paquet neuf jeté : la perte est la plus forte.
        g.slots[2].add(pack(2, Fresh.lifeMs(2)))
        val before = g.points
        g.press(FRESH_A)
        assertEquals(before - (Fresh.WASTE_BASE + Fresh.WASTE_SPAN), g.points)
        assertEquals(1, g.wasted)
        assertEquals(0, g.streak)
    }

    @Test
    fun leCrochetVisePerimeDAbordPuisLePremierAExpirer() {
        val g = playing()
        val a = pack(1, 20_000f)
        val b = pack(1, 5_000f)
        val c = pack(1, 0f)
        g.slots[1].addAll(listOf(a, b, c))
        assertEquals(c, g.target(1))
        g.slots[1].remove(c)
        assertEquals(b, g.target(1))
        b.marked = true
        assertEquals(a, g.target(1)) // un paquet démarqué passe après ceux qui ne le sont pas
        assertNull(g.target(0))
    }

    @Test
    fun demarquerNeSePeutQueSurUnPaquetBonEtNonDejaDemarque() {
        val g = playing()
        val fresh = pack(3, 10_000f)
        g.slots[3].add(fresh)
        goTo(g, 4)
        g.press(FRESH_B)
        assertTrue(fresh.marked)
        assertEquals(1, g.markdowns)
        val after = g.points
        g.press(FRESH_B) // déjà démarqué : refusé, sans points
        assertEquals(after, g.points)
        assertTrue(g.blockedMs > 0f)

        g.slots[3].clear()
        g.slots[3].add(pack(3, 0f))
        g.press(FRESH_B) // périmé : on ne démarque pas
        assertFalse(g.slots[3][0].marked)
    }

    @Test
    fun lesPaquetsVieillissentEtLaPorteOuverteAccelereLeTemps() {
        val g = playing()
        g.slots[0].add(pack(0, 30_000f))
        g.tick(1_000f)
        assertEquals(29_000f, g.slots[0][0].lifeMs, 1f)
        // On attend que la porte s'ouvre (à l'heure prévue), sans la fermer.
        var guard = 0
        while (!g.doorOpen && guard++ < 600) {
            g.slots[0].clear()
            g.cart.clear()
            g.customers.clear()
            g.tick(100f)
        }
        assertTrue(g.doorOpen)
        g.slots[0].clear()
        g.slots[0].add(pack(0, 30_000f))
        g.customers.clear()
        g.tick(100f)
        assertEquals(30_000f - 100f * Fresh.DOOR_AGE_MULT, g.slots[0][0].lifeMs, 1f)
    }

    @Test
    fun fermerLaPorteRapporteEtRienNArriveSiElleEstFermee() {
        val g = playing()
        goTo(g, Fresh.DOOR_POS)
        g.press(FRESH_A)
        assertEquals(0, g.points)
        assertEquals(0, g.doorsClosed)
        var guard = 0
        while (!g.doorOpen && guard++ < 600) {
            g.cart.clear()
            g.customers.clear()
            g.tick(100f)
        }
        assertTrue(g.doorOpen)
        g.press(FRESH_B)
        assertFalse(g.doorOpen)
        assertEquals(1, g.doorsClosed)
        assertEquals(Fresh.POINTS_DOOR, g.points)
    }

    @Test
    fun uneVenteRapporteEtLeClientPrendLePaquetDemarqueEnPriorite() {
        val g = playing()
        val front = pack(0, 30_000f)
        val back = pack(0, 25_000f, marked = true)
        g.slots[0].addAll(listOf(front, back))
        g.customers.add(FreshCustomer(0, 0, true, Fresh.WALK_MS - 1f))
        g.tick(5f)
        assertEquals(listOf(front), g.slots[0].toList())
        assertEquals(1, g.sold)
        assertEquals(Fresh.POINTS_SALE_MARKED, g.points)
        assertEquals(FRESH_EVENT_SALE_MARKED, g.lastEvent)
    }

    @Test
    fun unClientQuiPrendUnPaquetPerimeEstUneFauteGrave() {
        val g = playing()
        g.slots[1].add(pack(1, 0f))
        g.customers.add(FreshCustomer(1, 0, true, Fresh.WALK_MS - 1f))
        g.tick(5f)
        assertEquals(1, g.errors)
        assertEquals(1, g.sickCustomers)
        assertEquals(FRESH_EVENT_SICK, g.lastEvent)
        assertEquals(-Fresh.PENALTY_SICK, g.points)
        assertEquals(0, g.score) // le score ne descend jamais sous zéro
    }

    @Test
    fun uneColonneVideFaitPartirLeClientSansFaute() {
        val g = playing()
        g.customers.add(FreshCustomer(4, 0, false, Fresh.WALK_MS - 1f))
        g.tick(5f)
        assertEquals(1, g.stockouts)
        assertEquals(0, g.errors)
        assertEquals(FRESH_EVENT_STOCKOUT, g.lastEvent)
    }

    @Test
    fun leControleurTrouvantUnPerimeEstUneFauteEtUnMeubleEnOrdreUnePrime() {
        val g = playing()
        // Premier contrôle : un paquet périmé dans le meuble.
        waitCalmly(g, 2_000, untilInspection = true)
        assertEquals(FRESH_INSPECT_WARN, g.inspect)
        g.slots[2].add(pack(2, 0f))
        waitCalmly(g, 71)
        assertEquals(FRESH_INSPECT_VERDICT, g.inspect)
        assertFalse(g.inspectPass)
        assertEquals(1, g.errors)
        assertEquals(1, g.inspectFails)
        assertEquals(FRESH_EVENT_INSPECT_KO, g.lastEvent)

        // Le verdict passé, on retire le périmé : le contrôle suivant trouve un meuble sain et donne une prime.
        waitCalmly(g, 25)
        assertEquals(FRESH_INSPECT_NONE, g.inspect)
        g.slots[2].clear()
        waitCalmly(g, 2_000, untilInspection = true)
        assertEquals(FRESH_INSPECT_WARN, g.inspect)
        waitCalmly(g, 71)
        assertEquals(FRESH_INSPECT_VERDICT, g.inspect)
        assertTrue(g.inspectPass)
        assertEquals(1, g.inspectOk)
        assertEquals(1, g.errors)
    }

    @Test
    fun troisFautesGravesLicencientLeJoueur() {
        val g = playing()
        repeat(Fresh.MAX_ERRORS) {
            g.slots[0].clear()
            g.slots[0].add(pack(0, 0f))
            g.customers.add(FreshCustomer(0, 0, true, Fresh.WALK_MS - 1f))
            g.tick(5f)
        }
        assertTrue(g.over)
        assertEquals(FRESH_END_FIRED, g.endReason)
        assertFalse(g.canRestart)
        g.tick(Fresh.RESTART_DELAY_MS + 1f)
        assertTrue(g.canRestart)
    }

    @Test
    fun laJourneeSeTermineQuandLeTempsEstEcoule() {
        val g = playing()
        // Un meuble toujours sain, une porte aussitôt fermée : la journée va jusqu'au bout.
        var guard = 0
        while (!g.over && guard++ < 10_000) {
            for (queue in g.slots) queue.clear()
            waitCalmly(g, 1)
        }
        assertTrue(g.over)
        assertEquals(FRESH_END_TIME, g.endReason)
        assertEquals(0, g.stageLeftMs.toInt())
    }

    @Test
    fun laSauvegardeRetrouveLEtatExact() {
        val g = playing(7)
        g.tick(12_000f)
        g.press(FRESH_A)
        g.press(FRESH_RIGHT)
        g.slots[1].add(pack(1, 12_345f, marked = true))
        g.customers.add(FreshCustomer(2, 1, false, 400f))
        val copy = FreshGame.restore(g.save())
        assertEquals(g.save(), copy.save())
        assertEquals(g.pos, copy.pos)
        assertEquals(g.held?.product, copy.held?.product)
        assertEquals(g.slots[1][0].marked, copy.slots[1][0].marked)
        assertEquals(g.customers.size, copy.customers.size)
    }

    @Test
    fun uneSauvegardeInvalideDonneUnePartieNeuve() {
        val copy = FreshGame.restore(listOf(99.0, 1.0))
        assertEquals(FRESH_STAGE_OPENING, copy.stage)
        assertEquals(Fresh.START_CART, copy.cart.size)
    }

    @Test
    fun laDifficulteMonteAvecLaJournee() {
        val g = playing()
        assertEquals(0f, g.difficulty, 0.01f)
        g.tick(Fresh.DAY_MS / 2f)
        assertTrue(g.difficulty > 0.4f)
    }
}
