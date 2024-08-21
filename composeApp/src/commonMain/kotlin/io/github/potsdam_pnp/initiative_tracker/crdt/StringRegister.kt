package io.github.potsdam_pnp.initiative_tracker.crdt

import androidx.compose.runtime.mutableStateListOf
import io.github.aakira.napier.Napier


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

    sealed class DotGenerator {
        data class FromResult(val index: Int): DotGenerator()
        data class FromDot(val dot: Dot?): DotGenerator()
    }

    fun operationsToUpdateTo(newString: String, cursor: Int): Pair<((Int) -> Dot) -> List<StringOperation>, DotGenerator> {
        val s = asString()
        val sameFront = s.withIndex().indexOfFirst { newString.length <= it.index || newString[it.index] != it.value }
        if (sameFront == -1) {
            // Beginnings are the same, so we push the rest to the end
            return { dots: (Int) -> Dot ->
                newString.drop(s.length).toCharArray().mapIndexed { index, c ->
                    val after = if (index == 0) positionIndex(s.length) else dots(index - 1)
                    StringOperation.InsertAfter(c, after)
                }
            } to if (cursor <= s.length) positionIndex(cursor).let { DotGenerator.FromDot(it) } else DotGenerator.FromResult(cursor - s.length - 1)
        } else {
            val sameEnd = s.withIndex().indexOfLast { it.index - s.length + newString.length < 0 || newString[it.index - s.length + newString.length] != it.value}
            if (sameFront >= sameEnd + 1 && newString.length >= s.length) {
                val dot = when {
                    cursor < sameFront -> DotGenerator.FromDot(positionIndex(cursor))
                    cursor > sameEnd + newString.length - s.length ->
                        DotGenerator.FromDot(positionIndex(cursor - newString.length + s.length)!!)
                    else -> DotGenerator.FromResult(cursor - sameFront)
                }
                // All characters from s are part of the new string, so we add the new part
                val newPart = newString.substring(sameFront, sameFront + newString.length - s.length)
                return { dots: (Int) -> Dot ->
                    newPart.toCharArray().mapIndexed { index, c ->
                        val after = if (index == 0) positionIndex(sameFront) else dots(index - 1)
                        StringOperation.InsertAfter(c, after)
                    }
                } to dot
            } else if (sameFront <= sameEnd && newString.length == sameFront + s.length - sameEnd - 1) {
                val dot = when {
                    cursor < sameFront -> DotGenerator.FromDot(positionIndex(cursor))
                    else -> DotGenerator.FromDot(positionIndex(cursor - newString.length + s.length))
                }
                return { _: (Int) -> Dot -> (sameFront until (sameEnd + 1)).map { index -> StringOperation.Delete(copied[index].dot) } } to dot
            } else {
                Napier.w("String operations not supported: '$s' -> '$newString'")
                return { _: (Int) -> Dot -> listOf<StringOperation>() } to DotGenerator.FromDot(null)
            }
        }
    }
}