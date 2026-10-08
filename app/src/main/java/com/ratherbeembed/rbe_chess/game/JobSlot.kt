package com.ratherbeembed.rbe_chess.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Holds at most one running coroutine. Launching replaces (cancels) whatever
 * was there, so each kind of background work -- engine turn, autofill,
 * inactivity prompt -- can never overlap with a stale copy of itself.
 */
internal class JobSlot {
    private var job: Job? = null

    val isActive: Boolean get() = job?.isActive == true

    fun cancel() {
        job?.cancel()
        job = null
    }

    fun launch(scope: CoroutineScope, block: suspend CoroutineScope.() -> Unit) {
        cancel()
        job = scope.launch(block = block)
    }
}
