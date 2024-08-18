import io.github.potsdam_pnp.initiative_tracker.ChangeInitiative
import io.github.potsdam_pnp.initiative_tracker.StartTurn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActionStateTests {
    @Test
    fun emptyActions() {
        val state = State2(emptyList(), turnActions = emptyList())
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
            ),
            turnActions = listOf()
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
            ),
            turnActions = listOf()
        )
        val predicted = state.predictNextTurns(withCurrent = false)
        assertEquals(listOf("character2", "character1"), predicted.map { it.key })
        assertEquals("character1", state.currentTurn())
    }
}