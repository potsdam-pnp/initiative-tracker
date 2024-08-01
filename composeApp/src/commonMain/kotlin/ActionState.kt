sealed class ActionState

data class AddCharacter(val id: String): ActionState()
data class ChangeName(val id: String, val name: String): ActionState()
data class ChangeInitiative(val id: String, val initiative: Int): ActionState()
data class ChangePlayerCharacter(val id: String, val playerCharacter: Boolean): ActionState()
data class DeleteCharacter(val id: String): ActionState()

data class StartTurn(val id: String): ActionState()
data class Delay(val id: String): ActionState()
data class Die(val id: String): ActionState()

data class CharacterInfo(val id: String, val name: String? = null, val initiative: Int? = null, val playerCharacter: Boolean? = null, val dead: Boolean = false, val isDelayed: Boolean = false) {
    fun toCharacter(): Character =
        if (initiative == null) {
            Character.NoInitiativeYet(id, name ?: "", playerCharacter = playerCharacter ?: false)
        } else {
            Character.Finished(id, name ?: "", initiative, playerCharacter ?: false)
        }
}

data class State2(
    val actions: List<ActionState>
) {
    fun predictNextTurns(): List<CharacterInfo> {
        val alreadyPlayedCharactersSet = mutableSetOf<String>()
        val alreadyPlayedCharacters = mutableListOf<String>()
        val characters = mutableMapOf<String, CharacterInfo>()

        var dying = 0

        for (action in actions.reversed()) {
            when (action) {
                is StartTurn -> {
                    if (!alreadyPlayedCharactersSet.contains(action.id)) {
                        alreadyPlayedCharactersSet.add(action.id)
                        alreadyPlayedCharacters.add(alreadyPlayedCharacters.size - dying, action.id)
                        dying = 0
                    }
                }
                is Delay -> {
                    if (!alreadyPlayedCharactersSet.contains(action.id)) {
                        characters[action.id] =
                            characters.getOrPut(action.id) { CharacterInfo(action.id) }
                                .copy(isDelayed = true)
                    }
                }
                is Die -> {
                    if (alreadyPlayedCharactersSet.contains(action.id)) {
                        alreadyPlayedCharacters.add(action.id)
                        dying += 1
                        alreadyPlayedCharactersSet.add(action.id)
                    }
                }

                is AddCharacter -> {
                    characters.getOrPut(action.id) { CharacterInfo(action.id) }
                }
                is ChangeName -> {
                    characters[action.id] =
                        characters.getOrPut(action.id) { CharacterInfo(action.id) }.copy(name = action.name)
                }

                is ChangeInitiative -> {
                    characters[action.id] =
                        characters.getOrPut(action.id) { CharacterInfo(action.id) }.copy(initiative = action.initiative)
                }

                is ChangePlayerCharacter -> {
                    characters[action.id] = characters.getOrPut(action.id) { CharacterInfo(action.id) }
                        .copy(playerCharacter = action.playerCharacter)
                }

                is DeleteCharacter -> {
                    characters[action.id] = characters.getOrPut(action.id) { CharacterInfo(action.id) }
                        .copy(dead = true)
                }
            }
        }

        val notYetPlayed = characters.filterKeys { !alreadyPlayedCharactersSet.contains(it) }.values.sortedBy {
            (it.initiative ?: -100) * 2 + (if (it.playerCharacter == true) 0 else 1)
        }

        return (notYetPlayed.map { it.id } + alreadyPlayedCharacters.reversed()).mapNotNull {
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
            characters = predictNextTurns().map {
                it.toCharacter()
            },
            currentlySelectedCharacter = currentTurn()
        )

}
