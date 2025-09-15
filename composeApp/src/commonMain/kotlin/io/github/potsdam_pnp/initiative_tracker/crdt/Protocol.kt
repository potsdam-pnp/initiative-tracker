package io.github.potsdam_pnp.initiative_tracker.crdt

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

sealed class Message<Op> {
  data class CurrentState<Op>(
    val vectorClock: VectorClock,
    val clientIdentifier: ClientIdentifier,
  ) : Message<Op>()

  data class RequestVersions<Op>(
    val vectorClock: VectorClock,
    val fromVectorClock: VectorClock,
    val msgIdentifier: Int? = null,
    val maxMessageSize: Int? = null,
  ) : Message<Op>()

  data class SendVersions<Op>(
    val vectorClock: VectorClock,
    val versions: List<Operation<Op>>,
    val msgIdentifier: Int? = null,
    val clientIdentifier: ClientIdentifier,
  ) : Message<Op>()

  data class StopConnection<Op>(val unit: Unit) : Message<Op>()

  data class Heartbeat<Op>(val id: Int) : Message<Op>()
}

class MessageHandler<Op, State : AbstractState<Op>>(private val repository: Repository<Op, State>) {
  suspend fun run(
    scope: CoroutineScope,
    incoming: Channel<Message<Op>>,
    outgoing: Channel<Message<Op>>,
  ) {
    val job =
      scope.launch {
        repository.version.collect {
          outgoing.send(Message.CurrentState(it, repository.clientIdentifier))
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
        val vc = repository.version.value
        if (!vc.contains(message.vectorClock)) {
          return Message.RequestVersions(message.vectorClock, vc)
        } else {
          return null
        }
      }

      is Message.SendVersions -> {
        val result = repository.insert(message.vectorClock, message.versions)
        Napier.i("received and inserted versions: $result")
        return null
      }

      is Message.RequestVersions -> {
        val versions =
          message.vectorClock.versionsNotIn(message.fromVectorClock).mapNotNull {
            repository.fetchVersion(it)
          }
        return Message.SendVersions(
          message.vectorClock,
          versions,
          null,
          repository.clientIdentifier,
        )
      }

      is Message.StopConnection -> {
        return Message.StopConnection(Unit)
      }

      is Message.Heartbeat -> {
        return null
      }
    }
  }
}
