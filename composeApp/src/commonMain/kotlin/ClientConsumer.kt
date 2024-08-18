import io.github.aakira.napier.Napier
import io.github.potsdam_pnp.initiative_tracker.Action
import io.github.potsdam_pnp.initiative_tracker.Encoders
import io.github.potsdam_pnp.initiative_tracker.crdt.Message
import io.github.potsdam_pnp.initiative_tracker.crdt.MessageHandler
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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
                        path = "/ws/${model.repository.clientIdentifier.name}"
                    ) {
                        _clientStatus.update {
                            it.copy(status = ClientStatusState.Running(0, 0))
                        }


                        val receiveChannel = Channel<Message<Action>>()
                        val sendChannel = Channel<Message<Action>>()

                        launch {
                            closeReason.await()
                            receiveChannel.send(Message.StopConnection(Unit))
                        }

                        launch {
                            while (true) {
                                val msg = incoming.receive()
                                receiveChannel.send(Encoders.decode((msg as Frame.Text).readText()))
                            }
                        }

                        launch {
                            while (true) {
                                val msg = sendChannel.receive()
                                send(Frame.Text(Encoders.encode(msg)))
                            }
                        }


                        MessageHandler(model.repository).run(this, receiveChannel, sendChannel)

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
