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

    @Test
    fun twoCharacters() {
        val state = State2(
            listOf(
                ChangeInitiative("character1", 5),
                ChangeInitiative("character2", 10)
            )
        )
        val predicted = state.predictNextTurns(withCurrent = false)
        assertEquals(listOf("character2", "character1"), predicted.map { it.key })
        assertNull(state.currentTurn())
    }

    @Test
    fun twoCharacters2() {
        val state = State2(
            listOf(
                ChangeInitiative("character1", 5),
                ChangeInitiative("character2", 10),
                StartTurn("character1")
            )
        )
        val predicted = state.predictNextTurns(withCurrent = false)
        assertEquals(listOf("character2", "character1"), predicted.map { it.key })
        assertEquals("character1", state.currentTurn())
    }
}