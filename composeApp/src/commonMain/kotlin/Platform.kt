import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf

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
    val hosts: List<String>,
    val port: Int
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

    fun toggleServer(model: Model, context: PlatformContext) {}
    val serverStatus: androidx.compose.runtime.State<ServerStatus> @Composable get() {
        return mutableStateOf(unsupportedPlatform)
    }

    @Composable
    fun getContext(): PlatformContext

    fun shareLink(context: PlatformContext, link: JoinLink, allLinks: List<JoinLink>) {}
}

expect fun getPlatform(): Platform
expect class PlatformContext