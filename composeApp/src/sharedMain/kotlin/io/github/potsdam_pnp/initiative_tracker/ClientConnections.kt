package io.github.potsdam_pnp.initiative_tracker

import io.github.potsdam_pnp.initiative_tracker.crdt.ClientIdentifier
import io.github.potsdam_pnp.initiative_tracker.state.Encoders
import io.github.potsdam_pnp.initiative_tracker.crdt.Message
import io.github.potsdam_pnp.initiative_tracker.crdt.MessageHandler
import io.github.potsdam_pnp.initiative_tracker.crdt.Repository
import io.github.potsdam_pnp.initiative_tracker.state.State
import io.github.potsdam_pnp.initiative_tracker.crdt.VectorClock
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ClientConnections(val repository: Repository<ActionWrapper, State>, val connectionManager: ConnectionManager) {
    suspend fun run(scope: CoroutineScope) {
        val httpClient = HttpClient() {
            install(WebSockets)
        }

        val f = { connections: Map<ClientIdentifier, ConnectionState>, services: Map<String, ServiceInfo> ->
            services.mapValues { entry ->
                entry.value to connections[entry.value.clientId]
            }.filterValues { it.first.clientId != null && it.second?.let { !it.clientConnected && !it.serverConnected } != false }
        }

        connectionManager.connectionStates.combine(connectionManager.serviceInfoState, f).collect {
            for (connection in it) {
                val connectionInformation = connection.value.first.connectionInformation
                val clientIdentifier = connection.value.first.clientId!!
                connectionManager.clientInfoUpdate(clientIdentifier, VectorClock.empty())
                scope.launch {
                    var errorMsg: String? = null
                    try {
                        httpClient.webSocket(
                            method = HttpMethod.Get,
                            host = connectionInformation.hosts.first(),
                            port = connectionInformation.port,
                            path = "/ws/${repository.clientIdentifier.name}"
                        ) {
                            val receiveChannel = Channel<Message<ActionWrapper>>()
                            val sendChannel = Channel<Message<ActionWrapper>>()

                            launch {
                                closeReason.await()
                                receiveChannel.send(Message.StopConnection(Unit))
                            }

                            launch {
                                while (true) {
                                    val msg = incoming.receive()
                                    val decoded = Encoders.decode((msg as Frame.Text).readText())
                                    if (decoded is Message.CurrentState) {
                                        connectionManager.clientInfoUpdate(
                                            clientIdentifier,
                                            decoded.vectorClock
                                        )
                                    }
                                    receiveChannel.send(decoded)
                                }
                            }

                            launch {
                                while (true) {
                                    val msg = sendChannel.receive()
                                    send(Frame.Text(Encoders.encode(msg)))
                                }
                            }

                            MessageHandler(repository).run(this, receiveChannel, sendChannel)
                        }
                    } catch (e: Exception) {
                        errorMsg = e.toString()
                    } finally {
                        connectionManager.clientInfoConnectionStopped(clientIdentifier, errorMsg)
                    }
                }
            }
            delay(4000)
        }
    }
}