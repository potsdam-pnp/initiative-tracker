import androidx.compose.runtime.Composable
import platform.UIKit.UIDevice

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
    @Composable
    override fun getContext() = Unit

}

actual fun getPlatform(): Platform = IOSPlatform()
actual typealias PlatformContext = Unit