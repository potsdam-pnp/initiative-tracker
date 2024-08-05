import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.service.chooser.ChooserAction
import android.view.Choreographer.FrameData
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat.startActivity
import io.github.aakira.napier.Napier
import io.github.potsdam_pnp.initiative_tracker.MainActivity
import io.github.potsdam_pnp.initiative_tracker.R
import io.ktor.http.ContentType
import kotlinx.coroutines.flow.Flow

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.path
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import java.net.Inet4Address
import java.net.Inet6Address
import java.util.Formatter


object Server {
    val status = MutableStateFlow(
        ServerStatus(
            isRunning = false,
            message = "Server not running",
            isSupported = true,
            connections = 0
        )
    )

    var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    fun toggle(model: Model, context: Context) {
        if (status.value.isRunning) {
            stop()
        } else {
            start(model, context)
        }
    }

    private fun start(model: Model, context: Context) {
        server = embeddedServer(Netty, port = 8080) {
            install(WebSockets)
            routing {
                get("/") {
                    call.respondText("Initiative Tracker server running succesfully")
                }
                get("/app") {
                    val website = "https://potsdam-pnp.github.io/initiative-tracker"
                    call.respondText(
                        contentType = ContentType.Text.Html,
                        text = "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n"
                                + "    <meta charset=\"UTF-8\">\n    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
                                + "    <title>KotlinProject</title>\n"
                                + "    <link type=\"text/css\" rel=\"stylesheet\" href=\"$website/styles.css\">\n"
                                + "    <script type=\"application/javascript\" src=\"$website/composeApp.js\"></script>\n"
                                + "</head>\n<body>\n</body>\n</html>"
                    )
                }
                get("/composeApp.wasm") {
                    call.respondRedirect("https://potsdam-pnp.github.io/initiative-tracker/composeApp.wasm")
                }
                get("/composeResources/{...}") {
                    val newPath =
                        "https://potsdam-pnp.github.io/initiative-tracker" + call.request.origin.uri
                    call.respondRedirect(newPath)
                }
                webSocket("/ws") {
                    val job = launch {
                        model.state.collect {
                            if (sendUpdates.value) {
                                send(Frame.Text(serializeActions(it.actions)))
                            }
                        }
                    }

                    status.update { it.copy(connections = it.connections + 1) }

                    try {

                        val finish = launch {
                            status.first { !it.isRunning }
                        }

                        while (true) {
                            val nextFrame = async { incoming.receive() }

                            val frame = select<Frame?> {
                                finish.onJoin { null }
                                nextFrame.onAwait { it }
                            }

                            if (frame == null) {
                                break
                            } else {
                                val frameText = (frame as? Frame.Text)?.readText()
                                if (frameText != null) {
                                    val actions = deserializeActions(frameText)
                                    if (actions != null) {
                                        if (receiveUpdates.value) {
                                            model.receiveActions(actions)
                                        }
                                    }
                                }
                            }
                        }

                        Napier.w("Closing connection")

                        job.cancelAndJoin()
                    } finally {
                        status.update {
                            Napier.w("Updating connections")
                            it.copy(connections = it.connections - 1)
                        }
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
                    val connectors =
                        it.engine.resolvedConnectors().joinToString { it.host + ":" + it.port }
                    status.update {
                        it.copy(message = "Server running on $connectors")
                    }
                }
            }
        }

        ipAddressFromWifi(context)
    }

    private fun stop() {
        status.update {
            it.copy(isRunning = false, message = "Server stopping", joinLinks = emptyList())
        }
        server?.stop()
    }

    private fun ipAddressFromWifi(context: Context) {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val currentNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(currentNetwork)

        val linkProperties = connectivityManager.getLinkProperties(currentNetwork)
        val ipAddress = linkProperties?.linkAddresses?.mapNotNull { it.address }.orEmpty()

        val addressStrings = ipAddress.mapNotNull {
            // Convert IP address to a readable string (if needed)
            if (it is Inet4Address) {
                Formatter().format(
                    "%d.%d.%d.%d",
                    it.address[0].toInt() and 0xff,
                    it.address[1].toInt() and 0xff,
                    it.address[2].toInt() and 0xff,
                    it.address[3].toInt() and 0xff
                ).toString()
            } else if (it is Inet6Address) {
                val result = "[${it.toString().substring(1)}]"
                if (result.startsWith("[fe80")) {
                    // Link-local addresses aren't usable
                    null
                } else {
                    result
                }
            } else {
                it.toString()
            }
        }

        status.update {
            it.copy(joinLinks = addressStrings.map { JoinLink(it) })
        }
    }
}



class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"


    override fun isGeneratePlayerShortcutSupported(): Boolean = true

    override fun generatePlayerShortcut(context: PlatformContext, players: List<String>) {
        val shortcutManager = context.context.getSystemService(ShortcutManager::class.java)

        if (shortcutManager!!.isRequestPinShortcutSupported) {
            val uri = Uri.Builder().scheme("https").authority("potsdam-pnp.github.io").path("/initiative-tracker").fragment(players.joinToString(",")).build()
            val intent =  Intent(ACTION_VIEW, uri)

            val pinShortcutInfo = ShortcutInfo.Builder(context.context, "party-${players.joinToString(",")}")
                .setShortLabel("${players.first()}+${players.size-1}")
                .setLongLabel("Start initiative tracker for party of ${players.size} players: ${players.joinToString()}")
                .setIntent(intent)
                .setIcon(Icon.createWithResource(context.context, R.drawable.ic_launcher_background))
                .build()

            shortcutManager.requestPinShortcut(pinShortcutInfo, null)
        }
    }

    override val serverStatus: StateFlow<ServerStatus>
        get() = Server.status

    override fun toggleServer(model: Model, context: PlatformContext) {
        Server.toggle(model, context.context)
    }

    @Composable
    override fun getContext(): PlatformContext {
        return PlatformContext(LocalContext.current)
    }

    override fun shareLink(context: PlatformContext, link: JoinLink, allLinks: List<JoinLink>) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, link.toUrl())
            putExtra(Intent.EXTRA_TITLE, "Connection link - Join via ${link.host}")
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, "Share Connection Link to Initiative Tracker")

        val otherLinks = allLinks.filter { it != link }
        if (otherLinks.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val customActions = otherLinks.mapIndexed() { index, otherJoinLink ->
                ChooserAction.Builder(
                    Icon.createWithResource(context.context, R.drawable.ic_launcher_foreground),
                    "Share via ${otherJoinLink.host} instead",
                    PendingIntent.getActivity(
                        context.context,
                        index,
                        Intent(context.context, MainActivity::class.java).putExtra("forward_host", otherJoinLink.host),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
                    )
                ).build()
            }.toTypedArray()
            shareIntent.putExtra(Intent.EXTRA_CHOOSER_CUSTOM_ACTIONS, customActions)
        }

        startActivity(context.context, shareIntent, null)
    }
}

actual class PlatformContext(val context: Context)
actual fun getPlatform(): Platform = AndroidPlatform()