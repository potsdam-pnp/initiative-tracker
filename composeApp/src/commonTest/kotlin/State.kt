import kotlin.test.Test
import kotlin.test.assertNull

class State2Test {
    @Test
    fun predictsNextTurnIfNoCharacterPresent() {
        val state = State2()
        val predicted = state.predictNextTurn()
        assertNull(predicted)
    }
}
