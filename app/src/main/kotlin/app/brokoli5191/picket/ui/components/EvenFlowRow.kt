package app.brokoli5191.picket.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp

/**
 * Rows for a handful of equal-ranking buttons, such as a single choice. A child is never narrower
 * than its one-line width, so a label is never clipped; within that limit the children of a row are
 * equally wide and the row ends flush. Children that do not fit side by side (narrow screens, large
 * fonts, long labels) wrap onto as few rows as possible, spread evenly: two rows of two rather than
 * three and one.
 *
 * FlowRow with `Modifier.weight` cannot do this: it decides the line breaks from the children's
 * widths but then gives each weighted child an equal share, which can be less than its label needs.
 */
@Composable
fun EvenFlowRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val wanted = IntArray(measurables.size) { measurables[it].maxIntrinsicWidth(Constraints.Infinity) }
        val width =
            if (constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                wanted.sum() + gap * (wanted.size - 1).coerceAtLeast(0)
            }
        val natural = IntArray(wanted.size) { wanted[it].coerceAtMost(width) }
        val rows = evenRows(natural, width, gap)
        val widths = rowWidths(rows, natural, width, gap)
        val placeables = measurables.mapIndexed { i, child -> child.measure(Constraints.fixedWidth(widths[i])) }
        val heights = rows.map { row -> row.maxOf { placeables[it].height } }
        val height = heights.sum() + gap * (rows.size - 1).coerceAtLeast(0)
        layout(constraints.constrainWidth(width), constraints.constrainHeight(height)) {
            var y = 0
            rows.forEachIndexed { r, row ->
                var x = 0
                for (i in row) {
                    placeables[i].placeRelative(x, y + (heights[r] - placeables[i].height) / 2)
                    x += placeables[i].width + gap
                }
                y += heights[r] + gap
            }
        }
    }
}

/**
 * Splits children of the given [natural] widths (each at most [width]) into the fewest rows that
 * fit, then spreads them evenly over that many rows if every row still fits; otherwise keeps the
 * greedy split.
 */
internal fun evenRows(
    natural: IntArray,
    width: Int,
    gap: Int,
): List<IntRange> {
    val count = natural.size
    if (count == 0) return emptyList()

    fun fits(row: IntRange) = row.sumOf { natural[it] } + gap * (row.count() - 1) <= width

    val greedy = mutableListOf<IntRange>()
    var start = 0
    for (i in 1 until count) {
        if (!fits(start..i)) {
            greedy += start until i
            start = i
        }
    }
    greedy += start until count
    if (greedy.size == 1) return greedy

    val base = count / greedy.size
    val longer = count % greedy.size
    val even = mutableListOf<IntRange>()
    start = 0
    repeat(greedy.size) { r ->
        val size = base + if (r < longer) 1 else 0
        even += start until start + size
        start += size
    }
    return if (even.all(::fits)) even else greedy
}

/**
 * Width of every child. Each row's space is shared equally, except that a child whose natural
 * width is above the equal share keeps it and the others split what is left; leftover pixels go to
 * the first children so the row ends flush.
 */
internal fun rowWidths(
    rows: List<IntRange>,
    natural: IntArray,
    width: Int,
    gap: Int,
): IntArray {
    val widths = IntArray(natural.size)
    for (row in rows) {
        var free = width - gap * (row.count() - 1)
        var left = row.count()
        val kept = mutableSetOf<Int>()
        for (i in row.sortedByDescending { natural[it] }) {
            if (natural[i].toLong() * left <= free) break
            widths[i] = natural[i]
            kept += i
            free -= natural[i]
            left--
        }
        if (left == 0) continue
        val share = free / left
        var leftover = free - share * left
        for (i in row) {
            if (i in kept) continue
            val extra = if (leftover > 0) 1 else 0
            leftover -= extra
            widths[i] = (share + extra).coerceAtLeast(0)
        }
    }
    return widths
}
