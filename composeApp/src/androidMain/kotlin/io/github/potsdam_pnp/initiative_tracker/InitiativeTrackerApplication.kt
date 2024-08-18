package io.github.potsdam_pnp.initiative_tracker

import android.app.Application
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.github.potsdam_pnp.initiative_tracker.crdt.Repository
import io.github.potsdam_pnp.initiative_tracker.state.State

class InitiativeTrackerApplication: Application() {
    val repository: Repository<Action, State> = Repository(State())
    val connectionManager: ConnectionManager = ConnectionManagerAndroid(this, repository)

    override fun onCreate() {
        super.onCreate()
        Napier.base(DebugAntilog())

        Napier.i("Application is created")
    }
}