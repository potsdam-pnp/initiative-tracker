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

object Encoders {
  private data class Des(
    val action: ActionType,
    val characterId: String? = null,
    val dot: Dot? = null,
    val arg: Int? = null,
  )

  private fun convertMessage(msg: Message<Action>): ProtoMessage {
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
          messageIdentifier = msg.msgIdentifier,
          maxMessageLength = msg.maxMessageSize,
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
          msgIdentifier = pb.messageIdentifier,
          maxMessageSize = pb.maxMessageLength,
        )
      MessageKind.SEND_VERSIONS ->
        Message.SendVersions(asClock(pb.clock), pb.actions.map { decodePbAction(it) })
      MessageKind.STOP_CONNECTION -> Message.StopConnection(Unit)
      MessageKind.SEND_VERSIONS_PARTIAL -> Message.StopConnection(Unit)
      is MessageKind.UNRECOGNIZED -> Message.StopConnection(Unit)
    }
  }
}
