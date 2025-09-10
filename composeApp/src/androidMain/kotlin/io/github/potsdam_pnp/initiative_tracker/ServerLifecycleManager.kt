package io.github.potsdam_pnp.initiative_tracker

import android.content.Intent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class ServerSettings(
  val isAllowed: Boolean = false,
  val minutesAfterAppClose: Int = 60,
  val disableActivateServer: Boolean = false,
  val wifiAwareEnabled: Boolean = false,
)

sealed class ServerEvent

data object KeepRunning : ServerEvent()

data object StopServer : ServerEvent()

data class KillIn(val minutes: Int) : ServerEvent()

class ServerLifecycleManager(val application: InitiativeTrackerApplication) {
  private val _serverSettings = MutableStateFlow(ServerSettings())
  val serverSettings: StateFlow<ServerSettings> = _serverSettings
  val serverEventChannel = Channel<ServerEvent>()

  fun makeSureServerIsRunning(activity: MainActivity) {
    if (serverSettings.value.isAllowed) {
      val r = serverEventChannel.trySend(KeepRunning)
      if (r.isFailure) {
        activity.startService(Intent(activity, ConnectionService::class.java))
      }
    }
  }

  fun stopServerAfterDelay() {
    serverEventChannel.trySend(KillIn(serverSettings.value.minutesAfterAppClose))
  }

  fun stopServer() {
    serverEventChannel.trySend(StopServer)
  }

  fun changeServerIsAllowed(activity: MainActivity, isAllowed: Boolean) {
    _serverSettings.value = _serverSettings.value.copy(isAllowed = isAllowed)
    if (!isAllowed) {
      stopServer()
    } else {
      makeSureServerIsRunning(activity)
    }
  }

  fun changeMinutesAfterAppClose(minutes: Int) {
    _serverSettings.value = _serverSettings.value.copy(minutesAfterAppClose = minutes)
  }

  fun startShuttingDown() {
    _serverSettings.update { it.copy(disableActivateServer = true) }
  }

  fun finishedShuttingDown() {
    _serverSettings.update { it.copy(disableActivateServer = false) }
  }

  suspend fun changeWifiAwareEnabled(value: Boolean, activity: MainActivity) {
    _serverSettings.update { it.copy(wifiAwareEnabled = value) }
    if (value) {
      val permission = application.wifiAwareConnectionManager.neededMissingPermission(activity)
      if (permission != null) {
        activity.permissionRequestLauncher.launch(permission)
      }
      application.wifiAwareConnectionManager.start()
    } else {
      application.wifiAwareConnectionManager.stop()
    }
  }
}
