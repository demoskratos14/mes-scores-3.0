package com.aventure.messcores

import kotlin.random.Random

/**
 * Cartes du poker (Texas hold'em) : paquet, mélange et valeur d'une main de 5 à 7 cartes.
 *
 * Les cartes sont les mêmes entiers que dans [FrenchCards] : `couleur * 13 + valeur - 1`, la valeur allant de 1 (l'as) à
 * 13 (le roi). Ce fichier est pur (ni Android ni Compose) : il se teste sans émulateur.
 */
internal object PokerHands {

    /** Cartes du paquet (52, sans joker). */
    const val DECK = 52

    /** Catégories de mains, de la plus faible à la plus forte. Le quinte flush royale est une quinte flush à l'as. */
    const val HIGH_CARD = 0
    const val PAIR = 1
    const val TWO_PAIRS = 2
    const val THREE_OF_A_KIND = 3
    const val STRAIGHT = 4
    const val FLUSH = 5
    const val FULL_HOUSE = 6
    const val FOUR_OF_A_KIND = 7
    const val STRAIGHT_FLUSH = 8

    /** Nombre de catégories de mains. */
    const val CATEGORIES = 9

    // Une main vaut `catégorie * BASE^5 + départage`, le départage étant les valeurs utiles de la plus importante à la
    // plus faible, en base [BASE] (2 à 14, l'as valant 14).
    private const val BASE = 15
    private const val CATEGORY_UNIT = BASE * BASE * BASE * BASE * BASE

    /** Un paquet de 52 cartes mélangé. */
    fun shuffledDeck(random: Random = Random.Default): List<Int> = (0 until DECK).toList().shuffled(random)

    /** Force d'une carte : de 2 (le 2) à 14 (l'as, qui peut aussi valoir 1 dans une quinte). */
    fun strength(card: Int): Int {
        val rank = card % 13 + 1
        return if (rank == 1) 14 else rank
    }

    private fun suit(card: Int): Int = card / 13

    /** Catégorie d'une main d'après sa valeur [score] (de [HIGH_CARD] à [STRAIGHT_FLUSH]). */
    fun category(score: Int): Int = score / CATEGORY_UNIT

    /** Vrai pour la quinte flush royale (10, valet, dame, roi, as de la même couleur). */
    fun isRoyal(score: Int): Boolean = category(score) == STRAIGHT_FLUSH && score % CATEGORY_UNIT / (BASE * BASE * BASE * BASE) == 14

    /** Valeur de la plus haute carte d'une suite [ranks] (valeurs de 2 à 14), ou 0 s'il n'y en a pas ; la roue A-2-3-4-5 vaut 5. */
    private fun straightHigh(ranks: Set<Int>): Int {
        for (high in 14 downTo 5) {
            var complete = true
            for (r in high downTo high - 4) {
                val value = if (r == 1) 14 else r
                if (value !in ranks) {
                    complete = false
                    break
                }
            }
            if (complete) return high
        }
        return 0
    }

    private fun pack(category: Int, kickers: List<Int>): Int {
        var value = 0
        for (i in 0 until 5) value = value * BASE + kickers.getOrElse(i) { 0 }
        return category * CATEGORY_UNIT + value
    }

    /**
     * Valeur de la meilleure main de 5 cartes qu'on peut faire avec [cards] (5 à 7 cartes). Une main plus
     * forte a une valeur plus grande ; deux mains égales ont la même valeur (le pot se partage).
     */
    fun score(cards: List<Int>): Int {
        require(cards.size >= 5) { "Il faut au moins 5 cartes" }
        val byRank = IntArray(15)
        val bySuit = Array(4) { mutableListOf<Int>() }
        cards.forEach { card ->
            val s = strength(card)
            byRank[s]++
            bySuit[suit(card)].add(s)
        }
        val ranksPresent = (2..14).filter { byRank[it] > 0 }.toSet()

        // Quinte flush : une suite dans les cartes d'une même couleur.
        val flushSuit = bySuit.firstOrNull { it.size >= 5 }
        if (flushSuit != null) {
            val high = straightHigh(flushSuit.toSet())
            if (high > 0) return pack(STRAIGHT_FLUSH, listOf(high))
        }

        // Valeurs groupées : d'abord les plus nombreuses, puis les plus fortes.
        val groups = (14 downTo 2).filter { byRank[it] > 0 }.sortedWith(compareByDescending<Int> { byRank[it] }.thenByDescending { it })
        val top = groups.first()
        val topCount = byRank[top]

        if (topCount == 4) {
            val kicker = ranksPresent.filter { it != top }.maxOrNull() ?: 0
            return pack(FOUR_OF_A_KIND, listOf(top, kicker))
        }
        if (topCount == 3 && groups.size >= 2 && byRank[groups[1]] >= 2) {
            return pack(FULL_HOUSE, listOf(top, groups[1]))
        }
        if (flushSuit != null) {
            return pack(FLUSH, flushSuit.sortedDescending().take(5))
        }
        val straight = straightHigh(ranksPresent)
        if (straight > 0) return pack(STRAIGHT, listOf(straight))
        if (topCount == 3) {
            return pack(THREE_OF_A_KIND, listOf(top) + groups.drop(1).take(2))
        }
        if (topCount == 2) {
            val pairs = groups.filter { byRank[it] == 2 }
            if (pairs.size >= 2) {
                val kicker = ranksPresent.filter { it != pairs[0] && it != pairs[1] }.maxOrNull() ?: 0
                return pack(TWO_PAIRS, listOf(pairs[0], pairs[1], kicker))
            }
            return pack(PAIR, listOf(top) + groups.drop(1).take(3))
        }
        return pack(HIGH_CARD, groups.take(5))
    }
}
