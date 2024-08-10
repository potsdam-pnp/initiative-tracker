import androidx.lifecycle.ViewModel
import io.github.potsdam_pnp.initiative_tracker.state.CharacterId
import io.github.potsdam_pnp.initiative_tracker.state.State
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.random.Random

data class Character(
    val key: String,
    val name: String? = null,
    val initiative: Int? = null,
    val playerCharacter: Boolean? = null,
    val dead: Boolean = false,
    val isDelayed: Boolean = false,
    val turn: Int = 0
)

data class State(
    val characters: List<Character> = listOf(),
    val currentlySelectedCharacter: String? = null,
    val actions: List<ActionState> = listOf()
)

interface Actions {
    fun deleteCharacter(characterKey: String)
    fun editCharacter(characterKey: String, name: String)
    fun editInitiative(characterKey: String, initiative: String)
    fun addCharacter()
    fun die(characterKey: String)
    fun delay()
    fun next()
    fun togglePlayerCharacter(characterKey: String, playerCharacter: Boolean)
    fun startTurn(characterKey: String)
    fun finishTurn(characterKey: String)
    fun deleteAction(index: Int)
    fun deleteNewerActions(index: Int)
    fun receiveActions(actions: List<ActionState>)
}


class Model private constructor(s: State) : ViewModel(), Actions {
    private val _state = MutableStateFlow(s)
    val state = _state.map { it.toState2().toState() }

    @OptIn(ExperimentalStdlibApi::class)
    private val thisDevice = Random.nextInt().toHexString().takeLast(4)
    private var lastKey: Int = 0
    private fun nextKey(): String {
        lastKey += 1
        return "${thisDevice}$lastKey"
    }

    constructor(data: String?) : this(io.github.potsdam_pnp.initiative_tracker.state.State()) {
        addCharacters(data)
    }

    fun addCharacters(data: String?) {
        val characterData = data?.split("&")?.firstOrNull { !it.contains('=') }
        val characterNames = characterData?.split(",") ?: emptyList()
        addActions(
            *characterNames.flatMap {
                val key = it
                if (!_state.value.toState2().actions.contains(AddCharacter(key))) {
                    listOf(
                        AddCharacter(key),
                        ChangeName(key, it),
                        ChangePlayerCharacter(key, true)
                    )
                } else {
                    listOf()
                }
            }.toTypedArray()
        )
    }

    private fun addActions(vararg actions: ActionState) {
        _state.update {
            for (action in actions) {
                it.apply(
                    when (action) {
                        is AddCharacter -> io.github.potsdam_pnp.initiative_tracker.state.ChangeCharacterName(
                            metadata, CharacterId(action.id), "")
                        is ChangeName -> io.github.potsdam_pnp.initiative_tracker.state.ChangeCharacterName(action.id, action.name)
                        is ChangeInitiative -> io.github.potsdam_pnp.initiative_tracker.state.ChangeCharacterInitiative(action.id, action.initiative)
                        is ChangePlayerCharacter -> io.github.potsdam_pnp.initiative_tracker.state.ChangePlayerCharacter(action.id, action.playerCharacter)
                        is DeleteCharacter -> io.github.potsdam_pnp.initiative_tracker.state.DeleteCharacter(action.id)
                        is StartTurn -> io.github.potsdam_pnp.initiative_tracker.state.DoTurn(action.id)
                        is Delay -> io.github.potsdam_pnp.initiative_tracker.state.DoTurn(action.id)
                        is Die -> io.github.potsdam_pnp.initiative_tracker.state.DoTurn(action.id)
                        is FinishTurn -> io.github.potsdam_pnp.initiative_tracker.state.DoTurn(action.id)

                    }
                )
            }
            it
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

    override fun addCharacter() {
        addActions(AddCharacter(nextKey()))
    }

    override fun die(characterKey: String) {
        addActions(Die(characterKey))
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

    override fun togglePlayerCharacter(characterKey: String, playerCharacter: Boolean) {
        addActions(ChangePlayerCharacter(characterKey, playerCharacter)) // TODO Toggle instead of set to false
    }

    override fun startTurn(characterKey: String) {
        addActions(StartTurn(characterKey))
    }

    override fun finishTurn(characterKey: String) {
        addActions(FinishTurn(characterKey))
    }

    override fun deleteAction(index: Int) {
        _state.update {
            it.copy(
                actions = it.actions.toMutableList().also {
                    it.removeAt(index)
                }
            )
        }
    }

    override fun deleteNewerActions(index: Int) {
        _state.update {
            it.copy(
                actions = it.actions.dropLast(it.actions.size - index)
            )
        }
    }

    override fun receiveActions(actions: List<ActionState>) {
        _state.update {
            it.copy(
                actions = actions
            )
        }
    }
}
