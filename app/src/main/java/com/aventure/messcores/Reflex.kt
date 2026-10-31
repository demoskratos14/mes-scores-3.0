package com.aventure.messcores

import kotlin.random.Random

/** Identifiant du Duel de réflexes parmi les jeux prédéfinis (écran à part : voir [ReflexScreen]). */
internal const val REFLEX_ID = "builtin_reflex"

/** Réglages du Duel de réflexes. */
internal object Reflex {

    /** Nombre de manches à gagner pour remporter la partie. */
    const val TARGET = 3

    /** Attente avant le signal : entre 1,5 et 5 secondes, pour qu'on ne puisse pas l'anticiper. */
    const val MIN_DELAY_MS = 1_500L
    const val MAX_DELAY_MS = 5_000L

    /**
     * Chacun sur son téléphone : temps laissé après le signal avant de compter la manche comme « pas touchée ».
     * Sans cela, un joueur qui ne touche jamais bloquerait l'autre téléphone.
     */
    const val RESPONSE_TIMEOUT_MS = 3_000L

    /** Résultat d'un joueur pour une manche à deux téléphones : toucher avant le signal. */
    const val EARLY = 0

    /** Résultat d'un joueur pour une manche à deux téléphones : pas touché à temps. Un temps de réaction est positif. */
    const val MISS = -1

    /** Tire au hasard l'attente avant le signal, en millisecondes. */
    fun randomDelayMs(random: Random = Random.Default): Long = random.nextLong(MIN_DELAY_MS, MAX_DELAY_MS + 1)
}

/**
 * La séance de deux joueurs sur un téléphone : chacun a sa moitié d'écran, un signal apparaît après une attente
 * aléatoire et le premier qui touche sa zone gagne la manche. Toucher avant le signal (faux départ) donne la manche
 * à l'autre. Le premier à [target] manches gagnées remporte la partie.
 *
 * Les joueurs sont numérotés 1 et 2. [lastWinner] vaut 0 tant qu'aucune manche n'est jouée ; [lastReactionMs] est
 * le temps de réaction du vainqueur de la dernière manche (0 après un faux départ).
 */
internal data class ReflexMatch(
    val wins1: Int = 0,
    val wins2: Int = 0,
    val lastWinner: Int = 0,
    val lastReactionMs: Int = 0,
    val falseStart: Boolean = false,
    /** Manche nulle (deux téléphones seulement : égalité exacte, deux faux départs ou personne n'a touché) : on la rejoue. */
    val lastDraw: Boolean = false,
    val target: Int = Reflex.TARGET
) {
    /** Le vainqueur de la partie (1 ou 2), ou 0 si elle continue. */
    val winner: Int
        get() = when {
            wins1 >= target -> 1
            wins2 >= target -> 2
            else -> 0
        }

    val isOver: Boolean get() = winner != 0

    /** Victoires du joueur [player] (1 ou 2). */
    fun winsOf(player: Int): Int = if (player == 1) wins1 else wins2

    /**
     * Le joueur [player] touche sa zone [reactionMs] millisecondes après le signal : il gagne la manche.
     * Renvoie `null` si la partie est finie ou si le joueur n'existe pas.
     */
    fun tapAfterSignal(player: Int, reactionMs: Long): ReflexMatch? {
        if (isOver || player !in 1..2) return null
        return win(player, reactionMs.coerceIn(1L, MAX_REACTION_MS).toInt(), falseStart = false)
    }

    /** Le joueur [player] touche avant le signal : faux départ, la manche va à l'autre joueur. */
    fun tapTooEarly(player: Int): ReflexMatch? {
        if (isOver || player !in 1..2) return null
        return win(3 - player, reactionMs = 0, falseStart = true)
    }

    /**
     * Deux téléphones : chacun a mesuré son propre temps de réaction depuis son propre signal, l'organisateur compare.
     * Un résultat est un temps en millisecondes (positif), [Reflex.EARLY] (faux départ) ou [Reflex.MISS] (pas touché).
     * Un faux départ perd la manche ; sinon le temps le plus court gagne, et toucher vaut mieux que ne pas toucher.
     * Égalité exacte, deux faux départs ou deux « pas touché » : manche nulle, personne ne marque.
     * Renvoie `null` si la partie est finie.
     */
    fun resolve(result1: Int, result2: Int): ReflexMatch? {
        if (isOver) return null
        val r1 = result1.coerceAtMost(MAX_REACTION_MS.toInt()).let { if (it < 0) Reflex.MISS else it }
        val r2 = result2.coerceAtMost(MAX_REACTION_MS.toInt()).let { if (it < 0) Reflex.MISS else it }
        return when {
            r1 == Reflex.EARLY && r2 == Reflex.EARLY -> draw()
            r1 == Reflex.EARLY -> win(2, reactionMs = 0, falseStart = true)
            r2 == Reflex.EARLY -> win(1, reactionMs = 0, falseStart = true)
            r1 > 0 && r2 > 0 -> when {
                r1 < r2 -> win(1, r1, falseStart = false)
                r2 < r1 -> win(2, r2, falseStart = false)
                else -> draw()
            }
            r1 > 0 -> win(1, r1, falseStart = false)
            r2 > 0 -> win(2, r2, falseStart = false)
            else -> draw()
        }
    }

    /** Une nouvelle partie : les scores repartent de zéro, la cible reste la même. */
    fun newMatch(): ReflexMatch = ReflexMatch(target = target)

    private fun draw() = copy(lastWinner = 0, lastReactionMs = 0, falseStart = false, lastDraw = true)

    private fun win(player: Int, reactionMs: Int, falseStart: Boolean) = copy(
        wins1 = wins1 + if (player == 1) 1 else 0,
        wins2 = wins2 + if (player == 2) 1 else 0,
        lastWinner = player,
        lastReactionMs = reactionMs,
        falseStart = falseStart,
        lastDraw = false
    )

    private companion object {
        /** Au-delà de dix secondes, le temps n'a plus de sens (écran laissé de côté) : on le plafonne. */
        const val MAX_REACTION_MS = 9_999L
    }
}
