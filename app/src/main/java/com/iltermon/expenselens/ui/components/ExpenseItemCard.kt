package com.iltermon.expenselens.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iltermon.expenselens.R
import com.iltermon.expenselens.ui.util.frequencyLabel
import com.iltermon.expenselens.ui.util.money
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ExpenseItemCard(
    item: com.iltermon.expenselens.ui.ExpenseItem,
    onTogglePaid: (com.iltermon.expenselens.ui.ExpenseItem) -> Unit,
    onClick: () -> Unit = {},
    counterpartyName: String? = null,
    categoryName: String? = null,
    remainingOccurrences: Int? = null
) {
    val categoryLabel = categoryName ?: stringResource(R.string.uncategorized)
    val dateFormatter = DateTimeFormatter.ofPattern("d MMM")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isPaid)
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledIconButton(
                onClick = { onTogglePaid(item) },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (item.isPaid)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.tertiary
                ),
                modifier = Modifier.size(40.dp)
            ) {
                Text(if (item.isPaid) "✓" else "−")
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.description,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (!counterpartyName.isNullOrBlank()) "$categoryLabel · $counterpartyName" else categoryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.frequencyUnit != null) {
                    val frequency = stringResource(
                        R.string.recurring_frequency_prefix,
                        frequencyLabel(item.frequencyInterval ?: 1, item.frequencyUnit)
                    )
                    Text(
                        if (remainingOccurrences != null)
                            "$frequency · ${stringResource(R.string.recurring_remaining, remainingOccurrences)}"
                        else frequency,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    money(item.amount),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.isExpense) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
                Text(
                    LocalDate.parse(item.date).format(dateFormatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
