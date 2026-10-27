package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/** Où en est la manche à deux téléphones : le mot se choisit, on devine, la manche est finie. */
internal enum class HangmanStage { WORD, PLAYING, FINISHED }

/**
 * Pendu chacun sur son téléphone, sur la connexion de [SyncSession] (deux joueurs).
 *
 * - Celui qui choisit le mot le saisit sur son téléphone : le mot n'en sort jamais pendant la manche. Il n'envoie à
 *   l'autre que sa forme (le nombre de lettres et les séparateurs), de quoi dessiner les blancs.
 * - Celui qui devine envoie une lettre ; le téléphone de celui qui a le mot répond par les places de la lettre (aucune
 *   place : erreur). Les deux téléphones appliquent ensuite la même règle pour savoir si le mot est trouvé ou le
 *   bonhomme pendu.
 * - Fin de manche : le mot est dévoilé, et le téléphone de celui qui devinait vérifie que toutes les réponses reçues
 *   correspondent à ce mot ([opponentHonest]).
 * - Les manches s'enchaînent : les rôles s'échangent et le score de la séance reste affiché. Chacun des deux joueurs
 *   peut lancer la manche suivante.
 * - L'organisateur ([isHost], place 0) ouvre la salle d'attente et lance la partie ; l'invité a la place 1.
 *
 * @param chooseFirst tire au sort la place (0 ou 1) du joueur qui choisira le premier mot ; remplaçable dans les tests.
 */
internal class HangmanOnline(
    private val session: SyncSession,
    private val chooseFirst: () -> Int = { Random.nextInt(2) }
) {

    /** Vrai tant qu'une partie à deux téléphones est ouverte, de la salle d'attente à la fin de la séance. */
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

    var settings by mutableStateOf(HangmanSettings())
        private set

    var notice by mutableStateOf(HangmanNotice.NONE)
        private set

    /** Nombre de fois qu'un invité est entré dans une partie : sert à ouvrir l'écran du jeu à son arrivée. */
    var joined by mutableIntStateOf(0)
        private set

    // ---------- La manche (sur ce téléphone) ----------

    /** Vrai une fois la partie lancée par l'organisateur ; faux dans la salle d'attente. */
    var started by mutableStateOf(false)
        private set

    var stage by mutableStateOf(HangmanStage.WORD)
        private set

    /** Place du joueur qui choisit le mot de cette manche. */
    var setter by mutableIntStateOf(0)
        private set

    /** Numéro de la manche, à partir de 1. */
    var round by mutableIntStateOf(1)
        private set

    /** Manches gagnées par chaque place depuis le début de la séance. */
    var wins by mutableStateOf(listOf(0, 0))
        private set

    /** La forme du mot (des blancs et des séparateurs) ; vide tant que le mot n'est pas choisi. */
    var shape by mutableStateOf("")
        private set

    /** Mon mot, si c'est moi qui le choisis : il n'est envoyé à l'adversaire qu'à la fin de la manche. */
    var myWord by mutableStateOf<String?>(null)
        private set

    /** Les lettres proposées, avec la réponse (les places de la lettre dans le mot). */
    var guesses by mutableStateOf<List<HangmanGuess>>(emptyList())
        private set

    /** La lettre que je viens de proposer, tant que l'adversaire n'a pas répondu ; null sinon. */
    var pendingLetter by mutableStateOf<Char?>(null)
        private set

    /** Le mot de l'adversaire, dévoilé en fin de manche quand c'est lui qui l'avait choisi ; null avant. */
    var revealedWord by mutableStateOf<String?>(null)
        private set

    /** Le nom de l'autre joueur (vide tant qu'il n'est pas là). */
    val opponentName: String get() = lobby.getOrElse(1 - seat) { "" }

    /** Vrai si c'est moi qui choisis le mot de cette manche (et qui réponds aux lettres). */
    val iAmSetter: Boolean get() = started && setter == seat

    /** La manche en cours : on cherche encore, le mot est trouvé ou le bonhomme est pendu. */
    val outcome: HangmanOutcome
        get() = if (shape.isEmpty()) HangmanOutcome.PLAYING else Hangman.outcome(shape, settings, guesses)

    /** Place du joueur qui a gagné la manche, ou -1 tant qu'elle n'est pas finie. */
    val roundWinner: Int
        get() = when (outcome) {
            HangmanOutcome.WON -> 1 - setter
            HangmanOutcome.LOST -> setter
            HangmanOutcome.PLAYING -> -1
        }

    /** Vrai si c'est à moi de proposer une lettre. */
    val canGuess: Boolean
        get() = started && stage == HangmanStage.PLAYING && !iAmSetter && pendingLetter == null && outcome == HangmanOutcome.PLAYING

    /** Le mot tel que je le vois : les lettres trouvées pendant la manche, le mot entier une fois la manche finie. */
    val display: String
        get() = if (stage == HangmanStage.FINISHED) {
            myWord ?: revealedWord ?: Hangman.masked(shape, guesses)
        } else {
            Hangman.masked(shape, guesses)
        }

    /**
     * Une fois le mot de l'adversaire dévoilé : vrai si toutes ses réponses à mes lettres sont celles que ce mot devait
     * donner, faux s'il a menti ; null tant que le mot n'est pas dévoilé (ou si c'est moi qui l'avais choisi).
     */
    val opponentHonest: Boolean?
        get() = revealedWord?.let { Hangman.isConsistent(it, shape, guesses) }

    // Organisateur : l'identifiant de connexion de l'invité.
    private var peerId: String? = null

    init {
        session.hangman = this
    }

    // ---------- Organisateur ----------

    /** Ouvre la salle d'attente : la connexion doit déjà être annoncée (voir [SyncSession.startHostingHangman]). */
    fun openLobby(hostName: String, newSettings: HangmanSettings) {
        resetLocal()
        active = true
        isHost = true
        seat = 0
        lobby = listOf(hostName)
        settings = newSettings.coerced()
    }

    /** Lance la partie quand l'autre joueur est là : le premier joueur tiré au sort choisit un mot. */
    fun startGame() {
        if (!isHost || started || lobby.size < PLAYERS) return
        notice = HangmanNotice.NONE
        beginRound(chooseFirst().coerceIn(0, 1), listOf(0, 0), 1)
        send(HgMessage.Start(setter, wins, round))
    }

    // ---------- Tous les joueurs ----------

    /** Je choisis le mot de la manche : il reste sur ce téléphone, l'adversaire n'en reçoit que la forme. Faux si le mot n'est pas jouable. */
    fun submitWord(raw: String): Boolean {
        if (!iAmSetter || stage != HangmanStage.WORD || myWord != null) return false
        val word = Hangman.parseWord(raw) ?: return false
        myWord = word
        shape = Hangman.shape(word)
        stage = HangmanStage.PLAYING
        send(HgMessage.Ready(shape))
        return true
    }

    /** Je propose la lettre [letter] : la réponse arrive de l'adversaire. */
    fun guess(letter: Char) {
        if (!canGuess || !Hangman.isLetter(letter) || guesses.any { it.letter == letter }) return
        // La lettre est notée avant l'envoi : la réponse peut arriver avant la fin de l'envoi.
        pendingLetter = letter
        send(HgMessage.Guess(letter))
    }

    /** Manche suivante, une fois la manche finie : les rôles s'échangent. Chacun des deux joueurs peut la lancer. */
    fun nextRound() {
        if (!started || stage != HangmanStage.FINISHED || lobby.size < PLAYERS) return
        if (isHost) {
            startNextRound()
        } else {
            send(HgMessage.Next)
        }
    }

    /** Quitte la partie et coupe la connexion. */
    fun leave() {
        resetLocal()
        session.disconnect()
    }

    private fun startNextRound() {
        beginRound(1 - setter, wins, round + 1)
        send(HgMessage.Start(setter, wins, round))
    }

    private fun resetLocal() {
        active = false
        isHost = false
        seat = -1
        lobby = emptyList()
        notice = HangmanNotice.NONE
        peerId = null
        resetGame()
    }

    private fun resetGame() {
        started = false
        wins = listOf(0, 0)
        round = 1
        setter = 0
        resetRound()
    }

    private fun resetRound() {
        stage = HangmanStage.WORD
        shape = ""
        myWord = null
        guesses = emptyList()
        pendingLetter = null
        revealedWord = null
    }

    private fun beginRound(newSetter: Int, newWins: List<Int>, newRound: Int) {
        resetRound()
        started = true
        setter = newSetter
        wins = newWins
        round = newRound
    }

    /** La manche est finie : le score du gagnant monte d'un point (les deux téléphones comptent la même chose). */
    private fun finishRound() {
        stage = HangmanStage.FINISHED
        val winner = roundWinner
        if (winner in 0..1) wins = wins.mapIndexed { player, n -> if (player == winner) n + 1 else n }
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
            notice = if (started) HangmanNotice.PLAYER_LEFT else HangmanNotice.NONE
            resetGame()
        } else {
            notice = HangmanNotice.HOST_LEFT
            resetGame()
        }
    }

    // ---------- Messages reçus ----------

    fun onMessage(fromId: String, message: HgMessage) {
        if (message is HgMessage.Lobby) {
            if (!isHost) receiveLobby(message)
            return
        }
        if (!active || (isHost && fromId != peerId)) return
        when (message) {
            is HgMessage.Lobby -> Unit
            is HgMessage.Start -> {
                // Seul l'organisateur lance une manche ; l'invité la suit, au début ou une fois la manche finie.
                if (!isHost && (!started || stage == HangmanStage.FINISHED)) beginRound(message.setter, message.wins, message.round)
            }
            is HgMessage.Ready -> onReady(message.shape)
            is HgMessage.Guess -> onGuess(message.letter)
            is HgMessage.Report -> onReport(message.guess)
            is HgMessage.Reveal -> {
                if (started && !iAmSetter && revealedWord == null) revealedWord = message.word.take(Hangman.MAX_LENGTH)
            }
            HgMessage.Next -> if (isHost) nextRound()
        }
    }

    private fun receiveLobby(message: HgMessage.Lobby) {
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

    /** L'adversaire a choisi son mot : voici sa forme. */
    private fun onReady(newShape: String) {
        if (!started || stage != HangmanStage.WORD || iAmSetter || !Hangman.isValidShape(newShape)) return
        shape = newShape
        stage = HangmanStage.PLAYING
    }

    /** L'adversaire propose une lettre : mon téléphone répond, d'après mon mot. */
    private fun onGuess(letter: Char) {
        val word = myWord ?: return
        if (!iAmSetter || stage != HangmanStage.PLAYING) return
        val answer = Hangman.resolve(word, settings, guesses, letter) ?: return
        guesses = guesses + answer
        val over = outcome != HangmanOutcome.PLAYING
        // L'état est à jour avant l'envoi : la suite de la partie peut repartir avant la fin de l'envoi.
        if (over) finishRound()
        send(HgMessage.Report(answer))
        if (over) send(HgMessage.Reveal(word))
    }

    /** L'adversaire répond à ma lettre. */
    private fun onReport(answer: HangmanGuess) {
        if (!started || iAmSetter || stage != HangmanStage.PLAYING || pendingLetter != answer.letter) return
        pendingLetter = null
        // Seules les places possibles sont gardées (un blanc pas encore trouvé) ; une réponse truquée se voit à la fin,
        // quand le mot est dévoilé et que ses places ne correspondent pas.
        val found = guesses.flatMap { it.positions }.toSet()
        val positions = answer.positions.filter { it in shape.indices && shape[it] == Hangman.BLANK && it !in found }.distinct().sorted()
        guesses = guesses + HangmanGuess(answer.letter, positions)
        if (outcome != HangmanOutcome.PLAYING) finishRound()
    }

    // ---------- Envoi ----------

    private fun send(message: HgMessage) {
        if (isHost) {
            peerId?.let { session.sendTo(listOf(it), SyncMessage.Hg(message)) }
        } else {
            session.sendToHost(SyncMessage.Hg(message))
        }
    }

    private fun broadcastLobby() {
        send(HgMessage.Lobby(lobby, settings, notice))
    }

    companion object {
        const val PLAYERS = 2
    }
}
