package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Le Uno chacun sur son téléphone, sur la connexion de [SyncSession] (celle du partage de la feuille de scores : les scores
 * voyagent comme d'habitude, seules les cartes ont leurs messages, voir [UnoMessage]).
 *
 * - L'organisateur tient la partie ([UnoRound], dans le [ScoreViewModel]). Chaque invité choisit sa place à la table
 *   ([claim]) ; l'organisateur lui envoie sa vue de la table ([UnoRound.tableFor]) : sa main, mais jamais celle des autres.
 *   Les places que personne n'a prises restent jouées sur le téléphone de l'organisateur, qu'on se passe comme avant.
 * - L'invité ne modifie rien : il envoie sa pose, sa pioche ou sa passe, et l'organisateur les applique après avoir
 *   vérifié que c'est bien à lui de jouer.
 */
internal class UnoOnline(private val session: SyncSession) {

    // ---------- Organisateur ----------

    // La place de chaque téléphone connecté qui en a choisi une.
    private val seats = HashMap<String, Int>()

    /** Les places jouées sur un autre téléphone ; les autres se jouent sur celui de l'organisateur. */
    var remoteSeats by mutableStateOf<Set<Int>>(emptySet())
        private set

    // ---------- Invité ----------

    /** La table reçue de l'organisateur, vue de ma place ; null tant que rien n'est arrivé. */
    var table by mutableStateOf<UnoTable?>(null)
        private set

    /** Ma place à la table (-1 : pas encore choisie). */
    var mySeat by mutableIntStateOf(-1)
        private set

    /** Les places déjà prises par d'autres téléphones. */
    var taken by mutableStateOf<List<Int>>(emptyList())
        private set

    init {
        session.uno = this
    }

    /** Vrai si la place [seat] est jouée sur un autre téléphone. */
    fun isRemote(seat: Int): Boolean = seat in remoteSeats

    /** Remet tout à zéro (connexion coupée). */
    fun reset() {
        seats.clear()
        remoteSeats = emptySet()
        table = null
        mySeat = -1
        taken = emptyList()
    }

    // ---------- Ce que fait l'invité ----------

    fun claim(seat: Int) = session.sendToHost(SyncMessage.UnoMsg(UnoMessage.Claim(seat)))

    fun play(card: Int, color: Int = -1) = session.sendToHost(SyncMessage.UnoMsg(UnoMessage.Play(card, color)))

    fun draw() = session.sendToHost(SyncMessage.UnoMsg(UnoMessage.Draw))

    fun pass() = session.sendToHost(SyncMessage.UnoMsg(UnoMessage.Pass))

    fun next() = session.sendToHost(SyncMessage.UnoMsg(UnoMessage.Next))

    // ---------- Ce que rapporte la connexion ----------

    /** Envoie à chaque invité sa vue de la table ; à appeler à chaque changement de la partie (l'organisateur seulement). */
    fun publish() {
        if (session.role != SyncRole.HOST) return
        val round = hostRound() ?: return
        session.connected.forEach { sendView(it.id, round) }
    }

    private fun sendView(id: String, round: UnoRound) {
        val seat = seats[id] ?: -1
        session.sendTo(listOf(id), SyncMessage.UnoMsg(UnoMessage.View(round.tableFor(seat), seat, seats.values.toList())))
    }

    private fun hostRound(): UnoRound? {
        val game = session.attachedGame ?: return null
        if (!game.isGameActive || game.gameRules.id != UNO_ID) return null
        return game.unoRound()
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

    fun onMessage(id: String, message: UnoMessage) {
        when (session.role) {
            SyncRole.HOST -> onGuestAction(id, message)
            SyncRole.GUEST -> if (message is UnoMessage.View) {
                table = message.table
                mySeat = message.seat
                taken = message.taken
            }
            SyncRole.NONE -> Unit
        }
    }

    private fun onGuestAction(id: String, message: UnoMessage) {
        val game = session.attachedGame ?: return
        val round = hostRound() ?: return
        val seat = seats[id] ?: -1
        when (message) {
            is UnoMessage.Claim -> {
                val wanted = message.seat
                // Une place libre seulement : celle d'un autre téléphone ne se prend pas.
                val free = wanted in 0 until game.players.size && seats.none { it.key != id && it.value == wanted }
                if (free) seats[id] = wanted else if (wanted < 0) seats.remove(id)
                remoteSeats = seats.values.toSet()
            }
            is UnoMessage.Play -> if (seat >= 0) round.playFor(seat, message.card, message.color)
            UnoMessage.Draw -> if (seat >= 0) round.drawFor(seat)
            UnoMessage.Pass -> if (seat >= 0) round.passFor(seat)
            // Pas de manche suivante une fois la partie finie (500 points atteints).
            UnoMessage.Next -> if (round.settled && !game.isGameOver()) round.nextRound()
            is UnoMessage.View -> Unit
        }
        // Une manche finie est inscrite tout de suite, comme sur l'écran de l'organisateur.
        if (round.roundOver) round.settle(game)
        publish()
    }
}
