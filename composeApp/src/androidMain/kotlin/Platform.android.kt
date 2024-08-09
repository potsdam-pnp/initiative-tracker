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

    override val serverStatus: androidx.compose.runtime.State<ServerStatus>
        @Composable get() = (LocalContext.current as MainActivity).server!!.serverState

    override fun toggleServer(model: Model, context: PlatformContext) {
        (context.context as MainActivity).server!!.let {
            it.toggle(!it.serverState().isRunning)
        }
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