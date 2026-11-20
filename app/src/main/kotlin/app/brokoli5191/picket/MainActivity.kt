package app.brokoli5191.picket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.brokoli5191.picket.ui.PlaceholderScreen
import app.brokoli5191.picket.ui.theme.PicketTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PicketTheme {
                PlaceholderScreen()
            }
        }
    }
}
