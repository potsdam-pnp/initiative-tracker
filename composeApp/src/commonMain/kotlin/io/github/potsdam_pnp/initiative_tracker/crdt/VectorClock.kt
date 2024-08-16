package io.github.potsdam_pnp.initiative_tracker.crdt

import io.github.potsdam_pnp.initiative_tracker.state.ClientIdentifier

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

sealed class CompareResult {
    object Equal: CompareResult()
    object Greater: CompareResult()
    object Smaller: CompareResult()
    object Incomparable: CompareResult()
}

data class Dot(val clientIdentifier: ClientIdentifier, val position: Int)