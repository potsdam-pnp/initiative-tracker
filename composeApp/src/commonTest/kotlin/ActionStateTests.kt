import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActionStateTests {
    @Test
    fun emptyActions() {
        val state = State2(emptyList())
        val predicted = state.predictNextTurns(withCurrent = false)
        assertEquals(emptyList(), predicted)
        assertNull(state.currentTurn())
    }
}