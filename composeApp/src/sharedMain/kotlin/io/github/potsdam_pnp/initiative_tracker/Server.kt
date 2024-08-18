package io.github.potsdam_pnp.initiative_tracker

import io.github.aakira.napier.Napier
import io.github.potsdam_pnp.initiative_tracker.crdt.ClientIdentifier
import io.github.potsdam_pnp.initiative_tracker.state.Encoders
import io.github.potsdam_pnp.initiative_tracker.crdt.Message
import io.github.potsdam_pnp.initiative_tracker.crdt.MessageHandler
import io.github.potsdam_pnp.initiative_tracker.crdt.Repository
import io.github.potsdam_pnp.initiative_tracker.state.State
import io.ktor.http.ContentType
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.origin
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


sealed class ServerState {
    object Stopped : ServerState()
    object Starting : ServerState()
    data class Running(val port: Int, val connectedClients: Int) : ServerState()
    object Stopping : ServerState()

    fun message(): String =
        when (this) {
            is Stopped -> "Server stopped"
            is Starting -> "Server starting"
            is Running -> "Server running on port $port, $connectedClients clients connected"
            is Stopping -> "Server stopping"
        }

    fun isChangeEnabled(): Boolean =
        when (this) {
            is Stopped -> true
            is Starting -> false
            is Running -> true
            is Stopping -> false
        }

    fun isTargetRunning(): Boolean =
        when (this) {
            is Stopped -> false
            is Starting -> true
            is Running -> true
            is Stopping -> false
        }

    fun connectedClients(): Int =
        when (this) {
            is Stopped -> 0
            is Starting -> 0
            is Running -> connectedClients
            is Stopping -> 0
        }
}

private sealed class Actions {
    object Start: Actions()
    object End: Actions()
}

class Server(private val name: String, private val repository: Repository<ActionWrapper, State>, private val connectionManager: ConnectionManager) {
    private val state = MutableStateFlow<ServerState>(ServerState.Stopped)
    private val actions = Channel<Actions>()

    suspend fun runOnce() {
        Napier.i("waiting to start server")
        while (actions.receive() != Actions.Start) {
            Napier.w("Received Stop while server was already stopped")
        }
        state.update { ServerState.Starting }

        Napier.i("starting server")
        val server = startServer()
        server.start(wait = false)
        val resolvedPort = server.engine.resolvedConnectors()[0].port
        connectionManager.registerService(name, resolvedPort)

        state.update { ServerState.Running(resolvedPort, 0) }

        while (actions.receive() != Actions.End) {
            Napier.w("Received Start while server is already running")
        }

        state.update { ServerState.Stopping }

        withContext(Dispatchers.IO) {
            connectionManager.unregisterService()
            server.stop()
        }
    }

    suspend fun run() {
        while (true) {
            runOnce()
        }
    }

    private fun startServer(): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
        return embeddedServer(Netty, port = 8080) {
            install(WebSockets)
            routing {
                get("/") {
                    call.respondText("Initiative Tracker server running succesfully")
                }
                get("/app") {
                    val website = "https://potsdam-pnp.github.io/initiative-tracker"
                    call.respondText(
                        contentType = ContentType.Text.Html,
                        text = "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n"
                                + "    <meta charset=\"UTF-8\">\n    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
                                + "    <title>KotlinProject</title>\n"
                                + "    <link type=\"text/css\" rel=\"stylesheet\" href=\"$website/styles.css\">\n"
                                + "    <script type=\"application/javascript\" src=\"$website/composeApp.js\"></script>\n"
                                + "</head>\n<body>\n</body>\n</html>"
                    )
                }
                get("/composeApp.wasm") {
                    call.respondRedirect("https://potsdam-pnp.github.io/initiative-tracker/composeApp.wasm")
                }
                get("/composeResources/{...}") {
                    val newPath =
                        "https://potsdam-pnp.github.io/initiative-tracker" + call.request.origin.uri
                    call.respondRedirect(newPath)
                }
                get("/client") {
                    call.respondText(repository.clientIdentifier.name)
                }
                webSocket("/ws/{client}") {
                    val clientId = ClientIdentifier(call.parameters["client"].orEmpty())

                    state.update { (it as ServerState.Running).let { it.copy(connectedClients = it.connectedClients + 1) } }

                    try {
                        val receiveChannel = Channel<Message<ActionWrapper>>()
                        val sendChannel = Channel<Message<ActionWrapper>>()

                        launch {
                            state.first { it !is ServerState.Running }
                            receiveChannel.send(Message.StopConnection(Unit))
                        }

                        launch {
                            while (true) {
                                val msg = incoming.receive()
                                val decoded = Encoders.decode((msg as Frame.Text).readText())
                                if (decoded is Message.CurrentState) {
                                    connectionManager.serverInfoUpdate(clientId, decoded.vectorClock)
                                }
                                receiveChannel.send(Encoders.decode((msg as Frame.Text).readText()))
                            }
                        }

                        launch {
                            while (true) {
                                val msg = sendChannel.receive()
                                send(Frame.Text(Encoders.encode(msg)))
                            }
                        }


                        MessageHandler(repository).run(this, receiveChannel, sendChannel)
                    } finally {
                        connectionManager.serverInfoConnectionStopped(clientId)
                        state.update {
                            Napier.w("Updating connections")
                            (it as ServerState.Running).let { it.copy(connectedClients = it.connectedClients - 1) }
                        }
                    }
                }
            }
        }
    }

    fun toggle(to: Boolean) {
        when(to) {
            true -> actions.trySend(Actions.Start)
            false -> actions.trySend(Actions.End)
        }
    }
}
