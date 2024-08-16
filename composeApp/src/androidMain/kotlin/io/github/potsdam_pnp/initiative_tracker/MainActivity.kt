package io.github.potsdam_pnp.initiative_tracker

import App
import JoinLink
import Model
import PlatformContext
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import getPlatform
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.MainScope

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Napier.base(DebugAntilog())
        Napier.w("It works Napier")

        super.onCreate(savedInstanceState)

        val data = intent.data?.fragment
        val factory = viewModelFactory {
            initializer {
                Model((application as InitiativeTrackerApplication).repository, null)
            }
        }
        ViewModelProvider.create(viewModelStore, factory)[Model::class]

        setContent {
            App(data)
        }
    }

    override fun onStart() {
        super.onStart()

        startService(Intent(this, ConnectionService::class.java))
    }

    override fun onResume() {
        super.onResume()

        startService(Intent(this, ConnectionService::class.java))
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

            val predefinedServerHost = data?.split("&")?.firstOrNull { it.startsWith("server=") }
                ?.substring(7)
            if (predefinedServerHost != null) {
                ClientConsumer.changeHost(predefinedServerHost)

                ClientConsumer.stop()
                ClientConsumer.start(model, MainScope())
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}