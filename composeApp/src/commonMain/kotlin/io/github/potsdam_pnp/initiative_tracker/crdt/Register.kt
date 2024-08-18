package io.github.potsdam_pnp.initiative_tracker.crdt

/**
 * Value with newest wins semantics; in case of conflicts, all values are preserved
 */
data class Register<T>(val value: List<Pair<T, OperationMetadata>>) {
    fun merge(other: Register<T>): Register<T> {
        var result = this
        for (value in other.value) {
            result = result.insert(value.first, value.second)
        }
        return result
    }

    fun insert(newValue: T, clock: OperationMetadata): Register<T> {
        val result = mutableListOf<Pair<T, OperationMetadata>>()
        for (v in value) {
            when (v.second.clock.compare(clock.clock)) {
                CompareResult.Equal -> return this
                CompareResult.Greater -> return this
                CompareResult.Smaller -> continue
                CompareResult.Incomparable -> result.add(v)
            }
        }
        return Register(result + (newValue to clock))
    }

    fun insert(newValue: Operation<T>) {
        insert(newValue.op, newValue.metadata)
    }

    fun textField(): String {
        when {
            value.isEmpty() -> return ""
            value.size == 1 -> return value.first().first.toString()
            else -> return value.joinToString { it.first.toString() }
        }
    }

    companion object {
        fun <T> empty() = Register<T>(listOf())
    }
}