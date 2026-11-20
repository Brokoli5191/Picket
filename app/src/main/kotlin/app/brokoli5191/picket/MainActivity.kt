package app.brokoli5191.picket

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.net.toUri
import app.brokoli5191.picket.privileged.client.PrivilegedClient
import app.brokoli5191.picket.ui.LocalAppContainer
import app.brokoli5191.picket.ui.PicketApp

class MainActivity : ComponentActivity() {
    private val container get() = (application as PicketApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                PicketApp(onOpenShizuku = ::openShizuku)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Shizuku may have been started or granted while Picket was in the background.
        container.privilegedClient.evaluate()
    }

    private fun openShizuku() {
        val launch = packageManager.getLaunchIntentForPackage(PrivilegedClient.SHIZUKU_PACKAGE)
        val intent = launch ?: Intent(Intent.ACTION_VIEW, SHIZUKU_HOME.toUri())
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Nothing can open the link; the connection screen already explains what to do.
        }
    }

    private companion object {
        const val SHIZUKU_HOME = "https://shizuku.rikka.app/"
    }
}
