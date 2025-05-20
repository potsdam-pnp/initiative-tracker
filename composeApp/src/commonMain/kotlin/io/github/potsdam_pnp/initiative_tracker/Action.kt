package io.github.potsdam_pnp.initiative_tracker

import io.github.potsdam_pnp.initiative_tracker.crdt.ClientIdentifier
import io.github.potsdam_pnp.initiative_tracker.crdt.Dot
import io.github.potsdam_pnp.initiative_tracker.crdt.GrowingListItem
import io.github.potsdam_pnp.initiative_tracker.crdt.Message
import io.github.potsdam_pnp.initiative_tracker.crdt.Operation
import io.github.potsdam_pnp.initiative_tracker.crdt.OperationMetadata
import io.github.potsdam_pnp.initiative_tracker.crdt.StringOperation
import io.github.potsdam_pnp.initiative_tracker.crdt.VectorClock
import io.github.potsdam_pnp.initiative_tracker.proto.ActionType
import io.github.potsdam_pnp.initiative_tracker.proto.Message as ProtoMessage
import io.github.potsdam_pnp.initiative_tracker.proto.MessageKind
import io.github.potsdam_pnp.initiative_tracker.proto.OperationAction
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

data class Turn(val turnAction: TurnAction, override val predecessor: Dot?) :
  Action(), GrowingListItem<TurnAction> {
  override val item: TurnAction
    get() = turnAction
}

data class CharacterId(val id: String)

data class AddCharacter(val id: String) : Action()

data class ChangeName(val id: String, val operation: StringOperation) : Action()

data class ChangeInitiative(val id: String, val initiative: Int) : Action()

data class ChangePlayerCharacter(val id: String, val playerCharacter: Boolean) : Action()

data class DeleteCharacter(val id: String) : Action()

object ResetAllInitiatives : Action()

fun serializeAction(it: Action): String {
  return when (it) {
    is AddCharacter -> "a${it.id}"
    is ChangeName ->
      when (it.operation) {
        is StringOperation.Delete ->
          "n${it.id}:${it.operation.dot.clientIdentifier.name}:${it.operation.dot.position}"
        is StringOperation.InsertAfter ->
          "N${it.id}:${it.operation.after?.clientIdentifier?.name ?: ""}:${it.operation.after?.position ?: ""}:${it.operation.character}"
      }
    is ChangeInitiative -> "i${it.id}:${it.initiative}"
    is ChangePlayerCharacter -> "${if (it.playerCharacter) "p" else "P"}${it.id}"
    is DeleteCharacter -> "c${it.id}"
    is ResetAllInitiatives -> "q"
    is Turn ->
      when (it.turnAction) {
        is TurnAction.StartTurn -> "s${it.turnAction.characterId.id}"
        is TurnAction.FinishTurn -> "f${it.turnAction.characterId.id}"
        is TurnAction.Die -> "d${it.turnAction.characterId.id}"
        is TurnAction.Delay -> "D${it.turnAction.characterId.id}"
        is TurnAction.ResolveConflicts -> "r"
      } +
        if (it.predecessor != null)
          ":" + it.predecessor.clientIdentifier.name + ":" + it.predecessor.position
        else ""
  }
}

fun deserializeAction(it: String): Action? {
  try {
    val turn = { usedParts: Int, turnAction: (List<String>) -> TurnAction ->
      val parts = it.substring(1).split(":")
      val startParts = parts.take(usedParts)
      if (parts.size == usedParts) {
        Turn(turnAction(startParts), null)
      } else {
        val predecessor = Dot(ClientIdentifier(parts[usedParts]), parts[usedParts + 1].toInt())
        Turn(turnAction(startParts), predecessor)
      }
    }
    return when (it[0]) {
      'a' -> AddCharacter(it.substring(1))
      'n' ->
        ChangeName(
          it.substring(1).split(":")[0],
          StringOperation.Delete(
            Dot(
              ClientIdentifier(it.substring(1).split(":")[1]),
              it.substring(1).split(":")[2].toInt(),
            )
          ),
        )
      'N' ->
        ChangeName(
          it.substring(1).split(":")[0],
          StringOperation.InsertAfter(
            character = it.substring(1).split(":", limit = 4)[3][0],
            after =
              if (it.substring(1).split(":")[2] != "") {
                Dot(
                  ClientIdentifier(it.substring(1).split(":")[1]),
                  it.substring(1).split(":")[2].toInt(),
                )
              } else null,
          ),
        )
      'i' -> ChangeInitiative(it.substring(1).split(":")[0], it.substring(1).split(":")[1].toInt())
      'p' -> ChangePlayerCharacter(it.substring(1), true)
      'P' -> ChangePlayerCharacter(it.substring(1), false)
      'c' -> DeleteCharacter(it.substring(1))
      's' -> turn(1) { TurnAction.StartTurn(CharacterId(it[0])) }
      'D' -> turn(1) { TurnAction.Delay(CharacterId(it[0])) }
      'd' -> turn(1) { TurnAction.Die(CharacterId(it[0])) }
      'f' -> turn(1) { TurnAction.FinishTurn(CharacterId(it[0])) }
      'r' -> turn(1) { TurnAction.ResolveConflicts }
      'q' -> ResetAllInitiatives
      else -> return null
    }
  } catch (e: Exception) {
    throw Exception("Error deserializing \"$it\"", e)
  }
}

object Encoders {
  private fun vectorClockEncode(vectorClock: VectorClock): String {
    return vectorClock.clock.toList().joinToString("~") { "${it.first.name}:${it.second}" }
  }

  private fun vectorClockDecode(s: String): VectorClock {
    if (s == "") return VectorClock(mapOf())
    return VectorClock(
      s.split("~")
        .map {
          val parts = it.split(":")
          ClientIdentifier(parts[0]) to parts[1].toInt()
        }
        .toMap()
    )
  }

  fun encode(msg: Message<Action>): String {
    return when (msg) {
      is Message.CurrentState -> "c" + vectorClockEncode(msg.vectorClock)
      is Message.StopConnection -> "s"
      is Message.RequestVersions ->
        "r" +
          vectorClockEncode(msg.vectorClock) +
          "}" +
          msg.dots.joinToString("}") { it.clientIdentifier.name + ":" + it.position }
      is Message.SendVersions ->
        "v" +
          vectorClockEncode(msg.vectorClock) +
          "}" +
          msg.versions.joinToString("}") { actionEncode(it) }
    }
  }

  fun decode(s: String): Message<Action> {
    val initial = s[0]
    val rest = s.substring(1)

    when (initial) {
      'c' -> {
        return Message.CurrentState(vectorClockDecode(rest))
      }

      's' -> {
        return Message.StopConnection(Unit)
      }

      'r' -> {
        val parts = rest.split("}")
        val clock = vectorClockDecode(parts[0])
        val dots =
          parts.drop(1).map {
            val parts = it.split(":")
            Dot(ClientIdentifier(parts[0]), parts[1].toInt())
          }
        return Message.RequestVersions(clock, dots)
      }

      'v' -> {
        val parts = rest.split("}")
        val clock = vectorClockDecode(parts[0])
        val versions = parts.drop(1).map { actionDecode(it) }
        return Message.SendVersions(clock, versions)
      }
      else -> throw Exception("Unknown message type $initial")
    }
  }

  private fun actionEncode(action: Operation<Action>): String {
    val intAction = serializeAction(action.op)
    val clock = vectorClockEncode(action.metadata.clock)
    val client = action.metadata.client.name

    return "${clock}%${client}%${intAction}"
  }

  private fun actionDecode(s: String): Operation<Action> {
    val parts = s.split("%", limit = 3)
    val clock = vectorClockDecode(parts[0])
    val client = ClientIdentifier(parts[1])
    val action = deserializeAction(parts[2])!!
    return Operation(OperationMetadata(clock, client), action)
  }

  private data class Des(
    val action: ActionType,
    val characterId: String? = null,
    val dot: Dot? = null,
    val arg: Int? = null,
  )

  fun convertMessage(msg: Message<Action>): ProtoMessage {
    return when (msg) {
      is Message.CurrentState -> {
        val clientIdentifiers = msg.vectorClock.clock.keys.toList().map { it.name }
        ProtoMessage(
          messageKind = MessageKind.CURRENT_STATE,
          clientIdentifiers = clientIdentifiers,
          clock =
            clientIdentifiers.map { msg.vectorClock.clock[ClientIdentifier(it)]?.toLong() ?: 0 },
        )
      }

      is Message.RequestVersions -> {
        val clientIdentifiers = msg.vectorClock.clock.keys.toList().map { it.name }
        ProtoMessage(
          messageKind = MessageKind.REQUEST_VERSIONS,
          clientIdentifiers = clientIdentifiers,
          clock =
            clientIdentifiers.map { msg.vectorClock.clock[ClientIdentifier(it)]?.toLong() ?: 0 },
          dots =
            msg.dots
              .flatMap {
                listOf(
                  clientIdentifiers.indexOf(it.clientIdentifier.name).toLong(),
                  it.position.toLong(),
                )
              }
              .toList(),
        )
      }
      is Message.SendVersions -> {
        val clientIdentifiers = msg.vectorClock.clock.keys.toList().map { it.name }
        val actions =
          msg.versions.map { version ->
            val (action, characterId, dot, arg) =
              when (version.op) {
                is AddCharacter -> Des(ActionType.ADD_CHARACTER, version.op.id)
                is ChangeInitiative ->
                  Des(ActionType.CHANGE_INITIATIVE, version.op.id, arg = version.op.initiative)
                is ChangeName ->
                  when (version.op.operation) {
                    is StringOperation.Delete ->
                      Des(ActionType.CHANGE_NAME_DELETE, version.op.id, version.op.operation.dot)
                    is StringOperation.InsertAfter ->
                      Des(
                        ActionType.CHANGE_NAME_INSERT_AFTER,
                        version.op.id,
                        version.op.operation.after,
                        version.op.operation.character.code,
                      )
                  }
                is ChangePlayerCharacter ->
                  if (version.op.playerCharacter)
                    Des(ActionType.CHANGE_PLAYER_CHARACTER_TO_PLAYER, version.op.id)
                  else Des(ActionType.CHANGE_PLAYER_CHARACTER_TO_NON_PLAYER, version.op.id)
                is DeleteCharacter -> Des(ActionType.DELETE_CHARACTER, version.op.id)
                ResetAllInitiatives -> Des(ActionType.RESET_ALL_INITIATIVE)
                is Turn ->
                  when (version.op.turnAction) {
                    is TurnAction.StartTurn ->
                      Des(
                        ActionType.START_TURN,
                        version.op.turnAction.characterId.id,
                        version.op.predecessor,
                      )
                    is TurnAction.Delay ->
                      Des(
                        ActionType.DELAY,
                        version.op.turnAction.characterId.id,
                        version.op.predecessor,
                      )
                    is TurnAction.Die ->
                      Des(
                        ActionType.DIE,
                        version.op.turnAction.characterId.id,
                        version.op.predecessor,
                      )
                    is TurnAction.FinishTurn ->
                      Des(
                        ActionType.FINISH_TURN,
                        version.op.turnAction.characterId.id,
                        version.op.predecessor,
                      )
                    TurnAction.ResolveConflicts ->
                      Des(ActionType.RESOLVE_CONFLICTS, null, version.op.predecessor)
                  }
              }
            OperationAction(
              clock =
                clientIdentifiers.map {
                  version.metadata.clock.clock[ClientIdentifier(it)]?.toLong() ?: 0
                },
              client = clientIdentifiers.indexOf(version.metadata.client.name),
              action = action,
              characterId = characterId,
              dotClient = dot?.let { clientIdentifiers.indexOf(it.clientIdentifier.name) },
              dotPosition = dot?.position,
              arg = arg,
            )
          }
        ProtoMessage(
          messageKind = MessageKind.SEND_VERSIONS,
          clientIdentifiers = clientIdentifiers,
          clock =
            clientIdentifiers.map { msg.vectorClock.clock[ClientIdentifier(it)]?.toLong() ?: 0 },
          actions = actions,
        )
      }
      is Message.StopConnection -> ProtoMessage(messageKind = MessageKind.STOP_CONNECTION)
    }
  }

  fun encodePb(msg: Message<Action>): ByteArray {
    return convertMessage(msg).encodeToByteArray()
  }

  fun decodePb(msg: ByteArray): Message<Action> {
    val pb = ProtoMessage.decodeFromByteArray(msg)
    fun asClock(clock: List<Long>): VectorClock {
      val result =
        pb.clientIdentifiers.mapIndexed { index, value ->
          ClientIdentifier(value) to clock[index].toInt()
        }
      return VectorClock(result.toMap())
    }
    fun decodePbAction(a: OperationAction): Operation<Action> {
      val metadata =
        OperationMetadata(asClock(a.clock), ClientIdentifier(pb.clientIdentifiers[a.client]))
      val id = a.characterId ?: ""
      val dot =
        when (a.dotClient to a.dotPosition) {
          Pair(null, null) -> null
          else -> {
            Dot(ClientIdentifier(pb.clientIdentifiers[a.dotClient ?: 0]), a.dotPosition ?: 0)
          }
        }
      val op =
        when (a.action) {
          ActionType.ADD_CHARACTER -> AddCharacter(id)
          ActionType.CHANGE_INITIATIVE -> ChangeInitiative(id, a.arg ?: 0)
          ActionType.CHANGE_NAME_DELETE -> ChangeName(id, StringOperation.Delete(dot ?: TODO()))
          ActionType.CHANGE_NAME_INSERT_AFTER ->
            ChangeName(id, StringOperation.InsertAfter(Char(a.arg ?: 0), dot))
          ActionType.CHANGE_PLAYER_CHARACTER_TO_NON_PLAYER -> ChangePlayerCharacter(id, false)
          ActionType.CHANGE_PLAYER_CHARACTER_TO_PLAYER -> ChangePlayerCharacter(id, true)
          ActionType.DELAY -> Turn(TurnAction.Delay(CharacterId(id)), dot)
          ActionType.DELETE_CHARACTER -> DeleteCharacter(id)
          ActionType.DIE -> Turn(TurnAction.Die(CharacterId(id)), dot)
          ActionType.FINISH_TURN -> Turn(TurnAction.FinishTurn(CharacterId(id)), dot)
          ActionType.RESET_ALL_INITIATIVE -> ResetAllInitiatives
          ActionType.RESOLVE_CONFLICTS -> Turn(TurnAction.ResolveConflicts, dot)
          ActionType.START_TURN -> Turn(TurnAction.StartTurn(CharacterId(id)), dot)
          is ActionType.UNRECOGNIZED -> TODO()
        }
      return Operation(metadata, op)
    }
    return when (pb.messageKind) {
      MessageKind.CURRENT_STATE -> Message.CurrentState(asClock(pb.clock))
      MessageKind.REQUEST_VERSIONS ->
        Message.RequestVersions(
          asClock(pb.clock),
          pb.dots.chunked(2) {
            Dot(ClientIdentifier(pb.clientIdentifiers[it[0].toInt()]), it[1].toInt())
          },
        )
      MessageKind.SEND_VERSIONS ->
        Message.SendVersions(asClock(pb.clock), pb.actions.map { decodePbAction(it) })
      MessageKind.STOP_CONNECTION -> Message.StopConnection(Unit)
      is MessageKind.UNRECOGNIZED -> Message.StopConnection(Unit)
    }
  }
}
