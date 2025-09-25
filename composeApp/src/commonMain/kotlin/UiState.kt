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
import io.github.potsdam_pnp.initiative_tracker.crdt.ConflictTree
import io.github.potsdam_pnp.initiative_tracker.crdt.Dot
import io.github.potsdam_pnp.initiative_tracker.crdt.ImmutableStringRegister
import io.github.potsdam_pnp.initiative_tracker.crdt.Repository
import io.github.potsdam_pnp.initiative_tracker.crdt.StringOperation
import io.github.potsdam_pnp.initiative_tracker.crdt.VectorClock
import io.github.potsdam_pnp.initiative_tracker.crdt.conflicts
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

data class UiCharacter(
  val key: CharacterId,
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

data class CurrentlyEditedCharacter(
  val key: CharacterId,
  val positions: EditedCharacterPositions<Dot>,
)

data class UiState(
  val characters: List<UiCharacter> = listOf(),
  val currentlySelectedCharacter: CharacterId? = null,
  val turnConflicts: ConflictTree<Turn>,
  val currentlyEditedCharacter: CurrentlyEditedCharacter? = null,
  val shownView: ShownView,
  val knownPlayerCharacters: List<String?>,
)

interface Actions {
  fun deleteCharacter(characterKey: CharacterId)

  fun editCharacter(characterKey: CharacterId, operation: StringOperation): Dot

  fun editInitiative(characterKey: CharacterId, initiative: String)

  fun addCharacter(playerCharacter: Boolean, name: String? = null)

  fun die(characterKey: CharacterId)

  fun delay()

  fun next()

  fun togglePlayerCharacter(characterKey: CharacterId, playerCharacter: Boolean)

  fun startTurn(characterKey: CharacterId)

  fun finishTurn(characterKey: CharacterId)

  fun pickAction(dot: Dot?)

  fun restartEncounter()

  fun toggleEditCharacter(key: CharacterId)

  fun updateName(characterKey: CharacterId, name: ImmutableStringRegister?, text: TextFieldValue)

  fun showView(shownView: ShownView)

  fun addPlayerCharacters(players: List<String>)
}

interface PersistData {
  fun fetchKnownPlayerCharacters(): List<String>

  fun storeKnownPlayerCharacters(data: List<String>)
}

class Model
private constructor(val repository: Repository<Action, State>, val persist: PersistData? = null) :
  ViewModel(), Actions {
  private val _state =
    MutableStateFlow(
      UiState(
        shownView = ShownView.CHARACTERS,
        knownPlayerCharacters = listOf(null) + (persist?.fetchKnownPlayerCharacters() ?: listOf()),
        turnConflicts = ConflictTree(listOf(), null),
      )
    )
  val state: StateFlow<UiState> = _state

  @OptIn(ExperimentalStdlibApi::class)
  private val thisDevice = Random.nextInt().toHexString().takeLast(4)

  constructor(
    repository: Repository<Action, State>,
    persist: PersistData?,
    data: String?,
  ) : this(repository, persist) {

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
                .toUiState(repository, prevState.shownView, prevState.knownPlayerCharacters)
                .copy(currentlyEditedCharacter = currentlyEditedCharacter)
            if (
              result.currentlySelectedCharacter != prevState.currentlySelectedCharacter &&
                result.currentlyEditedCharacter == null
            ) {
              val turns = result.turnConflicts.m != null
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

  fun addTurn(turnAction: TurnAction) {
    val c =
      repository.state.turnActions.conflicts(skip = { it == TurnAction.ResolveConflicts }) {
        repository.fetchVersion(it)!!.let { (it.op as Turn) to it.metadata }
      }
    if (c.children.isNotEmpty()) {
      return
    }
    repository.produce(Turn(turnAction, c.m?.second?.toDot()))
  }

  override fun deleteCharacter(characterKey: CharacterId) {
    repository.produce(DeleteCharacter(characterKey))
  }

  override fun editCharacter(characterKey: CharacterId, operation: StringOperation): Dot {
    return repository.produce(ChangeName(operation))[0]
  }

  override fun editInitiative(characterKey: CharacterId, initiative: String) {
    val initiativeNumber = initiative.toIntOrNull()
    if (initiativeNumber != null) {
      repository.produce(ChangeInitiative(characterKey, initiativeNumber))
    }
  }

  override fun addCharacter(playerCharacter: Boolean, name: String?) {
    val versions = { d: (Int) -> Dot ->
      val key = CharacterId(d(0))
      val result = mutableListOf(AddCharacter, ChangePlayerCharacter(key, playerCharacter))
      if (name != null) {
        var dot: Dot = d(0)
        for (c in name) {
          result.add(ChangeName(StringOperation.InsertAfter(c, dot)))
          dot = d(result.size - 1)
        }
      }
      result
    }
    val dots = repository.produce(versions)
    val key = CharacterId(dots[0])
    toggleEditCharacter(key)
  }

  override fun die(characterKey: CharacterId) {
    addTurn(TurnAction.Die(characterKey))
  }

  override fun delay() {
    val current = _state.value.currentlySelectedCharacter
    if (current != null) {
      addTurn(TurnAction.Delay(current))
    }
  }

  override fun next() {
    val next =
      repository.state.predictNextTurns(withCurrent = false, repository = repository).firstOrNull()
    if (next != null) {
      addTurn(TurnAction.StartTurn(next.key))
    }
  }

  override fun togglePlayerCharacter(characterKey: CharacterId, playerCharacter: Boolean) {
    repository.produce(ChangePlayerCharacter(characterKey, playerCharacter))
  }

  override fun startTurn(characterKey: CharacterId) {
    addTurn(TurnAction.StartTurn(characterKey))
  }

  override fun finishTurn(characterKey: CharacterId) {
    addTurn(TurnAction.FinishTurn(characterKey))
  }

  override fun pickAction(dot: Dot?) {
    repository.produce(Turn(TurnAction.ResolveConflicts, dot))
  }

  override fun restartEncounter() {
    repository.produce(Turn(TurnAction.ResolveConflicts, null), ResetAllInitiatives)
  }

  private val doNameActionLock: Channel<Unit> = Channel(1)
  private val positionLock: Channel<Pair<Dot, EditedCharacterPositions<Dot>>> = Channel(1)

  override fun updateName(
    characterKey: CharacterId,
    name: ImmutableStringRegister?,
    text: TextFieldValue,
  ) {
    if (doNameActionLock.trySend(Unit).isSuccess) {
      val c = repository.state.characters[characterKey]
      if (c == null || c.name.asString() != (name?.asString() ?: "")) {
        doNameActionLock.tryReceive()
        return
      }

      val upd =
        (name ?: ImmutableStringRegister(characterKey.dot, listOf())).operationsToUpdateTo(
          text.text,
          text.asEditedCharacterPositions(),
        )

      val dots = repository.produce { upd.first(it).map { ChangeName(it) } }

      val dotPositions =
        upd.second.map {
          when (it) {
            is ImmutableStringRegister.DotGenerator.FromDot -> it.dot
            is ImmutableStringRegister.DotGenerator.FromResult -> dots[it.index]
          }
        }

      positionLock.trySend((dots.lastOrNull() ?: characterKey.dot) to dotPositions)
    } else {
      Napier.i("Don't update because of lock")
    }
  }

  override fun toggleEditCharacter(key: CharacterId) {
    _state.update {
      if (it.currentlyEditedCharacter?.key == key) {
        it.copy(currentlyEditedCharacter = null)
      } else {
        it.copy(
          currentlyEditedCharacter =
            CurrentlyEditedCharacter(key, EditedCharacterPositions(Pair(key.dot, key.dot), null))
        )
      }
    }
  }

  override fun showView(shownView: ShownView) {
    _state.update { it.copy(shownView = shownView) }
  }

  override fun addPlayerCharacters(players: List<String>) {
    _state.update { state ->
      val toAdd = players.filter { !state.knownPlayerCharacters.contains(it) }
      state.copy(knownPlayerCharacters = state.knownPlayerCharacters + toAdd)
    }
    persist?.storeKnownPlayerCharacters(state.value.knownPlayerCharacters.filterNotNull())
  }
}
