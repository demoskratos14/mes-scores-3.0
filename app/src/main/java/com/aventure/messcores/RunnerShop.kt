package com.aventure.messcores

/** Les trois bonus qu'on améliore en dépensant des pièces. */
internal enum class RunnerUpgrade { MAGNET, BOOST, STAR }

/** Les niveaux d'amélioration (de 0 à [RunnerShop.MAX_LEVEL]) : la partie les lit au départ. */
internal data class RunnerLevels(val magnet: Int = 0, val boost: Int = 0, val star: Int = 0) {
    fun of(upgrade: RunnerUpgrade): Int = when (upgrade) {
        RunnerUpgrade.MAGNET -> magnet
        RunnerUpgrade.BOOST -> boost
        RunnerUpgrade.STAR -> star
    }

    fun withLevel(upgrade: RunnerUpgrade, level: Int): RunnerLevels = when (upgrade) {
        RunnerUpgrade.MAGNET -> copy(magnet = level)
        RunnerUpgrade.BOOST -> copy(boost = level)
        RunnerUpgrade.STAR -> copy(star = level)
    }
}

/** Ce que rapportent les niveaux d'amélioration, et ce qu'ils coûtent. */
internal object RunnerShop {
    const val MAX_LEVEL = 5

    /** À partir de ce niveau, l'aimant attire aussi les pièces des autres voies (même quand on est tout au bord). */
    const val ALL_LANES_LEVEL = 3

    /** Prix pour passer du niveau 0 au niveau 1, du 1 au 2, etc. */
    private val COSTS = intArrayOf(150, 300, 500, 800, 1200)

    /** Le prix du niveau suivant, ou null si [level] est déjà le maximum. */
    fun cost(level: Int): Int? = COSTS.getOrNull(level)

    fun magnetSeconds(level: Int): Int = 8 + 3 * level
    fun boostSeconds(level: Int): Int = 5 + 2 * level
    fun starSeconds(level: Int): Int = 10 + 3 * level

    /** Durée en secondes du bonus [upgrade] à ce niveau. */
    fun seconds(upgrade: RunnerUpgrade, level: Int): Int = when (upgrade) {
        RunnerUpgrade.MAGNET -> magnetSeconds(level)
        RunnerUpgrade.BOOST -> boostSeconds(level)
        RunnerUpgrade.STAR -> starSeconds(level)
    }

    /** Jusqu'où devant le coureur (en mètres) l'aimant attire les pièces. */
    fun magnetAhead(level: Int): Float = 6f + 4f * level

    /** Écart latéral maximal (en mètres) entre le coureur et une pièce attirée : une voie à côté, puis toutes les voies. */
    fun magnetLateral(level: Int): Float = if (level >= ALL_LANES_LEVEL) 99f else 1.4f
}
