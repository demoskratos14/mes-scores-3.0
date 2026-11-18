package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Le poker chacun sur son téléphone, sur la connexion de [SyncSession] (celle du partage de la feuille de scores : noms et
 * couleurs des joueurs voyagent comme d'habitude, seules les cartes et les actions ont leurs messages, voir [PokerMessage]).
 *
 * - L'organisateur tient la partie ([PokerSession], dans le [ScoreViewModel]) et donc toutes les cartes. Chaque invité choisit sa
 *   place à la table ([claim]) ; l'organisateur lui envoie la table vue de sa place ([PokerEngine.viewFor]) : ses cartes, jamais
 *   celles des autres. Les places que personne n'a prises restent jouées sur le téléphone de l'organisateur, qu'on se passe.
 * - L'invité ne modifie rien : il envoie son action, et l'organisateur l'applique après avoir vérifié que c'est bien à lui de parler.
 */
internal class PokerOnline(private val session: SyncSession) {

    // ---------- Organisateur ----------

    // La place de chaque téléphone connecté qui en a choisi une.
    private val seats = HashMap<String, Int>()

    /** Les places jouées sur un autre téléphone ; les autres se jouent sur celui de l'organisateur. */
    var remoteSeats by mutableStateOf<Set<Int>>(emptySet())
        private set

    // ---------- Invité ----------

    /** La table reçue de l'organisateur, vue de ma place ; null tant que rien n'est arrivé. */
    var table by mutableStateOf<PokerGame?>(null)
        private set

    /** Ma place à la table (-1 : pas encore choisie). */
    var mySeat by mutableIntStateOf(-1)
        private set

    /** Les places déjà prises par d'autres téléphones. */
    var taken by mutableStateOf<List<Int>>(emptyList())
        private set

    init {
        session.poker = this
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

    fun claim(seat: Int) = session.sendToHost(SyncMessage.PokerMsg(PokerMessage.Claim(seat)))

    fun fold() = session.sendToHost(SyncMessage.PokerMsg(PokerMessage.Fold))

    fun check() = session.sendToHost(SyncMessage.PokerMsg(PokerMessage.Check))

    fun call() = session.sendToHost(SyncMessage.PokerMsg(PokerMessage.Call))

    fun allIn() = session.sendToHost(SyncMessage.PokerMsg(PokerMessage.AllIn))

    fun raiseTo(to: Int) = session.sendToHost(SyncMessage.PokerMsg(PokerMessage.Raise(to)))

    fun showdown() = session.sendToHost(SyncMessage.PokerMsg(PokerMessage.Showdown))

    fun nextHand() = session.sendToHost(SyncMessage.PokerMsg(PokerMessage.NextHand))

    // ---------- Ce que rapporte la connexion ----------

    /** Envoie à chaque invité la table vue de sa place ; à appeler à chaque changement de la partie (l'organisateur seulement). */
    fun publish() {
        if (session.role != SyncRole.HOST) return
        val game = hostGame() ?: return
        session.connected.forEach { sendView(it.id, game) }
    }

    private fun sendView(id: String, game: PokerGame) {
        val seat = seats[id] ?: -1
        session.sendTo(listOf(id), SyncMessage.PokerMsg(PokerMessage.View(PokerEngine.viewFor(game, seat), seat, seats.values.toList())))
    }

    private fun hostGame(): PokerGame? = session.attachedGame?.pokerSession?.game

    fun onPeerConnected(id: String) {
        if (session.role != SyncRole.HOST) return
        val game = hostGame() ?: return
        sendView(id, game)
    }

    fun onPeerDisconnected(id: String) {
        if (seats.remove(id) != null) {
            remoteSeats = seats.values.toSet()
            publish()
        }
    }

    fun onMessage(id: String, message: PokerMessage) {
        when (session.role) {
            SyncRole.HOST -> onGuestAction(id, message)
            SyncRole.GUEST -> if (message is PokerMessage.View) {
                table = message.game
                mySeat = message.seat
                taken = message.taken
            }
            SyncRole.NONE -> Unit
        }
    }

    private fun onGuestAction(id: String, message: PokerMessage) {
        val viewModel = session.attachedGame ?: return
        val poker = viewModel.pokerSession
        val game = poker.game ?: return
        val seat = seats[id] ?: -1
        val myTurn = seat >= 0 && game.phase == PokerPhase.BETTING && game.toAct == seat
        when (message) {
            is PokerMessage.Claim -> {
                val wanted = message.seat
                // Une place libre seulement : celle d'un autre téléphone ne se prend pas, ni celle d'un joueur éliminé.
                val free = wanted in game.seats.indices && !game.seats[wanted].out &&
                    seats.none { it.key != id && it.value == wanted }
                if (free) seats[id] = wanted else if (wanted < 0) seats.remove(id)
                remoteSeats = seats.values.toSet()
            }
            PokerMessage.Fold -> if (myTurn) poker.fold()
            PokerMessage.Check -> if (myTurn) poker.check()
            PokerMessage.Call -> if (myTurn) poker.call()
            PokerMessage.AllIn -> if (myTurn) poker.allIn()
            is PokerMessage.Raise -> if (myTurn) poker.raiseTo(message.to)
            // L'abattage et la main suivante : n'importe quel joueur assis les demande.
            PokerMessage.Showdown -> if (seat >= 0 && game.phase == PokerPhase.SHOWDOWN && game.cardsDealt) poker.resolveAuto()
            PokerMessage.NextHand -> if (seat >= 0 && game.phase == PokerPhase.HAND_OVER) poker.nextHand()
            is PokerMessage.View -> Unit
        }
        publish()
    }
}
