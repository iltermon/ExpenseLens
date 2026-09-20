package com.iltermon.expenselens.ui.templates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iltermon.expenselens.R
import com.iltermon.expenselens.data.RecurringTransactionTemplate
import com.iltermon.expenselens.ui.ExpenseLensViewModel
import com.iltermon.expenselens.ui.transactions.ListFilterState
import com.iltermon.expenselens.ui.transactions.SortKey
import com.iltermon.expenselens.ui.transactions.SortState
import com.iltermon.expenselens.ui.components.BackButton
import com.iltermon.expenselens.ui.components.FilterBottomSheet
import com.iltermon.expenselens.ui.components.NoFilterResults
import com.iltermon.expenselens.ui.components.SearchField
import com.iltermon.expenselens.ui.components.SortMenu
import com.iltermon.expenselens.ui.transactions.matchesFields
import com.iltermon.expenselens.ui.util.frequencyLabel
import com.iltermon.expenselens.ui.util.money
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class TemplateFilter { ALL, ACTIVE, INACTIVE }

/** Keys offered when sorting templates (category/counterparty aren't meaningful here). DATE = start date. */
private val templateSortKeys = listOf(SortKey.DESCRIPTION, SortKey.AMOUNT, SortKey.DATE)

/** A template is ended once its (optional) end date is strictly in the past; null = open-ended. */
internal fun RecurringTransactionTemplate.isEnded(today: LocalDate = LocalDate.now()): Boolean =
    endDate != null && LocalDate.parse(endDate) < today

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    viewModel: ExpenseLensViewModel,
    onNavigateBack: () -> Unit,
    onEditTemplate: (Int) -> Unit
) {
    val templates by viewModel.allTemplates.collectAsState()
    val counterparties by viewModel.counterparties.collectAsState()
    val allCategories by viewModel.allCategories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val counterpartyNames = remember(counterparties) { counterparties.associate { it.id to it.name } }
    var filter by remember { mutableStateOf(TemplateFilter.ALL) }
    var sort by remember { mutableStateOf(SortState(SortKey.DESCRIPTION)) }
    var sortOpen by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(ListFilterState()) }
    var showSheet by remember { mutableStateOf(false) }
    var deleteTemplate by remember { mutableStateOf<RecurringTransactionTemplate?>(null) }

    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    // Exiting search mode clears both the query and the advanced filter (matches the tab screens).
    fun exitSearch() { searchActive = false; query = ""; advanced = ListFilterState()
    }
    // System back exits search mode first, mirroring the app-bar back arrow.
    BackHandler(enabled = searchActive) { exitSearch() }

    // Dismiss the keyboard before opening the filter sheet so it opens at full height in one step.
    fun openFilters() { keyboard?.hide(); scope.launch { delay(100); showSheet = true } }

    val today = LocalDate.now()
    val visible = remember(templates, filter, sort, query, advanced, counterpartyNames) {
        val q = query.trim()
        // Ended templates always sink to the bottom (fixed primary key); the chosen sort orders the rest.
        val keyCmp: Comparator<RecurringTransactionTemplate> = when (sort.key) {
            SortKey.AMOUNT -> compareBy { it.amount }
            SortKey.DATE -> compareBy { it.startDate }
            else -> compareBy { it.description.lowercase() }
        }
        val directed = if (sort.ascending) keyCmp else keyCmp.reversed()
        templates
            .sortedWith(compareBy<RecurringTransactionTemplate> { it.isEnded(today) }.then(directed).thenBy { it.id })
            .filter {
                val matchesFilter = when (filter) {
                    TemplateFilter.ALL -> true
                    TemplateFilter.ACTIVE -> !it.isEnded(today)
                    TemplateFilter.INACTIVE -> it.isEnded(today)
                }
                // Search matches description OR counterparty name (mirrors the transaction search).
                val matchesQuery = q.isBlank() ||
                    it.description.contains(q, ignoreCase = true) ||
                    (it.counterpartyId?.let { id -> counterpartyNames[id] }?.contains(q, ignoreCase = true) == true)
                matchesFilter && matchesQuery &&
                    advanced.matchesFields(it.categoryId, it.accountId, it.counterpartyId, it.amount)
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        SearchField(value = query, onValueChange = { query = it })
                    } else {
                        Text(stringResource(R.string.settings_templates))
                    }
                },
                navigationIcon = {
                    if (searchActive) {
                        IconButton(onClick = { exitSearch() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_search_close))
                        }
                    } else {
                        BackButton(onClick = onNavigateBack)
                    }
                },
                actions = {
                    if (searchActive) {
                        BadgedBox(badge = { if (advanced.advancedCount > 0) Badge { Text(advanced.advancedCount.toString()) } }) {
                            IconButton(onClick = { openFilters() }) {
                                Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.filter_open))
                            }
                        }
                    } else {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search_open))
                        }
                    }
                    Box {
                        IconButton(onClick = { sortOpen = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.sort_open))
                        }
                        SortMenu(
                            expanded = sortOpen,
                            sort = sort,
                            onSelect = { sort = sort.tapped(it) },
                            onDismiss = { sortOpen = false },
                            keys = templateSortKeys
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                val entries = listOf(
                    TemplateFilter.ALL to R.string.templates_filter_all,
                    TemplateFilter.ACTIVE to R.string.templates_filter_active,
                    TemplateFilter.INACTIVE to R.string.templates_filter_inactive
                )
                entries.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = filter == value,
                        onClick = { filter = value },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = entries.size)
                    ) { Text(stringResource(label)) }
                }
            }

            if (visible.isEmpty() && (query.isNotBlank() || advanced.isActive)) {
                NoFilterResults(onClearFilters = { query = ""; advanced = ListFilterState() })
            } else {
                LazyColumn {
                    items(visible) { template ->
                        TemplateRow(
                            template = template,
                            ended = template.isEnded(today),
                            onClick = { onEditTemplate(template.id) },
                            onDelete = { deleteTemplate = template }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showSheet) {
        FilterBottomSheet(
            filter = advanced,
            categories = allCategories,
            accounts = accounts,
            counterparties = counterparties,
            onUpdateFilter = { transform -> advanced = transform(advanced) },
            onClearFilter = { advanced = ListFilterState() },
            onDismiss = { showSheet = false },
            // Templates have no paid/one-time distinction.
            showStatus = false,
            showRecurrence = false
        )
    }

    deleteTemplate?.let { template ->
        AlertDialog(
            onDismissRequest = { deleteTemplate = null },
            title = { Text(stringResource(R.string.template_delete_title)) },
            text = { Text(stringResource(R.string.template_delete_message, template.description)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTemplate(template)
                    deleteTemplate = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTemplate = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun TemplateRow(
    template: RecurringTransactionTemplate,
    ended: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = template.description, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                text = stringResource(
                    R.string.recurring_frequency_prefix,
                    frequencyLabel(template.frequencyInterval, template.frequencyUnit)
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        StatusChip(
            text = stringResource(if (ended) R.string.template_status_ended else R.string.template_status_active),
            container = if (ended) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
            content = if (ended) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = money(template.amount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (template.isExpense) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.template_delete_title))
        }
    }
}

@Composable
private fun StatusChip(text: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
