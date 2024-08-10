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
import StartTurn
import State2

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
    data class ResolveConflicts(val undos: List<VectorClock>): TurnAction()
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
            listOfNotNull(
                AddCharacter(it.value.id.id),
                _ChangeName(it.value.id.id, it.value.name.textField()),
                if (initiative != null) ChangeInitiative(it.value.id.id, initiative) else null,
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
                    else ->
                        throw Exception("Not a turn action")
                }
            GrowingListItem(turn, v.op.predecessor) to v.metadata
        }

        val turnActions = turnActions.show(fetchVersion).mapNotNull {
            val result = when (it.second.turnAction) {
                is TurnAction.StartTurn ->
                    _StartTurn(it.second.id.id)

                is TurnAction.FinishTurn ->
                    _FinishTurn(it.second.id.id)

                is TurnAction.Die ->
                    _Die(it.second.id.id)

                is TurnAction.Delay ->
                    _Delay(it.second.id.id)

                is TurnAction.ResolveConflicts ->
                    null
            }
            if (result == null) null else it.first to result
        }

        return State2(characterActions + turnActions.map { it.second }, turnActions)
    }
}