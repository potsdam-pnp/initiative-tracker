sealed class ActionState

data class AddCharacter(val id: String): ActionState()
data class ChangeName(val id: String, val name: String): ActionState()
data class ChangeInitiative(val id: String, val initiative: Int): ActionState()
data class ChangePlayerCharacter(val id: String, val playerCharacter: Boolean): ActionState()
data class DeleteCharacter(val id: String): ActionState()

data class StartTurn(val id: String): ActionState()
data class Delay(val id: String): ActionState()
data class Die(val id: String): ActionState()

data class State2(
    val actions: List<ActionState>
) {
    fun predictNextTurns(withCurrent: Boolean): List<Character> {
        val alreadyPlayedCharactersSet = mutableSetOf<String>()
        val alreadyPlayedCharacters = mutableListOf<String>()
        val characters = mutableMapOf<String, Character>()

        val current = if (withCurrent) currentTurn() else null
        if (current != null) {
            alreadyPlayedCharactersSet.add(current)
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
                }
                is Delay -> {
                    if (!alreadyPlayedCharactersSet.contains(action.id)) {
                        characters[action.id] =
                            characters.getOrPut(action.id) { Character(action.id) }
                                .copy(isDelayed = true)
                    }
                }
                is Die -> {
                    if (!alreadyPlayedCharactersSet.contains(action.id)) {
                        alreadyPlayedCharacters.add(action.id)
                        dying += 1
                        alreadyPlayedCharactersSet.add(action.id)
                    }
                }

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
            }
        }

        val notYetPlayed = characters.filterKeys { !alreadyPlayedCharactersSet.contains(it) }.values.sortedBy {
            -((it.initiative ?: -100) * 2 + (if (it.playerCharacter == true) 0 else 1))
        }

        val currentAsList = if (current == null) listOf() else listOf(current)

        return (currentAsList + notYetPlayed.map { it.key } + alreadyPlayedCharacters.reversed()).mapNotNull {
            characters[it]
        }
    }

    fun currentTurn(): String? {
        for (action in actions.reversed()) {
            when (action) {
                is StartTurn -> return action.id
                is Delay -> return null
                else -> {}
            }
        }
        return null
    }

    fun toState(): State =
        State(
            inEditMode = false,
            characters = predictNextTurns(withCurrent = true),
            currentlySelectedCharacter = currentTurn()
        )

}
