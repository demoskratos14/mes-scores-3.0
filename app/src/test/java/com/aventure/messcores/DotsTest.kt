package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DotsTest {

    @Test
    fun lesTraitsSontNumerotesSansTrouNiDoublon() {
        val all = (0 until Dots.BOXES).flatMap { Dots.edgesOf(it) }.toSet()
        assertEquals((0 until Dots.EDGES).toSet(), all)
        assertEquals(60, Dots.EDGES)
        assertEquals(25, Dots.BOXES)
    }

    @Test
    fun chaqueCarreAQuatreTraitsEtChaqueTraitToucheUnOuDeuxCarres() {
        for (box in 0 until Dots.BOXES) {
            val edges = Dots.edgesOf(box)
            assertEquals(4, edges.toSet().size)
            // Réciproque : chaque trait du carré touche bien ce carré.
            edges.forEach { assertTrue("carré $box, trait $it", box in Dots.boxesOf(it)) }
        }
        for (edge in 0 until Dots.EDGES) {
            val boxes = Dots.boxesOf(edge)
            assertTrue(boxes.size in 1..2)
            boxes.forEach { assertTrue(edge in Dots.edgesOf(it)) }
        }
        // Un trait du bord ne touche qu'un carré, un trait du milieu en touche deux.
        assertEquals(listOf(0), Dots.boxesOf(Dots.horizontal(0, 0)))
        assertEquals(listOf(0, 5), Dots.boxesOf(Dots.horizontal(1, 0)))
        assertEquals(listOf(0), Dots.boxesOf(Dots.vertical(0, 0)))
        assertEquals(listOf(0, 1), Dots.boxesOf(Dots.vertical(0, 1)))
        assertEquals(listOf(24), Dots.boxesOf(Dots.vertical(4, 5)))
    }

    @Test
    fun unTexteValideNeContientQueDesChiffresDe0A2() {
        assertTrue(Dots.isValid("0120", 4))
        assertFalse(Dots.isValid("0123", 4))
        assertFalse(Dots.isValid("012", 4))
        assertFalse(Dots.isValid("01a0", 4))
    }
}

class DotsMatchTest {

    /** Trace [edges] en alternant les joueurs 1 et 2 (sans fermer de carré), en partant du joueur courant. */
    private fun drawAll(start: DotsMatch, edges: List<Int>): DotsMatch =
        edges.fold(start) { match, edge -> match.play(edge, match.current)!! }

    @Test
    fun seulLeJoueurDontCEstLeTourPeutJouer() {
        val match = DotsMatch()
        assertEquals(1, match.current)
        assertNull(match.play(0, 2))
        val after = match.play(0, 1)!!
        assertEquals(2, after.current)
        assertEquals(0, after.last)
        assertEquals('1', after.edges[0])
        assertNull(after.play(1, 1))
    }

    @Test
    fun unTraitDejaTraceOuInexistantEstRefuse() {
        val after = DotsMatch().play(5, 1)!!
        assertNull(after.play(5, 2))
        assertNull(after.play(Dots.EDGES, 2))
        assertNull(after.play(-1, 2))
    }

    @Test
    fun fermerUnCarreLeDonneEtPermetDeRejouer() {
        // Carré 0 : traits horizontal(0,0), horizontal(1,0), vertical(0,0), vertical(0,1).
        val top = Dots.horizontal(0, 0)
        val bottom = Dots.horizontal(1, 0)
        val left = Dots.vertical(0, 0)
        val right = Dots.vertical(0, 1)
        var match = DotsMatch()
        match = match.play(top, 1)!!        // joueur 1
        match = match.play(bottom, 2)!!     // joueur 2
        match = match.play(left, 1)!!       // joueur 1
        assertEquals(2, match.current)
        match = match.play(right, 2)!!      // joueur 2 ferme le carré
        assertEquals('2', match.boxes[0])
        assertEquals(1, match.boxesB)
        assertEquals(0, match.boxesA)
        // Il rejoue aussitôt.
        assertEquals(2, match.current)
        match = match.play(Dots.horizontal(0, 1), 2)!!
        assertEquals(1, match.current)
    }

    @Test
    fun unTraitPeutFermerDeuxCarresALaFois() {
        // Carrés 0 et 1 : tout est tracé sauf le trait vertical(0,1) qui les sépare.
        val around = listOf(
            Dots.horizontal(0, 0), Dots.horizontal(1, 0), Dots.vertical(0, 0),
            Dots.horizontal(0, 1), Dots.horizontal(1, 1), Dots.vertical(0, 2)
        )
        val edges = StringBuilder(Dots.EMPTY_EDGES)
        around.forEach { edges[it] = '1' }
        val match = DotsMatch(edges = edges.toString(), current = 2)
        val closed = match.play(Dots.vertical(0, 1), 2)!!
        assertEquals('2', closed.boxes[0])
        assertEquals('2', closed.boxes[1])
        assertEquals(2, closed.boxesB)
        assertEquals(2, closed.current)
    }

    /** Une manche presque finie : tous les traits sont tracés sauf [free], et les carrés fermés appartiennent aux joueurs donnés. */
    private fun almostOver(free: Int, ownerOfRest: Char, otherOwner: Char, otherCount: Int, current: Int): DotsMatch {
        val edges = StringBuilder("1".repeat(Dots.EDGES)).also { it[free] = '0' }
        val boxes = StringBuilder(Dots.EMPTY_BOXES)
        val closable = Dots.boxesOf(free)
        var given = 0
        for (box in 0 until Dots.BOXES) {
            if (box in closable) continue
            boxes[box] = if (given < otherCount) otherOwner else ownerOfRest
            given++
        }
        return DotsMatch(edges = edges.toString(), boxes = boxes.toString(), current = current)
    }

    @Test
    fun laMancheSeTermineQuandTousLesCarresSontPrisEtLePlusGrandNombreGagne() {
        // Le dernier trait ferme le carré 24 : 12 carrés chacun avant, le dernier carré décide.
        val free = Dots.vertical(4, 5)
        assertEquals(listOf(24), Dots.boxesOf(free))
        val match = almostOver(free, ownerOfRest = '2', otherOwner = '1', otherCount = 12, current = 1)
        assertEquals(12, match.boxesA)
        assertEquals(12, match.boxesB)
        assertFalse(match.isOver)

        val done = match.play(free, 1)!!
        assertTrue(done.isOver)
        assertEquals(13, done.boxesA)
        assertEquals(1, done.winner)
        assertEquals(1, done.winsA)
        assertEquals(0, done.winsB)
        assertNull(done.play(0, 1))
    }

    @Test
    fun leJoueurAvecLePlusDeCarresGagneMemeSiCEstLAutreQuiFermeLeDernier() {
        val free = Dots.vertical(4, 5)
        // 13 carrés pour le joueur 1, 11 pour le joueur 2 : le joueur 2 ferme le dernier, mais perd quand même.
        val match = almostOver(free, ownerOfRest = '1', otherOwner = '2', otherCount = 11, current = 2)
        assertEquals(13, match.boxesA)
        assertEquals(11, match.boxesB)
        val done = match.play(free, 2)!!
        assertTrue(done.isOver)
        assertEquals(12, done.boxesB)
        assertEquals(1, done.winner)
        assertEquals(1, done.winsA)
        assertEquals(0, done.winsB)
    }

    @Test
    fun laMancheSuivanteNeSeLancheQueQuandLaMancheEstFinieEtLAutreJoueurCommence() {
        assertNull(DotsMatch().newRound())
        val free = Dots.vertical(4, 5)
        val done = almostOver(free, '2', '1', 12, 1).play(free, 1)!!
        val next = done.newRound()!!
        assertEquals(Dots.EMPTY_EDGES, next.edges)
        assertEquals(Dots.EMPTY_BOXES, next.boxes)
        assertEquals(1, next.starter)
        assertEquals(2, next.current)
        assertEquals(1, next.winsA)
        assertEquals(-1, next.last)
        assertNull(next.newRound())
        // Si c'est le deuxième joueur qui avait commencé, le premier reprend à la manche suivante.
        val second = DotsMatch(edges = "1".repeat(Dots.EDGES), boxes = "1".repeat(Dots.BOXES), starter = 1).newRound()!!
        assertEquals(0, second.starter)
        assertEquals(1, second.current)
    }

    @Test
    fun laSeanceSurvitAuTexte() {
        val match = drawAll(DotsMatch(winsA = 2, winsB = 1, draws = 3, starter = 1), listOf(0, 7, 31))
        assertEquals(match, DotsMatch.decode(match.encode()))
        assertEquals(DotsMatch(), DotsMatch.decode(DotsMatch().encode()))
    }

    @Test
    fun unTexteIllisibleEstRefuseEtUnScoreAbsurdeEstRamene() {
        assertNull(DotsMatch.decode("n'importe quoi"))
        assertNull(DotsMatch.decode("000;000;1;0;0;0;0;-1"))
        assertNull(DotsMatch.decode("${"3".repeat(Dots.EDGES)};${Dots.EMPTY_BOXES};1;0;0;0;0;-1"))
        val fixed = DotsMatch.decode("${Dots.EMPTY_EDGES};${Dots.EMPTY_BOXES};9;7;-4;x;2;999")
        assertNotNull(fixed)
        assertEquals(2, fixed!!.current)
        assertEquals(1, fixed.starter)
        assertEquals(0, fixed.winsA)
        assertEquals(0, fixed.winsB)
        assertEquals(2, fixed.draws)
        assertEquals(Dots.EDGES - 1, fixed.last)
    }
}
