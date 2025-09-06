import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aakira.napier.Napier
import io.github.potsdam_pnp.initiative_tracker.Action
import io.github.potsdam_pnp.initiative_tracker.AddCharacter
import io.github.potsdam_pnp.initiative_tracker.ChangeInitiative
import io.github.potsdam_pnp.initiative_tracker.ChangeName
import io.github.potsdam_pnp.initiative_tracker.ChangePlayerCharacter
import io.github.potsdam_pnp.initiative_tracker.CharacterId
import io.github.potsdam_pnp.initiative_tracker.DeleteCharacter
import io.github.potsdam_pnp.initiative_tracker.ResetAllInitiatives
import io.github.potsdam_pnp.initiative_tracker.State
import io.github.potsdam_pnp.initiative_tracker.Turn
import io.github.potsdam_pnp.initiative_tracker.TurnAction
import io.github.potsdam_pnp.initiative_tracker.crdt.ConflictState
import io.github.potsdam_pnp.initiative_tracker.crdt.Dot
import io.github.potsdam_pnp.initiative_tracker.crdt.ImmutableStringRegister
import io.github.potsdam_pnp.initiative_tracker.crdt.Repository
import io.github.potsdam_pnp.initiative_tracker.crdt.StringOperation
import io.github.potsdam_pnp.initiative_tracker.crdt.VectorClock
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

data class UiCharacter(
  val key: String,
  val name: ImmutableStringRegister? = null,
  val initiative: Int? = null,
  val playerCharacter: Boolean? = null,
  val dead: Boolean = false,
  val isDelayed: Boolean = false,
  val turn: Int = 0,
  val notPlayedYet: Boolean = true,
)

data class EditedCharacterPositions<T>(val selection: Pair<T, T>, val composition: Pair<T, T>?) {
  fun <U> map(f: (T) -> U): EditedCharacterPositions<U> {
    val composition = composition?.let { f(it.first) to f(it.second) }
    return EditedCharacterPositions(
      selection = f(selection.first) to f(selection.second),
      composition = composition,
    )
  }
}

fun EditedCharacterPositions<Int>.asTextFieldValue(text: String): TextFieldValue {
  return TextFieldValue(
    text,
    selection = TextRange(selection.first, selection.second),
    composition = composition?.let { TextRange(it.first, it.second) },
  )
}

fun TextFieldValue.asEditedCharacterPositions(): EditedCharacterPositions<Int> {
  return EditedCharacterPositions(
    selection = selection.start to selection.end,
    composition = composition?.let { it.start to it.end },
  )
}

data class CurrentlyEditedCharacter(val key: String, val positions: EditedCharacterPositions<Dot?>)

data class UiState(
  val characters: List<UiCharacter> = listOf(),
  val currentlySelectedCharacter: String? = null,
  val actions: List<Triple<Dot, ConflictState, TurnAction>> = listOf(),
  val turnConflicts: Boolean = false,
  val currentlyEditedCharacter: CurrentlyEditedCharacter? = null,
  val shownView: ShownView,
)

interface Actions {
  fun deleteCharacter(characterKey: String)

  fun editCharacter(characterKey: String, operation: StringOperation): Dot

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

  fun toggleEditCharacter(key: String)

  fun updateName(characterKey: String, name: ImmutableStringRegister?, text: TextFieldValue)

  fun showView(shownView: ShownView)
}

class Model private constructor(val repository: Repository<Action, State>) : ViewModel(), Actions {
  private val _state = MutableStateFlow(UiState(shownView = ShownView.CHARACTERS))
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

    val versionChannel = Channel<VectorClock>()

    scope.launch { repository.version.collect { versionChannel.send(it) } }

    scope.launch {
      var vc = VectorClock.empty()

      while (true) {
        val s = select {
          versionChannel.onReceive { null to it }
          positionLock.onReceive { it to null }
        }

        val newVc = s.second
        if (newVc != null) vc = s.second!!

        val p = s.first
        if (p != null && vc.contains(p.first) || p == null) {
          _state.update { prevState ->
            val currentlyEditedCharacter =
              if (p == null) prevState.currentlyEditedCharacter
              else prevState.currentlyEditedCharacter?.copy(positions = p.second)
            val result =
              repository.state
                .toUiState(repository, prevState.shownView)
                .copy(currentlyEditedCharacter = currentlyEditedCharacter)
            if (
              result.currentlySelectedCharacter != prevState.currentlySelectedCharacter &&
                result.currentlyEditedCharacter == null
            ) {
              val turns = result.actions.any { it.third != TurnAction.ResolveConflicts }
              val noInitiatives = result.characters.all { it.initiative == null }
              if (turns) {
                result.copy(shownView = ShownView.TURNS)
              } else if (noInitiatives) {
                result.copy(shownView = ShownView.CHARACTERS)
              } else {
                result
              }
            } else {
              result
            }
          }

          if (p != null) {
            doNameActionLock.tryReceive()
          }
        } else {
          positionLock.trySend(p)
        }
      }
    }
  }

  fun addCharacters(data: String?) {
    val characterData = data?.split("&")?.firstOrNull { !it.contains('=') }
    val characterNames = characterData?.split(",") ?: emptyList()
    repository.produce(
      *characterNames
        .flatMap {
          val key = it
          if (!_state.value.characters.any { it.key == key }) {
            listOf(AddCharacter(key), ChangePlayerCharacter(key, true)) +
              it.reversed().map { ChangeName(key, StringOperation.InsertAfter(it, null)) }
          } else {
            listOf()
          }
        }
        .toTypedArray()
    )
  }

  fun addTurn(turnAction: TurnAction) {
    val predecessors = repository.state.turnActions.value.map { it.second }
    if (predecessors.size > 1) return
    val predecessor = predecessors.firstOrNull()
    repository.produce(Turn(turnAction, predecessor?.toDot()))
  }

  override fun deleteCharacter(characterKey: String) {
    repository.produce(DeleteCharacter(characterKey))
  }

  override fun editCharacter(characterKey: String, operation: StringOperation): Dot {
    return repository.produce(ChangeName(characterKey, operation))[0]
  }

  override fun editInitiative(characterKey: String, initiative: String) {
    val initiativeNumber = initiative.toIntOrNull()
    if (initiativeNumber != null) {
      repository.produce(ChangeInitiative(characterKey, initiativeNumber))
    }
  }

  override fun addCharacter() {
    repository.produce(AddCharacter(nextKey()))
  }

  override fun die(characterKey: String) {
    addTurn(TurnAction.Die(CharacterId(characterKey)))
  }

  override fun delay() {
    val current = _state.value.currentlySelectedCharacter
    if (current != null) {
      addTurn(TurnAction.Delay(CharacterId(current)))
    }
  }

  override fun next() {
    val next = repository.state.predictNextTurns(withCurrent = false, repository).firstOrNull()
    if (next != null) {
      addTurn(TurnAction.StartTurn(CharacterId(next.key)))
    }
  }

  override fun togglePlayerCharacter(characterKey: String, playerCharacter: Boolean) {
    repository.produce(ChangePlayerCharacter(characterKey, playerCharacter))
  }

  override fun startTurn(characterKey: String) {
    addTurn(TurnAction.StartTurn(CharacterId(characterKey)))
  }

  override fun finishTurn(characterKey: String) {
    addTurn(TurnAction.FinishTurn(CharacterId(characterKey)))
  }

  override fun pickAction(dot: Dot?) {
    repository.produce(Turn(TurnAction.ResolveConflicts, dot))
  }

  override fun restartEncounter() {
    repository.produce(Turn(TurnAction.ResolveConflicts, null), ResetAllInitiatives)
  }

  private val doNameActionLock: Channel<Unit> = Channel(1)
  private val positionLock: Channel<Pair<Dot?, EditedCharacterPositions<Dot?>>> = Channel(1)

  override fun updateName(
    characterKey: String,
    name: ImmutableStringRegister?,
    text: TextFieldValue,
  ) {
    if (doNameActionLock.trySend(Unit).isSuccess) {
      val c = repository.state.characters[CharacterId(characterKey)]
      if (c == null || c.name.asString() != (name?.asString() ?: "")) {
        doNameActionLock.tryReceive()
        return
      }

      val upd =
        (name ?: ImmutableStringRegister(listOf())).operationsToUpdateTo(
          text.text,
          text.asEditedCharacterPositions(),
        )

      val dots = repository.produce { upd.first(it).map { ChangeName(characterKey, it) } }

      val dotPositions =
        upd.second.map {
          when (it) {
            is ImmutableStringRegister.DotGenerator.FromDot -> it.dot
            is ImmutableStringRegister.DotGenerator.FromResult -> dots[it.index]
          }
        }

      positionLock.trySend(dots.lastOrNull() to dotPositions)
    } else {
      Napier.i("Don't update because of lock")
    }
  }

  override fun toggleEditCharacter(key: String) {
    _state.update {
      if (it.currentlyEditedCharacter?.key == key) {
        it.copy(currentlyEditedCharacter = null)
      } else {
        it.copy(
          currentlyEditedCharacter =
            CurrentlyEditedCharacter(key, EditedCharacterPositions(Pair(null, null), null))
        )
      }
    }
  }

  override fun showView(shownView: ShownView) {
    _state.update { it.copy(shownView = shownView) }
  }
}
