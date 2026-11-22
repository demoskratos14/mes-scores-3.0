package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/** Nombre de joueurs de la partie test : l'organisateur et des joueurs simulés. */
internal const val WEREWOLF_TEST_PLAYERS = 6

/**
 * Loup-garou de Thiercelieux chacun sur son téléphone, sur la connexion de [SyncSession] (la salle multijoueur).
 *
 * - L'organisateur ([isHost]) tient la partie et fait aussi le narrateur : son téléphone lit la partie à voix haute et mène les
 *   étapes (voir [WerewolfRound]), mais il joue comme les autres. Il est la place 0 ; les invités prennent les places suivantes
 *   dans l'ordre d'arrivée.
 * - Les joueurs arrivent tant que la partie n'est pas lancée ([acceptsPlayers]) ; de [WEREWOLF_MIN_PLAYERS] à
 *   [WEREWOLF_MAX_PLAYERS] joueurs. Ensuite la table est fermée. Avant le lancement, l'organisateur règle les cartes
 *   ([wolves], [specials]) : par défaut elles dépendent du nombre de joueurs.
 * - L'organisateur envoie à chaque joueur sa propre vue ([WerewolfRound.viewFor]) après chaque changement : un joueur ne reçoit
 *   jamais la carte d'un autre. Les invités n'envoient que leurs actions ([act]), que l'organisateur vérifie.
 * - Un joueur qui part en cours de partie est éliminé et la partie continue. Si l'organisateur part, la partie s'arrête.
 * - Seul, l'organisateur peut lancer une partie test ([startTest]) : une nuit et un jour avec des joueurs simulés ([WerewolfBots]),
 *   pour vérifier le déroulement avant de jouer avec du monde. Il peut aussi lancer le test complet ([startFullTest]) : toutes les
 *   cartes sont à la table et son téléphone joue lui-même chaque place, tour à tour ([controlled]), pour essayer chaque pouvoir.
 */
internal class WerewolfOnline(
    private val session: SyncSession,
    private val random: Random = Random.Default
) {

    /** Vrai tant qu'une table est ouverte, de l'attente des joueurs à la fin de la séance. */
    var active by mutableStateOf(false)
        private set

    var isHost by mutableStateOf(false)
        private set

    /** Ma place de joueur (0 : l'organisateur, puis les invités dans l'ordre d'arrivée), ou -1 hors partie. */
    var seat by mutableIntStateOf(-1)
        private set

    /** Les joueurs à la table, dans l'ordre des places (l'organisateur en premier). */
    var lobby by mutableStateOf<List<String>>(emptyList())
        private set

    var notice by mutableStateOf(WerewolfNotice.NONE)
        private set

    /** Nombre de fois qu'un invité est entré dans une partie : sert à ouvrir l'écran du jeu à son arrivée. */
    var joined by mutableIntStateOf(0)
        private set

    /** Augmente à chaque changement de la partie : l'écran se redessine. */
    var version by mutableIntStateOf(0)
        private set

    /** Ma vue de la partie ; null tant qu'elle n'est pas lancée. */
    var view by mutableStateOf<WerewolfView?>(null)
        private set

    /** La partie (organisateur seulement) ; null tant qu'elle n'est pas lancée. */
    var round: WerewolfRound? = null
        private set

    // ---------- Réglage des cartes (organisateur, avant le lancement) ----------

    /** Nombre de loups-garous dans la partie. */
    var wolves by mutableIntStateOf(1)
        private set

    /** Les cartes à pouvoir dans la partie ; les places restantes sont des villageois. */
    var specials by mutableStateOf<Set<WerewolfRole>>(emptySet())
        private set

    /** Vrai si l'organisateur a changé les cartes à la main : elles ne suivent plus le nombre de joueurs. */
    var customCards by mutableStateOf(false)
        private set

    // ---------- Partie test (organisateur seul) ----------

    /** Vrai pendant une partie test : les places 1 et suivantes sont des joueurs simulés. */
    var isTest by mutableStateOf(false)
        private set

    /** Vrai quand la partie test a déroulé une nuit et un jour. */
    var testFinished by mutableStateOf(false)
        private set

    /** Le rôle que l'organisateur veut essayer pendant le test (null : au hasard). */
    var testRole by mutableStateOf<WerewolfRole?>(null)
        private set

    /** Vrai pendant le test complet : toutes les cartes sont à la table et ce téléphone joue chaque place à tour de rôle. */
    var testAll by mutableStateOf(false)
        private set

    /** Test complet : la place que ce téléphone joue en ce moment (la première dont une action est attendue). */
    var controlled by mutableIntStateOf(0)
        private set

    // Organisateur : les identifiants de connexion des invités (le premier invité a la place 1, etc.), et les places de ceux qui
    // sont partis en cours de partie.
    private val guestIds = mutableListOf<String>()
    private val gone = mutableSetOf<Int>()

    /** Vrai si les cartes réglées tiennent à la table des joueurs présents. */
    val validCards: Boolean get() = isValidWerewolfDeal(lobby.size, wolves, specials)

    /** Nombre de villageois que les réglages laissent à la table. */
    val villagers: Int get() = lobby.size - wolves - werewolfSeats(specials)

    /** Vrai (organisateur) quand assez de joueurs sont là et que les cartes tiennent : la partie peut être lancée. */
    val ready: Boolean get() = active && isHost && round == null && lobby.size >= WEREWOLF_MIN_PLAYERS && validCards

    /** Vrai (organisateur) tant qu'un téléphone de plus peut encore s'asseoir à la table. */
    val acceptsPlayers: Boolean get() = active && isHost && round == null && lobby.size < WEREWOLF_MAX_PLAYERS

    /** Vrai (organisateur) quand une partie test peut être lancée : seul à la table. */
    val canTest: Boolean get() = active && isHost && round == null && lobby.size == 1

    /** Vrai quand la partie est lancée (chez l'organisateur comme chez un joueur). */
    val started: Boolean get() = if (isHost) round != null else view != null

    init {
        session.werewolf = this
    }

    // ---------- Organisateur ----------

    /** Ouvre la table : la connexion doit déjà être ouverte (voir [SyncSession.startRoomGame]). L'organisateur y prend la place 0. */
    fun openLobby(hostName: String) {
        resetLocal()
        active = true
        isHost = true
        seat = 0
        lobby = listOf(hostName)
        customCards = false
        applyDefaultCards()
    }

    /** Remet les cartes au réglage automatique du nombre de joueurs présents. */
    fun resetCards() {
        customCards = false
        applyDefaultCards()
    }

    private fun applyDefaultCards() {
        val players = lobby.size.coerceAtLeast(WEREWOLF_MIN_PLAYERS)
        wolves = werewolfCount(players)
        specials = defaultWerewolfSpecials(players).toSet()
    }

    /** Ajoute ou retire la carte [role] de la partie. */
    fun toggleSpecial(role: WerewolfRole) {
        if (role in WEREWOLF_SPECIALS) {
            customCards = true
            specials = if (role in specials) specials - role else specials + role
        }
    }

    /** Change le nombre de loups-garous de [delta]. */
    fun changeWolves(delta: Int) {
        customCards = true
        wolves = (wolves + delta).coerceIn(1, WEREWOLF_MAX_PLAYERS - 1)
    }

    /** Lance la partie : les cartes réglées sont distribuées. */
    fun start() {
        if (!ready) return
        val deal = dealWerewolf(lobby.size, wolves, specials, random)
        round = WerewolfRound(lobby.toList(), random, deal.roles, deal.table, WerewolfRole.CAPTAIN in specials)
        isTest = false
        testFinished = false
        gone.clear()
        publish()
    }

    /** Passe au rôle suivant pour le test (au hasard, puis chaque carte l'une après l'autre). */
    fun cycleTestRole() {
        val all = WerewolfRole.entries
        val next = testRole?.let { all.indexOf(it) + 1 } ?: 0
        testRole = all.getOrNull(next)
    }

    /**
     * Lance la partie test : l'organisateur et des joueurs simulés (leurs noms : [botNames]), avec les cartes réglées par défaut
     * pour [WEREWOLF_TEST_PLAYERS] joueurs, auxquelles s'ajoute [testRole] si l'organisateur en a choisi un : il le reçoit.
     */
    fun startTest(botNames: List<String>) {
        if (!canTest || botNames.size != WEREWOLF_TEST_PLAYERS - 1) return
        val wanted = testRole
        val testSpecials = defaultWerewolfSpecials(WEREWOLF_TEST_PLAYERS).toMutableList()
        if (wanted != null && wanted in WEREWOLF_SPECIALS && wanted !in testSpecials) testSpecials.add(0, wanted)
        // Les cartes doivent tenir à la table de test : on retire celles de la fin.
        while (!isValidWerewolfDeal(WEREWOLF_TEST_PLAYERS, 1, testSpecials)) testSpecials.removeAt(testSpecials.lastIndex)
        val deal = dealWerewolf(WEREWOLF_TEST_PLAYERS, 1, testSpecials, random)
        val roles = deal.roles.toMutableList()
        if (wanted != null) {
            // L'organisateur (place 0) prend le rôle essayé, s'il est une carte de la partie : il l'échange avec celui qui l'avait.
            val holder = roles.indexOf(wanted)
            if (holder > 0) {
                roles[holder] = roles[0]
                roles[0] = wanted
            }
        }
        round = WerewolfRound(listOf(lobby[0]) + botNames, random, roles, deal.table, WerewolfRole.CAPTAIN in testSpecials)
        isTest = true
        testAll = false
        controlled = 0
        testFinished = false
        gone.clear()
        publish()
    }

    /**
     * Lance le test complet : une carte de chaque rôle à la table (un loup-garou simple, toutes les cartes à pouvoir, le Capitaine
     * élu), et ce téléphone joue chaque place à tour de rôle : à chaque étape il affiche la vue de la première place dont une
     * action est attendue ([controlled]). [seatName] donne le nom de la place [place] (à partir de 0) qui a la carte [role].
     * Rien n'est joué au hasard tant qu'on ne le demande pas ([randomForOthers]).
     */
    fun startFullTest(seatName: (place: Int, role: WerewolfRole) -> String) {
        if (!canTest) return
        val cards = mutableListOf(WerewolfRole.WEREWOLF)
        for (role in WEREWOLF_SPECIALS) repeat(role.seatCount()) { cards.add(role) }
        val roles = cards.shuffled(random)
        val names = roles.mapIndexed { place, role -> seatName(place, role) }
        // Les deux cartes du Voleur : un villageois et un loup-garou, pour qu'il puisse essayer les deux.
        val table = listOf(WerewolfRole.VILLAGER, WerewolfRole.WEREWOLF)
        round = WerewolfRound(names, random, roles, table, true)
        isTest = true
        testAll = true
        controlled = 0
        testFinished = false
        gone.clear()
        publish()
    }

    /** Test complet : passe à la place suivante dont une action est attendue (s'il y en a plusieurs). */
    fun cycleControlled() {
        val current = round ?: return
        if (!testAll) return
        val expected = current.expected()
        if (expected.size < 2) return
        controlled = expected.getOrElse(expected.indexOf(controlled) + 1) { expected.first() }
        publish()
    }

    /** Test complet : les places dont une action est attendue, sauf celle que je joue, jouent un coup permis au hasard. */
    fun randomForOthers() {
        val current = round ?: return
        if (!testAll) return
        var changed = false
        for (other in current.expected().filter { it != controlled }) {
            val action = WerewolfBots.action(current, other, random) ?: continue
            if (current.perform(other, action)) changed = true
        }
        if (changed) publish()
    }

    /** Joue les coups des joueurs simulés qui ont quelque chose à faire (partie test) ; pas dans le test complet, où je joue tout. */
    fun runBots() {
        val current = round ?: return
        if (!isTest || testAll) return
        var changed = false
        for (botSeat in current.expected().filter { it != 0 }) {
            val action = WerewolfBots.action(current, botSeat, random) ?: continue
            if (current.perform(botSeat, action)) changed = true
        }
        if (changed) publish()
    }

    /** La partie test a déroulé une nuit et un jour. */
    fun finishTest() {
        if (isTest) {
            testFinished = true
            version++
        }
    }

    /** Le narrateur passe à l'étape suivante. */
    fun advance() {
        val current = round ?: return
        current.advance()
        publish()
    }

    /** Après la partie (ou le test) : tout le monde revient à la table d'attente, avec les mêmes joueurs. */
    fun backToLobby() {
        if (!isHost) return
        round = null
        isTest = false
        testAll = false
        controlled = 0
        testFinished = false
        // Ceux qui étaient partis ne sont plus à la table.
        for (gonePlace in gone.sortedDescending()) {
            guestIds.removeAt(gonePlace - 1)
            lobby = lobby.filterIndexed { i, _ -> i != gonePlace }
        }
        gone.clear()
        if (!customCards) applyDefaultCards()
        publish()
    }

    // ---------- Tous les joueurs ----------

    /** Ma décision : l'organisateur l'applique tout de suite, un invité l'envoie à l'organisateur, qui la vérifie. */
    fun act(kind: WerewolfActionKind, a: Int = -1, b: Int = -1) {
        val action = WerewolfAction(kind, a, b)
        if (isHost) {
            val current = round ?: return
            // Dans le test complet, je joue la place affichée ; sinon, la mienne (la place 0).
            if (current.perform(if (testAll) controlled else 0, action)) publish()
        } else {
            session.sendToHost(SyncMessage.WerewolfMsg(WerewolfMessage.Act(action)))
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
        notice = WerewolfNotice.NONE
        view = null
        round = null
        isTest = false
        testAll = false
        controlled = 0
        testFinished = false
        testRole = null
        wolves = 1
        specials = emptySet()
        customCards = false
        guestIds.clear()
        gone.clear()
        version++
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
        if (!customCards) applyDefaultCards()
        publish()
    }

    fun onPeerDisconnected(id: String) {
        if (!active) return
        if (!isHost) {
            view = null
            notice = WerewolfNotice.HOST_LEFT
            return
        }
        val index = guestIds.indexOf(id)
        if (index < 0) return
        val place = index + 1
        val current = round
        if (current == null) {
            // Avant la partie : sa place disparaît et les suivants avancent d'un rang.
            guestIds.removeAt(index)
            lobby = lobby.filterIndexed { i, _ -> i != place }
            if (!customCards) applyDefaultCards()
        } else {
            // En cours de partie : les places ne changent pas, le joueur est éliminé.
            gone.add(place)
            current.forfeit(place)
        }
        publish()
    }

    // ---------- Messages reçus ----------

    fun onMessage(fromId: String, message: WerewolfMessage) {
        if (isHost) onHostMessage(fromId, message) else onGuestMessage(message)
    }

    private fun onGuestMessage(message: WerewolfMessage) {
        if (message !is WerewolfMessage.Table) return
        val firstTime = !active
        active = true
        isHost = false
        seat = message.seat
        lobby = message.names
        notice = message.notice
        view = message.view
        if (firstTime) joined++
        version++
    }

    private fun onHostMessage(fromId: String, message: WerewolfMessage) {
        if (!active || message !is WerewolfMessage.Act) return
        val current = round ?: return
        val place = guestIds.indexOf(fromId) + 1
        if (place <= 0 || place in gone) return
        if (current.perform(place, message.action)) publish()
    }

    /** Envoie à chaque invité sa vue de la table, met à jour celle de l'organisateur et redessine l'écran. */
    private fun publish() {
        version++
        if (!isHost) return
        val current = round
        if (testAll && current != null) {
            // Test complet : on montre la place dont une action est attendue ; si la mienne a fini, la suivante.
            val expected = current.expected()
            if (expected.isNotEmpty() && controlled !in expected) controlled = expected.first()
        }
        view = current?.viewFor(if (testAll) controlled else 0)
        guestIds.forEachIndexed { index, id ->
            val place = index + 1
            if (place in gone) return@forEachIndexed
            session.sendTo(
                listOf(id),
                SyncMessage.WerewolfMsg(WerewolfMessage.Table(lobby, place, notice, current?.viewFor(place)))
            )
        }
    }
}
