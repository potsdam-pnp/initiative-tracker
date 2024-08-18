import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.potsdam_pnp.initiative_tracker.Action
import io.github.potsdam_pnp.initiative_tracker.AddCharacter
import io.github.potsdam_pnp.initiative_tracker.ChangeInitiative
import io.github.potsdam_pnp.initiative_tracker.ChangeName
import io.github.potsdam_pnp.initiative_tracker.ChangePlayerCharacter
import io.github.potsdam_pnp.initiative_tracker.DeleteCharacter
import io.github.potsdam_pnp.initiative_tracker.ResetAllInitiatives
import io.github.potsdam_pnp.initiative_tracker.Turn
import io.github.potsdam_pnp.initiative_tracker.TurnAction
import io.github.potsdam_pnp.initiative_tracker.crdt.ConflictState
import io.github.potsdam_pnp.initiative_tracker.crdt.Repository
import io.github.potsdam_pnp.initiative_tracker.state.State
import io.github.potsdam_pnp.initiative_tracker.crdt.Dot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class UiCharacter(
    val key: String,
    val name: String? = null,
    val initiative: Int? = null,
    val playerCharacter: Boolean? = null,
    val dead: Boolean = false,
    val isDelayed: Boolean = false,
    val turn: Int = 0
)

data class UiState(
    val characters: List<UiCharacter> = listOf(),
    val currentlySelectedCharacter: String? = null,
    val actions: List<Triple<Dot, ConflictState, TurnAction>> = listOf(),
    val turnConflicts: Boolean = false
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


class Model private constructor (val repository: Repository<Action, State>) : ViewModel(), Actions {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    @OptIn(ExperimentalStdlibApi::class)
    private val thisDevice = Random.nextInt().toHexString().takeLast(4)
    private var lastKey: Int = 0
    private fun nextKey(): String {
        lastKey += 1
        return "${thisDevice}$lastKey"
    }

    constructor(repository: Repository<Action, State>, data: String?) : this(repository) {
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
                    repository.state.toUiState(repository)
                }
            }
        }
    }

    fun addCharacters(data: String?) {
        val characterData = data?.split("&")?.firstOrNull { !it.contains('=') }
        val characterNames = characterData?.split(",") ?: emptyList()
        repository.produce(
            characterNames.flatMap {
                val key = it
                if (!_state.value.characters.any { it.key == key }) {
                    listOf(
                        AddCharacter(key),
                        ChangeName(key, it),
                        ChangePlayerCharacter(key, true)
                    )
                } else {
                    listOf()
                }
            }
        )
    }
/*
    private fun addActions(vararg actions: Action) {
        repository.produce(actions)

            /*.mapNotNull {
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
        }.toList())*/
    }*/

    fun addTurn(turnAction: TurnAction) {
        val predecessors = repository.state.turnActions.value.map { it.second }
        if (predecessors.size > 1) return
        val predecessor = predecessors.firstOrNull()
        repository.produce(listOf(Turn(turnAction, predecessor?.toDot())))

    }

    override fun deleteCharacter(characterKey: String) {
        repository.produce(listOf(DeleteCharacter(characterKey)))
    }

    override fun editCharacter(characterKey: String, name: String) {
        repository.produce(listOf(ChangeName(characterKey, name)))
    }

    override fun editInitiative(characterKey: String, initiative: String) {
        val initiativeNumber = initiative.toIntOrNull()
        if (initiativeNumber != null) {
            repository.produce(listOf(ChangeInitiative(characterKey, initiativeNumber)))
        }
    }

    override fun addCharacter() {
        repository.produce(listOf(AddCharacter(nextKey())))
    }

    override fun die(characterKey: String) {
        addTurn(TurnAction.Die(characterKey))
    }

    override fun delay() {
        val current = _state.value.currentlySelectedCharacter
        if (current != null) {
            addTurn(TurnAction.Delay(current))
        }
    }

    override fun next() {
        val next = repository.state.predictNextTurns(withCurrent = false, repository).firstOrNull()
        if (next != null) {
            addTurn(TurnAction.StartTurn(next.key))
        }
    }

    override fun togglePlayerCharacter(characterKey: String, playerCharacter: Boolean) {
        repository.produce(listOf(ChangePlayerCharacter(characterKey, playerCharacter)))
    }

    override fun startTurn(characterKey: String) {
        addTurn(TurnAction.StartTurn(characterKey))
    }

    override fun finishTurn(characterKey: String) {
        addTurn(TurnAction.FinishTurn(characterKey))
    }

    override fun pickAction(dot: Dot?) {
        repository.produce(listOf(Turn(TurnAction.ResolveConflicts, dot)))
    }

    override fun restartEncounter() {
        repository.produce(
            listOf(
                Turn(TurnAction.ResolveConflicts, null),
                ResetAllInitiatives
            )
        )
    }
}
