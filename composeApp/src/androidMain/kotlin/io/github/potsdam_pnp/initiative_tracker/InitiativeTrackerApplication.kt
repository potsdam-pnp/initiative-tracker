package io.github.potsdam_pnp.initiative_tracker

import android.app.Application
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleService
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.github.potsdam_pnp.initiative_tracker.crdt.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class InitiativeTrackerApplication: Application() {
    val repository: Repository<Action, State> = Repository(State())
    val connectionManager: ConnectionManager = ConnectionManagerAndroid(this, repository)
    val serverLifecycleManager = ServerLifecycleManager(this)

    override fun onCreate() {
        super.onCreate()
        Napier.base(DebugAntilog())

        Napier.i("Application is created")
    }
}