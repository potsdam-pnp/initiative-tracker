package io.github.potsdam_pnp.initiative_tracker.state

import AddCharacter
import ChangeInitiative
import State2

import StartTurn as _StartTurn
import Delay as _Delay
import Die as _Die
import FinishTurn as _FinishTurn
import ChangeName as _ChangeName

sealed class InitiativeOperation: Operation()

data class ChangeCharacterName(
    override val metadata: OperationMetadata,
    val id: CharacterId,
    val to: String): InitiativeOperation()

data class ChangeCharacterInitiative(
    override val metadata: OperationMetadata,
    val id: CharacterId,
    val to: Int): InitiativeOperation()

data class ChangeCharacterPlayerCharacter(
    override val metadata: OperationMetadata,
    val id: CharacterId,
    val to: Boolean): InitiativeOperation()

data class DeleteCharacter(
    override val metadata: OperationMetadata,
    val id: CharacterId,
    val to: Boolean): InitiativeOperation()


data class DoTurn(
    override val metadata: OperationMetadata,
    val id: CharacterId,
    val turnAction: TurnAction,
): InitiativeOperation()

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

class State(
    val characters: MutableMap<CharacterId, Character> = mutableMapOf(),
    val turnActions: GrowingList<Turn> = GrowingList()
): OperationState<InitiativeOperation>() {

    private fun withCharacter(id: CharacterId, op: Character.() -> Character) {
        characters[id] =
            characters.getOrPut(id) { Character(id, Value.empty(), Value.empty(), Value.empty()) }
                .let { it.op() }

    }

    override fun apply(operation: InitiativeOperation) {
        when (operation) {
            is ChangeCharacterName -> {
                withCharacter(operation.id) {
                    copy(name = name.insert(operation.to, operation.metadata.clock))
                }
            }
            is ChangeCharacterInitiative ->
                withCharacter(operation.id) {
                    copy(initiative = initiative.insert(operation.to, operation.metadata.clock))
                }
            is ChangeCharacterPlayerCharacter ->
                withCharacter(operation.id) {
                    copy(playerCharacter = playerCharacter.insert(operation.to, operation.metadata.clock))
                }
            is DeleteCharacter ->
                withCharacter(operation.id) {
                    copy(dead = dead.insert(true, operation.metadata.clock))
                }
            is DoTurn ->
                turnActions.insert(
                    GrowingListItem(
                        Turn(operation.id, operation.turnAction),
                        operation.metadata.clock,
                        predecessorUndos = (operation.turnAction as? TurnAction.ResolveConflicts)?.undos ?: listOf()
                    )
                )
        }
    }

    fun toState2(): State2 {
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

        val turnActions = turnActions.ordered().prefixWithoutConflict.mapNotNull {
            when (it.turnAction) {
                is TurnAction.StartTurn ->
                    _StartTurn(it.id.id)

                is TurnAction.FinishTurn ->
                    _FinishTurn(it.id.id)

                is TurnAction.Die ->
                    _Die(it.id.id)

                is TurnAction.Delay ->
                    _Delay(it.id.id)

                is TurnAction.ResolveConflicts ->
                    null
            }
        }

        return State2(characterActions + turnActions)
    }
}