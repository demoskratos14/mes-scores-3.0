package com.aventure.messcores

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy

/**
 * Lien radio entre téléphones par l'API Nearby Connections de Google : Bluetooth et connexion directe
 * entre les téléphones, sans routeur ni internet (les services Google Play sont nécessaires).
 * Tout est rapporté à [listener], sur le fil principal.
 */
internal class NearbyTransport(context: Context) : SyncTransport {

    private val client = Nearby.getConnectionsClient(context.applicationContext)

    override var listener: TransportListener? = null

    // Une étoile : un organisateur, plusieurs invités possibles.
    private val strategy = Strategy.P2P_STAR

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { listener?.onMessage(endpointId, it) }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            listener?.onConnectionInitiated(endpointId, info.endpointName, info.authenticationDigits)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val status = result.status
            if (status.isSuccess) {
                listener?.onConnected(endpointId)
            } else {
                listener?.onConnectionFailed(endpointId, status.statusCode == ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED)
            }
        }

        override fun onDisconnected(endpointId: String) {
            listener?.onDisconnected(endpointId)
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            listener?.onEndpointFound(endpointId, info.endpointName)
        }

        override fun onEndpointLost(endpointId: String) {
            listener?.onEndpointLost(endpointId)
        }
    }

    override fun startHosting(deviceName: String) {
        val options = AdvertisingOptions.Builder().setStrategy(strategy).build()
        client.startAdvertising(deviceName, SERVICE_ID, connectionCallback, options)
            .addOnFailureListener { listener?.onStartFailed() }
    }

    override fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(strategy).build()
        client.startDiscovery(SERVICE_ID, discoveryCallback, options)
            .addOnFailureListener { listener?.onStartFailed() }
    }

    override fun requestConnection(deviceName: String, endpointId: String) {
        client.requestConnection(deviceName, endpointId, connectionCallback)
            .addOnFailureListener { listener?.onConnectionFailed(endpointId, rejected = false) }
    }

    override fun acceptConnection(endpointId: String) {
        client.acceptConnection(endpointId, payloadCallback)
            .addOnFailureListener { listener?.onConnectionFailed(endpointId, rejected = false) }
    }

    override fun rejectConnection(endpointId: String) {
        client.rejectConnection(endpointId)
    }

    override fun send(endpointIds: Collection<String>, bytes: ByteArray) {
        if (endpointIds.isEmpty()) return
        client.sendPayload(endpointIds.toList(), Payload.fromBytes(bytes))
    }

    override fun disconnect(endpointId: String) {
        client.disconnectFromEndpoint(endpointId)
    }

    override fun stopAll() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
    }

    private companion object {
        // Identifie l'appli : seuls les téléphones qui ont Mes scores se voient.
        const val SERVICE_ID = "com.aventure.messcores.share"
    }
}

/**
 * Garde la session de partage en vie pendant toute la durée de l'appli (rotation de l'écran comprise) :
 * la connexion avec l'autre téléphone ne doit pas se couper parce que l'écran tourne.
 */
class SyncViewModel(application: Application) : AndroidViewModel(application) {
    internal val session = SyncSession(NearbyTransport(application))

    /** Mastermind chacun sur son téléphone, sur la même connexion que le partage des scores. */
    internal val mastermind = MastermindOnline(session)

    /** Morpion chacun sur son téléphone, sur la même connexion. */
    internal val ticTacToe = TicTacToeOnline(session)

    /** Touché coulé chacun sur son téléphone, sur la même connexion. */
    internal val battleship = BattleshipOnline(session)

    /** Jeu des allumettes chacun sur son téléphone, sur la même connexion. */
    internal val nim = NimOnline(session)

    override fun onCleared() {
        session.disconnect()
    }
}
