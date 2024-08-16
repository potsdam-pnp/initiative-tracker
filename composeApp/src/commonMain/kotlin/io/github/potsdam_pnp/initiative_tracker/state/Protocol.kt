package io.github.potsdam_pnp.initiative_tracker.state

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch


sealed class Message<Op> {
    data class CurrentState<Op>(val vectorClock: VectorClock): Message<Op>()

    data class RequestVersions<Op>(val vectorClock: VectorClock, val dots: List<Dot>): Message<Op>()

    data class SendVersions<Op>(val vectorClock: VectorClock, val versions: List<Operation<Op>>): Message<Op>()

    data class StopConnection<Op>(val unit: Unit): Message<Op>()
}

class MessageHandler<Op, State: OperationState<Op>>(private val repository: Repository<Op, State>) {
    suspend fun run(scope: CoroutineScope, incoming: Channel<Message<Op>>, outgoing: Channel<Message<Op>>) {
        val job = scope.launch {
            repository.version.collect {
                outgoing.send(Message.CurrentState(it))
            }
        }

        while (true) {
            val answer = handleMessage(incoming.receive())
            if (answer is Message.StopConnection) {
                job.cancelAndJoin()
                break
            }
            if (answer != null) {
                outgoing.send(answer)
            }
        }
    }

    private fun handleMessage(message: Message<Op>): Message<Op>? {
        when (message) {
            is Message.CurrentState -> {
                when (repository.insert(message.vectorClock, listOf())) {
                    is InsertResult.MissingVersions -> {
                        return Message.RequestVersions(message.vectorClock, (repository.insert(message.vectorClock, listOf()) as InsertResult.MissingVersions).missingDots)
                    }
                    is InsertResult.Success ->
                        return null
                }
            }

            is Message.SendVersions -> {
                val result = repository.insert(message.vectorClock, message.versions)
                Napier.i("received and inserted versions: $result")
                return null
            }

            is Message.RequestVersions -> {
                val versions = message.dots.mapNotNull {
                    repository.fetchVersion(it)
                }
                return Message.SendVersions(message.vectorClock, versions)
            }

            is Message.StopConnection -> {
                return Message.StopConnection(Unit)
            }
        }
    }
}