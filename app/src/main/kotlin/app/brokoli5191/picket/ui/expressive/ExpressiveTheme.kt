@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.brokoli5191.picket.ui.expressive

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

/*
 * Every experimental Material 3 Expressive API is used from this package only, so that once the
 * APIs stabilise the opt-ins disappear from a single place.
 */

@Composable
fun PicketExpressiveTheme(
    colorScheme: ColorScheme,
    motionScheme: MotionScheme,
    typography: Typography,
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = motionScheme,
        typography = typography,
        content = content,
    )
}

fun expressiveMotion(): MotionScheme = MotionScheme.expressive()

fun standardMotion(): MotionScheme = MotionScheme.standard()
