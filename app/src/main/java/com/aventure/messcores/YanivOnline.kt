package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Le Yaniv chacun sur son téléphone, sur la connexion de [SyncSession] (celle du partage de la feuille de scores : les scores
 * voyagent comme d'habitude, seules les cartes ont leurs messages, voir [YanivMessage]).
 *
 * - L'organisateur tient la partie ([YanivRound], dans le [ScoreViewModel]). Chaque invité choisit sa place à la table
 *   ([claim]) ; l'organisateur lui envoie sa vue de la table ([YanivRound.tableFor]) : sa main, mais jamais celle des autres.
 *   Les places que personne n'a prises restent jouées sur le téléphone de l'organisateur, qu'on se passe comme avant.
 * - L'invité ne modifie rien : il envoie sa défausse, sa pioche ou son « Yaniv », et l'organisateur les applique après avoir
 *   vérifié que c'est bien à lui de jouer.
 */
internal class YanivOnline(private val session: SyncSession) {

    // ---------- Organisateur ----------

    // La place de chaque téléphone connecté qui en a choisi une.
    private val seats = HashMap<String, Int>()

    /** Les places jouées sur un autre téléphone ; les autres se jouent sur celui de l'organisateur. */
    var remoteSeats by mutableStateOf<Set<Int>>(emptySet())
        private set

    // ---------- Invité ----------

    /** La table reçue de l'organisateur, vue de ma place ; null tant que rien n'est arrivé. */
    var table by mutableStateOf<YanivTable?>(null)
        private set

    /** Ma place à la table (-1 : pas encore choisie). */
    var mySeat by mutableIntStateOf(-1)
        private set

    /** Les places déjà prises par d'autres téléphones. */
    var taken by mutableStateOf<List<Int>>(emptyList())
        private set

    init {
        session.yaniv = this
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

    fun claim(seat: Int) = session.sendToHost(SyncMessage.YanivMsg(YanivMessage.Claim(seat)))

    fun discard(cards: List<Int>) = session.sendToHost(SyncMessage.YanivMsg(YanivMessage.Discard(cards)))

    fun drawFromStock() = session.sendToHost(SyncMessage.YanivMsg(YanivMessage.DrawStock))

    fun takeFromDiscard(card: Int) = session.sendToHost(SyncMessage.YanivMsg(YanivMessage.TakeDiscard(card)))

    fun callYaniv() = session.sendToHost(SyncMessage.YanivMsg(YanivMessage.CallYaniv))

    fun next() = session.sendToHost(SyncMessage.YanivMsg(YanivMessage.Next))

    // ---------- Ce que rapporte la connexion ----------

    /** Envoie à chaque invité sa vue de la table ; à appeler à chaque changement de la partie (l'organisateur seulement). */
    fun publish() {
        if (session.role != SyncRole.HOST) return
        val round = hostRound() ?: return
        session.connected.forEach { sendView(it.id, round) }
    }

    private fun sendView(id: String, round: YanivRound) {
        val seat = seats[id] ?: -1
        session.sendTo(listOf(id), SyncMessage.YanivMsg(YanivMessage.View(round.tableFor(seat), seat, seats.values.toList())))
    }

    private fun hostRound(): YanivRound? {
        val game = session.attachedGame ?: return null
        if (!game.isGameActive || game.gameRules.id != YANIV_ID) return null
        return game.yanivRound()
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

    fun onMessage(id: String, message: YanivMessage) {
        when (session.role) {
            SyncRole.HOST -> onGuestAction(id, message)
            SyncRole.GUEST -> if (message is YanivMessage.View) {
                table = message.table
                mySeat = message.seat
                taken = message.taken
            }
            SyncRole.NONE -> Unit
        }
    }

    private fun onGuestAction(id: String, message: YanivMessage) {
        val game = session.attachedGame ?: return
        val round = hostRound() ?: return
        val seat = seats[id] ?: -1
        when (message) {
            is YanivMessage.Claim -> {
                val wanted = message.seat
                // Une place libre seulement : celle d'un autre téléphone ne se prend pas.
                val free = wanted in 0 until game.players.size && seats.none { it.key != id && it.value == wanted }
                if (free) seats[id] = wanted else if (wanted < 0) seats.remove(id)
                remoteSeats = seats.values.toSet()
            }
            is YanivMessage.Discard -> if (seat >= 0) round.discardFor(seat, message.cards)
            YanivMessage.DrawStock -> if (seat >= 0) round.drawFromStockFor(seat)
            is YanivMessage.TakeDiscard -> if (seat >= 0) round.takeFromDiscardFor(seat, message.card)
            YanivMessage.CallYaniv -> if (seat >= 0) round.callYanivFor(seat)
            // Pas de manche suivante une fois la partie finie (plus de 200 points).
            YanivMessage.Next -> if (round.settled && !game.isGameOver()) round.nextRound()
            is YanivMessage.View -> Unit
        }
        // Une manche finie est inscrite tout de suite, comme sur l'écran de l'organisateur.
        if (round.roundOver) round.settle(game)
        publish()
    }
}
