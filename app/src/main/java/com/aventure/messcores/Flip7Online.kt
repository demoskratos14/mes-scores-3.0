package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Flip 7 chacun sur son téléphone, sur la connexion de [SyncSession] (celle du partage de la feuille de scores : les scores
 * voyagent comme d'habitude, seules les cartes ont leurs messages, voir [Flip7Message]).
 *
 * - L'organisateur tient la partie ([Flip7Round], dans le [ScoreViewModel]). Chaque invité choisit sa place à la table
 *   ([claim]) ; l'organisateur lui envoie la table ([Flip7Round.tableFor]), la même pour tous : rien n'y est caché. Les places
 *   que personne n'a prises restent jouées sur le téléphone de l'organisateur, qu'on se passe comme avant.
 * - L'invité ne modifie rien : il envoie sa pioche, son arrêt ou son choix de cible, et l'organisateur les applique après avoir
 *   vérifié que c'est bien à lui de jouer.
 */
internal class Flip7Online(private val session: SyncSession) {

    // ---------- Organisateur ----------

    // La place de chaque téléphone connecté qui en a choisi une.
    private val seats = HashMap<String, Int>()

    /** Les places jouées sur un autre téléphone ; les autres se jouent sur celui de l'organisateur. */
    var remoteSeats by mutableStateOf<Set<Int>>(emptySet())
        private set

    // ---------- Invité ----------

    /** La table reçue de l'organisateur ; null tant que rien n'est arrivé. */
    var table by mutableStateOf<Flip7Table?>(null)
        private set

    /** Ma place à la table (-1 : pas encore choisie). */
    var mySeat by mutableIntStateOf(-1)
        private set

    /** Les places déjà prises par d'autres téléphones. */
    var taken by mutableStateOf<List<Int>>(emptyList())
        private set

    init {
        session.flip7 = this
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

    fun claim(seat: Int) = session.sendToHost(SyncMessage.Flip7Msg(Flip7Message.Claim(seat)))

    fun hit() = session.sendToHost(SyncMessage.Flip7Msg(Flip7Message.Hit))

    fun stay() = session.sendToHost(SyncMessage.Flip7Msg(Flip7Message.Stay))

    fun choose(target: Int) = session.sendToHost(SyncMessage.Flip7Msg(Flip7Message.Choose(target)))

    fun next() = session.sendToHost(SyncMessage.Flip7Msg(Flip7Message.Next))

    // ---------- Ce que rapporte la connexion ----------

    /** Envoie la table à chaque invité ; à appeler à chaque changement de la partie (l'organisateur seulement). */
    fun publish() {
        if (session.role != SyncRole.HOST) return
        val round = hostRound() ?: return
        session.connected.forEach { sendView(it.id, round) }
    }

    private fun sendView(id: String, round: Flip7Round) {
        val seat = seats[id] ?: -1
        session.sendTo(listOf(id), SyncMessage.Flip7Msg(Flip7Message.View(round.tableFor(), seat, seats.values.toList())))
    }

    private fun hostRound(): Flip7Round? {
        val game = session.attachedGame ?: return null
        if (!game.isGameActive || game.gameRules.id != FLIP7_ID) return null
        return game.flip7Round()
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

    fun onMessage(id: String, message: Flip7Message) {
        when (session.role) {
            SyncRole.HOST -> onGuestAction(id, message)
            SyncRole.GUEST -> if (message is Flip7Message.View) {
                table = message.table
                mySeat = message.seat
                taken = message.taken
            }
            SyncRole.NONE -> Unit
        }
    }

    private fun onGuestAction(id: String, message: Flip7Message) {
        val game = session.attachedGame ?: return
        val round = hostRound() ?: return
        val seat = seats[id] ?: -1
        when (message) {
            is Flip7Message.Claim -> {
                val wanted = message.seat
                // Une place libre seulement : celle d'un autre téléphone ne se prend pas.
                val free = wanted in 0 until game.players.size && seats.none { it.key != id && it.value == wanted }
                if (free) seats[id] = wanted else if (wanted < 0) seats.remove(id)
                remoteSeats = seats.values.toSet()
            }
            Flip7Message.Hit -> if (seat >= 0) round.hitFor(seat)
            Flip7Message.Stay -> if (seat >= 0) round.stayFor(seat)
            is Flip7Message.Choose -> if (seat >= 0) round.chooseTargetFor(seat, message.target)
            // Pas de manche suivante une fois la partie finie (200 points atteints).
            Flip7Message.Next -> if (round.settled && !game.isGameOver()) round.nextRound()
            is Flip7Message.View -> Unit
        }
        // Une manche finie est inscrite tout de suite, comme sur l'écran de l'organisateur.
        if (round.roundOver) round.settle(game)
        publish()
    }
}
