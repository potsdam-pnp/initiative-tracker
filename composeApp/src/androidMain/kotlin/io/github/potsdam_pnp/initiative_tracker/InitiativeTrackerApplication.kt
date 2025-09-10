package io.github.potsdam_pnp.initiative_tracker

import android.app.Application
import android.content.Context
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.github.potsdam_pnp.initiative_tracker.crdt.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.acra.BuildConfig
import org.acra.config.dialog
import org.acra.config.mailSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra

class InitiativeTrackerApplication : Application() {
  val repository: Repository<Action, State> = Repository(State())
  val serverLifecycleManager = ServerLifecycleManager(this)
  val wifiAwareConnectionManager = WifiAwareConnectionManager(repository)

  override fun onCreate() {
    super.onCreate()
    Napier.base(DebugAntilog())

    wifiAwareConnectionManager.run(this, CoroutineScope(Job() + Dispatchers.Main.immediate))

    Napier.i("Application is created")
  }

  override fun attachBaseContext(base: Context) {
    super.attachBaseContext(base)

    initAcra {
      buildConfigClass = BuildConfig::class.java
      reportFormat = StringFormat.KEY_VALUE_LIST

      mailSender {
        mailTo = "initiative-tracker-crash-reports@schmitthenner.eu"
        subject = "Initiative Tracker Crash Report"
        // reportFileName = "CrashReport.txt"
        reportAsFile = false
      }

      dialog {
        title = "Initiative Tracker has crashed"
        text =
          "An unexpected error occurred forcing the application to stop. Please help us fix this by sending us error data."
        commentPrompt = "You might add your comments about the problem below:"
      }
    }
  }
}
