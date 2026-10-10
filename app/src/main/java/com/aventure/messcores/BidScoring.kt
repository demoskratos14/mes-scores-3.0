package com.aventure.messcores

import kotlin.math.abs

/**
 * Calcul du score d'une manche pour les jeux où l'on annonce un nombre de plis avant de jouer.
 * La fenêtre de saisie demande l'annonce et les plis réalisés, puis enregistre le score obtenu.
 */
object BidScoring {
    const val WIZARD = "wizard"
    const val OH_HELL = "oh_hell"

    /**
     * Score d'un joueur qui avait annoncé [bid] plis et en a fait [tricks].
     * Wizard : 20 points + 10 par pli annoncé si c'est exact, sinon −10 par pli d'écart.
     * Oh Hell : 10 points + 1 par pli annoncé si c'est exact, sinon −1 par pli d'écart.
     */
    fun score(kind: String, bid: Int, tricks: Int): Int = when (kind) {
        WIZARD -> if (bid == tricks) 20 + 10 * bid else -10 * abs(bid - tricks)
        OH_HELL -> if (bid == tricks) 10 + bid else -abs(bid - tricks)
        else -> 0
    }
}
