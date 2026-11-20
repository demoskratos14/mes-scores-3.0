package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/**
 * Simon chacun sur son téléphone, sur la connexion de [SyncSession].
 *
 * - De [MIN_PLAYERS] à [Simon.MAX_PLAYERS] joueurs. L'organisateur ([isHost]) est le premier joueur (place 0), les invités
 *   prennent les places suivantes dans l'ordre d'arrivée. Les joueurs arrivent tant que la partie n'est pas lancée
 *   ([acceptsPlayers]) ; dès qu'ils sont deux, l'organisateur choisit le mode et lance la partie ([gathering]). Ensuite la
 *   table est fermée : un téléphone qui arrive plus tard est refusé.
 * - Deux modes, comme sur un seul téléphone (voir [SimonMode]) :
 *   - « chacun sa série » : tout le monde joue en même temps sa propre suite, tirée sur son téléphone. Chacun envoie son
 *     score à la fin de sa série ([finishSeries]) ; quand tous les scores sont là, le meilleur gagne (voir [SimonGame]) ;
 *   - « chacun son tour » : une seule suite, tenue par l'organisateur, que chaque joueur reproduit à son tour. Celui dont
 *     c'est le tour dit s'il l'a reproduite ([reportTurn]) ; l'organisateur la rallonge ou élimine le joueur et passe au
 *     suivant (voir [SimonTurns]).
 * - Un invité qui part en cours de partie quitte la table. En « chacun sa série », sa place disparaît et les autres
 *   finissent leur série ; en « chacun son tour », la partie est annulée et la table se reforme. Si l'organisateur se
 *   retrouve seul, la table repart de zéro.
 */
internal class SimonOnline(
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

    /** Le mode de la partie lancée, ou `null` tant que les joueurs se rassemblent. */
    var mode by mutableStateOf<SimonMode?>(null)
        private set

    /** Numéro de l'étape en cours : il augmente à chaque lancement et, en « chacun son tour », à chaque tour joué. */
    var round by mutableIntStateOf(0)
        private set

    /** « Chacun sa série » : le score de chaque place ([PENDING] tant que le joueur n'a pas fini sa série). */
    var scores by mutableStateOf<List<Int>>(emptyList())
        private set

    /** « Chacun son tour » : la suite commune, les places éliminées (dans l'ordre de sortie), le joueur dont c'est le tour et le dernier éliminé (-1 si aucun). */
    var sequence by mutableStateOf<List<Int>>(emptyList())
        private set
    var eliminated by mutableStateOf<List<Int>>(emptyList())
        private set
    var current by mutableIntStateOf(0)
        private set
    var lastOut by mutableIntStateOf(-1)
        private set

    var notice by mutableStateOf(SimonNotice.NONE)
        private set

    /** Nombre de fois qu'un invité est entré dans une partie : sert à ouvrir l'écran du jeu à son arrivée. */
    var joined by mutableIntStateOf(0)
        private set

    /** Vrai quand j'ai déjà donné mon résultat pour l'étape en cours. */
    var reported by mutableStateOf(false)
        private set

    /** Vrai quand assez de joueurs sont là pour que la partie puisse se jouer. */
    val ready: Boolean get() = active && lobby.size >= MIN_PLAYERS

    /** Vrai tant que la partie n'est pas lancée : les joueurs se rassemblent, l'organisateur choisit le mode. */
    val gathering: Boolean get() = ready && mode == null

    /** Vrai (organisateur) quand un téléphone de plus peut encore s'asseoir à la table. */
    val acceptsPlayers: Boolean
        get() = active && isHost && mode == null && lobby.size < Simon.MAX_PLAYERS

    /** La partie « chacun son tour » telle qu'elle est maintenant (à ne lire qu'en mode [SimonMode.TURNS]). */
    fun turns(): SimonTurns = SimonTurns(lobby.size, sequence, eliminated, current)

    /** La séance « chacun sa série » telle qu'elle est maintenant, scores finis seulement (à ne lire qu'en mode [SimonMode.SERIES] une fois [over]). */
    fun series(): SimonGame = SimonGame(lobby.size, scores.map { it.coerceAtLeast(0) })

    /** Vrai quand la partie lancée est finie : tous les scores sont là, ou il ne reste qu'un joueur en jeu. */
    val over: Boolean
        get() = when (mode) {
            SimonMode.SERIES -> lobby.isNotEmpty() && scores.size == lobby.size && scores.none { it < 0 }
            SimonMode.TURNS -> turns().isOver
            null -> false
        }

    /** Vrai quand c'est à moi de jouer ma série, ou mon tour : l'écran de jeu doit être affiché. */
    val myPlay: Boolean
        get() = ready && !over && !reported && when (mode) {
            SimonMode.SERIES -> scores.getOrNull(seat) == PENDING
            SimonMode.TURNS -> current == seat && seat !in eliminated
            null -> false
        }

    // Organisateur : les identifiants de connexion des invités (le premier invité a la place 1, etc.).
    private val guestIds = mutableListOf<String>()

    init {
        session.simon = this
    }

    // ---------- Organisateur ----------

    /** Ouvre la table : la connexion doit déjà être annoncée (voir [SyncSession.startHostingSimon]). */
    fun openLobby(hostName: String) {
        resetLocal()
        active = true
        isHost = true
        seat = 0
        lobby = listOf(hostName)
    }

    /** Organisateur : lance la partie dans le [mode] choisi, une fois tout le monde à la table. */
    fun start(mode: SimonMode) {
        if (!isHost || !gathering) return
        round++
        this.mode = mode
        notice = SimonNotice.NONE
        reported = false
        lastOut = -1
        current = 0
        eliminated = emptyList()
        when (mode) {
            SimonMode.SERIES -> {
                scores = List(lobby.size) { PENDING }
                sequence = emptyList()
            }
            SimonMode.TURNS -> {
                scores = emptyList()
                sequence = SimonRun.start(random).sequence
            }
        }
        broadcast()
    }

    /** Organisateur : une nouvelle partie avec les mêmes joueurs, une fois la partie finie ; il rechoisit le mode. */
    fun newGame() {
        if (!isHost || !ready || !over) return
        backToGathering(SimonNotice.NONE)
        broadcast()
    }

    // ---------- Tous les joueurs ----------

    /** « Chacun sa série » : donne mon score à la fin de ma série. Un seul score par partie. */
    fun finishSeries(score: Int) {
        if (!ready || mode != SimonMode.SERIES || over || reported) return
        reported = true
        if (isHost) {
            recordScore(0, score)
        } else {
            session.sendToHost(SyncMessage.SimonMsg(SimonMessage.SeriesDone(round, score.coerceAtLeast(0))))
        }
    }

    /** « Chacun son tour » : dit si j'ai reproduit la suite en entier ([reproduced]) ou si je me suis trompé. Seulement à mon tour. */
    fun reportTurn(reproduced: Boolean) {
        if (!ready || mode != SimonMode.TURNS || over || reported || current != seat) return
        reported = true
        if (isHost) {
            applyTurn(reproduced)
        } else {
            session.sendToHost(SyncMessage.SimonMsg(SimonMessage.TurnResult(round, reproduced)))
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
        mode = null
        round = 0
        scores = emptyList()
        sequence = emptyList()
        eliminated = emptyList()
        current = 0
        lastOut = -1
        notice = SimonNotice.NONE
        reported = false
        guestIds.clear()
    }

    private fun backToGathering(reason: SimonNotice) {
        mode = null
        scores = emptyList()
        sequence = emptyList()
        eliminated = emptyList()
        current = 0
        lastOut = -1
        reported = false
        notice = reason
    }

    private fun recordScore(place: Int, score: Int) {
        if (mode != SimonMode.SERIES || scores.getOrNull(place) != PENDING) return
        scores = scores.toMutableList().also { it[place] = score.coerceIn(0, MAX_SCORE) }
        broadcast()
    }

    private fun applyTurn(reproduced: Boolean) {
        val game = turns()
        if (game.isOver) return
        if (reproduced) {
            val next = game.succeed(random)
            sequence = next.sequence
            current = next.current
            lastOut = -1
        } else {
            val next = game.fail()
            lastOut = game.current
            eliminated = next.eliminated
            current = next.current
        }
        round++
        reported = false
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
        notice = SimonNotice.NONE
        broadcast()
    }

    fun onPeerDisconnected(id: String) {
        if (!active) return
        if (isHost) {
            val index = guestIds.indexOf(id)
            if (index < 0) return
            val place = index + 1
            val wasPlaying = mode
            guestIds.removeAt(index)
            lobby = lobby.filterIndexed { i, _ -> i != place }
            when {
                // L'organisateur est seul : la table repart de zéro, un autre téléphone peut la rejoindre.
                lobby.size < MIN_PLAYERS -> {
                    round = 0
                    backToGathering(SimonNotice.PLAYER_LEFT)
                }
                wasPlaying == SimonMode.SERIES -> {
                    // Sa place disparaît ; les autres finissent leur série.
                    scores = scores.filterIndexed { i, _ -> i != place }
                    notice = SimonNotice.PLAYER_LEFT
                }
                // En « chacun son tour », les places changent : la partie est annulée et la table se reforme.
                wasPlaying == SimonMode.TURNS -> backToGathering(SimonNotice.GAME_CANCELLED)
                else -> notice = SimonNotice.PLAYER_LEFT
            }
            broadcast()
        } else {
            notice = SimonNotice.HOST_LEFT
        }
    }

    // ---------- Messages reçus ----------

    fun onMessage(fromId: String, message: SimonMessage) {
        if (isHost) onHostMessage(fromId, message) else onGuestMessage(message)
    }

    private fun onGuestMessage(message: SimonMessage) {
        if (message !is SimonMessage.Table) return
        val firstTime = !active
        // Une nouvelle étape, ou la table qui se reforme : je n'ai pas encore donné mon résultat.
        if (message.round != round || message.mode == null) reported = false
        active = true
        isHost = false
        seat = message.seat
        lobby = message.names
        mode = message.mode
        round = message.round
        scores = message.scores
        sequence = message.sequence
        eliminated = message.eliminated
        current = message.current
        lastOut = message.lastOut
        notice = message.notice
        if (firstTime) joined++
    }

    private fun onHostMessage(fromId: String, message: SimonMessage) {
        if (!active) return
        val guestSeat = guestIds.indexOf(fromId) + 1
        if (guestSeat == 0) return
        when (message) {
            // Un score n'est pris que pour la partie en cours, et une seule fois par joueur.
            is SimonMessage.SeriesDone -> if (message.round == round) recordScore(guestSeat, message.score)
            // Un résultat n'est pris que du joueur dont c'est le tour, et pour le tour en cours.
            is SimonMessage.TurnResult -> {
                if (mode != SimonMode.TURNS || message.round != round || current != guestSeat) return
                applyTurn(message.reproduced)
            }
            else -> return
        }
    }

    /** Envoie à chaque invité l'état de la table, avec sa propre place. */
    private fun broadcast() {
        guestIds.forEachIndexed { index, id ->
            session.sendTo(
                listOf(id),
                SyncMessage.SimonMsg(
                    SimonMessage.Table(lobby, index + 1, mode, round, scores, sequence, eliminated, current, lastOut, notice)
                )
            )
        }
    }

    companion object {
        /** Un seul téléphone ne fait pas une partie en ligne : il en faut au moins deux. */
        const val MIN_PLAYERS = 2

        /** Le score d'une place qui n'a pas encore fini sa série. */
        const val PENDING = -1

        private const val MAX_SCORE = 2_000
    }
}
