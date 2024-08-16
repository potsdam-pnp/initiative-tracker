package io.github.potsdam_pnp.initiative_tracker.crdt

import io.github.potsdam_pnp.initiative_tracker.state.ClientIdentifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

sealed class InsertResult {
    data class MissingVersions(val missingDots: List<Dot>): InsertResult()
    data class Success(val newVersion: VectorClock): InsertResult()
}

class Repository<Op, State: AbstractState<Op>> @OptIn(ExperimentalStdlibApi::class) constructor(
    val state: State,
    current: VectorClock = VectorClock.empty(),
    val clientIdentifier: ClientIdentifier = ClientIdentifier(
        Random.nextInt().toHexString().take(6)
    )
        ) {
    private val currentVersion: MutableStateFlow<VectorClock> = MutableStateFlow(current)

    private val versions: MutableMap<Dot, Operation<Op>> = mutableMapOf()

    val version: StateFlow<VectorClock> get() = currentVersion

    fun produce(versions: List<Op>) {
        var nextVersion = currentVersion.value
        val next = mutableListOf<Operation<Op>>()

        for (version in versions) {
            nextVersion = nextVersion.next(clientIdentifier)
            next.add(Operation(OperationMetadata(nextVersion, clientIdentifier), version))
        }

        insert(nextVersion, next)
    }

    fun insert(version: VectorClock, data: List<Operation<Op>>): InsertResult {
        val toBeInserted = version.versionsNotIn(currentVersion.value).toMutableSet()

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

        versions.putAll(dataInsertions.map {
            it.dot to it
        })

        dataInsertions.forEach {
            state.apply(it)
        }

        currentVersion.update { it.merge(version) }

        return InsertResult.Success(currentVersion.value)
    }

    fun fetchVersion(dot: Dot): Operation<Op>? = versions[dot]
}