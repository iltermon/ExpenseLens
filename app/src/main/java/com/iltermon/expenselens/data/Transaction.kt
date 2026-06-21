package com.iltermon.expenselens.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
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