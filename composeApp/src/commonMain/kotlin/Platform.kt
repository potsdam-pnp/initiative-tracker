import androidx.compose.runtime.Composable
import io.github.potsdam_pnp.initiative_tracker.state.VectorClock

data class JoinLink(
    val host: String
) {
    fun toUrl(): String {
        val safeHost =
            if (host.contains(":") && !host.contains("["))
                "[$host]"
            else
                host
        return "https://potsdam-pnp.github.io/initiative-tracker/app#server=$safeHost"
    }
}

data class DiscoveredClient(
    val name: String,
    val hosts: List<String>?,
    val port: Int?,
    val state: VectorClock?,
    val isServerConnected: Boolean,
    val isClientConnected: Boolean,
    val errorMsg: String? = null
)

data class ServerStatus(
    val isRunning: Boolean,
    val message: String,
    val isSupported: Boolean,
    val joinLinks: List<JoinLink> = emptyList(),
    val connections: Int,
    val discoveredClients: List<DiscoveredClient>
)

val unsupportedPlatform = ServerStatus(false, "Server not supported on ${getPlatform().name}", isSupported = false, connections = 0, discoveredClients = listOf())

interface Platform {
    val name: String

    fun isGeneratePlayerShortcutSupported(): Boolean = false
    fun generatePlayerShortcut(context: PlatformContext, playerList: List<String>) {}

    @Composable fun serverStatus(): ServerStatus {
        return unsupportedPlatform
    }

    @Composable
    fun getContext(): PlatformContext

    fun shareLink(context: PlatformContext, link: JoinLink, allLinks: List<JoinLink>) {}
}

expect fun getPlatform(): Platform
expect class PlatformContext