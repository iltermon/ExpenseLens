package com.iltermon.expenselens.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iltermon.expenselens.R

/** A tappable Settings landing entry: title on the left, chevron on the right, opens a sub-screen. */
@Composable
fun SettingsMenuRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Maps the stored category type (null/"expense"/"income") to its localized label. */
@Composable
internal fun categoryTypeLabel(type: String?): String = when (type) {
    "expense" -> stringResource(R.string.category_type_expense_only)
    "income" -> stringResource(R.string.category_type_income_only)
    else -> stringResource(R.string.category_type_both)
}

/** Optional "per month / per year" limit line shown under an account/category row. */
@Composable
internal fun LimitSubtitle(limitMonthly: Double?, limitYearly: Double?) {
    if (limitMonthly == null && limitYearly == null) return
    val parts = mutableListOf<String>()
    if (limitMonthly != null) parts.add(stringResource(R.string.limit_per_month, money(limitMonthly, 0)))
    if (limitYearly != null) parts.add(stringResource(R.string.limit_per_year, money(limitYearly, 0)))
    Text(
        text = stringResource(R.string.limit_subtitle, parts.joinToString(stringResource(R.string.limit_separator))),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

/** Small "Disabled" label shown on a greyed account/category row. */
@Composable
internal fun DisabledBadge() {
    Text(
        text = stringResource(R.string.status_disabled),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.error
    )
}
