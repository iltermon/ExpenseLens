package com.iltermon.expenselens.ui.transactions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.iltermon.expenselens.data.Account
import com.iltermon.expenselens.data.Category
import com.iltermon.expenselens.data.Counterparty

/**
 * Holds the fields common to both the one-time and recurring transaction forms so
 * their values survive switching between the two tabs. Owned by the screen, which
 * stays in composition while the individual forms come and go.
 */
@Stable
class TransactionFormState {
    var description by mutableStateOf("")
    var amount by mutableStateOf("")
    var selectedCategory by mutableStateOf<Category?>(null)
    var selectedAccount by mutableStateOf<Account?>(null)
    // The counterparty picker keeps both the raw text (what the user typed) and, when it matches an
    // existing row, the selected entity — so a save can tell "new name" from "known counterparty".
    var counterpartyName by mutableStateOf("")
    var selectedCounterparty by mutableStateOf<Counterparty?>(null)
}

@Composable
fun rememberTransactionFormState(): TransactionFormState = remember { TransactionFormState() }
