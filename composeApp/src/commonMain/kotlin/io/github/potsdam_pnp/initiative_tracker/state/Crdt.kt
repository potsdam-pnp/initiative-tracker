package io.github.potsdam_pnp.initiative_tracker.state

import kotlinx.coroutines.flow.Flow

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
}


data class OperationMetadata(
    val clock: VectorClock,
    val client: ClientIdentifier
) {
    fun clockBefore(): VectorClock =
        VectorClock(clock.clock + (client to ((clock.clock[client] ?: 0) - 1)))
}


sealed class CompareResult {
    object Equal: CompareResult()
    object Greater: CompareResult()
    object Smaller: CompareResult()
    object Incomparable: CompareResult()
}

abstract class Operation {
    abstract val metadata: OperationMetadata

    val version: Version get() {
        return Version(metadata.client, metadata.clock.clock[metadata.client] ?: 0)
    }
}

abstract class OperationState<Op: Operation> {
    abstract fun apply(operation: Op)
}

data class Version(val clientIdentifier: ClientIdentifier, val position: Int)

sealed class InsertResult {
    data class MissingVersions(val missingVersions: List<Version>): InsertResult()
    data class Success(val newVersion: VectorClock): InsertResult()
}


class Snapshot<Op: Operation, State: OperationState<Op>>(val state: State) {
    private var currentVersion: VectorClock = VectorClock(mapOf())

    private val versions: MutableMap<Version, Op> = mutableMapOf()

    val version: VectorClock get() = currentVersion

    fun insert(version: VectorClock, data: List<Op>): InsertResult {
        val toBeInserted = version.versionsNotIn(currentVersion).toMutableSet()

        val dataInsertions = mutableListOf<Op>()
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

        currentVersion = currentVersion.merge(version)

        return InsertResult.Success(currentVersion)
    }

    fun fetchVersion(version: Version): Op? = versions[version]
}


/**
 * Value with newest wins semantics; in case of conflicts, all values are preserved
 */
data class Value<T>(val value: List<Pair<T, VectorClock>>) {
    fun merge(other: Value<T>): Value<T> {
        var result = this
        for (value in other.value) {
            result = result.insert(value.first, value.second)
        }
        return result
    }

    fun insert(newValue: T, clock: VectorClock): Value<T> {
        val result = mutableListOf<Pair<T, VectorClock>>()
        for (v in value) {
            when (v.second.compare(clock)) {
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
    val clock: VectorClock,
    val predecessorUndos: List<VectorClock>
)

data class ConflictedList<T>(
    val prefixWithoutConflict: List<T>,
    val differentConflictSuffixes: List<List<T>>
)

class ConflictedListBuilder<T>(
    val prefix: MutableList<Pair<VectorClock, T>> = mutableListOf(),
    val suffix: MutableList<MutableList<Pair<VectorClock, T>>> = mutableListOf()
) {
    fun add(clock: VectorClock, item: T) {
        var index = 0

        while (prefix.size > index && clock.contains(prefix[index].first)) {
            index += 1
        }

        if (prefix.size > index && prefix[index].first.contains(clock)) {
            prefix.add(index, clock to item)
        } else {
            if (prefix.size == index && suffix.all { it.first().first.contains(clock) }) {
                prefix.add(clock to item)
            } else {
                val newConflictPrefix = prefix.drop(index)
                while (index < prefix.size) {
                    prefix.removeLast()
                }

                for (list in suffix) {
                    list.addAll(0, newConflictPrefix)
                }

                var hasBeenAdded = false
                for (list in suffix) {
                    if (addToConflict(list, clock, item) { suffix.add(it) }) {
                        hasBeenAdded = true
                    }
                }

                if (!hasBeenAdded) {
                    suffix.add(mutableListOf(clock to item))
                }
            }
        }
    }

    private fun addToConflict(list: MutableList<Pair<VectorClock, T>>, clock: VectorClock, item: T, callback: (MutableList<Pair<VectorClock, T>>) -> Unit): Boolean {
        var index = 0

        while (list.size > index && clock.contains(list[index].first)) {
            index += 1
        }

        if (list.size > index && list[index].first.contains(clock)) {
            list.add(index, clock to item)
            return true
        } else {
            if (index == 0) {
                return false
            } else {
                val newList = list.take(index).toMutableList()
                newList.add(clock to item)
                callback(newList)
                return true
            }
        }
    }

    fun build(): ConflictedList<T> {
        return ConflictedList(
            prefixWithoutConflict = prefix.map { it.second },
            differentConflictSuffixes = suffix.map { it.map { it.second } }
        )
    }
}

/**
 * Value containing a list that only grows in the end
 */
data class GrowingList<T>(val currentActive: MutableMap<VectorClock, GrowingListItem<T>> = mutableMapOf(), val currentInactive: MutableMap<VectorClock, Pair<Int, GrowingListItem<T>?>> = mutableMapOf()) {
    fun insert(item: GrowingListItem<T>) {
        if (currentInactive.containsKey(item.clock)) {
            currentInactive[item.clock] = currentInactive[item.clock]!!.copy(second = item)
        } else {
            currentActive[item.clock] = item
            for (undo in item.predecessorUndos) {
                addUndo(undo)
            }
        }
    }

    private fun addUndo(clock: VectorClock) {
        if (currentInactive.containsKey(clock)) {
            currentInactive[clock] = currentInactive[clock]!!.let { it.copy(first = it.first + 1 )}
        } else {
            val active = currentActive[clock]
            currentInactive[clock] = Pair(1, active)
            for (undo in active?.predecessorUndos.orEmpty()) {
                removeUndo(undo)
            }
        }
    }

    private fun removeUndo(clock: VectorClock) {
        currentInactive[clock].also {
            require(it != null)
            if (it.first - 1 > 0) {
                currentInactive[clock] = it.copy(first = it.first - 1)
            } else {
                val active = it.second
                currentInactive.remove(clock)
                if (active != null) {
                    currentActive[clock] = active
                    for (undo in active.predecessorUndos) {
                        addUndo(undo)
                    }
                }
            }
        }
    }

    fun ordered(): ConflictedList<T> {
        val result = ConflictedListBuilder<T>()
        for (item in currentActive) {
            result.add(item.key, item.value.item)
        }
        return result.build()
    }
}