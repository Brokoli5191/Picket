package app.brokoli5191.picket.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.brokoli5191.picket.ui.expressive.PicketExpressiveTheme
import app.brokoli5191.picket.ui.expressive.expressiveMotion

@Composable
fun PicketTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> {
                darkColorScheme()
            }

            else -> {
                lightColorScheme()
            }
        }
    PicketExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = expressiveMotion(),
        typography = Typography(),
        content = content,
    )
}
