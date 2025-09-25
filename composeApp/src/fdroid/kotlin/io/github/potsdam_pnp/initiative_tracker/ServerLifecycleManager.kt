package io.github.potsdam_pnp.initiative_tracker

import android.content.Intent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.update

class ServerLifecycleManager(a: InitiativeTrackerApplication) : ServerLifecycleManagerStub(a) {
  val serverEventChannel = Channel<ServerEvent>()

  override fun makeSureServerIsRunning(activity: MainActivity) {
    if (serverSettings.value.isAllowed) {
      val r = serverEventChannel.trySend(KeepRunning)
      if (r.isFailure) {
        activity.startService(Intent(activity, ConnectionService::class.java))
      }
    }
  }

  override fun stopServerAfterDelay() {
    serverEventChannel.trySend(KillIn(serverSettings.value.minutesAfterAppClose))
  }

  fun stopServer() {
    serverEventChannel.trySend(StopServer)
  }

  override fun changeServerIsAllowed(activity: MainActivity, isAllowed: Boolean) {
    _serverSettings.value = _serverSettings.value.copy(isAllowed = isAllowed)
    if (!isAllowed) {
      stopServer()
    } else {
      makeSureServerIsRunning(activity)
    }
  }

  override fun changeMinutesAfterAppClose(minutes: Int) {
    _serverSettings.value = _serverSettings.value.copy(minutesAfterAppClose = minutes)
  }

  fun startShuttingDown() {
    _serverSettings.update { it.copy(disableActivateServer = true) }
  }

  fun finishedShuttingDown() {
    _serverSettings.update { it.copy(disableActivateServer = false) }
  }

  init {
    _serverSettings.update { it.copy(disableActivateServer = false) }
  }
}
