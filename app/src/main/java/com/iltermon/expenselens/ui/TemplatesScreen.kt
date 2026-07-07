package com.iltermon.expenselens.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iltermon.expenselens.R
import com.iltermon.expenselens.data.RecurringTemplate
import java.time.LocalDate

private enum class TemplateFilter { ALL, ACTIVE, INACTIVE }

/** A template is ended once its (optional) end date is strictly in the past; null = open-ended. */
internal fun RecurringTemplate.isEnded(today: LocalDate = LocalDate.now()): Boolean =
    endDate != null && LocalDate.parse(endDate) < today

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    viewModel: ExpenseLensViewModel,
    onNavigateBack: () -> Unit,
    onEditTemplate: (Int) -> Unit
) {
    val templates by viewModel.allTemplates.collectAsState()
    var filter by remember { mutableStateOf(TemplateFilter.ALL) }
    var deleteTemplate by remember { mutableStateOf<RecurringTemplate?>(null) }

    val today = LocalDate.now()
    val visible = remember(templates, filter) {
        templates
            .sortedWith(compareBy({ it.isEnded(today) }, { it.description.lowercase() }))
            .filter {
                when (filter) {
                    TemplateFilter.ALL -> true
                    TemplateFilter.ACTIVE -> !it.isEnded(today)
                    TemplateFilter.INACTIVE -> it.isEnded(today)
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_templates)) },
                navigationIcon = { BackButton(onClick = onNavigateBack) }
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
    template: RecurringTemplate,
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
