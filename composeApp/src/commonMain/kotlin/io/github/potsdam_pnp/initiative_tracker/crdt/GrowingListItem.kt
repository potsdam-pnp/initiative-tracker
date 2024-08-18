package io.github.potsdam_pnp.initiative_tracker.crdt

interface GrowingListItem<T> {
    val item: T
    val predecessor: Dot?

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

fun <U: GrowingListItem<T>, T> Register<U>.show(fetchVersion: (Dot) -> Pair<U, OperationMetadata>): List<Triple<Dot, ConflictState, T>> {
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