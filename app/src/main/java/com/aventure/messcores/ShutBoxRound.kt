package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/** Règles du Shut the Box : voir la fiche de règles du jeu (« Le tour de jeu » et « Le score »). */
internal object ShutBoxScoring {

    /** Nombre de clapets : numérotés de 1 à 9. */
    const val FLAPS = 9

    /** Nombre de manches de la partie. */
    const val ROUNDS = 5

    /** Tous les clapets ouverts : un bit par clapet (le bit 0 est le clapet 1, le bit 8 le clapet 9). */
    const val ALL_OPEN = (1 shl FLAPS) - 1

    /** Masque du clapet [flap] (de 1 à 9). */
    fun bit(flap: Int): Int = 1 shl (flap - 1)

    /** Numéros des clapets du masque [mask], dans l'ordre croissant. */
    fun flaps(mask: Int): List<Int> = (1..FLAPS).filter { mask and bit(it) != 0 }

    /** Somme des numéros des clapets du masque [mask]. */
    fun sum(mask: Int): Int = flaps(mask).sum()

    /** Points du tour : la somme des clapets restés ouverts (0 quand la boîte est fermée). */
    fun score(openMask: Int): Int = sum(openMask and ALL_OPEN)

    /** Vrai si les clapets 7, 8 et 9 sont rabattus : on peut alors ne lancer qu'un seul dé. */
    fun canRollOneDie(openMask: Int): Boolean = openMask and (bit(7) or bit(8) or bit(9)) == 0

    /** Vrai si l'on peut rabattre des clapets ouverts dont la somme fait [total]. */
    fun canMake(openMask: Int, total: Int): Boolean {
        if (total <= 0) return false
        // Les 512 combinaisons possibles au plus : un parcours complet des sous-ensembles reste instantané.
        val mask = openMask and ALL_OPEN
        var sub = mask
        while (sub != 0) {
            if (sum(sub) == total) return true
            sub = (sub - 1) and mask
        }
        return false
    }

    /** Vrai si [selected] est une sélection permise : des clapets ouverts, au moins un, dont la somme fait [total]. */
    fun isValid(openMask: Int, selected: Int, total: Int): Boolean =
        selected != 0 && selected and openMask.inv() == 0 && selected and ALL_OPEN == selected && sum(selected) == total
}

/**
 * Une partie de Shut the Box jouée tour par tour, de la même façon pour tous les téléphones.
 *
 * - Le joueur qui joue lance les 2 dés ([roll]), puis choisit ([toggle]) des clapets ouverts dont la somme est égale au total
 *   des dés et les rabat ([confirm]). Il relance ensuite, jusqu'à ne plus pouvoir rabattre de clapet ([stuck]) ou jusqu'à
 *   avoir rabattu les neuf ([boxShut]) : son tour est fini.
 * - Quand les clapets 7, 8 et 9 sont rabattus, il peut ne lancer qu'un seul dé ([setOneDie]) ; le total est alors la valeur du premier dé.
 * - À la fin du tour ([endTurn]), la somme des clapets restés ouverts (0 si la boîte est fermée) est inscrite dans la prochaine
 *   case libre du joueur au tableau des scores, puis la main passe au joueur suivant : le total de chacun, la fin de partie
 *   (après [ShutBoxScoring.ROUNDS] manches jouées par tous) et le journal viennent de ce tableau, le plus petit total gagnant.
 *
 * [startPlayer] : le joueur qui joue en premier (une partie reprise continue avec le joueur qui n'a pas encore joué dans la manche).
 * [rollDie] tire un dé de 1 à 6 (remplaçable dans les tests).
 */
internal class ShutBoxRound(
    val playerCount: Int,
    startPlayer: Int = 0,
    private val rollDie: () -> Int = { Random.nextInt(1, 7) }
) {
    private val first = startPlayer.coerceIn(0, (playerCount - 1).coerceAtLeast(0))

    /** Les dés du joueur qui joue : réinitialisés après chaque combinaison rabattue, sans pouvoir en garder. */
    val turn = DiceTurn(DiceSetup(diceCount = 2, allowHold = false), rollDie)

    /** Le joueur qui joue. */
    var current by mutableIntStateOf(first)
        private set

    /** Clapets encore ouverts pendant le tour du joueur (un bit par clapet, voir [ShutBoxScoring.bit]). */
    var openMask by mutableIntStateOf(ShutBoxScoring.ALL_OPEN)
        private set

    /** Clapets choisis sur le lancer en cours, pas encore rabattus. */
    var selected by mutableIntStateOf(0)
        private set

    /** Vrai si le joueur ne lance qu'un seul dé (permis quand les clapets 7, 8 et 9 sont rabattus). */
    var oneDie by mutableStateOf(false)
        private set

    /** Total du lancer en cours ; null tant que les dés n'ont pas été lancés. */
    val total: Int?
        get() = if (!turn.hasRolled) null else if (oneDie) turn.values.first() else turn.values.sum()

    /** Vrai quand la boîte est fermée : les neuf clapets sont rabattus. */
    val boxShut: Boolean get() = openMask == 0

    /** Vrai quand le lancer en cours ne permet de rabattre aucun clapet. */
    val stuck: Boolean get() = total?.let { !ShutBoxScoring.canMake(openMask, it) } ?: false

    /** Vrai quand le tour du joueur est fini : boîte fermée, ou lancer sans combinaison possible. */
    val turnOver: Boolean get() = boxShut || stuck

    /** Points du tour si le joueur s'arrête maintenant : la somme des clapets encore ouverts. */
    val score: Int get() = ShutBoxScoring.score(openMask)

    /** Somme des clapets choisis. */
    val selectionSum: Int get() = ShutBoxScoring.sum(selected)

    /** Vrai si les clapets choisis sont une combinaison permise pour le total des dés. */
    val canConfirm: Boolean get() = total?.let { ShutBoxScoring.isValid(openMask, selected, it) } ?: false

    /** Vrai si le joueur peut lancer : la boîte n'est pas fermée et le lancer précédent a été rabattu. */
    val canRoll: Boolean get() = !boxShut && !turn.hasRolled

    /** Vrai si le joueur peut choisir de ne lancer qu'un seul dé (avant son lancer, les clapets 7, 8 et 9 étant rabattus). */
    val canChooseOneDie: Boolean get() = !turn.hasRolled && ShutBoxScoring.canRollOneDie(openMask)

    /** Choisit de lancer un ou deux dés ; un seul dé n'est permis que si les clapets 7, 8 et 9 sont rabattus. */
    @JvmName("chooseOneDie")
    fun setOneDie(value: Boolean) {
        if (turn.hasRolled || (value && !ShutBoxScoring.canRollOneDie(openMask))) return
        oneDie = value
    }

    /** Lance les dés (les deux, même à un seul dé : le second est alors ignoré). */
    fun roll() {
        if (!canRoll) return
        selected = 0
        turn.roll()
    }

    /** Choisit le clapet [flap] (de 1 à 9), ou le remet en jeu s'il était choisi ; sans effet sur un clapet déjà rabattu. */
    fun toggle(flap: Int) {
        if (!turn.hasRolled || turnOver || flap !in 1..ShutBoxScoring.FLAPS) return
        val bit = ShutBoxScoring.bit(flap)
        if (openMask and bit == 0) return
        selected = selected xor bit
    }

    /** Rabat les clapets choisis ; renvoie faux (rien ne change) si la combinaison n'est pas permise. */
    fun confirm(): Boolean {
        if (!canConfirm) return false
        openMask = openMask and selected.inv()
        selected = 0
        turn.reset()
        return true
    }

    /** Inscrit [points] dans la prochaine case libre de [player] au tableau des scores. */
    private fun write(viewModel: ScoreViewModel, player: Int, points: Int) {
        val row = viewModel.scores.indexOfFirst { it.getOrNull(player)?.baseValue == null }
        if (row >= 0) viewModel.setCell(row, player, points, false)
    }

    /**
     * Fin du tour : la somme des clapets restés ouverts est inscrite au tableau et la main passe au joueur suivant.
     * Renvoie les points inscrits, ou -1 si le tour n'est pas fini (voir [turnOver]).
     */
    fun endTurn(viewModel: ScoreViewModel): Int {
        if (!turnOver) return -1
        val points = score
        write(viewModel, current, points)
        current = (current + 1) % playerCount.coerceAtLeast(1)
        resetTurn()
        return points
    }

    private fun resetTurn() {
        turn.reset()
        openMask = ShutBoxScoring.ALL_OPEN
        selected = 0
        oneDie = false
    }

    // ---------- Sauvegarde et partage ----------

    private fun resetAll() {
        current = first
        resetTurn()
    }

    /** État sous forme de liste d'entiers, pour survivre à une rotation de l'écran et être partagé (voir [restoreFrom]). */
    fun snapshot(): List<Int> = buildList {
        add(playerCount)
        add(current)
        add(openMask)
        add(selected)
        add(if (oneDie) 1 else 0)
        addAll(turn.snapshot())
    }

    /** Rétablit l'état produit par [snapshot] ; une liste invalide remet la partie au départ. */
    fun restoreFrom(data: List<Int>) {
        resetAll()
        try {
            var cursor = 0
            fun next(): Int = data[cursor++]

            require(next() == playerCount)
            val newCurrent = next()
            val newOpen = next()
            val newSelected = next()
            val newOneDie = next()
            require(newCurrent in 0 until playerCount && newOpen in 0..ShutBoxScoring.ALL_OPEN && newOneDie in 0..1)
            // Les clapets choisis sont forcément des clapets encore ouverts, sur un lancer déjà fait.
            require(newSelected and newOpen.inv() == 0)
            turn.restoreFrom(data.drop(cursor))
            require(newSelected == 0 || turn.hasRolled)
            require(newOneDie == 0 || ShutBoxScoring.canRollOneDie(newOpen))

            current = newCurrent
            openMask = newOpen
            selected = newSelected
            oneDie = newOneDie == 1
        } catch (e: Exception) {
            resetAll()
        }
    }
}
