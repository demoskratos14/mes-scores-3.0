package com.aventure.messcores

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/**
 * Dés d'un jeu : combien, combien de lancers par tour, et si l'on peut en garder.
 *
 * @param maxRolls nombre de lancers par tour (Yams : 3) ; null si le nombre de lancers est libre.
 * @param allowHold vrai si l'on peut garder des dés d'un lancer à l'autre. Sans objet avec un seul dé.
 * @param tints couleur ARGB de chaque dé (Qwixx) ; vide ou null = dé blanc.
 */
data class DiceSetup(
    val diceCount: Int,
    val maxRolls: Int? = null,
    val allowHold: Boolean = true,
    val tints: List<Long?> = emptyList()
)

/** Les dés à proposer pour chaque jeu de la rubrique « Dés » (voir [diceGames]). */
internal object DiceSetups {

    // Qwixx : deux dés blancs, puis un dé rouge, jaune, vert et bleu.
    private val qwixxTints: List<Long?> = listOf(null, null, 0xFFEF5350, 0xFFFFEE58, 0xFF66BB6A, 0xFF42A5F5)

    /** Dés du jeu [gameId], ou null s'il n'y a rien à lancer dans l'appli (Zombie Dice a ses propres dés). */
    fun forGame(gameId: String): DiceSetup? = when (gameId) {
        "builtin_yams" -> DiceSetup(diceCount = 5, maxRolls = 3)
        "builtin_421" -> DiceSetup(diceCount = 3, maxRolls = 3)
        // King of Tokyo demande son plateau : pas de jeu jouable, seulement des dés génériques sur sa feuille (lancers libres).
        "builtin_kot" -> DiceSetup(diceCount = 6)
        "builtin_mexicain" -> DiceSetup(diceCount = 2, maxRolls = 3)
        "builtin_cdc" -> DiceSetup(diceCount = 3)
        "builtin_dixmille" -> DiceSetup(diceCount = 6)
        "builtin_cochon" -> DiceSetup(diceCount = 1)
        "builtin_shutbox" -> DiceSetup(diceCount = 2, allowHold = false)
        "builtin_bunco" -> DiceSetup(diceCount = 3, allowHold = false)
        "builtin_qwixx" -> DiceSetup(diceCount = 6, allowHold = false, tints = qwixxTints)
        else -> null
    }
}

/**
 * Un tour de lancers : les valeurs des dés, ceux que l'on garde et le nombre de lancers déjà faits.
 * [rollDie] tire un dé de 1 à 6 (remplaçable dans les tests).
 */
class DiceTurn(
    val setup: DiceSetup,
    private val rollDie: () -> Int = { Random.nextInt(1, 7) }
) {
    /** Valeur de chaque dé ; vide tant que rien n'a été lancé. */
    var values by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Index des dés gardés : ils ne sont pas relancés. */
    var held by mutableStateOf<Set<Int>>(emptySet())
        private set

    var rollsUsed by mutableIntStateOf(0)
        private set

    val hasRolled: Boolean get() = values.isNotEmpty()

    /**
     * Nombre de lancers autorisés à ce tour quand il est plus petit que celui du jeu : au 421, le premier
     * joueur de la manche fixe le nombre de lancers de tous les autres. Null : le nombre du jeu s'applique.
     */
    var rollLimit by mutableStateOf<Int?>(null)

    /** Lancers restants dans ce tour ; null si leur nombre est libre. */
    val rollsLeft: Int? get() = (rollLimit ?: setup.maxRolls)?.let { (it - rollsUsed).coerceAtLeast(0) }

    /** Vrai s'il reste un lancer à faire et au moins un dé à lancer. */
    val canRoll: Boolean get() = (rollsLeft ?: 1) > 0 && (!hasRolled || held.size < setup.diceCount)

    /** Vrai si l'on peut garder des dés : après un premier lancer, avec plusieurs dés et un lancer à venir. */
    val canHold: Boolean get() = setup.allowHold && setup.diceCount > 1 && hasRolled && (rollsLeft ?: 1) > 0

    /** Lance tous les dés au premier lancer, puis seulement ceux qui ne sont pas gardés. */
    fun roll() {
        if (!canRoll) return
        values = if (!hasRolled) {
            List(setup.diceCount) { rollDie() }
        } else {
            values.mapIndexed { index, value -> if (index in held) value else rollDie() }
        }
        rollsUsed += 1
    }

    /** Garde le dé [index] (ou le remet en jeu s'il était gardé). */
    fun toggleHold(index: Int) {
        if (!canHold || index !in 0 until setup.diceCount) return
        held = if (index in held) held - index else held + index
    }

    /** Nouveau tour : plus aucun dé lancé ni gardé. */
    fun reset() {
        values = emptyList()
        held = emptySet()
        rollsUsed = 0
    }

    /** État sous forme de liste d'entiers, pour survivre à une rotation de l'écran (voir [restoreFrom]). */
    fun snapshot(): List<Int> = listOf(rollsUsed, values.size) + values + held.sorted()

    /** Rétablit l'état produit par [snapshot] ; une liste invalide remet le tour à zéro. */
    fun restoreFrom(data: List<Int>) {
        reset()
        val count = data.getOrNull(1) ?: return
        if (count < 0 || (count != setup.diceCount && count != 0) || data.size < 2 + count) return
        rollsUsed = data[0]
        values = data.subList(2, 2 + count).toList()
        held = data.drop(2 + count).filter { it in 0 until setup.diceCount }.toSet()
    }
}

/** Points d'un lancer de dés au Yams, ligne par ligne de la feuille ([ScoreSheets] « yams »). */
internal object YamsScoring {

    /** Points que rapporte [dice] dans la ligne [rowId] de la feuille ; 0 si la combinaison n'y est pas. */
    fun score(rowId: String, dice: List<Int>): Int {
        val counts = IntArray(7)
        dice.forEach { if (it in 1..6) counts[it]++ }
        val sum = dice.sum()
        return when (rowId) {
            "aces" -> counts[1]
            "twos" -> counts[2] * 2
            "threes" -> counts[3] * 3
            "fours" -> counts[4] * 4
            "fives" -> counts[5] * 5
            "sixes" -> counts[6] * 6
            "three_kind" -> if (counts.any { it >= 3 }) sum else 0
            "four_kind" -> if (counts.any { it >= 4 }) sum else 0
            "full_house" -> if (counts.count { it == 3 } == 1 && counts.count { it == 2 } == 1) 25 else 0
            "small_straight" -> if (hasRun(counts, 4)) 30 else 0
            "large_straight" -> if (hasRun(counts, 5)) 40 else 0
            "yams" -> if (counts.any { it == 5 }) 50 else 0
            "chance" -> sum
            else -> 0
        }
    }

    /** Vrai si [counts] contient [length] valeurs consécutives (1 à 6). */
    private fun hasRun(counts: IntArray, length: Int): Boolean =
        (1..(6 - length + 1)).any { start -> (start until start + length).all { counts[it] > 0 } }
}

/** Jetons déplacés à la fin d'une manche : [fromPot] vrai s'ils viennent du milieu, faux s'ils viennent du gagnant. */
internal data class Settlement421(val tokens: Int, val fromPot: Boolean)

/** Une combinaison du 421 et le nombre de jetons qu'elle vaut. */
internal data class Combo421(val points: Int, @StringRes val label: Int)

/** Comment un jeu de dés classe les mains d'une manche : nombre de dés, force d'une main, et points qu'elle rapporte. */
internal interface HandRanking {
    val diceCount: Int

    /** Force d'une main, pour savoir laquelle gagne la manche. */
    fun strength(dice: List<Int>): Int

    /** Points (jetons au 421) que rapporte une main. */
    fun points(dice: List<Int>): Int
}

/** Le classement du 421 (voir [Dice421]). */
internal object Dice421Ranking : HandRanking {
    override val diceCount: Int = 3
    override fun strength(dice: List<Int>): Int = Dice421.strength(dice)
    override fun points(dice: List<Int>): Int = Dice421.combo(dice).points
}

/** Le classement du Mexicain (voir [DiceMexicain]). */
internal object MexicainRanking : HandRanking {
    override val diceCount: Int = 2
    override fun strength(dice: List<Int>): Int = DiceMexicain.strength(dice)
    override fun points(dice: List<Int>): Int = 1
}

/** Valeur et force des jeux du 421 : voir la fiche de règles du jeu (« Valeur des jeux »). */
internal object Dice421 {

    /** Nombre de jetons de la partie : 21, tous au milieu au départ (la « charge »). */
    const val TOKENS = 21

    /**
     * Force d'un jeu, pour savoir lequel gagne la manche : 421, puis 111, puis les as doubles (611 … 211),
     * les brelans (666 … 222), les suites (456 … 123) et enfin les autres jeux, du plus haut au plus bas.
     */
    fun strength(dice: List<Int>): Int {
        if (dice.size != 3) return 0
        val s = dice.sorted()
        return when {
            s == listOf(1, 2, 4) -> 1000
            s == listOf(1, 1, 1) -> 900
            s.count { it == 1 } == 2 -> 800 + s.last()
            s[0] == s[2] -> 700 + s[0]
            s[1] == s[0] + 1 && s[2] == s[1] + 1 -> 600 + s[0]
            else -> (s[2] - 1) * 36 + (s[1] - 1) * 6 + (s[0] - 1)
        }
    }

    /** Jetons restant au milieu quand les joueurs en tiennent [holdings] (la charge, tant qu'il en reste). */
    fun pot(holdings: List<Int>): Int = (TOKENS - holdings.sum()).coerceAtLeast(0)

    /** Joueurs qui jouent la manche : tous pendant la charge ; pendant la décharge, ceux qui ont encore des jetons. */
    fun activePlayers(holdings: List<Int>): List<Int> =
        if (pot(holdings) > 0) holdings.indices.toList() else holdings.indices.filter { holdings[it] > 0 }

    /** Le joueur qui perd la partie : en fin de décharge, celui qui a pris tous les jetons ; sinon null. */
    fun gameLoser(holdings: List<Int>): Int? =
        if (pot(holdings) == 0 && holdings.count { it > 0 } == 1) holdings.indexOfFirst { it > 0 } else null

    /**
     * Jetons que le perdant d'une manche reçoit quand le gagnant a un jeu de [points] : pris au milieu pendant
     * la charge (au plus ce qu'il y reste), puis au gagnant pendant la décharge (au plus ce qu'il a).
     */
    fun settle(points: Int, winner: Int, holdings: List<Int>): Settlement421 {
        val pot = pot(holdings)
        return if (pot > 0) Settlement421(minOf(points, pot), fromPot = true)
        else Settlement421(minOf(points, holdings.getOrElse(winner) { 0 }), fromPot = false)
    }

    fun combo(dice: List<Int>): Combo421 {
        if (dice.size != 3) return Combo421(1, R.string.dice_421_name_other)
        val s = dice.sorted()
        return when {
            s == listOf(1, 2, 4) -> Combo421(10, R.string.dice_421_name_421)
            s == listOf(1, 1, 1) -> Combo421(7, R.string.dice_421_name_111)
            // Deux as et un autre dé : autant de jetons que ce troisième dé.
            s.count { it == 1 } == 2 -> Combo421(s.last(), R.string.dice_421_name_aces)
            s[0] == s[2] -> Combo421(s[0], R.string.dice_421_name_triple)
            s[1] == s[0] + 1 && s[2] == s[1] + 1 -> Combo421(2, R.string.dice_421_name_straight)
            else -> Combo421(1, R.string.dice_421_name_other)
        }
    }
}

/** Qwixx : les deux dés blancs sont les dés 0 et 1, puis un dé par couleur. */
internal object QwixxDice {

    fun whiteSum(dice: List<Int>): Int = dice.getOrElse(0) { 0 } + dice.getOrElse(1) { 0 }

    /** Les deux totaux possibles pour la couleur du dé [colorDie] : un dé blanc + le dé de couleur. */
    fun colorOptions(dice: List<Int>, colorDie: Int): Pair<Int, Int> {
        val color = dice.getOrElse(colorDie) { 0 }
        return dice.getOrElse(0) { 0 } + color to dice.getOrElse(1) { 0 } + color
    }
}

/**
 * Le Mexicain : deux dés, trois lancers au plus ; le plus mauvais jeu de la manche fait perdre une vie. Le compteur
 * de chaque joueur compte les vies perdues ; à [LIVES] vies perdues, le joueur est éliminé.
 */
internal object DiceMexicain {

    /** Nombre de vies de chaque joueur au départ. */
    const val LIVES = 5

    /** Le Mexico : le 2 et le 1, le meilleur jeu. */
    private const val MEXICO = 1000

    /**
     * Force d'un jeu : le Mexico (2 et 1), puis les doubles (6-6 … 1-1), puis les autres jeux en lisant d'abord
     * le dé le plus fort (6-5 bat 6-4, qui bat 5-4, etc.).
     */
    fun strength(dice: List<Int>): Int {
        if (dice.size != 2) return 0
        val high = maxOf(dice[0], dice[1])
        val low = minOf(dice[0], dice[1])
        return when {
            high == 2 && low == 1 -> MEXICO
            high == low -> 900 + high
            else -> high * 10 + low
        }
    }

    fun isMexico(dice: List<Int>): Boolean = strength(dice) == MEXICO

    /** Vrai pour un double (la valeur du dé est alors [doubleValue]). */
    fun isDouble(dice: List<Int>): Boolean = dice.size == 2 && dice[0] == dice[1]

    fun doubleValue(dice: List<Int>): Int = dice.firstOrNull() ?: 0

    /** Vies restantes de chacun, d'après les vies perdues ([lost], le compteur de chaque joueur). */
    fun livesLeft(lost: List<Int>): List<Int> = lost.map { (LIVES - it).coerceAtLeast(0) }

    /** Joueurs encore en jeu : ceux qui ont encore au moins une vie. */
    fun activePlayers(lost: List<Int>): List<Int> = lost.indices.filter { lost[it] < LIVES }

    /** Le vainqueur : le dernier joueur qui a encore des vies ; null tant qu'ils sont plusieurs. */
    fun winner(lost: List<Int>): Int? = activePlayers(lost).singleOrNull()
}
