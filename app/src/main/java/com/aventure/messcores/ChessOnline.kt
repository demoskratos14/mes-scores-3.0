package com.aventure.messcores

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Échecs chacun sur son téléphone, sur la connexion de [SyncSession].
 *
 * - Deux joueurs exactement : l'organisateur ([isHost]) est la place 0, l'invité la place 1. La partie commence dès
 *   que l'invité est connecté ; les couleurs s'échangent à chaque manche (voir [ChessMatch]).
 * - L'organisateur tient la partie ([match]) et en envoie l'état complet à l'invité après chaque changement.
 *   L'invité ne modifie rien lui-même : il envoie son coup (ou son abandon, sa proposition de nulle…),
 *   l'organisateur le vérifie (tour, coup permis) puis renvoie l'état.
 */
internal class ChessOnline(private val session: SyncSession) {

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

    /** La manche en cours et le score de la séance. */
    var match by mutableStateOf(ChessMatch())
        private set

    var notice by mutableStateOf(ChessNotice.NONE)
        private set

    /** Instant (horloge du téléphone) où le joueur actuel a commencé à réfléchir : sert à la pendule. */
    var turnStart by mutableLongStateOf(0L)
        private set

    /** Nombre de fois qu'un invité est entré dans une partie : sert à ouvrir l'écran du jeu à son arrivée. */
    var joined by mutableIntStateOf(0)
        private set

    /** Vrai quand les deux joueurs sont là et que la partie peut se jouer. */
    val ready: Boolean get() = active && lobby.size >= PLAYERS

    /** Vrai si je joue les blancs dans la manche en cours. */
    val playsWhite: Boolean get() = match.playsWhite(seat)

    /** Vrai quand c'est à moi de jouer. */
    val myTurn: Boolean get() = ready && !match.isOver && match.currentSeat == seat

    // Organisateur : l'identifiant de connexion de l'invité.
    private var guestId: String? = null

    init {
        session.chess = this
    }

    // ---------- Organisateur ----------

    /** Ouvre la table : la connexion doit déjà être annoncée (voir [SyncSession.startHostingChess]). */
    fun openLobby(hostName: String) {
        resetLocal()
        active = true
        isHost = true
        seat = 0
        lobby = listOf(hostName)
    }

    // ---------- Les deux joueurs ----------

    /** Je joue [move] : l'organisateur l'applique, l'invité l'envoie à l'organisateur. */
    fun play(move: ChessMove) {
        if (!myTurn) return
        if (isHost) {
            match = match.play(move, seat, elapsed()) ?: return
            turnStart = SystemClock.elapsedRealtime()
            broadcast()
        } else {
            session.sendToHost(SyncMessage.Chess(ChessMessage.Move(move.toText())))
        }
    }

    /** Manche suivante, une fois la manche finie ; chacun des deux joueurs peut la lancer. */
    fun newRound() {
        if (!ready || !match.isOver) return
        if (isHost) {
            match = match.newRound() ?: return
            turnStart = SystemClock.elapsedRealtime()
            broadcast()
        } else {
            session.sendToHost(SyncMessage.Chess(ChessMessage.NewRound))
        }
    }

    /** Organisateur : choisit la pendule de la manche ([baseSec] = 0 : sans), tant que rien n'a été joué. */
    fun setClock(baseSec: Int, incSec: Int) {
        if (!ready || !isHost) return
        match = match.withClock(baseSec, incSec) ?: return
        turnStart = SystemClock.elapsedRealtime()
        broadcast()
    }

    /** Organisateur : à appeler souvent ; si le temps du joueur actuel est écoulé, il perd la manche. */
    fun checkClock() {
        if (!ready || !isHost || !match.clockRunning) return
        if (match.remainingMs(match.currentSeat, elapsed()) > 0L) return
        match = match.flag(match.currentSeat) ?: return
        broadcast()
    }

    private fun elapsed(): Long = SystemClock.elapsedRealtime() - turnStart

    /** J'abandonne la manche en cours. */
    fun resign() {
        if (!ready || match.isOver) return
        if (isHost) {
            match = match.resign(seat) ?: return
            broadcast()
        } else {
            session.sendToHost(SyncMessage.Chess(ChessMessage.Resign))
        }
    }

    /** Je propose la nulle à l'autre joueur. */
    fun offerDraw() {
        if (!ready || match.isOver || match.drawOffer != -1) return
        if (isHost) {
            match = match.offerDraw(seat) ?: return
            broadcast()
        } else {
            session.sendToHost(SyncMessage.Chess(ChessMessage.OfferDraw))
        }
    }

    /** J'accepte ([accept]) ou je refuse la nulle que l'autre joueur m'a proposée. */
    fun answerDraw(accept: Boolean) {
        if (!ready || match.isOver || match.drawOffer != 1 - seat) return
        if (isHost) {
            match = match.answerDraw(seat, accept) ?: return
            broadcast()
        } else {
            session.sendToHost(SyncMessage.Chess(ChessMessage.AnswerDraw(accept)))
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
        match = ChessMatch()
        notice = ChessNotice.NONE
        guestId = null
        turnStart = 0L
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
        match = ChessMatch()
        notice = ChessNotice.NONE
        turnStart = SystemClock.elapsedRealtime()
        broadcast()
    }

    fun onPeerDisconnected(id: String) {
        if (!active) return
        if (isHost) {
            if (id != guestId) return
            // L'invité parti, la table repart de zéro : un autre téléphone peut la rejoindre.
            guestId = null
            lobby = lobby.take(1)
            match = ChessMatch()
            notice = ChessNotice.PLAYER_LEFT
        } else {
            notice = ChessNotice.HOST_LEFT
        }
    }

    // ---------- Messages reçus ----------

    fun onMessage(fromId: String, message: ChessMessage) {
        if (isHost) onHostMessage(fromId, message) else onGuestMessage(message)
    }

    private fun onGuestMessage(message: ChessMessage) {
        if (message !is ChessMessage.Table) return
        val firstTime = !active
        active = true
        isHost = false
        seat = message.seat
        lobby = message.names
        match = message.match
        notice = message.notice
        turnStart = SystemClock.elapsedRealtime() - message.elapsedMs
        if (firstTime) joined++
    }

    private fun onHostMessage(fromId: String, message: ChessMessage) {
        if (!active || fromId != guestId) return
        when (message) {
            // Le coup est refusé hors du tour de l'invité ou s'il n'est pas permis.
            is ChessMessage.Move -> {
                val move = ChessMove.parse(message.move) ?: return
                match = match.play(move, GUEST_SEAT, elapsed()) ?: return
                turnStart = SystemClock.elapsedRealtime()
            }
            ChessMessage.NewRound -> {
                match = match.newRound() ?: return
                turnStart = SystemClock.elapsedRealtime()
            }
            ChessMessage.Resign -> match = match.resign(GUEST_SEAT) ?: return
            ChessMessage.OfferDraw -> match = match.offerDraw(GUEST_SEAT) ?: return
            is ChessMessage.AnswerDraw -> match = match.answerDraw(GUEST_SEAT, message.accept) ?: return
            is ChessMessage.Table -> return
        }
        broadcast()
    }

    private fun broadcast() {
        val id = guestId ?: return
        session.sendTo(listOf(id), SyncMessage.Chess(ChessMessage.Table(lobby, GUEST_SEAT, match, notice, elapsed())))
    }

    companion object {
        const val PLAYERS = 2
        private const val GUEST_SEAT = 1
    }
}
