import io.github.potsdam_pnp.initiative_tracker.ChangeInitiative
import io.github.potsdam_pnp.initiative_tracker.Turn
import io.github.potsdam_pnp.initiative_tracker.TurnAction
import io.github.potsdam_pnp.initiative_tracker.crdt.Repository
import io.github.potsdam_pnp.initiative_tracker.State
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActionStateTests {
    @Test
    fun emptyActions() {
        val repository = Repository(State())
        val predicted = repository.state.predictNextTurns(withCurrent = false, repository)
        assertEquals(emptyList(), predicted)
        assertNull(repository.state.currentTurn(repository))
    }

    @Test
    fun twoCharacters() {
        val repository = Repository(State())
        repository.produce(
            listOf(
                ChangeInitiative("character1", 5),
                ChangeInitiative("character2", 10)
            )
        )
        val predicted = repository.state.predictNextTurns(withCurrent = false, repository)
        assertEquals(listOf("character2", "character1"), predicted.map { it.key })
        assertEquals(listOf(0, 0), predicted.map { it.turn })

        assertNull(repository.state.currentTurn(repository))
    }

    @Test
    fun twoCharacters2() {
        val repository = Repository(State())
        repository.produce(
            listOf(
                ChangeInitiative("character1", 5),
                ChangeInitiative("character2", 10),
                Turn(TurnAction.StartTurn("character1"), null)
            )
        )
        val predicted = repository.state.predictNextTurns(withCurrent = false, repository)
        val predicted2 = repository.state.predictNextTurns(withCurrent = true, repository)
        assertEquals(listOf("character2" to 0, "character1" to 1), predicted.map { it.key to it.turn })
        assertEquals(listOf("character1" to 0, "character2" to 0), predicted2.map { it.key to it.turn })
        assertEquals("character1", repository.state.currentTurn(repository))
    }
}