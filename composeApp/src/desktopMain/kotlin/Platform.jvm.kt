import androidx.compose.runtime.Composable

class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    @Composable
    override fun getContext() = Unit
}

actual fun getPlatform(): Platform = JVMPlatform()
actual typealias PlatformContext = Unit