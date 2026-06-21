package com.iltermon.expenselens.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String?,  // null = both, "expense" = expense only, "income" = income only
    val limitMonthly: Double? = null,  // optional net spending limit per month
    val limitYearly: Double? = null    // optional net spending limit per year
)
