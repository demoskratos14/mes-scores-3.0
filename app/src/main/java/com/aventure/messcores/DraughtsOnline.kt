package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Dames chacun sur son téléphone, sur la connexion de [SyncSession].
 *
 * - Deux joueurs exactement : l'organisateur ([isHost]) est le premier joueur (place 0), l'invité le deuxième
 *   (place 1). La partie commence dès que l'invité est connecté ; les couleurs s'échangent à chaque partie.
 * - L'organisateur tient la partie ([match]) et en envoie l'état complet à l'invité après chaque changement.
 *   L'invité ne modifie rien lui-même : il envoie son coup, l'organisateur le vérifie (tour, règles) puis
 *   renvoie l'état.
 */
internal class DraughtsOnline(private val session: SyncSession) {

    /** Vrai tant qu'une partie à deux téléphones est ouverte, de l'attente de l'autre joueur à la fin de la séance. */
    var active by mutableStateOf(false)
        private set

    var isHost by mutableStateOf(false)
        private set

    /** Ma place à la table (0 : organisateur ; 1 : invité), ou -1 hors partie. */
    var seat by mutableIntStateOf(-1)
        private set

    /** Les joueurs à la table, dans l'ordre des places. */
    var lobby by mutableStateOf<List<String>>(emptyList())
        private set

    /** La partie et le score de la séance. */
    var match by mutableStateOf(DraughtsMatch())
        private set

    var notice by mutableStateOf(DraughtsNotice.NONE)
        private set

    /** Nombre de fois qu'un invité est entré dans une partie : sert à ouvrir l'écran du jeu à son arrivée. */
    var joined by mutableIntStateOf(0)
        private set

    /** Mon camp dans la partie en cours : [Draughts.WHITE] ou [Draughts.BLACK] (0 hors partie). */
    val myColor: Int get() = if (seat < 0) 0 else match.colorOf(seat)

    /** Vrai quand les deux joueurs sont là et que la partie peut se jouer. */
    val ready: Boolean get() = active && lobby.size >= PLAYERS

    /** Vrai quand c'est à moi de jouer. */
    val myTurn: Boolean get() = ready && !match.isOver && match.currentSeat == seat

    // Organisateur : l'identifiant de connexion de l'invité.
    private var guestId: String? = null

    init {
        session.draughts = this
    }

    // ---------- Organisateur ----------

    /** Ouvre la table : la connexion doit déjà être annoncée (voir [SyncSession.startHostingDraughts]). */
    fun openLobby(hostName: String) {
        resetLocal()
        active = true
        isHost = true
        seat = 0
        lobby = listOf(hostName)
    }

    // ---------- Les deux joueurs ----------

    /** Je joue le coup qui suit [path] : l'organisateur l'applique, l'invité l'envoie à l'organisateur. */
    fun play(path: List<Int>) {
        if (!myTurn) return
        if (isHost) {
            match = match.play(path, seat) ?: return
            broadcast()
        } else {
            session.sendToHost(SyncMessage.Dr(DrMessage.Move(path)))
        }
    }

    /** J'abandonne la partie en cours. */
    fun resign() {
        if (!ready || match.isOver) return
        if (isHost) {
            match = match.resign(seat) ?: return
            broadcast()
        } else {
            session.sendToHost(SyncMessage.Dr(DrMessage.Resign))
        }
    }

    /** Partie suivante, une fois la partie finie ; chacun des deux joueurs peut la lancer. */
    fun newRound() {
        if (!ready || !match.isOver) return
        if (isHost) {
            match = match.newRound() ?: return
            broadcast()
        } else {
            session.sendToHost(SyncMessage.Dr(DrMessage.NewRound))
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
        match = DraughtsMatch()
        notice = DraughtsNotice.NONE
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
        match = DraughtsMatch()
        notice = DraughtsNotice.NONE
        broadcast()
    }

    fun onPeerDisconnected(id: String) {
        if (!active) return
        if (isHost) {
            if (id != guestId) return
            // L'invité parti, la table repart de zéro : un autre téléphone peut la rejoindre.
            guestId = null
            lobby = lobby.take(1)
            match = DraughtsMatch()
            notice = DraughtsNotice.PLAYER_LEFT
        } else {
            notice = DraughtsNotice.HOST_LEFT
        }
    }

    // ---------- Messages reçus ----------

    fun onMessage(fromId: String, message: DrMessage) {
        if (isHost) onHostMessage(fromId, message) else onGuestMessage(message)
    }

    private fun onGuestMessage(message: DrMessage) {
        if (message !is DrMessage.Table) return
        val firstTime = !active
        active = true
        isHost = false
        seat = message.seat
        lobby = message.names
        match = message.match
        notice = message.notice
        if (firstTime) joined++
    }

    private fun onHostMessage(fromId: String, message: DrMessage) {
        if (!active || fromId != guestId) return
        when (message) {
            // Le coup est refusé hors du tour de l'invité ou s'il est contraire aux règles.
            is DrMessage.Move -> match = match.play(message.path, GUEST_SEAT) ?: return
            DrMessage.Resign -> match = match.resign(GUEST_SEAT) ?: return
            DrMessage.NewRound -> match = match.newRound() ?: return
            else -> return
        }
        broadcast()
    }

    private fun broadcast() {
        val id = guestId ?: return
        session.sendTo(listOf(id), SyncMessage.Dr(DrMessage.Table(lobby, GUEST_SEAT, match, notice)))
    }

    companion object {
        const val PLAYERS = 2
        private const val GUEST_SEAT = 1
    }
}
