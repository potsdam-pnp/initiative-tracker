import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.utils.io.core.use
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

object ClientConsumer {
    private val _clientStatus = MutableStateFlow(ClientStatus())
    val clientStatus: StateFlow<ClientStatus> = _clientStatus

    var httpClient: HttpClient? = null

    var job: Job? = null

    fun toggleClient(model: Model, coroutineScope: CoroutineScope) {
        when (clientStatus.value.status) {
            is ClientStatusState.Stopped -> start(model, coroutineScope)
            is ClientStatusState.Running -> stop()
            is ClientStatusState.Starting -> {}
            is ClientStatusState.ConnectionError -> start(model, coroutineScope)
        }
    }

    fun changeHost(host: String) {
        _clientStatus.update {
            it.copy(host = host)
        }
    }

    fun start(model: Model, coroutineScope: kotlinx.coroutines.CoroutineScope) {
        _clientStatus.update {
            val result = it.copy(status = ClientStatusState.Starting)
            Napier.i("Starting client - previous: $it next: $result")
            result
        }
        if (httpClient == null) {
            httpClient = HttpClient() {
                install(WebSockets)
            }
        }
        httpClient!!.also { client ->
            val previousJob = job
            job = coroutineScope.launch {
                try {
                    previousJob?.join()
                    client.webSocket(
                        method = HttpMethod.Get,
                        host = clientStatus.value.host,
                        port = 8080,
                        path = "/ws"
                    ) {
                        _clientStatus.update {
                            it.copy(status = ClientStatusState.Running(0, 0))
                        }

                        launch {
                            var alreadyDroppedFirstMessage = false
                            model.state.collect {
                                if (!alreadyDroppedFirstMessage) {
                                    alreadyDroppedFirstMessage = true
                                } else {
                                    if (sendUpdates.value) {
                                        //send(Frame.Text(serializeActions(it.actions)))
                                    }
                                }
                            }
                        }

                        while (true) {
                            val nextFrame = async { incoming.receive() }

                            val frame = select<Frame?> {
                                closeReason.onAwait { null }
                                nextFrame.onAwait { it }
                            }

                            if (frame == null) {
                                break
                            }

                            val othersMessage = frame as? Frame.Text
                            if (othersMessage != null) {
                                val actions = deserializeActions(othersMessage.readText())
                                if (actions != null) {
                                    if (receiveUpdates.value) {
                                        model.receiveActions(actions)
                                    }
                                    _clientStatus.update {
                                        it.copy(status = it.status.receivedSuccesfulFrame())
                                    }
                                } else {
                                    _clientStatus.update {
                                        it.copy(status = it.status.receivedFailedFrame())
                                    }
                                }
                            }
                        }

                        val stopMessage = closeReason.await().let {
                            when (it) {
                                null -> "Connection terminated"
                                else -> "Connection terminated with reason: $it"
                            }
                        }

                        _clientStatus.update {
                            it.copy(status = ClientStatusState.ConnectionError(stopMessage))
                        }
                    }
                } catch (e: CancellationException) {
                    _clientStatus.update {
                        val result = it.copy(status = ClientStatusState.Stopped)
                        Napier.i("Update stopped: previous $it next $result")
                        result
                    }
                } catch (e: Exception) {
                    _clientStatus.update {
                        it.copy(
                            status = ClientStatusState.ConnectionError("Error: $e")
                        )
                    }
                }
            }
        }
    }

    fun stop() {
        httpClient?.close()
        httpClient = null
        job?.cancel()
    }

}

sealed class ClientStatusState {
    open fun receivedSuccesfulFrame() = this
    open fun receivedFailedFrame() = this

    object Stopped : ClientStatusState()
    object Starting : ClientStatusState()
    data class Running(
        val receivedSuccesfulFrames: Int = 0,
        val receivedFailedFrames: Int = 0) : ClientStatusState() {
            override fun receivedSuccesfulFrame() = copy(receivedSuccesfulFrames = receivedSuccesfulFrames + 1)
            override fun receivedFailedFrame() = copy(receivedFailedFrames = receivedFailedFrames + 1)
    }
    data class ConnectionError(val errorMessage: String) : ClientStatusState()
}

data class ClientStatus(
    val status: ClientStatusState = ClientStatusState.Stopped,
    val host: String = "127.0.0.1"
)

val sendUpdates = MutableStateFlow<Boolean>(true)
val receiveUpdates = MutableStateFlow<Boolean>(true)