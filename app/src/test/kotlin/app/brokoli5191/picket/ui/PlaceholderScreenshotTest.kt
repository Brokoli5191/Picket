package app.brokoli5191.picket.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.brokoli5191.picket.ui.theme.PicketTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PlaceholderScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun placeholderLight() {
        compose.setContent { PicketTheme(darkTheme = false, dynamicColor = false) { PlaceholderScreen() } }
        compose.onRoot().captureRoboImage("placeholder-light.png")
    }

    @Test
    fun placeholderDark() {
        compose.setContent { PicketTheme(darkTheme = true, dynamicColor = false) { PlaceholderScreen() } }
        compose.onRoot().captureRoboImage("placeholder-dark.png")
    }
}
