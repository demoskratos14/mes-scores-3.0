package com.aventure.messcores

/** Jeux du skat. L'ordre des quatre couleurs est celui des valeurs de base (trèfle > pique > cœur > carreau). */
object SkatGame {
    const val CLUBS = 0
    const val SPADES = 1
    const val HEARTS = 2
    const val DIAMONDS = 3
    const val GRAND = 4
    const val NULL = 5
}

/** Résultat d'une donne du point de vue du déclarant. */
object SkatOutcome {
    const val WON = 0
    const val WON_SCHNEIDER = 1
    const val WON_SCHWARZ = 2
    const val LOST = 3
    const val LOST_SCHNEIDER = 4
    const val LOST_SCHWARZ = 5
}

/** Plus grand nombre de matadors (« avec / sans ») selon le jeu : 11 à la couleur, 4 au grand. */
fun skatMaxMatadors(game: Int): Int = if (game == SkatGame.GRAND) 4 else 11

/** Saisie d'une donne de skat, conservée dans [TeamRound.contractInput] pour pouvoir la corriger. */
data class SkatInput(
    val declarer: Int,
    val game: Int,
    /** Nombre de matadors, « avec » ou « sans » (1 à 11) ; ignoré au null. */
    val matadors: Int,
    val hand: Boolean,
    val schneiderAnnounced: Boolean,
    val schwarzAnnounced: Boolean,
    val open: Boolean,
    val outcome: Int,
    /** Enchère du déclarant, ou 0 si elle n'est pas saisie. */
    val bid: Int
) {
    fun encode(): List<Int> = listOf(
        declarer, game, matadors, if (hand) 1 else 0, if (schneiderAnnounced) 1 else 0,
        if (schwarzAnnounced) 1 else 0, if (open) 1 else 0, outcome, bid
    )

    companion object {
        /** Relit une saisie enregistrée ; null si elle est incomplète (ancien format, fichier abîmé). */
        fun decode(values: List<Int>): SkatInput? =
            if (values.size < 9) null
            else SkatInput(
                declarer = values[0], game = values[1], matadors = values[2], hand = values[3] == 1,
                schneiderAnnounced = values[4] == 1, schwarzAnnounced = values[5] == 1, open = values[6] == 1,
                outcome = values[7], bid = values[8]
            )
    }
}

/**
 * Résultat du calcul d'une donne de skat.
 *
 * @param won vrai si le déclarant gagne la donne (une annonce non tenue ou une enchère dépassée la fait perdre).
 * @param overbid vrai si l'enchère dépasse la valeur du jeu.
 * @param announceFailed vrai si le déclarant a annoncé Schneider ou Schwarz sans le réaliser.
 * @param baseValue valeur de base du jeu (0 au null, qui a des valeurs fixes).
 * @param multiplier multiplicateur du jeu (0 au null).
 * @param gameValue valeur du jeu, utilisée pour la marque.
 * @param declarerScore points du déclarant : 50 + valeur s'il gagne, −(50 + 2 × valeur) s'il perd.
 * @param defenderScore points de chacun des autres joueurs : 30 si le déclarant perd, sinon 0.
 */
data class SkatResult(
    val won: Boolean,
    val overbid: Boolean,
    val announceFailed: Boolean,
    val baseValue: Int,
    val multiplier: Int,
    val gameValue: Int,
    val declarerScore: Int,
    val defenderScore: Int
)

private const val SKAT_GAME_BONUS = 50
private const val SKAT_DEFENDER_BONUS = 30

/** Valeur fixe d'un null selon Hand et Ouvert : 23, Hand 35, Ouvert 46, Ouvert Hand 59. */
fun skatNullValue(hand: Boolean, open: Boolean): Int = when {
    hand && open -> 59
    open -> 46
    hand -> 35
    else -> 23
}

/**
 * Calcule une donne de skat avec la marque de la liste de tournoi (Seeger-Fabian). Fonction pure,
 * pour pouvoir la tester facilement.
 *
 * Les annonces s'impliquent entre elles : Ouvert vaut Schwarz annoncé, Schwarz annoncé vaut Schneider
 * annoncé, et toute annonce suppose Hand. Au null, seuls Hand, Ouvert et gagné / perdu comptent.
 *
 * @param game jeu joué (voir [SkatGame]).
 * @param matadors nombre de matadors, « avec » ou « sans » (au moins 1).
 * @param outcome résultat (voir [SkatOutcome]).
 * @param bid enchère du déclarant, ou 0 si elle n'est pas connue.
 */
fun computeSkatRound(
    game: Int,
    matadors: Int,
    hand: Boolean,
    schneiderAnnounced: Boolean,
    schwarzAnnounced: Boolean,
    open: Boolean,
    outcome: Int,
    bid: Int
): SkatResult {
    val wonByCards = outcome <= SkatOutcome.WON_SCHWARZ
    if (game == SkatGame.NULL) {
        val value = skatNullValue(hand, open)
        val overbid = bid > value
        return skatResult(wonByCards && !overbid, overbid, false, 0, 0, value)
    }

    val schwarzAnnouncedEffective = schwarzAnnounced || open
    val schneiderAnnouncedEffective = schneiderAnnounced || schwarzAnnouncedEffective
    val handEffective = hand || schneiderAnnouncedEffective

    val schneider = outcome == SkatOutcome.WON_SCHNEIDER || outcome == SkatOutcome.WON_SCHWARZ ||
        outcome == SkatOutcome.LOST_SCHNEIDER || outcome == SkatOutcome.LOST_SCHWARZ
    val schwarz = outcome == SkatOutcome.WON_SCHWARZ || outcome == SkatOutcome.LOST_SCHWARZ

    // Une annonce non tenue fait perdre la donne, même si le déclarant a plus de 60 points.
    val announceFailed = wonByCards &&
        ((schneiderAnnouncedEffective && !schneider) || (schwarzAnnouncedEffective && !schwarz))

    val base = when (game) {
        SkatGame.CLUBS -> 12
        SkatGame.SPADES -> 11
        SkatGame.HEARTS -> 10
        SkatGame.DIAMONDS -> 9
        else -> 24
    }
    val multiplier = 1 + matadors.coerceAtLeast(1) +
        (if (handEffective) 1 else 0) + (if (open) 1 else 0) +
        (if (schneiderAnnouncedEffective) 1 else 0) + (if (schwarzAnnouncedEffective) 1 else 0) +
        (if (schneider) 1 else 0) + (if (schwarz) 1 else 0)
    var value = base * multiplier

    // Enchère dépassée : le jeu est perdu, et sa valeur est le plus petit multiple de la valeur de base ≥ l'enchère.
    val overbid = bid > value
    if (overbid) value = (bid + base - 1) / base * base

    return skatResult(wonByCards && !announceFailed && !overbid, overbid, announceFailed, base, multiplier, value)
}

private fun skatResult(won: Boolean, overbid: Boolean, announceFailed: Boolean, base: Int, multiplier: Int, value: Int) =
    SkatResult(
        won = won,
        overbid = overbid,
        announceFailed = announceFailed,
        baseValue = base,
        multiplier = multiplier,
        gameValue = value,
        declarerScore = if (won) SKAT_GAME_BONUS + value else -(SKAT_GAME_BONUS + 2 * value),
        defenderScore = if (won) 0 else SKAT_DEFENDER_BONUS
    )

/** Score de chaque joueur pour la donne : le déclarant a le sien, tous les autres joueurs reçoivent celui des défenseurs. */
fun skatDeltas(playerCount: Int, declarer: Int, result: SkatResult): List<Int> =
    List(playerCount) { player -> if (player == declarer) result.declarerScore else result.defenderScore }
