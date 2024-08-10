package io.github.potsdam_pnp.initiative_tracker.state

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch


sealed class Message<Op> {
    data class CurrentState<Op>(val vectorClock: VectorClock): Message<Op>()

    data class RequestVersions<Op>(val vectorClock: VectorClock, val versions: List<Version>): Message<Op>()

    data class SendVersions<Op>(val vectorClock: VectorClock, val versions: List<Operation<Op>>): Message<Op>()

    data class StopConnection<Op>(val unit: Unit): Message<Op>()
}

class MessageHandler<Op, State: OperationState<Op>>(private val snapshot: Snapshot<Op, State>) {
    suspend fun run(scope: CoroutineScope, incoming: Channel<Message<Op>>, outgoing: Channel<Message<Op>>) {
        val job = scope.launch {
            snapshot.version.collect {
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
                when (snapshot.insert(message.vectorClock, listOf())) {
                    is InsertResult.MissingVersions -> {
                        return Message.RequestVersions(message.vectorClock, (snapshot.insert(message.vectorClock, listOf()) as InsertResult.MissingVersions).missingVersions)
                    }
                    is InsertResult.Success ->
                        return null
                }
            }

            is Message.SendVersions -> {
                val result = snapshot.insert(message.vectorClock, message.versions)
                Napier.i("received and inserted versions: $result")
                return null
            }

            is Message.RequestVersions -> {
                val versions = message.versions.mapNotNull {
                    snapshot.fetchVersion(it)
                }
                return Message.SendVersions(message.vectorClock, versions)
            }

            is Message.StopConnection -> {
                return Message.StopConnection(Unit)
            }
        }
    }
}