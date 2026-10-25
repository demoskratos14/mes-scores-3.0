package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/**
 * Touché coulé chacun sur son téléphone, sur la connexion de [SyncSession] (deux joueurs).
 *
 * - Chaque joueur place sa flotte sur son téléphone : elle n'en sort jamais pendant la partie.
 * - Le tireur envoie la case visée ; le téléphone du défenseur répond (à l'eau, touché, coulé) et dit si sa flotte
 *   est entièrement coulée. Les deux téléphones appliquent ensuite la même règle pour savoir qui tire.
 * - Fin de partie : chacun dévoile sa flotte, et chaque téléphone vérifie que les réponses reçues correspondent à la
 *   flotte dévoilée ([opponentHonest]).
 * - L'organisateur ([isHost], place 0) ouvre la salle d'attente et lance la partie ; l'invité a la place 1.
 *
 * @param chooseFirst tire au sort le joueur (0 ou 1) qui tirera le premier ; remplaçable dans les tests.
 */
internal class BattleshipOnline(
    private val session: SyncSession,
    private val chooseFirst: () -> Int = { Random.nextInt(2) }
) {

    /** Vrai tant qu'une partie à deux téléphones est ouverte, de la salle d'attente aux résultats. */
    var active by mutableStateOf(false)
        private set

    var isHost by mutableStateOf(false)
        private set

    /** Ma place à la table (0 pour l'organisateur, 1 pour l'invité), ou -1 hors partie. */
    var seat by mutableIntStateOf(-1)
        private set

    /** Les joueurs à la table, dans l'ordre des places. */
    var lobby by mutableStateOf<List<String>>(emptyList())
        private set

    var settings by mutableStateOf(BattleshipSettings())
        private set

    var notice by mutableStateOf(BattleshipNotice.NONE)
        private set

    /** Nombre de fois qu'un invité est entré dans une partie : sert à ouvrir l'écran du jeu à son arrivée. */
    var joined by mutableIntStateOf(0)
        private set

    // ---------- La partie (sur ce téléphone) ----------

    /** Vrai une fois la partie lancée par l'organisateur (placement, tirs, résultats) ; faux dans la salle d'attente. */
    var started by mutableStateOf(false)
        private set

    var phase by mutableStateOf(BattleshipPhase.PLACEMENT)
        private set

    /** Place du joueur qui tire (phase [BattleshipPhase.PLAYING]). */
    var turn by mutableIntStateOf(0)
        private set

    /** Ma flotte, une fois placée : elle n'est envoyée à l'adversaire qu'à la fin de la partie. */
    var myFleet by mutableStateOf<List<List<Int>>?>(null)
        private set

    /** Vrai quand l'adversaire a placé sa flotte. */
    var opponentReady by mutableStateOf(false)
        private set

    /** Mes tirs sur la flotte adverse, avec les réponses reçues. */
    var myShots by mutableStateOf<List<BattleshipShot>>(emptyList())
        private set

    /** Les tirs de l'adversaire sur ma flotte, avec mes réponses. */
    var shotsAtMe by mutableStateOf<List<BattleshipShot>>(emptyList())
        private set

    /** La case que je viens de viser, tant que l'adversaire n'a pas répondu ; null sinon. */
    var pendingShot by mutableStateOf<Int?>(null)
        private set

    /** Place du joueur qui a gagné, ou -1. */
    var winner by mutableIntStateOf(-1)
        private set

    /** La flotte de l'adversaire, dévoilée en fin de partie ; null avant. */
    var opponentFleet by mutableStateOf<List<List<Int>>?>(null)
        private set

    /** Le nom de l'autre joueur (vide tant qu'il n'est pas là). */
    val opponentName: String get() = lobby.getOrNull(1 - seat).orEmpty()

    /** Vrai si c'est à moi de tirer. */
    val myTurn: Boolean get() = started && phase == BattleshipPhase.PLAYING && turn == seat

    /**
     * Une fois la flotte adverse dévoilée : vrai si toutes ses réponses à mes tirs sont celles que cette flotte devait
     * donner, faux si l'adversaire a menti ; null tant qu'elle n'est pas dévoilée.
     */
    val opponentHonest: Boolean?
        get() = opponentFleet?.let { Battleship.isValidFleet(it) && Battleship.isConsistent(it, myShots) }

    // Organisateur : l'identifiant de connexion de l'invité.
    private var peerId: String? = null

    init {
        session.battleship = this
    }

    // ---------- Organisateur ----------

    /** Ouvre la salle d'attente : la connexion doit déjà être annoncée (voir [SyncSession.startHostingBattleship]). */
    fun openLobby(hostName: String, newSettings: BattleshipSettings) {
        resetLocal()
        active = true
        isHost = true
        seat = 0
        lobby = listOf(hostName)
        settings = newSettings
    }

    /** Lance la partie quand l'autre joueur est là : chacun place sa flotte. */
    fun startGame() {
        if (!isHost || started || lobby.size < PLAYERS) return
        notice = BattleshipNotice.NONE
        val first = chooseFirst().coerceIn(0, 1)
        beginGame(first)
        send(BsMessage.Start(first))
    }

    /** Rejouer avec le même adversaire : retour à la salle d'attente, de nouvelles flottes sont à placer. */
    fun replay() {
        if (!isHost) return
        resetGame()
        notice = BattleshipNotice.NONE
        broadcastLobby()
    }

    // ---------- Tous les joueurs ----------

    /** Enregistre ma flotte (elle reste sur ce téléphone) et prévient l'adversaire qu'elle est placée. */
    fun submitFleet(ships: List<List<Int>>) {
        if (!started || phase != BattleshipPhase.PLACEMENT || myFleet != null || !Battleship.isValidFleet(ships)) return
        myFleet = ships.map { it.sorted() }
        checkReady()
        send(BsMessage.Ready)
    }

    /** Vise la case [cell] : la réponse arrive de l'adversaire. */
    fun fire(cell: Int) {
        if (!myTurn || pendingShot != null || cell !in 0 until Battleship.CELLS || myShots.any { it.cell == cell }) return
        // La case est notée avant l'envoi : la réponse peut arriver avant la fin de l'envoi.
        pendingShot = cell
        send(BsMessage.Fire(cell))
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
        notice = BattleshipNotice.NONE
        peerId = null
        resetGame()
    }

    private fun resetGame() {
        started = false
        phase = BattleshipPhase.PLACEMENT
        turn = 0
        myFleet = null
        opponentReady = false
        myShots = emptyList()
        shotsAtMe = emptyList()
        pendingShot = null
        winner = -1
        opponentFleet = null
    }

    private fun beginGame(first: Int) {
        resetGame()
        started = true
        phase = BattleshipPhase.PLACEMENT
        turn = first
    }

    /** Les deux flottes sont placées : on tire. */
    private fun checkReady() {
        if (phase == BattleshipPhase.PLACEMENT && myFleet != null && opponentReady) phase = BattleshipPhase.PLAYING
    }

    // ---------- Connexions (rapportées par la session) ----------

    fun onPeerConnected(id: String) {
        if (!active || !isHost) return
        // Une partie lancée ou une table pleine n'accepte plus personne.
        if (started || lobby.size >= PLAYERS) {
            session.disconnectEndpoint(id)
            return
        }
        peerId = id
        lobby = PlayerNames.makeUnique(lobby + session.nameOf(id))
        broadcastLobby()
    }

    fun onPeerDisconnected(id: String) {
        if (!active) return
        if (isHost) {
            if (id != peerId) return
            peerId = null
            lobby = lobby.take(1)
            // Un joueur parti en pleine partie la rend impossible à finir : retour à la salle d'attente.
            notice = if (started) BattleshipNotice.PLAYER_LEFT else BattleshipNotice.NONE
            resetGame()
        } else {
            notice = BattleshipNotice.HOST_LEFT
            resetGame()
        }
    }

    // ---------- Messages reçus ----------

    fun onMessage(fromId: String, message: BsMessage) {
        if (message is BsMessage.Lobby) {
            if (!isHost) receiveLobby(message)
            return
        }
        if (!active || (isHost && fromId != peerId)) return
        when (message) {
            is BsMessage.Lobby -> Unit
            is BsMessage.Start -> if (!isHost && !started) beginGame(message.first.coerceIn(0, 1))
            BsMessage.Ready -> {
                if (started && phase == BattleshipPhase.PLACEMENT) {
                    opponentReady = true
                    checkReady()
                }
            }
            is BsMessage.Fire -> onFire(message.cell)
            is BsMessage.Report -> onReport(message.shot, message.gameOver)
            is BsMessage.Reveal -> if (started) opponentFleet = message.ships.map { it.sorted() }
        }
    }

    private fun receiveLobby(message: BsMessage.Lobby) {
        val firstTime = !active
        active = true
        isHost = false
        seat = 1
        lobby = message.names
        settings = message.settings
        notice = message.notice
        resetGame()
        if (firstTime) joined++
    }

    /** L'adversaire me tire dessus : mon téléphone répond, sur ma flotte. */
    private fun onFire(cell: Int) {
        val fleet = myFleet ?: return
        val opponent = 1 - seat
        if (phase != BattleshipPhase.PLAYING || turn != opponent) return
        val shot = Battleship.resolve(fleet, shotsAtMe, cell) ?: return
        shotsAtMe = shotsAtMe + shot
        val over = Battleship.allSunk(fleet, shotsAtMe)
        // L'état est à jour avant l'envoi : la suite de la partie peut repartir avant la fin de l'envoi.
        if (over) {
            phase = BattleshipPhase.FINISHED
            winner = opponent
        } else {
            turn = Battleship.nextShooter(settings, opponent, shot)
        }
        send(BsMessage.Report(shot, over))
        if (over) send(BsMessage.Reveal(fleet))
    }

    /** L'adversaire répond à mon tir. */
    private fun onReport(shot: BattleshipShot, over: Boolean) {
        if (phase != BattleshipPhase.PLAYING || turn != seat || pendingShot != shot.cell) return
        pendingShot = null
        myShots = myShots + shot
        if (over) {
            phase = BattleshipPhase.FINISHED
            winner = seat
        } else {
            turn = Battleship.nextShooter(settings, seat, shot)
        }
        if (over) myFleet?.let { send(BsMessage.Reveal(it)) }
    }

    // ---------- Envoi ----------

    private fun send(message: BsMessage) {
        if (isHost) {
            peerId?.let { session.sendTo(listOf(it), SyncMessage.Bs(message)) }
        } else {
            session.sendToHost(SyncMessage.Bs(message))
        }
    }

    private fun broadcastLobby() {
        send(BsMessage.Lobby(lobby, settings, notice))
    }

    companion object {
        const val PLAYERS = 2
    }
}
