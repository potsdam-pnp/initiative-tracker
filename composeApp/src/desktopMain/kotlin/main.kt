import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.github.potsdam_pnp.initiative_tracker.ClientConnections
import io.github.potsdam_pnp.initiative_tracker.ConnectionManager
import io.github.potsdam_pnp.initiative_tracker.Server
import io.github.potsdam_pnp.initiative_tracker.crdt.Repository
import io.github.potsdam_pnp.initiative_tracker.state.State
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
    val repository = Repository(State())
    connectionManager = ConnectionManagerDesktop()
    val server = Server("Unnamed", repository, connectionManager!!)
    val clientConnections = ClientConnections(repository, connectionManager!!)

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
            val _model = viewModel { Model(repository, null) }
            App()
        }
    }
}