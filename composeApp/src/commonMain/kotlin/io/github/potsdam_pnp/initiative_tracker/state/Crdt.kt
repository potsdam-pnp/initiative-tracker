package io.github.potsdam_pnp.initiative_tracker.state

import kotlinx.coroutines.flow.Flow
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

    fun versionsNotIn(other: VectorClock): Set<Version> {
        val result = mutableSetOf<Version>()
        for (key in clock.keys + other.clock.keys) {
            val thisValue = clock[key] ?: 0
            val otherValue = other.clock[key] ?: 0

            if (thisValue > otherValue) {
                for (i in (otherValue + 1) until (thisValue + 1)) {
                    result.add(Version(key, i))
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

    fun toVersion(): Version =
        Version(client, clock.clock[client] ?: 0)
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
    val version: Version get() {
        return Version(metadata.client, metadata.clock.clock[metadata.client] ?: 0)
    }
}

abstract class OperationState<Op> {
    abstract fun apply(operation: Operation<Op>)
}

data class Version(val clientIdentifier: ClientIdentifier, val position: Int)

sealed class InsertResult {
    data class MissingVersions(val missingVersions: List<Version>): InsertResult()
    data class Success(val newVersion: VectorClock): InsertResult()
}


class Snapshot<Op, State: OperationState<Op>> @OptIn(ExperimentalStdlibApi::class) constructor(
        val state: State,
        current: VectorClock = VectorClock(mapOf()),
        val clientIdentifier: ClientIdentifier = ClientIdentifier(
            Random.nextInt().toHexString().take(6))
        ) {
    private val currentVersion: MutableStateFlow<VectorClock> = MutableStateFlow(current)

    private val versions: MutableMap<Version, Operation<Op>> = mutableMapOf()

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
            if (operation.version in toBeInserted) {
                dataInsertions += operation
                toBeInserted -= operation.version
            }
        }

        if (toBeInserted.isNotEmpty()) {
            return InsertResult.MissingVersions(toBeInserted.toList())
        }

        versions.putAll(dataInsertions.map {
            it.version to it
        })

        dataInsertions.forEach {
            state.apply(it)
        }

        currentVersion.update { it.merge(version) }

        return InsertResult.Success(currentVersion.value)
    }

    fun fetchVersion(version: Version): Operation<Op>? = versions[version]
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
    val predecessor: Version?,
) {
    fun asList(version: Version, fetchVersion: (Version) -> Pair<Version, GrowingListItem<T>>): List<Pair<Version, T>> {
        val result = mutableListOf<Pair<Version, T>>(version to item)

        var current = predecessor

        while (current != null) {
            val i = fetchVersion(current)
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

fun <T> Value<GrowingListItem<T>>.show(fetchVersion: (Version) -> Pair<GrowingListItem<T>, OperationMetadata>): List<Triple<Version, ConflictState, T>> {
    if (value.isEmpty()) return emptyList()

    val currentTop = value.mapIndexed { index, v -> setOf(index) to v }.toMap().toMutableMap()
    val result = mutableListOf<Triple<Version, ConflictState, T>>()

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

        result.add(Triple(candidateValue.second.toVersion(), ConflictState.InTimelines(candidate), candidateValue.first.item))
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

    return rest.first.asList(rest.second.toVersion()) {
        val v = fetchVersion(it)
        v.second.toVersion() to v.first
    }.map { Triple(it.first,conflictState, it.second) } + result.reversed()
}