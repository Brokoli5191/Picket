package app.brokoli5191.picket.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** How much motion the UI may use; screens check this before hero transitions and digit rolls. */
enum class MotionLevel { FULL, REDUCED, OFF }

val LocalMotionLevel = staticCompositionLocalOf { MotionLevel.FULL }
val LocalHapticsEnabled = staticCompositionLocalOf { true }
val LocalDarkTheme = staticCompositionLocalOf { false }

/** Motion "Off": every Material component snaps to its end state. */
object NoMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}

/** Semantic haptics that respect the in-app switch. Never used for scrolling. */
class Haptics(
    private val feedback: HapticFeedback,
    private val enabled: Boolean,
) {
    fun confirm() = perform(HapticFeedbackType.Confirm)

    fun reject() = perform(HapticFeedbackType.Reject)

    fun toggle(on: Boolean) = perform(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    fun tick() = perform(HapticFeedbackType.SegmentTick)

    private fun perform(type: HapticFeedbackType) {
        if (enabled) feedback.performHapticFeedback(type)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(feedback, enabled) { Haptics(feedback, enabled) }
}
