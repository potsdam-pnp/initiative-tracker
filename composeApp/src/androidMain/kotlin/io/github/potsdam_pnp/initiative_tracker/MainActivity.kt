package io.github.potsdam_pnp.initiative_tracker

import App
import JoinLink
import Model
import PersistData
import PlatformContext
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import getPlatform
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.MainScope
import org.json.JSONArray

class SharedPreferencesPersistData(val sharedPreferences: SharedPreferences) : PersistData {
  override fun fetchKnownPlayerCharacters(): List<String> {
    val players = JSONArray(sharedPreferences.getString("players", "[]"))
    return (0 until players.length()).map { players.getString(it) }
  }

  override fun storeKnownPlayerCharacters(data: List<String>) {
    sharedPreferences.edit { putString("players", JSONArray(data.toTypedArray()).toString(0)) }
  }
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    Napier.base(DebugAntilog())
    Napier.w("It works Napier")

    enableEdgeToEdge()

    super.onCreate(savedInstanceState)

    val persistData =
      SharedPreferencesPersistData(getSharedPreferences("knownPlayerCharacters", MODE_PRIVATE))

    val factory = viewModelFactory {
      initializer {
        Model((application as InitiativeTrackerApplication).repository, persistData, null)
      }
    }
    ViewModelProvider.create(viewModelStore, factory)[Model::class]

    setContent { App(null) }
  }

  override fun onStart() {
    super.onStart()

    (application as InitiativeTrackerApplication)
      .serverLifecycleManager
      .makeSureServerIsRunning(this)
  }

  override fun onResume() {
    super.onResume()

    (application as InitiativeTrackerApplication)
      .serverLifecycleManager
      .makeSureServerIsRunning(this)
  }

  override fun onStop() {
    (application as InitiativeTrackerApplication).serverLifecycleManager.stopServerAfterDelay()

    super.onStop()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    Napier.i("Received intent with content: ${intent.data?.fragment}")

    val forwardHost = intent.getStringExtra("forward_host")
    if (forwardHost != null) {
      val joinLinks = emptyList<JoinLink>() // TODO Use correct JoinLinks
      getPlatform().shareLink(PlatformContext(this), JoinLink(forwardHost), joinLinks)
    } else if (intent.data?.fragment != null) {
      val model = ViewModelProvider.create(viewModelStore)[Model::class]

      val data = intent.data?.fragment
      Napier.i("Received intent with content: $data")

      model.addCharacters(data)

      val predefinedServerHost =
        data?.split("&")?.firstOrNull { it.startsWith("server=") }?.substring(7)
      if (predefinedServerHost != null) {
        ClientConsumer.changeHost(predefinedServerHost)

        ClientConsumer.stop()
        ClientConsumer.start(model, MainScope())
      }
    }
  }

  val permissionRequestLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) {
      (application as InitiativeTrackerApplication)
        .wifiAwareConnectionManager
        .neededMissingPermission(this)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
  App()
}
