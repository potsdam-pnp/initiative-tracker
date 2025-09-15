package io.github.potsdam_pnp.initiative_tracker

import io.github.aakira.napier.Napier
import io.github.potsdam_pnp.initiative_tracker.crdt.ClientIdentifier
import io.github.potsdam_pnp.initiative_tracker.crdt.Dot
import io.github.potsdam_pnp.initiative_tracker.crdt.GrowingListItem
import io.github.potsdam_pnp.initiative_tracker.crdt.Message
import io.github.potsdam_pnp.initiative_tracker.crdt.Operation
import io.github.potsdam_pnp.initiative_tracker.crdt.OperationMetadata
import io.github.potsdam_pnp.initiative_tracker.crdt.StringOperation
import io.github.potsdam_pnp.initiative_tracker.crdt.VectorClock
import io.github.potsdam_pnp.initiative_tracker.proto.Message as ProtoMessage
import io.github.potsdam_pnp.initiative_tracker.proto.MessageKind
import pbandk.decodeFromByteArray
import pbandk.encodeToByteArray

sealed class Action()

sealed class TurnAction {
  data class StartTurn(val characterId: CharacterId) : TurnAction()

  data class FinishTurn(val characterId: CharacterId) : TurnAction()

  data class Die(val characterId: CharacterId) : TurnAction()

  data class Delay(val characterId: CharacterId) : TurnAction()

  object ResolveConflicts : TurnAction()
}

expect fun highestOneBit(value: Int): Int

data class Turn(val turnAction: TurnAction, override val predecessor: Dot?) :
  Action(), GrowingListItem<TurnAction> {
  override val item: TurnAction
    get() = turnAction
}

data class CharacterId(val dot: Dot)

data object AddCharacter : Action()

data class ChangeName(val operation: StringOperation) : Action()

data class ChangeInitiative(val id: CharacterId, val initiative: Int) : Action()

data class ChangePlayerCharacter(val id: CharacterId, val playerCharacter: Boolean) : Action()

data class DeleteCharacter(val id: CharacterId) : Action()

object ResetAllInitiatives : Action()

object Encoders {
  private fun convertMessage(msg: Message<Action>): ProtoMessage {
    return when (msg) {
      is Message.CurrentState -> {
        val clientIdentifiers = msg.vectorClock.clock.keys.toList()
        ProtoMessage(
          messageKind = MessageKind.CURRENT_STATE,
          clientIdentifiers = clientIdentifiers.map { it.encodeToProto() },
          clock = clientIdentifiers.map { msg.vectorClock.clock[it]?.toLong() ?: 0 },
          clientIdentifier = msg.clientIdentifier.encodeToProto(),
        )
      }

      is Message.RequestVersions -> {
        val clientIdentifiers = msg.vectorClock.clock.keys.toList()
        ProtoMessage(
          messageKind = MessageKind.REQUEST_VERSIONS,
          clientIdentifiers = clientIdentifiers.map { it.encodeToProto() },
          clock = clientIdentifiers.map { msg.vectorClock.clock[it]?.toLong() ?: 0 },
          requestClock = clientIdentifiers.map { msg.fromVectorClock.clock[it] ?: 0 },
          messageIdentifier = msg.msgIdentifier,
          maxMessageLength = msg.maxMessageSize,
        )
      }
      is Message.SendVersions -> {
        val clientIdentifiers = msg.vectorClock.clock.keys.toList()

        val actions = buildList {
          msg.versions.forEach { encodeOperation(clientIdentifiers, it, this) }
        }

        ProtoMessage(
          messageKind = MessageKind.SEND_VERSIONS,
          clientIdentifiers = clientIdentifiers.map { it.encodeToProto() },
          clock = clientIdentifiers.map { msg.vectorClock.clock[it]?.toLong() ?: 0 },
          actions = actions,
          messageIdentifier = msg.msgIdentifier,
          clientIdentifier = msg.clientIdentifier.encodeToProto(),
        )
      }
      is Message.StopConnection -> ProtoMessage(messageKind = MessageKind.STOP_CONNECTION)
      is Message.Heartbeat ->
        ProtoMessage(messageKind = MessageKind.HEARTBEAT, messageIdentifier = msg.id)
    }
  }

  fun encodePb(msg: Message<Action>): ByteArray {
    return convertMessage(msg).encodeToByteArray()
  }

  private fun calculateSize(i: Int): Int {
    var result = 1
    var ii = i
    while (ii >= 64) {
      ii /= 64
      result += 1
    }
    return result
  }

  @OptIn(ExperimentalStdlibApi::class)
  fun encodeSendVersionsMaxSize(
    clientIdentifier: ClientIdentifier,
    maxSize: Int,
    from: VectorClock,
    to: VectorClock,
    messageIdentifier: Int?,
    fetchVersion: (Dot) -> Operation<Action>,
  ): ByteArray {
    val initial =
      encodePb(Message.SendVersions(to.merge(from), listOf(), messageIdentifier, clientIdentifier))
    var size = initial.size + 3
    val clients = to.clock.keys.toList()
    val values = mutableListOf<Int>()
    var index: Int = 0
    var dot: Dot? = null

    val sendVector = from.clock.toMutableMap()
    val iterator = to.dotsNotIn(from)
    var prevSize = size

    while (size < maxSize && iterator.hasNext()) {
      prevSize = size
      dot?.also { sendVector[it.clientIdentifier] = it.position }
      index = values.size
      dot = iterator.next()
      val op = fetchVersion(dot)
      encodeOperation(clients, op, values)
      (index until values.size).forEach { size += calculateSize(values[it]) }
    }
    if (size >= maxSize) {
      values.dropLast(values.size - index)
    } else {
      dot?.also { sendVector[it.clientIdentifier] = it.position }
    }

    val result =
      ProtoMessage(
          messageKind = MessageKind.SEND_VERSIONS,
          clientIdentifiers = clients.map { it.encodeToProto() },
          clock = clients.map { sendVector[it]?.toLong() ?: 0 },
          actions = values,
          messageIdentifier = messageIdentifier,
          clientIdentifier = clientIdentifier.encodeToProto(),
        )
        .encodeToByteArray()
    if (result.size > prevSize) {
      Napier.i(
        "Message size limit ${maxSize}, but total size ${result.size} (calculated ${prevSize} for ${values.size} values)" +
          "\nbytes: ${result.toHexString()},\nvalues: ${values.joinToString { it.toHexString() }}\n" +
          "initial: ${initial.toHexString()}, initial size: ${initial.size}"
      )
    }
    check(result.size <= maxSize) {
      "Message size limit ${maxSize}, but total size ${result.size} (calculated ${prevSize} for ${values.size} values)" +
        "\nbytes: ${result.toHexString()},\nvalues: ${values.joinToString { ", " }} "
    }
    return result
  }

  fun decodePb(msg: ByteArray): Message<Action> {
    val pb = ProtoMessage.decodeFromByteArray(msg)
    fun asClock(clock: List<Long>): VectorClock {
      val result =
        pb.clientIdentifiers.mapIndexed { index, value ->
          ClientIdentifier.decodeFromProto(value) to clock[index].toInt()
        }
      return VectorClock(result.toMap())
    }
    return when (pb.messageKind) {
      MessageKind.CURRENT_STATE ->
        Message.CurrentState(
          asClock(pb.clock),
          ClientIdentifier.decodeFromProto(pb.clientIdentifier!!),
        )
      MessageKind.REQUEST_VERSIONS ->
        Message.RequestVersions(
          asClock(pb.clock),
          asClock(pb.requestClock.map { it.toLong() }),
          msgIdentifier = pb.messageIdentifier,
          maxMessageSize = pb.maxMessageLength,
        )
      MessageKind.SEND_VERSIONS ->
        Message.SendVersions(
          asClock(pb.clock),
          decodeOperations(pb.clientIdentifiers, pb.actions),
          pb.messageIdentifier,
          ClientIdentifier.decodeFromProto(pb.clientIdentifier!!),
        )
      MessageKind.STOP_CONNECTION -> Message.StopConnection(Unit)
      MessageKind.SEND_VERSIONS_PARTIAL -> Message.StopConnection(Unit)
      is MessageKind.UNRECOGNIZED -> Message.StopConnection(Unit)
      MessageKind.HEARTBEAT -> Message.Heartbeat(pb.messageIdentifier!!)
    }
  }

  private fun encodeOperation(
    clients: List<ClientIdentifier>,
    op: Operation<Action>,
    into: MutableList<Int>,
  ) {
    clients.forEach { into.add(op.metadata.clock.clock[it] ?: 0) }

    val shift = highestOneBit(clients.size)

    fun addCombine(client: ClientIdentifier, value: Int) {
      into.add(clients.indexOf(client) or (value shl shift))
    }

    fun encodeOperation(index: Int, characterId: CharacterId? = null) {
      addCombine(op.metadata.client, index)
      if (characterId != null) {
        addCombine(characterId.dot.clientIdentifier, characterId.dot.position)
      }
    }

    fun encodeTurnNoCharacter(index: Int, predecessor: Dot?) {
      addCombine(op.metadata.client, index)
      if (predecessor != null) {
        addCombine(predecessor.clientIdentifier, predecessor.position)
      } else {
        into.add(0)
      }
    }

    fun encodeTurn(index: Int, predecessor: Dot?, characterId: CharacterId) {
      encodeTurnNoCharacter(index, predecessor)
      addCombine(characterId.dot.clientIdentifier, characterId.dot.position)
    }

    when (op.op) {
      is AddCharacter -> encodeOperation(0)
      is ChangeInitiative -> {
        encodeOperation(1, op.op.id)
        into.add(op.op.initiative)
      }
      is ChangeName ->
        when (op.op.operation) {
          is StringOperation.Delete -> {
            encodeOperation(2)
            addCombine(op.op.operation.dot.clientIdentifier, op.op.operation.dot.position)
          }

          is StringOperation.InsertAfter -> {
            encodeOperation(3)
            addCombine(op.op.operation.after.clientIdentifier, op.op.operation.after.position)
            into.add(op.op.operation.character.code)
          }
        }
      is ChangePlayerCharacter -> encodeOperation(if (op.op.playerCharacter) 4 else 5, op.op.id)
      is DeleteCharacter -> encodeOperation(6, op.op.id)
      ResetAllInitiatives -> encodeOperation(7)
      is Turn ->
        when (op.op.turnAction) {
          is TurnAction.Delay -> encodeTurn(8, op.op.predecessor, op.op.turnAction.characterId)
          is TurnAction.Die -> encodeTurn(9, op.op.predecessor, op.op.turnAction.characterId)
          is TurnAction.FinishTurn ->
            encodeTurn(10, op.op.predecessor, op.op.turnAction.characterId)
          TurnAction.ResolveConflicts -> encodeTurnNoCharacter(11, op.op.predecessor)
          is TurnAction.StartTurn -> encodeTurn(12, op.op.predecessor, op.op.turnAction.characterId)
        }
    }
  }

  private fun decodeOperations(clients: List<Int>, actions: List<Int>): List<Operation<Action>> {
    if (clients.isEmpty()) {
      check(actions.isEmpty())
      return emptyList()
    }
    val shift = highestOneBit(clients.size)
    val lowerBits = (1 shl shift) - 1
    check(((clients.size - 1) and lowerBits) == (clients.size - 1))
    var index = 0

    fun decodeClock(): VectorClock {
      return buildMap {
          clients.forEach {
            val key = ClientIdentifier.decodeFromProto(it)
            val value = actions[index]
            put(key, value)
            index += 1
          }
        }
        .let { VectorClock(it) }
    }

    fun decodeCombined(): Pair<ClientIdentifier, Int> {
      val value = actions[index]
      index += 1
      val clientIdentifier = ClientIdentifier.decodeFromProto(clients[value and lowerBits])
      return Pair(clientIdentifier, value shr shift)
    }

    fun decodeDot(): Dot? {
      val (clientIdentifier, position) = decodeCombined()
      return if (position == 0) {
        null
      } else {
        Dot(clientIdentifier, position)
      }
    }

    fun decodeCharacterId(): CharacterId {
      return CharacterId(decodeDot()!!)
    }

    fun decodeTurn(turn: (CharacterId) -> TurnAction): Action {
      val predecessor = decodeDot()
      val characterId = decodeCharacterId()
      return Turn(turn(characterId), predecessor)
    }

    fun decodeTurnNoCharacter(turn: () -> TurnAction): Action {
      val predecessor = decodeDot()
      return Turn(turn(), predecessor)
    }

    return buildList<Operation<Action>> {
      while (index < actions.size) {
        val vectorClock = decodeClock()
        val (client, command) = decodeCombined()
        val metadata = OperationMetadata(vectorClock, client)

        fun insert(action: Action) {
          add(Operation(metadata, action))
        }

        when (command) {
          0 -> insert(AddCharacter)
          1 -> {
            val characterId = decodeCharacterId()
            insert(ChangeInitiative(characterId, actions[index]))
            index += 1
          }
          2 -> {
            val dot = decodeDot()!!
            insert(ChangeName(StringOperation.Delete(dot)))
          }
          3 -> {
            val dot = decodeDot()!!
            insert(ChangeName(StringOperation.InsertAfter(actions[index].toChar(), dot)))
            index += 1
          }
          4 -> insert(ChangePlayerCharacter(decodeCharacterId(), true))
          5 -> insert(ChangePlayerCharacter(decodeCharacterId(), false))
          6 -> insert(DeleteCharacter(decodeCharacterId()))
          7 -> insert(ResetAllInitiatives)
          8 -> insert(decodeTurn { TurnAction.Delay(it) })
          9 -> insert(decodeTurn { TurnAction.Die(it) })
          10 -> insert(decodeTurn { TurnAction.FinishTurn(it) })
          11 -> insert(decodeTurnNoCharacter { TurnAction.ResolveConflicts })
          12 -> insert(decodeTurn { TurnAction.StartTurn(it) })
        }
      }
    }
  }
}
