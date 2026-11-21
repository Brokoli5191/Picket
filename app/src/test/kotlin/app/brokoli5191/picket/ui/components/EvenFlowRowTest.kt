package app.brokoli5191.picket.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class EvenFlowRowTest {
    @Test
    fun aChildWiderThanTheShareKeepsItsWidthAndTheOthersSplitTheRest() {
        val natural = intArrayOf(100, 150, 80)
        val rows = evenRows(natural, width = 400, gap = 8)
        assertThat(rows).containsExactly(0..2)
        assertThat(rowWidths(rows, natural, width = 400, gap = 8).toList()).containsExactly(117, 150, 117).inOrder()
    }

    @Test
    fun childrenThatFitTheShareAreEquallyWide() {
        val natural = intArrayOf(60, 70, 50, 65)
        val rows = evenRows(natural, width = 400, gap = 8)
        assertThat(rowWidths(rows, natural, width = 400, gap = 8).toList()).containsExactly(94, 94, 94, 94)
    }

    @Test
    fun wrapsIntoEvenRowsRatherThanThreeAndOne() {
        val natural = intArrayOf(100, 150, 100, 160)
        val rows = evenRows(natural, width = 400, gap = 8)
        assertThat(rows).containsExactly(0..1, 2..3).inOrder()
        assertThat(rowWidths(rows, natural, width = 400, gap = 8).toList()).containsExactly(196, 196, 196, 196)
    }

    @Test
    fun keepsTheGreedySplitWhenAnEvenOneDoesNotFit() {
        assertThat(evenRows(intArrayOf(390, 10, 10, 10), width = 400, gap = 8)).containsExactly(0..0, 1..3).inOrder()
    }

    @Test
    fun leftoverPixelsGoToTheFirstChildren() {
        assertThat(rowWidths(listOf(0..2), intArrayOf(10, 10, 10), width = 101, gap = 0).toList())
            .containsExactly(34, 34, 33)
            .inOrder()
    }

    @Test
    fun noChildIsNarrowerThanItsLabelAndEveryRowEndsFlush() {
        val random = Random(42)
        repeat(10_000) {
            val width = random.nextInt(100, 1500)
            val gap = random.nextInt(0, 30)
            val natural = IntArray(random.nextInt(1, 7)) { random.nextInt(1, width + 1) }
            val rows = evenRows(natural, width, gap)
            assertThat(rows.flatMap { it.toList() }).isEqualTo(natural.indices.toList())
            val widths = rowWidths(rows, natural, width, gap)
            natural.indices.forEach { assertThat(widths[it]).isAtLeast(natural[it]) }
            rows.forEach { row -> assertThat(row.sumOf { widths[it] } + gap * (row.count() - 1)).isEqualTo(width) }
        }
    }
}
