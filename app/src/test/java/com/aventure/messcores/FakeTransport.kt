package com.aventure.messcores

/**
 * Un des deux bouts d'un faux lien radio entre deux téléphones, pour les tests : ce qu'envoie l'un arrive tout de
 * suite à l'autre, sans Bluetooth. Relier deux bouts : `a.peer = b` et `b.peer = a`.
 */
internal class FakeTransport(private val selfId: String) : SyncTransport {
    override var listener: TransportListener? = null
    lateinit var peer: FakeTransport
    private var hostName: String? = null
    private var discovering = false
    private var accepted = false
    private var connected = false

    override fun startHosting(deviceName: String) {
        hostName = deviceName
        if (peer.discovering) peer.listener?.onEndpointFound(selfId, deviceName)
    }

    override fun startDiscovery() {
        discovering = true
        peer.hostName?.let { listener?.onEndpointFound(peer.selfId, it) }
    }

    override fun requestConnection(deviceName: String, endpointId: String) {
        listener?.onConnectionInitiated(peer.selfId, peer.hostName ?: "?", DIGITS)
        peer.listener?.onConnectionInitiated(selfId, deviceName, DIGITS)
    }

    override fun acceptConnection(endpointId: String) {
        accepted = true
        if (peer.accepted && !connected) {
            connected = true
            peer.connected = true
            // L'organisateur est prévenu en premier : il envoie la partie dès qu'il est connecté.
            val host = if (hostName != null) this else peer
            val guest = if (host === this) peer else this
            host.listener?.onConnected(guest.selfId)
            guest.listener?.onConnected(host.selfId)
        }
    }

    override fun rejectConnection(endpointId: String) {
        listener?.onConnectionFailed(peer.selfId, rejected = true)
        peer.listener?.onConnectionFailed(selfId, rejected = true)
    }

    override fun send(endpointIds: Collection<String>, bytes: ByteArray) {
        if (connected || peer.connected) peer.listener?.onMessage(selfId, bytes)
    }

    override fun disconnect(endpointId: String) {
        if (connected) {
            connected = false
            peer.connected = false
            peer.accepted = false
            accepted = false
            peer.listener?.onDisconnected(selfId)
        }
    }

    override fun stopAll() {
        hostName = null
        discovering = false
        accepted = false
        if (connected) {
            connected = false
            peer.connected = false
            peer.accepted = false
            peer.listener?.onDisconnected(selfId)
        }
    }

    private companion object {
        const val DIGITS = "4821"
    }
}
