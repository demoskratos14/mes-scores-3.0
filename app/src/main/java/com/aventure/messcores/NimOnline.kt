package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Jeu des allumettes chacun sur son téléphone, sur la connexion de [SyncSession].
 *
 * - Deux joueurs exactement : l'organisateur ([isHost]) est le premier joueur, l'invité le deuxième. La partie commence
 *   dès que l'invité est connecté.
 * - L'organisateur tient la partie ([match]) et en envoie l'état complet à l'invité après chaque changement.
 *   L'invité ne modifie rien lui-même : il envoie son coup, l'organisateur le vérifie (tour, nombre d'allumettes) puis
 *   renvoie l'état.
 * - Les manches s'enchaînent comme sur un seul téléphone : le premier à jouer change à chaque manche et le score
 *   de la séance reste affiché.
 */
internal class NimOnline(private val session: SyncSession) {

    /** Vrai tant qu'une partie à deux téléphones est ouverte, de l'attente de l'autre joueur à la fin de la séance. */
    var active by mutableStateOf(false)
        private set

    var isHost by mutableStateOf(false)
        private set

    /** Ma place à la table (0 : organisateur, premier joueur ; 1 : invité, deuxième joueur), ou -1 hors partie. */
    var seat by mutableIntStateOf(-1)
        private set

    /** Les joueurs à la table, dans l'ordre des places. */
    var lobby by mutableStateOf<List<String>>(emptyList())
        private set

    /** La grille et le score de la séance. */
    var match by mutableStateOf(NimMatch())
        private set

    var notice by mutableStateOf(NimNotice.NONE)
        private set

    /** Nombre de fois qu'un invité est entré dans une partie : sert à ouvrir l'écran du jeu à son arrivée. */
    var joined by mutableIntStateOf(0)
        private set

    /** Mon numéro de joueur : 1 pour l'organisateur, 2 pour l'invité. */
    val myNumber: Int get() = seat + 1

    /** Vrai quand les deux joueurs sont là et que la partie peut se jouer. */
    val ready: Boolean get() = active && lobby.size >= PLAYERS

    /** Vrai quand c'est à moi de jouer. */
    val myTurn: Boolean get() = ready && !match.isOver && match.turn == myNumber

    // Organisateur : l'identifiant de connexion de l'invité.
    private var guestId: String? = null

    init {
        session.nim = this
    }

    // ---------- Organisateur ----------

    /** Ouvre la table : la connexion doit déjà être annoncée (voir [SyncSession.startHostingNim]). */
    fun openLobby(hostName: String) {
        resetLocal()
        active = true
        isHost = true
        seat = 0
        lobby = listOf(hostName)
    }

    // ---------- Les deux joueurs ----------

    /** Je retire [count] allumettes : l'organisateur applique le coup, l'invité l'envoie à l'organisateur. */
    fun take(count: Int) {
        if (!myTurn) return
        if (isHost) {
            match = match.play(count, myNumber) ?: return
            broadcast()
        } else {
            session.sendToHost(SyncMessage.Nim(NimMessage.Take(count)))
        }
    }

    /** Manche suivante, une fois la manche finie ; chacun des deux joueurs peut la lancer. */
    fun newRound() {
        if (!ready || !match.isOver) return
        if (isHost) {
            match = match.newRound() ?: return
            broadcast()
        } else {
            session.sendToHost(SyncMessage.Nim(NimMessage.NewRound))
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
        match = NimMatch()
        notice = NimNotice.NONE
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
        match = NimMatch()
        notice = NimNotice.NONE
        broadcast()
    }

    fun onPeerDisconnected(id: String) {
        if (!active) return
        if (isHost) {
            if (id != guestId) return
            // L'invité parti, la table repart de zéro : un autre téléphone peut la rejoindre.
            guestId = null
            lobby = lobby.take(1)
            match = NimMatch()
            notice = NimNotice.PLAYER_LEFT
        } else {
            notice = NimNotice.HOST_LEFT
        }
    }

    // ---------- Messages reçus ----------

    fun onMessage(fromId: String, message: NimMessage) {
        if (isHost) onHostMessage(fromId, message) else onGuestMessage(message)
    }

    private fun onGuestMessage(message: NimMessage) {
        if (message !is NimMessage.Table) return
        val firstTime = !active
        active = true
        isHost = false
        seat = message.seat
        lobby = message.names
        match = message.match
        notice = message.notice
        if (firstTime) joined++
    }

    private fun onHostMessage(fromId: String, message: NimMessage) {
        if (!active || fromId != guestId) return
        when (message) {
            // L'invité est le deuxième joueur ; le coup est refusé hors de son tour ou s'il retire trop d'allumettes.
            is NimMessage.Take -> match = match.play(message.count, GUEST_NUMBER) ?: return
            NimMessage.NewRound -> match = match.newRound() ?: return
            else -> return
        }
        broadcast()
    }

    private fun broadcast() {
        val id = guestId ?: return
        session.sendTo(listOf(id), SyncMessage.Nim(NimMessage.Table(lobby, 1, match, notice)))
    }

    companion object {
        const val PLAYERS = 2
        private const val GUEST_NUMBER = 2
    }
}
