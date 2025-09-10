import androidx.compose.runtime.Composable
import io.github.potsdam_pnp.initiative_tracker.crdt.ClientIdentifier
import io.github.potsdam_pnp.initiative_tracker.crdt.VectorClock

data class JoinLink(val host: String) {
  fun toUrl(): String {
    val safeHost = if (host.contains(":") && !host.contains("[")) "[$host]" else host
    return "https://potsdam-pnp.github.io/initiative-tracker/app#server=$safeHost"
  }
}

data class ConnectedClient(
  val id: ClientIdentifier?,
  val state: VectorClock?,
  val connectedViaClient: Boolean,
  val connectedViaServer: Boolean,
  val connectedViaWifiAware: Boolean,
  val errorMsg: String? = null,
)

data class ServerStatus(
  val isRunning: Boolean,
  val message: String,
  val isSupported: Boolean,
  val joinLinks: List<JoinLink> = emptyList(),
  val connections: Int,
  val discoveredClients: List<ConnectedClient>,
)

val unsupportedPlatform =
  ServerStatus(
    false,
    "Server not supported on ${getPlatform().name}",
    isSupported = false,
    connections = 0,
    discoveredClients = listOf(),
  )

interface Platform {
  val name: String

  @Composable
  fun serverStatus(): ServerStatus {
    return unsupportedPlatform
  }

  @Composable fun getContext(): PlatformContext

  fun shareLink(context: PlatformContext, link: JoinLink, allLinks: List<JoinLink>) {}

  @Composable fun ServerSettings() {}

  @Composable fun ServerSettingsBelow() {}

  @Composable fun connectionStateClickableEnabled(): Boolean = false

  @Composable fun connectionStateOnClick(): () -> Unit = {}
}

expect fun getPlatform(): Platform

expect class PlatformContext
