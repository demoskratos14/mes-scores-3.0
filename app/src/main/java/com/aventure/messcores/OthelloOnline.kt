package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Othello chacun sur son téléphone, sur la connexion de [SyncSession].
 *
 * - Deux joueurs exactement : l'organisateur ([isHost], place 0) et l'invité (place 1). La partie commence dès que
 *   l'invité est connecté. L'organisateur joue les noirs à la première manche, puis les couleurs s'échangent.
 * - L'organisateur tient la partie ([match]) et en envoie l'état complet à l'invité après chaque changement.
 *   L'invité ne modifie rien lui-même : il envoie son coup, l'organisateur le vérifie (tour, case encadrante) puis
 *   renvoie l'état.
 * - Les passes de tour sont gérées par la séance ([OthelloMatch.play]) : un joueur sans coup possible est sauté.
 */
internal class OthelloOnline(private val session: SyncSession) {

    /** Vrai tant qu'une partie à deux téléphones est ouverte, de l'attente de l'autre joueur à la fin de la séance. */
    var active by mutableStateOf(false)
        private set

    var isHost by mutableStateOf(false)
        private set

    /** Ma place à la table (0 : organisateur, 1 : invité), ou -1 hors partie. */
    var seat by mutableIntStateOf(-1)
        private set

    /** Les joueurs à la table, dans l'ordre des places. */
    var lobby by mutableStateOf<List<String>>(emptyList())
        private set

    /** La grille et le score de la séance. */
    var match by mutableStateOf(OthelloMatch())
        private set

    var notice by mutableStateOf(OthelloNotice.NONE)
        private set

    /** Nombre de fois qu'un invité est entré dans une partie : sert à ouvrir l'écran du jeu à son arrivée. */
    var joined by mutableIntStateOf(0)
        private set

    /** Ma couleur pendant cette manche : 1 pour les noirs, 2 pour les blancs. */
    val myColor: Int get() = match.colorOf(seat)

    /** Vrai quand les deux joueurs sont là et que la partie peut se jouer. */
    val ready: Boolean get() = active && lobby.size >= PLAYERS

    /** Vrai quand c'est à moi de jouer. */
    val myTurn: Boolean get() = ready && !match.isOver && match.currentSeat == seat

    // Organisateur : l'identifiant de connexion de l'invité.
    private var guestId: String? = null

    init {
        session.othello = this
    }

    // ---------- Organisateur ----------

    /** Ouvre la table : la connexion doit déjà être annoncée (voir [SyncSession.startHostingOthello]). */
    fun openLobby(hostName: String) {
        resetLocal()
        active = true
        isHost = true
        seat = 0
        lobby = listOf(hostName)
    }

    // ---------- Les deux joueurs ----------

    /** Je joue la case [index] : l'organisateur l'applique, l'invité l'envoie à l'organisateur. */
    fun play(index: Int) {
        if (!myTurn) return
        if (isHost) {
            match = match.play(index, seat) ?: return
            broadcast()
        } else {
            session.sendToHost(SyncMessage.Oth(OthMessage.Move(index)))
        }
    }

    /** Manche suivante, une fois la manche finie ; chacun des deux joueurs peut la lancer. */
    fun newRound() {
        if (!ready || !match.isOver) return
        if (isHost) {
            match = match.newRound() ?: return
            broadcast()
        } else {
            session.sendToHost(SyncMessage.Oth(OthMessage.NewRound))
        }
    }

    /** Quitte la partie et coupe la connexion. */
    fun leave() {
        resetLocal()
        session.disconnect()
    }

    private fun resetLocal() {
        active = false
        isHost = false
        seat = -1
        lobby = emptyList()
        match = OthelloMatch()
        notice = OthelloNotice.NONE
        guestId = null
    }

    // ---------- Connexions (rapportées par la session) ----------

    fun onPeerConnected(id: String) {
        if (!active || !isHost) return
        // Une table à deux est pleine : personne d'autre n'y entre.
        if (guestId != null || lobby.size >= PLAYERS) {
            session.disconnectEndpoint(id)
            return
        }
        guestId = id
        lobby = PlayerNames.makeUnique(lobby + session.nameOf(id))
        match = OthelloMatch()
        notice = OthelloNotice.NONE
        broadcast()
    }

    fun onPeerDisconnected(id: String) {
        if (!active) return
        if (isHost) {
            if (id != guestId) return
            // L'invité parti, la table repart de zéro : un autre téléphone peut la rejoindre.
            guestId = null
            lobby = lobby.take(1)
            match = OthelloMatch()
            notice = OthelloNotice.PLAYER_LEFT
        } else {
            notice = OthelloNotice.HOST_LEFT
        }
    }

    // ---------- Messages reçus ----------

    fun onMessage(fromId: String, message: OthMessage) {
        if (isHost) onHostMessage(fromId, message) else onGuestMessage(message)
    }

    private fun onGuestMessage(message: OthMessage) {
        if (message !is OthMessage.Table) return
        val firstTime = !active
        active = true
        isHost = false
        seat = message.seat
        lobby = message.names
        match = message.match
        notice = message.notice
        if (firstTime) joined++
    }

    private fun onHostMessage(fromId: String, message: OthMessage) {
        if (!active || fromId != guestId) return
        when (message) {
            // Le coup est refusé hors du tour de l'invité ou s'il n'encadre aucun pion.
            is OthMessage.Move -> match = match.play(message.index, GUEST_SEAT) ?: return
            OthMessage.NewRound -> match = match.newRound() ?: return
            else -> return
        }
        broadcast()
    }

    private fun broadcast() {
        val id = guestId ?: return
        session.sendTo(listOf(id), SyncMessage.Oth(OthMessage.Table(lobby, GUEST_SEAT, match, notice)))
    }

    companion object {
        const val PLAYERS = 2
        private const val GUEST_SEAT = 1
    }
}
