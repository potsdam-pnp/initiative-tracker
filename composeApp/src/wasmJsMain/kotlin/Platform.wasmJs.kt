import androidx.compose.runtime.Composable

class WasmPlatform: Platform {
    override val name: String = "Web with Kotlin/Wasm"
    @Composable
    override fun getContext() = Unit

}

actual fun getPlatform(): Platform = WasmPlatform()
actual typealias PlatformContext = Unit