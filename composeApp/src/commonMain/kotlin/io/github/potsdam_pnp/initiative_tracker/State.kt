package io.github.potsdam_pnp.initiative_tracker

import UiCharacter
import UiState
import io.github.potsdam_pnp.initiative_tracker.crdt.Dot
import io.github.potsdam_pnp.initiative_tracker.crdt.Operation
import io.github.potsdam_pnp.initiative_tracker.crdt.AbstractState
import io.github.potsdam_pnp.initiative_tracker.crdt.Register
import io.github.potsdam_pnp.initiative_tracker.crdt.Repository
import io.github.potsdam_pnp.initiative_tracker.crdt.VectorClock
import io.github.potsdam_pnp.initiative_tracker.crdt.show

class State(
    val characters: MutableMap<CharacterId, Character> = mutableMapOf(),
    var turnActions: Register<Turn> = Register.empty(),
    var initiativeResets: VectorClock = VectorClock.empty()
): AbstractState<Action>() {
    private fun withCharacter(id: CharacterId, op: Character.() -> Character) {
        characters[id] =
            characters.getOrPut(id) { Character(id, Register.empty(), Register.empty(), Register.empty()) }
                .let { it.op() }
    }

    override fun apply(operation: Operation<Action>): List<Dot> {
        when (val op = operation.op) {
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
            is ResetAllInitiatives ->
                initiativeResets = initiativeResets.merge(operation.metadata.clock)


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

            is Turn -> {
                turnActions = turnActions.insert(op, operation.metadata)
            }
        }
        return listOf()
    }

    override fun predecessors(op: Action): List<Dot> =
        if (op is Turn) { op.predecessor?.let { listOf(it) } ?: listOf() } else listOf()

    private fun commonLatestTurn(repository: Repository<Action, State>): Turn? {
        return when {
            turnActions.value.isEmpty() -> null
            else -> {
                var resultValue = turnActions
                while (resultValue.value.size > 1) {
                    val firstElement = resultValue.value[0]
                    resultValue = Register(resultValue.value.drop(1))
                    val predecessor = firstElement.first.predecessor
                    if (predecessor != null) {
                        val fetched = repository.fetchVersion(predecessor)!!
                        resultValue = resultValue.insert(fetched.op as Turn, fetched.metadata)
                    } else {
                        return null
                    }
                }
                return resultValue.value[0].first
            }
        }
    }

    private data class CharacterData(val turns: Int = 0, val delayed: Boolean = false, val alreadyPlayed: Boolean = false)
    fun predictNextTurns(withCurrent: Boolean, repository: Repository<Action, State>): List<UiCharacter> {
        val state = mutableMapOf<CharacterId, CharacterData>()
        val alreadyPlayedCharacters = mutableListOf<CharacterId>()

        val updatePlayedCharacters = { characterId: CharacterId, characterData: (CharacterData) -> CharacterData ->
            state[characterId] = characterData(state.getOrElse(characterId){ CharacterData() })
        }
        val alreadyPlayed = { characterId: CharacterId -> state.get(characterId)?.alreadyPlayed  == true }

        val current = if (withCurrent) currentTurn(repository) else null
        if (current != null) {
            state[current] = CharacterData(turns = -1, alreadyPlayed = true)
        }

        var dying = 0

        var turn = commonLatestTurn(repository)
        while (turn != null) {
            when (val action = turn.turnAction) {
                is TurnAction.StartTurn -> {
                    if (!alreadyPlayed(action.characterId)) {
                        alreadyPlayedCharacters.add(alreadyPlayedCharacters.size - dying, action.characterId)
                    }
                    updatePlayedCharacters(action.characterId) { it.copy(turns = it.turns + 1, alreadyPlayed = true) }
                    dying = 0
                }
                is TurnAction.Delay -> {
                    if (!alreadyPlayed(action.characterId)) {
                        updatePlayedCharacters(action.characterId) { it.copy(delayed = true) }
                    }
                    updatePlayedCharacters(action.characterId) { it.copy(turns = it.turns - 1)}
                }
                is TurnAction.Die -> {
                    if (!alreadyPlayed(action.characterId)) {
                        alreadyPlayedCharacters.add(action.characterId)
                        dying += 1
                        updatePlayedCharacters(action.characterId) { it.copy(alreadyPlayed = true) }
                    }
                }
                is TurnAction.FinishTurn -> {}

                is TurnAction.ResolveConflicts -> {}
            }

            turn = turn.predecessor?.let { repository.fetchVersion(it) }?.let { it.op as Turn }
        }

        val notYetPlayed = characters.filterKeys { !alreadyPlayed(it) }.values.sortedBy {
            -((it.resolvedInitiative(initiativeResets) ?: -100) * 2 + (if (it.resolvedPlayerCharacter() == true) 0 else 1))
        }

        val currentAsList = if (current == null) listOf() else listOf(current)

        return (currentAsList + notYetPlayed.map { it.id } + alreadyPlayedCharacters.reversed()).mapNotNull {
            val result = characters[it]
            if (result?.resolvedDead() == true) null else {
                UiCharacter(
                    key = it.id,
                    name = result?.resolvedName(),
                    initiative = result?.resolvedInitiative(initiativeResets),
                    playerCharacter = result?.resolvedPlayerCharacter(),
                    dead = result?.resolvedDead() ?: false,
                    isDelayed = state[it]?.delayed ?: false,
                    turn = state[it]?.turns ?: 0
                )
            }
        }
    }

    fun currentTurn(repository: Repository<Action, State>): CharacterId? {
        var turn = commonLatestTurn(repository)
        while (turn != null) {
            when (val action = turn.turnAction) {
                is TurnAction.StartTurn -> return action.characterId
                is TurnAction.Delay -> return null
                is TurnAction.FinishTurn -> return null
                else -> {}
            }
            turn = turn.predecessor?.let { repository.fetchVersion(it) }?.let { it.op as Turn }
        }
        return null
    }

    fun toUiState(repository: Repository<Action, State>): UiState =
        UiState(
            characters = predictNextTurns(withCurrent = true, repository),
            currentlySelectedCharacter = currentTurn(repository)?.id,
            actions = turnActions.show {
                val result = repository.fetchVersion(it)!!
                Pair(result.op as Turn, result.metadata)
            },
            turnConflicts = turnActions.value.size > 1
        )
}

