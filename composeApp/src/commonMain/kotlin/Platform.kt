import androidx.compose.runtime.Composable

interface Platform {
    val name: String

    @Composable
    fun DropdownMenuItemPlayerShortcut(enabled: Boolean, playerList: () -> List<String>) {

    }
}

expect fun getPlatform(): Platform