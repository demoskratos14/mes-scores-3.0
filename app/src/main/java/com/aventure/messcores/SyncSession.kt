package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Un téléphone vu par la connexion : son identifiant technique et le nom qu'il affiche. */
internal data class NearbyEndpoint(val id: String, val name: String)

/** Une connexion en cours d'établissement : le code à vérifier sur les deux téléphones. */
internal data class PendingConnection(val id: String, val name: String, val digits: String)

internal enum class SyncRole { NONE, HOST, GUEST }

/** Ce qui est partagé : la partie en cours d'un jeu de score, ou un Mastermind chacun sur son téléphone. */
internal enum class SyncKind { SCORE, MASTERMIND }

/** Ce qui a mal tourné, pour l'expliquer à l'utilisateur. */
internal enum class SyncProblem {
    /** La recherche ou l'annonce n'a pas pu démarrer (Bluetooth coupé, services Google Play absents…). */
    START_FAILED,

    /** La connexion à l'autre téléphone a échoué. */
    CONNECTION_FAILED,

    /** L'organisateur a refusé la connexion. */
    REFUSED,

    /** La connexion avec l'organisateur est perdue. */
    HOST_LOST,

    /** La partie est trop volumineuse pour être envoyée. */
    TOO_BIG
}

/**
 * Le lien radio entre deux téléphones (voir [NearbyTransport]). Tout ce qui arrive est rapporté à [listener].
 * Une interface à part pour pouvoir tester le partage avec un faux lien, sans téléphones.
 */
internal interface SyncTransport {
    var listener: TransportListener?
    fun startHosting(deviceName: String)
    fun startDiscovery()
    fun requestConnection(deviceName: String, endpointId: String)
    fun acceptConnection(endpointId: String)
    fun rejectConnection(endpointId: String)
    fun send(endpointIds: Collection<String>, bytes: ByteArray)

    /** Coupe la connexion avec un seul téléphone. */
    fun disconnect(endpointId: String)

    /** Coupe toutes les connexions et arrête l'annonce et la recherche. */
    fun stopAll()
}

internal interface TransportListener {
    fun onEndpointFound(id: String, name: String)
    fun onEndpointLost(id: String)

    /** [digits] : le code à quatre chiffres affiché sur les deux téléphones pour vérifier qu'on se connecte au bon. */
    fun onConnectionInitiated(id: String, name: String, digits: String)
    fun onConnected(id: String)
    fun onConnectionFailed(id: String, rejected: Boolean)
    fun onDisconnected(id: String)
    fun onMessage(id: String, bytes: ByteArray)

    /** La recherche ou l'annonce n'a pas pu démarrer. */
    fun onStartFailed()
}

/** Ce que [ScoreViewModel] demande à la session de partage : l'invité n'agit pas lui-même, il envoie à l'organisateur. */
internal interface SyncRemote {
    /** Vrai pour un invité connecté : ses actions sont envoyées à l'organisateur au lieu d'être appliquées ici. */
    val isGuest: Boolean

    /** Annuler et Rétablir de l'organisateur, que l'invité ne peut pas deviner. */
    val hostCanUndo: Boolean
    val hostCanRedo: Boolean

    fun sendOp(op: SyncOp)
}

/**
 * Partage de la partie en cours avec d'autres téléphones, sans internet. Vit pour toute la durée de l'appli
 * (voir [SyncViewModel]) pour survivre à une rotation de l'écran.
 *
 * - Organisateur ([SyncRole.HOST]) : annonce sa partie, accepte les invités, leur envoie l'état de la partie
 *   à chaque changement ([broadcastState]) et applique ce qu'ils font.
 * - Invité ([SyncRole.GUEST]) : cherche les parties à proximité, se connecte, affiche l'état reçu et envoie
 *   ses actions à l'organisateur.
 */
internal class SyncSession(private val transport: SyncTransport) : TransportListener, SyncRemote {

    var role by mutableStateOf(SyncRole.NONE)
        private set

    /** Ce qui est partagé avec les téléphones connectés. Un invité l'apprend au premier message de l'organisateur. */
    var kind by mutableStateOf(SyncKind.SCORE)
        private set

    /** Le Mastermind chacun sur son téléphone, qui reçoit ses messages et les événements de connexion. */
    var mastermind: MastermindOnline? = null

    /** Recherche en cours (invité). */
    var scanning by mutableStateOf(false)
        private set

    /** Annonce en cours (organisateur). */
    var advertising by mutableStateOf(false)
        private set

    /** Parties trouvées à proximité (invité). */
    val endpoints = mutableStateListOf<NearbyEndpoint>()

    /** Téléphones connectés. */
    val connected = mutableStateListOf<NearbyEndpoint>()

    /** Connexion en attente : l'organisateur doit l'accepter ou la refuser, l'invité attend sa réponse. */
    var pending by mutableStateOf<PendingConnection?>(null)
        private set

    var problem by mutableStateOf<SyncProblem?>(null)
        private set

    /** Nombre d'états reçus de l'organisateur : sert à réagir à l'arrivée d'une partie (invité). */
    var receivedStates by mutableIntStateOf(0)
        private set

    override var hostCanUndo by mutableStateOf(false)
        private set

    override var hostCanRedo by mutableStateOf(false)
        private set

    private var game: ScoreViewModel? = null
    private val names = HashMap<String, String>()

    init {
        transport.listener = this
    }

    /** Relie la session à la partie de l'appli. */
    fun attach(game: ScoreViewModel) {
        this.game = game
        game.remote = this
    }

    val isConnected: Boolean get() = connected.isNotEmpty()

    override val isGuest: Boolean get() = role == SyncRole.GUEST && connected.isNotEmpty() && kind == SyncKind.SCORE

    /** Vrai si la partie en cours peut être partagée : une partie ouverte, au tableau, au compteur ou à manches. */
    fun canShareCurrentGame(): Boolean {
        val game = game ?: return false
        return game.isGameActive && game.gameRules.scoreMode in SHAREABLE_MODES
    }

    /** Annonce la partie en cours aux téléphones proches. Renvoie faux si elle ne peut pas être partagée. */
    fun startHosting(deviceName: String): Boolean {
        if (!canShareCurrentGame()) return false
        reset()
        role = SyncRole.HOST
        advertising = true
        transport.startHosting(deviceName)
        return true
    }

    /** Cherche les parties annoncées à proximité. */
    /** Annonce un Mastermind chacun sur son téléphone : les autres joueurs le rejoignent comme une partie de score. */
    fun startHostingMastermind(deviceName: String) {
        reset()
        role = SyncRole.HOST
        kind = SyncKind.MASTERMIND
        advertising = true
        transport.startHosting(deviceName)
    }

    fun startJoining() {
        reset()
        role = SyncRole.GUEST
        scanning = true
        transport.startDiscovery()
    }

    fun connectTo(endpoint: NearbyEndpoint, deviceName: String) {
        problem = null
        transport.requestConnection(deviceName, endpoint.id)
    }

    /** L'organisateur accepte la connexion en attente. */
    fun accept() {
        val request = pending ?: return
        pending = null
        transport.acceptConnection(request.id)
    }

    /** L'organisateur refuse la connexion en attente. */
    fun refuse() {
        val request = pending ?: return
        pending = null
        transport.rejectConnection(request.id)
    }

    /** Coupe tout : la partie reste sur chaque téléphone, mais n'est plus partagée. */
    fun disconnect() {
        reset()
        problem = null
    }

    private fun reset() {
        transport.stopAll()
        role = SyncRole.NONE
        scanning = false
        advertising = false
        endpoints.clear()
        connected.clear()
        names.clear()
        pending = null
        kind = SyncKind.SCORE
        hostCanUndo = false
        hostCanRedo = false
    }

    /** À appeler à chaque changement de la partie : l'organisateur en envoie l'état complet à tous les invités. */
    fun broadcastState() {
        if (role != SyncRole.HOST || kind != SyncKind.SCORE || connected.isEmpty()) return
        sendState(connected.map { it.id })
    }

    private fun sendState(ids: Collection<String>) {
        val game = game ?: return
        send(ids, SyncMessage.State(game.snapshot(), game.canUndo, game.canRedo))
    }

    override fun sendOp(op: SyncOp) {
        if (role != SyncRole.GUEST || connected.isEmpty()) return
        send(connected.map { it.id }, SyncMessage.Operation(op))
    }

    /** Nom affiché par le téléphone [id]. */
    fun nameOf(id: String): String = names[id] ?: id

    /** Envoie [message] aux téléphones [ids]. */
    fun sendTo(ids: Collection<String>, message: SyncMessage) = send(ids, message)

    /** Envoie [message] à l'organisateur (invité). */
    fun sendToHost(message: SyncMessage) {
        if (role == SyncRole.GUEST) send(connected.map { it.id }, message)
    }

    /** Coupe la connexion avec un seul téléphone. */
    fun disconnectEndpoint(id: String) {
        transport.disconnect(id)
        connected.removeAll { it.id == id }
    }

    private fun send(ids: Collection<String>, message: SyncMessage) {
        val bytes = SyncCodec.encode(message)
        if (bytes.size > SyncCodec.MAX_BYTES) {
            problem = SyncProblem.TOO_BIG
            return
        }
        transport.send(ids, bytes)
    }

    // ---------- Ce que rapporte le lien radio ----------

    override fun onEndpointFound(id: String, name: String) {
        if (role == SyncRole.GUEST && endpoints.none { it.id == id }) endpoints.add(NearbyEndpoint(id, name))
    }

    override fun onEndpointLost(id: String) {
        endpoints.removeAll { it.id == id }
    }

    override fun onConnectionInitiated(id: String, name: String, digits: String) {
        names[id] = name
        pending = PendingConnection(id, name, digits)
        // L'invité a choisi lui-même la partie à rejoindre : c'est à l'organisateur de décider.
        if (role == SyncRole.GUEST) transport.acceptConnection(id)
    }

    override fun onConnected(id: String) {
        pending = null
        connected.removeAll { it.id == id }
        connected.add(NearbyEndpoint(id, names[id] ?: id))
        if (role == SyncRole.HOST && kind == SyncKind.SCORE) sendState(listOf(id))
        mastermind?.onPeerConnected(id)
    }

    override fun onConnectionFailed(id: String, rejected: Boolean) {
        if (pending?.id == id) pending = null
        if (role == SyncRole.GUEST) problem = if (rejected) SyncProblem.REFUSED else SyncProblem.CONNECTION_FAILED
    }

    override fun onDisconnected(id: String) {
        connected.removeAll { it.id == id }
        mastermind?.onPeerDisconnected(id)
        if (role == SyncRole.GUEST && connected.isEmpty()) {
            // La partie reste sur ce téléphone ; les actions s'y appliquent de nouveau directement.
            reset()
            problem = SyncProblem.HOST_LOST
        }
    }

    override fun onMessage(id: String, bytes: ByteArray) {
        val message = SyncCodec.decode(bytes) ?: return
        when (message) {
            is SyncMessage.Mm -> {
                if (role == SyncRole.GUEST) kind = SyncKind.MASTERMIND
                mastermind?.onMessage(id, message.mm)
            }
            is SyncMessage.State -> {
                val game = game ?: return
                if (role == SyncRole.GUEST) {
                    game.loadFromSaved(message.game)
                    hostCanUndo = message.canUndo
                    hostCanRedo = message.canRedo
                    receivedStates++
                }
            }
            is SyncMessage.Operation -> {
                val game = game ?: return
                if (role == SyncRole.HOST && kind == SyncKind.SCORE && game.isGameActive) message.op.applyTo(game)
            }
        }
    }

    override fun onStartFailed() {
        reset()
        problem = SyncProblem.START_FAILED
    }

    private companion object {
        val SHAREABLE_MODES = setOf(ScoreMode.TABLE, ScoreMode.COUNTER, ScoreMode.VARIABLE_TEAMS)
    }
}
