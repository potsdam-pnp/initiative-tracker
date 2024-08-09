package io.github.potsdam_pnp.initiative_tracker

import JoinLink
import Model
import ServerStatus
import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import deserializeActions
import io.github.aakira.napier.Napier
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import receiveUpdates
import sendUpdates
import serializeActions
import java.net.Inet4Address
import java.net.Inet6Address
import java.util.Formatter
import kotlin.concurrent.thread

sealed class ServerState {
    object Stopped: ServerState()
    object Starting: ServerState()
    data class Running(val port: Int, val connectedClients: Int, val joinLinks: List<JoinLink>): ServerState()
    object Stopping: ServerState()

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

class Server(val context: Context, val model: Model) {
    private val state = MutableStateFlow<ServerState>(ServerState.Stopped)
    private val actions = Channel<Actions>()

    private val f = { state: ServerState ->
        ServerStatus(
            isRunning = state.isTargetRunning(),
            message = state.message(),
            isSupported = state.isChangeEnabled(),
            connections = state.connectedClients()
        )
    }

    fun serverState() = f(state.value)

    val serverState: androidx.compose.runtime.State<ServerStatus> @Composable get() {
        return state.map(f).collectAsState(f(state.value))
    }


    suspend fun run(coroutineScope: CoroutineScope) {
        while (true) {
            while (actions.receive() != Actions.Start) {
                Napier.w("Received Stop while server was already stopped")
            }
            state.update { ServerState.Starting }

            val server = startServer()
            server.start(wait = false)
            val resolvedPort = server.engine.resolvedConnectors()[0].port
            val joinLinks = listenAddresses()
            registerService(resolvedPort)

            state.update { ServerState.Running(resolvedPort, 0, joinLinks) }

            while (actions.receive() != Actions.End) {
                Napier.w("Received Start while server is already running")
            }

            state.update { ServerState.Stopping }

            withContext(Dispatchers.IO) {
                unregisterService()
                server.stop()
            }

            state.update { ServerState.Stopped }
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
                webSocket("/ws") {
                    val job = launch {
                        model.state.collect {
                            if (sendUpdates.value) {
                                send(Frame.Text(serializeActions(it.actions)))
                            }
                        }
                    }

                    state.update { (it as ServerState.Running).let { it.copy(connectedClients = it.connectedClients + 1) } }

                    try {
                        val finish = launch {
                            state.first { it !is ServerState.Running }
                        }

                        while (true) {
                            Napier.i("receiving next message")
                            val nextFrame = async { incoming.receive() }

                            val frame = select<Frame?> {
                                finish.onJoin { null }
                                nextFrame.onAwait { it }
                            }
                            Napier.i("received ${frame == null}")

                            if (frame == null) {
                                break
                            } else {
                                val frameText = (frame as? Frame.Text)?.readText()
                                if (frameText != null) {
                                    val actions = deserializeActions(frameText)
                                    if (actions != null) {
                                        if (receiveUpdates.value) {
                                            model.receiveActions(actions)
                                        }
                                    }
                                }
                            }
                        }

                        Napier.w("Closing connection")

                        job.cancelAndJoin()
                    } finally {
                        state.update {
                            Napier.w("Updating connections")
                            (it as ServerState.Running).let { it.copy(connectedClients = it.connectedClients - 1) }
                        }
                    }
                }
            }
        }
    }

    private fun listenAddresses(): List<JoinLink> {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val currentNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(currentNetwork)

        val linkProperties = connectivityManager.getLinkProperties(currentNetwork)
        val ipAddress = linkProperties?.linkAddresses?.mapNotNull { it.address }.orEmpty()

        val addressStrings = ipAddress.mapNotNull {
            // Convert IP address to a readable string (if needed)
            if (it is Inet4Address) {
                Formatter().format(
                    "%d.%d.%d.%d",
                    it.address[0].toInt() and 0xff,
                    it.address[1].toInt() and 0xff,
                    it.address[2].toInt() and 0xff,
                    it.address[3].toInt() and 0xff
                ).toString()
            } else if (it is Inet6Address) {
                val result = "[${it.toString().substring(1)}]"
                if (result.startsWith("[fe80")) {
                    // Link-local addresses aren't usable
                    null
                } else {
                    result
                }
            } else {
                it.toString()
            }
        }

        return addressStrings.map { JoinLink(it) }
    }

    fun toggle(to: Boolean) {
        when(to) {
            true -> actions.trySend(Actions.Start)
            false -> actions.trySend(Actions.End)
        }
    }

    var serviceName: String? = null

    private fun registerService(port: Int) {
        // Create the NsdServiceInfo object, and populate it.
        val serviceInfo = NsdServiceInfo().apply {
            // The name is subject to change based on conflicts
            // with other services advertised on the same network.
            serviceName = "No Name"
            serviceType = "_initiative_tracker._tcp"
            setPort(port)
        }

        val nsdManager = (context.getSystemService(Context.NSD_SERVICE) as NsdManager)
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        nsdManager.discoverServices("_initiative_tracker._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private fun unregisterService() {
        val nsdManager = (context.getSystemService(Context.NSD_SERVICE) as NsdManager)
        nsdManager.unregisterService(registrationListener)
        nsdManager.stopServiceDiscovery(discoveryListener)
    }

    private val registrationListener = object : NsdManager.RegistrationListener {

        override fun onServiceRegistered(p0: NsdServiceInfo?) {
            serviceName = p0?.serviceName
        }

        override fun onRegistrationFailed(p0: NsdServiceInfo?, p1: Int) {

        }

        override fun onServiceUnregistered(p0: NsdServiceInfo?) {
            serviceName = null
        }

        override fun onUnregistrationFailed(p0: NsdServiceInfo?, p1: Int) {

        }
    }


    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(p0: String?) {

        }

        override fun onDiscoveryStopped(p0: String?) {

        }

        override fun onServiceFound(service: NsdServiceInfo) {
            when {
                service.serviceType != "_initiative_tracker._tcp." -> // Service type is the string containing the protocol and
                    // transport layer for this service.
                    Napier.i("Unknown Service Type: ${service.serviceType}")
                //service.serviceName == mServiceName -> // The name of the service tells the user what they'd be
                //    // connecting to. It could be "Bob's Chat App".
                //    Log.d(TAG, "Same machine: $mServiceName")
                else -> {
                    val nsdManager = (context.getSystemService(Context.NSD_SERVICE) as NsdManager)

                    Napier.i("resolving")

                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onServiceResolved(p0: NsdServiceInfo?) {
                            Napier.i("service resolved: $p0")
                        }

                        override fun onResolveFailed(p0: NsdServiceInfo?, p1: Int) {
                            Napier.i("service resolve failed: $p0, $p1")
                            p0.toString()
                        }
                    })
                }
            }


            Napier.i("service found: $service")
        }

        override fun onServiceLost(p0: NsdServiceInfo?) {
        }

        override fun onStartDiscoveryFailed(p0: String?, p1: Int) {
        }

        override fun onStopDiscoveryFailed(p0: String?, p1: Int) {
        }
    }
}