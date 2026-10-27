package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Un jeu à deux joueurs au tour par tour (Gomoku, Pipopipette) chacun sur son téléphone, sur la connexion de
 * [SyncSession]. Une instance par jeu ([kind] dit lequel) ; la même logique sert aux deux, la séance de jeu ([DuoMatch])
 * étant seule à connaître les règles.
 *
 * - Deux joueurs exactement : l'organisateur ([isHost]) est le premier joueur, l'invité le deuxième. La partie commence
 *   dès que l'invité est connecté.
 * - L'organisateur tient la partie ([match]) et en envoie l'état complet à l'invité après chaque changement.
 *   L'invité ne modifie rien lui-même : il envoie son coup, l'organisateur le vérifie (tour, coup permis) puis
 *   renvoie l'état.
 * - Les manches s'enchaînent comme sur un seul téléphone : le premier à jouer change à chaque manche et le score
 *   de la séance reste affiché.
 */
internal class DuoOnline<M : DuoMatch<M>>(
    private val session: SyncSession,
    val kind: SyncKind,
    private val initial: () -> M,
    private val decode: (String) -> M?
) {

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

    /** La manche en cours et le score de la séance. */
    var match by mutableStateOf(initial())
        private set

    var notice by mutableStateOf(DuoNotice.NONE)
        private set

    /** Nombre de fois qu'un invité est entré dans une partie : sert à ouvrir l'écran du jeu à son arrivée. */
    var joined by mutableIntStateOf(0)
        private set

    /** Mon numéro de joueur : 1 pour l'organisateur, 2 pour l'invité. */
    val myPlayer: Int get() = seat + 1

    /** Vrai quand les deux joueurs sont là et que la partie peut se jouer. */
    val ready: Boolean get() = active && lobby.size >= PLAYERS

    /** Vrai quand c'est à moi de jouer. */
    val myTurn: Boolean get() = ready && !match.isOver && match.current == myPlayer

    // Organisateur : l'identifiant de connexion de l'invité.
    private var guestId: String? = null

    init {
        session.registerDuo(this)
    }

    // ---------- Organisateur ----------

    /** Ouvre la table : la connexion doit déjà être annoncée (voir [SyncSession.startHostingDuo]). */
    fun openLobby(hostName: String) {
        resetLocal()
        active = true
        isHost = true
        seat = 0
        lobby = listOf(hostName)
    }

    // ---------- Les deux joueurs ----------

    /** Je joue [move] : l'organisateur l'applique, l'invité l'envoie à l'organisateur. */
    fun play(move: Int) {
        if (!myTurn) return
        if (isHost) {
            match = match.play(move, myPlayer) ?: return
            broadcast()
        } else {
            session.sendToHost(SyncMessage.Duo(kind, DuoMessage.Move(move)))
        }
    }

    /** Manche suivante, une fois la manche finie ; chacun des deux joueurs peut la lancer. */
    fun newRound() {
        if (!ready || !match.isOver) return
        if (isHost) {
            match = match.newRound() ?: return
            broadcast()
        } else {
            session.sendToHost(SyncMessage.Duo(kind, DuoMessage.NewRound))
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
        match = initial()
        notice = DuoNotice.NONE
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
        match = initial()
        notice = DuoNotice.NONE
        broadcast()
    }

    fun onPeerDisconnected(id: String) {
        if (!active) return
        if (isHost) {
            if (id != guestId) return
            // L'invité parti, la table repart de zéro : un autre téléphone peut la rejoindre.
            guestId = null
            lobby = lobby.take(1)
            match = initial()
            notice = DuoNotice.PLAYER_LEFT
        } else {
            notice = DuoNotice.HOST_LEFT
        }
    }

    // ---------- Messages reçus ----------

    fun onMessage(fromId: String, message: DuoMessage) {
        if (isHost) onHostMessage(fromId, message) else onGuestMessage(message)
    }

    private fun onGuestMessage(message: DuoMessage) {
        if (message !is DuoMessage.Table) return
        // Une séance illisible est ignorée : on garde celle qu'on a.
        val received = decode(message.match) ?: return
        val firstTime = !active
        active = true
        isHost = false
        seat = message.seat
        lobby = message.names
        match = received
        notice = message.notice
        if (firstTime) joined++
    }

    private fun onHostMessage(fromId: String, message: DuoMessage) {
        if (!active || fromId != guestId) return
        when (message) {
            // L'invité est le deuxième joueur ; le coup est refusé hors de son tour ou s'il n'est pas permis.
            is DuoMessage.Move -> match = match.play(message.move, GUEST_PLAYER) ?: return
            DuoMessage.NewRound -> match = match.newRound() ?: return
            else -> return
        }
        broadcast()
    }

    private fun broadcast() {
        val id = guestId ?: return
        session.sendTo(listOf(id), SyncMessage.Duo(kind, DuoMessage.Table(lobby, 1, match.encode(), notice)))
    }

    companion object {
        const val PLAYERS = 2
        private const val GUEST_PLAYER = 2
    }
}
