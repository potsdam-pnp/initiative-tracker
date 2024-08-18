package io.github.potsdam_pnp.initiative_tracker.crdt


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