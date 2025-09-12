package io.github.potsdam_pnp.initiative_tracker.crdt

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

sealed class InsertResult {
  data class MissingVersions(val missingDots: List<Dot>) : InsertResult()

  data class Success(val newVersion: VectorClock) : InsertResult()
}

class Repository<Op, State : AbstractState<Op>>
@OptIn(ExperimentalStdlibApi::class)
constructor(
  val state: State,
  current: VectorClock = VectorClock.empty(),
  prefetched: VectorClock = current,
  val clientIdentifier: ClientIdentifier = ClientIdentifier.new(),
) {
  private val currentVersion: MutableStateFlow<VectorClock> = MutableStateFlow(current)
  private val prefetchedVersion: MutableStateFlow<VectorClock> = MutableStateFlow(prefetched)

  private val versions: MutableMap<Dot, Operation<Op>> = mutableMapOf()

  val version: StateFlow<VectorClock>
    get() = currentVersion

  val prefetched: StateFlow<VectorClock>
    get() = prefetchedVersion

  fun produce(versionsFun: ((Int) -> Dot) -> List<Op>): List<Dot> {
    var nextVersion = currentVersion.value
    var highestIndex = -1
    val currentPosition = nextVersion.clock[clientIdentifier] ?: 0
    val dotCreator = { index: Int ->
      highestIndex = maxOf(highestIndex, index)
      Dot(clientIdentifier, currentPosition + index + 1)
    }
    val versions = versionsFun(dotCreator)

    if (highestIndex >= versions.size) {
      throw IllegalStateException("Invalid usage of Repository.produce")
    }

    val next = mutableListOf<Operation<Op>>()

    for (version in versions) {
      nextVersion = nextVersion.next(clientIdentifier)
      next.add(Operation(OperationMetadata(nextVersion, clientIdentifier), version))
    }

    insert(nextVersion, next)
    return next.map { it.dot }
  }

  fun produce(vararg versions: Op): List<Dot> {
    return produce { versions.toList() }
  }

  fun insert(version: VectorClock, data: List<Operation<Op>>): InsertResult {
    val toBeInserted = version.versionsNotIn(prefetchedVersion.value).toMutableSet()

    val dataInsertions = mutableListOf<Operation<Op>>()
    for (operation in data) {
      if (operation.dot in toBeInserted) {
        dataInsertions += operation
        toBeInserted -= operation.dot
      }
    }

    if (toBeInserted.isNotEmpty()) {
      return InsertResult.MissingVersions(toBeInserted.toList())
    }

    versions.putAll(dataInsertions.map { it.dot to it })

    prefetchedVersion.update { it.merge(version) }

    updateState()

    return InsertResult.Success(currentVersion.value)
  }

  private fun updateState() {
    val p = prefetched.value
    val v = version.value
    var commit = v
    do {
      var hasProgress = false

      p.clock.firstNotNullOfOrNull {
        val nextDot = Dot(it.key, (v.clock[it.key] ?: 0) + 1)
        val version = fetchVersion(nextDot) ?: return@firstNotNullOfOrNull null
        if (v.contains(version.metadata.clockBefore())) {
          hasProgress = true
          state.apply(version)
          commit = commit.merge(version.metadata.clock)
        } else null
      }
    } while (hasProgress)

    currentVersion.update { it.merge(commit) }
  }

  fun fetchVersion(dot: Dot): Operation<Op>? = versions[dot]
}
