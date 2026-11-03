package com.aventure.messcores

import kotlin.math.max
import kotlin.random.Random

/** Identifiant du jeu Simon parmi les jeux prédéfinis (écran à part : voir [SimonScreen]). */
internal const val SIMON_ID = "builtin_simon"

/** Réglages du jeu Simon. */
internal object Simon {

    /** Un téléphone qu'on se passe : seul ou jusqu'à huit joueurs. */
    const val MIN_PLAYERS = 1
    const val MAX_PLAYERS = 8

    /** Nombre de touches colorées : 0 vert, 1 rouge, 2 jaune, 3 bleu. */
    const val PADS = 4

    /** Durée d'allumage d'une touche quand la suite n'a qu'une couleur, puis plancher atteint en l'allongeant. */
    const val START_FLASH_MS = 600L
    const val MIN_FLASH_MS = 220L

    /** Temps retiré à la durée d'allumage chaque fois que la suite s'allonge d'une couleur. */
    const val FLASH_STEP_MS = 25L

    /** Pause entre deux touches allumées. */
    const val GAP_MS = 150L

    /** Pause avant de montrer la suite : le temps de se concentrer. */
    const val PRE_SHOW_MS = 700L

    /** Durée d'allumage d'une touche pour une suite de [length] couleurs : de plus en plus rapide. */
    fun flashMs(length: Int): Long =
        max(MIN_FLASH_MS, START_FLASH_MS - (length - 1).coerceAtLeast(0) * FLASH_STEP_MS)
}

/**
 * Une série d'un joueur : la suite de touches à reproduire ([sequence]), où il en est ([progress]), le nombre de
 * suites reproduites en entier ([completed], c'est son score) et s'il s'est trompé ([failed]).
 * Une série commence par une suite d'une seule couleur ; chaque suite réussie en ajoute une.
 */
internal data class SimonRun(
    val sequence: List<Int>,
    val progress: Int = 0,
    val completed: Int = 0,
    val failed: Boolean = false
) {
    /** Vrai quand toute la suite vient d'être reproduite : on passe à la suivante avec [nextRound]. */
    val roundComplete: Boolean get() = !failed && progress >= sequence.size

    /** La touche attendue, ou `null` si la suite est reproduite ou la série finie. */
    val expected: Int? get() = if (failed) null else sequence.getOrNull(progress)

    /** Le joueur touche [pad] : bonne touche, il avance ; mauvaise touche, la série est finie. Sans effet une fois la suite finie ou ratée. */
    fun tap(pad: Int): SimonRun = when {
        failed || roundComplete -> this
        pad == sequence[progress] -> copy(progress = progress + 1)
        else -> copy(failed = true)
    }

    /** La suite suivante : la même avec une touche de plus, tirée au hasard. Sans effet tant que la suite n'est pas reproduite. */
    fun nextRound(random: Random = Random.Default): SimonRun =
        if (!roundComplete) this
        else copy(sequence = sequence + random.nextInt(Simon.PADS), progress = 0, completed = completed + 1)

    companion object {
        /** Une série neuve : une couleur au hasard. */
        fun start(random: Random = Random.Default) = SimonRun(listOf(random.nextInt(Simon.PADS)))
    }
}

/**
 * La séance de Simon sur un seul téléphone qu'on se passe : chaque joueur joue sa série à son tour (avec sa propre
 * suite) et le meilleur score gagne. Les scores sont rangés dans l'ordre de jeu ; le joueur courant est le suivant.
 */
internal data class SimonGame(
    val players: Int,
    val scores: List<Int> = emptyList()
) {
    /** La place du joueur qui doit jouer (ou [players] quand tout le monde a joué). */
    val current: Int get() = scores.size

    val isOver: Boolean get() = scores.size >= players

    /** Le meilleur score joué jusqu'ici (0 si personne n'a encore joué ou réussi une suite). */
    val best: Int get() = scores.maxOrNull() ?: 0

    /** Les places gagnantes une fois la partie finie. Vide si personne n'a réussi une seule suite. */
    val winners: List<Int>
        get() = if (!isOver || best <= 0) emptyList() else scores.indices.filter { scores[it] == best }

    /** Les places classées du meilleur score au plus faible (à égalité, dans l'ordre de jeu). */
    fun ranking(): List<Int> = scores.indices.sortedByDescending { scores[it] }

    /** Le joueur courant a fini sa série avec [score] : au suivant. Sans effet quand la partie est finie. */
    fun finishRun(score: Int): SimonGame =
        if (isOver) this else copy(scores = scores + score.coerceAtLeast(0))

    /** Une nouvelle partie avec les mêmes joueurs. */
    fun newGame(): SimonGame = SimonGame(players)
}

/**
 * Le mode « chacun son tour » : une seule suite pour tous, qui s'allonge d'une couleur chaque fois qu'un joueur la
 * reproduit en entier. Celui qui se trompe est éliminé et le joueur suivant retente la même suite ; le dernier joueur
 * en jeu gagne.
 *
 * Les joueurs sont repérés par leur place (0, 1, ...) dans l'ordre de jeu. [sequence] est la suite que le joueur
 * [current] doit reproduire ; [eliminated] liste les places éliminées, dans l'ordre où elles sont sorties.
 */
internal data class SimonTurns(
    val players: Int,
    val sequence: List<Int>,
    val eliminated: List<Int> = emptyList(),
    val current: Int = 0
) {
    /** Les places encore en jeu. */
    val alive: List<Int> get() = (0 until players).filter { it !in eliminated }

    /** La partie est finie quand il ne reste qu'un joueur en jeu. */
    val isOver: Boolean get() = alive.size <= 1

    /** La place du vainqueur une fois la partie finie, sinon `null`. */
    val winner: Int? get() = if (isOver) alive.singleOrNull() else null

    /** La plus longue suite reproduite en entier jusqu'ici (0 tant que personne n'a réussi la première couleur). */
    val reached: Int get() = sequence.size - 1

    /** Le joueur courant a reproduit la suite : elle s'allonge d'une couleur et c'est au joueur suivant encore en jeu. */
    fun succeed(random: Random = Random.Default): SimonTurns =
        if (isOver) this
        else copy(sequence = sequence + random.nextInt(Simon.PADS), current = nextAlive(current, eliminated))

    /** Le joueur courant s'est trompé : il est éliminé et le suivant retente la même suite. */
    fun fail(): SimonTurns {
        if (isOver) return this
        val out = eliminated + current
        return copy(eliminated = out, current = nextAlive(current, out))
    }

    /** Le classement : le vainqueur, puis les éliminés du dernier sorti au premier sorti. */
    fun ranking(): List<Int> = listOfNotNull(winner) + eliminated.reversed()

    /** Une nouvelle partie avec les mêmes joueurs. */
    fun newGame(random: Random = Random.Default): SimonTurns = start(players, random)

    private fun nextAlive(from: Int, out: List<Int>): Int {
        for (step in 1..players) {
            val seat = (from + step) % players
            if (seat !in out) return seat
        }
        return from
    }

    companion object {
        /** Une partie neuve : une couleur au hasard, le premier joueur commence. */
        fun start(players: Int, random: Random = Random.Default) = SimonTurns(players, listOf(random.nextInt(Simon.PADS)))
    }
}
