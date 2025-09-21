package io.github.potsdam_pnp.initiative_tracker

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class ServerSettings(
    val isAllowed: Boolean = false,
    val minutesAfterAppClose: Int = 60,
    val disableActivateServer: Boolean = true,
    val wifiAwareEnabled: Boolean = false,
)

sealed class ServerEvent

data object KeepRunning : ServerEvent()

data object StopServer : ServerEvent()

data class KillIn(val minutes: Int) : ServerEvent()


open class ServerLifecycleManagerStub(val application: InitiativeTrackerApplication) {
    protected val _serverSettings = MutableStateFlow(ServerSettings())
    val serverSettings: StateFlow<ServerSettings> = _serverSettings

    val _serverState: MutableStateFlow<StateFlow<ServerState>> =
        MutableStateFlow(MutableStateFlow(ServerState.Stopped))

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

    open fun makeSureServerIsRunning(activity: MainActivity) {
        if (serverSettings.value.isAllowed) {
            throw IllegalStateException()
        }
    }

    open fun changeMinutesAfterAppClose(value: Int) {
        throw IllegalStateException()
    }


    open fun stopServerAfterDelay() {}

    open fun changeServerIsAllowed(activity: MainActivity, newValue: Boolean) {
        throw IllegalStateException()
    }
}