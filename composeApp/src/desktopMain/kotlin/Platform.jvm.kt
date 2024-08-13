import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.potsdam_pnp.initiative_tracker.toServerStatus

class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    @Composable
    override fun getContext() = Unit

    @Composable
    override fun serverStatus(): ServerStatus {
        val connectionStates by connectionManager!!.connectionStates.collectAsState()
        val serviceInfoStates by connectionManager!!.serviceInfoState.collectAsState()
        val name by connectionManager!!.name.collectAsState()
        return toServerStatus(connectionStates, serviceInfoStates, name)
    }

}

actual fun getPlatform(): Platform = JVMPlatform()
actual typealias PlatformContext = Unit