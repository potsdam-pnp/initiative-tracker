package io.github.potsdam_pnp.initiative_tracker

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import io.github.aakira.napier.Napier
import io.github.potsdam_pnp.initiative_tracker.state.ActionWrapper
import io.github.potsdam_pnp.initiative_tracker.state.ClientIdentifier
import io.github.potsdam_pnp.initiative_tracker.state.Snapshot
import io.github.potsdam_pnp.initiative_tracker.state.State
import io.github.potsdam_pnp.initiative_tracker.state.VectorClock
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update


data class ConnectionInformation(
    val port: Int,
    val hosts: List<String>
)

data class ServiceInfo(
    val name: String,
    val clientId: ClientIdentifier?,
    val connectionInformation: ConnectionInformation
)


data class ConnectionState(
    val clientConnected: Boolean,
    val serverConnected: Boolean,
    val errorMsg: String?,
    val state: VectorClock
)

class ConnectionManager(val context: Context, val snapshot: Snapshot<ActionWrapper, State>) {
    val serviceInfoState: MutableStateFlow<Map<String, ServiceInfo>> = MutableStateFlow(mapOf())
    val connectionStates: MutableStateFlow<Map<ClientIdentifier, ConnectionState>> = MutableStateFlow(mapOf())
    val name: MutableStateFlow<String?> = MutableStateFlow(null)

    suspend fun run() {
        val httpClient = HttpClient() {
            install(WebSockets)
        }

        serviceInfoState.collect {
            try {
                val newClients = mutableListOf<Pair<String, ClientIdentifier>>()
                for (client in it.filterValues { it.clientId == null }) {
                    Napier.i("check client id of ${client.value.name}")

                    val url = URLBuilder(
                        protocol = URLProtocol.HTTP,
                        host = client.value.connectionInformation.hosts.first(),
                        port = client.value.connectionInformation.port,
                        pathSegments = listOf("client")
                    ).build()

                    val response = httpClient.get(url)
                    if (response.status == HttpStatusCode.OK) {
                        newClients.add(client.key to ClientIdentifier(response.bodyAsText()))
                    }
                }
                if (newClients.isNotEmpty()) {
                    serviceInfoState.update { info ->
                        info + newClients.mapNotNull { client ->
                            info[client.first]?.let {
                                client.first to it.copy(
                                    clientId = client.second
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Napier.e("error in connection manager: $e")
            }
        }
    }

    fun serverInfoUpdate(clientId: ClientIdentifier, state: VectorClock) {
        connectionStates.update {
            val current = it[clientId] ?: ConnectionState(clientConnected = false, serverConnected = false, null, VectorClock.empty())
            val new = current.copy(serverConnected = true, errorMsg = null, state = state)

            it + (clientId to new)
        }
    }

    fun serverInfoConnectionStopped(clientId: ClientIdentifier) {
        connectionStates.update {
            val next = it[clientId]?.copy(serverConnected = false)
            if (next == null) it else it + (clientId to next)
        }
    }

    fun clientInfoUpdate(clientId: ClientIdentifier, state: VectorClock) {
        connectionStates.update {
            val current = it[clientId] ?: ConnectionState(clientConnected = false, serverConnected = false, null, VectorClock.empty())
            val new = current.copy(clientConnected = true, errorMsg = null,state = state)

            it + (clientId to new)
        }
    }

    fun clientInfoConnectionStopped(clientId: ClientIdentifier, errorMsg: String?) {
        connectionStates.update {
            val next = it[clientId]?.copy(clientConnected = false, errorMsg = errorMsg)
            if (next == null) it else it + (clientId to next)
        }
    }

    fun registerService(name: String, port: Int) {
        // Create the NsdServiceInfo object, and populate it.
        val serviceInfo = NsdServiceInfo().apply {
            // The name is subject to change based on conflicts
            // with other services advertised on the same network.
            serviceName = name
            serviceType = "_initiative_tracker._tcp"
            setPort(port)
        }

        val nsdManager = (context.getSystemService(Context.NSD_SERVICE) as NsdManager)
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        nsdManager.discoverServices("_initiative_tracker._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun unregisterService() {
        val nsdManager = (context.getSystemService(Context.NSD_SERVICE) as NsdManager)
        nsdManager.unregisterService(registrationListener)
        nsdManager.stopServiceDiscovery(discoveryListener)
    }

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(p0: NsdServiceInfo) {
            name.update { p0.serviceName }
        }

        override fun onRegistrationFailed(p0: NsdServiceInfo?, p1: Int) {

        }

        override fun onServiceUnregistered(p0: NsdServiceInfo?) {
            name.update { null }
        }

        override fun onUnregistrationFailed(p0: NsdServiceInfo?, p1: Int) {

        }
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(p0: String?) {}

        override fun onDiscoveryStopped(p0: String?) {}

        override fun onServiceFound(service: NsdServiceInfo) {
            when {
                service.serviceType != "_initiative_tracker._tcp." ->
                    Napier.i("Unknown Service Type: ${service.serviceType}")
                else -> {
                    val nsdManager = (context.getSystemService(Context.NSD_SERVICE) as NsdManager)

                    Napier.i("resolving")

                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onServiceResolved(service: NsdServiceInfo) {
                            Napier.i("service resolved: $service")

                            val info = ServiceInfo(
                                name = service.serviceName,
                                clientId = null,
                                connectionInformation = ConnectionInformation(
                                    port = service.port,
                                    hosts = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                                        service.hostAddresses.map { it.toString().drop(1) }
                                    else
                                        listOf(service.host.toString().drop(1))
                                )
                            )

                            serviceInfoState.update {
                                it + (service.serviceName to info)
                            }
                        }

                        override fun onResolveFailed(p0: NsdServiceInfo?, p1: Int) {
                            Napier.i("service resolve failed: $p0, $p1")
                        }
                    })
                }
            }


            Napier.i("service found: $service")
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            serviceInfoState.update { it - service.serviceName }
        }

        override fun onStartDiscoveryFailed(p0: String?, p1: Int) {
        }

        override fun onStopDiscoveryFailed(p0: String?, p1: Int) {
        }
    }


}