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

data class ConflictTree<T>(
  val children: List<ConflictTree<T>>,
  val m: Pair<T, OperationMetadata>?,
) {
  fun isConflict(): Boolean {
    return children.size >= 2
  }

  fun pretty(
    indent: Int = 0,
    withoutMe: Boolean = true,
  ): List<Pair<Pair<Int, Int>, Pair<T, OperationMetadata>>> {
    val h =
      if (!withoutMe && m != null) {
        listOf((indent to children.size) to m)
      } else {
        emptyList()
      }
    return h + children.flatMap { it.pretty(indent + 1, false) }
  }

  fun conflictActionDepth(includeThis: Boolean = false): Int {
    return (if (includeThis) 1 else 0) +
      (children.maxOfOrNull { it.conflictActionDepth(includeThis = true) } ?: 0)
  }
}

fun <U : GrowingListItem<T>, T> List<ConflictTree<U>>.addChild(
  child: ConflictTree<U>
): List<ConflictTree<U>> {
  val i = indexOfFirst { it.m?.first?.item == child.m?.first?.item }
  if (i == -1) {
    return this + child
  } else {
    return this.toMutableList().apply {
      var newChildren = this[i].children
      child.children.forEach { newChildren = newChildren.addChild(it) }
      this[i] = this[i].copy(children = newChildren)
    }
  }
}

fun <U : GrowingListItem<T>, T> Register<U>.conflicts(
  skip: (T) -> Boolean,
  fetchVersion: (Dot) -> Pair<U, OperationMetadata>,
): ConflictTree<U> {

  fun skipUntil(p: Pair<U, OperationMetadata>): Pair<U, OperationMetadata>? {
    return when (skip(p.first.item)) {
      false -> p
      true -> p.first.predecessor?.let { fetchVersion(it) }
    }
  }

  val current: MutableList<ConflictTree<U>> =
    value
      .mapNotNull { skipUntil(it)?.let { ConflictTree<U>(listOf(), it) } }
      .sortedWith { l, r ->
        when {
          l.m == null && r.m == null -> 0
          l.m == null -> 1
          r.m == null -> -1
          else -> l.m.second.clock.compTotalOrder(r.m.second.clock)
        }
      }
      .toMutableList()

  if (current.isEmpty()) {
    return ConflictTree(listOf(), null)
  }

  while (current.size > 1) {
    val c = current.first()
    current.removeAt(0)
    val d = c.m!!.first.predecessor?.let { fetchVersion(it) }
    val index =
      current.binarySearch { l ->
        when {
          l.m == null && d == null -> 0
          l.m == null -> 1
          d == null -> -1
          else -> l.m.second.clock.compTotalOrder(d.second.clock)
        }
      }
    if (index >= 0) {
      current[index] = current[index].copy(children = current[index].children.addChild(c))
    } else {
      current.add(-index - 1, ConflictTree(listOf(c), d))
    }
  }

  var result = current.first()
  while (result.children.size == 1) {
    result = result.children.first()
  }
  return result
}
