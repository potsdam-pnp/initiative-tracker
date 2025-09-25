package io.github.potsdam_pnp.initiative_tracker

import ShownView
import UiCharacter
import UiState
import io.github.potsdam_pnp.initiative_tracker.crdt.AbstractState
import io.github.potsdam_pnp.initiative_tracker.crdt.ConflictTree
import io.github.potsdam_pnp.initiative_tracker.crdt.Dot
import io.github.potsdam_pnp.initiative_tracker.crdt.Operation
import io.github.potsdam_pnp.initiative_tracker.crdt.Register
import io.github.potsdam_pnp.initiative_tracker.crdt.Repository
import io.github.potsdam_pnp.initiative_tracker.crdt.StringOperation
import io.github.potsdam_pnp.initiative_tracker.crdt.StringRegister
import io.github.potsdam_pnp.initiative_tracker.crdt.VectorClock
import io.github.potsdam_pnp.initiative_tracker.crdt.conflicts

data class Character(
  val id: CharacterId,
  val name: StringRegister = StringRegister.empty(id.dot),
  val initiative: Register<Int> = Register.empty(),
  val playerCharacter: Register<Boolean> = Register.empty(),
  val dead: Register<Boolean> = Register.empty(),
) {
  fun resolvedInitiative(initiativeResets: VectorClock): Int? =
    if (initiative.value.size == 1 && initiative.value[0].second.clock.contains(initiativeResets))
      initiative.value.first().first
    else null

  fun resolvedPlayerCharacter() =
    playerCharacter.value.let {
      when {
        it.isEmpty() -> null
        it.all { it.first } -> true
        it.all { !it.first } -> false
        else -> null
      }
    }

  fun resolvedDead() =
    dead.value.let {
      when {
        it.isEmpty() -> false
        it.any { it.first } -> true
        else -> false
      }
    }
}

class StringOperationLookup private constructor(val inside: MutableMap<Dot, Value>) {
  sealed class Value {
    class Processed(val characterId: CharacterId) : Value()

    class Unprocessed(val dependencies: MutableList<Operation<StringOperation>>) : Value()
  }

  constructor() : this(mutableMapOf()) {}

  fun insertCharacter(dot: Dot) {
    inside[dot] = Value.Processed(CharacterId(dot))
  }

  fun insert(
    value: Operation<StringOperation>,
    doProcess: (CharacterId, Operation<StringOperation>) -> Unit,
  ) {
    val dot = value.op.after
    val v = inside.getOrPut(dot) { Value.Unprocessed(mutableListOf()) }
    when (v) {
      is Value.Unprocessed -> {
        v.dependencies.add(value)
      }
      is Value.Processed -> {
        return process(v.characterId, value) { doProcess(v.characterId, it) }
      }
    }
  }

  private fun process(
    characterId: CharacterId,
    value: Operation<StringOperation>,
    doProcess: (Operation<StringOperation>) -> Unit,
  ) {
    val toProcess = mutableListOf(value)

    while (toProcess.isNotEmpty()) {
      val next = toProcess.removeAt(toProcess.lastIndex)
      when (val v = inside[next.dot]) {
        is Value.Unprocessed -> toProcess.addAll(v.dependencies)
        null -> {}
        is Value.Processed -> throw IllegalStateException()
      }
      inside[next.dot] = Value.Processed(characterId)
      doProcess(next)
    }
  }
}

class State(
  val characters: MutableMap<CharacterId, Character> = mutableMapOf(),
  var turnActions: Register<Turn> = Register.empty(),
  var initiativeResets: VectorClock = VectorClock.empty(),
  val stringOperationsLookup: StringOperationLookup = StringOperationLookup(),
) : AbstractState<Action>() {
  private fun withCharacter(id: CharacterId, op: Character.() -> Character) {
    characters[id] =
      characters
        .getOrPut(id) {
          Character(id, StringRegister.empty(id.dot), Register.empty(), Register.empty())
        }
        .op()
  }

  override fun apply(operation: Operation<Action>): List<Dot> {
    when (val op = operation.op) {
      is AddCharacter -> {
        withCharacter(CharacterId(operation.dot)) { this }
        stringOperationsLookup.insertCharacter(operation.dot)
      }
      is ChangeName -> {
        stringOperationsLookup.insert(Operation(operation.metadata, op.operation)) { characterId, o
          ->
          withCharacter(characterId) {
            name.insert(o)
            this
          }
        }
      }

      is ChangeInitiative ->
        withCharacter(op.id) {
          copy(initiative = initiative.insert(op.initiative, operation.metadata))
        }
      is ResetAllInitiatives -> initiativeResets = initiativeResets.merge(operation.metadata.clock)

      is ChangePlayerCharacter ->
        withCharacter(op.id) {
          copy(playerCharacter = playerCharacter.insert(op.playerCharacter, operation.metadata))
        }

      is DeleteCharacter ->
        withCharacter(op.id) { copy(dead = dead.insert(true, operation.metadata)) }

      is Turn -> {
        turnActions = turnActions.insert(op, operation.metadata)
      }
    }
    return listOf()
  }

  override fun predecessors(op: Action): List<Dot> =
    if (op is Turn) {
      op.predecessor?.let { listOf(it) } ?: listOf()
    } else listOf()

  private fun commonLatestTurn(
    repository: Repository<Action, State>,
    conflictTree: ConflictTree<Turn>,
  ): Turn? {
    return conflictTree.m?.first
  }

  private data class CharacterData(
    val turns: Int = 0,
    val delayed: Boolean = false,
    val alreadyPlayed: Boolean = false,
  )

  fun predictNextTurns(
    withCurrent: Boolean,
    conflictTree: ConflictTree<Turn>? = null,
    repository: Repository<Action, State>,
  ): List<UiCharacter> {
    val conflictTree1 =
      conflictTree
        ?: turnActions.conflicts(skip = { it == TurnAction.ResolveConflicts }) {
          repository.fetchVersion(it)!!.let { (it.op as Turn) to it.metadata }
        }
    val state = mutableMapOf<CharacterId, CharacterData>()
    val alreadyPlayedCharacters = mutableListOf<CharacterId>()

    val updatePlayedCharacters =
      { characterId: CharacterId, characterData: (CharacterData) -> CharacterData ->
        state[characterId] = characterData(state.getOrElse(characterId) { CharacterData() })
      }
    val alreadyPlayed = { characterId: CharacterId ->
      state.get(characterId)?.alreadyPlayed == true
    }

    val current = if (withCurrent) currentTurn(repository) else null
    if (current != null) {
      state[current] = CharacterData(turns = -1, alreadyPlayed = true)
    }

    var dying = 0

    var turn = commonLatestTurn(repository, conflictTree1)
    while (turn != null) {
      when (val action = turn.turnAction) {
        is TurnAction.StartTurn -> {
          if (!alreadyPlayed(action.characterId)) {
            alreadyPlayedCharacters.add(alreadyPlayedCharacters.size - dying, action.characterId)
          }
          updatePlayedCharacters(action.characterId) {
            it.copy(turns = it.turns + 1, alreadyPlayed = true)
          }
          dying = 0
        }
        is TurnAction.Delay -> {
          if (!alreadyPlayed(action.characterId)) {
            updatePlayedCharacters(action.characterId) { it.copy(delayed = true) }
          }
          updatePlayedCharacters(action.characterId) { it.copy(turns = it.turns - 1) }
        }
        is TurnAction.Die -> {
          if (!alreadyPlayed(action.characterId)) {
            alreadyPlayedCharacters.add(action.characterId)
            dying += 1
            updatePlayedCharacters(action.characterId) { it.copy(alreadyPlayed = true) }
          }
        }
        is TurnAction.FinishTurn -> {}

        is TurnAction.ResolveConflicts -> {}
      }

      turn = turn.predecessor?.let { repository.fetchVersion(it) }?.let { it.op as Turn }
    }

    val notYetPlayed =
      characters
        .filterKeys { !alreadyPlayed(it) }
        .values
        .sortedBy {
          -((it.resolvedInitiative(initiativeResets) ?: -100) * 2 +
            (if (it.resolvedPlayerCharacter() == true) 0 else 1))
        }

    val currentAsList = if (current == null) listOf() else listOf(current)

    return (currentAsList + notYetPlayed.map { it.id } + alreadyPlayedCharacters.reversed())
      .mapNotNull {
        val result = characters[it]
        if (result?.resolvedDead() == true) null
        else {
          UiCharacter(
            key = it,
            name = result?.name?.toImmutableStringRegister(),
            initiative = result?.resolvedInitiative(initiativeResets),
            playerCharacter = result?.resolvedPlayerCharacter(),
            dead = result?.resolvedDead() ?: false,
            isDelayed = state[it]?.delayed ?: false,
            turn = state[it]?.turns ?: 0,
            notPlayedYet = !(state[it]?.alreadyPlayed ?: false),
          )
        }
      }
  }

  fun currentTurn(repository: Repository<Action, State>): CharacterId? {
    val conflictTree1 =
      turnActions.conflicts(skip = { it == TurnAction.ResolveConflicts }) {
        repository.fetchVersion(it)!!.let { (it.op as Turn) to it.metadata }
      }
    var turn = commonLatestTurn(repository, conflictTree1)
    while (turn != null) {
      when (val action = turn.turnAction) {
        is TurnAction.StartTurn -> return action.characterId
        is TurnAction.Delay -> return null
        is TurnAction.FinishTurn -> return null
        else -> {}
      }
      turn = turn.predecessor?.let { repository.fetchVersion(it) }?.let { it.op as Turn }
    }
    return null
  }

  fun toUiState(
    repository: Repository<Action, State>,
    shownView: ShownView,
    knownPlayerCharacters: List<String?>,
  ): UiState {
    val turnConflicts =
      turnActions.conflicts(skip = { it == TurnAction.ResolveConflicts }) {
        repository.fetchVersion(it)!!.let { (it.op as Turn) to it.metadata }
      }
    return UiState(
      characters = predictNextTurns(withCurrent = true, turnConflicts, repository),
      currentlySelectedCharacter = currentTurn(repository),
      turnConflicts = turnConflicts,
      shownView = shownView,
      knownPlayerCharacters = knownPlayerCharacters,
    )
  }
}
