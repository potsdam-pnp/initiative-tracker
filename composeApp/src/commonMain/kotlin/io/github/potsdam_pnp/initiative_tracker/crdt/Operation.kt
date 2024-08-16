package io.github.potsdam_pnp.initiative_tracker.crdt

import io.github.potsdam_pnp.initiative_tracker.state.ClientIdentifier

data class OperationMetadata(
    val clock: VectorClock,
    val client: ClientIdentifier
) {
    fun clockBefore(): VectorClock =
        VectorClock(clock.clock + (client to ((clock.clock[client] ?: 0) - 1)))

    fun toDot(): Dot =
        Dot(client, clock.clock[client] ?: 0)
}

data class Operation<Op>(
    val metadata: OperationMetadata,
    val op: Op
) {
    val dot: Dot
        get() {
        return Dot(metadata.client, metadata.clock.clock[metadata.client] ?: 0)
    }
}