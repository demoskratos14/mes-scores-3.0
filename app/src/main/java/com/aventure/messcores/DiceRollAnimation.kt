package com.aventure.messcores

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.random.Random
import kotlinx.coroutines.delay

/** Durées de l'animation d'un lancer : les dés tournent, puis s'arrêtent l'un après l'autre. */
internal object DiceRollTiming {
    /** Intervalle entre deux faces aléatoires affichées sur un dé qui tourne. */
    const val FACE_CHANGE_MS = 80L

    /** Moment où s'arrête le premier dé (ms après le lancer). */
    const val FIRST_STOP_MS = 600L

    /** Délai entre l'arrêt d'un dé et celui du suivant. */
    const val STOP_STEP_MS = 300L

    /** Moment d'arrêt du dé qui est en [position]-ième place parmi ceux qui ont été lancés (0 : le premier). */
    fun stopAt(position: Int): Long = FIRST_STOP_MS + position * STOP_STEP_MS
}

/**
 * L'animation d'un lancer de dés, purement visuelle : le [DiceTurn] a déjà ses nouvelles valeurs, mais les dés lancés
 * (pas ceux qu'on garde) montrent des faces au hasard puis se figent un à un, de gauche à droite. Tant que [rolling]
 * est vrai, le reste de l'écran (score proposé, combinaison, total du Cochon…) attend pour ne pas dévoiler le résultat.
 *
 * Un lancer est repéré par le nombre de lancers du tour ([DiceTurn.rollsUsed]) : il vaut donc aussi pour les dés
 * lancés depuis un autre téléphone. Rien ne tourne à l'ouverture de l'écran ni quand il pivote.
 */
internal class DiceRollAnimation(private val turn: DiceTurn) {

    // Dernier nombre de lancers déjà « joué » ; un lancer est en attente tant que le tour en compte davantage.
    private var seenRolls by mutableIntStateOf(turn.rollsUsed)

    private var spinning by mutableStateOf<Set<Int>>(emptySet())

    private var faces by mutableStateOf<List<Int>>(emptyList())

    private val pending: Boolean get() = turn.hasRolled && turn.rollsUsed > seenRolls

    /** Les dés lancés ce coup-ci : tous au premier lancer, puis ceux qui ne sont pas gardés. */
    private fun thrown(): List<Int> = (0 until turn.setup.diceCount).filter { it !in turn.held }

    /** Vrai tant qu'au moins un dé tourne. Vrai dès l'image qui suit le lancer, sans retard. */
    val rolling: Boolean get() = pending || spinning.isNotEmpty()

    /** Vrai si le dé [index] tourne encore. */
    fun isSpinning(index: Int): Boolean = if (pending) index in thrown() else index in spinning

    /** La face montrée sur le dé [index] tant qu'il tourne. */
    fun faceOf(index: Int): Int = faces.getOrNull(index) ?: 1

    /** Joue l'animation du lancer en attente, le cas échéant (à lancer dans un LaunchedEffect). */
    suspend fun run() {
        val rolled = pending
        seenRolls = turn.rollsUsed
        if (!rolled) {
            spinning = emptySet()
            return
        }
        val order = thrown()
        spinning = order.toSet()
        var elapsed = 0L
        while (spinning.isNotEmpty()) {
            faces = List(turn.setup.diceCount) { Random.nextInt(1, 7) }
            delay(DiceRollTiming.FACE_CHANGE_MS)
            elapsed += DiceRollTiming.FACE_CHANGE_MS
            spinning = order.filterIndexed { position, _ -> DiceRollTiming.stopAt(position) > elapsed }.toSet()
        }
    }
}

/** L'animation des lancers de [turn] ; elle repart de zéro si c'est un autre tour (autre joueur au 421). */
@Composable
internal fun rememberDiceRollAnimation(turn: DiceTurn): DiceRollAnimation {
    val animation = remember(turn) { DiceRollAnimation(turn) }
    LaunchedEffect(animation, turn.rollsUsed, turn.hasRolled) { animation.run() }
    return animation
}
