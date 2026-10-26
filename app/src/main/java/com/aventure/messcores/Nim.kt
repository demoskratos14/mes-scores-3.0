package com.aventure.messcores

/** Identifiant du jeu des allumettes (Nim) parmi les jeux prédéfinis (écran à part : voir [NimScreen]). */
internal const val NIM_ID = "builtin_nim"

/**
 * Règles du jeu des allumettes : un tas d'allumettes, chacun son tour en retire 1, 2 ou 3, et celui qui prend la
 * dernière perd.
 */
internal object Nim {

    /** Allumettes au départ de chaque manche. */
    const val START = 21

    /** Nombre maximal d'allumettes qu'on peut retirer en un tour. */
    const val MAX_TAKE = 3

    /** Un coup est permis s'il retire de 1 à [MAX_TAKE] allumettes, sans dépasser celles qui restent. */
    fun isLegal(remaining: Int, take: Int): Boolean = take in 1..MAX_TAKE && take <= remaining

    /**
     * Le meilleur coup pour qui joue avec [remaining] allumettes : laisser un nombre d'allumettes de la forme 4k + 1
     * à l'adversaire (la dernière est alors pour lui). Quand ce n'est pas possible, la position est perdante
     * face à un adversaire parfait et on prend une seule allumette.
     */
    fun bestTake(remaining: Int): Int {
        if (remaining <= 0) return 0
        val take = (remaining - 1) % (MAX_TAKE + 1)
        return if (take == 0) 1 else take
    }
}

/**
 * Une séance de jeu des allumettes : le tas de la manche en cours, à qui c'est de jouer, qui a commencé
 * (0 : le premier joueur, 1 : le deuxième), qui a pris la dernière allumette et le score. Sert au jeu chacun sur
 * son téléphone (voir [NimOnline]) : l'organisateur la tient, l'invité en reçoit une copie à chaque coup.
 * Le premier joueur (1) est l'organisateur, le deuxième (2) l'invité.
 */
internal data class NimMatch(
    val remaining: Int = Nim.START,
    val turn: Int = 1,
    val starter: Int = 0,
    /** Le joueur (1 ou 2) qui a pris la dernière allumette et perd la manche, 0 tant qu'elle n'est pas finie. */
    val loser: Int = 0,
    val wins1: Int = 0,
    val wins2: Int = 0
) {
    val isOver: Boolean get() = remaining == 0

    /** Le joueur (1 ou 2) qui gagne la manche, 0 tant qu'elle n'est pas finie. */
    val winner: Int get() = if (loser == 0) 0 else 3 - loser

    /** La séance après le coup de [player] qui retire [take] allumettes, ou null si ce n'est pas son tour ou si le coup n'est pas permis. */
    fun play(take: Int, player: Int): NimMatch? {
        if (isOver || player != turn || !Nim.isLegal(remaining, take)) return null
        val left = remaining - take
        return if (left == 0) {
            // Qui prend la dernière allumette perd : l'autre joueur marque.
            copy(
                remaining = 0,
                loser = player,
                wins1 = if (player == 2) wins1 + 1 else wins1,
                wins2 = if (player == 1) wins2 + 1 else wins2
            )
        } else {
            copy(remaining = left, turn = 3 - player)
        }
    }

    /** Manche suivante (l'autre joueur commence), ou null tant que la manche n'est pas finie. */
    fun newRound(): NimMatch? =
        if (isOver) copy(remaining = Nim.START, turn = 2 - starter, starter = 1 - starter, loser = 0) else null
}
