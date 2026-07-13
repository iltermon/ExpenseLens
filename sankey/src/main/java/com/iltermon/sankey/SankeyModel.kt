package com.iltermon.sankey

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * One node on a side of the diagram. The library derives ribbons between the two sides itself, so
 * callers only supply the aggregated totals — never the links.
 *
 * @param id stable identity used for selection/highlighting; must be unique within its side.
 * @param label text drawn beside the node.
 * @param amount magnitude of the node; must be `>= 0`. Zero-amount entries are dropped.
 * @param color explicit color; when null the node is assigned one from [SankeyStyle.palette].
 */
@Immutable
data class SankeyEntry(
    val id: String,
    val label: String,
    val amount: Double,
    val color: Color? = null,
)

/** Which column an entry lives in: [SOURCE] on the left (incomes), [TARGET] on the right (outputs). */
enum class SankeySide { SOURCE, TARGET }

/** A selected node, identified by its side and [SankeyEntry.id]. */
data class SankeySelection(val side: SankeySide, val id: String)

/** Ids of the synthetic balancing nodes the library injects; never collide with caller ids. */
internal const val REMAINDER_ID = "__remainder__"
internal const val DEFICIT_ID = "__deficit__"

/** Id of the synthetic central "total" node in the hub layout. */
internal const val HUB_ID = "__hub__"
