import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.service.chooser.ChooserAction
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.lifecycleScope
import io.github.potsdam_pnp.initiative_tracker.InitiativeTrackerApplication
import io.github.potsdam_pnp.initiative_tracker.MainActivity
import io.github.potsdam_pnp.initiative_tracker.R
import io.github.potsdam_pnp.initiative_tracker.WifiAwareAvailableState
import io.github.potsdam_pnp.initiative_tracker.crdt.VectorClock
import io.github.potsdam_pnp.initiative_tracker.toServerStatus
import kotlinx.coroutines.launch

class AndroidPlatform : Platform {
  override val name: String = "Android ${Build.VERSION.SDK_INT}"

  override fun isGeneratePlayerShortcutSupported(): Boolean = true

  override fun generatePlayerShortcut(context: PlatformContext, players: List<String>) {
    val shortcutManager = context.context.getSystemService(ShortcutManager::class.java)

    if (shortcutManager!!.isRequestPinShortcutSupported) {
      val uri =
        Uri.Builder()
          .scheme("https")
          .authority("potsdam-pnp.github.io")
          .path("/initiative-tracker")
          .fragment(players.joinToString(","))
          .build()
      val intent = Intent(ACTION_VIEW, uri)

      val pinShortcutInfo =
        ShortcutInfo.Builder(context.context, "party-${players.joinToString(",")}")
          .setShortLabel("${players.first()}+${players.size-1}")
          .setLongLabel(
            "Start initiative tracker for party of ${players.size} players: ${players.joinToString()}"
          )
          .setIntent(intent)
          .setIcon(Icon.createWithResource(context.context, R.drawable.ic_launcher_background))
          .build()

      shortcutManager.requestPinShortcut(pinShortcutInfo, null)
    }
  }

  @Composable
  override fun serverStatus(): ServerStatus {
    val app = LocalContext.current.applicationContext as InitiativeTrackerApplication
    val connectionStates by app.connectionManager.connectionStates.collectAsState()
    val serviceInfoStates by app.connectionManager.serviceInfoState.collectAsState()
    val name by app.connectionManager.name.collectAsState()
    return toServerStatus(connectionStates, serviceInfoStates, name)
  }

  @Composable
  override fun getContext(): PlatformContext {
    return PlatformContext(LocalContext.current)
  }

  override fun shareLink(context: PlatformContext, link: JoinLink, allLinks: List<JoinLink>) {
    val sendIntent: Intent =
      Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, link.toUrl())
        putExtra(Intent.EXTRA_TITLE, "Connection link - Join via ${link.host}")
        type = "text/plain"
      }

    val shareIntent =
      Intent.createChooser(sendIntent, "Share Connection Link to Initiative Tracker")

    val otherLinks = allLinks.filter { it != link }
    if (otherLinks.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      val customActions =
        otherLinks
          .mapIndexed() { index, otherJoinLink ->
            ChooserAction.Builder(
                Icon.createWithResource(context.context, R.drawable.ic_notification),
                "Share via ${otherJoinLink.host} instead",
                PendingIntent.getActivity(
                  context.context,
                  index,
                  Intent(context.context, MainActivity::class.java)
                    .putExtra("forward_host", otherJoinLink.host),
                  PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT,
                ),
              )
              .build()
          }
          .toTypedArray()
      shareIntent.putExtra(Intent.EXTRA_CHOOSER_CUSTOM_ACTIONS, customActions)
    }

    startActivity(context.context, shareIntent, null)
  }

  private fun prettyClock(remote: VectorClock, here: VectorClock): String {
    val count = remote.clock.values.sum()
    val ahead =
      remote.clock.mapValues { (k, v) ->
        val vv = here.clock[k] ?: -1
        if (vv >= v) vv - v else 0
      }
    val behind =
      here.clock.mapValues { (k, v) ->
        val vv = remote.clock[k] ?: -1
        if (vv >= v) vv - v else 0
      }
    return "$count versions, $ahead ahead, $behind behind"
  }

  @Composable
  override fun ServerSettings() {
    val application = LocalContext.current.applicationContext as InitiativeTrackerApplication
    val activity = LocalActivity.current as MainActivity
    val serverSettings by application.serverLifecycleManager.serverSettings.collectAsState()
    val scope = rememberCoroutineScope()
    val state by application.wifiAwareConnectionManager.available(scope).collectAsState()
    val details by application.wifiAwareConnectionManager.details.collectAsState()
    ListItem(
      headlineContent = { Text("Connect to nearby devices") },
      trailingContent = {
        Switch(
          checked = serverSettings.wifiAwareEnabled,
          enabled = state != WifiAwareAvailableState.DeviceNotSupported,
          onCheckedChange = { newValue ->
            activity.lifecycleScope.launch {
              application.serverLifecycleManager.changeWifiAwareEnabled(newValue, activity)
            }
          },
        )
      },
      supportingContent = {
        Text(
          "Current state: $state\n${details.publish.pretty("publish")}\n${details.subscribe.pretty("subscribe")}"
        )
      },
    )
    for (clock in details.peers.values) {
      ListItem(
        headlineContent = { Text("Connected client") },
        supportingContent = {
          val vc by application.repository.version.collectAsState()
          Text(prettyClock(clock, vc))
        },
      )
    }
    HorizontalDivider()
    ListItem(
      headlineContent = { Text("Allow to run server") },
      trailingContent = {
        Switch(
          checked = serverSettings.isAllowed,
          enabled = serverSettings.isAllowed || !serverSettings.disableActivateServer,
          onCheckedChange = { newValue ->
            application.serverLifecycleManager.changeServerIsAllowed(activity, newValue)
          },
        )
      },
      supportingContent = {
        Text(
          "Server is needed to let other devices and clients join and share the initiative tracker state."
        )
      },
    )
    ListItem(
      headlineContent = {
        TextField(
          serverSettings.minutesAfterAppClose.toString(),
          label = { Text("Number of minutes server will keep running after app has been closed") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          onValueChange = { newValue ->
            val value = newValue.toIntOrNull()
            if (value != null) {
              application.serverLifecycleManager.changeMinutesAfterAppClose(value)
            }
          },
        )
      }
    )
  }
}

actual class PlatformContext(val context: Context)

actual fun getPlatform(): Platform = AndroidPlatform()
