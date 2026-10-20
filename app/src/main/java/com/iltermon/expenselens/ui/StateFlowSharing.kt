package com.iltermon.expenselens.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * How long a ViewModel StateFlow keeps its upstream (e.g. a Room query) alive after the last screen
 * stops collecting. A rotation re-subscribes well within this window, so it doesn't restart queries.
 */
internal const val SUBSCRIBE_TIMEOUT_MS = 5000L

/** `stateIn` with the app's standard sharing policy; used for every ViewModel StateFlow. */
internal fun <T> Flow<T>.stateInWhileSubscribed(scope: CoroutineScope, initial: T): StateFlow<T> =
    stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), initial)
