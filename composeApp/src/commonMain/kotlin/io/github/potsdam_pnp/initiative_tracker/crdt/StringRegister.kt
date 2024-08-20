package io.github.potsdam_pnp.initiative_tracker.crdt

import androidx.compose.runtime.mutableStateListOf


sealed class StringOperation {
    data class InsertAfter(val character: Char, val after: Dot?): StringOperation()
    data class Delete(val dot: Dot): StringOperation()
}

data class Successors(
    val successors: MutableList<Operation<Char>> = mutableListOf()
): Iterable<Operation<Char>> {
    fun insert(d: Operation<Char>) {
        val index = successors.indexOfFirst { it.metadata.clock.clientTotalOrder(d.metadata.clock) < 0 }
        if (index == -1) {
            successors.add(d)
        } else {
            successors.add(index, d)
        }
    }

    override fun iterator(): Iterator<Operation<Char>> {
        return successors.iterator()
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

    fun toImmutableStringRegister() = if (state.isNotEmpty()) ImmutableStringRegister(toList()) else null

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