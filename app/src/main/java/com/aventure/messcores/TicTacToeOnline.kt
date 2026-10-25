package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Morpion chacun sur son téléphone, sur la connexion de [SyncSession].
 *
 * - Deux joueurs exactement : l'organisateur ([isHost]) joue les croix, l'invité les ronds. La partie commence
 *   dès que l'invité est connecté.
 * - L'organisateur tient la partie ([match]) et en envoie l'état complet à l'invité après chaque changement.
 *   L'invité ne modifie rien lui-même : il envoie son coup, l'organisateur le vérifie (tour, case libre) puis
 *   renvoie l'état.
 * - Les manches s'enchaînent comme sur un seul téléphone : le premier à jouer change à chaque manche et le score
 *   de la séance reste affiché.
 */
internal class TicTacToeOnline(private val session: SyncSession) {

    /** Vrai tant qu'une partie à deux téléphones est ouverte, de l'attente de l'autre joueur à la fin de la séance. */
    var active by mutableStateOf(false)
        private set

    var isHost by mutableStateOf(false)
        private set

    /** Ma place à la table (0 : organisateur, croix ; 1 : invité, ronds), ou -1 hors partie. */
    var seat by mutableIntStateOf(-1)
        private set

    /** Les joueurs à la table, dans l'ordre des places. */
    var lobby by mutableStateOf<List<String>>(emptyList())
        private set

    /** La grille et le score de la séance. */
    var match by mutableStateOf(TicTacToeMatch())
        private set

    var notice by mutableStateOf(TicTacToeNotice.NONE)
        private set

    /** Nombre de fois qu'un invité est entré dans une partie : sert à ouvrir l'écran du jeu à son arrivée. */
    var joined by mutableIntStateOf(0)
        private set

    /** Mon symbole : 1 pour les croix, 2 pour les ronds. */
    val mySymbol: Int get() = seat + 1

    /** Vrai quand les deux joueurs sont là et que la partie peut se jouer. */
    val ready: Boolean get() = active && lobby.size >= PLAYERS

    /** Vrai quand c'est à moi de jouer. */
    val myTurn: Boolean get() = ready && !match.isOver && match.current == mySymbol

    // Organisateur : l'identifiant de connexion de l'invité.
    private var guestId: String? = null

    init {
        session.ticTacToe = this
    }

    // ---------- Organisateur ----------

    /** Ouvre la table : la connexion doit déjà être annoncée (voir [SyncSession.startHostingTicTacToe]). */
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
            match = match.play(index, mySymbol) ?: return
            broadcast()
        } else {
            session.sendToHost(SyncMessage.Ttt(TttMessage.Move(index)))
        }
    }

    /** Manche suivante, une fois la manche finie ; chacun des deux joueurs peut la lancer. */
    fun newRound() {
        if (!ready || !match.isOver) return
        if (isHost) {
            match = match.newRound() ?: return
            broadcast()
        } else {
            session.sendToHost(SyncMessage.Ttt(TttMessage.NewRound))
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
        match = TicTacToeMatch()
        notice = TicTacToeNotice.NONE
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
        match = TicTacToeMatch()
        notice = TicTacToeNotice.NONE
        broadcast()
    }

    fun onPeerDisconnected(id: String) {
        if (!active) return
        if (isHost) {
            if (id != guestId) return
            // L'invité parti, la table repart de zéro : un autre téléphone peut la rejoindre.
            guestId = null
            lobby = lobby.take(1)
            match = TicTacToeMatch()
            notice = TicTacToeNotice.PLAYER_LEFT
        } else {
            notice = TicTacToeNotice.HOST_LEFT
        }
    }

    // ---------- Messages reçus ----------

    fun onMessage(fromId: String, message: TttMessage) {
        if (isHost) onHostMessage(fromId, message) else onGuestMessage(message)
    }

    private fun onGuestMessage(message: TttMessage) {
        if (message !is TttMessage.Table) return
        val firstTime = !active
        active = true
        isHost = false
        seat = message.seat
        lobby = message.names
        match = message.match
        notice = message.notice
        if (firstTime) joined++
    }

    private fun onHostMessage(fromId: String, message: TttMessage) {
        if (!active || fromId != guestId) return
        when (message) {
            // L'invité joue les ronds ; le coup est refusé hors de son tour ou sur une case prise.
            is TttMessage.Move -> match = match.play(message.index, GUEST_SYMBOL) ?: return
            TttMessage.NewRound -> match = match.newRound() ?: return
            else -> return
        }
        broadcast()
    }

    private fun broadcast() {
        val id = guestId ?: return
        session.sendTo(listOf(id), SyncMessage.Ttt(TttMessage.Table(lobby, 1, match, notice)))
    }

    companion object {
        const val PLAYERS = 2
        private const val GUEST_SYMBOL = 2
    }
}
