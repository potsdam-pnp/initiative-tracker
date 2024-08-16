package io.github.potsdam_pnp.initiative_tracker.crdt

/**
 * The type that a CRDT needs to implement
 *
 * It needs to have the following properties
 *   apply is commutative, that is applying Operations in different orders leads to the same result
 *
 *
 */
abstract class AbstractState<Op> {
    /**
     * A mutable function that applies an operation to the state
     * Additionally, it returns a list of operations that are invalidated
     * by this operation (that is replaying the same operations that lead to
     * this state, but without these operations, leads to the exact same state)
     * It is always okay to return an empty list. Adding more things to this list
     * helps in removing operations from the repository
     * It is only allowed to return dots exactly once after it has been added in the
     * lifecycle of this state. After it has been re-added, it can be removed again though
     * (it is okay to remove in the same apply operation, that added it - in this case
     * the apply is communicating that it's a no-op).
     */
    abstract fun apply(operation: Operation<Op>): List<Dot>

    /**
     * For an operation, return the predecessors that always need to be kept,
     * even if apply is already okay with removing them
     */
    abstract fun predecessors(operation: Op): List<Dot>
}