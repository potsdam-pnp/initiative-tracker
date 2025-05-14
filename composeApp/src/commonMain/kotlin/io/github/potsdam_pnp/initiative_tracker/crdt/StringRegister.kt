package io.github.potsdam_pnp.initiative_tracker.crdt

import EditedCharacterPositions
import androidx.compose.ui.text.input.TextFieldValue


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

    private sealed class Change {
        data class AddAfter(val index: Int, val str: String): Change()
        data class Delete(val from: Int, val untilExclusive: Int): Change()
        data class DeleteAndAdd(val from: Int, val untilExclusive: Int, val replace: String): Change()

        fun cursorPosition(self: ImmutableStringRegister, cursor: Int): DotGenerator {
            when (this) {
                is AddAfter ->
                    when {
                        cursor <= index -> return DotGenerator.FromDot(self.positionIndex(cursor))
                        cursor <= index + str.length -> return DotGenerator.FromResult(cursor - index - 1)
                        else -> return DotGenerator.FromDot(self.positionIndex(cursor - str.length))
                    }
                is Delete ->
                    when {
                        cursor <= from -> return DotGenerator.FromDot(self.positionIndex(cursor))
                        else -> return DotGenerator.FromDot(self.positionIndex(cursor - from + untilExclusive))
                    }
                is DeleteAndAdd ->
                    when {
                        cursor <= from -> return DotGenerator.FromDot(self.positionIndex(cursor))
                        cursor <= from + replace.length -> return DotGenerator.FromResult(cursor - from - 1 + (untilExclusive - from))
                        else -> return DotGenerator.FromDot(self.positionIndex(cursor - replace.length + untilExclusive - from))
                    }
            }
        }

        fun operations(self: ImmutableStringRegister): ((Int) -> Dot) -> List<StringOperation> {
            when (this) {
                is AddAfter ->
                    return { dots ->
                        str.mapIndexed { index, c ->
                            if (index == 0) {
                                StringOperation.InsertAfter(c, self.positionIndex(this.index))
                            } else {
                                StringOperation.InsertAfter(c, dots(index - 1))
                            }
                        }
                    }
                is Delete ->
                    return { _ ->
                        (from until untilExclusive).map { StringOperation.Delete(self.positionIndex(it + 1)!!) }
                    }
                is DeleteAndAdd ->
                    return { dots ->
                        (from until untilExclusive).map { StringOperation.Delete(self.positionIndex(it + 1)!!) } +
                        replace.mapIndexed { index, c ->
                            if (index == 0) {
                                StringOperation.InsertAfter(c, self.positionIndex(from + 1))
                            } else {
                                StringOperation.InsertAfter(c, dots(index - 1 + untilExclusive - from))
                            }
                        }
                    }

            }
        }
    }


    fun operationsToUpdateTo(newString: String, positions: EditedCharacterPositions<Int>): Pair<((Int) -> Dot) -> List<StringOperation>, EditedCharacterPositions<DotGenerator>> {
        val s = asString()
        val sameFront = s.withIndex().indexOfFirst { newString.length <= it.index || newString[it.index] != it.value }
        val sameEnd = s.withIndex().indexOfLast { it.index - s.length + newString.length < 0 || newString[it.index - s.length + newString.length] != it.value}

        val operation = if (sameFront == -1) {
            Change.AddAfter(s.length, newString.drop(s.length))
        } else if (sameFront >= sameEnd + 1) {
            if (newString.length >= s.length) {
                Change.AddAfter(sameFront, newString.substring(sameFront, sameFront + newString.length - s.length))
            } else {
                Change.Delete(sameFront, s.length - newString.length + sameFront)
            }
        } else {
            Change.DeleteAndAdd(sameFront, sameEnd + 1, newString.substring(sameFront, sameEnd + 1 - s.length + newString.length))
        }

        return operation.operations(this) to positions.map { operation.cursorPosition(this, it) }
    }
}