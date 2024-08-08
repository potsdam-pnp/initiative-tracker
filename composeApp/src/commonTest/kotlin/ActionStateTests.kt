import kotlin.test.Test
import kotlin.test.assertNull

class ActionStateTests {
    @Test
    fun emptyActions() {
        val state = State2(emptyList())
        val predicted = state.predictNextTurns(withCurrent = false)
        assertNull(predicted)
        assertNull(state.currentTurn())
    }
}