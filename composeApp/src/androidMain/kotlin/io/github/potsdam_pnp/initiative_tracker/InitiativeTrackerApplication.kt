package io.github.potsdam_pnp.initiative_tracker

import Model
import android.app.Application
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

class InitiativeTrackerApplication: Application() {

    val model: Model = Model(null)
    val connectionManager: ConnectionManager = ConnectionManager(this, model.snapshot)

    override fun onCreate() {
        super.onCreate()
        Napier.base(DebugAntilog())

        Napier.i("Application is created")
    }
}