package io.github.potsdam_pnp.initiative_tracker.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

data class ClientIdentifier(val name: String)

data class VectorClock(
    val clock: Map<ClientIdentifier, Int>
) {
    fun compare(other: VectorClock): CompareResult {
        if (this === other) return CompareResult.Equal

        var result: CompareResult = CompareResult.Equal

        for (entry in clock.keys + other.clock.keys) {
            val thisValue = clock[entry] ?: 0
            val otherValue = other.clock[entry] ?: 0

            val thisComparison = when {
                thisValue == otherValue -> CompareResult.Equal
                thisValue > otherValue -> CompareResult.Greater
                else -> CompareResult.Smaller
            }

            when {
                result == CompareResult.Equal ->
                    result = thisComparison

                result == CompareResult.Greater && thisComparison == CompareResult.Smaller ->
                    return CompareResult.Incomparable

                result == CompareResult.Smaller && thisComparison == CompareResult.Greater ->
                    return CompareResult.Incomparable
            }
        }

        return result
    }

    fun contains(other: VectorClock) =
        compare(other).let { it == CompareResult.Equal || it == CompareResult.Greater }

    fun versionsNotIn(other: VectorClock): Set<Dot> {
        val result = mutableSetOf<Dot>()
        for (key in clock.keys + other.clock.keys) {
            val thisValue = clock[key] ?: 0
            val otherValue = other.clock[key] ?: 0

            if (thisValue > otherValue) {
                for (i in (otherValue + 1) until (thisValue + 1)) {
                    result.add(Dot(key, i))
                }
            }
        }
        return result
    }

    fun merge(other: VectorClock): VectorClock =
        VectorClock(
            (clock.keys + other.clock.keys).map { key ->
                val thisValue = clock[key] ?: 0
                val otherValue = other.clock[key] ?: 0
                key to maxOf(thisValue, otherValue)
            }.toMap()
        )

    fun next(clientIdentifier: ClientIdentifier): VectorClock {
        val newValue = (clock[clientIdentifier] ?: 0) + 1
        return copy(clock = clock + (clientIdentifier to newValue))
    }

    companion object {
        fun empty() = VectorClock(mapOf())
    }
}


data class OperationMetadata(
    val clock: VectorClock,
    val client: ClientIdentifier
) {
    fun clockBefore(): VectorClock =
        VectorClock(clock.clock + (client to ((clock.clock[client] ?: 0) - 1)))

    fun toDot(): Dot =
        Dot(client, clock.clock[client] ?: 0)
}


sealed class CompareResult {
    object Equal: CompareResult()
    object Greater: CompareResult()
    object Smaller: CompareResult()
    object Incomparable: CompareResult()
}

data class Operation<Op>(
    val metadata: OperationMetadata,
    val op: Op
) {
    val dot: Dot get() {
        return Dot(metadata.client, metadata.clock.clock[metadata.client] ?: 0)
    }
}

abstract class OperationState<Op> {
    abstract fun apply(operation: Operation<Op>)
}

data class Dot(val clientIdentifier: ClientIdentifier, val position: Int)

sealed class InsertResult {
    data class MissingVersions(val missingDots: List<Dot>): InsertResult()
    data class Success(val newVersion: VectorClock): InsertResult()
}


class Repository<Op, State: OperationState<Op>> @OptIn(ExperimentalStdlibApi::class) constructor(
        val state: State,
        current: VectorClock = VectorClock.empty(),
        val clientIdentifier: ClientIdentifier = ClientIdentifier(
            Random.nextInt().toHexString().take(6))
        ) {
    private val currentVersion: MutableStateFlow<VectorClock> = MutableStateFlow(current)

    private val versions: MutableMap<Dot, Operation<Op>> = mutableMapOf()

    val version: StateFlow<VectorClock> get() = currentVersion

    fun produce(versions: List<Op>) {
        var nextVersion = currentVersion.value
        val next = mutableListOf<Operation<Op>>()

        for (version in versions) {
            nextVersion = nextVersion.next(clientIdentifier)
            next.add(Operation(OperationMetadata(nextVersion, clientIdentifier), version))
        }

        insert(nextVersion, next)
    }

    fun insert(version: VectorClock, data: List<Operation<Op>>): InsertResult {
        val toBeInserted = version.versionsNotIn(currentVersion.value).toMutableSet()

        val dataInsertions = mutableListOf<Operation<Op>>()
        for (operation in data) {
            if (operation.dot in toBeInserted) {
                dataInsertions += operation
                toBeInserted -= operation.dot
            }
        }

        if (toBeInserted.isNotEmpty()) {
            return InsertResult.MissingVersions(toBeInserted.toList())
        }

        versions.putAll(dataInsertions.map {
            it.dot to it
        })

        dataInsertions.forEach {
            state.apply(it)
        }

        currentVersion.update { it.merge(version) }

        return InsertResult.Success(currentVersion.value)
    }

    fun fetchVersion(dot: Dot): Operation<Op>? = versions[dot]
}


/**
 * Value with newest wins semantics; in case of conflicts, all values are preserved
 */
data class Value<T>(val value: List<Pair<T, OperationMetadata>>) {
    fun merge(other: Value<T>): Value<T> {
        var result = this
        for (value in other.value) {
            result = result.insert(value.first, value.second)
        }
        return result
    }

    fun insert(newValue: T, clock: OperationMetadata): Value<T> {
        val result = mutableListOf<Pair<T, OperationMetadata>>()
        for (v in value) {
            when (v.second.clock.compare(clock.clock)) {
                CompareResult.Equal -> return this
                CompareResult.Greater -> return this
                CompareResult.Smaller -> continue
                CompareResult.Incomparable -> result.add(v)
            }
        }
        return Value(result + (newValue to clock))
    }

    fun textField(): String {
        when {
            value.isEmpty() -> return ""
            value.size == 1 -> return value.first().first.toString()
            else -> return value.joinToString { it.first.toString() }
        }
    }

    companion object {
        fun <T> empty() = Value<T>(listOf())
    }
}

data class GrowingListItem<T>(
    val item: T,
    val predecessor: Dot?,
) {
    fun asList(dot: Dot, fetchDot: (Dot) -> Pair<Dot, GrowingListItem<T>>): List<Pair<Dot, T>> {
        val result = mutableListOf<Pair<Dot, T>>(dot to item)

        var current = predecessor

        while (current != null) {
            val i = fetchDot(current)
            current = i.second.predecessor
            result.add(i.first to i.second.item)
        }

        return result.reversed()
    }
}

sealed class ConflictState {
    object InAllTimelines: ConflictState()
    data class InTimelines(val timeline: Set<Int>): ConflictState()
}

fun <T> Value<GrowingListItem<T>>.show(fetchVersion: (Dot) -> Pair<GrowingListItem<T>, OperationMetadata>): List<Triple<Dot, ConflictState, T>> {
    if (value.isEmpty()) return emptyList()

    val currentTop = value.mapIndexed { index, v -> setOf(index) to v }.toMap().toMutableMap()
    val result = mutableListOf<Triple<Dot, ConflictState, T>>()

    var inAllTimelines = true

    while (currentTop.size > 1) {
        var candidate = currentTop.keys.first()
        var candidateValue = currentTop[candidate]!!
        var candidateClock = candidateValue.second.clock

        // Find equal values
        val allEquals = currentTop.filterValues { it.second.clock == candidateClock }
        if (allEquals.size > 1) {
            for (key in allEquals.keys) {
                currentTop.remove(key)
            }
            currentTop[allEquals.keys.flatten().toSet()] = candidateValue
            continue
        }

        for (key in currentTop.keys) {
            if (key == candidate) continue
            val clock = currentTop[key]!!.second.clock

            if (clock.contains(candidateClock)) {
                candidate = key
                candidateValue = currentTop[key]!!
                candidateClock = clock
            }
        }

        result.add(Triple(candidateValue.second.toDot(), ConflictState.InTimelines(candidate), candidateValue.first.item))
        val predecessor = candidateValue.first.predecessor
        if (predecessor == null) {
            inAllTimelines = false
            currentTop.remove(candidate)
        } else {
            currentTop[candidate] = fetchVersion(predecessor)
        }
    }

    val conflictState = if (inAllTimelines) ConflictState.InAllTimelines else ConflictState.InTimelines(currentTop.keys.first())

    val rest = currentTop.values.first()

    return rest.first.asList(rest.second.toDot()) {
        val v = fetchVersion(it)
        v.second.toDot() to v.first
    }.map { Triple(it.first,conflictState, it.second) } + result.reversed()
}

sealed class StringOperation {
    data class InsertAfter(val character: Char, val after: Dot?): StringOperation()
    data class Delete(val dot: Dot): StringOperation()
}

data class CharacterStringState(
    val successors: MutableList<Operation<Char>> = mutableListOf(),
    var isDeleted: Boolean = false
) {
    fun insert(d: Operation<Char>) {
        val index = successors.indexOfFirst {
            val cmp = it.metadata.clock.compare(d.metadata.clock)
            when (cmp) {
                CompareResult.Smaller -> false
                CompareResult.Greater -> true
                CompareResult.Equal -> throw RuntimeException("inserting duplicate - not allowed")
                CompareResult.Incomparable ->
                    // Here we need to define an arbitrary but consistent order, so let's pick the client id
                    it.metadata.client.name.compareTo(d.metadata.client.name) < 0
            }
        }
        if (index != -1) {
            successors.add(index, d)
        } else {
            successors.add(d)
        }
    }
}

class StringRegister(): Iterable<Operation<Char>> {
    val state: MutableMap<Dot?, CharacterStringState> = mutableMapOf()

    override fun iterator(): Iterator<Operation<Char>> {
        val position: MutableList<Pair<Dot?, Int>> = mutableListOf(null to 0)

        return object : Iterator<Operation<Char>> {
            var _next: Operation<Char>? = null

            override fun hasNext(): Boolean {
                if (_next != null) return true
                while (position.isNotEmpty()) {
                    val (current, index) = position.removeLast()
                    val next = state[current]?.successors?.getOrNull(index)
                    if (next != null) {
                        position.add(current to (index + 1))
                        position.add(next.dot to 0)
                        if (state[next.dot]?.isDeleted != true) {
                            _next = next
                            return true
                        }
                    }
                }
                return false
            }

            override fun next(): Operation<Char> {
                hasNext()
                if (_next != null) {
                    val result = _next!!
                    _next = null
                    return result
                } else {
                    throw NoSuchElementException()
                }
            }
        }
    }

    fun asString(): String {
        val result = StringBuilder()
        for (c in this) {
            result.append(c.op)
        }
        return result.toString()
    }

    fun positionIndex(index: Int): Dot? {
        if (index == 0) return null
        return withIndex().firstOrNull { it.index + 1 == index }?.value?.dot
    }


    fun insert(data: Operation<StringOperation>) {
        when (data.op) {
            is StringOperation.Delete -> {
                state.getOrPut(data.op.dot) { CharacterStringState() }.isDeleted = true
            }
            is StringOperation.InsertAfter -> {
                val d = Operation(data.metadata, data.op.character)
                state.getOrPut(data.op.after) { CharacterStringState() }.insert(d)
            }
        }
    }
}