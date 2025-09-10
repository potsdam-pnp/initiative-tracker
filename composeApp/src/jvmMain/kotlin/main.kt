import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.github.potsdam_pnp.initiative_tracker.Server
import io.github.potsdam_pnp.initiative_tracker.State
import io.github.potsdam_pnp.initiative_tracker.crdt.Repository
import java.lang.Thread.sleep
import kotlin.concurrent.thread
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object Global {
  var server: Server? = null
}

fun main() {
  Napier.base(DebugAntilog())
  val repository = Repository(State())
  val server = Server(repository)
  Global.server = server

  thread { runBlocking { launch { server.run() } } }

  thread {
    sleep(1000)
    server.toggle(true)
  }

  application {
    Window(onCloseRequest = ::exitApplication, title = "Initiative Tracker") {
      val _model = viewModel { Model(repository, null, null) }
      App()
    }
  }
}
