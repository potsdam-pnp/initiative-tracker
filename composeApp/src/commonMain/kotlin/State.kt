import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

sealed class Character {
    abstract val key: String

    data class Finished(
        override val key: String,
        val name: String,
        val initiative: Int,
        val playerCharacter: Boolean): Character()

    data class NoInitiativeYet(
        override val key: String,
        val name: String,
        val playerCharacter: Boolean): Character()

    data class Edit(
        override val key: String,
        val name: String,
        val initiative: Int?,
        val playerCharacter: Boolean,
        val focusRequester: FocusRequester = FocusRequester(),
        val focusInitiative: Boolean): Character()
}

data class State(
    val inEditMode: Boolean,
    val characters: List<Character> = listOf(),
    val currentlySelectedCharacter: String? = null,
)

interface Actions {
    fun deleteCharacter(characterKey: String)
    fun editCharacter(characterKey: String, name: String)
    fun editInitiative(characterKey: String, initiative: String)
    fun toggleEditCharacter(characterKey: String)
    fun moveCharacterUp(characterKey: String)
    fun moveCharacterDown(characterKey: String)
    fun addCharacter()
    fun die(characterKey: String)
    fun toggleEditMode()
    fun sort()
    fun delay()
    fun next()
}


class Model private constructor(s: State) : ViewModel(), Actions {
    private val _state = MutableStateFlow(s)
    val state = _state.asStateFlow()

    private var lastKey: Int = 0
    private fun nextKey(): String {
        lastKey += 1
        return lastKey.toString()
    }

    constructor(data: String?) : this(State(inEditMode = data != null)) {
        val characterNames = data?.split(",") ?: emptyList()
        updateCharacterList {
            characterNames.map {
                Character.NoInitiativeYet(nextKey(), it, playerCharacter = true)
            }
        }
    }

    private fun updateCharacterList(f: (List<Character>) -> List<Character>) {
        _state.update { it.copy(characters = f(it.characters)) }
    }

    override fun deleteCharacter(characterKey: String) {
        updateCharacterList { it.filter { it.key != characterKey } }
    }

    override fun editCharacter(characterKey: String, name: String) {
        updateCharacterList {
            it.map {
                if (it.key == characterKey && it is Character.Edit) {
                    it.copy(name = name)
                } else {
                    it
                }
            }
        }
    }

    override fun editInitiative(characterKey: String, initiative: String) {
        if (initiative.toIntOrNull() == null && initiative != "") return
        updateCharacterList {
            it.map {
                if (it.key == characterKey && it is Character.Edit) {
                    it.copy(initiative = initiative.toIntOrNull())
                } else {
                    it
                }
            }
        }
    }

    override fun toggleEditCharacter(characterKey: String) {
        updateCharacterList {
            it.map {
                if (it.key == characterKey) {
                    when (it) {
                        is Character.Edit ->
                            if (it.initiative != null) {
                                Character.Finished(
                                    it.key,
                                    it.name,
                                    it.initiative,
                                    it.playerCharacter
                                )
                            } else {
                                Character.NoInitiativeYet(it.key, it.name, it.playerCharacter)
                            }

                        is Character.Finished -> Character.Edit(
                            it.key,
                            it.name,
                            it.initiative,
                            it.playerCharacter,
                            focusInitiative = false
                        )

                        is Character.NoInitiativeYet -> Character.Edit(
                            it.key,
                            it.name,
                            null,
                            it.playerCharacter,
                            focusInitiative = true
                        )
                    }
                } else {
                    it
                }
            }
        }
    }

    override fun moveCharacterUp(characterKey: String) {
        updateCharacterList {
            it.toMutableList().also {
                val currentIndex = it.indexOfFirst { it.key == characterKey }
                if (currentIndex >= 1) {
                    val currentCharacter = it[currentIndex]
                    val previousCharacter = it[currentIndex - 1]
                    it[currentIndex] = previousCharacter
                    it[currentIndex - 1] = currentCharacter
                } else if (currentIndex == 0) {
                    val currentCharacter = it[currentIndex]
                    it.removeAt(0)
                    it.add(currentCharacter)
                }
            }
        }
    }

    override fun moveCharacterDown(characterKey: String) {
        updateCharacterList {
            it.toMutableList().also {
                val currentIndex = it.indexOfFirst { it.key == characterKey }
                if (currentIndex < it.size - 1 && currentIndex >= 0) {
                    val currentCharacter = it[currentIndex]
                    val nextCharacter = it[currentIndex + 1]
                    it[currentIndex] = nextCharacter
                    it[currentIndex + 1] = currentCharacter
                } else if (currentIndex == it.size - 1) {
                    val currentCharacter = it[currentIndex]
                    it.removeAt(currentIndex)
                    it.add(0, currentCharacter)
                }
            }
        }
    }

    override fun addCharacter() {
        val key = nextKey()
        val next = Character.Edit(key,"", null, false, FocusRequester(), focusInitiative = false);
        updateCharacterList { it + next }
    }

    override fun die(characterKey: String) {
        val currentlySelectedCharacter = state.value.currentlySelectedCharacter
        if (characterKey == currentlySelectedCharacter) return

        updateCharacterList {
            it.toMutableList().also {
                val index = it.indexOfFirst { it.key == characterKey }
                val character = it.removeAt(index)
                val selectedIndex = it.indexOfFirst { it.key == currentlySelectedCharacter }
                if (selectedIndex < 0) {
                    it.add(index, character);
                } else {
                    it.add(selectedIndex, character);
                }
            }
        }
    }

    override fun toggleEditMode() {
        _state.update { it.copy(inEditMode = !it.inEditMode)}

    }

    override fun sort() {
        updateCharacterList {
            it.sortedBy { c ->
                when (c) {
                    is Character.Finished -> -c.initiative*2- (if (c.playerCharacter) 0 else 1)
                    is Character.NoInitiativeYet, is Character.Edit -> 1
                }
            }
        }
    }

    override fun delay() {
        _state.update {
            val currentlySelectedCharacter = it.currentlySelectedCharacter
            val currentIndex =
                it.characters.indexOfFirst { it.key == currentlySelectedCharacter }
            if (currentIndex >= 0) {
                var nextIndex = currentIndex + 1

                if (nextIndex >= it.characters.size) {
                    nextIndex = 0
                }
                val nextSelectedCharacter = it.characters[nextIndex].key

                val characters = it.characters.toMutableList().also {
                    val currentCharacter = it[currentIndex]
                    val nextCharacter = it[nextIndex]
                    it[currentIndex] = nextCharacter
                    it[nextIndex] = currentCharacter

                    if (nextIndex == 0) {
                        // It's better to keep the character at the top instead of moving it to the end
                        it.removeAt(currentIndex)
                        it.add(0, nextCharacter)
                    }
                }

                it.copy(
                    characters = characters,
                    currentlySelectedCharacter = nextSelectedCharacter
                )
            } else {
                it
            }
        }
    }

    override fun next() {
        if (state.value.characters.isNotEmpty()) {
            _state.update {
                val currentlySelectedCharacter = it.currentlySelectedCharacter
                if (it.characters.isNotEmpty()) {
                    val currentIndex =
                        it.characters.indexOfFirst { it.key == currentlySelectedCharacter }
                    var nextIndex = currentIndex + 1
                    if (nextIndex >= it.characters.size) {
                        nextIndex = 0;
                    }
                    val nextSelectedCharacter = it.characters[nextIndex].key
                    it.copy(currentlySelectedCharacter = nextSelectedCharacter)
                } else {
                    it
                }
            }
        }
    }
}
