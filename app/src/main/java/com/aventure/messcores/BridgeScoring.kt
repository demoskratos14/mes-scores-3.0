package com.aventure.messcores

/** Couleurs d'un contrat de bridge, dans l'ordre des enchères (de la plus faible à la plus forte). */
object BridgeSuit {
    const val CLUBS = 0
    const val DIAMONDS = 1
    const val HEARTS = 2
    const val SPADES = 3
    const val NO_TRUMP = 4
}

/** Saisie d'une donne de bridge, conservée dans [TeamRound.contractInput] pour pouvoir la corriger. */
data class BridgeInput(
    /** Index du joueur (du camp) qui a déclaré le contrat. */
    val declarer: Int,
    /** Niveau du contrat, de 1 à 7 : il faut réaliser 6 + niveau plis. */
    val level: Int,
    val suit: Int,
    /** 0 pas de contre, 1 contré, 2 surcontré. */
    val doubling: Int,
    val vulnerable: Boolean,
    /** Plis réalisés par le déclarant, de 0 à 13. */
    val tricks: Int
) {
    fun encode(): List<Int> = listOf(declarer, level, suit, doubling, if (vulnerable) 1 else 0, tricks)

    companion object {
        /** Relit une saisie enregistrée ; null si elle est incomplète (ancien format, fichier abîmé). */
        fun decode(values: List<Int>): BridgeInput? =
            if (values.size < 6) null
            else BridgeInput(values[0], values[1], values[2], values[3], values[4] == 1, values[5])
    }
}

/**
 * Résultat du calcul d'une donne de bridge (marque de la donne, comme au bridge de tournoi).
 *
 * @param made vrai si le contrat est rempli.
 * @param difference plis réalisés moins plis nécessaires (négatif si le contrat chute).
 * @param contractPoints points des plis du contrat (déjà multipliés si contré ou surcontré).
 * @param overtrickPoints points des plis en plus.
 * @param doubledBonus bonus de réussite d'un contrat contré (50) ou surcontré (100).
 * @param gameBonus prime de partielle (50) ou de manche (300, 500 vulnérable).
 * @param slamBonus prime de petit ou de grand chelem.
 * @param penalty points de pénalité marqués par les défenseurs si le contrat chute.
 * @param score score de la donne vu du déclarant : positif s'il remplit son contrat, négatif sinon
 *   (les défenseurs marquent alors l'opposé).
 */
data class BridgeResult(
    val made: Boolean,
    val difference: Int,
    val contractPoints: Int,
    val overtrickPoints: Int,
    val doubledBonus: Int,
    val gameBonus: Int,
    val slamBonus: Int,
    val penalty: Int,
    val score: Int
)

/** Plis nécessaires pour remplir un contrat de niveau [level]. */
fun bridgeTricksNeeded(level: Int): Int = 6 + level

/** Pénalité due par le déclarant pour [down] plis de chute (1 ou plus). */
private fun bridgePenalty(down: Int, doubling: Int, vulnerable: Boolean): Int {
    if (doubling == 0) return down * if (vulnerable) 100 else 50
    val doubled = if (vulnerable) {
        200 + 300 * (down - 1)
    } else {
        (1..down).sumOf { trick ->
            when {
                trick == 1 -> 100
                trick <= 3 -> 200
                else -> 300
            }
        }
    }
    return if (doubling == 2) doubled * 2 else doubled
}

/**
 * Calcule la marque d'une donne de bridge. Fonction pure (aucun état, aucune interface), pour
 * pouvoir la tester facilement.
 *
 * @param level niveau du contrat (1 à 7).
 * @param suit couleur du contrat (voir [BridgeSuit]).
 * @param doubling 0 pas de contre, 1 contré, 2 surcontré.
 * @param vulnerable vrai si le camp déclarant est vulnérable.
 * @param tricks plis réalisés par le déclarant (0 à 13).
 */
fun computeBridgeContract(level: Int, suit: Int, doubling: Int, vulnerable: Boolean, tricks: Int): BridgeResult {
    val difference = tricks - bridgeTricksNeeded(level)
    if (difference < 0) {
        val penalty = bridgePenalty(-difference, doubling, vulnerable)
        return BridgeResult(false, difference, 0, 0, 0, 0, 0, penalty, -penalty)
    }
    val factor = when (doubling) {
        1 -> 2
        2 -> 4
        else -> 1
    }
    val minor = suit == BridgeSuit.CLUBS || suit == BridgeSuit.DIAMONDS
    // Sans-atout : 40 pour le premier pli, 30 pour les suivants. Majeures : 30 le pli. Mineures : 20 le pli.
    val contractPoints = factor * when {
        suit == BridgeSuit.NO_TRUMP -> 40 + 30 * (level - 1)
        minor -> 20 * level
        else -> 30 * level
    }
    val overtrickPoints = difference * when (doubling) {
        0 -> if (minor) 20 else 30
        1 -> if (vulnerable) 200 else 100
        else -> if (vulnerable) 400 else 200
    }
    val doubledBonus = when (doubling) {
        1 -> 50
        2 -> 100
        else -> 0
    }
    val gameBonus = when {
        contractPoints < 100 -> 50
        vulnerable -> 500
        else -> 300
    }
    val slamBonus = when (level) {
        6 -> if (vulnerable) 750 else 500
        7 -> if (vulnerable) 1500 else 1000
        else -> 0
    }
    val score = contractPoints + overtrickPoints + doubledBonus + gameBonus + slamBonus
    return BridgeResult(true, difference, contractPoints, overtrickPoints, doubledBonus, gameBonus, slamBonus, 0, score)
}

/** Score de chaque joueur (camp) pour la donne : le gagnant de la donne marque, l'autre reste à 0. */
fun bridgeDeltas(playerCount: Int, declarer: Int, result: BridgeResult): List<Int> =
    List(playerCount) { player ->
        when {
            result.made && player == declarer -> result.score
            !result.made && player != declarer -> result.penalty
            else -> 0
        }
    }
