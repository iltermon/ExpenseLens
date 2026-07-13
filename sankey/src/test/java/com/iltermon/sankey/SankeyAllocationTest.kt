package com.iltermon.sankey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Invariants of the balancing + proportional link allocation. */
class SankeyAllocationTest {

    private val spec = LayoutSpec(
        left = 0f, top = 0f, right = 200f, bottom = 100f,
        nodeWidth = 12f, nodeGap = 10f, minNodeHeight = 0f,
    )

    @Test fun `link row sums equal source values`() {
        val sources = listOf(SankeyNodeInput("a", 3.0), SankeyNodeInput("b", 1.0))
        val targets = listOf(SankeyNodeInput("x", 2.0), SankeyNodeInput("y", 2.0))
        val layout = computeSankeyLayout(spec, sources, targets)

        for (node in layout.sources) {
            val rowSum = layout.links.filter { it.sourceIndex == node.index }.sumOf { it.value }
            assertEquals("row sum for ${node.id}", node.value, rowSum, 1e-9)
        }
    }

    @Test fun `link column sums equal target values`() {
        val sources = listOf(SankeyNodeInput("a", 3.0), SankeyNodeInput("b", 1.0))
        val targets = listOf(SankeyNodeInput("x", 2.0), SankeyNodeInput("y", 2.0))
        val layout = computeSankeyLayout(spec, sources, targets)

        for (node in layout.targets) {
            val colSum = layout.links.filter { it.targetIndex == node.index }.sumOf { it.value }
            assertEquals("column sum for ${node.id}", node.value, colSum, 1e-9)
        }
    }

    @Test fun `balanceSides pads targets with remainder when income exceeds outputs`() {
        val sources = listOf(SankeyNodeInput("salary", 100.0))
        val targets = listOf(SankeyNodeInput("rent", 60.0))
        val balanced = balanceSides(sources, targets)

        assertEquals(sources, balanced.sources)
        assertEquals(2, balanced.targets.size)
        val synthetic = balanced.targets.last()
        assertEquals(REMAINDER_ID, synthetic.id)
        assertEquals(40.0, synthetic.value, 1e-9)
        assertEquals(balanced.sources.sumOf { it.value }, balanced.targets.sumOf { it.value }, 1e-9)
    }

    @Test fun `balanceSides pads sources with deficit when outputs exceed income`() {
        val sources = listOf(SankeyNodeInput("salary", 40.0))
        val targets = listOf(SankeyNodeInput("rent", 60.0))
        val balanced = balanceSides(sources, targets)

        assertEquals(2, balanced.sources.size)
        val synthetic = balanced.sources.last()
        assertEquals(DEFICIT_ID, synthetic.id)
        assertEquals(20.0, synthetic.value, 1e-9)
        assertEquals(targets, balanced.targets)
        assertEquals(balanced.sources.sumOf { it.value }, balanced.targets.sumOf { it.value }, 1e-9)
    }

    @Test fun `balanceSides injects the balancing node even alongside an Other aggregate node`() {
        // A folded "Other" bucket is just another node to balanceSides; its presence must not stop the
        // deficit/remainder node from being appended, nor get merged into it. Guards that "Not
        // covered"/"Unspent" are always shown when they exist, even when real categories overflowed.
        val sources = listOf(
            SankeyNodeInput("salary", 40.0),
            SankeyNodeInput("__other__", 10.0),
        )
        val targets = listOf(SankeyNodeInput("rent", 80.0))
        val balanced = balanceSides(sources, targets)

        assertEquals(3, balanced.sources.size)
        assertEquals(DEFICIT_ID, balanced.sources.last().id)
        assertEquals(30.0, balanced.sources.last().value, 1e-9) // 80 - (40 + 10)
        assertTrue(balanced.sources.any { it.id == "__other__" }) // Other survives untouched
        assertEquals(balanced.sources.sumOf { it.value }, balanced.targets.sumOf { it.value }, 1e-9)
    }

    @Test fun `balanceSides leaves already balanced sides untouched`() {
        val sources = listOf(SankeyNodeInput("a", 50.0))
        val targets = listOf(SankeyNodeInput("x", 50.0))
        val balanced = balanceSides(sources, targets)
        assertEquals(sources, balanced.sources)
        assertEquals(targets, balanced.targets)
    }

    @Test fun `empty side yields empty layout`() {
        val layout = computeSankeyLayout(spec, emptyList(), listOf(SankeyNodeInput("x", 1.0)))
        assertTrue(layout.sources.isEmpty())
        assertTrue(layout.targets.isEmpty())
        assertTrue(layout.links.isEmpty())
    }

    @Test fun `zero total flow yields empty layout`() {
        val layout = computeSankeyLayout(
            spec,
            listOf(SankeyNodeInput("a", 0.0)),
            listOf(SankeyNodeInput("x", 0.0)),
        )
        assertTrue(layout.links.isEmpty())
    }
}
