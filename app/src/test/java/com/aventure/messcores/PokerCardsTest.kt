package com.aventure.messcores

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PokerCardsTest {

    // Une carte s'écrit « AS » (as de pique), « TH » (10 de cœur)… : valeur puis couleur (S pique, H cœur, D carreau, C trèfle).
    private fun cards(vararg names: String): List<Int> = names.map {
        val rank = when (val r = it.dropLast(1)) {
            "A" -> 1
            "K" -> 13
            "Q" -> 12
            "J" -> 11
            "T" -> 10
            else -> r.toInt()
        }
        "SHDC".indexOf(it.last()) * 13 + rank - 1
    }

    private fun score(vararg names: String) = PokerHands.score(cards(*names))

    private fun category(vararg names: String) = PokerHands.category(score(*names))

    private val settings = PokerSettings(1000, 10, 20, levelMinutes = 0, anteEnabled = false, anteAfterIncreases = 0, firstDealer = 0, dealCards = true)

    @Test
    fun categories() {
        assertTrue(PokerHands.isRoyal(score("AS", "KS", "QS", "JS", "TS", "2D", "3C")))
        assertEquals(PokerHands.STRAIGHT_FLUSH, category("9H", "8H", "7H", "6H", "5H", "AC", "KD"))
        assertEquals(PokerHands.FOUR_OF_A_KIND, category("9H", "9S", "9D", "9C", "5H", "AC", "KD"))
        assertEquals(PokerHands.FULL_HOUSE, category("9H", "9S", "9D", "KH", "KC", "2C", "3D"))
        assertEquals(PokerHands.FULL_HOUSE, category("9H", "9S", "9D", "KC", "KH", "KD", "3D"))
        assertEquals(PokerHands.FLUSH, category("2H", "8H", "7H", "KH", "5H", "AC", "KD"))
        assertEquals(PokerHands.STRAIGHT, category("AH", "2S", "3D", "4C", "5H", "9C", "KD"))
        assertEquals(PokerHands.THREE_OF_A_KIND, category("9H", "9S", "9D", "6C", "5H", "AC", "KD"))
        assertEquals(PokerHands.TWO_PAIRS, category("9H", "9S", "4D", "4C", "5H", "AC", "KD"))
        assertEquals(PokerHands.PAIR, category("9H", "9S", "3D", "4C", "5H", "AC", "KD"))
        assertEquals(PokerHands.HIGH_CARD, category("9H", "2S", "3D", "4C", "7H", "AC", "KD"))
    }

    @Test
    fun orderingAndKickers() {
        assertTrue(score("AH", "2S", "3D", "4C", "5H", "9C", "KD") < score("2H", "3S", "4D", "5C", "6H", "9C", "KD"))
        assertTrue(score("4H", "4S", "4D", "4C", "KH", "3C", "3D") > score("4H", "4S", "4D", "4C", "QH", "3C", "3D"))
        assertTrue(score("KH", "KS", "QD", "QC", "3H", "3C", "AD") > score("KH", "KS", "QD", "QC", "3H", "3C", "2D"))
        assertEquals(score("KH", "KS", "QD", "QC", "3H", "2C", "AD"), score("KD", "KC", "QH", "QS", "3D", "2S", "AC"))
        assertTrue(score("9H", "8H", "7H", "6H", "5H", "9C", "9D") > score("AH", "AS", "AD", "AC", "5H", "2C", "3D"))
        assertFalse(PokerHands.isRoyal(score("9H", "8H", "7H", "6H", "5H", "AC", "KD")))
    }

    @Test
    fun dealIsConsistentAndPrivate() {
        for (seed in 1..50) {
            val rnd = Random(seed)
            val n = 2 + seed % 9
            val game = PokerEngine.newGame(settings, n, rnd)
            val all = game.holeCards.flatten() + game.board
            assertEquals(2 * n + 5, all.size)
            assertEquals(all.size, all.toSet().size)
            val view = PokerEngine.viewFor(game, 0)
            assertEquals(game.holeCards[0], view.holeCards[0])
            assertTrue((1 until n).all { view.holeCards[it].isEmpty() })
            assertTrue(view.board.isEmpty())
        }
    }

    @Test
    fun showdownPaysTheBestHandAndKeepsChips() {
        for (seed in 1..100) {
            val rnd = Random(seed)
            val n = 2 + seed % 9
            var game = PokerEngine.newGame(settings, n, rnd)
            var guard = 0
            while (game.phase == PokerPhase.BETTING && guard++ < 200) game = PokerEngine.call(game)
            assertEquals(PokerPhase.SHOWDOWN, game.phase)
            assertEquals(5, PokerEngine.visibleBoard(game).size)
            val total = game.seats.sumOf { it.stack } + game.pot
            val resolved = PokerEngine.resolveAuto(game)
            assertEquals(PokerPhase.HAND_OVER, resolved.phase)
            assertEquals(total, resolved.seats.sumOf { it.stack })
            val best = PokerEngine.revealedSeats(game).maxOf { PokerEngine.handScore(game, it)!! }
            resolved.winnings.indices.filter { resolved.winnings[it] > 0 }.forEach {
                assertEquals(best, PokerEngine.handScore(game, it))
            }
        }
    }

    @Test
    fun foldedHandIsNotRevealed() {
        var game = PokerEngine.newGame(settings, 3, Random(5))
        game = PokerEngine.fold(PokerEngine.fold(game))
        assertEquals(PokerPhase.HAND_OVER, game.phase)
        assertTrue(PokerEngine.revealedSeats(game).isEmpty())
    }

    @Test
    fun jsonRoundTripKeepsCards() {
        val game = PokerEngine.newGame(settings, 4, Random(7))
        assertEquals(game, PokerGame.fromJson(game.toJson()))
    }

    @Test
    fun withoutDealingNothingChanges() {
        val game = PokerEngine.newGame(settings.copy(dealCards = false), 3, Random(5))
        assertFalse(game.cardsDealt)
        assertEquals(game, PokerEngine.viewFor(game, 0))
        assertEquals(game, PokerEngine.resolveAuto(game))
    }

    @Test
    fun messagesRoundTrip() {
        val game = PokerEngine.viewFor(PokerEngine.newGame(settings, 3, Random(3)), 1)
        val messages = listOf(
            PokerMessage.View(game, 1, listOf(1, 2)), PokerMessage.Claim(2), PokerMessage.Fold, PokerMessage.Check,
            PokerMessage.Call, PokerMessage.AllIn, PokerMessage.Raise(120), PokerMessage.Showdown, PokerMessage.NextHand
        )
        messages.forEach { assertEquals(it, pokerMessageFromJson(it.toJson())) }
    }
}
