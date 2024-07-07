import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window

fun appData(): String? {
    val hash = window.location.hash
    if (hash == "")
        return null
    val afterHash = hash.substring(1)
    return afterHash // TODO call something like decodeUriCompoenent
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        App(appData())
    }
}