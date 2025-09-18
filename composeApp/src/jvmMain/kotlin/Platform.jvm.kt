import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.potsdam_pnp.initiative_tracker.ServerState

class JVMPlatform : Platform {
  override val name: String = "Java ${System.getProperty("java.version")}"

  @Composable override fun getContext() = Unit

  @Composable
  override fun serverStatus(): ServerStatus {
    val serverState by Global.server!!.state.collectAsState()
    return ServerStatus(
      isRunning = serverState is ServerState.Running,
      message = serverState.message(),
      isSupported = true,
      joinLinks = listOf(),
      connections = serverState.connectedClients(),
      discoveredClients =
        ((serverState as? ServerState.Running)?.connectedClients ?: mapOf()).map {
          ConnectedClient(
            name = "Connected client",
            id = it.key,
            state = it.value,
            connectedViaServer = true,
            connectedViaClient = false,
            connectedViaWifiAware = false,
            errorMsg = null,
          )
        },
      uploading = 0,
      downloading = 0,
    )
  }
}

actual fun getPlatform(): Platform = JVMPlatform()

actual typealias PlatformContext = Unit
