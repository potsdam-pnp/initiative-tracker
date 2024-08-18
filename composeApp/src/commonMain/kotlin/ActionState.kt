import io.github.potsdam_pnp.initiative_tracker.ActionState
import io.github.potsdam_pnp.initiative_tracker.AddCharacter
import io.github.potsdam_pnp.initiative_tracker.ChangeInitiative
import io.github.potsdam_pnp.initiative_tracker.ChangeName
import io.github.potsdam_pnp.initiative_tracker.ChangePlayerCharacter
import io.github.potsdam_pnp.initiative_tracker.Delay
import io.github.potsdam_pnp.initiative_tracker.DeleteCharacter
import io.github.potsdam_pnp.initiative_tracker.Die
import io.github.potsdam_pnp.initiative_tracker.FinishTurn
import io.github.potsdam_pnp.initiative_tracker.ResetAllInitiatives
import io.github.potsdam_pnp.initiative_tracker.ResolveConflict
import io.github.potsdam_pnp.initiative_tracker.StartTurn
import io.github.potsdam_pnp.initiative_tracker.crdt.ConflictState
import io.github.potsdam_pnp.initiative_tracker.crdt.Dot

data class State2(
    val actions: List<ActionState>,
    val turnActions: List<Triple<Dot, ConflictState, ActionState>>
) {
    fun predictNextTurns(withCurrent: Boolean): List<Character> {
        val alreadyPlayedCharactersSet = mutableSetOf<String>()
        val alreadyPlayedCharacters = mutableListOf<String>()
        val characters = mutableMapOf<String, Character>()

        val current = if (withCurrent) currentTurn() else null
        if (current != null) {
            alreadyPlayedCharactersSet.add(current)
            characters[current] = Character(current, turn = -1)
        }

        var dying = 0

        for (action in actions.reversed()) {
            when (action) {
                is StartTurn -> {
                    if (!alreadyPlayedCharactersSet.contains(action.id)) {
                        alreadyPlayedCharactersSet.add(action.id)
                        alreadyPlayedCharacters.add(alreadyPlayedCharacters.size - dying, action.id)
                    }
                    dying = 0
                    characters[action.id] =
                        characters.getOrPut(action.id) {
                            Character(action.id)
                        }.let {
                            it.copy(turn = it.turn + 1)
                        }
                }
                is Delay -> {
                    if (!alreadyPlayedCharactersSet.contains(action.id)) {
                        characters[action.id] =
                            characters.getOrPut(action.id) { Character(action.id) }
                                .copy(isDelayed = true)
                    }
                    characters[action.id] = characters[action.id]!!.let { it.copy(turn = it.turn - 1) }
                }
                is Die -> {
                    if (!alreadyPlayedCharactersSet.contains(action.id)) {
                        alreadyPlayedCharacters.add(action.id)
                        dying += 1
                        alreadyPlayedCharactersSet.add(action.id)
                    }
                }
                is FinishTurn -> {}

                is AddCharacter -> {
                    characters.getOrPut(action.id) { Character(action.id) }
                }
                is ChangeName -> {
                    if (characters[action.id]?.name == null) {
                        characters[action.id] =
                            characters.getOrPut(action.id) { Character(action.id) }
                                .copy(name = action.name)
                    }
                }

                is ChangeInitiative -> {
                    if (characters[action.id]?.initiative == null) {
                        characters[action.id] =
                            characters.getOrPut(action.id) { Character(action.id) }
                                .copy(initiative = action.initiative)
                    }
                }

                is ChangePlayerCharacter -> {
                    if (characters[action.id]?.playerCharacter == null) {
                        characters[action.id] =
                            characters.getOrPut(action.id) { Character(action.id) }
                                .copy(playerCharacter = action.playerCharacter)
                    }
                }

                is DeleteCharacter -> {
                    characters[action.id] = characters.getOrPut(action.id) { Character(action.id) }
                        .copy(dead = true)
                }
                is ResolveConflict -> {}
                is ResetAllInitiatives -> {}
            }
        }

        val notYetPlayed = characters.filterKeys { !alreadyPlayedCharactersSet.contains(it) }.values.sortedBy {
            -((it.initiative ?: -100) * 2 + (if (it.playerCharacter == true) 0 else 1))
        }

        val currentAsList = if (current == null) listOf() else listOf(current)

        return (currentAsList + notYetPlayed.map { it.key } + alreadyPlayedCharacters.reversed()).mapNotNull {
            val result = characters[it]
            if (result?.dead == true) null else result
        }
    }

    fun currentTurn(): String? {
        for (action in actions.reversed()) {
            when (action) {
                is StartTurn -> return action.id
                is Delay -> return null
                is FinishTurn -> return null
                else -> {}
            }
        }
        return null
    }

    fun toState(): State =
        State(
            characters = predictNextTurns(withCurrent = true),
            currentlySelectedCharacter = currentTurn(),
            actions = turnActions,
            turnConflicts = turnActions.any { it.second is ConflictState.InTimelines }
        )
}
