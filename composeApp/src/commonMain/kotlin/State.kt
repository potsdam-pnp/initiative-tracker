import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.potsdam_pnp.initiative_tracker.ActionState
import io.github.potsdam_pnp.initiative_tracker.ActionWrapper
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
import io.github.potsdam_pnp.initiative_tracker.crdt.Repository
import io.github.potsdam_pnp.initiative_tracker.state.State
import io.github.potsdam_pnp.initiative_tracker.crdt.Dot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val actions: List<Triple<Dot, ConflictState, ActionState>> = listOf(),
    val turnConflicts: Boolean
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
    fun pickAction(dot: Dot?)
    fun restartEncounter()
}


class Model private constructor (val repository: Repository<ActionWrapper, State>) : ViewModel(), Actions {
    private val _state = MutableStateFlow(State2(listOf(), turnActions = listOf()))
    val state = _state.map { it.toState() }

    @OptIn(ExperimentalStdlibApi::class)
    private val thisDevice = Random.nextInt().toHexString().takeLast(4)
    private var lastKey: Int = 0
    private fun nextKey(): String {
        lastKey += 1
        return "${thisDevice}$lastKey"
    }

    constructor(repository: Repository<ActionWrapper, State>, data: String?) : this(repository) {
        addCharacters(data)

        val scope =
            if (getPlatform().name.startsWith("Android")) {
                viewModelScope
            } else {
                CoroutineScope(Dispatchers.Unconfined)
            }

        scope.launch {
            repository.version.collect {
                _state.update {
                    repository.state.toState2(repository)
                }
            }
        }
    }

    fun addCharacters(data: String?) {
        val characterData = data?.split("&")?.firstOrNull { !it.contains('=') }
        val characterNames = characterData?.split(",") ?: emptyList()
        addActions(
            *characterNames.flatMap {
                val key = it
                if (!_state.value.actions.contains(AddCharacter(key))) {
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
        repository.produce(actions.mapNotNull {
            if (it is StartTurn || it is FinishTurn || it is Die || it is Delay) {
                val predecessors = repository.state.turnActions.value.map { it.second }
                if (predecessors.isEmpty()) {
                    ActionWrapper(it, null)
                } else if (predecessors.size == 1) {
                    ActionWrapper(it, predecessors.first().toDot())
                } else {
                    null
                }
            } else {
                ActionWrapper(it, null)
            }
        }.toList())
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

    override fun pickAction(dot: Dot?) {
        repository.produce(
            listOf(
                ActionWrapper(ResolveConflict, dot)
            )
        )
    }

    override fun restartEncounter() {
        repository.produce(
            listOf(
                ActionWrapper(ResolveConflict, null),
                ActionWrapper(ResetAllInitiatives, null)
            )
        )
    }
}
