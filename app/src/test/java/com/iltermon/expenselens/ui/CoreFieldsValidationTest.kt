package com.iltermon.expenselens.ui

import com.iltermon.expenselens.ui.transactions.TransactionFormState
import com.iltermon.expenselens.ui.transactions.coreFieldsValid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CoreFieldsValidationTest {

    @Test fun `description and amount are trimmed for saving`() {
        val state = TransactionFormState().apply {
            description = "  Item A \n"
            amount = " 12.5 "
        }
        val validation = coreFieldsValid(state)
        assertEquals("Item A", validation.description)
        assertEquals(12.5, validation.amountValue!!, 1e-9)
    }

    @Test fun `whitespace-only description is invalid`() {
        val state = TransactionFormState().apply { description = "   " }
        assertFalse(coreFieldsValid(state).descriptionValid)
    }
}
