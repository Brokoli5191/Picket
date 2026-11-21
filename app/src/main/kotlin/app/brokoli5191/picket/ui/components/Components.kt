package app.brokoli5191.picket.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.brokoli5191.picket.R
import app.brokoli5191.picket.ui.theme.LocalMotionLevel
import app.brokoli5191.picket.ui.theme.MotionLevel

/**
 * A live number with tabular figures. With full motion, changed values roll vertically
 * (up when growing); reduced or no motion swaps instantly.
 */
@Composable
fun RollingNumber(
    text: String,
    sortKey: Double,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val tabular = style.copy(fontFeatureSettings = "tnum")
    if (LocalMotionLevel.current != MotionLevel.FULL) {
        Text(text, modifier = modifier, style = tabular, color = color)
        return
    }
    // Spatial movement on the scheme's spatial springs, alpha on its effects springs.
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedContent(
        targetState = text to sortKey,
        modifier = modifier,
        transitionSpec = {
            val up = targetState.second >= initialState.second
            (slideInVertically(spatial) { h -> if (up) h else -h } + fadeIn(effects)) togetherWith
                (slideOutVertically(spatial) { h -> if (up) -h else h } + fadeOut(effects))
        },
        label = "number",
    ) { (value, _) -> Text(value, style = tabular, color = color) }
}

@Composable
private fun noticeColor(error: Boolean) =
    if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Explains why something is missing (a failing source, no data yet) without hiding the rest. */
@Composable
fun NoticeCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = noticeColor(error)),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                painterResource(if (error) R.drawable.ic_warning else R.drawable.ic_info),
                contentDescription = null,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Container colours for grouped (segmented) lists, so a group reads as one block on any surface. */
@Composable
fun segmentedColors() = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
