package io.github.potsdam_pnp.initiative_tracker.crdt

data class VectorClock(val clock: Map<ClientIdentifier, Int>) {
  fun compare(other: VectorClock): CompareResult {
    if (this === other) return CompareResult.Equal

    var result: CompareResult = CompareResult.Equal

    for (entry in clock.keys + other.clock.keys) {
      val thisValue = clock[entry] ?: 0
      val otherValue = other.clock[entry] ?: 0

      val thisComparison =
        when {
          thisValue == otherValue -> CompareResult.Equal
          thisValue > otherValue -> CompareResult.Greater
          else -> CompareResult.Smaller
        }

      when {
        result == CompareResult.Equal -> result = thisComparison

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
      (clock.keys + other.clock.keys)
        .map { key ->
          val thisValue = clock[key] ?: 0
          val otherValue = other.clock[key] ?: 0
          key to maxOf(thisValue, otherValue)
        }
        .toMap()
    )

  fun next(clientIdentifier: ClientIdentifier): VectorClock {
    val newValue = (clock[clientIdentifier] ?: 0) + 1
    return copy(clock = clock + (clientIdentifier to newValue))
  }

  fun clientTotalOrder(other: VectorClock): Int {
    val allKeys = clock.keys + other.clock.keys
    val orderedKeys = allKeys.sortedWith(ClientIdentifier.totalOrder)
    for (key in orderedKeys) {
      val result = (clock[key] ?: 0).compareTo(other.clock[key] ?: 0)
      if (result != 0) {
        return result
      }
    }
    return 0
  }

  fun compTotalOrder(other: VectorClock): Int {
    val s = clock.values.sum()
    val o = other.clock.values.sum()
    return if (s < o) {
      -1
    } else if (s > o) {
      1
    } else {
      clientTotalOrder(other)
    }
  }

  fun contains(other: Dot?): Boolean {
    if (other == null) return true
    return (clock[other.clientIdentifier] ?: 0) >= other.position
  }

  fun dotsNotIn(previous: VectorClock): Iterator<Dot> {
    val dots = previous.clock.keys.toList()
    var index = -1
    var value: Int
    do {
      index += 1
      if (index >= dots.size) {
        return object : Iterator<Dot> {
          override fun hasNext(): Boolean = false

          override fun next(): Dot {
            throw NoSuchElementException()
          }
        }
      }
      value = previous.clock[dots[index]]!!
    } while (value >= (clock[dots[index]] ?: 0))

    return object : Iterator<Dot> {
      override fun hasNext(): Boolean {
        return index < dots.size
      }

      override fun next(): Dot {
        value += 1
        val result = Dot(dots[index], value)
        while (index < dots.size && value >= (clock[dots[index]] ?: 0)) {
          index += 1
          if (index < dots.size) {
            value = previous.clock[dots[index]] ?: 0
          }
        }

        return result
      }
    }
  }

  companion object {
    fun empty() = VectorClock(mapOf())
  }
}

sealed class CompareResult {
  object Equal : CompareResult()

  object Greater : CompareResult()

  object Smaller : CompareResult()

  object Incomparable : CompareResult()
}

data class Dot(val clientIdentifier: ClientIdentifier, val position: Int)
