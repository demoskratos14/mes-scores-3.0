package com.aventure.messcores

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PokerReseatTest {

    private val settings = PokerSettings(
        startingStack = 5000, smallBlind = 50, bigBlind = 100, levelMinutes = 10,
        anteEnabled = false, anteAfterIncreases = 0, firstDealer = 0
    )

    /** Partie à [count] joueurs, main terminée (tout le monde se couche devant le dernier joueur). */
    private fun handOver(count: Int, dealer: Int = 0): PokerGame {
        var g = PokerEngine.newGame(settings.copy(firstDealer = dealer), count)
        while (g.phase == PokerPhase.BETTING) g = PokerEngine.fold(g)
        assertEquals(PokerPhase.HAND_OVER, g.phase)
        return g
    }

    @Test
    fun lesArrivantsNeChangentNiLOrdreDesPresentsNiLeBouton() {
        repeat(200) { seed ->
            val g = handOver(5, dealer = 2)
            val r = PokerEngine.reseat(g, emptySet(), listOf(3000, 1200), Random(seed))!!
            assertEquals(7, r.game.seats.size)
            // Ordre relatif des anciens joueurs conservé.
            assertEquals(r.oldToNew.sorted(), r.oldToNew)
            assertEquals(5, r.oldToNew.distinct().size)
            // Le bouton est resté chez le même joueur.
            assertEquals(r.oldToNew[g.dealer], r.game.dealer)
            // Les arrivants ont leur tapis, à des places différentes de celles des anciens.
            assertEquals(3000, r.game.seats[r.arrivals[0]].stack)
            assertEquals(1200, r.game.seats[r.arrivals[1]].stack)
            assertEquals(7, (r.oldToNew + r.arrivals).distinct().size)
            // Les tapis des anciens joueurs n'ont pas bougé.
            g.seats.indices.forEach { assertEquals(g.seats[it].stack, r.game.seats[r.oldToNew[it]].stack) }
            assertEquals(7, r.game.winnings.size)
            assertEquals(-1, r.game.toAct)
        }
    }

    @Test
    fun toutesLesPlacesSontPossiblesPourUnArrivant() {
        val g = handOver(4)
        val gapsSeen = mutableSetOf<Int>()
        repeat(500) { seed ->
            val r = PokerEngine.reseat(g, emptySet(), listOf(1000), Random(seed))!!
            val newIndex = r.arrivals.single()
            // Ancien joueur assis juste avant l'arrivant.
            gapsSeen.add(r.oldToNew.indexOf(newIndex - 1))
        }
        assertEquals(setOf(0, 1, 2, 3), gapsSeen)
    }

    @Test
    fun siLeBoutonPartIlPasseAuJoueurAssisAvantLui() {
        val g = handOver(5, dealer = 2)
        val r = PokerEngine.reseat(g, setOf(2), emptyList())!!
        assertEquals(4, r.game.seats.size)
        assertEquals(-1, r.oldToNew[2])
        assertEquals(r.oldToNew[1], r.game.dealer)
        // La main suivante : le bouton avance vers le joueur qui suivait l'ancien bouton.
        val next = PokerEngine.nextHand(r.game, 1)
        assertEquals(r.oldToNew[3], next.dealer)
    }

    @Test
    fun lePremierJoueurPeutPartirEtLeBoutonRevientAuDernier() {
        val g = handOver(4, dealer = 0)
        val r = PokerEngine.reseat(g, setOf(0), emptyList())!!
        assertEquals(r.oldToNew[3], r.game.dealer)
    }

    @Test
    fun lesJoueursEliminesRestentEliminesApresUnChangementDePlaces() {
        val base = handOver(4)
        val seats = base.seats.toMutableList()
        seats[1] = seats[1].copy(stack = 0, out = true)
        val g = base.copy(seats = seats, eliminated = listOf(1))
        val r = PokerEngine.reseat(g, emptySet(), listOf(800), Random(7))!!
        val newIndex = r.oldToNew[1]
        assertTrue(r.game.seats[newIndex].out)
        assertEquals(listOf(newIndex), r.game.eliminated)
    }

    @Test
    fun pasDeChangementPendantUneMainNiAvecMoinsDeDeuxJoueurs() {
        val g = PokerEngine.newGame(settings, 4)
        assertEquals(PokerPhase.BETTING, g.phase)
        assertNull(PokerEngine.reseat(g, emptySet(), listOf(1000)))

        val over = handOver(3)
        // Il ne resterait qu'un joueur en lice.
        assertNull(PokerEngine.reseat(over, setOf(0, 1), emptyList()))
        // Un arrivant sans jeton est refusé.
        assertNull(PokerEngine.reseat(over, emptySet(), listOf(0)))
        // Mais un départ compensé par une arrivée est possible.
        assertNotNull(PokerEngine.reseat(over, setOf(0, 1), listOf(900)))
    }

    @Test
    fun fichierDeJoueursAllerRetour() {
        val players = listOf(PokerTransferPlayer("Marie", 4200, -1), PokerTransferPlayer("Léo", 150, 0))
        val parsed = PokerTransfer.parse(PokerTransfer.export(players, 1_700_000_000_000L))
        assertEquals(players, parsed)
    }

    @Test
    fun fichierInvalideEstRefuse() {
        assertNull(PokerTransfer.parse("pas du json"))
        assertNull(PokerTransfer.parse("""{"format":"autre","version":1,"players":[{"name":"A","stack":10}]}"""))
        assertNull(PokerTransfer.parse("""{"format":"mes-scores-poker-players","version":99,"players":[{"name":"A","stack":10}]}"""))
        // Aucun joueur utilisable (sans nom ou sans jeton).
        assertNull(PokerTransfer.parse("""{"format":"mes-scores-poker-players","version":1,"players":[{"name":" ","stack":10},{"name":"B","stack":0}]}"""))
        // Les entrées valides sont gardées, les autres ignorées.
        val partial = PokerTransfer.parse("""{"format":"mes-scores-poker-players","version":1,"players":[{"name":" ","stack":10},{"name":"B","stack":300}]}""")
        assertEquals(listOf(PokerTransferPlayer("B", 300, 0)), partial)
    }
}
