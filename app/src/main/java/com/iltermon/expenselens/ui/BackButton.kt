package com.iltermon.expenselens.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.iltermon.expenselens.R

/**
 * Standard top-bar back affordance: an arrow icon (auto-mirrored for RTL) with a localized
 * "Back" content description. Used as the `navigationIcon` on screens that push onto the stack.
 */
@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.action_back)
        )
    }
}
