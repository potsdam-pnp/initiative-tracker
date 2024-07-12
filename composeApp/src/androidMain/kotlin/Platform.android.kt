import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.potsdam_pnp.initiative_tracker.R

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"

    @Composable
    override fun DropdownMenuItemPlayerShortcut(enabled: Boolean, playerList: () -> List<String>) {
        val context = LocalContext.current;
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
        }, enabled = enabled) {
            Text("Add players to home screen")
        }
    }
}

actual fun getPlatform(): Platform = AndroidPlatform()