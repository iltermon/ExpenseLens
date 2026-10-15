package com.iltermon.expenselens.ui

import com.iltermon.expenselens.data.RecurringTransactionTemplate
import com.iltermon.expenselens.data.Transaction
import com.iltermon.expenselens.data.occurrencesInRange
import java.time.LocalDate

// The UI's unified list model and the pure logic that builds it from the database rows. Kept free of
// Android/ViewModel types so it can be unit-tested directly (see MergeItemsTest).

data class ExpenseItem(
    val id: Int,
    val description: String,
    val amount: Double,
    val categoryId: Int?,
    val date: String,
    val isExpense: Boolean,
    val isPaid: Boolean,
    val isRecurring: Boolean,
    val transactionId: Int? = null,
    val templateId: Int? = null,          // non-null only for recurring items
    val frequencyInterval: Int? = null,   // non-null only for recurring items
    val frequencyUnit: String? = null,    // non-null only for recurring items
    val accountId: Int? = null,
    val counterpartyId: Int? = null
)

internal fun List<Transaction>.inRange(range: DateRange): List<Transaction> = filter { transaction ->
    val date = LocalDate.parse(transaction.date)
    date >= range.start && date <= range.end
}

// Merges concrete transactions (already filtered to [range]) with the projected occurrences of
// every recurring template in [range], hiding occurrences that already exist as a transaction.
internal fun mergeItems(
    transactions: List<Transaction>,
    templates: List<RecurringTransactionTemplate>,
    range: DateRange
): List<ExpenseItem> {
    val transactionItems = transactions.map { t ->
        ExpenseItem(
            id = t.id,
            description = t.description,
            amount = t.amount,
            categoryId = t.categoryId,
            date = t.date,
            isExpense = t.isExpense,
            isPaid = t.isPaid,
            isRecurring = false,
            transactionId = t.id,
            templateId = t.templateId,
            accountId = t.accountId,
            counterpartyId = t.counterpartyId
        )
    }

    val recurringItems = templates.flatMap { template ->
        template.occurrencesInRange(range.start, range.end)
            // hide an occurrence that's already been recorded as a transaction
            .filter { date ->
                transactions.none { t -> t.templateId == template.id && t.date == date.toString() }
            }
            .map { date ->
                ExpenseItem(
                    id = template.id,
                    description = template.description,
                    amount = template.amount,
                    categoryId = template.categoryId,
                    date = date.toString(),
                    isExpense = template.isExpense,
                    isPaid = false,
                    isRecurring = true,
                    transactionId = null,
                    templateId = template.id,
                    frequencyInterval = template.frequencyInterval,
                    frequencyUnit = template.frequencyUnit,
                    accountId = template.accountId,
                    counterpartyId = template.counterpartyId
                )
            }
    }

    return (transactionItems + recurringItems).sortedBy { it.date }
}
