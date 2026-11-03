package com.aventure.messcores

import kotlin.random.Random

/** Identifiant du Duel de réflexes parmi les jeux prédéfinis (écran à part : voir [ReflexScreen]). */
internal const val REFLEX_ID = "builtin_reflex"

/** Réglages du Duel de réflexes. */
internal object Reflex {

    /** Nombre de manches à gagner pour remporter la partie. */
    const val TARGET = 3

    /** Chacun sur son téléphone : de deux joueurs à huit autour de la table. */
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 8

    /** Attente avant le signal : entre 1,5 et 5 secondes, pour qu'on ne puisse pas l'anticiper. */
    const val MIN_DELAY_MS = 1_500L
    const val MAX_DELAY_MS = 5_000L

    /**
     * Chacun sur son téléphone : temps laissé après le signal avant de compter la manche comme « pas touchée ».
     * Sans cela, un joueur qui ne touche jamais bloquerait l'autre téléphone.
     */
    const val RESPONSE_TIMEOUT_MS = 3_000L

    /** Un seul téléphone : temps minimal laissé au perdant pour toucher après le vainqueur, pour noter son temps. */
    const val LOSER_MIN_WINDOW_MS = 1_000L

    /** Résultat d'un joueur pour une manche chacun sur son téléphone : toucher avant le signal. */
    const val EARLY = 0

    /** Résultat d'un joueur pour une manche chacun sur son téléphone : pas touché à temps. Un temps de réaction est positif. */
    const val MISS = -1

    /** Tire au hasard l'attente avant le signal, en millisecondes. */
    fun randomDelayMs(random: Random = Random.Default): Long = random.nextLong(MIN_DELAY_MS, MAX_DELAY_MS + 1)
}

/**
 * La séance de deux joueurs sur un seul téléphone (chacun sur le sien : voir [ReflexGame]) : chacun a sa moitié d'écran, un signal apparaît après une attente
 * aléatoire et le premier qui touche sa zone gagne la manche. Toucher avant le signal (faux départ) donne la manche
 * à l'autre. Le premier à [target] manches gagnées remporte la partie.
 *
 * Les joueurs sont numérotés 1 et 2. [lastWinner] vaut 0 tant qu'aucune manche n'est jouée ; [lastReactionMs] est
 * le temps de réaction du vainqueur de la dernière manche (0 après un faux départ) et [lastLoserReactionMs] celui du
 * perdant (0 s'il a fait un faux départ ou n'a pas touché à temps).
 */
internal data class ReflexMatch(
    val wins1: Int = 0,
    val wins2: Int = 0,
    val lastWinner: Int = 0,
    val lastReactionMs: Int = 0,
    val falseStart: Boolean = false,
    val lastLoserReactionMs: Int = 0,
    val target: Int = Reflex.TARGET,
    /** Somme des temps de réaction du joueur 1 depuis le début de la partie, et nombre de temps pris en compte. */
    val totalMs1: Int = 0,
    val timedRounds1: Int = 0,
    /** Idem pour le joueur 2. */
    val totalMs2: Int = 0,
    val timedRounds2: Int = 0
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

    /** Nombre de manches où le joueur [player] (1 ou 2) a touché après le signal et dont le temps est compté. */
    fun timedRoundsOf(player: Int): Int = if (player == 1) timedRounds1 else timedRounds2

    /**
     * Temps de réaction moyen du joueur [player] (1 ou 2) depuis le début de la partie, en millisecondes arrondies,
     * ou `null` si aucun de ses temps n'est connu. Les faux départs, les manches non touchées et les manches nulles
     * ne comptent pas : ce ne sont pas des temps de réaction.
     */
    fun averageMsOf(player: Int): Int? {
        val count = timedRoundsOf(player)
        if (count == 0) return null
        val total = if (player == 1) totalMs1 else totalMs2
        return ((total + count / 2) / count)
    }

    /** Vrai quand au moins un joueur a deux temps ou plus : c'est là qu'une moyenne apporte quelque chose. */
    val hasAverages: Boolean get() = timedRounds1 >= 2 || timedRounds2 >= 2

    /**
     * Le joueur [player] touche sa zone [reactionMs] millisecondes après le signal : il gagne la manche.
     * Renvoie `null` si la partie est finie ou si le joueur n'existe pas.
     */
    fun tapAfterSignal(player: Int, reactionMs: Long): ReflexMatch? {
        if (isOver || player !in 1..2) return null
        return win(player, reactionMs.coerceIn(1L, MAX_REACTION_MS).toInt(), falseStart = false)
    }

    /**
     * Un seul téléphone : le perdant a touché sa zone [reactionMs] millisecondes après le signal, après le vainqueur.
     * Son temps est noté sans changer le score. Sans effet après un faux départ ou si le temps du
     * perdant est déjà connu.
     */
    fun loserTapped(reactionMs: Long): ReflexMatch {
        if (lastWinner == 0 || falseStart || lastLoserReactionMs > 0) return this
        val loserMs = reactionMs.coerceIn(1L, MAX_REACTION_MS).toInt()
        return copy(lastLoserReactionMs = loserMs).withTime(3 - lastWinner, loserMs)
    }

    /** Le joueur [player] touche avant le signal : faux départ, la manche va à l'autre joueur. */
    fun tapTooEarly(player: Int): ReflexMatch? {
        if (isOver || player !in 1..2) return null
        return win(3 - player, reactionMs = 0, falseStart = true)
    }

    /** Une nouvelle partie : les scores repartent de zéro, la cible reste la même. */
    fun newMatch(): ReflexMatch = ReflexMatch(target = target)

    private fun win(player: Int, reactionMs: Int, falseStart: Boolean) = copy(
        wins1 = wins1 + if (player == 1) 1 else 0,
        wins2 = wins2 + if (player == 2) 1 else 0,
        lastWinner = player,
        lastReactionMs = reactionMs,
        lastLoserReactionMs = 0,
        falseStart = falseStart
    ).withTime(player, reactionMs)

    /** Ajoute le temps de réaction [ms] du joueur [player] à ses totaux (rien si ce n'est pas un vrai temps). */
    private fun withTime(player: Int, ms: Int): ReflexMatch = when {
        ms <= 0 -> this
        player == 1 -> copy(totalMs1 = totalMs1 + ms, timedRounds1 = timedRounds1 + 1)
        else -> copy(totalMs2 = totalMs2 + ms, timedRounds2 = timedRounds2 + 1)
    }

    private companion object {
        /** Au-delà de dix secondes, le temps n'a plus de sens (écran laissé de côté) : on le plafonne. */
        const val MAX_REACTION_MS = 9_999L
    }
}
