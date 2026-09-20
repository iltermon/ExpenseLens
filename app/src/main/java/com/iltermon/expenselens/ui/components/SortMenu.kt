package com.iltermon.expenselens.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.iltermon.expenselens.R
import com.iltermon.expenselens.ui.transactions.SortKey
import com.iltermon.expenselens.ui.transactions.SortState

/** Localized label for a sort key. */
@Composable
internal fun sortKeyLabel(key: SortKey): String = stringResource(
    when (key) {
        SortKey.DATE -> R.string.sort_by_date
        SortKey.AMOUNT -> R.string.sort_by_amount
        SortKey.DESCRIPTION -> R.string.sort_by_description
        SortKey.CATEGORY -> R.string.sort_by_category
        SortKey.COUNTERPARTY -> R.string.sort_by_counterparty
    }
)

/**
 * The sort dropdown anchored to the sort icon. Every key is listed; the active one shows a direction
 * arrow as its trailing icon. Selecting a key delegates to [onSelect] — the ViewModel's
 * `SortState.tapped` flips direction when the active key is re-selected, else switches ascending.
 */
@Composable
internal fun SortMenu(
    expanded: Boolean,
    sort: SortState,
    onSelect: (SortKey) -> Unit,
    onDismiss: () -> Unit,
    keys: List<SortKey> = SortKey.entries,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        keys.forEach { key ->
            val active = key == sort.key
            DropdownMenuItem(
                text = { Text(sortKeyLabel(key)) },
                onClick = { onSelect(key); onDismiss() },
                trailingIcon = if (!active) null else {
                    {
                        Icon(
                            imageVector = if (sort.ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = stringResource(
                                if (sort.ascending) R.string.cd_sort_ascending else R.string.cd_sort_descending
                            )
                        )
                    }
                }
            )
        }
    }
}
