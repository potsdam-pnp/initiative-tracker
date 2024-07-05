package io.github.potsdam_pnp.initiative_tracker

import App
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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