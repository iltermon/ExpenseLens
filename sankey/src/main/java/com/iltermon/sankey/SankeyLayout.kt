package com.iltermon.sankey

/**
 * Pure geometry for the Sankey diagram — no Compose types, so it is exercised by plain JUnit. The
 * composable measures text, hands in a plot rect via [LayoutSpec], and draws the shapes this
 * produces. The math never touches text or [androidx.compose.ui.graphics.Color].
 *
 * Sources are expected already balanced against targets (both sums equal, synthetic remainder/
 * deficit already injected by the caller) and pre-sorted; nodes are stacked in the given order.
 */

/** A single node's aggregated total, in caller order. */
data class SankeyNodeInput(val id: String, val value: Double)

/** Result of [balanceSides]: the two columns padded so their sums are equal. */
data class BalancedSides(val sources: List<SankeyNodeInput>, val targets: List<SankeyNodeInput>)

/**
 * Pad the smaller column with a synthetic balancing node so both sums equal `max(sumS, sumT)`,
 * which the allocation math relies on. Appends a [DEFICIT_ID] node to the sources when outputs
 * exceed income, or a [REMAINDER_ID] node to the targets when income exceeds outputs. When the
 * sides already balance (within [epsilon]) both lists are returned unchanged.
 */
fun balanceSides(
    sources: List<SankeyNodeInput>,
    targets: List<SankeyNodeInput>,
    epsilon: Double = 1e-6,
): BalancedSides {
    val sumS = sources.sumOf { it.value }
    val sumT = targets.sumOf { it.value }
    val total = maxOf(sumS, sumT)
    val paddedSources =
        if (total - sumS > epsilon) sources + SankeyNodeInput(DEFICIT_ID, total - sumS) else sources
    val paddedTargets =
        if (total - sumT > epsilon) targets + SankeyNodeInput(REMAINDER_ID, total - sumT) else targets
    return BalancedSides(paddedSources, paddedTargets)
}

/** The inner plot rectangle (node/ribbon area, label margins excluded) plus node metrics, in px. */
data class LayoutSpec(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val nodeWidth: Float,
    val nodeGap: Float,
    val minNodeHeight: Float,
) {
    val height: Float get() = bottom - top
}

/** An axis-aligned rectangle in px. */
data class SankeyRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** A placed node. [index] is its position within its own side. */
data class NodeGeometry(
    val id: String,
    val side: SankeySide,
    val index: Int,
    val value: Double,
    val rect: SankeyRect,
) {
    val centerY: Float get() = (rect.top + rect.bottom) / 2f
}

/**
 * A ribbon between one source and one target. The anchor intervals are the vertical slices the
 * ribbon occupies on each node's inner edge (source right edge, target left edge).
 */
data class LinkGeometry(
    val sourceIndex: Int,
    val targetIndex: Int,
    val sourceId: String,
    val targetId: String,
    val value: Double,
    val sourceX: Float,
    val targetX: Float,
    val sourceTop: Float,
    val sourceBottom: Float,
    val targetTop: Float,
    val targetBottom: Float,
)

/** The full computed geometry, shared by the draw phase and pointer hit-testing. */
data class SankeyLayout(
    val sources: List<NodeGeometry>,
    val targets: List<NodeGeometry>,
    val links: List<LinkGeometry>,
)

/**
 * Distribute [usable] px across [values] proportionally, clamping any node below [minHeight] up to
 * [minHeight] and rescaling the remainder. Iterates until the pinned set is stable.
 */
internal fun computeNodeHeights(values: List<Double>, usable: Float, minHeight: Float): FloatArray {
    val n = values.size
    val heights = FloatArray(n)
    if (n == 0) return heights
    val pinned = BooleanArray(n)
    var remaining = usable

    while (true) {
        var activeSum = 0.0
        var activeCount = 0
        for (i in 0 until n) if (!pinned[i]) {
            activeSum += values[i]
            activeCount++
        }
        if (activeCount == 0) break
        if (activeSum <= 0.0) {
            // Degenerate: no positive value left to weight by — split the remainder evenly.
            val even = remaining / activeCount
            for (i in 0 until n) if (!pinned[i]) heights[i] = even
            break
        }
        var changed = false
        for (i in 0 until n) {
            if (pinned[i]) continue
            val h = (remaining * (values[i] / activeSum)).toFloat()
            if (h < minHeight) {
                heights[i] = minHeight
                pinned[i] = true
                remaining -= minHeight
                changed = true
            }
        }
        if (!changed) {
            for (i in 0 until n) if (!pinned[i]) {
                heights[i] = (remaining * (values[i] / activeSum)).toFloat()
            }
            break
        }
    }
    return heights
}

/** Stack a side's nodes top-to-bottom in order, separated by [LayoutSpec.nodeGap]. */
private fun placeColumn(
    inputs: List<SankeyNodeInput>,
    heights: FloatArray,
    side: SankeySide,
    left: Float,
    right: Float,
    spec: LayoutSpec,
): List<NodeGeometry> {
    val nodes = ArrayList<NodeGeometry>(inputs.size)
    var y = spec.top
    for (i in inputs.indices) {
        val top = y
        val bottom = y + heights[i]
        nodes += NodeGeometry(
            id = inputs[i].id,
            side = side,
            index = i,
            value = inputs[i].value,
            rect = SankeyRect(left, top, right, bottom),
        )
        y = bottom + spec.nodeGap
    }
    return nodes
}

/**
 * Compute the full layout. [sources] and [targets] must already be balanced (equal sums) and
 * ordered. Returns empty geometry when either side is empty or the total flow is non-positive.
 */
fun computeSankeyLayout(
    spec: LayoutSpec,
    sources: List<SankeyNodeInput>,
    targets: List<SankeyNodeInput>,
): SankeyLayout {
    if (sources.isEmpty() || targets.isEmpty()) return SankeyLayout(emptyList(), emptyList(), emptyList())
    val totalFlow = sources.sumOf { it.value }
    if (totalFlow <= 0.0) return SankeyLayout(emptyList(), emptyList(), emptyList())

    fun usable(count: Int): Float = spec.height - spec.nodeGap * (count - 1)
    val srcHeights = computeNodeHeights(sources.map { it.value }, usable(sources.size), spec.minNodeHeight)
    val tgtHeights = computeNodeHeights(targets.map { it.value }, usable(targets.size), spec.minNodeHeight)

    val srcLeft = spec.left
    val srcRight = spec.left + spec.nodeWidth
    val tgtLeft = spec.right - spec.nodeWidth
    val tgtRight = spec.right

    val srcNodes = placeColumn(sources, srcHeights, SankeySide.SOURCE, srcLeft, srcRight, spec)
    val tgtNodes = placeColumn(targets, tgtHeights, SankeySide.TARGET, tgtLeft, tgtRight, spec)

    // Anchor cursors: each node's inner edge is tiled by fractional intervals ordered by the
    // opposite index, so slices tile the (possibly clamped) node height exactly. The fraction of
    // source i used by link (i,j) is v_j/totalFlow; of target j by link (i,j) is v_i/totalFlow.
    val srcAccum = FloatArray(sources.size)   // fraction consumed so far on each source edge
    val tgtAccum = FloatArray(targets.size)   // fraction consumed so far on each target edge

    val links = ArrayList<LinkGeometry>(sources.size * targets.size)
    for (i in sources.indices) {
        val s = srcNodes[i]
        for (j in targets.indices) {
            val value = sources[i].value * targets[j].value / totalFlow
            if (value <= 0.0) continue

            val srcFrac = (targets[j].value / totalFlow).toFloat()
            val sTop = s.rect.top + s.rect.height * srcAccum[i]
            srcAccum[i] += srcFrac
            val sBottom = s.rect.top + s.rect.height * srcAccum[i]

            val t = tgtNodes[j]
            val tgtFrac = (sources[i].value / totalFlow).toFloat()
            val tTop = t.rect.top + t.rect.height * tgtAccum[j]
            tgtAccum[j] += tgtFrac
            val tBottom = t.rect.top + t.rect.height * tgtAccum[j]

            links += LinkGeometry(
                sourceIndex = i,
                targetIndex = j,
                sourceId = sources[i].id,
                targetId = targets[j].id,
                value = value,
                sourceX = s.rect.right,
                targetX = t.rect.left,
                sourceTop = sTop,
                sourceBottom = sBottom,
                targetTop = tTop,
                targetBottom = tBottom,
            )
        }
    }
    return SankeyLayout(srcNodes, tgtNodes, links)
}

// --- Hub layout ------------------------------------------------------------------------------

/**
 * A three-column hub layout: every source flows into one central [hub] node, which then splits out
 * to every target. [leftLinks] are `source → hub`, [rightLinks] are `hub → target`.
 */
data class HubLayout(
    val sources: List<NodeGeometry>,
    val hub: NodeGeometry,
    val targets: List<NodeGeometry>,
    val leftLinks: List<LinkGeometry>,
    val rightLinks: List<LinkGeometry>,
)

/**
 * Build the hub layout by reusing [computeSankeyLayout] twice against a synthetic hub node whose
 * value equals the total flow: `sources → [hub]` on the left half, `[hub] → targets` on the right.
 * Because the hub is the only node on its side it spans the full column height, so its edges tile
 * into constant-width ribbons that match each source/target node's own height.
 *
 * [sources] and [targets] must already be balanced (equal sums). Returns null when either side is
 * empty or the total flow is non-positive.
 */
fun computeHubLayout(
    spec: LayoutSpec,
    sources: List<SankeyNodeInput>,
    targets: List<SankeyNodeInput>,
    hubId: String,
): HubLayout? {
    if (sources.isEmpty() || targets.isEmpty()) return null
    val total = sources.sumOf { it.value }
    if (total <= 0.0) return null

    val centerX = (spec.left + spec.right) / 2f
    val hubLeft = centerX - spec.nodeWidth / 2f
    val hubRight = centerX + spec.nodeWidth / 2f
    val hub = SankeyNodeInput(hubId, total)

    val left = computeSankeyLayout(spec.copy(right = hubRight), sources, listOf(hub))
    val right = computeSankeyLayout(spec.copy(left = hubLeft), listOf(hub), targets)
    val hubNode = left.targets.firstOrNull() ?: return null

    return HubLayout(
        sources = left.sources,
        hub = hubNode,
        targets = right.targets,
        leftLinks = left.links,
        rightLinks = right.links,
    )
}

// --- Hit-testing -----------------------------------------------------------------------------

/** A tappable region: the union of a node rect and its label rect, tagged with the node identity. */
data class HitTarget(val side: SankeySide, val id: String, val rect: SankeyRect)

/** Grow [rect] symmetrically so each dimension is at least [min], for a comfortable touch target. */
internal fun inflateToMinTouch(rect: SankeyRect, min: Float): SankeyRect {
    val dx = if (rect.width < min) (min - rect.width) / 2f else 0f
    val dy = if (rect.height < min) (min - rect.height) / 2f else 0f
    return SankeyRect(rect.left - dx, rect.top - dy, rect.right + dx, rect.bottom + dy)
}

/**
 * Return the node whose (touch-inflated) region contains the point, or null. Targets are tested in
 * order; callers pass node∪label rects. [minTouch] defaults to a 48px comfortable target.
 */
fun hitTestNode(
    targets: List<HitTarget>,
    x: Float,
    y: Float,
    minTouch: Float = 48f,
): SankeySelection? {
    for (t in targets) {
        val r = inflateToMinTouch(t.rect, minTouch)
        if (x >= r.left && x <= r.right && y >= r.top && y <= r.bottom) {
            return SankeySelection(t.side, t.id)
        }
    }
    return null
}
