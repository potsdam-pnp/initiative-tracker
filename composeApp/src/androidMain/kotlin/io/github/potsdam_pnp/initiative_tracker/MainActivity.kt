package io.github.potsdam_pnp.initiative_tracker

import App
import JoinLink
import PlatformContext
import android.content.BroadcastReceiver
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import getPlatform
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Napier.base(DebugAntilog())
        Napier.w("It works Napier")

        super.onCreate(savedInstanceState)

        setContent {
            App(intent.data?.fragment)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}

class ShareLinkReceiver: BroadcastReceiver() {
    override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
        val joinLink = JoinLink(intent!!.getStringExtra("host")!!)
        val joinLinks = getPlatform().serverStatus.value.joinLinks
        getPlatform().shareLink(PlatformContext(context!!), joinLink, joinLinks)
    }
}