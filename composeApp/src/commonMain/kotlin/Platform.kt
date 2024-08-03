import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


data class ServerStatus(
    val isRunning: Boolean,
    val message: String,
    val isSupported: Boolean,
)

val unsupportedPlatformFlow = MutableStateFlow(ServerStatus(false, "Server not supported on ${getPlatform().name}", isSupported = false))

interface Platform {
    val name: String

    @Composable
    fun DropdownMenuItemPlayerShortcut(enabled: Boolean, playerList: () -> List<String>) {

    }

    fun toggleServer(state: Flow<State>) {}
    val serverStatus: StateFlow<ServerStatus> get() { return unsupportedPlatformFlow }
}

expect fun getPlatform(): Platform