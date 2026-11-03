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
 * - De [Reflex.MIN_PLAYERS] à [Reflex.MAX_PLAYERS] joueurs. L'organisateur ([isHost]) est le premier joueur (place 0), les
 *   invités prennent les places suivantes dans l'ordre d'arrivée.
 * - Les joueurs arrivent tant que la première manche n'est pas lancée ([acceptsPlayers]) ; dès qu'ils sont deux, l'écran
 *   de jeu s'ouvre et l'organisateur lance la partie ([gathering]). Ensuite la table est fermée : un téléphone qui
 *   arrive plus tard est refusé.
 * - Une fois la partie lancée, n'importe quel joueur peut lancer la manche suivante. L'organisateur tire l'attente avant
 *   le signal ([delayMs]) et l'envoie aux invités ; chaque téléphone attend ce délai de son côté, donne le signal, et
 *   mesure le temps de réaction de son joueur depuis ce signal. Que le signal n'apparaisse pas au même instant sur tous
 *   les téléphones ne change rien : seul compte le temps de réaction de chacun.
 * - Chaque joueur envoie son résultat ([report]) ; quand l'organisateur a tout le monde, il compare (voir
 *   [ReflexGame.resolve]) et renvoie l'état complet à chaque invité.
 * - Un invité qui part en cours de partie quitte la table : la manche en cours est annulée et la partie continue avec les
 *   autres, tant qu'ils restent deux. Si l'organisateur se retrouve seul, la table repart de zéro.
 */
internal class ReflexOnline(
    private val session: SyncSession,
    private val random: Random = Random.Default
) {

    /** Vrai tant qu'une partie chacun sur son téléphone est ouverte, de l'attente des autres joueurs à la fin de la séance. */
    var active by mutableStateOf(false)
        private set

    var isHost by mutableStateOf(false)
        private set

    /** Ma place à la table (0 : organisateur, puis les invités dans l'ordre d'arrivée), ou -1 hors partie. */
    var seat by mutableIntStateOf(-1)
        private set

    /** Les joueurs à la table, dans l'ordre des places. */
    var lobby by mutableStateOf<List<String>>(emptyList())
        private set

    /** Le score de la séance et le résultat de la dernière manche. */
    var match by mutableStateOf(ReflexGame.of(0))
        private set

    var notice by mutableStateOf(ReflexNotice.NONE)
        private set

    /** Nombre de fois qu'un invité est entré dans une partie : sert à ouvrir l'écran du jeu à son arrivée. */
    var joined by mutableIntStateOf(0)
        private set

    /** Numéro de la manche lancée, qui augmente à chaque lancement (0 tant que la partie n'est pas lancée). */
    var round by mutableIntStateOf(0)
        private set

    /** Attente avant le signal de la manche lancée, en millisecondes. */
    var delayMs by mutableLongStateOf(0L)
        private set

    /** Vrai de l'instant où la manche est lancée jusqu'à ce que tous les résultats soient connus. */
    var running by mutableStateOf(false)
        private set

    /** Vrai quand j'ai déjà donné mon résultat pour la manche en cours. */
    var reported by mutableStateOf(false)
        private set

    /** Vrai quand assez de joueurs sont là pour que la partie puisse se jouer. */
    val ready: Boolean get() = active && lobby.size >= Reflex.MIN_PLAYERS

    /** Vrai tant que la première manche n'est pas lancée : les joueurs se rassemblent, l'organisateur lance la partie. */
    val gathering: Boolean get() = ready && !running && round == 0

    /** Vrai (organisateur) quand un téléphone de plus peut encore s'asseoir à la table. */
    val acceptsPlayers: Boolean
        get() = active && isHost && !running && round == 0 && lobby.size < Reflex.MAX_PLAYERS

    // Organisateur : les identifiants de connexion des invités (le premier invité a la place 1, etc.), et les résultats
    // reçus pour la manche en cours, par place (null tant qu'une place n'a rien donné).
    private val guestIds = mutableListOf<String>()
    private var results = mutableListOf<Int?>()

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
        match = ReflexGame.of(1)
    }

    // ---------- Tous les joueurs ----------

    /**
     * Lance la manche suivante (ou la première) : l'organisateur la lance, un invité la demande à l'organisateur. La
     * première manche n'est lancée que par l'organisateur, une fois tout le monde à la table.
     */
    fun startRound() {
        if (!ready || running || match.isOver) return
        if (isHost) beginRound() else if (round > 0) session.sendToHost(SyncMessage.Reflex(ReflexMessage.Start))
    }

    /**
     * Donne mon résultat pour la manche en cours : un temps de réaction en millisecondes, [Reflex.EARLY] si j'ai touché
     * avant le signal, [Reflex.MISS] si je n'ai pas touché à temps. Un seul résultat par manche.
     */
    fun report(result: Int) {
        if (!ready || !running || reported) return
        reported = true
        if (isHost) {
            results[0] = result
            resolveIfComplete()
        } else {
            session.sendToHost(SyncMessage.Reflex(ReflexMessage.Result(round, result)))
        }
    }

    /** Nouvelle partie, une fois la partie finie ; chacun des joueurs peut la lancer. */
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
        match = ReflexGame.of(0)
        notice = ReflexNotice.NONE
        round = 0
        delayMs = 0L
        running = false
        reported = false
        guestIds.clear()
        results = mutableListOf()
    }

    private fun beginRound() {
        round++
        delayMs = Reflex.randomDelayMs(random)
        running = true
        reported = false
        notice = ReflexNotice.NONE
        results = MutableList<Int?>(lobby.size) { null }
        broadcast()
    }

    private fun resolveIfComplete() {
        if (results.size != lobby.size || results.any { it == null }) return
        match = match.resolve(results.map { it ?: Reflex.MISS }) ?: match
        running = false
        broadcast()
    }

    // ---------- Connexions (rapportées par la session) ----------

    fun onPeerConnected(id: String) {
        if (!active || !isHost || id in guestIds) return
        // La table est fermée une fois la partie lancée, ou pleine : personne d'autre n'y entre.
        if (!acceptsPlayers) {
            session.disconnectEndpoint(id)
            return
        }
        guestIds.add(id)
        lobby = PlayerNames.makeUnique(lobby + session.nameOf(id))
        match = ReflexGame.of(lobby.size)
        notice = ReflexNotice.NONE
        broadcast()
    }

    fun onPeerDisconnected(id: String) {
        if (!active) return
        if (isHost) {
            val index = guestIds.indexOf(id)
            if (index < 0) return
            // Sa place disparaît ; la manche en cours, qui l'attendait, est annulée.
            guestIds.removeAt(index)
            lobby = lobby.filterIndexed { i, _ -> i != index + 1 }
            running = false
            reported = false
            results = mutableListOf()
            notice = ReflexNotice.PLAYER_LEFT
            if (lobby.size < Reflex.MIN_PLAYERS) {
                // L'organisateur est seul : la table repart de zéro, un autre téléphone peut la rejoindre.
                match = ReflexGame.of(lobby.size)
                round = 0
            } else {
                match = match.removePlayer(index + 1)
            }
            broadcast()
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
        if (!active) return
        val guestSeat = guestIds.indexOf(fromId) + 1
        if (guestSeat == 0) return
        when (message) {
            ReflexMessage.Start -> if (ready && !running && !match.isOver && round > 0) beginRound()
            // Le résultat d'un invité n'est pris que pour la manche en cours, et une seule fois.
            is ReflexMessage.Result -> {
                if (!running || message.round != round || results.getOrNull(guestSeat) != null) return
                results[guestSeat] = message.reactionMs
                resolveIfComplete()
            }
            ReflexMessage.NewMatch -> if (match.isOver) {
                match = match.newMatch()
                broadcast()
            }
            else -> return
        }
    }

    /** Envoie à chaque invité l'état de la table, avec sa propre place. */
    private fun broadcast() {
        guestIds.forEachIndexed { index, id ->
            session.sendTo(
                listOf(id),
                SyncMessage.Reflex(ReflexMessage.Table(lobby, index + 1, match, notice, round, delayMs, running))
            )
        }
    }
}
