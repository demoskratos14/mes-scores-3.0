package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/**
 * Duel de réflexes chacun sur son téléphone, sur la connexion de [SyncSession].
 *
 * - Deux joueurs exactement : l'organisateur ([isHost]) est le premier joueur, l'invité le deuxième. La partie commence
 *   dès que l'invité est connecté.
 * - Chacun peut lancer la manche. L'organisateur tire l'attente avant le signal ([delayMs]) et l'envoie à l'invité ; chaque
 *   téléphone attend ce délai de son côté, donne le signal, et mesure le temps de réaction de son joueur depuis ce signal.
 *   Que le signal n'apparaisse pas au même instant sur les deux téléphones ne change rien : seul compte le temps de
 *   réaction de chacun.
 * - Chaque joueur envoie son résultat ([report]) ; quand l'organisateur a les deux, il les compare (voir
 *   [ReflexMatch.resolve]) et renvoie l'état complet à l'invité.
 */
internal class ReflexOnline(
    private val session: SyncSession,
    private val random: Random = Random.Default
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

    /** Le score de la séance et le résultat de la dernière manche. */
    var match by mutableStateOf(ReflexMatch())
        private set

    var notice by mutableStateOf(ReflexNotice.NONE)
        private set

    /** Nombre de fois qu'un invité est entré dans une partie : sert à ouvrir l'écran du jeu à son arrivée. */
    var joined by mutableIntStateOf(0)
        private set

    /** Numéro de la manche lancée, qui augmente à chaque lancement. */
    var round by mutableIntStateOf(0)
        private set

    /** Attente avant le signal de la manche lancée, en millisecondes. */
    var delayMs by mutableLongStateOf(0L)
        private set

    /** Vrai de l'instant où la manche est lancée jusqu'à ce que les deux résultats soient connus. */
    var running by mutableStateOf(false)
        private set

    /** Vrai quand j'ai déjà donné mon résultat pour la manche en cours. */
    var reported by mutableStateOf(false)
        private set

    /** Mon numéro de joueur : 1 pour l'organisateur, 2 pour l'invité. */
    val myNumber: Int get() = seat + 1

    /** Vrai quand les deux joueurs sont là et que la partie peut se jouer. */
    val ready: Boolean get() = active && lobby.size >= PLAYERS

    // Organisateur : l'identifiant de connexion de l'invité, et les résultats reçus pour la manche en cours.
    private var guestId: String? = null
    private var hostResult: Int? = null
    private var guestResult: Int? = null

    init {
        session.reflex = this
    }

    // ---------- Organisateur ----------

    /** Ouvre la table : la connexion doit déjà être annoncée (voir [SyncSession.startHostingReflex]). */
    fun openLobby(hostName: String) {
        resetLocal()
        active = true
        isHost = true
        seat = 0
        lobby = listOf(hostName)
    }

    // ---------- Les deux joueurs ----------

    /** Lance la manche suivante (ou la première) : l'organisateur la lance, l'invité la demande à l'organisateur. */
    fun startRound() {
        if (!ready || running || match.isOver) return
        if (isHost) beginRound() else session.sendToHost(SyncMessage.Reflex(ReflexMessage.Start))
    }

    /**
     * Donne mon résultat pour la manche en cours : un temps de réaction en millisecondes, [Reflex.EARLY] si j'ai touché
     * avant le signal, [Reflex.MISS] si je n'ai pas touché à temps. Un seul résultat par manche.
     */
    fun report(result: Int) {
        if (!ready || !running || reported) return
        reported = true
        if (isHost) {
            hostResult = result
            resolveIfComplete()
        } else {
            session.sendToHost(SyncMessage.Reflex(ReflexMessage.Result(round, result)))
        }
    }

    /** Nouvelle partie, une fois la partie finie ; chacun des deux joueurs peut la lancer. */
    fun newMatch() {
        if (!ready || !match.isOver) return
        if (isHost) {
            match = match.newMatch()
            broadcast()
        } else {
            session.sendToHost(SyncMessage.Reflex(ReflexMessage.NewMatch))
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
        match = ReflexMatch()
        notice = ReflexNotice.NONE
        round = 0
        delayMs = 0L
        running = false
        reported = false
        guestId = null
        hostResult = null
        guestResult = null
    }

    private fun beginRound() {
        round++
        delayMs = Reflex.randomDelayMs(random)
        running = true
        reported = false
        hostResult = null
        guestResult = null
        broadcast()
    }

    private fun resolveIfComplete() {
        val mine = hostResult ?: return
        val theirs = guestResult ?: return
        match = match.resolve(mine, theirs) ?: match
        running = false
        broadcast()
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
        match = ReflexMatch()
        running = false
        notice = ReflexNotice.NONE
        broadcast()
    }

    fun onPeerDisconnected(id: String) {
        if (!active) return
        if (isHost) {
            if (id != guestId) return
            // L'invité parti, la table repart de zéro : un autre téléphone peut la rejoindre.
            guestId = null
            lobby = lobby.take(1)
            match = ReflexMatch()
            running = false
            reported = false
            hostResult = null
            guestResult = null
            notice = ReflexNotice.PLAYER_LEFT
        } else {
            running = false
            notice = ReflexNotice.HOST_LEFT
        }
    }

    // ---------- Messages reçus ----------

    fun onMessage(fromId: String, message: ReflexMessage) {
        if (isHost) onHostMessage(fromId, message) else onGuestMessage(message)
    }

    private fun onGuestMessage(message: ReflexMessage) {
        if (message !is ReflexMessage.Table) return
        val firstTime = !active
        // Une nouvelle manche lancée : je n'ai pas encore donné mon résultat.
        if (message.running && (!running || message.round != round)) reported = false
        active = true
        isHost = false
        seat = message.seat
        lobby = message.names
        match = message.match
        notice = message.notice
        round = message.round
        delayMs = message.delayMs
        running = message.running
        if (firstTime) joined++
    }

    private fun onHostMessage(fromId: String, message: ReflexMessage) {
        if (!active || fromId != guestId) return
        when (message) {
            ReflexMessage.Start -> if (ready && !running && !match.isOver) beginRound()
            // Le résultat de l'invité n'est pris que pour la manche en cours, et une seule fois.
            is ReflexMessage.Result -> {
                if (!running || message.round != round || guestResult != null) return
                guestResult = message.reactionMs
                resolveIfComplete()
            }
            ReflexMessage.NewMatch -> if (match.isOver) {
                match = match.newMatch()
                broadcast()
            }
            else -> return
        }
    }

    private fun broadcast() {
        val id = guestId ?: return
        session.sendTo(listOf(id), SyncMessage.Reflex(ReflexMessage.Table(lobby, 1, match, notice, round, delayMs, running)))
    }

    companion object {
        const val PLAYERS = 2
    }
}
