import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.github.potsdam_pnp.initiative_tracker.ClientConnections
import io.github.potsdam_pnp.initiative_tracker.ConnectionInformation
import io.github.potsdam_pnp.initiative_tracker.ConnectionManager
import io.github.potsdam_pnp.initiative_tracker.ConnectionState
import io.github.potsdam_pnp.initiative_tracker.Server
import io.github.potsdam_pnp.initiative_tracker.ServiceInfo
import io.github.potsdam_pnp.initiative_tracker.state.ClientIdentifier
import io.github.potsdam_pnp.initiative_tracker.state.Snapshot
import io.github.potsdam_pnp.initiative_tracker.state.State
import io.github.potsdam_pnp.initiative_tracker.state.VectorClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.lang.Thread.sleep
import kotlin.concurrent.thread


class ConnectionManagerDesktop(): ConnectionManager() {
    override fun unregisterService() {
        Napier.i("Unregister service")
    }
    override fun registerService(name: String, resolvedPort: Int) {
        Napier.i("register service")
        this.name.update { "Server running on port $resolvedPort" }
    }
}

var connectionManager: ConnectionManager? = null

fun main() {
    Napier.base(DebugAntilog())
    val snapshot = Snapshot(State())
    connectionManager = ConnectionManagerDesktop()
    val server = Server("Unnamed", snapshot, connectionManager!!)
    val clientConnections = ClientConnections(snapshot, connectionManager!!)

    thread {
        runBlocking {
            launch {
                server.run()
            }
            launch {
                clientConnections.run(this)
            }
            launch {
                connectionManager!!.run()
            }
        }
    }

    thread {
        sleep(1000)
        server.toggle(true)
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Initiative Tracker",
        ) {
            val _model = viewModel { Model(snapshot, null) }
            App()
        }
    }
}