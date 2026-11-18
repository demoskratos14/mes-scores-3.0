package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Barbu chacun sur son téléphone, sur la connexion de [SyncSession] (celle du partage de la feuille de scores : les scores
 * voyagent comme d'habitude, seules les cartes ont leurs messages, voir [BarbuMsg]).
 *
 * - L'organisateur tient la partie ([BarbuRound], dans le [ScoreViewModel]). Chaque invité choisit sa place à la table
 *   ([claim]) ; l'organisateur lui envoie sa vue de la table ([BarbuRound.tableFor]) : sa main, mais jamais celle des autres.
 *   Les places que personne n'a prises restent jouées sur le téléphone de l'organisateur, qu'on se passe comme avant.
 * - L'invité ne modifie rien : il envoie son contrat, sa carte posée ou le pli qu'il ramasse, et l'organisateur les applique
 *   après avoir vérifié que c'est bien à lui d'agir.
 */
internal class BarbuOnline(private val session: SyncSession) {

    // ---------- Organisateur ----------

    // La place de chaque téléphone connecté qui en a choisi une.
    private val seats = HashMap<String, Int>()

    /** Les places jouées sur un autre téléphone ; les autres se jouent sur celui de l'organisateur. */
    var remoteSeats by mutableStateOf<Set<Int>>(emptySet())
        private set

    // ---------- Invité ----------

    /** La table reçue de l'organisateur, vue de ma place ; null tant que rien n'est arrivé. */
    var table by mutableStateOf<BarbuTable?>(null)
        private set

    /** Ma place à la table (-1 : pas encore choisie). */
    var mySeat by mutableIntStateOf(-1)
        private set

    /** Les places déjà prises par d'autres téléphones. */
    var taken by mutableStateOf<List<Int>>(emptyList())
        private set

    init {
        session.barbu = this
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

    fun claim(seat: Int) = session.sendToHost(SyncMessage.Barbu(BarbuMsg.Claim(seat)))

    fun choose(contract: BarbuContract) = session.sendToHost(SyncMessage.Barbu(BarbuMsg.Choose(contract.ordinal)))

    fun play(card: Int) = session.sendToHost(SyncMessage.Barbu(BarbuMsg.Play(card)))

    fun collect() = session.sendToHost(SyncMessage.Barbu(BarbuMsg.Collect))

    fun next() = session.sendToHost(SyncMessage.Barbu(BarbuMsg.Next))

    // ---------- Ce que rapporte la connexion ----------

    /** Envoie à chaque invité sa vue de la table ; à appeler à chaque changement de la partie (l'organisateur seulement). */
    fun publish() {
        if (session.role != SyncRole.HOST) return
        val round = hostRound() ?: return
        session.connected.forEach { sendView(it.id, round) }
    }

    private fun sendView(id: String, round: BarbuRound) {
        val seat = seats[id] ?: -1
        session.sendTo(listOf(id), SyncMessage.Barbu(BarbuMsg.View(round.tableFor(seat), seat, seats.values.toList())))
    }

    private fun hostRound(): BarbuRound? {
        val game = session.attachedGame ?: return null
        if (!game.isGameActive || game.gameRules.id != BARBU_ID) return null
        return game.barbuRound()
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

    fun onMessage(id: String, message: BarbuMsg) {
        when (session.role) {
            SyncRole.HOST -> onGuestAction(id, message)
            SyncRole.GUEST -> if (message is BarbuMsg.View) {
                table = message.table
                mySeat = message.seat
                taken = message.taken
            }
            SyncRole.NONE -> Unit
        }
    }

    private fun onGuestAction(id: String, message: BarbuMsg) {
        val game = session.attachedGame ?: return
        val round = hostRound() ?: return
        val seat = seats[id] ?: -1
        when (message) {
            is BarbuMsg.Claim -> {
                val wanted = message.seat
                // Une place libre seulement : celle d'un autre téléphone ne se prend pas.
                val free = wanted in 0 until game.players.size && seats.none { it.key != id && it.value == wanted }
                if (free) seats[id] = wanted else if (wanted < 0) seats.remove(id)
                remoteSeats = seats.values.toSet()
            }
            is BarbuMsg.Choose -> {
                val chosen = BarbuContract.entries.getOrNull(message.contract)
                if (seat >= 0 && chosen != null) round.chooseFor(seat, chosen)
            }
            is BarbuMsg.Play -> if (seat >= 0) round.playFor(seat, message.card)
            BarbuMsg.Collect -> if (seat >= 0) round.collectFor(seat)
            BarbuMsg.Next -> if (round.settled) round.nextRound()
            is BarbuMsg.View -> Unit
        }
        // Une donne finie est inscrite tout de suite, comme sur l'écran de l'organisateur.
        if (round.roundOver) round.settle(game)
        publish()
    }
}
