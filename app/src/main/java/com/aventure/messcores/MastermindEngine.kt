package com.aventure.messcores

/** Identifiant du jeu dans la liste : il a son propre écran, comme le poker (voir [MastermindScreen]). */
internal const val MASTERMIND_ID = "builtin_mastermind"

/** Réglages d'une partie de Mastermind : classique = 4 pions, 6 couleurs, 10 essais. */
data class MastermindSettings(
    val codeLength: Int = 4,
    val colorCount: Int = 6,
    val maxAttempts: Int = 10
) {
    companion object {
        val LENGTH_RANGE = 3..6
        val COLOR_RANGE = 4..8
        val ATTEMPTS_RANGE = 6..12
    }

    /** Les mêmes réglages ramenés dans les limites permises. */
    fun coerced() = MastermindSettings(
        codeLength = codeLength.coerceIn(LENGTH_RANGE),
        colorCount = colorCount.coerceIn(COLOR_RANGE),
        maxAttempts = maxAttempts.coerceIn(ATTEMPTS_RANGE)
    )
}

/** Réponse à un essai : pions de la bonne couleur au bon endroit, et de la bonne couleur au mauvais endroit. */
data class MastermindFeedback(val wellPlaced: Int, val misplaced: Int)

/** Un essai (une couleur par pion, de 0 à [MastermindSettings.colorCount] − 1) et la réponse qu'il a reçue. */
data class MastermindGuess(val pegs: List<Int>, val feedback: MastermindFeedback)

enum class MastermindPhase {
    /** Chaque joueur saisit sa suite secrète, l'un après l'autre. */
    SECRETS,

    /** Les suites sont cherchées l'une après l'autre. */
    PLAYING,

    /** Toutes les suites ont été cherchées : moyennes et classement. */
    RESULTS
}

/**
 * Une partie de Mastermind à plusieurs : chaque joueur crée une suite secrète, puis tous les autres
 * la cherchent ensemble (un essai à la fois, d'un commun accord), suite après suite.
 *
 * @param secrets suite de chaque joueur (null tant qu'il ne l'a pas saisie).
 * @param guesses essais faits pour chaque suite, dans l'ordre.
 * @param secretTurn joueur en train de saisir sa suite (phase [MastermindPhase.SECRETS]).
 * @param target joueur dont on cherche la suite (phase [MastermindPhase.PLAYING]).
 */
data class MastermindState(
    val players: List<String>,
    val settings: MastermindSettings,
    val phase: MastermindPhase = MastermindPhase.SECRETS,
    val secrets: List<List<Int>?> = List(players.size) { null },
    val guesses: List<List<MastermindGuess>> = List(players.size) { emptyList() },
    val secretTurn: Int = 0,
    val target: Int = 0
)

/** Règles du Mastermind : réponse à un essai, déroulement de la partie, moyennes et classement. */
object Mastermind {

    /**
     * Réponse à l'essai [guess] pour la suite [secret] : les pions bien placés, puis parmi les autres
     * ceux dont la couleur se trouve ailleurs dans la suite (chaque pion de la suite ne compte qu'une fois).
     */
    fun feedback(secret: List<Int>, guess: List<Int>): MastermindFeedback {
        var well = 0
        val secretLeft = HashMap<Int, Int>()
        val guessLeft = HashMap<Int, Int>()
        for (i in secret.indices) {
            if (secret[i] == guess.getOrNull(i)) {
                well++
            } else {
                secretLeft[secret[i]] = (secretLeft[secret[i]] ?: 0) + 1
                val g = guess.getOrNull(i) ?: continue
                guessLeft[g] = (guessLeft[g] ?: 0) + 1
            }
        }
        val misplaced = guessLeft.entries.sumOf { (color, count) -> minOf(count, secretLeft[color] ?: 0) }
        return MastermindFeedback(well, misplaced)
    }

    /** Vrai si [pegs] a la bonne longueur et que chaque couleur existe dans ces [settings]. */
    fun isValid(pegs: List<Int>, settings: MastermindSettings): Boolean =
        pegs.size == settings.codeLength && pegs.all { it in 0 until settings.colorCount }

    fun newGame(players: List<String>, settings: MastermindSettings): MastermindState =
        MastermindState(players = players, settings = settings.coerced())

    /** Enregistre la suite secrète du joueur dont c'est le tour ; null si elle est invalide ou si ce n'est plus la phase. */
    fun withSecret(state: MastermindState, pegs: List<Int>): MastermindState? {
        if (state.phase != MastermindPhase.SECRETS || !isValid(pegs, state.settings)) return null
        val secrets = state.secrets.toMutableList().also { it[state.secretTurn] = pegs.toList() }
        val last = state.secretTurn == state.players.lastIndex
        return state.copy(
            secrets = secrets,
            phase = if (last) MastermindPhase.PLAYING else MastermindPhase.SECRETS,
            secretTurn = if (last) state.secretTurn else state.secretTurn + 1,
            target = 0
        )
    }

    /** Ajoute l'essai [pegs] à la suite cherchée ; null s'il est invalide ou si la suite est déjà trouvée ou épuisée. */
    fun withGuess(state: MastermindState, pegs: List<Int>): MastermindState? {
        if (state.phase != MastermindPhase.PLAYING || !isValid(pegs, state.settings)) return null
        if (isOver(state, state.target)) return null
        val secret = state.secrets.getOrNull(state.target) ?: return null
        val guess = MastermindGuess(pegs.toList(), feedback(secret, pegs))
        val guesses = state.guesses.toMutableList().also { it[state.target] = it[state.target] + guess }
        return state.copy(guesses = guesses)
    }

    /** Vrai si le dernier essai pour la suite de [target] a tous ses pions bien placés. */
    fun isSolved(state: MastermindState, target: Int): Boolean =
        state.guesses.getOrNull(target)?.lastOrNull()?.feedback?.wellPlaced == state.settings.codeLength

    /** Vrai si la suite de [target] n'a pas été trouvée et que tous les essais sont utilisés. */
    fun isFailed(state: MastermindState, target: Int): Boolean =
        !isSolved(state, target) && (state.guesses.getOrNull(target)?.size ?: 0) >= state.settings.maxAttempts

    fun isOver(state: MastermindState, target: Int): Boolean = isSolved(state, target) || isFailed(state, target)

    /** Passe à la suite suivante, ou aux résultats après la dernière ; sans effet tant que la suite en cours n'est pas finie. */
    fun nextTarget(state: MastermindState): MastermindState {
        if (state.phase != MastermindPhase.PLAYING || !isOver(state, state.target)) return state
        return if (state.target == state.players.lastIndex) {
            state.copy(phase = MastermindPhase.RESULTS)
        } else {
            state.copy(target = state.target + 1)
        }
    }

    /**
     * Essais comptés pour la suite de [target] : ceux qu'il a fallu pour la trouver ; une suite non trouvée
     * compte un essai de plus que la limite. Null tant que la suite est encore en cours de recherche.
     */
    fun attempts(state: MastermindState, target: Int): Int? = when {
        isSolved(state, target) -> state.guesses[target].size
        isFailed(state, target) -> state.settings.maxAttempts + 1
        else -> null
    }

    /** Somme des essais des suites que [player] a cherchées : toutes sauf la sienne. */
    fun attemptsSum(state: MastermindState, player: Int): Int =
        state.players.indices.filter { it != player }.sumOf { attempts(state, it) ?: 0 }

    /** Moyenne des essais des suites que [player] a cherchées ; la plus basse gagne. */
    fun average(state: MastermindState, player: Int): Double {
        val searched = state.players.size - 1
        return if (searched <= 0) 0.0 else attemptsSum(state, player).toDouble() / searched
    }

    /** Rang (1 = premier) de chaque joueur : la plus petite moyenne gagne, les égalités partagent le rang. */
    fun ranks(state: MastermindState): List<Int> {
        // Tous les joueurs ont cherché autant de suites : comparer les sommes revient à comparer les moyennes, sans arrondi.
        val sums = state.players.indices.map { attemptsSum(state, it) }
        return sums.map { sum -> 1 + sums.count { it < sum } }
    }

    fun winners(state: MastermindState): List<Int> =
        ranks(state).withIndex().filter { it.value == 1 }.map { it.index }
}
