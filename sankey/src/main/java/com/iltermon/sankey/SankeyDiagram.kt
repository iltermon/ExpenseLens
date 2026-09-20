package com.iltermon.sankey

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * A hand-rolled Sankey diagram. Pass [hubLabel] for the budget shape — every [sources] node
 * converges into one central hub, which fans out to every [targets] node (income → total →
 * expenses). Leave [hubLabel] null for a plain two-column diagram.
 *
 * When the two sides don't sum equal the library injects one balancing node so the shorter column
 * fills the height: a [remainderLabel] "unspent" node on the target side when income exceeds outflow,
 * or a [deficitLabel] "not covered" node on the source side when outflow exceeds income (it flows
 * into the hub, which then reads as total *funds*, not just income). Highlights connected ribbons on
 * tap. Fully parameterized via [style]; no strings, colors, or currency are hardcoded. Draws nothing
 * when both sides are empty — the host renders its own empty state.
 */
@Composable
fun SankeyDiagram(
    sources: List<SankeyEntry>,
    targets: List<SankeyEntry>,
    modifier: Modifier = Modifier,
    style: SankeyStyle = SankeyDefaults.style(),
    remainderLabel: String? = null,
    deficitLabel: String? = null,
    hubLabel: String? = null,
    onSelectionChange: ((SankeySelection?) -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Selection survives recomposition/rotation but resets whenever the entry set changes.
    val idsKey = remember(sources, targets) {
        (sources.map { it.id } + " " + targets.map { it.id }).toString()
    }
    var selectedKey by rememberSaveable(idsKey) { mutableStateOf<String?>(null) }
    val selection = decodeSelection(selectedKey)

    val model = remember(sources, targets, canvasSize, style, density, remainderLabel, deficitLabel, hubLabel) {
        if (canvasSize.width == 0 || canvasSize.height == 0) return@remember null
        buildRenderModel(
            sources, targets, canvasSize, style, remainderLabel, deficitLabel, hubLabel,
            measurer, density,
        )
    }

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(model) {
                    detectTapGestures { pos ->
                        val hit = model?.let { hitTestNode(it.hitTargets, pos.x, pos.y) }
                        val next = when {
                            hit == null -> null
                            hit == selection -> null // re-tap clears
                            else -> hit
                        }
                        selectedKey = next?.let(::encodeSelection)
                        onSelectionChange?.invoke(next)
                    }
                }
        ) {
            val m = model ?: return@Canvas
            drawSankey(m, style, selection)
        }
    }
}

// --- Render model ---------------------------------------------------------------------------

private data class ResolvedEntry(val id: String, val label: String, val amount: Double, val color: Color)

private data class NodeLabel(
    val nameLayout: TextLayoutResult,
    val nameTopLeft: Offset,
    val valueLayout: TextLayoutResult?,
    val valueTopLeft: Offset?,
)

private class SankeyRenderModel(
    val sourceNodes: List<NodeGeometry>,
    val targetNodes: List<NodeGeometry>,
    val hubNode: NodeGeometry?,
    val links: List<LinkGeometry>,
    val colorById: Map<String, Color>,
    val sourceLabels: List<NodeLabel>,
    val targetLabels: List<NodeLabel>,
    val hubLabel: NodeLabel?,
    val hitTargets: List<HitTarget>,
    val nodeCornerRadius: Float,
)

private fun buildRenderModel(
    sources: List<SankeyEntry>,
    targets: List<SankeyEntry>,
    canvasSize: IntSize,
    style: SankeyStyle,
    remainderLabel: String?,
    deficitLabel: String?,
    hubLabel: String?,
    measurer: TextMeasurer,
    density: Density,
): SankeyRenderModel? {
    val srcFiltered = sources.filter { it.amount > 0.0 }
    val tgtFiltered = targets.filter { it.amount > 0.0 }
    if (srcFiltered.isEmpty() && tgtFiltered.isEmpty()) return null

    val sumS = srcFiltered.sumOf { it.amount }
    val sumT = tgtFiltered.sumOf { it.amount }
    val total = maxOf(sumS, sumT)
    if (total <= 0.0) return null

    // Resolve colors: one palette index runs across sources then targets so the two columns don't
    // start on the same hue. Explicit entry colors win and still advance the index.
    val palette = style.palette
    var idx = 0
    val realSources = ArrayList<ResolvedEntry>(srcFiltered.size)
    for (e in srcFiltered) {
        realSources += ResolvedEntry(e.id, e.label, e.amount, e.color ?: palette[idx % palette.size]); idx++
    }
    val realTargets = ArrayList<ResolvedEntry>(tgtFiltered.size)
    for (e in tgtFiltered) {
        realTargets += ResolvedEntry(e.id, e.label, e.amount, e.color ?: palette[idx % palette.size]); idx++
    }

    // Balance the columns so both sides sum to `total` (the allocation math relies on it): a surplus
    // adds a REMAINDER node to the targets, an overspend a DEFICIT ("not covered") node to the
    // sources. The deficit flows into the hub, so the hub reads as total funds, not just income.
    val balanced = balanceSides(
        realSources.map { SankeyNodeInput(it.id, it.amount) },
        realTargets.map { SankeyNodeInput(it.id, it.amount) },
    )
    val realById = HashMap<String, ResolvedEntry>(realSources.size + realTargets.size)
    for (e in realSources) realById[e.id] = e
    for (e in realTargets) realById[e.id] = e
    fun resolve(input: SankeyNodeInput): ResolvedEntry = realById[input.id] ?: when (input.id) {
        REMAINDER_ID -> ResolvedEntry(REMAINDER_ID, remainderLabel ?: "", input.value, style.remainderColor)
        else -> ResolvedEntry(DEFICIT_ID, deficitLabel ?: "", input.value, style.neutralColor)
    }
    val resolvedSources = balanced.sources.map(::resolve)
    val resolvedTargets = balanced.targets.map(::resolve)

    val colorById = HashMap<String, Color>(resolvedSources.size + resolvedTargets.size + 1)
    for (e in resolvedSources) colorById[e.id] = e.color
    for (e in resolvedTargets) colorById[e.id] = e.color
    colorById[HUB_ID] = style.neutralColor

    with(density) {
        val nodeWidthPx = style.nodeWidth.toPx()
        val nodeGapPx = style.nodeGap.toPx()
        val minNodePx = style.minNodeHeight.toPx()
        val labelSpacingPx = style.labelSpacing.toPx()
        val cornerPx = style.nodeCornerRadius.toPx()
        val maxLabelPx = (style.maxLabelWidthFraction * canvasSize.width).toInt().coerceAtLeast(1)

        val labelStyle = style.labelTextStyle.copy(color = style.labelColor)
        val valueStyle = style.valueTextStyle.copy(color = style.valueColor)

        fun measureLine(text: String, ts: TextStyle, maxWidth: Int): TextLayoutResult =
            measurer.measure(
                text = text,
                style = ts,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = Constraints(maxWidth = maxWidth),
            )

        // Side-column labels (values may be wider than names → size margins off the max of both).
        val srcNames = resolvedSources.map { measureLine(it.label, labelStyle, maxLabelPx) }
        val tgtNames = resolvedTargets.map { measureLine(it.label, labelStyle, maxLabelPx) }
        val srcValues = resolvedSources.map { measureLine(style.valueFormatter(it.amount), valueStyle, maxLabelPx) }
        val tgtValues = resolvedTargets.map { measureLine(style.valueFormatter(it.amount), valueStyle, maxLabelPx) }

        val leftMargin = (srcNames.indices.maxOfOrNull { maxOf(srcNames[it].size.width, srcValues[it].size.width) } ?: 0) + labelSpacingPx
        val rightMargin = (tgtNames.indices.maxOfOrNull { maxOf(tgtNames[it].size.width, tgtValues[it].size.width) } ?: 0) + labelSpacingPx

        val vPad = nodeGapPx / 2f

        // The hub label sits centered above the columns; reserve its block height as top padding. It
        // reports the balanced `total` (income plus any not-covered deficit) — i.e. total funds.
        val hubName = hubLabel?.let { measureLine(it, labelStyle, canvasSize.width) }
        val hubValue = if (hubLabel != null) measureLine(style.valueFormatter(total), valueStyle, canvasSize.width) else null
        val hubBlockH = if (hubName != null) hubName.size.height + (hubValue?.size?.height ?: 0) else 0

        val spec = LayoutSpec(
            left = leftMargin,
            top = vPad + hubBlockH + if (hubBlockH > 0) vPad else 0f,
            right = canvasSize.width - rightMargin,
            bottom = canvasSize.height - vPad,
            nodeWidth = nodeWidthPx,
            nodeGap = nodeGapPx,
            minNodeHeight = minNodePx,
        )
        if (spec.right <= spec.left || spec.bottom <= spec.top) return null

        val srcInputs = balanced.sources
        val tgtInputs = balanced.targets

        val sourceNodes: List<NodeGeometry>
        val targetNodes: List<NodeGeometry>
        val hubNode: NodeGeometry?
        val links: List<LinkGeometry>

        if (hubLabel != null) {
            val hub = computeHubLayout(spec, srcInputs, tgtInputs, HUB_ID) ?: return null
            sourceNodes = hub.sources
            targetNodes = hub.targets
            hubNode = hub.hub
            links = hub.leftLinks + hub.rightLinks
        } else {
            val layout = computeSankeyLayout(spec, srcInputs, tgtInputs)
            sourceNodes = layout.sources
            targetNodes = layout.targets
            hubNode = null
            links = layout.links
        }
        if (links.isEmpty()) return null

        val hitTargets = ArrayList<HitTarget>(sourceNodes.size + targetNodes.size)

        fun placeLabels(
            nodes: List<NodeGeometry>,
            names: List<TextLayoutResult>,
            values: List<TextLayoutResult>,
            isSource: Boolean,
        ): List<NodeLabel> {
            val labels = ArrayList<NodeLabel>(nodes.size)
            var prevBottom = Float.NEGATIVE_INFINITY
            for (node in nodes) {
                val name = names[node.index]
                val showValue = node.rect.height >= 18f
                val value = if (showValue) values[node.index] else null
                val blockH = name.size.height + (value?.size?.height ?: 0)
                var top = node.centerY - blockH / 2f
                if (top < prevBottom + 2f) top = prevBottom + 2f // greedy downward nudge
                prevBottom = top + blockH

                val nameX: Float
                val valueX: Float
                val lineW = maxOf(name.size.width, value?.size?.width ?: 0)
                if (isSource) {
                    val rightEdge = spec.left - labelSpacingPx
                    nameX = rightEdge - name.size.width
                    valueX = rightEdge - (value?.size?.width ?: 0)
                } else {
                    nameX = spec.right + labelSpacingPx
                    valueX = spec.right + labelSpacingPx
                }
                val valueTopLeft = value?.let { Offset(valueX, top + name.size.height.toFloat()) }
                labels += NodeLabel(name, Offset(nameX, top), value, valueTopLeft)

                // Hit region = node rect ∪ label block.
                val labelLeft = if (isSource) minOf(nameX, valueX) else node.rect.left
                val labelRight = if (isSource) node.rect.right else nameX + lineW
                val unionTop = minOf(node.rect.top, top)
                val unionBottom = maxOf(node.rect.bottom, top + blockH)
                hitTargets += HitTarget(
                    node.side, node.id,
                    SankeyRect(minOf(labelLeft, node.rect.left), unionTop, maxOf(labelRight, node.rect.right), unionBottom),
                )
            }
            return labels
        }

        val sourceLabels = placeLabels(sourceNodes, srcNames, srcValues, isSource = true)
        val targetLabels = placeLabels(targetNodes, tgtNames, tgtValues, isSource = false)

        // Hub label: centered horizontally over the hub column, stacked at the top.
        val hubNodeLabel = if (hubNode != null && hubName != null) {
            val cx = (hubNode.rect.left + hubNode.rect.right) / 2f
            val valueTopLeft = hubValue?.let { Offset(cx - it.size.width / 2f, vPad + hubName.size.height) }
            NodeLabel(hubName, Offset(cx - hubName.size.width / 2f, vPad), hubValue, valueTopLeft)
        } else null

        return SankeyRenderModel(
            sourceNodes, targetNodes, hubNode, links, colorById,
            sourceLabels, targetLabels, hubNodeLabel, hitTargets, cornerPx,
        )
    }
}

// --- Drawing --------------------------------------------------------------------------------

private fun DrawScope.drawSankey(
    model: SankeyRenderModel,
    style: SankeyStyle,
    selection: SankeySelection?,
) {
    fun isConnected(link: LinkGeometry): Boolean = when (selection?.side) {
        SankeySide.SOURCE -> link.sourceId == selection.id
        SankeySide.TARGET -> link.targetId == selection.id
        null -> false
    }
    val connectedNodeIds = HashSet<String>()
    if (selection != null) {
        for (link in model.links) if (isConnected(link)) {
            connectedNodeIds += link.sourceId
            connectedNodeIds += link.targetId
        }
        connectedNodeIds += selection.id
    }

    // 1) Ribbons — colored by the non-hub endpoint; alpha by selection state.
    for (link in model.links) {
        val colorId = if (link.sourceId == HUB_ID) link.targetId else link.sourceId
        val color = model.colorById[colorId] ?: style.neutralColor
        val alpha = when {
            selection == null -> style.ribbonAlpha
            isConnected(link) -> style.highlightRibbonAlpha
            else -> style.dimmedAlpha
        }
        val mx = (link.sourceX + link.targetX) / 2f
        val path = Path().apply {
            moveTo(link.sourceX, link.sourceTop)
            cubicTo(mx, link.sourceTop, mx, link.targetTop, link.targetX, link.targetTop)
            lineTo(link.targetX, link.targetBottom)
            cubicTo(mx, link.targetBottom, mx, link.sourceBottom, link.sourceX, link.sourceBottom)
            close()
        }
        drawPath(path, color = color, alpha = alpha)
    }

    // 2) Nodes — dim the unconnected ones when there's a selection.
    fun drawNode(node: NodeGeometry) {
        val dimmed = selection != null && node.id !in connectedNodeIds
        drawRoundRect(
            color = model.colorById[node.id] ?: style.neutralColor,
            topLeft = Offset(node.rect.left, node.rect.top),
            size = Size(node.rect.width, node.rect.height),
            cornerRadius = CornerRadius(model.nodeCornerRadius),
            alpha = if (dimmed) style.dimmedAlpha else 1f,
        )
    }
    model.sourceNodes.forEach(::drawNode)
    model.targetNodes.forEach(::drawNode)
    model.hubNode?.let(::drawNode)

    // 3) Labels — always in text tokens.
    fun drawNodeLabel(label: NodeLabel) {
        drawText(label.nameLayout, topLeft = label.nameTopLeft)
        if (label.valueLayout != null && label.valueTopLeft != null) {
            drawText(label.valueLayout, topLeft = label.valueTopLeft)
        }
    }
    model.sourceNodes.forEach { drawNodeLabel(model.sourceLabels[it.index]) }
    model.targetNodes.forEach { drawNodeLabel(model.targetLabels[it.index]) }
    model.hubLabel?.let(::drawNodeLabel)
}

// --- Selection encoding (for rememberSaveable) ----------------------------------------------

// Encode as a single ordinal digit prefix + the id, so no delimiter can collide with an id.
private fun encodeSelection(s: SankeySelection): String = "${s.side.ordinal}${s.id}"

private fun decodeSelection(key: String?): SankeySelection? {
    if (key.isNullOrEmpty()) return null
    val ordinal = key[0].digitToIntOrNull() ?: return null
    val side = SankeySide.entries.getOrNull(ordinal) ?: return null
    return SankeySelection(side, key.substring(1))
}

// --- Previews ---------------------------------------------------------------------------------

private val previewSources = listOf(
    SankeyEntry("salary", "Salary", 4200.0),
    SankeyEntry("freelance", "Freelance", 900.0),
    SankeyEntry("interest", "Interest", 180.0),
)
private val previewTargets = listOf(
    SankeyEntry("rent", "Rent", 1600.0),
    SankeyEntry("groceries", "Groceries", 720.0),
    SankeyEntry("transport", "Transport", 340.0),
    SankeyEntry("fun", "Entertainment", 260.0),
)

@Preview(widthDp = 360, heightDp = 320)
@Composable
private fun SankeyDiagramLightPreview() {
    Box(Modifier.size(360.dp, 320.dp).background(Color(0xFFFCFCFB))) {
        SankeyDiagram(
            sources = previewSources,
            targets = previewTargets,
            style = SankeyDefaults.style(darkTheme = false),
            remainderLabel = "Unspent",
            hubLabel = "Total Funds",
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(widthDp = 360, heightDp = 320)
@Composable
private fun SankeyDiagramDarkPreview() {
    Box(Modifier.size(360.dp, 320.dp).background(Color(0xFF1A1A19))) {
        SankeyDiagram(
            sources = previewSources,
            targets = previewTargets,
            style = SankeyDefaults.style(darkTheme = true),
            remainderLabel = "Unspent",
            hubLabel = "Total Funds",
            modifier = Modifier.fillMaxSize(),
        )
    }
}
