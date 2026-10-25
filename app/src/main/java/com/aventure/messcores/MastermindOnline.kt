package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Mastermind chacun sur son téléphone, sur la connexion de [SyncSession].
 *
 * - Chaque joueur saisit sa suite secrète. Elle n'est affichée sur aucun autre téléphone : elle part chez
 *   l'organisateur, qui la transmet cachée aux autres joueurs dès que tout le monde est prêt.
 * - La recherche est individuelle et en parallèle : chacun cherche, l'une après l'autre, les suites de tous les
 *   autres (voir [Mastermind.searchOrder]), à son rythme. Son téléphone calcule seul la réponse à chacun de ses essais.
 * - Une suite terminée (trouvée ou essais épuisés), le joueur envoie ses essais à l'organisateur, qui les rejoue
 *   contre la suite avant de les compter. Le gagnant est celui dont la somme d'essais est la plus basse.
 * - L'organisateur ([isHost]) tient l'état public de la partie ([state]) et l'envoie à tous les joueurs ; les
 *   invités lui envoient leurs actions. Les places ([seat]) suivent l'ordre de connexion : 0 pour l'organisateur.
 */
internal class MastermindOnline(private val session: SyncSession) {

    /** Vrai tant qu'une partie à plusieurs téléphones est ouverte, de la salle d'attente aux résultats. */
    var active by mutableStateOf(false)
        private set

    var isHost by mutableStateOf(false)
        private set

    /** Ma place à la table (0 pour l'organisateur), ou -1 hors partie. */
    var seat by mutableIntStateOf(-1)
        private set

    /** Les joueurs à la table, dans l'ordre des places. */
    var lobby by mutableStateOf<List<String>>(emptyList())
        private set

    var settings by mutableStateOf(MastermindSettings())
        private set

    /** État public de la partie ; null tant que l'organisateur ne l'a pas lancée (salle d'attente). */
    var state by mutableStateOf<MastermindState?>(null)
        private set

    /** Ma suite secrète, que les autres joueurs cherchent. */
    var mySecret by mutableStateOf<List<Int>?>(null)
        private set

    var notice by mutableStateOf(MastermindNotice.NONE)
        private set

    /** Nombre de fois qu'un invité est entré dans une partie : sert à ouvrir l'écran du jeu à son arrivée. */
    var joined by mutableIntStateOf(0)
        private set

    // ---------- Ma recherche (sur ce téléphone seulement) ----------

    /** Les places des suites que je cherche, dans l'ordre ; vide tant que les suites ne sont pas reçues. */
    var order by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Position, dans [order], de la suite que je cherche ; [order].size quand je les ai toutes terminées. */
    var position by mutableIntStateOf(0)
        private set

    /** Mes essais, avec leur réponse, pour la suite en cours. */
    var guesses by mutableStateOf<List<MastermindGuess>>(emptyList())
        private set

    // Les suites des autres joueurs (null pour la mienne) : jamais affichées avant la fin de la recherche qui les vise.
    private var others: List<List<Int>?> = emptyList()

    /** Place de la suite que je cherche, ou null si je les ai toutes terminées (ou pas encore reçues). */
    val currentTarget: Int? get() = order.getOrNull(position)

    val searchStarted: Boolean get() = order.isNotEmpty()

    /** Vrai si le dernier essai pour la suite en cours a tous ses pions bien placés. */
    val runSolved: Boolean get() = guesses.lastOrNull()?.feedback?.wellPlaced == settings.codeLength

    /** Vrai si la suite en cours est trouvée ou si tous mes essais sont utilisés. */
    val runOver: Boolean get() = runSolved || guesses.size >= settings.maxAttempts

    /** La suite en cours, à n'afficher qu'une fois ratée ([runOver] sans [runSolved]). */
    val currentSecret: List<Int>? get() = currentTarget?.let { others.getOrNull(it) }

    // Organisateur : l'identifiant de connexion du joueur de chaque place, à partir de la place 1.
    private val seatIds = ArrayList<String>()

    // Organisateur : la suite de chaque place, pour rejouer les essais. Jamais dans l'état public avant les résultats.
    private val secretsBySeat = ArrayList<List<Int>?>()

    init {
        session.mastermind = this
    }

    // ---------- Organisateur ----------

    /** Ouvre la salle d'attente : la connexion doit déjà être annoncée (voir [SyncSession.startHostingMastermind]). */
    fun openLobby(hostName: String, newSettings: MastermindSettings) {
        resetLocal()
        active = true
        isHost = true
        seat = 0
        lobby = listOf(hostName)
        settings = newSettings.coerced()
    }

    /** Lance la partie avec les joueurs présents ; chacun saisit alors sa suite. */
    fun startGame() {
        if (!isHost || state != null || lobby.size < MIN_PLAYERS) return
        mySecret = null
        clearSearch()
        notice = MastermindNotice.NONE
        secretsBySeat.clear()
        repeat(lobby.size) { secretsBySeat.add(null) }
        state = Mastermind.newOnlineGame(lobby, settings)
        broadcastState()
    }

    /** Rejouer avec les mêmes joueurs : retour à la salle d'attente, de nouvelles suites sont à saisir. */
    fun replay() {
        if (!isHost) return
        state = null
        mySecret = null
        clearSearch()
        secretsBySeat.clear()
        notice = MastermindNotice.NONE
        broadcastLobby()
    }

    // ---------- Tous les joueurs ----------

    /** Enregistre ma suite secrète et l'envoie à l'organisateur, qui la cache aux autres téléphones. */
    fun submitSecret(pegs: List<Int>) {
        val current = state ?: return
        if (current.phase != MastermindPhase.SECRETS || !Mastermind.isValid(pegs, settings)) return
        if (current.ready.getOrNull(seat) != false) return
        mySecret = pegs.toList()
        if (isHost) hostSecret(0, pegs) else session.sendToHost(SyncMessage.Mm(MmMessage.Secret(pegs)))
    }

    /** Propose un essai pour la suite en cours : la réponse est calculée ici, sur ce téléphone. */
    fun submitGuess(pegs: List<Int>) {
        val current = state ?: return
        val target = currentTarget ?: return
        if (current.phase != MastermindPhase.PLAYING || runOver || !Mastermind.isValid(pegs, settings)) return
        val secret = others.getOrNull(target) ?: return
        guesses = guesses + MastermindGuess(pegs.toList(), Mastermind.feedback(secret, pegs))
        if (runOver) reportRun(target)
    }

    /** Passe à la suite suivante, une fois la suite en cours trouvée ou ratée. */
    fun next() {
        if (currentTarget == null || !runOver) return
        position++
        guesses = emptyList()
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
        state = null
        mySecret = null
        notice = MastermindNotice.NONE
        seatIds.clear()
        secretsBySeat.clear()
        clearSearch()
    }

    private fun clearSearch() {
        others = emptyList()
        order = emptyList()
        position = 0
        guesses = emptyList()
    }

    private fun startSearch(secrets: List<List<Int>?>) {
        others = secrets
        position = 0
        guesses = emptyList()
        order = Mastermind.searchOrder(secrets.size, seat)
    }

    /** Envoie à l'organisateur mes essais pour la suite de [target], qu'il rejoue avant de les compter. */
    private fun reportRun(target: Int) {
        val run = guesses.map { it.pegs }
        if (isHost) hostRun(0, target, run) else session.sendToHost(SyncMessage.Mm(MmMessage.Run(target, run)))
    }

    // ---------- Connexions (rapportées par la session) ----------

    fun onPeerConnected(id: String) {
        if (!active || !isHost) return
        // Une partie lancée ou une table pleine n'accepte plus personne.
        if (state != null || lobby.size >= MAX_PLAYERS) {
            session.disconnectEndpoint(id)
            return
        }
        seatIds.add(id)
        lobby = PlayerNames.makeUnique(lobby + session.nameOf(id))
        broadcastLobby()
    }

    fun onPeerDisconnected(id: String) {
        if (!active) return
        if (isHost) {
            val index = seatIds.indexOf(id)
            if (index < 0) return
            seatIds.removeAt(index)
            lobby = lobby.toMutableList().also { it.removeAt(index + 1) }
            // Un joueur parti en pleine partie la rend impossible à finir : retour à la salle d'attente.
            notice = if (state != null) MastermindNotice.PLAYER_LEFT else MastermindNotice.NONE
            state = null
            mySecret = null
            secretsBySeat.clear()
            clearSearch()
            broadcastLobby()
        } else {
            notice = MastermindNotice.HOST_LEFT
            state = null
            mySecret = null
            clearSearch()
        }
    }

    // ---------- Messages reçus ----------

    fun onMessage(fromId: String, message: MmMessage) {
        if (isHost) onHostMessage(fromId, message) else onGuestMessage(message)
    }

    private fun onGuestMessage(message: MmMessage) {
        when (message) {
            is MmMessage.Lobby -> {
                val firstTime = !active
                active = true
                isHost = false
                seat = message.seat
                lobby = message.names
                settings = message.settings
                notice = message.notice
                state = null
                mySecret = null
                clearSearch()
                if (firstTime) joined++
            }
            is MmMessage.State -> {
                if (!active) return
                state = message.state
            }
            is MmMessage.Secrets -> {
                // Les suites des autres, cachées : la recherche peut commencer.
                if (active && message.secrets.size == lobby.size) startSearch(message.secrets)
            }
            else -> Unit
        }
    }

    private fun onHostMessage(fromId: String, message: MmMessage) {
        val fromSeat = seatIds.indexOf(fromId) + 1
        if (fromSeat < 1) return
        when (message) {
            is MmMessage.Secret -> hostSecret(fromSeat, message.pegs)
            is MmMessage.Run -> hostRun(fromSeat, message.target, message.guesses)
            else -> Unit
        }
    }

    // ---------- Ce que fait l'organisateur ----------

    private fun hostSecret(fromSeat: Int, pegs: List<Int>) {
        val current = state ?: return
        if (current.ready.getOrNull(fromSeat) != false || !Mastermind.isValid(pegs, current.settings)) return
        val next = Mastermind.markReady(current, fromSeat) ?: return
        secretsBySeat[fromSeat] = pegs.toList()
        // Tous prêts : chacun reçoit les suites des autres (avant l'état, pour que la recherche soit prête à l'ouverture).
        if (next.phase == MastermindPhase.PLAYING) distributeSecrets()
        state = next
        broadcastState()
    }

    private fun distributeSecrets() {
        seatIds.forEachIndexed { index, id ->
            val forThem = secretsBySeat.mapIndexed { place, secret -> if (place == index + 1) null else secret }
            session.sendTo(listOf(id), SyncMessage.Mm(MmMessage.Secrets(forThem)))
        }
        startSearch(secretsBySeat.mapIndexed { place, secret -> if (place == 0) null else secret })
    }

    private fun hostRun(fromSeat: Int, target: Int, run: List<List<Int>>) {
        val current = state ?: return
        if (current.phase != MastermindPhase.PLAYING || fromSeat == target) return
        val secret = secretsBySeat.getOrNull(target) ?: return
        // La recherche est rejouée contre la vraie suite : des essais qui ne la trouvent pas sont refusés.
        val attempts = Mastermind.countRun(secret, run, current.settings) ?: return
        var next = Mastermind.withResult(current, fromSeat, target, attempts) ?: return
        // Tout le monde a fini : les suites sont enfin dévoilées.
        if (next.phase == MastermindPhase.RESULTS) next = next.copy(secrets = secretsBySeat.toList())
        state = next
        broadcastState()
    }

    private fun broadcastState() {
        val current = state ?: return
        if (seatIds.isEmpty()) return
        session.sendTo(seatIds, SyncMessage.Mm(MmMessage.State(current)))
    }

    private fun broadcastLobby() {
        seatIds.forEachIndexed { index, id ->
            session.sendTo(listOf(id), SyncMessage.Mm(MmMessage.Lobby(lobby, settings, index + 1, notice)))
        }
    }

    companion object {
        const val MIN_PLAYERS = 2
        const val MAX_PLAYERS = 8
    }
}
