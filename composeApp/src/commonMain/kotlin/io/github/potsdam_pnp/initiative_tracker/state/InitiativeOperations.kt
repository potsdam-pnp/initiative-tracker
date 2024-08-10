package io.github.potsdam_pnp.initiative_tracker.state

import ActionState
import AddCharacter
import ChangeInitiative
import ChangeName
import ChangePlayerCharacter
import Delay
import DeleteCharacter
import Die
import FinishTurn
import ResolveConflict
import StartTurn
import State2
import deserializeAction
import serializeAction

import StartTurn as _StartTurn
import Delay as _Delay
import Die as _Die
import FinishTurn as _FinishTurn
import ChangeName as _ChangeName



sealed class TurnAction {
    object StartTurn: TurnAction()
    object FinishTurn: TurnAction()
    object Die: TurnAction()
    object Delay: TurnAction()
    object ResolveConflicts: TurnAction()
}

data class Turn(
    val id: CharacterId,
    val turnAction: TurnAction
)

data class CharacterId(val id: String)

data class Character(
    val id: CharacterId,
    val name: Value<String> = Value.empty(),
    val initiative: Value<Int> = Value.empty(),
    val playerCharacter: Value<Boolean> = Value.empty(),
    val dead: Value<Boolean> = Value.empty()
)

data class ActionWrapper(
    val action: ActionState,
    val predecessor: Version? // only used for turn-based actions
)

class State(
    val characters: MutableMap<CharacterId, Character> = mutableMapOf(),
    var turnActions: Value<GrowingListItem<Turn>> = Value.empty()
): OperationState<ActionWrapper>() {
    private fun withCharacter(id: CharacterId, op: Character.() -> Character) {
        characters[id] =
            characters.getOrPut(id) { Character(id, Value.empty(), Value.empty(), Value.empty()) }
                .let { it.op() }
    }

    override fun apply(operation: Operation<ActionWrapper>) {
        val op = operation.op.action
        when (op) {
            is AddCharacter -> {
                withCharacter(CharacterId(op.id)) { this }
            }
            is ChangeName -> {
                withCharacter(CharacterId(op.id)) {
                    copy(name = name.insert(op.name, operation.metadata))
                }
            }

            is ChangeInitiative ->
                withCharacter(CharacterId(op.id)) {
                    copy(initiative = initiative.insert(op.initiative, operation.metadata))
                }

            is ChangePlayerCharacter ->
                withCharacter(CharacterId(op.id)) {
                    copy(
                        playerCharacter = playerCharacter.insert(
                            op.playerCharacter,
                            operation.metadata
                        )
                    )
                }

            is DeleteCharacter ->
                withCharacter(CharacterId(op.id)) {
                    copy(dead = dead.insert(true, operation.metadata))
                }

            is StartTurn ->
                withTurnActions {
                    insert(
                        GrowingListItem(
                            Turn(CharacterId(op.id), TurnAction.StartTurn),
                            predecessor = operation.op.predecessor
                        ),
                        operation.metadata
                    )
                }

            is FinishTurn ->
                withTurnActions {
                    insert(
                        GrowingListItem(
                            Turn(CharacterId(op.id), TurnAction.FinishTurn),
                            predecessor = operation.op.predecessor
                        ),
                        operation.metadata
                    )
                }

            is Die ->
                withTurnActions {
                    insert(
                        GrowingListItem(
                            Turn(CharacterId(op.id), TurnAction.Die),
                            predecessor = operation.op.predecessor
                        ),
                        operation.metadata
                    )
                }
            is Delay ->
                withTurnActions {
                    insert(
                        GrowingListItem(
                            Turn(CharacterId(op.id), TurnAction.Delay),
                            predecessor = operation.op.predecessor
                        ),
                        operation.metadata
                    )
                }
            is ResolveConflict ->
                withTurnActions {
                    insert(
                        GrowingListItem(
                            Turn(CharacterId(""), TurnAction.ResolveConflicts), //TODO ResolveConflict should be a "Turn" without a character
                            predecessor = operation.op.predecessor
                        ),
                        operation.metadata
                    )
                }
        }
    }

    private fun withTurnActions(f: Value<GrowingListItem<Turn>>.() -> Value<GrowingListItem<Turn>>) {
        turnActions = turnActions.f()
    }

    fun toState2(snapshot: Snapshot<ActionWrapper, State>): State2 {
        val characterActions = characters.flatMap {
            val initiative = it.value.initiative.value.let {
                if (it.size == 1) it.first().first else null
            }

            val playerCharacter = it.value.playerCharacter.value.let {
                when {
                    it.isEmpty() -> null
                    it.all { it.first } -> true
                    it.all { !it.first } -> false
                    else -> null
                }
            }

            val isDead = it.value.dead.value.let {
                when {
                    it.isEmpty() -> null
                    it.any { it.first } -> true
                    else -> false
                }
            }

            listOfNotNull(
                AddCharacter(it.value.id.id),
                if (it.value.name.value.isNotEmpty()) ChangeName(it.value.id.id, it.value.name.textField()) else null,
                if (initiative != null) ChangeInitiative(it.value.id.id, initiative) else null,
                if (playerCharacter != null) ChangePlayerCharacter(it.value.id.id, playerCharacter) else null,
                if (isDead == true) DeleteCharacter(it.value.id.id) else null
            )
        }

        val fetchVersion = { version: Version ->
            val v = snapshot.fetchVersion(version)!!
            val turn =
                when (v.op.action) {
                    is StartTurn ->
                        Turn(CharacterId(v.op.action.id), TurnAction.StartTurn)

                    is FinishTurn ->
                        Turn(CharacterId(v.op.action.id), TurnAction.FinishTurn)

                    is Die ->
                        Turn(CharacterId(v.op.action.id), TurnAction.Die)

                    is Delay ->
                        Turn(CharacterId(v.op.action.id), TurnAction.Delay)

                    is ResolveConflict ->
                        Turn(CharacterId(""), TurnAction.ResolveConflicts)

                    else ->
                        throw Exception("Not a turn action")
                }
            GrowingListItem(turn, v.op.predecessor) to v.metadata
        }

        val turnActions = turnActions.show(fetchVersion).mapNotNull {
            val result = when (it.third.turnAction) {
                is TurnAction.StartTurn ->
                    _StartTurn(it.third.id.id)

                is TurnAction.FinishTurn ->
                    _FinishTurn(it.third.id.id)

                is TurnAction.Die ->
                    _Die(it.third.id.id)

                is TurnAction.Delay ->
                    _Delay(it.third.id.id)

                is TurnAction.ResolveConflicts ->
                    ResolveConflict
            }
            Triple(it.first, it.second, result)
        }

        return State2(characterActions + turnActions.filter { it.second == ConflictState.InAllTimelines }.map { it.third }, turnActions)
    }
}

object Encoders {
    private fun vectorClockEncode(vectorClock: VectorClock): String {
        return vectorClock.clock.toList().joinToString("~") { "${it.first.name}:${it.second}" }
    }

    private fun vectorClockDecode(s: String): VectorClock {
        if (s == "") return VectorClock(mapOf())
        return VectorClock(s.split("~").map {
            val parts = it.split(":")
            ClientIdentifier(parts[0]) to parts[1].toInt()
        }.toMap())
    }


    fun encode(msg: Message<ActionWrapper>): String {
        return when (msg) {
            is Message.CurrentState ->
                "c" + vectorClockEncode(msg.vectorClock)
            is Message.StopConnection -> "s"
            is Message.RequestVersions ->
                "r" + vectorClockEncode(msg.vectorClock) +
                        "}" + msg.versions.joinToString("}") { it.clientIdentifier.name + ":" + it.position }
            is Message.SendVersions ->
                "v" + vectorClockEncode(msg.vectorClock) + "}" +
                        msg.versions.joinToString("}") { actionEncode(it) }
        }
    }

    fun decode(s: String): Message<ActionWrapper> {
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
                val versions = parts.drop(1).map {
                    val parts = it.split(":")
                    Version(ClientIdentifier(parts[0]), parts[1].toInt())
                }
                return Message.RequestVersions(clock, versions)
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

    private fun actionEncode(action: Operation<ActionWrapper>): String {
        val intAction = serializeAction(action.op.action)
        val clock = vectorClockEncode(action.metadata.clock)
        val client = action.metadata.client.name
        val predecessor = if (action.op.predecessor == null) "" else
            action.op.predecessor.clientIdentifier.name + ":" + action.op.predecessor.position

        return "${clock}%${client}%${predecessor}%${intAction}"
    }

    private fun actionDecode(s: String): Operation<ActionWrapper> {
        val parts = s.split("%", limit=4)
        val clock = vectorClockDecode(parts[0])
        val client = ClientIdentifier(parts[1])
        val predecessor = if (parts[2] == "") null else {
            val p = parts[2].split(":")
            Version(ClientIdentifier(p[0]), p[1].toInt())
        }
        val action = deserializeAction(parts[3])!!
        return Operation(OperationMetadata(clock, client), ActionWrapper(action, predecessor))
    }
}