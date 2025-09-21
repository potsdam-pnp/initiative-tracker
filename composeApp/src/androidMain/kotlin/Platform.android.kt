import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.chooser.ChooserAction
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.lifecycleScope
import io.github.potsdam_pnp.initiative_tracker.BuildConfig
import io.github.potsdam_pnp.initiative_tracker.InitiativeTrackerApplication
import io.github.potsdam_pnp.initiative_tracker.MainActivity
import io.github.potsdam_pnp.initiative_tracker.R
import io.github.potsdam_pnp.initiative_tracker.ServerState
import io.github.potsdam_pnp.initiative_tracker.WifiAwareAvailableState
import io.github.potsdam_pnp.initiative_tracker.crdt.CompareResult
import kotlinx.coroutines.launch

class AndroidPlatform : Platform {
  override val name: String =
    "Android ${Build.VERSION.SDK_INT} (${BuildConfig.VERSION_NAME}-${BuildConfig.DistributionChannel}) (${BuildConfig.VERSION_CODE} ${BuildConfig.BUILD_TYPE})"

  @Composable
  override fun serverStatus(): ServerStatus {
    val app = LocalContext.current.applicationContext as InitiativeTrackerApplication
    val serverState1 by app.serverLifecycleManager._serverState.collectAsState()
    val serverState by serverState1.collectAsState()
    val wifiAware by app.wifiAwareConnectionManager.details.collectAsState()
    val wifiAwareS by
      app.wifiAwareConnectionManager.available(rememberCoroutineScope()).collectAsState()
    val wifiAwareState = "Nearby devices: ${wifiAwareS.name}"
    val version by app.repository.version.collectAsState()

    var downloading = 0
    var uploading = 0
    wifiAware.peers.values
      .filter { it.heartbeatState.allowSend() }
      .forEach { vc ->
        when (version.compare(vc.state)) {
          CompareResult.Equal -> {}
          CompareResult.Greater -> uploading += 1
          CompareResult.Incomparable -> {
            downloading += 1
            uploading += 1
          }
          CompareResult.Smaller -> downloading += 1
        }
      }

    return ServerStatus(
      isRunning =
        serverState is ServerState.Running ||
          (wifiAware.publish.isActive && wifiAware.subscribe.isActive),
      message =
        wifiAwareState +
          (if (serverState != ServerState.Stopped) "\n${serverState.message()}" else ""),
      isSupported = true,
      joinLinks = listOf(),
      connections = serverState.connectedClients() + wifiAware.peers.size,
      discoveredClients =
        ((serverState as? ServerState.Running)?.connectedClients ?: mapOf()).map {
          ConnectedClient(
            connectedViaServer = true,
            connectedViaClient = false,
            connectedViaWifiAware = false,
            id = it.key,
            name = it.key.pretty(),
            state = it.value,
            errorMsg = null,
          )
        } +
          wifiAware.peers.map {
            ConnectedClient(
              connectedViaServer = false,
              connectedViaClient = false,
              connectedViaWifiAware = true,
              id = it.value.clientIdentifier,
              name = it.value.clientIdentifier.pretty(),
              state = it.value.state,
              errorMsg = it.value.heartbeatState.pretty(),
            )
          },
      uploading = uploading,
      downloading = downloading,
    )
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

  @Composable
  override fun ServerSettings() {
    val application = LocalContext.current.applicationContext as InitiativeTrackerApplication
    val activity = LocalActivity.current as MainActivity
    val serverSettings by application.serverLifecycleManager.serverSettings.collectAsState()
    val state by application.wifiAwareConnectionManager._available.collectAsState()
    ListItem(
      headlineContent = {
        Text("Connect to nearby devices (${application.wifiAwareConnectionManager.maxMessageSize})")
      },
      trailingContent = {
        Switch(
          checked = serverSettings.wifiAwareEnabled,
          enabled = state.first != WifiAwareAvailableState.DeviceNotSupported,
          onCheckedChange = { newValue ->
            activity.lifecycleScope.launch {
              application.serverLifecycleManager.changeWifiAwareEnabled(newValue, activity)
            }
          },
        )
      },
      supportingContent = { Text("Current state: ${state.first} (${state.second.pretty()})") },
    )
  }

  @Composable
  override fun ServerSettingsBelow() {
    val application = LocalContext.current.applicationContext as InitiativeTrackerApplication
    val serverSettings by application.serverLifecycleManager.serverSettings.collectAsState()
    val activity = LocalActivity.current as MainActivity

    ListItem(
      headlineContent = { Text("Run server") },
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
          "Running server allows clients to connect. Not needed for nearby connections or when connecting as client."
        )
      },
    )
    ListItem(
      headlineContent = {
        TextField(
          serverSettings.minutesAfterAppClose.toString(),
          label = { Text("Number of minutes server will keep running after app has been closed") },
          enabled = !serverSettings.disableActivateServer,
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
    var showDebugInfo by remember { mutableStateOf(false) }
    ListItem(
      headlineContent = { Text("Show low-level debug information") },
      trailingContent = { Switch(showDebugInfo, onCheckedChange = { showDebugInfo = it }) },
      supportingContent = {
        if (showDebugInfo) {
          val details by application.wifiAwareConnectionManager.details.collectAsState()
          Text(
            "${details.publish.pretty("publish")}\n${details.subscribe.pretty("subscribe")}\n${details.sessionConfig.pretty("session config")}\nterminated:${details.terminated}"
          )
        }
      },
    )
  }

  @Composable
  override fun connectionStateClickableEnabled(): Boolean {
    val app = LocalContext.current.applicationContext as InitiativeTrackerApplication
    val scope = rememberCoroutineScope()
    val available by app.wifiAwareConnectionManager.available(scope).collectAsState()
    return available != WifiAwareAvailableState.DeviceNotSupported &&
      available != WifiAwareAvailableState.Unknown
  }

  @Composable
  override fun connectionStateOnClick(): () -> Unit {
    val app = LocalContext.current.applicationContext as InitiativeTrackerApplication
    val activity = LocalActivity.current as MainActivity
    val scope = rememberCoroutineScope()
    return { scope.launch { app.serverLifecycleManager.changeWifiAwareEnabled(true, activity) } }
  }
}

actual class PlatformContext(val context: Context)

actual fun getPlatform(): Platform = AndroidPlatform()
