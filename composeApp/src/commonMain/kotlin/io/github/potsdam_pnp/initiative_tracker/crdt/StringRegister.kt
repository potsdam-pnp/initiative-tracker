package io.github.potsdam_pnp.initiative_tracker.crdt

import androidx.compose.runtime.mutableStateListOf


sealed class StringOperation {
    data class InsertAfter(val character: Char, val after: Dot?): StringOperation()
    data class Delete(val dot: Dot): StringOperation()
}

data class Successors(
    val successors: MutableList<Pair<Operation<Char>, Successors>> = mutableListOf()
): Iterable<Operation<Char>> {
    private fun insertDirectly(d: Operation<Char>, succ: Successors) {
        val index = successors.indexOfFirst {
            it.first.metadata.client.name.compareTo(d.metadata.client.name) < 0
        }
        if (index != -1) {
            successors.add(index, d to succ)
        } else {
            successors.add(d to succ)
        }
    }

    fun insert(d: Operation<Char>) {
        val removed = mutableListOf<Int>()
        for (successor in successors.withIndex().reversed()) {
            when (successor.value.first.metadata.clock.compare(d.metadata.clock)) {
                CompareResult.Smaller -> removed.add(successor.index)
                CompareResult.Greater -> {
                    successor.value.second.insert(d)
                    return
                }
                CompareResult.Incomparable -> {}
                CompareResult.Equal -> throw RuntimeException("inserting duplicate - not allowed")
            }
        }
        val newSuccessors = mutableListOf<Pair<Operation<Char>, Successors>>()
        for (index in removed.reversed()) {
            newSuccessors.add(successors.removeAt(index))
        }
        newSuccessors.reverse()
        insertDirectly(d, Successors(newSuccessors))
    }

    private class Iter(var current: Successors?, var currentIndex: Int = 0, var preds: MutableList<Pair<Successors, Int>> = mutableListOf()): Iterator<Operation<Char>> {
        override fun hasNext(): Boolean {
            return current != null
        }

        override fun next(): Operation<Char> {
            val c = current ?: throw NoSuchElementException()
            val result = c.successors[currentIndex].first
            if (currentIndex + 1 < c.successors.size) {
                preds.add(c to (currentIndex + 1))
            }
            val nextSuccessors = c.successors[currentIndex].second
            if (nextSuccessors.successors.isNotEmpty()) {
                current = nextSuccessors
                currentIndex = 0
            } else {
                val p = preds.removeLastOrNull()
                if (p == null) {
                    current = null
                } else {
                    current = p.first
                    currentIndex = p.second
                }
            }
            return result
        }
    }

    override fun iterator(): Iterator<Operation<Char>> {
        return Iter(
            if (successors.isNotEmpty()) this else null
        )
    }
}

data class CharacterStringState(
    val successors: Successors = Successors(),
    var isDeleted: Boolean = false
) {
    fun insert(d: Operation<Char>) {
        successors.insert(d)
    }
}

class StringRegister(): Iterable<Operation<Char>> {
    val state: MutableMap<Dot?, CharacterStringState> = mutableMapOf()

    override fun iterator(): Iterator<Operation<Char>> {
        val position = state[null]?.successors?.iterator()
            ?.let { listOf(it) }.orEmpty().toMutableList()

        return object : Iterator<Operation<Char>> {
            var _next: Operation<Char>? = null

            override fun hasNext(): Boolean {
                if (_next != null) return true
                while (position.isNotEmpty()) {
                    val iterator = position.last()
                    if (iterator.hasNext()) {
                        val next = iterator.next()
                        val nextState = state[next.dot]
                        if (nextState != null) {
                            position.add(nextState.successors.iterator())
                        }
                        if (nextState?.isDeleted != true) {
                            _next = next
                            return true
                        }
                    } else {
                        position.removeLast()
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

    fun indexPosition(dot: Dot?): Int? {
        if (dot == null) return 0
        return withIndex().firstOrNull { it.value.dot == dot }?.let { it.index + 1 }
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

    fun toImmutableStringRegister() = ImmutableStringRegister(toList())

    companion object {
        fun empty(): StringRegister = StringRegister()
    }
}

data class ImmutableStringRegister(
    private val copied: List<Operation<Char>>
) {
    fun asString(): String = copied.joinToString("") { it.op.toString() }

    fun positionIndex(index: Int): Dot? {
        if (index == 0) return null
        return copied.withIndex().firstOrNull { it.index + 1 == index }?.value?.dot
    }

    fun indexPosition(dot: Dot?): Int? {
        if (dot == null) return 0
        return copied.withIndex().firstOrNull { it.value.dot == dot }?.let { it.index + 1 }
    }
}