package com.iltermon.expenselens.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Unique (templateId, date) enforces auto-pay idempotency at the DB level: a recurring
// template can produce at most one transaction per occurrence date. SQLite treats NULLs as
// distinct, so manually-entered transactions (templateId = null) are never constrained.
@Entity(
    tableName = "transactions",
    indices = [Index(value = ["templateId", "date"], unique = true)]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val description: String,
    val amount: Double,
    val category: String,
    val date: String,
    val isExpense: Boolean,
    val isPaid: Boolean = false,
    val accountId: Int? = null,
    val templateId: Int? = null   // links an auto/recurring-generated transaction back to its template
)