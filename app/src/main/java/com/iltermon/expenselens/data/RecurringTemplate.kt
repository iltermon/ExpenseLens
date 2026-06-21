package com.iltermon.expenselens.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recurring_templates")
data class RecurringTemplate(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val description: String,
    val amount: Double,
    val category: String,
    val startDate: String,         // format: "YYYY-MM-DD" — anchor for occurrence generation
    val endDate: String?,          // format: "YYYY-MM-DD"; null = open-ended
    val isExpense: Boolean,
    val frequencyInterval: Int = 1,
    val frequencyUnit: String = "Monthly",
    val autoPayment: Boolean = true,
    val accountId: Int? = null
)