@file:OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3AdaptiveApi::class,
)

package app.brokoli5191.picket.ui.expressive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.SceneStrategy
import app.brokoli5191.picket.ui.components.EvenFlowRow
import kotlinx.coroutines.delay

/*
 * Wrappers around experimental Material 3 Expressive and adaptive APIs. Nothing outside this
 * package opts in; when the APIs become stable only this file changes.
 */

/** Shows the expressive LoadingIndicator, but only after [delayMs]: fast loads show nothing at all. */
@Composable
fun DelayedLoadingIndicator(
    modifier: Modifier = Modifier,
    delayMs: Long = 300,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        visible = true
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (visible) LoadingIndicator(Modifier.size(48.dp))
    }
}

/** Icon-container shapes from MaterialShapes, chosen per hook kind so the watchers list is scannable. */
enum class BadgeShape { COOKIE, CLOVER, PILL, SUNNY, GEM, ARCH, SOFT_BURST, SQUARE }

@Composable
fun badgeShape(shape: BadgeShape): Shape =
    when (shape) {
        BadgeShape.COOKIE -> MaterialShapes.Cookie9Sided
        BadgeShape.CLOVER -> MaterialShapes.Clover4Leaf
        BadgeShape.PILL -> MaterialShapes.Pill
        BadgeShape.SUNNY -> MaterialShapes.Sunny
        BadgeShape.GEM -> MaterialShapes.Gem
        BadgeShape.ARCH -> MaterialShapes.Arch
        BadgeShape.SOFT_BURST -> MaterialShapes.SoftBurst
        BadgeShape.SQUARE -> MaterialShapes.Square
    }.toShape()

@Composable
fun ShapeContainer(
    shape: BadgeShape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.Surface(
        modifier = modifier.size(40.dp).clip(badgeShape(shape)),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** List-detail on wide windows (processes → app detail), single pane on phones. */
@Composable
fun rememberPicketSceneStrategy(): SceneStrategy<NavKey> = rememberListDetailSceneStrategy()

fun listPaneMetadata(): Map<String, Any> = ListDetailSceneStrategy.listPane()

fun detailPaneMetadata(): Map<String, Any> = ListDetailSceneStrategy.detailPane()

/**
 * Single choice from a few options, as Expressive toggle buttons that morph when pressed and when
 * selected. Unlike ButtonGroup, a label never gets cut off or dropped: [EvenFlowRow] keeps every
 * button at least as wide as its label and wraps to further rows when they do not fit side by side
 * (narrow screens, large fonts, long labels).
 */
@Composable
fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    EvenFlowRow(modifier = modifier.selectableGroup()) {
        options.forEach { option ->
            val checked = option == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = { if (!checked) onSelect(option) },
                modifier = Modifier.semantics { role = Role.RadioButton },
            ) {
                // Wraps only if a single label is wider than the whole row; never clipped.
                Text(label(option), textAlign = TextAlign.Center)
            }
        }
    }
}

/** A toggle with a visible label, for toolbars where an icon alone would be ambiguous. */
@Composable
fun LabelToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    ToggleButton(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier) {
        Text(label, maxLines = 1)
    }
}
