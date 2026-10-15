package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/**
 * Tournoi de poker en cours : la partie ([PokerGame]), l'historique d'annulation et de rétablissement,
 * et le minuteur de blinds. Les règles sont dans [PokerEngine] (pur) ; cette classe n'ajoute que
 * l'état qui évolue pendant la partie. Elle ne dépend ni d'Android ni du ViewModel : [clock] donne
 * l'heure en millisecondes (l'horloge du système par défaut), ce qui permet de la remplacer dans les
 * tests pour faire avancer le temps à la main.
 *
 * Les noms et les couleurs des joueurs ne sont pas ici : ils restent dans [ScoreViewModel], qui les
 * met à jour après un changement de table (voir [reseat]).
 */
class PokerSession(private val clock: () -> Long = System::currentTimeMillis) {

    companion object {
        /** Nombre maximal d'actions mémorisées pour l'annulation. */
        const val MAX_UNDO = 300
    }

    /** Partie en cours, null tant que le tournoi n'est pas lancé. */
    var game by mutableStateOf<PokerGame?>(null)
        private set

    private val undoStack = mutableStateListOf<PokerGame>()
    private val redoStack = mutableStateListOf<PokerGame>()

    /** Temps de jeu écoulé au dernier arrêt du minuteur de blinds (le temps qui court s'y ajoute). */
    private var clockBaseMs by mutableLongStateOf(0L)
    private var clockStartedAt: Long? = null

    /** Vrai tant que le minuteur de blinds tourne. */
    var clockRunning by mutableStateOf(false)
        private set

    /** Vrai s'il y a une action à annuler. */
    val canUndo: Boolean get() = undoStack.isNotEmpty()

    /** Vrai s'il y a une action annulée à rétablir. */
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Efface tout : plus de partie, plus d'historique, minuteur à zéro et arrêté. */
    fun reset() {
        game = null
        undoStack.clear()
        redoStack.clear()
        clockBaseMs = 0L
        clockStartedAt = null
        clockRunning = false
    }

    /** Lance le tournoi pour [playerCount] joueurs ; le minuteur démarre tout de suite. */
    fun start(settings: PokerSettings, playerCount: Int) {
        reset()
        game = PokerEngine.newGame(settings, playerCount)
        clockStartedAt = clock()
        clockRunning = true
    }

    /**
     * Reprend une partie enregistrée : le minuteur repart du temps enregistré, en pause (à
     * l'utilisateur de le relancer quand la partie reprend), sans historique d'annulation.
     */
    fun restore(saved: PokerGame?) {
        reset()
        game = saved
        clockBaseMs = saved?.clockMs ?: 0L
    }

    /** Partie à enregistrer : l'état actuel avec le temps de jeu écoulé. Null s'il n'y a pas de partie. */
    fun snapshot(): PokerGame? = game?.copy(clockMs = elapsedMs())

    /** Vrai si au moins une main a été jouée ou une action enregistrée (donc qu'il y a quelque chose à perdre). */
    fun hasProgress(): Boolean = game?.let { it.handsPlayed > 0 || it.log.isNotEmpty() } == true

    // ---------- Minuteur ----------

    /** Temps de jeu écoulé à l'instant [now], en millisecondes, minuteur en pause compris. */
    fun elapsedMs(now: Long = clock()): Long {
        val started = clockStartedAt
        return clockBaseMs + if (started != null) (now - started).coerceAtLeast(0L) else 0L
    }

    /** Niveau de blinds atteint à l'instant [now] (il s'appliquera à la prochaine main). */
    fun level(now: Long = clock()): Int {
        val current = game ?: return 1
        return PokerBlinds.levelAt(current.settings, elapsedMs(now))
    }

    /** Arrête le minuteur s'il tourne, le relance sinon. */
    fun toggleClock() {
        val now = clock()
        if (clockRunning) {
            clockBaseMs = elapsedMs(now)
            clockStartedAt = null
            clockRunning = false
        } else {
            clockStartedAt = now
            clockRunning = true
        }
    }

    /** Passe tout de suite au niveau de blinds suivant (sans attendre la fin du minuteur). */
    fun skipToNextLevel() {
        val current = game ?: return
        val now = clock()
        clockBaseMs = PokerBlinds.elapsedAtNextLevel(current.settings, elapsedMs(now))
        if (clockRunning) clockStartedAt = now
    }

    // ---------- Actions et historique ----------

    /** Applique une action : l'état d'avant est gardé pour [undo] ; sans effet si rien ne change. */
    private fun apply(change: (PokerGame) -> PokerGame) {
        val current = game ?: return
        val next = change(current)
        if (next == current) return
        undoStack.add(current)
        if (undoStack.size > MAX_UNDO) undoStack.removeAt(0)
        redoStack.clear()
        game = next
    }

    /** Annule la dernière action. Sans effet s'il n'y en a pas. */
    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        game?.let { redoStack.add(it) }
        game = previous
    }

    /** Rétablit la dernière action annulée. Sans effet s'il n'y en a pas (ou si une nouvelle action a été faite depuis). */
    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        game?.let { undoStack.add(it) }
        game = next
    }

    fun fold() = apply { PokerEngine.fold(it) }

    fun check() = apply { PokerEngine.check(it) }

    fun call() = apply { PokerEngine.call(it) }

    fun allIn() = apply { PokerEngine.allIn(it) }

    /** Miser ou relancer pour une mise totale de [to] jetons dans le tour (voir [PokerEngine.raiseTo]). */
    fun raiseTo(to: Int) = apply { PokerEngine.raiseTo(it, to) }

    /** Désigne le(s) gagnant(s) de chaque pot à l'abattage (voir [PokerEngine.resolveShowdown]). */
    fun resolve(winners: List<List<Int>>) = apply { PokerEngine.resolveShowdown(it, winners) }

    /** Recave d'un joueur éliminé (voir [PokerEngine.rebuy]) : se joue entre deux mains, pendant la période réglée. */
    fun rebuy(player: Int) = apply { PokerEngine.rebuy(it, player, PokerBlinds.levelAt(it.settings, elapsedMs())) }

    fun nextHand() = apply { PokerEngine.nextHand(it, PokerBlinds.levelAt(it.settings, elapsedMs())) }

    // ---------- Changement de table ----------

    /** Vrai entre deux mains tant que le tournoi n'est pas terminé : seul moment où l'on peut déplacer des joueurs. */
    val canReseat: Boolean
        get() = game?.let { it.phase == PokerPhase.HAND_OVER && it.champion == null } == true

    /** Places des joueurs encore en lice, dans l'ordre de la table. */
    fun aliveIndices(): List<Int> {
        val current = game ?: return emptyList()
        return current.seats.indices.filter { !current.seats[it].out }
    }

    /** Vrai si [indices] peuvent quitter la table : il doit rester au moins deux joueurs en lice. */
    fun canRemove(indices: Collection<Int>): Boolean {
        val current = game ?: return false
        val leaving = indices.filter { it in current.seats.indices && !current.seats[it].out }.toSet()
        return canReseat && leaving.isNotEmpty() && aliveIndices().count { it !in leaving } >= 2
    }

    /** Vrai si l'arrivée de [count] joueurs obligerait à retirer de l'écran les joueurs déjà éliminés (table de [maxPlayers] places). */
    fun importNeedsRoom(count: Int, maxPlayers: Int): Boolean {
        val current = game ?: return false
        return current.seats.size + count > maxPlayers
    }

    /**
     * Fait partir les places [remove] et arriver des joueurs avec les tapis [arrivalStacks] (voir
     * [PokerEngine.reseat]). Renvoie le détail du déplacement, ou null (rien ne change) s'il est refusé.
     * L'historique est remis à zéro : les anciens états ne correspondent plus à la liste des joueurs.
     */
    fun reseat(remove: Set<Int>, arrivalStacks: List<Int>, random: Random = Random.Default): PokerReseat? {
        val current = game ?: return null
        val result = PokerEngine.reseat(current, remove, arrivalStacks, random) ?: return null
        game = result.game
        undoStack.clear()
        redoStack.clear()
        return result
    }
}
