package com.aventure.messcores

import java.text.Normalizer
import java.util.Locale
import kotlin.random.Random

/** Identifiant du jeu dans la liste : il a son propre écran, comme le touché coulé (voir [HangmanScreen]). */
internal const val HANGMAN_ID = "builtin_hangman"

/** Réglages d'une partie de Pendu : le nombre d'erreurs permises avant que le bonhomme soit pendu. */
data class HangmanSettings(val maxMistakes: Int = 7) {
    companion object {
        val MISTAKES_RANGE = 4..10
    }

    /** Les mêmes réglages ramenés dans les limites permises. */
    fun coerced() = HangmanSettings(maxMistakes.coerceIn(MISTAKES_RANGE))
}

/**
 * Une lettre proposée et la réponse reçue : les places où elle se trouve dans le mot (vide : la lettre n'y est pas,
 * c'est une erreur). Les places comptent chaque caractère du mot, séparateurs compris (voir [Hangman.normalize]).
 */
data class HangmanGuess(val letter: Char, val positions: List<Int>) {
    val isMistake: Boolean get() = positions.isEmpty()
}

/** Où en est la manche : le mot est trouvé, le bonhomme est pendu, ou on cherche encore. */
enum class HangmanOutcome { PLAYING, WON, LOST }

/**
 * Une partie de Pendu à deux sur le même téléphone (voir [HangmanViewModel]). Chaque manche, l'un choisit un mot
 * ([setter]) et l'autre ([guesser]) en devine les lettres ; les rôles s'échangent à la manche suivante.
 * Chacun sur son téléphone, aucun téléphone ne connaît le mot de l'autre : voir [HangmanOnline].
 *
 * @param word le mot de la manche, normalisé (null tant que celui qui le choisit ne l'a pas saisi).
 * @param guesses les lettres proposées, dans l'ordre, avec la réponse reçue.
 * @param wins manches gagnées par chaque joueur depuis le début de la séance.
 */
data class HangmanGame(
    val players: List<String>,
    val settings: HangmanSettings,
    val setter: Int,
    val word: String? = null,
    val guesses: List<HangmanGuess> = emptyList(),
    val wins: List<Int> = listOf(0, 0),
    val round: Int = 1
) {
    val guesser: Int get() = 1 - setter

    val outcome: HangmanOutcome
        get() = word?.let { Hangman.outcome(Hangman.shape(it), settings, guesses) } ?: HangmanOutcome.PLAYING

    val isOver: Boolean get() = outcome != HangmanOutcome.PLAYING

    /** Joueur qui a gagné la manche (le deviné s'il a trouvé le mot, celui qui l'a choisi si le bonhomme est pendu), ou -1. */
    val roundWinner: Int
        get() = when (outcome) {
            HangmanOutcome.WON -> guesser
            HangmanOutcome.LOST -> setter
            HangmanOutcome.PLAYING -> -1
        }
}

/**
 * Règles du Pendu. Un mot est une suite de lettres de A à Z, éventuellement séparées par des espaces, tirets ou
 * apostrophes (« pomme de terre », « porte-clés ») : les séparateurs sont affichés dès le départ, seules les lettres
 * se devinent. Les accents ne comptent pas (« é » se devine avec le E).
 */
object Hangman {
    const val MIN_LETTERS = 2
    const val MAX_LETTERS = 20
    const val MAX_LENGTH = 30

    /** Caractère affiché à la place d'une lettre pas encore trouvée. */
    const val BLANK = '_'

    val ALPHABET: List<Char> = ('A'..'Z').toList()

    private val SEPARATORS = setOf(' ', '-', '\'')

    /** Nombre de pièces du dessin du pendu : socle, poteau, traverse, corde, tête, corps, deux bras, deux jambes. */
    const val DRAWING_PARTS = 10

    fun isLetter(c: Char): Boolean = c in 'A'..'Z'

    /**
     * Le mot tel qu'on le joue : en majuscules, sans accents (« œ » devient « OE »), les apostrophes typographiques
     * ramenées à l'apostrophe droite, les blancs réduits à une espace, sans blanc autour. Les autres caractères sont
     * laissés tels quels : [isValidWord] les refuse.
     */
    fun normalize(raw: String): String {
        val upper = raw.trim().uppercase(Locale.ROOT)
            .replace("Œ", "OE").replace("Æ", "AE")
            .replace('’', '\'').replace('‘', '\'').replace('`', '\'')
            .replace(Regex("[\\u2010-\\u2015]"), "-")
        val stripped = Normalizer.normalize(upper, Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        return stripped.replace(Regex("\\s+"), " ")
    }

    /** Nombre de lettres à deviner dans [word] (les séparateurs ne comptent pas). */
    fun letterCount(word: String): Int = word.count { isLetter(it) }

    /** Vrai si [word] (déjà normalisé) est un mot jouable : lettres et séparateurs, qui commence et finit par une lettre. */
    fun isValidWord(word: String): Boolean {
        if (word.length > MAX_LENGTH || letterCount(word) !in MIN_LETTERS..MAX_LETTERS) return false
        if (!word.all { isLetter(it) || it in SEPARATORS }) return false
        if (!isLetter(word.first()) || !isLetter(word.last())) return false
        // Pas deux séparateurs de suite : « A  B » ou « A-'B » n'ont pas de sens.
        return word.zipWithNext().none { (a, b) -> a in SEPARATORS && b in SEPARATORS }
    }

    /** Le mot saisi par un joueur, normalisé, ou null s'il n'est pas jouable. */
    fun parseWord(raw: String): String? = normalize(raw).takeIf { isValidWord(it) }

    /** Le mot sans ses lettres : « POMME DE-TERRE » donne « _____ __-_____ ». C'est tout ce que voit celui qui devine. */
    fun shape(word: String): String = word.map { if (isLetter(it)) BLANK else it }.joinToString("")

    /** Vrai si [shape] est la forme d'un mot jouable : des blancs et des séparateurs, rien d'autre. */
    fun isValidShape(shape: String): Boolean =
        shape.length <= MAX_LENGTH && shape.all { it == BLANK || it in SEPARATORS } &&
            isValidWord(shape.map { if (it == BLANK) 'A' else it }.joinToString(""))

    /** Les places de [letter] dans [word]. */
    fun positionsOf(word: String, letter: Char): List<Int> = word.indices.filter { word[it] == letter }

    /** Les places que [guesses] a trouvées, sous forme d'ensemble. */
    private fun foundPositions(guesses: List<HangmanGuess>): Set<Int> = guesses.flatMap { it.positions }.toSet()

    /** Le mot tel que le voit celui qui devine : les lettres trouvées, des blancs pour les autres. */
    fun masked(shape: String, guesses: List<HangmanGuess>): String {
        val letterAt = HashMap<Int, Char>()
        guesses.forEach { guess -> guess.positions.forEach { letterAt[it] = guess.letter } }
        return shape.indices.map { i -> if (shape[i] == BLANK) letterAt[i] ?: BLANK else shape[i] }.joinToString("")
    }

    fun mistakes(guesses: List<HangmanGuess>): Int = guesses.count { it.isMistake }

    fun mistakesLeft(settings: HangmanSettings, guesses: List<HangmanGuess>): Int =
        (settings.maxMistakes - mistakes(guesses)).coerceAtLeast(0)

    /** Vrai si toutes les lettres de [shape] ont été trouvées. */
    fun isSolved(shape: String, guesses: List<HangmanGuess>): Boolean {
        val found = foundPositions(guesses)
        return shape.indices.all { shape[it] != BLANK || it in found }
    }

    fun outcome(shape: String, settings: HangmanSettings, guesses: List<HangmanGuess>): HangmanOutcome = when {
        isSolved(shape, guesses) -> HangmanOutcome.WON
        mistakes(guesses) >= settings.maxMistakes -> HangmanOutcome.LOST
        else -> HangmanOutcome.PLAYING
    }

    /** Nombre de pièces du dessin à afficher pour [mistakes] erreurs sur [settings] permises (le dessin est complet à la dernière). */
    fun drawingParts(settings: HangmanSettings, mistakes: Int): Int {
        if (mistakes <= 0) return 0
        if (mistakes >= settings.maxMistakes) return DRAWING_PARTS
        return (mistakes * DRAWING_PARTS / settings.maxMistakes).coerceIn(1, DRAWING_PARTS - 1)
    }

    /**
     * La réponse à la lettre [letter] pour le mot [word], ou null si elle n'est pas permise : pas une lettre de A à Z,
     * déjà proposée, ou manche finie.
     */
    fun resolve(word: String, settings: HangmanSettings, guesses: List<HangmanGuess>, letter: Char): HangmanGuess? {
        if (!isLetter(letter) || guesses.any { it.letter == letter }) return null
        if (outcome(shape(word), settings, guesses) != HangmanOutcome.PLAYING) return null
        return HangmanGuess(letter, positionsOf(word, letter))
    }

    /**
     * Vérifie, une fois le mot dévoilé, que les réponses reçues étaient honnêtes : le mot est jouable, il a la forme
     * annoncée au départ ([shape]) et chaque réponse donne bien les places de sa lettre dans ce mot.
     */
    fun isConsistent(word: String, shape: String, guesses: List<HangmanGuess>): Boolean {
        if (!isValidWord(word) || shape(word) != shape) return false
        if (guesses.map { it.letter }.toSet().size != guesses.size) return false
        return guesses.all { it.positions == positionsOf(word, it.letter) }
    }

    /** Une nouvelle séance : [firstSetter] (0 ou 1) choisit le premier mot. */
    fun newGame(players: List<String>, settings: HangmanSettings, firstSetter: Int): HangmanGame =
        HangmanGame(players = players.take(2), settings = settings, setter = firstSetter.coerceIn(0, 1))

    /** La partie avec le mot saisi par celui qui le choisit, ou null si le mot n'est pas jouable ou déjà choisi. */
    fun withWord(game: HangmanGame, raw: String): HangmanGame? {
        if (game.word != null) return null
        val word = parseWord(raw) ?: return null
        return game.copy(word = word, guesses = emptyList())
    }

    /** La partie après la lettre [letter], ou null si elle n'est pas permise. Compte la manche gagnée à la fin. */
    fun guess(game: HangmanGame, letter: Char): HangmanGame? {
        val word = game.word ?: return null
        val answer = resolve(word, game.settings, game.guesses, letter) ?: return null
        val next = game.copy(guesses = game.guesses + answer)
        val winner = next.roundWinner
        return if (winner >= 0) next.copy(wins = next.wins.mapIndexed { player, n -> if (player == winner) n + 1 else n }) else next
    }

    /** La manche suivante, les rôles échangés, ou null tant que la manche n'est pas finie. */
    fun nextRound(game: HangmanGame): HangmanGame? =
        if (game.isOver) game.copy(setter = game.guesser, word = null, guesses = emptyList(), round = game.round + 1) else null

    /** Tire au sort qui choisit le premier mot. */
    fun randomFirst(random: Random = Random.Default): Int = random.nextInt(2)
}
