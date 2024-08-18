package io.github.potsdam_pnp.initiative_tracker

import DiscoveredClient
import JoinLink
import ServerStatus
import io.github.aakira.napier.Napier
import io.github.potsdam_pnp.initiative_tracker.crdt.ClientIdentifier
import io.github.potsdam_pnp.initiative_tracker.crdt.VectorClock
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import kotlinx.coroutines.delay
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


abstract class ConnectionManager() {
    val serviceInfoState: MutableStateFlow<Map<String, ServiceInfo>> = MutableStateFlow(mapOf())
    val connectionStates: MutableStateFlow<Map<ClientIdentifier, ConnectionState>> = MutableStateFlow(mapOf())
    val name: MutableStateFlow<String?> = MutableStateFlow(null)

    suspend fun run() {
        val httpClient = HttpClient() {
            install(WebSockets)
        }

        serviceInfoState.collect {
            val newClients = mutableListOf<Pair<String, ClientIdentifier>>()
            for (client in it.filterValues { it.clientId == null }) {
                try {
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
                } catch (e: Exception) {
                    Napier.e("error in connection manager: $e")
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
                delay(4000)
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

    abstract fun registerService(name: String, resolvedPort: Int)
    abstract fun unregisterService()
}

fun toServerStatus(connectionStates: Map<ClientIdentifier, ConnectionState>, serviceInfoStates: Map<String, ServiceInfo>, name: String?): ServerStatus {
    val otherCorresponding =
        connectionStates.filterKeys { key -> serviceInfoStates.all { it.value.clientId != key } }
    return ServerStatus(
        isRunning = name != null,
        message = if (name == null) "Not connected" else "Running as '$name'",
        isSupported = false,
        connections = connectionStates.filterValues { it.serverConnected || it.clientConnected }.size,
        joinLinks = name?.let { serviceInfoStates[it] }?.connectionInformation?.let { it.hosts.map { JoinLink(it) } } ?: emptyList(),
        discoveredClients = serviceInfoStates.map {
            val corresponding = connectionStates[it.value.clientId]
            DiscoveredClient(
                name = it.key + " (${it.value.clientId?.name})",
                port = it.value.connectionInformation.port,
                hosts = it.value.connectionInformation.hosts,
                state = corresponding?.state,
                isServerConnected = corresponding?.serverConnected == true,
                isClientConnected = corresponding?.clientConnected == true,
                errorMsg = corresponding?.errorMsg
            )
        } + otherCorresponding.map {
            DiscoveredClient(
                name = "(${it.key.name})",
                port = null,
                hosts = null,
                isServerConnected = it.value.serverConnected,
                isClientConnected = it.value.clientConnected,
                state = it.value.state,
                errorMsg = it.value.errorMsg
            )
        }
    )
}