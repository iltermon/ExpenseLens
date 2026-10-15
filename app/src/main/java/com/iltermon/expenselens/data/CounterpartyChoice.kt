package com.iltermon.expenselens.data

/**
 * How the transaction form resolved its counterparty field at save time: either an existing
 * counterparty (optionally re-curating its defaults) or a brand-new name to create.
 */
sealed interface CounterpartyChoice {
    data class Existing(val counterparty: Counterparty, val updateDefaults: Boolean) : CounterpartyChoice
    data class New(val name: String) : CounterpartyChoice
}
