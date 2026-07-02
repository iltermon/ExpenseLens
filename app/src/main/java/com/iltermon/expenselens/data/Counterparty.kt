package com.iltermon.expenselens.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// A counterparty — the store/vendor an expense is paid to, or the payer/source income comes from —
// with curated defaults. The default category/account prefill a transaction form when the
// counterparty is picked; they only change when the user edits the counterparty (in Settings) or
// opts to update them at save time. Name is unique so it can be an identity users rename or merge.
@Entity(tableName = "counterparties", indices = [Index(value = ["name"], unique = true)])
data class Counterparty(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val defaultCategory: String? = null,
    val defaultAccountId: Int? = null
)
