import io.github.potsdam_pnp.initiative_tracker.Action
import io.github.potsdam_pnp.initiative_tracker.AddCharacter
import io.github.potsdam_pnp.initiative_tracker.ChangeInitiative
import io.github.potsdam_pnp.initiative_tracker.CharacterId
import io.github.potsdam_pnp.initiative_tracker.Encoders
import io.github.potsdam_pnp.initiative_tracker.Turn
import io.github.potsdam_pnp.initiative_tracker.TurnAction
import io.github.potsdam_pnp.initiative_tracker.crdt.Repository
import io.github.potsdam_pnp.initiative_tracker.State
import io.github.potsdam_pnp.initiative_tracker.crdt.ClientIdentifier
import io.github.potsdam_pnp.initiative_tracker.crdt.Dot
import io.github.potsdam_pnp.initiative_tracker.crdt.Message
import io.github.potsdam_pnp.initiative_tracker.deserializeAction
import io.github.potsdam_pnp.initiative_tracker.serializeAction
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.checkAll
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
            ChangeInitiative("character1", 5),
            ChangeInitiative("character2", 10)
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
            ChangeInitiative("character1", 5),
            ChangeInitiative("character2", 10),
            Turn(TurnAction.StartTurn(CharacterId("character1")), null)
        )
        val predicted = repository.state.predictNextTurns(withCurrent = false, repository)
        val predicted2 = repository.state.predictNextTurns(withCurrent = true, repository)
        assertEquals(listOf("character2" to 0, "character1" to 1), predicted.map { it.key to it.turn })
        assertEquals(listOf("character1" to 0, "character2" to 0), predicted2.map { it.key to it.turn })
        assertEquals(CharacterId("character1"), repository.state.currentTurn(repository))
    }

    @Test
    fun delay() {
        val repository = Repository(State())
        repository.produce { dots ->
            listOf(
                ChangeInitiative("character", 5),
                Turn(TurnAction.StartTurn(CharacterId("character")), null),
                Turn(TurnAction.Delay(CharacterId("character")), dots(1))
            )
        }

        val predicted = repository.state.predictNextTurns(withCurrent = false, repository)
        assertEquals(listOf("character"), predicted.map { it.key })
    }

    @Test
    fun startTurn() {
        val repository = Repository(State())
        repository.produce { dots ->
            listOf(
                ChangeInitiative("character", 5),
                Turn(TurnAction.StartTurn(CharacterId("character")), null),
                Turn(TurnAction.StartTurn(CharacterId("character")), dots(1))
            )
        }

        val predicted = repository.state.predictNextTurns(withCurrent = false, repository)
        assertEquals(listOf("character"), predicted.map { it.key })

    }

    @Test
    fun checkDecode() {
        val actions = listOf(
            Turn(TurnAction.StartTurn(CharacterId("character")), null),
            Turn(TurnAction.ResolveConflicts, Dot(ClientIdentifier("af6f6f"), 2))
        )
        for (action in actions) {
            assertEquals(deserializeAction(serializeAction(action)), action)
        }
    }
}

class DecodeEncodeTests: StringSpec({
    "Decode encoded value returns the same value" {
        checkAll<AddCharacter> {
            deserializeAction(serializeAction(it)) shouldBe it
        }
    }
})