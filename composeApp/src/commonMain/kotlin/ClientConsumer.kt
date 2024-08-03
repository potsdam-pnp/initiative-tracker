import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.utils.io.core.use
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object ClientConsumer {
    private val _clientStatus = MutableStateFlow(ClientStatus())
    val clientStatus: StateFlow<ClientStatus> = _clientStatus

    var httpClient: HttpClient? = null

    var job: Job? = null

    fun toggleClient(model: Model, coroutineScope: CoroutineScope) {
        if (clientStatus.value.isRunning) {
            stop()
        } else {
            start(model, coroutineScope)
        }
    }

    fun changeHost(host: String) {
        _clientStatus.update {
            it.copy(host = host)
        }
    }

    fun start(model: Model, coroutineScope: kotlinx.coroutines.CoroutineScope) {
        _clientStatus.update {
            it.copy(isRunning = true, message = "Starting")
        }
        if (httpClient == null) {
            httpClient = HttpClient() {
                install(WebSockets)
            }
        }
        httpClient!!.also { client ->
            job = coroutineScope.launch {
                try {
                    client.webSocket(
                        method = HttpMethod.Get,
                        host = clientStatus.value.host,
                        port = 8080,
                        path = "/ws"
                    ) {
                        while (true) {
                            val othersMessage = incoming.receive() as? Frame.Text
                            if (othersMessage != null) {
                                val actions = deserializeActions(othersMessage.readText())
                                if (actions != null) {
                                    model.receiveActions(actions)
                                    _clientStatus.update {
                                        it.copy(
                                            receivedSuccesfulFrames = it.receivedSuccesfulFrames + 1
                                        ).let {
                                            it.copy(message = runningMessage(it))
                                        }
                                    }
                                } else {
                                    _clientStatus.update {
                                        it.copy(
                                            receivedFailedFrames = it.receivedFailedFrames + 1
                                        ).let {
                                            it.copy(message = runningMessage(it))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch(e: Exception) {
                    _clientStatus.update {
                        it.copy(
                            isRunning = false,
                            message = "Error: $e (${it.receivedSuccesfulFrames} states received so far, ${it.receivedFailedFrames} failed transmissions)"
                        )
                    }
                }
            }
        }

    }

    private fun runningMessage(clientStatus: ClientStatus): String {
        return "Running - ${clientStatus.receivedSuccesfulFrames} states received so far, ${clientStatus.receivedFailedFrames} failed transmissions"
    }

    fun stop() {
        httpClient?.close()
        httpClient = null
        job?.cancel()
        _clientStatus.update {
            it.copy(isRunning = false, message = "Stopping")
        }
    }

}

data class ClientStatus(
    val isRunning: Boolean = false,
    val message: String = "Not running",
    val host: String = "127.0.0.1",
    val receivedSuccesfulFrames: Int = 0,
    val receivedFailedFrames: Int = 0)