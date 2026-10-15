package com.aventure.messcores

/** Gains du tournoi : cagnotte = buy-in × nombre de joueurs, répartie selon le classement final. */
object PokerPrizes {
    /** Répartitions proposées à la création (en % de la cagnotte, de la 1re place vers les suivantes). */
    val PRESETS: List<List<Int>> = listOf(listOf(100), listOf(65, 35), listOf(50, 30, 20))

    /** Nombre minimal de joueurs pour qu'une répartition à [places] places ait un sens. */
    fun minPlayers(places: Int): Int = places + 1

    /** Cagnotte : un buy-in par joueur et par recave. */
    fun pool(settings: PokerSettings, playerCount: Int, rebuyCount: Int = 0): Int =
        (settings.buyIn.toLong() * (playerCount + rebuyCount)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /**
     * Gain de chaque joueur (même ordre que les places), 0 si rien n'est à gagner. À appeler une fois
     * le tournoi fini : les ex æquo se partagent les gains des places qu'ils occupent, et les jetons
     * qui ne se divisent pas vont au joueur placé en premier dans la liste.
     */
    fun payouts(game: PokerGame): List<Int> {
        val n = game.seats.size
        val settings = game.settings
        val result = MutableList(n) { 0 }
        val percents = settings.payoutPercents.take(n)
        if (settings.buyIn <= 0 || percents.isEmpty()) return result
        val pool = pool(settings, n, game.rebuys.sum()).toLong()
        val prizes = percents.map { pool * it / 100 }.toMutableList()
        prizes[0] += pool - prizes.sum() // reste des arrondis : à la première place
        val ranks = PokerEngine.ranks(game)
        ranks.indices.groupBy { ranks[it] }.forEach { (rank, members) ->
            val total = (rank - 1 until rank - 1 + members.size).sumOf { prizes.getOrElse(it) { 0L } }
            val share = total / members.size
            var rest = total % members.size
            members.sorted().forEach { i ->
                result[i] = (share + if (rest > 0) 1 else 0).toInt()
                if (rest > 0) rest--
            }
        }
        return result
    }
}
