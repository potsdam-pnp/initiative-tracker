import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


data class ServerStatus(
    val isRunning: Boolean,
    val message: String,
    val isSupported: Boolean,
    val joinLinks: List<String> = emptyList(),
    val connections: Int
)

val unsupportedPlatformFlow = MutableStateFlow(ServerStatus(false, "Server not supported on ${getPlatform().name}", isSupported = false, connections = 0))

interface Platform {
    val name: String

    fun isGeneratePlayerShortcutSupported(): Boolean = false
    fun generatePlayerShortcut(context: PlatformContext, playerList: List<String>) {}

    fun toggleServer(model: Model, context: PlatformContext) {}
    val serverStatus: StateFlow<ServerStatus> get() { return unsupportedPlatformFlow }

    @Composable
    fun getContext(): PlatformContext

    fun shareLink(context: PlatformContext, link: String) {}
}

expect fun getPlatform(): Platform
expect class PlatformContext