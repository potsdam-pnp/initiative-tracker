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

data class Character(
    val key: String,
    val name: String? = null,
    val initiative: Int? = null,
    val playerCharacter: Boolean? = null,
    val dead: Boolean = false,
    val isDelayed: Boolean = false
)

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
    fun sort()
    fun delay()
    fun next()
    fun togglePlayerCharacter(key: String)
}


class Model private constructor(s: State2) : ViewModel(), Actions {
    private val _state = MutableStateFlow(s)
    val state = _state.map { it.toState() }

    private var lastKey: Int = 0
    private fun nextKey(): String {
        lastKey += 1
        return lastKey.toString()
    }

    constructor(data: String?) : this(State2(listOf())) {
        val characterNames = data?.split(",") ?: emptyList()
        addActions(
            *characterNames.flatMap {
                val key = nextKey()
                listOf(
                    AddCharacter(key),
                    ChangeName(key, it),
                    ChangePlayerCharacter(key, true)
                )
            }.toTypedArray()
        )
    }

    private fun addActions(vararg actions: ActionState) {
        _state.update {
            it.copy(
                actions = it.actions + actions
            )
        }
    }

    override fun deleteCharacter(characterKey: String) {
        addActions(DeleteCharacter(characterKey))
    }

    override fun editCharacter(characterKey: String, name: String) {
        addActions(ChangeName(characterKey, name))
    }

    override fun editInitiative(characterKey: String, initiative: String) {
        val initiativeNumber = initiative.toIntOrNull()
        if (initiativeNumber != null) {
            addActions(ChangeInitiative(characterKey, initiativeNumber))
        }
    }

    override fun toggleEditCharacter(characterKey: String) {
        // TODO
    }

    override fun moveCharacterUp(characterKey: String) {
        // TODO
    }

    override fun moveCharacterDown(characterKey: String) {
        // TODO
    }

    override fun addCharacter() {
        addActions(AddCharacter(nextKey()))
    }

    override fun die(characterKey: String) {
        addActions(Die(characterKey))
    }

    override fun sort() {
       // TODO
    }

    override fun delay() {
        val current = _state.value.currentTurn()
        if (current != null) {
            addActions(Delay(current))
        }
    }

    override fun next() {
        val next = _state.value.predictNextTurns(withCurrent = false).firstOrNull()
        if (next != null) {
            addActions(StartTurn(next.key))
        }
    }

    override fun togglePlayerCharacter(characterKey: String) {
        addActions(ChangePlayerCharacter(characterKey, true)) // TODO Toggle instead of set to false
    }
}
