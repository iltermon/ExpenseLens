package com.iltermon.expenselens.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String?  // null = both, "expense" = expense only, "income" = income only
)
