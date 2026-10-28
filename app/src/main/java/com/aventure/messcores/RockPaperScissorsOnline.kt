package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Pierre-feuille-ciseaux chacun sur son téléphone, sur la connexion de [SyncSession].
 *
 * - Deux joueurs exactement : l'organisateur ([isHost]) est le joueur 1, l'invité le joueur 2. La partie commence
 *   dès que l'invité est connecté.
 * - Chacun choisit en secret sur son téléphone. L'organisateur tient la partie ([match]) ; l'invité lui envoie son
 *   choix, l'organisateur le vérifie puis renvoie l'état.
 * - Le choix d'un joueur ne quitte jamais son téléphone avant la révélation : la copie envoyée à l'invité cache le
 *   choix de l'organisateur tant que l'invité n'a pas choisi (voir [RpsMatch.maskedFor]), et l'invité n'envoie le sien
 *   qu'à l'organisateur, qui ne le renvoie qu'une fois la manche révélée.
 * - Les manches s'enchaînent jusqu'à ce qu'un joueur ait gagné le nombre de manches choisi ; chacun peut alors lancer
 *   la manche suivante, ou une nouvelle partie.
 */
internal class RockPaperScissorsOnline(private val session: SyncSession) {

    /** Vrai tant qu'une partie à deux téléphones est ouverte, de l'attente de l'autre joueur à la fin de la séance. */
    var active by mutableStateOf(false)
        private set

    var isHost by mutableStateOf(false)
        private set

    /** Ma place à la table (0 : organisateur, joueur 1 ; 1 : invité, joueur 2), ou -1 hors partie. */
    var seat by mutableIntStateOf(-1)
        private set

    /** Les joueurs à la table, dans l'ordre des places. */
    var lobby by mutableStateOf<List<String>>(emptyList())
        private set

    /**
     * La partie telle que ce téléphone la connaît. Sur l'organisateur elle contient les deux choix : pour
     * l'affichage, passer par [view], qui cache celui de l'autre joueur tant que la manche n'est pas révélée.
     */
    var match by mutableStateOf(RpsMatch())
        private set

    var notice by mutableStateOf(RpsNotice.NONE)
        private set

    /** Nombre de fois qu'un invité est entré dans une partie : sert à ouvrir l'écran du jeu à son arrivée. */
    var joined by mutableIntStateOf(0)
        private set

    /** Manches à gagner pour la prochaine partie que j'organise (voir [chooseTarget]). */
    var target by mutableIntStateOf(RockPaperScissors.DEFAULT_TARGET)
        private set

    /** Version « lézard Spock » pour la prochaine partie que j'organise (voir [chooseExtended]). */
    var extended by mutableStateOf(false)
        private set

    /** Mon numéro de joueur dans la partie : 1 pour l'organisateur, 2 pour l'invité. */
    val me: Int get() = seat + 1

    /** La partie à afficher sur ce téléphone : le choix de l'autre joueur reste caché tant que la manche n'est pas révélée. */
    val view: RpsMatch get() = match.maskedFor(me)

    /** Vrai quand les deux joueurs sont là et que la partie peut se jouer. */
    val ready: Boolean get() = active && lobby.size >= PLAYERS

    /** Vrai quand je peux choisir : la partie est lancée, pas finie, et je n'ai pas encore choisi pour cette manche. */
    val canPick: Boolean get() = ready && !match.isOver && !match.hasPicked(me)

    // Organisateur : l'identifiant de connexion de l'invité.
    private var guestId: String? = null

    init {
        session.rockPaperScissors = this
    }

    /** Nombre de manches à gagner de la prochaine partie organisée ; une valeur non proposée est ignorée. */
    fun chooseTarget(value: Int) {
        if (value in RockPaperScissors.TARGETS) target = value
    }

    /** Version de la prochaine partie organisée : classique, ou « lézard Spock » si [value]. */
    fun chooseExtended(value: Boolean) {
        extended = value
    }

    // ---------- Organisateur ----------

    /** Ouvre la table : la connexion doit déjà être annoncée (voir [SyncSession.startHostingRockPaperScissors]). */
    fun openLobby(hostName: String) {
        resetLocal()
        active = true
        isHost = true
        seat = 0
        lobby = listOf(hostName)
        match = RpsMatch(target = target, extended = extended)
    }

    // ---------- Les deux joueurs ----------

    /** Je choisis [choice] : l'organisateur l'applique, l'invité l'envoie à l'organisateur. */
    fun pick(choice: Int) {
        if (!canPick || !RockPaperScissors.isChoice(choice)) return
        if (isHost) {
            match = match.pick(me, choice) ?: return
            broadcast()
        } else {
            session.sendToHost(SyncMessage.Rps(RpsMessage.Pick(choice)))
        }
    }

    /** Manche suivante (ou nouvelle partie si celle-ci est finie), une fois la manche révélée ; chacun peut la lancer. */
    fun next() {
        if (!ready) return
        if (isHost) {
            match = match.advance() ?: return
            broadcast()
        } else {
            session.sendToHost(SyncMessage.Rps(RpsMessage.Next))
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
        match = RpsMatch()
        notice = RpsNotice.NONE
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
        match = RpsMatch(target = target, extended = extended)
        notice = RpsNotice.NONE
        broadcast()
    }

    fun onPeerDisconnected(id: String) {
        if (!active) return
        if (isHost) {
            if (id != guestId) return
            // L'invité parti, la table repart de zéro : un autre téléphone peut la rejoindre.
            guestId = null
            lobby = lobby.take(1)
            match = RpsMatch(target = target, extended = extended)
            notice = RpsNotice.PLAYER_LEFT
        } else {
            notice = RpsNotice.HOST_LEFT
        }
    }

    // ---------- Messages reçus ----------

    fun onMessage(fromId: String, message: RpsMessage) {
        if (isHost) onHostMessage(fromId, message) else onGuestMessage(message)
    }

    private fun onGuestMessage(message: RpsMessage) {
        if (message !is RpsMessage.Table) return
        val firstTime = !active
        active = true
        isHost = false
        seat = message.seat
        lobby = message.names
        match = message.match
        notice = message.notice
        if (firstTime) joined++
    }

    private fun onHostMessage(fromId: String, message: RpsMessage) {
        if (!active || fromId != guestId) return
        when (message) {
            // L'invité est le joueur 2 ; son choix est refusé s'il a déjà choisi ou si la partie est finie.
            is RpsMessage.Pick -> match = match.pick(GUEST_PLAYER, message.choice) ?: return
            RpsMessage.Next -> match = match.advance() ?: return
            else -> return
        }
        broadcast()
    }

    private fun broadcast() {
        val id = guestId ?: return
        session.sendTo(
            listOf(id),
            SyncMessage.Rps(RpsMessage.Table(lobby, 1, match.maskedFor(GUEST_PLAYER), notice))
        )
    }

    companion object {
        const val PLAYERS = 2
        private const val GUEST_PLAYER = 2
    }
}
