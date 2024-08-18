import io.github.potsdam_pnp.initiative_tracker.Action
import io.github.potsdam_pnp.initiative_tracker.ChangeInitiative
import io.github.potsdam_pnp.initiative_tracker.Turn
import io.github.potsdam_pnp.initiative_tracker.TurnAction
import io.github.potsdam_pnp.initiative_tracker.crdt.Repository
import io.github.potsdam_pnp.initiative_tracker.state.State
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
        assertEquals(listOf("character2", "character1"), predicted.map { it.key })
        assertEquals("character1", repository.state.currentTurn(repository))
    }
}