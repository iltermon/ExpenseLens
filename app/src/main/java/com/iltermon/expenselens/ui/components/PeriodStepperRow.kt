package com.iltermon.expenselens.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Fixed width for the side pills so they never resize as the short month/year label changes. */
private val SIDE_PILL_WIDTH = 92.dp

/**
 * The date navigator shared by the transaction screens and the analytics screen so both read the
 * same: a pill on each end showing the adjacent period's short label with a directional arrow, and a
 * bold center pill with the current period's full label that opens a picker on tap. Callers supply
 * the already-formatted labels; this composable owns only the layout and styling.
 *
 * The side pills are a fixed width and the center pill takes the remaining space, so no button ever
 * shifts position when a label's length changes (e.g. "May 2026" → "September 2026").
 */
@Composable
fun PeriodStepperRow(
    previousLabel: String,
    currentLabel: String,
    nextLabel: String,
    onPrevious: () -> Unit,
    onCurrent: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sidePadding = PaddingValues(horizontal = 12.dp)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onPrevious,
            shape = RoundedCornerShape(50),
            contentPadding = sidePadding,
            modifier = Modifier.width(SIDE_PILL_WIDTH),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(previousLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        TextButton(
            onClick = onCurrent,
            shape = RoundedCornerShape(50),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                currentLabel,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        OutlinedButton(
            onClick = onNext,
            shape = RoundedCornerShape(50),
            contentPadding = sidePadding,
            modifier = Modifier.width(SIDE_PILL_WIDTH),
        ) {
            Text(nextLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
