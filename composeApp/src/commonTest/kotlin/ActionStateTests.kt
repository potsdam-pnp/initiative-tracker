import io.github.potsdam_pnp.initiative_tracker.Action
import io.github.potsdam_pnp.initiative_tracker.AddCharacter
import io.github.potsdam_pnp.initiative_tracker.ChangeInitiative
import io.github.potsdam_pnp.initiative_tracker.ChangePlayerCharacter
import io.github.potsdam_pnp.initiative_tracker.CharacterId
import io.github.potsdam_pnp.initiative_tracker.Encoders
import io.github.potsdam_pnp.initiative_tracker.State
import io.github.potsdam_pnp.initiative_tracker.Turn
import io.github.potsdam_pnp.initiative_tracker.TurnAction
import io.github.potsdam_pnp.initiative_tracker.crdt.ClientIdentifier
import io.github.potsdam_pnp.initiative_tracker.crdt.Dot
import io.github.potsdam_pnp.initiative_tracker.crdt.Message
import io.github.potsdam_pnp.initiative_tracker.crdt.Operation
import io.github.potsdam_pnp.initiative_tracker.crdt.OperationMetadata
import io.github.potsdam_pnp.initiative_tracker.crdt.Repository
import io.github.potsdam_pnp.initiative_tracker.crdt.VectorClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActionStateTests {
  @Test
  fun emptyActions() {
    val repository = Repository(State())
    val predicted = repository.state.predictNextTurns(withCurrent = false, repository = repository)
    assertEquals(emptyList(), predicted)
    assertNull(repository.state.currentTurn(repository))
  }

  @Test
  fun twoCharacters() {
    val character1 = CharacterId(Dot(ClientIdentifier.new(), 1))
    val character2 = CharacterId(Dot(ClientIdentifier.new(), 2))
    val repository = Repository(State())
    repository.produce(ChangeInitiative(character1, 5), ChangeInitiative(character2, 10))
    val predicted = repository.state.predictNextTurns(withCurrent = false, repository = repository)
    assertEquals(listOf(character2, character1), predicted.map { it.key })
    assertEquals(listOf(0, 0), predicted.map { it.turn })

    assertNull(repository.state.currentTurn(repository))
  }

  @Test
  fun twoCharacters2() {
    val repository = Repository(State())
    val character1 = CharacterId(Dot(ClientIdentifier.new(), 1))
    val character2 = CharacterId(Dot(ClientIdentifier.new(), 2))
    repository.produce(
      ChangeInitiative(character1, 5),
      ChangeInitiative(character2, 10),
      Turn(TurnAction.StartTurn(character1), null),
    )
    val predicted = repository.state.predictNextTurns(withCurrent = false, repository = repository)
    val predicted2 = repository.state.predictNextTurns(withCurrent = true, repository = repository)
    assertEquals(listOf(character2 to 0, character1 to 1), predicted.map { it.key to it.turn })
    assertEquals(listOf(character1 to 0, character2 to 0), predicted2.map { it.key to it.turn })
    assertEquals(character1, repository.state.currentTurn(repository))
  }

  @Test
  fun delay() {
    val repository = Repository(State())
    val character = CharacterId(Dot(ClientIdentifier.new(), 1))
    repository.produce { dots ->
      listOf(
        ChangeInitiative(character, 5),
        Turn(TurnAction.StartTurn(character), null),
        Turn(TurnAction.Delay(character), dots(1)),
      )
    }

    val predicted = repository.state.predictNextTurns(withCurrent = false, repository = repository)
    assertEquals(listOf(character), predicted.map { it.key })
  }

  @Test
  fun startTurn() {
    val repository = Repository(State())
    val character = CharacterId(Dot(ClientIdentifier.new(), 1))
    repository.produce { dots ->
      listOf(
        ChangeInitiative(character, 5),
        Turn(TurnAction.StartTurn(character), null),
        Turn(TurnAction.StartTurn(character), dots(1)),
      )
    }

    val predicted = repository.state.predictNextTurns(withCurrent = false, repository = repository)
    assertEquals(listOf(character), predicted.map { it.key })
  }

  @Test
  fun checkDecode() {
    val client1 = ClientIdentifier.new()
    val characterId = CharacterId(Dot(client1, 1))
    val clock = VectorClock(mapOf(client1 to 2))

    val actions: List<Operation<Action>> =
      listOf(
          AddCharacter,
          ChangePlayerCharacter(characterId, true),
          Turn(TurnAction.StartTurn(characterId), null),
          Turn(TurnAction.ResolveConflicts, Dot(client1, 2)),
        )
        .mapIndexed { index, action ->
          Operation(OperationMetadata(VectorClock(mapOf(client1 to (index + 1))), client1), action)
        }

    val sendVersions = Message.SendVersions(clock, actions, null, ClientIdentifier.new())
    assertEquals(Encoders.decodePb(Encoders.encodePb(sendVersions)), sendVersions)
  }
}

/*
class DecodeEncodeTests :
  StringSpec({
    "Decode encod SendVersions return the same value" {
      checkAll<AddCharacter> { deserializeAction(serializeAction(it)) shouldBe it }
    }
  })
*/
