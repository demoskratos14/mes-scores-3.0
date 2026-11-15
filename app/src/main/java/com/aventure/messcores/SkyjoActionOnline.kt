package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Skyjo Action chacun sur son téléphone, sur la connexion de [SyncSession] (celle du partage de la feuille de scores : les scores
 * voyagent comme d'habitude, seules les cartes ont leurs messages, voir [SkyjoMessage]).
 *
 * - L'organisateur tient la partie ([SkyjoActionRound], dans le [ScoreViewModel]). Chaque invité choisit sa place ([claim]) ;
 *   l'organisateur lui envoie la table vue de sa place ([SkyjoActionRound.tableFor]) : les cartes cachées des grilles n'y ont pas de
 *   valeur, et les secrets (carte en main, trois cartes, cartes regardées) ne partent que vers celui qui joue. Les places que
 *   personne n'a prises restent jouées sur le téléphone de l'organisateur, qu'on se passe comme avant.
 * - L'invité ne modifie rien : il envoie ses actions ([send]) et l'organisateur les applique après avoir vérifié que c'est bien à
 *   lui de jouer. L'invité affiche la table reçue dans [mirror], une manche qui ne sert qu'à l'affichage.
 */
internal class SkyjoActionOnline(private val session: SyncSession) {

    // ---------- Organisateur ----------

    // La place de chaque téléphone connecté qui en a choisi une.
    private val seats = HashMap<String, Int>()

    /** Les places jouées sur un autre téléphone ; les autres se jouent sur celui de l'organisateur. */
    var remoteSeats by mutableStateOf<Set<Int>>(emptySet())
        private set

    // ---------- Invité ----------

    /** La table reçue de l'organisateur, rangée dans une manche d'affichage ; null tant que rien n'est arrivé. */
    var mirror by mutableStateOf<SkyjoActionRound?>(null)
        private set

    /** Ma place à la table (-1 : pas encore choisie). */
    var mySeat by mutableIntStateOf(-1)
        private set

    /** Les places déjà prises par d'autres téléphones. */
    var taken by mutableStateOf<List<Int>>(emptyList())
        private set

    init {
        session.skyjo = this
    }

    /** Vrai si la place [seat] est jouée sur un autre téléphone. */
    fun isRemote(seat: Int): Boolean = seat in remoteSeats

    /** Remet tout à zéro (connexion coupée). */
    fun reset() {
        seats.clear()
        remoteSeats = emptySet()
        mirror = null
        mySeat = -1
        taken = emptyList()
    }

    // ---------- Ce que fait l'invité ----------

    fun claim(seat: Int) = session.sendToHost(SyncMessage.SkyjoMsg(SkyjoMessage.Claim(seat)))

    /** Envoie l'action [op] à l'organisateur ; vrai dès qu'elle est partie (c'est lui qui la vérifie). */
    fun send(op: SkyjoOp, a: Int = -1, b: Int = -1, flag: Boolean = false): Boolean {
        session.sendToHost(SyncMessage.SkyjoMsg(SkyjoMessage.Do(op, a, b, flag)))
        return true
    }

    // ---------- Ce que rapporte la connexion ----------

    /** Envoie à chaque invité la table vue de sa place ; à appeler à chaque changement de la partie (l'organisateur seulement). */
    fun publish() {
        if (session.role != SyncRole.HOST) return
        val round = hostRound() ?: return
        session.connected.forEach { sendView(it.id, round) }
    }

    private fun sendView(id: String, round: SkyjoActionRound) {
        val seat = seats[id] ?: -1
        session.sendTo(listOf(id), SyncMessage.SkyjoMsg(SkyjoMessage.View(round.tableFor(seat), seat, seats.values.toList())))
    }

    private fun hostRound(): SkyjoActionRound? {
        val game = session.attachedGame ?: return null
        if (!game.isGameActive || game.gameRules.id != SKYJO_ACTION_ID) return null
        return game.skyjoActionRound()
    }

    fun onPeerConnected(id: String) {
        if (session.role != SyncRole.HOST) return
        val round = hostRound() ?: return
        sendView(id, round)
    }

    fun onPeerDisconnected(id: String) {
        if (seats.remove(id) != null) {
            remoteSeats = seats.values.toSet()
            publish()
        }
    }

    fun onMessage(id: String, message: SkyjoMessage) {
        when (session.role) {
            SyncRole.HOST -> onGuestAction(id, message)
            SyncRole.GUEST -> if (message is SkyjoMessage.View) onView(message)
            SyncRole.NONE -> Unit
        }
    }

    private fun onView(message: SkyjoMessage.View) {
        val table = message.table
        // Une manche d'affichage à la taille de la table ; refaite si le nombre de joueurs change.
        val shown = mirror?.takeIf { it.playerCount == table.playerCount } ?: SkyjoActionRound(table.playerCount)
        shown.applyTable(table)
        mirror = shown
        mySeat = message.seat
        taken = message.taken
    }

    private fun onGuestAction(id: String, message: SkyjoMessage) {
        val game = session.attachedGame ?: return
        val round = hostRound() ?: return
        val seat = seats[id] ?: -1
        when (message) {
            is SkyjoMessage.Claim -> {
                val wanted = message.seat
                // Une place libre seulement : celle d'un autre téléphone ne se prend pas.
                val free = wanted in 0 until game.players.size && seats.none { it.key != id && it.value == wanted }
                if (free) seats[id] = wanted else if (wanted < 0) seats.remove(id)
                remoteSeats = seats.values.toSet()
            }
            is SkyjoMessage.Do -> when (message.op) {
                // Pas de manche suivante une fois la partie finie.
                SkyjoOp.NEXT -> if (round.settled && !game.isGameOver()) round.perform(SkyjoOp.NEXT)
                // Toute autre action : seulement de la part de celui qui doit agir, et pas une fois la manche finie.
                else -> if (seat >= 0 && seat == round.actor && !round.roundOver) {
                    round.perform(message.op, message.a, message.b, message.flag)
                }
            }
            is SkyjoMessage.View -> Unit
        }
        // Une manche finie est inscrite tout de suite, comme sur l'écran de l'organisateur.
        if (round.roundOver) round.settle(game)
        publish()
    }
}
