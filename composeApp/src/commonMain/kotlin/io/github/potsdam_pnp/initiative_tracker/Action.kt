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

data class CharacterId(val dot: Dot)

data class AddCharacter(val id: CharacterId) : Action()

data class ChangeName(val id: CharacterId, val operation: StringOperation) : Action()

data class ChangeInitiative(val id: CharacterId, val initiative: Int) : Action()

data class ChangePlayerCharacter(val id: CharacterId, val playerCharacter: Boolean) : Action()

data class DeleteCharacter(val id: CharacterId) : Action()

object ResetAllInitiatives : Action()

object Encoders {
  private data class Des(
    val action: ActionType,
    val characterId: CharacterId? = null,
    val dot: Dot? = null,
    val arg: Int? = null,
  )

  private fun convertMessage(msg: Message<Action>): ProtoMessage {
    return when (msg) {
      is Message.CurrentState -> {
        val clientIdentifiers = msg.vectorClock.clock.keys.toList()
        ProtoMessage(
          messageKind = MessageKind.CURRENT_STATE,
          clientIdentifiers = clientIdentifiers.map { it.encodeToProto() },
          clock = clientIdentifiers.map { msg.vectorClock.clock[it]?.toLong() ?: 0 },
        )
      }

      is Message.RequestVersions -> {
        val clientIdentifiers = msg.vectorClock.clock.keys.toList()
        ProtoMessage(
          messageKind = MessageKind.REQUEST_VERSIONS,
          clientIdentifiers = clientIdentifiers.map { it.encodeToProto() },
          clock = clientIdentifiers.map { msg.vectorClock.clock[it]?.toLong() ?: 0 },
          dots =
            msg.dots
              .flatMap {
                listOf(
                  clientIdentifiers.indexOf(it.clientIdentifier).toLong(),
                  it.position.toLong(),
                )
              }
              .toList(),
          messageIdentifier = msg.msgIdentifier,
          maxMessageLength = msg.maxMessageSize,
        )
      }
      is Message.SendVersions -> {
        val clientIdentifiers = msg.vectorClock.clock.keys.toList()
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
                        version.op.turnAction.characterId,
                        version.op.predecessor,
                      )
                    is TurnAction.Delay ->
                      Des(
                        ActionType.DELAY,
                        version.op.turnAction.characterId,
                        version.op.predecessor,
                      )
                    is TurnAction.Die ->
                      Des(
                        ActionType.DIE,
                        version.op.turnAction.characterId,
                        version.op.predecessor,
                      )
                    is TurnAction.FinishTurn ->
                      Des(
                        ActionType.FINISH_TURN,
                        version.op.turnAction.characterId,
                        version.op.predecessor,
                      )
                    TurnAction.ResolveConflicts ->
                      Des(ActionType.RESOLVE_CONFLICTS, null, version.op.predecessor)
                  }
              }
            OperationAction(
              clock = clientIdentifiers.map { version.metadata.clock.clock[it]?.toLong() ?: 0 },
              client = clientIdentifiers.indexOf(version.metadata.client),
              action = action,
              characterIdDotClient = characterId?.let { clientIdentifiers.indexOf(it.dot.clientIdentifier) },
              characterIdDotPosition = characterId?.dot?.position,
              dotClient = dot?.let { clientIdentifiers.indexOf(it.clientIdentifier) },
              dotPosition = dot?.position,
              arg = arg,
            )
          }
        ProtoMessage(
          messageKind = MessageKind.SEND_VERSIONS,
          clientIdentifiers = clientIdentifiers.map { it.encodeToProto() },
          clock = clientIdentifiers.map { msg.vectorClock.clock[it]?.toLong() ?: 0 },
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
          ClientIdentifier.decodeFromProto(value) to clock[index].toInt()
        }
      return VectorClock(result.toMap())
    }
    fun decodePbAction(a: OperationAction): Operation<Action> {
      val metadata =
        OperationMetadata(
          asClock(a.clock),
          ClientIdentifier.decodeFromProto(pb.clientIdentifiers[a.client]),
        )
      val id = a.characterIdDotClient?.let { client ->
        a.characterIdDotPosition?.let { position ->
          CharacterId(Dot(ClientIdentifier.decodeFromProto(pb.clientIdentifiers[client]), position))
        }
      }
      val dot =
        when (a.dotClient to a.dotPosition) {
          Pair(null, null) -> null
          else -> {
            Dot(
              ClientIdentifier.decodeFromProto(pb.clientIdentifiers[a.dotClient ?: 0]),
              a.dotPosition ?: 0,
            )
          }
        }
      val op =
        when (a.action) {
          ActionType.ADD_CHARACTER -> AddCharacter(id!!)
          ActionType.CHANGE_INITIATIVE -> ChangeInitiative(id!!, a.arg ?: 0)
          ActionType.CHANGE_NAME_DELETE -> ChangeName(id!!, StringOperation.Delete(dot ?: TODO()))
          ActionType.CHANGE_NAME_INSERT_AFTER ->
            ChangeName(id!!, StringOperation.InsertAfter(Char(a.arg ?: 0), dot))
          ActionType.CHANGE_PLAYER_CHARACTER_TO_NON_PLAYER -> ChangePlayerCharacter(id!!, false)
          ActionType.CHANGE_PLAYER_CHARACTER_TO_PLAYER -> ChangePlayerCharacter(id!!, true)
          ActionType.DELAY -> Turn(TurnAction.Delay(id!!), dot)
          ActionType.DELETE_CHARACTER -> DeleteCharacter(id!!)
          ActionType.DIE -> Turn(TurnAction.Die(id!!), dot)
          ActionType.FINISH_TURN -> Turn(TurnAction.FinishTurn(id!!), dot)
          ActionType.RESET_ALL_INITIATIVE -> ResetAllInitiatives
          ActionType.RESOLVE_CONFLICTS -> Turn(TurnAction.ResolveConflicts, dot)
          ActionType.START_TURN -> Turn(TurnAction.StartTurn(id!!), dot)
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
            Dot(
              ClientIdentifier.decodeFromProto(pb.clientIdentifiers[it[0].toInt()]),
              it[1].toInt(),
            )
          },
          msgIdentifier = pb.messageIdentifier,
          maxMessageSize = pb.maxMessageLength,
        )
      MessageKind.REQUEST_VERSIONS_OPTIMIZED -> {
        val clock = asClock(pb.clock)
        val dots =
          pb.dots.chunked(2) {
            val clientIdentifier =
              ClientIdentifier.decodeFromProto(pb.clientIdentifiers[it[0].toInt()])
            val position = it[1].toInt()
            val clockPosition = clock.clock[clientIdentifier] ?: 0
            (position until (clockPosition + 1)).map { Dot(clientIdentifier, it) }
          }
        Message.RequestVersions(
          clock,
          dots.flatten(),
          msgIdentifier = pb.messageIdentifier,
          maxMessageSize = pb.maxMessageLength,
        )
      }
      MessageKind.SEND_VERSIONS ->
        Message.SendVersions(asClock(pb.clock), pb.actions.map { decodePbAction(it) })
      MessageKind.STOP_CONNECTION -> Message.StopConnection(Unit)
      MessageKind.SEND_VERSIONS_PARTIAL -> Message.StopConnection(Unit)
      is MessageKind.UNRECOGNIZED -> Message.StopConnection(Unit)
    }
  }
}
