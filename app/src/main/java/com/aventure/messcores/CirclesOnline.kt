package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Les Ronds chacun sur son téléphone, sur la connexion de [SyncSession].
 *
 * - De [MIN_PLAYERS] à [Circles.MAX_PLAYERS] joueurs. L'organisateur ([isHost]) est le premier joueur (place 0), les
 *   invités prennent les places suivantes dans l'ordre d'arrivée. Les joueurs arrivent tant que la partie n'est pas
 *   lancée ([acceptsPlayers]) ; dès qu'ils sont deux, l'organisateur choisit le niveau et lance la partie
 *   ([gathering]). Ensuite la table est fermée : un téléphone qui arrive plus tard est refusé.
 * - Tout le monde joue en même temps sa propre série, sur son téléphone : les ronds sont tirés au hasard localement
 *   (voir [Circles]) et rien n'est échangé pendant la série. Chacun envoie la longueur de sa série quand elle est
 *   finie ([finishRun]) ; quand toutes les séries sont là, la plus longue gagne (voir [CirclesGame]). Le niveau est
 *   celui choisi par l'organisateur : tous les joueurs jouent le même.
 * - Un invité qui part en cours de partie quitte la table : sa place disparaît et les autres finissent leur série. Si
 *   l'organisateur se retrouve seul, la table repart de zéro.
 */
internal class CirclesOnline(private val session: SyncSession) {

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

    /** Le niveau de la partie lancée, ou `null` tant que les joueurs se rassemblent. */
    var level by mutableStateOf<CirclesLevel?>(null)
        private set

    /** Numéro de la partie lancée : il augmente à chaque lancement (0 tant qu'aucune n'a été lancée). */
    var round by mutableIntStateOf(0)
        private set

    /** La série de chaque place ([PENDING] tant que le joueur n'a pas fini sa série). */
    var scores by mutableStateOf<List<Int>>(emptyList())
        private set

    var notice by mutableStateOf(CirclesNotice.NONE)
        private set

    /** Nombre de fois qu'un invité est entré dans une partie : sert à ouvrir l'écran du jeu à son arrivée. */
    var joined by mutableIntStateOf(0)
        private set

    /** Vrai quand j'ai déjà donné ma série pour la partie en cours. */
    var reported by mutableStateOf(false)
        private set

    /** Vrai quand assez de joueurs sont là pour que la partie puisse se jouer. */
    val ready: Boolean get() = active && lobby.size >= MIN_PLAYERS

    /** Vrai tant que la partie n'est pas lancée : les joueurs se rassemblent, l'organisateur choisit le niveau. */
    val gathering: Boolean get() = ready && level == null

    /** Vrai (organisateur) quand un téléphone de plus peut encore s'asseoir à la table. */
    val acceptsPlayers: Boolean
        get() = active && isHost && level == null && lobby.size < Circles.MAX_PLAYERS

    /** Vrai quand la partie lancée est finie : toutes les séries sont là. */
    val over: Boolean
        get() = level != null && lobby.isNotEmpty() && scores.size == lobby.size && scores.none { it < 0 }

    /** Vrai quand c'est à moi de jouer ma série : l'écran de jeu doit être affiché. */
    val myPlay: Boolean
        get() = ready && level != null && !over && !reported && scores.getOrNull(seat) == PENDING

    /** La séance telle qu'elle est maintenant, séries finies seulement (à ne lire qu'une fois [over]). */
    fun game(): CirclesGame = CirclesGame(lobby.size, scores.map { it.coerceAtLeast(0) })

    // Organisateur : les identifiants de connexion des invités (le premier invité a la place 1, etc.).
    private val guestIds = mutableListOf<String>()

    init {
        session.circles = this
    }

    // ---------- Organisateur ----------

    /** Ouvre la table : la connexion doit déjà être annoncée (voir [SyncSession.startHostingCircles]). */
    fun openLobby(hostName: String) {
        resetLocal()
        active = true
        isHost = true
        seat = 0
        lobby = listOf(hostName)
    }

    /** Organisateur : lance la partie au [level] choisi, une fois tout le monde à la table. */
    fun start(level: CirclesLevel) {
        if (!isHost || !gathering) return
        round++
        this.level = level
        scores = List(lobby.size) { PENDING }
        notice = CirclesNotice.NONE
        reported = false
        broadcast()
    }

    /** Organisateur : une nouvelle partie avec les mêmes joueurs, une fois la partie finie ; il rechoisit le niveau. */
    fun newGame() {
        if (!isHost || !ready || !over) return
        backToGathering(CirclesNotice.NONE)
        broadcast()
    }

    // ---------- Tous les joueurs ----------

    /** Donne ma série de [streak] ronds à la fin de ma série. Une seule série par partie. */
    fun finishRun(streak: Int) {
        if (!ready || level == null || over || reported) return
        reported = true
        if (isHost) {
            recordScore(0, streak)
        } else {
            session.sendToHost(SyncMessage.CirclesMsg(CirclesMessage.RunDone(round, streak.coerceAtLeast(0))))
        }
    }

    /** Quitte la partie et coupe la connexion. */
    fun leave() {
        resetLocal()
        session.disconnect()
    }

    internal fun resetLocal() {
        active = false
        isHost = false
        seat = -1
        lobby = emptyList()
        level = null
        round = 0
        scores = emptyList()
        notice = CirclesNotice.NONE
        reported = false
        guestIds.clear()
    }

    private fun backToGathering(reason: CirclesNotice) {
        level = null
        scores = emptyList()
        reported = false
        notice = reason
    }

    private fun recordScore(place: Int, streak: Int) {
        if (level == null || scores.getOrNull(place) != PENDING) return
        scores = scores.toMutableList().also { it[place] = streak.coerceIn(0, MAX_STREAK) }
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
        notice = CirclesNotice.NONE
        broadcast()
    }

    fun onPeerDisconnected(id: String) {
        if (!active) return
        if (isHost) {
            val index = guestIds.indexOf(id)
            if (index < 0) return
            val place = index + 1
            val wasPlaying = level != null
            guestIds.removeAt(index)
            lobby = lobby.filterIndexed { i, _ -> i != place }
            when {
                // L'organisateur est seul : la table repart de zéro, un autre téléphone peut la rejoindre.
                lobby.size < MIN_PLAYERS -> {
                    round = 0
                    backToGathering(CirclesNotice.PLAYER_LEFT)
                }
                wasPlaying -> {
                    // Sa place disparaît ; les autres finissent leur série.
                    scores = scores.filterIndexed { i, _ -> i != place }
                    notice = CirclesNotice.PLAYER_LEFT
                }
                else -> notice = CirclesNotice.PLAYER_LEFT
            }
            broadcast()
        } else {
            notice = CirclesNotice.HOST_LEFT
        }
    }

    // ---------- Messages reçus ----------

    fun onMessage(fromId: String, message: CirclesMessage) {
        if (isHost) onHostMessage(fromId, message) else onGuestMessage(message)
    }

    private fun onGuestMessage(message: CirclesMessage) {
        if (message !is CirclesMessage.Table) return
        val firstTime = !active
        // Une nouvelle partie, ou la table qui se reforme : je n'ai pas encore donné ma série.
        if (message.round != round || message.level == null) reported = false
        active = true
        isHost = false
        seat = message.seat
        lobby = message.names
        level = message.level
        round = message.round
        scores = message.scores
        notice = message.notice
        if (firstTime) joined++
    }

    private fun onHostMessage(fromId: String, message: CirclesMessage) {
        if (!active) return
        val guestSeat = guestIds.indexOf(fromId) + 1
        if (guestSeat == 0) return
        when (message) {
            // Une série n'est prise que pour la partie en cours, et une seule fois par joueur.
            is CirclesMessage.RunDone -> if (message.round == round) recordScore(guestSeat, message.streak)
            else -> return
        }
    }

    /** Envoie à chaque invité l'état de la table, avec sa propre place. */
    private fun broadcast() {
        guestIds.forEachIndexed { index, id ->
            session.sendTo(
                listOf(id),
                SyncMessage.CirclesMsg(CirclesMessage.Table(lobby, index + 1, level, round, scores, notice))
            )
        }
    }

    companion object {
        /** Un seul téléphone ne fait pas une partie en ligne : il en faut au moins deux. */
        const val MIN_PLAYERS = 2

        /** La série d'une place qui n'a pas encore fini la sienne. */
        const val PENDING = -1

        /** Une série plus longue que cela est absurde : elle est ramenée à cette valeur. */
        const val MAX_STREAK = 100_000
    }
}
