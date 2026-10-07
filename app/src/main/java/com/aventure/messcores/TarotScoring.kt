package com.aventure.messcores

import kotlin.math.abs

/** Points à réaliser par le preneur selon son nombre de bouts (0, 1, 2 ou 3). */
private val POINTS_REQUIRED_BY_BOUTS = listOf(56, 51, 41, 36)

/** Total des points d'un jeu de Tarot : avoir 91 points signifie avoir remporté tous les plis. */
const val TAROT_TOTAL_POINTS = 91

/**
 * Prime accordée aux défenseurs quand ils remportent tous les plis (chelem de la défense).
 * Ce n'est pas une prime du règlement officiel mais une règle maison courante : changer
 * cette valeur pour l'ajuster (0 pour la désactiver).
 */
const val DEFENSE_SLAM_PRIZE = 200

/** Points que le preneur doit réaliser pour remplir son contrat, selon son nombre de [bouts] (0 à 3). */
fun tarotRequiredPoints(bouts: Int): Int = POINTS_REQUIRED_BY_BOUTS[bouts]

/**
 * Résultat du calcul d'une manche de Tarot.
 *
 * @param required points à réaliser pour remplir le contrat.
 * @param difference points réalisés moins points requis (négatif si le contrat chute).
 * @param success vrai si le contrat est rempli.
 * @param slamSucceeded vrai si le preneur a fait tous les plis (91 points).
 * @param defenseSlamActive vrai si la défense a fait tous les plis et que le contrat a chuté.
 * @param unit score de la manche pour une unité « défenseur », vu du côté des attaquants
 *   (positif si les attaquants gagnent) : chaque défenseur reçoit l'opposé de [unit].
 * @param deltas points gagnés ou perdus par chaque joueur ; leur somme est toujours nulle.
 *   Null si le preneur n'est pas encore choisi.
 */
data class TarotResult(
    val required: Int,
    val difference: Int,
    val success: Boolean,
    val slamSucceeded: Boolean,
    val defenseSlamActive: Boolean,
    val slamScore: Int,
    val unit: Int,
    val deltas: List<Int>?
)

/**
 * Calcule une manche de Tarot selon les règles officielles : contrat (25 + écart) × coefficient,
 * petit au bout, poignée et chelem. C'est une fonction pure (aucun état, aucune interface), pour
 * pouvoir la tester facilement.
 *
 * @param playerCount nombre de joueurs (3, 4 ou 5).
 * @param taker index du preneur, ou null s'il n'est pas encore choisi (les [TarotResult.deltas] sont alors null).
 * @param partner index du joueur appelé (à 5 joueurs), ou null si le preneur est seul.
 * @param factor coefficient du contrat (petite ×1, garde ×2, garde sans ×4, garde contre ×6).
 * @param bouts nombre de bouts du preneur (0 à 3).
 * @param points points réalisés par le preneur (0 à 91).
 * @param petitAuBout 0 aucun, 1 preneur, 2 défense.
 * @param handful 0 aucune, 1 simple (20), 2 double (30), 3 triple (40).
 * @param slamAnnounced chelem annoncé par le preneur.
 * @param defenseSlam la défense a fait tous les plis (pris en compte seulement si le contrat chute).
 */
fun computeTarotRound(
    playerCount: Int,
    taker: Int?,
    partner: Int?,
    factor: Int,
    bouts: Int,
    points: Int,
    petitAuBout: Int,
    handful: Int,
    slamAnnounced: Boolean,
    defenseSlam: Boolean
): TarotResult {
    val required = tarotRequiredPoints(bouts)
    val difference = points - required
    val success = difference >= 0
    // Le preneur ne peut faire 91 points que s'il a remporté tous les plis : le chelem est
    // donc déduit automatiquement des points saisis, ce qui évite toute incohérence.
    val slamSucceeded = points == TAROT_TOTAL_POINTS
    val defenseSlamActive = defenseSlam && !success
    val slamScore = when {
        slamSucceeded -> if (slamAnnounced) 400 else 200
        defenseSlamActive -> -DEFENSE_SLAM_PRIZE
        slamAnnounced -> -200 // annoncé mais raté
        else -> 0
    }
    val sign = if (success) 1 else -1
    val contractScore = (25 + abs(difference)) * factor * sign
    val petitScore = when (petitAuBout) {
        1 -> 10 * factor
        2 -> -10 * factor
        else -> 0
    }
    val handfulScore = listOf(0, 20, 30, 40)[handful] * sign
    val unit = contractScore + petitScore + handfulScore + slamScore

    val deltas = taker?.let {
        val attackers = setOfNotNull(taker, partner)
        val defenderCount = playerCount - attackers.size
        List(playerCount) { player ->
            when (player) {
                taker -> unit * (defenderCount - if (partner != null) 1 else 0)
                partner -> unit
                else -> -unit
            }
        }
    }
    return TarotResult(required, difference, success, slamSucceeded, defenseSlamActive, slamScore, unit, deltas)
}
