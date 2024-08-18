package io.github.potsdam_pnp.initiative_tracker.state

import io.github.potsdam_pnp.initiative_tracker.AddCharacter
import io.github.potsdam_pnp.initiative_tracker.ChangeInitiative
import io.github.potsdam_pnp.initiative_tracker.ChangeName
import io.github.potsdam_pnp.initiative_tracker.ChangePlayerCharacter
import io.github.potsdam_pnp.initiative_tracker.DeleteCharacter
import io.github.potsdam_pnp.initiative_tracker.ResetAllInitiatives
import UiCharacter
import UiState
import io.github.potsdam_pnp.initiative_tracker.Action
import io.github.potsdam_pnp.initiative_tracker.deserializeAction
import io.github.potsdam_pnp.initiative_tracker.Character
import io.github.potsdam_pnp.initiative_tracker.CharacterId
import io.github.potsdam_pnp.initiative_tracker.Turn
import io.github.potsdam_pnp.initiative_tracker.TurnAction
import io.github.potsdam_pnp.initiative_tracker.crdt.Dot
import io.github.potsdam_pnp.initiative_tracker.crdt.Operation
import io.github.potsdam_pnp.initiative_tracker.crdt.OperationMetadata
import io.github.potsdam_pnp.initiative_tracker.crdt.AbstractState
import io.github.potsdam_pnp.initiative_tracker.crdt.ClientIdentifier
import io.github.potsdam_pnp.initiative_tracker.crdt.ConflictState
import io.github.potsdam_pnp.initiative_tracker.crdt.GrowingListItem
import io.github.potsdam_pnp.initiative_tracker.crdt.Message
import io.github.potsdam_pnp.initiative_tracker.crdt.Register
import io.github.potsdam_pnp.initiative_tracker.crdt.Repository
import io.github.potsdam_pnp.initiative_tracker.crdt.VectorClock
import io.github.potsdam_pnp.initiative_tracker.crdt.show
import io.github.potsdam_pnp.initiative_tracker.serializeAction

class State(
    val characters: MutableMap<CharacterId, Character> = mutableMapOf(),
    var turnActions: Register<Turn> = Register.empty(),
    var initiativeResets: VectorClock = VectorClock.empty()
): AbstractState<Action>() {
    private fun withCharacter(id: CharacterId, op: Character.() -> Character) {
        characters[id] =
            characters.getOrPut(id) { Character(id, Register.empty(), Register.empty(), Register.empty()) }
                .let { it.op() }
    }

    override fun apply(operation: Operation<Action>): List<Dot> {
        when (val op = operation.op) {
            is AddCharacter -> {
                withCharacter(CharacterId(op.id)) { this }
            }
            is ChangeName -> {
                withCharacter(CharacterId(op.id)) {
                    copy(name = name.insert(op.name, operation.metadata))
                }
            }

            is ChangeInitiative ->
                withCharacter(CharacterId(op.id)) {
                    copy(initiative = initiative.insert(op.initiative, operation.metadata))
                }
            is ResetAllInitiatives ->
                initiativeResets = initiativeResets.merge(operation.metadata.clock)


            is ChangePlayerCharacter ->
                withCharacter(CharacterId(op.id)) {
                    copy(
                        playerCharacter = playerCharacter.insert(
                            op.playerCharacter,
                            operation.metadata
                        )
                    )
                }

            is DeleteCharacter ->
                withCharacter(CharacterId(op.id)) {
                    copy(dead = dead.insert(true, operation.metadata))
                }

            is Turn -> {
                turnActions = turnActions.insert(op, operation.metadata)
            }
        }
        return listOf()
    }

    override fun predecessors(op: Action): List<Dot> =
        if (op is Turn) { op.predecessor?.let { listOf(it) } ?: listOf() } else listOf()

    private fun commonLatestTurn(repository: Repository<Action, State>): Turn? {
        return when {
            turnActions.value.isEmpty() -> null
            else -> {
                var resultValue = turnActions
                while (resultValue.value.size > 1) {
                    val firstElement = resultValue.value[0]
                    resultValue = Register(resultValue.value.drop(1))
                    val predecessor = firstElement.first.predecessor
                    if (predecessor != null) {
                        val fetched = repository.fetchVersion(predecessor)!!
                        resultValue = resultValue.insert(fetched.op as Turn, fetched.metadata)
                    } else {
                        return null
                    }
                }
                return resultValue.value[0].first
            }
        }
    }

    private data class CharacterData(val turns: Int = 0, val delayed: Boolean = false)
    fun predictNextTurns(withCurrent: Boolean, repository: Repository<Action, State>): List<UiCharacter> {
        val alreadyPlayedCharactersSet = mutableMapOf<String, CharacterData>()
        val alreadyPlayedCharacters = mutableListOf<String>()

        val updatePlayedCharacters = { characterId: String, characterData: (CharacterData) -> CharacterData ->
            alreadyPlayedCharactersSet[characterId] = characterData(alreadyPlayedCharactersSet.getOrElse(characterId){CharacterData()})
        }

        val current = if (withCurrent) currentTurn(repository) else null
        if (current != null) {
            alreadyPlayedCharactersSet[current] = CharacterData(turns = -1)
        }

        var dying = 0

        var turn = commonLatestTurn(repository)
        while (turn != null) {
            when (val action = turn.turnAction) {
                is TurnAction.StartTurn -> {
                    if (!alreadyPlayedCharactersSet.contains(action.characterId)) {
                        updatePlayedCharacters(action.characterId) { it.copy(turns = it.turns + 1) }
                        alreadyPlayedCharacters.add(alreadyPlayedCharacters.size - dying, action.characterId)
                    }
                    dying = 0
                }
                is TurnAction.Delay -> {
                    if (!alreadyPlayedCharactersSet.contains(action.characterId)) {
                        updatePlayedCharacters(action.characterId) { it.copy(delayed = true) }
                    }
                    updatePlayedCharacters(action.characterId) { it.copy(turns = it.turns - 1)}
                }
                is TurnAction.Die -> {
                    if (!alreadyPlayedCharactersSet.contains(action.characterId)) {
                        alreadyPlayedCharacters.add(action.characterId)
                        dying += 1
                        updatePlayedCharacters(action.characterId) { it }
                    }
                }
                is TurnAction.FinishTurn -> {}

                is TurnAction.ResolveConflicts -> {}
            }

            turn = turn.predecessor?.let { repository.fetchVersion(it) }?.let { it.op as Turn }
        }

        val notYetPlayed = characters.filterKeys { !alreadyPlayedCharactersSet.contains(it.id) }.values.sortedBy {
            -((it.resolvedInitiative(initiativeResets) ?: -100) * 2 + (if (it.resolvedPlayerCharacter() == true) 0 else 1))
        }

        val currentAsList = if (current == null) listOf() else listOf(current)

        return (currentAsList + notYetPlayed.map { it.id.id } + alreadyPlayedCharacters.reversed()).mapNotNull {
            val result = characters[CharacterId(it)]
            if (result?.resolvedDead() == true) null else {
                UiCharacter(
                    key = it,
                    name = result?.resolvedName(),
                    initiative = result?.resolvedInitiative(initiativeResets),
                    playerCharacter = result?.resolvedPlayerCharacter(),
                    dead = result?.resolvedDead() ?: false,
                    isDelayed = alreadyPlayedCharactersSet[it]?.delayed ?: false,
                    turn = alreadyPlayedCharactersSet[it]?.turns ?: 0
                )
            }
        }
    }

    fun currentTurn(repository: Repository<Action, State>): String? {
        var turn = commonLatestTurn(repository)
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

    fun toUiState(repository: Repository<Action, State>): UiState =
        UiState(
            characters = predictNextTurns(withCurrent = true, repository),
            currentlySelectedCharacter = currentTurn(repository),
            actions = turnActions.show {
                val result = repository.fetchVersion(it)!!
                Pair(result.op as Turn, result.metadata)
            },
            turnConflicts = turnActions.value.size > 1
        )
}

object Encoders {
    private fun vectorClockEncode(vectorClock: VectorClock): String {
        return vectorClock.clock.toList().joinToString("~") { "${it.first.name}:${it.second}" }
    }

    private fun vectorClockDecode(s: String): VectorClock {
        if (s == "") return VectorClock(mapOf())
        return VectorClock(s.split("~").map {
            val parts = it.split(":")
            ClientIdentifier(parts[0]) to parts[1].toInt()
        }.toMap())
    }


    fun encode(msg: Message<Action>): String {
        return when (msg) {
            is Message.CurrentState ->
                "c" + vectorClockEncode(msg.vectorClock)
            is Message.StopConnection -> "s"
            is Message.RequestVersions ->
                "r" + vectorClockEncode(msg.vectorClock) +
                        "}" + msg.dots.joinToString("}") { it.clientIdentifier.name + ":" + it.position }
            is Message.SendVersions ->
                "v" + vectorClockEncode(msg.vectorClock) + "}" +
                        msg.versions.joinToString("}") { actionEncode(it) }
        }
    }

    fun decode(s: String): Message<Action> {
        val initial = s[0]
        val rest = s.substring(1)

        when (initial) {
            'c' -> {
                return Message.CurrentState(vectorClockDecode(rest))
            }

            's' -> {
                return Message.StopConnection(Unit)
            }

            'r' -> {
                val parts = rest.split("}")
                val clock = vectorClockDecode(parts[0])
                val dots = parts.drop(1).map {
                    val parts = it.split(":")
                    Dot(ClientIdentifier(parts[0]), parts[1].toInt())
                }
                return Message.RequestVersions(clock, dots)
            }

            'v' -> {
                val parts = rest.split("}")
                val clock = vectorClockDecode(parts[0])
                val versions = parts.drop(1).map { actionDecode(it) }
                return Message.SendVersions(clock, versions)
            }
            else -> throw Exception("Unknown message type $initial")
        }
    }

    private fun actionEncode(action: Operation<Action>): String {
        val intAction = serializeAction(action.op)
        val clock = vectorClockEncode(action.metadata.clock)
        val client = action.metadata.client.name

        return "${clock}%${client}%${intAction}"
    }

    private fun actionDecode(s: String): Operation<Action> {
        val parts = s.split("%", limit=3)
        val clock = vectorClockDecode(parts[0])
        val client = ClientIdentifier(parts[1])
        val action = deserializeAction(parts[2])!!
        return Operation(OperationMetadata(clock, client), action)
    }
}