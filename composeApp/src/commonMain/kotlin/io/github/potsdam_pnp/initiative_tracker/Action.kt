package io.github.potsdam_pnp.initiative_tracker

import io.github.potsdam_pnp.initiative_tracker.crdt.ClientIdentifier
import io.github.potsdam_pnp.initiative_tracker.crdt.Dot
import io.github.potsdam_pnp.initiative_tracker.crdt.GrowingListItem
import io.github.potsdam_pnp.initiative_tracker.crdt.Message
import io.github.potsdam_pnp.initiative_tracker.crdt.Operation
import io.github.potsdam_pnp.initiative_tracker.crdt.OperationMetadata
import io.github.potsdam_pnp.initiative_tracker.crdt.Register
import io.github.potsdam_pnp.initiative_tracker.crdt.VectorClock

sealed class Action()

sealed class TurnAction {
    data class StartTurn(val characterId: CharacterId): TurnAction()
    data class FinishTurn(val characterId: CharacterId): TurnAction()
    data class Die(val characterId: CharacterId): TurnAction()
    data class Delay(val characterId: CharacterId): TurnAction()
    object ResolveConflicts: TurnAction()
}

data class Turn(
    val turnAction: TurnAction,
    override val predecessor: Dot?
): Action(), GrowingListItem<TurnAction> {
    override val item: TurnAction
        get() = turnAction
}


data class CharacterId(val id: String)
data class Character(
    val id: CharacterId,
    val name: Register<String> = Register.empty(),
    val initiative: Register<Int> = Register.empty(),
    val playerCharacter: Register<Boolean> = Register.empty(),
    val dead: Register<Boolean> = Register.empty()
) {
    fun resolvedInitiative(initiativeResets: VectorClock): Int? =
        if (initiative.value.size == 1 && initiative.value[0].second.clock.contains(initiativeResets))
            initiative.value.first().first
        else
            null

    fun resolvedPlayerCharacter() = playerCharacter.value.let {
        when {
            it.isEmpty() -> null
            it.all { it.first } -> true
            it.all { !it.first } -> false
            else -> null
        }
    }

    fun resolvedDead() = dead.value.let {
        when {
            it.isEmpty() -> false
            it.any { it.first } -> true
            else -> false
        }
    }

    fun resolvedName(): String? = if (name.value.isEmpty()) null else name.textField()
}


data class AddCharacter(val id: String): Action()
data class ChangeName(val id: String, val name: String): Action()
data class ChangeInitiative(val id: String, val initiative: Int): Action()
data class ChangePlayerCharacter(val id: String, val playerCharacter: Boolean): Action()
data class DeleteCharacter(val id: String): Action()
object ResetAllInitiatives: Action()

fun serializeAction(it: Action): String {
    return when (it) {
        is AddCharacter -> "a${it.id}"
        is ChangeName -> "n${it.id}:${it.name}"
        is ChangeInitiative -> "i${it.id}:${it.initiative}"
        is ChangePlayerCharacter -> "${if (it.playerCharacter) "p" else "P"}${it.id}"
        is DeleteCharacter -> "c${it.id}"
        is ResetAllInitiatives -> "q"
        is Turn -> when (it.turnAction) {
            is TurnAction.StartTurn -> "s${it.turnAction.characterId.id}"
            is TurnAction.FinishTurn -> "f${it.turnAction.characterId.id}"
            is TurnAction.Die -> "d${it.turnAction.characterId.id}"
            is TurnAction.Delay -> "D${it.turnAction.characterId.id}"
            is TurnAction.ResolveConflicts -> "r"
        } + if (it.predecessor != null) ":" + it.predecessor.clientIdentifier.name + ":" + it.predecessor.position else ""
    }
}

fun deserializeAction(it: String): Action? {
    try {
        val turn = { usedParts: Int, turnAction: (List<String>) -> TurnAction ->
            val parts = it.substring(1).split(":")
            val startParts = parts.take(usedParts)
            val predecessor = Dot(ClientIdentifier(parts[usedParts]), parts[usedParts+1].toInt())
            Turn(turnAction(startParts), predecessor)
        }
        return when (it[0]) {
            'a' -> AddCharacter(it.substring(1))
            'n' -> ChangeName(
                it.substring(1).split(":")[0],
                it.substring(1).split(":", limit = 2)[1]
            )
            'i' -> ChangeInitiative(
                it.substring(1).split(":")[0],
                it.substring(1).split(":")[1].toInt()
            )
            'p' -> ChangePlayerCharacter(it.substring(1), true)
            'P' -> ChangePlayerCharacter(it.substring(1), false)
            'c' -> DeleteCharacter(it.substring(1))
            's' -> turn(1) { TurnAction.StartTurn(CharacterId(it[0])) }
            'D' -> turn(1) { TurnAction.Delay(CharacterId(it[0])) }
            'd' -> turn(1) { TurnAction.Die(CharacterId(it[0])) }
            'f' -> turn(1) { TurnAction.FinishTurn(CharacterId(it[0])) }
            'r' -> turn(0) { TurnAction.ResolveConflicts }
            'q' -> ResetAllInitiatives
            else -> return null
        }
    } catch (e: Exception) {
        throw Exception("Error deserializing \"$it\"", e)
    }
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