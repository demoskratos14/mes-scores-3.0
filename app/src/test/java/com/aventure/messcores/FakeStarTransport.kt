package com.aventure.messcores

/**
 * Un faux lien radio en étoile pour les tests : un organisateur et autant d'invités qu'on veut, chacun avec son bout
 * (voir [FakeStar.end]). Ce qu'envoie un bout arrive tout de suite aux bouts visés, sans Bluetooth.
 * Un invité cherche l'organisateur, demande la connexion ; l'organisateur et l'invité l'acceptent chacun de leur côté.
 */
internal class FakeStar {
    internal val ends = mutableMapOf<String, End>()

    /** Le bout du téléphone [id]. */
    fun end(id: String): End = End(id).also { ends[id] = it }

    internal inner class End(val selfId: String) : SyncTransport {
        override var listener: TransportListener? = null
        var hostName: String? = null
        private var discovering = false
        private val accepted = mutableSetOf<String>()
        private val links = mutableSetOf<String>()

        override fun startHosting(deviceName: String) {
            hostName = deviceName
            ends.values.filter { it.discovering }.forEach { it.listener?.onEndpointFound(selfId, deviceName) }
        }

        override fun startDiscovery() {
            discovering = true
            ends.values.filter { it !== this && it.hostName != null }.forEach { listener?.onEndpointFound(it.selfId, it.hostName!!) }
        }

        override fun requestConnection(deviceName: String, endpointId: String) {
            val host = ends.getValue(endpointId)
            listener?.onConnectionInitiated(endpointId, host.hostName ?: "?", DIGITS)
            host.listener?.onConnectionInitiated(selfId, deviceName, DIGITS)
        }

        override fun acceptConnection(endpointId: String) {
            accepted.add(endpointId)
            val other = ends.getValue(endpointId)
            if (selfId in other.accepted && endpointId !in links) {
                links.add(endpointId)
                other.links.add(selfId)
                // L'organisateur est prévenu en premier : il envoie la partie dès qu'il est connecté.
                val host = if (hostName != null) this else other
                val guest = if (host === this) other else this
                host.listener?.onConnected(guest.selfId)
                guest.listener?.onConnected(host.selfId)
            }
        }

        override fun rejectConnection(endpointId: String) {
            listener?.onConnectionFailed(endpointId, rejected = true)
            ends[endpointId]?.listener?.onConnectionFailed(selfId, rejected = true)
        }

        override fun send(endpointIds: Collection<String>, bytes: ByteArray) {
            for (id in endpointIds) if (id in links) ends[id]?.listener?.onMessage(selfId, bytes)
        }

        override fun disconnect(endpointId: String) {
            if (!links.remove(endpointId)) return
            accepted.remove(endpointId)
            val other = ends.getValue(endpointId)
            other.links.remove(selfId)
            other.accepted.remove(selfId)
            other.listener?.onDisconnected(selfId)
        }

        override fun stopAll() {
            hostName = null
            discovering = false
            links.toList().forEach { disconnect(it) }
            accepted.clear()
        }
    }

    private companion object {
        const val DIGITS = "4821"
    }
}
