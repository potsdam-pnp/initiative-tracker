import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.view.Choreographer.FrameData
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.potsdam_pnp.initiative_tracker.R
import kotlinx.coroutines.flow.Flow

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread
import io.ktor.websocket.Frame


object Server {
    val status = MutableStateFlow(ServerStatus(
        isRunning = false,
        message = "Server not running",
        isSupported = true
    ))

    var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    fun toggle(state: Flow<State>) {
        if (status.value.isRunning) {
            stop()
        } else {
            start(state)
        }
    }

    private fun start(state: Flow<State>) {
        server = embeddedServer(Netty, port = 8080) {
            install(WebSockets)
            routing {
                get("/") {
                    call.respondText("Initiative Tracker server running succesfully")
                }
                webSocket("/ws") {
                    state.collect {
                        send(Frame.Text(serializeActions(it.actions)))
                    }
                }

            }
        }.also {
            it.start(wait = false)
            status.update {
                it.copy(isRunning = true, message = "Server starting")
            }

            thread {
                runBlocking {
                    val connectors = it.engine.resolvedConnectors().joinToString { it.host + ":" + it.port }
                    status.update {
                        it.copy(message = "Server running on $connectors")
                    }
                }
            }
        }
    }

    private fun stop() {
        server?.stop()
        status.update {
            it.copy(isRunning = false, message = "Server stopped")
        }
    }
}


class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"

    @Composable
    override fun DropdownMenuItemPlayerShortcut(enabled: Boolean, playerList: () -> List<String>) {
        val context = LocalContext.current
        DropdownMenuItem(onClick = {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java)

            if (shortcutManager!!.isRequestPinShortcutSupported) {
                val players = playerList()
                val uri = Uri.Builder().scheme("https").authority("potsdam-pnp.github.io").path("/initiative-tracker").fragment(players.joinToString(",")).build()
                val intent =  Intent(ACTION_VIEW, uri)

                val pinShortcutInfo = ShortcutInfo.Builder(context, "party-${players.joinToString(",")}")
                    .setShortLabel("${players.first()}+${players.size-1}")
                    .setLongLabel("Start initiative tracker for party of ${players.size} players: ${players.joinToString()}")
                    .setIntent(intent)
                    .setIcon(Icon.createWithResource(context, R.drawable.ic_launcher_background))
                    .build()

                shortcutManager.requestPinShortcut(pinShortcutInfo, null)
            }
        }, enabled = enabled, text = {
            Text("Add players to home screen")
        })
    }

    override val serverStatus: StateFlow<ServerStatus>
        get() = Server.status

    override fun toggleServer(state: Flow<State>) {
        Server.toggle(state)
    }
}

actual fun getPlatform(): Platform = AndroidPlatform()