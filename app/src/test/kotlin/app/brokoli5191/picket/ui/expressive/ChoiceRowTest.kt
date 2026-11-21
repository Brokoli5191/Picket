package app.brokoli5191.picket.ui.expressive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import app.brokoli5191.picket.data.settings.AppearanceSettings
import app.brokoli5191.picket.data.settings.MotionPref
import app.brokoli5191.picket.data.settings.ThemeMode
import app.brokoli5191.picket.ui.theme.PicketTheme
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.ceil

/**
 * Choice rows at 200 % font scale, the worst case for their labels. A label counts as complete when
 * it is laid out on one line at no less than its natural width. (TextLayoutResult.hasVisualOverflow
 * cannot be used: the semantics layout result is laid out at the maximum width, so it reports an
 * overflow for every label narrower than the space it was offered.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", fontScale = 2.0f)
class ChoiceRowTest {
    @get:Rule
    val compose = createComposeRule()

    private val appearance =
        AppearanceSettings(themeMode = ThemeMode.LIGHT, dynamicColor = false, motion = MotionPref.OFF)

    @Test
    fun everyLabelIsCompleteAtEveryWidth() {
        val sort = listOf("CPU", "Memory", "Name", "Uptime")
        val style = listOf("Tonal", "Vibrant", "Expressive", "Fidelity")
        // Content widths of 312, 360 and 411 dp wide screens with 16 dp margins.
        val widths = listOf(280, 328, 379)
        compose.setContent {
            PicketTheme(appearance) {
                Surface {
                    Column {
                        widths.forEach { width ->
                            ChoiceRow(sort, sort.first(), { it }, {}, Modifier.width(width.dp))
                            ChoiceRow(style, style.first(), { it }, {}, Modifier.width(width.dp))
                        }
                    }
                }
            }
        }
        (sort + style).forEach { label ->
            val nodes = compose.onAllNodesWithText(label, useUnmergedTree = true)
            assertWithMessage(label).that(nodes.fetchSemanticsNodes()).hasSize(widths.size)
            widths.forEachIndexed { index, width ->
                val text = nodes[index].textLayout()
                val natural = ceil(text.multiParagraph.intrinsics.maxIntrinsicWidth).toInt()
                assertWithMessage("$label at $width dp").that(text.lineCount).isEqualTo(1)
                assertWithMessage("$label at $width dp").that(text.size.width).isAtLeast(natural)
            }
        }
    }

    @Test
    fun aLabelWiderThanTheRowWrapsInsteadOfBeingClipped() {
        val long = "Follow the system setting"
        compose.setContent {
            PicketTheme(appearance) {
                Surface { ChoiceRow(listOf(long, "Off"), "Off", { it }, {}, Modifier.width(160.dp)) }
            }
        }
        val text = compose.onNodeWithText(long, useUnmergedTree = true).textLayout()
        assertThat(text.lineCount).isGreaterThan(1)
        assertThat(text.size.height).isAtLeast(ceil(text.multiParagraph.height).toInt())
    }
}

private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    checkNotNull(fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action).invoke(results)
    return results.single()
}
