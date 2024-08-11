package io.github.potsdam_pnp.initiative_tracker

import Model
import android.app.Application
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.github.potsdam_pnp.initiative_tracker.state.ActionWrapper
import io.github.potsdam_pnp.initiative_tracker.state.Snapshot
import io.github.potsdam_pnp.initiative_tracker.state.State

class InitiativeTrackerApplication: Application() {
    val snapshot: Snapshot<ActionWrapper, State> = Snapshot(State())
    val connectionManager: ConnectionManager = ConnectionManager(this, snapshot)

    override fun onCreate() {
        super.onCreate()
        Napier.base(DebugAntilog())

        Napier.i("Application is created")
    }
}