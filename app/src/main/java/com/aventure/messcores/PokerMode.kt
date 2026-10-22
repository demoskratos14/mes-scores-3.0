package com.aventure.messcores

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.random.Random

/** Issue d'un départ ou d'une arrivée de joueurs à la table de poker ([ScoreViewModel.pokerImportPlayers]). */
enum class PokerReseatResult { DONE, NOT_ALLOWED, TABLE_FULL }

/** Noms et couleurs des joueurs, dans l'ordre des places : ils vivent dans [ScoreViewModel], pas dans [PokerSession]. */
internal class PokerRoster(val names: List<String>, val colors: List<Color>)

/**
 * Résultat d'un changement de table : [result], et la nouvelle liste de joueurs [roster] si
 * (et seulement si) le changement a eu lieu : le ViewModel l'applique.
 */
internal class PokerReseatOutcome(val result: PokerReseatResult, val roster: PokerRoster? = null)

/**
 * Mode POKER : un tournoi, porté par [session] (partie, annulation, minuteur de blinds ; les règles
 * sont dans [PokerEngine]). Ce gestionnaire y ajoute ce qui touche aussi aux joueurs : exporter,
 * retirer et importer des joueurs entre deux tables, en calculant leurs nouveaux noms et couleurs.
 */
internal class PokerMode(val session: PokerSession = PokerSession()) : ModeState {

    override fun clear() = session.reset()

    override fun close() = session.reset()

    // Le poker démarre avec ses réglages : voir PokerSession.start (via ScoreViewModel.startPoker).
    override fun start(playerCount: Int) = Unit

    // Le minuteur reprend en pause : à l'utilisateur de le relancer quand la partie reprend.
    override fun restore(saved: SavedGame, playerCount: Int) = session.restore(saved.poker)

    override fun applyTo(saved: SavedGame): SavedGame = saved.copy(poker = session.snapshot())

    override fun hasProgress(): Boolean = session.hasProgress()

    override val canUndo: Boolean get() = session.canUndo
    override val canRedo: Boolean get() = session.canRedo
    override fun undo() = session.undo()
    override fun redo() = session.redo()

    override fun total(player: Int): Int = session.game?.seats?.getOrNull(player)?.stack ?: 0

    override fun roundsPlayed(): Int = session.game?.handsPlayed ?: 0

    /** Terminé quand il ne reste qu'un joueur avec des jetons. */
    override fun gameOver(): Boolean = session.game?.champion != null

    /** Joueurs en lice d'après leurs jetons, puis éliminés dans l'ordre inverse d'élimination (null avant le départ). */
    override fun ranks(): List<Int>? = session.game?.let { PokerEngine.ranks(it) }

    // ---------- Changement de table ----------

    /** Joueurs (nom, tapis, couleur) des places [indices] encore en lice, prêts à être écrits dans un fichier. */
    fun transferPlayers(indices: Collection<Int>, roster: PokerRoster): List<PokerTransferPlayer> {
        val game = session.game ?: return emptyList()
        return indices.sorted()
            .filter { it in game.seats.indices && !game.seats[it].out }
            .map { i ->
                PokerTransferPlayer(
                    name = roster.names.getOrElse(i) { "" },
                    stack = game.seats[i].stack,
                    colorArgb = roster.colors.getOrElse(i) { Color.Gray }.toArgb()
                )
            }
    }

    /** Fait quitter la table aux joueurs [indices] (après leur export). L'annulation est remise à zéro. */
    fun removePlayers(indices: Collection<Int>, roster: PokerRoster): PokerReseatOutcome {
        val game = session.game ?: return PokerReseatOutcome(PokerReseatResult.NOT_ALLOWED)
        val leaving = indices.filter { it in game.seats.indices && !game.seats[it].out }.toSet()
        if (leaving.isEmpty() || !session.canRemove(leaving)) return PokerReseatOutcome(PokerReseatResult.NOT_ALLOWED)
        return reseat(game, leaving, emptyList(), roster, Random.Default)
    }

    /** Vrai si l'arrivée de [count] joueurs obligerait à retirer de l'écran les joueurs déjà éliminés (table de [maxPlayers] places). */
    fun importNeedsRoom(count: Int, maxPlayers: Int): Boolean = session.importNeedsRoom(count, maxPlayers)

    /**
     * Ajoute à la table les joueurs [incoming] (avec leur tapis), assis au hasard entre les joueurs
     * déjà présents : leur ordre et la place du bouton ne changent pas. Si la table n'a plus de place
     * à cause des joueurs éliminés, ceux-ci sont retirés de l'affichage. L'annulation est remise à zéro.
     */
    fun importPlayers(
        incoming: List<PokerTransferPlayer>,
        roster: PokerRoster,
        maxPlayers: Int,
        random: Random
    ): PokerReseatOutcome {
        val game = session.game ?: return PokerReseatOutcome(PokerReseatResult.NOT_ALLOWED)
        if (!session.canReseat || incoming.isEmpty()) return PokerReseatOutcome(PokerReseatResult.NOT_ALLOWED)
        if (session.aliveIndices().size + incoming.size > maxPlayers) return PokerReseatOutcome(PokerReseatResult.TABLE_FULL)
        val purge = if (importNeedsRoom(incoming.size, maxPlayers)) {
            game.seats.indices.filter { game.seats[it].out }.toSet()
        } else {
            emptySet()
        }
        return reseat(game, purge, incoming, roster, random)
    }

    /** Déplace les joueurs dans la partie, puis calcule les noms et les couleurs en conséquence. */
    private fun reseat(
        game: PokerGame,
        remove: Set<Int>,
        incoming: List<PokerTransferPlayer>,
        roster: PokerRoster,
        random: Random
    ): PokerReseatOutcome {
        val result = session.reseat(remove, incoming.map { it.stack }, random)
            ?: return PokerReseatOutcome(PokerReseatResult.NOT_ALLOWED)
        val kept = game.seats.indices.filter { it !in remove }
        // Noms : les joueurs présents gardent le leur, un arrivant au même nom est numéroté.
        val arrivalNames = PlayerNames.makeUnique(kept.map { roster.names.getOrElse(it) { "" } } + incoming.map { it.name })
            .drop(kept.size)
        // Couleurs : les joueurs présents sont servis d'abord ; un arrivant qui a la même couleur en reçoit une libre.
        val keptColors = kept.map { roster.colors.getOrElse(it) { Color.Gray } }
        val arrivalColors = assignPlayerColors(
            count = kept.size + incoming.size,
            chosen = keptColors + incoming.map { p -> if (p.colorArgb == 0) null else Color(p.colorArgb) },
            random = random
        ).drop(kept.size)

        val total = result.game.seats.size
        val newNames = MutableList(total) { "" }
        val newColors = MutableList(total) { Color.Gray }
        result.oldToNew.forEachIndexed { old, new ->
            if (new >= 0) {
                newNames[new] = roster.names.getOrElse(old) { "" }
                newColors[new] = roster.colors.getOrElse(old) { Color.Gray }
            }
        }
        result.arrivals.forEachIndexed { k, new ->
            newNames[new] = arrivalNames[k]
            newColors[new] = arrivalColors[k]
        }
        return PokerReseatOutcome(PokerReseatResult.DONE, PokerRoster(newNames, newColors))
    }
}
