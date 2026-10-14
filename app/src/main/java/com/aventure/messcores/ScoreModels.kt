package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * État d'une case du tableau (mode TABLE) : la magnitude saisie (toujours positive
 * côté saisie), le signe, et la règle choisie. Chaque champ est observable
 * individuellement par Compose, donc modifier l'un d'eux ne recrée pas l'objet.
 */
class CellState {
    var baseValue by mutableStateOf<Int?>(null)
    var isNegative by mutableStateOf(false)
    var multiplierId by mutableStateOf(GameRules.NORMAL_MULTIPLIER_ID)
}

/**
 * Une manche jouée en mode équipes variables (ex: Tarot) : les index des joueurs
 * de "l'équipe A" (ex: le preneur, ou preneur + appelé) pour CETTE manche, et le
 * score final déjà calculé (signe + multiplicateur + bonus appliqués). L'équipe B
 * regroupe automatiquement tous les autres joueurs et reçoit l'opposé de ce score.
 *
 * Si [deltas] est renseigné (Tarot), il donne le score exact de chaque joueur et remplace
 * cette règle simple : [value] à chaque membre de l'équipe A, son opposé à chaque membre
 * de l'équipe B.
 */
data class TeamRound(
    val teamALabel: String,
    val teamAPlayers: Set<Int>,
    val value: Int,
    /** Saisie d'origine d'une manche de Tarot, conservée pour pouvoir la corriger (voir [TarotRoundInput]). */
    val tarotInput: TarotRoundInput? = null,
    /**
     * Points exacts gagnés ou perdus par chaque joueur (même index que la liste des joueurs),
     * pour les jeux qui répartissent le score de façon inégale (Tarot). Si null, on applique
     * la règle simple : [value] à l'équipe A et son opposé aux autres joueurs.
     */
    val deltas: List<Int>? = null,
    /**
     * Saisie d'origine d'une donne de bridge ou de skat, sous forme de nombres (voir [BridgeInput] et
     * [SkatInput]), conservée pour pouvoir la corriger. Null pour les autres jeux.
     */
    val contractInput: List<Int>? = null
)

/** Saisie brute d'une manche de Tarot (preneur, contrat, points, primes…), pour la modifier plus tard. */
data class TarotRoundInput(
    val taker: Int,
    val partner: Int?,
    val multiplierIndex: Int,
    val bouts: Int,
    val points: Int,
    val petitAuBout: Int,
    val handful: Int,
    val slamAnnounced: Boolean,
    val defenseSlam: Boolean
)

/**
 * Une saisie annulable (modes TABLE et COUNTER). Les manches à équipes variables (Tarot) ont
 * déjà leur propre suppression/correction dans l'historique des manches.
 */
sealed interface UndoAction {
    /** Un appui sur +/− d'un compteur : [delta] a été ajouté au joueur [player]. */
    data class CounterChange(val player: Int, val delta: Int) : UndoAction

    /**
     * Une modification de la case (manche [round], joueur [player]) : [before] est son état
     * d'avant. [addedRound] vaut vrai si cette saisie a fait ajouter automatiquement une
     * nouvelle manche vide à la fin, qu'il faut alors retirer en annulant.
     */
    data class CellEdit(
        val round: Int,
        val player: Int,
        val before: CellSnapshot,
        val addedRound: Boolean
    ) : UndoAction
}

/** Une saisie annulée, que [ScoreViewModel.redo] sait rétablir. */
sealed interface RedoAction {
    /** [delta] doit être ré-ajouté au joueur [player]. */
    data class CounterChange(val player: Int, val delta: Int) : RedoAction

    /**
     * La case (manche [round], joueur [player]) doit reprendre l'état [state]. [removedRound] vaut
     * vrai si l'annulation avait retiré la manche vide ajoutée automatiquement : il faut la recréer.
     */
    data class CellEdit(
        val round: Int,
        val player: Int,
        val state: CellSnapshot,
        val removedRound: Boolean
    ) : RedoAction
}
