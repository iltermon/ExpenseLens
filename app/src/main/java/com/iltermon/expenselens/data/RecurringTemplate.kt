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
    val startMonth: String,        // format: "YYYY-MM"
    val endMonth: String?,         // null = open-ended, not null = fixed end
    val isExpense: Boolean,
    val frequencyInterval: Int = 1,
    val frequencyUnit: String = "Monthly",
    val autoPayment: Boolean = true
)