package com.iltermon.sankey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Node placement, min-height clamping, exact slice tiling, and hit-testing. */
class SankeyLayoutTest {

    private fun spec(minHeight: Float = 0f) = LayoutSpec(
        left = 0f, top = 0f, right = 200f, bottom = 100f,
        nodeWidth = 12f, nodeGap = 10f, minNodeHeight = minHeight,
    )

    @Test fun `heights are proportional to values`() {
        val heights = computeNodeHeights(listOf(2.0, 1.0), usable = 90f, minHeight = 0f)
        assertEquals(60f, heights[0], 1e-3f)
        assertEquals(30f, heights[1], 1e-3f)
    }

    @Test fun `all heights sum to usable space`() {
        val heights = computeNodeHeights(listOf(5.0, 3.0, 2.0), usable = 90f, minHeight = 0f)
        assertEquals(90f, heights.sum(), 1e-3f)
    }

    @Test fun `tiny node is clamped to min height and remainder rescales`() {
        // One dominant node and a near-zero node; the small one pins to the floor.
        val heights = computeNodeHeights(listOf(1000.0, 0.001), usable = 100f, minHeight = 8f)
        assertEquals(8f, heights[1], 1e-3f)
        assertEquals(92f, heights[0], 1e-3f)
        assertEquals(100f, heights.sum(), 1e-3f)
    }

    @Test fun `nodes are stacked in input order with gaps`() {
        val sources = listOf(
            SankeyNodeInput("a", 1.0), SankeyNodeInput("b", 1.0), SankeyNodeInput("c", 1.0),
        )
        val targets = listOf(SankeyNodeInput("x", 3.0))
        val layout = computeSankeyLayout(spec(), sources, targets)

        assertEquals(listOf("a", "b", "c"), layout.sources.map { it.id })
        // Tops strictly increase; each successive node starts a gap below the previous bottom.
        assertTrue(layout.sources[0].rect.top < layout.sources[1].rect.top)
        assertEquals(
            layout.sources[0].rect.bottom + spec().nodeGap,
            layout.sources[1].rect.top,
            1e-3f,
        )
    }

    @Test fun `source link slices tile the node height exactly`() {
        val sources = listOf(SankeyNodeInput("a", 4.0))
        val targets = listOf(SankeyNodeInput("x", 1.0), SankeyNodeInput("y", 3.0))
        val layout = computeSankeyLayout(spec(), sources, targets)

        val node = layout.sources.single()
        val slices = layout.links.filter { it.sourceIndex == 0 }.sortedBy { it.targetIndex }
        // First slice starts at node top, last ends at node bottom, contiguous in between.
        assertEquals(node.rect.top, slices.first().sourceTop, 1e-3f)
        assertEquals(node.rect.bottom, slices.last().sourceBottom, 1e-3f)
        for (k in 1 until slices.size) {
            assertEquals(slices[k - 1].sourceBottom, slices[k].sourceTop, 1e-3f)
        }
    }

    @Test fun `target link slices tile the node height exactly`() {
        val sources = listOf(SankeyNodeInput("a", 1.0), SankeyNodeInput("b", 3.0))
        val targets = listOf(SankeyNodeInput("x", 4.0))
        val layout = computeSankeyLayout(spec(), sources, targets)

        val node = layout.targets.single()
        val slices = layout.links.filter { it.targetIndex == 0 }.sortedBy { it.sourceIndex }
        assertEquals(node.rect.top, slices.first().targetTop, 1e-3f)
        assertEquals(node.rect.bottom, slices.last().targetBottom, 1e-3f)
    }

    @Test fun `hitTest returns the node containing the point`() {
        val targets = listOf(
            HitTarget(SankeySide.SOURCE, "a", SankeyRect(0f, 0f, 60f, 60f)),
        )
        val hit = hitTestNode(targets, x = 30f, y = 30f)
        assertEquals(SankeySelection(SankeySide.SOURCE, "a"), hit)
    }

    @Test fun `hitTest inflates small rects to a minimum touch target`() {
        // A thin 12x4 node: a tap 20px away vertically still lands via the 48px inflation.
        val targets = listOf(
            HitTarget(SankeySide.TARGET, "x", SankeyRect(100f, 50f, 112f, 54f)),
        )
        val hit = hitTestNode(targets, x = 106f, y = 30f, minTouch = 48f)
        assertEquals(SankeySelection(SankeySide.TARGET, "x"), hit)
    }

    @Test fun `hub layout has one hub, N source links and M target links`() {
        val sources = listOf(
            SankeyNodeInput("a", 2.0), SankeyNodeInput("b", 1.0), SankeyNodeInput("c", 1.0),
        )
        val targets = listOf(SankeyNodeInput("x", 3.0), SankeyNodeInput("y", 1.0))
        val hub = computeHubLayout(spec(), sources, targets, "__hub__")!!

        assertEquals(3, hub.sources.size)
        assertEquals(2, hub.targets.size)
        assertEquals(3, hub.leftLinks.size)  // each source -> hub
        assertEquals(2, hub.rightLinks.size) // hub -> each target
        assertEquals("__hub__", hub.hub.id)
    }

    @Test fun `hub node spans full height and is horizontally centered`() {
        val s = spec()
        val hub = computeHubLayout(
            s,
            listOf(SankeyNodeInput("a", 1.0), SankeyNodeInput("b", 1.0)),
            listOf(SankeyNodeInput("x", 2.0)),
            "__hub__",
        )!!
        // Single node on the hub's side -> no gaps -> spans the whole plot height.
        assertEquals(s.top, hub.hub.rect.top, 1e-3f)
        assertEquals(s.bottom, hub.hub.rect.bottom, 1e-3f)
        val hubCenterX = (hub.hub.rect.left + hub.hub.rect.right) / 2f
        assertEquals((s.left + s.right) / 2f, hubCenterX, 1e-3f)
    }

    @Test fun `hub layout is null when a side is empty`() {
        assertNull(computeHubLayout(spec(), emptyList(), listOf(SankeyNodeInput("x", 1.0)), "__hub__"))
    }

    @Test fun `hitTest returns null outside every region`() {
        val targets = listOf(
            HitTarget(SankeySide.SOURCE, "a", SankeyRect(0f, 0f, 12f, 20f)),
        )
        assertNull(hitTestNode(targets, x = 190f, y = 190f, minTouch = 48f))
    }
}
