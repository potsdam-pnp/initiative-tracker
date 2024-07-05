import androidx.compose.runtime.Composable

data class PartyCharacter(
    val name: String,
)

data class Party(
    val name: String,
    val characters: List<PartyCharacter>
)

@Composable
fun ShowParty(party: Party) {

}