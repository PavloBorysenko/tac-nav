package com.tacnav.mortar.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TorchResult {
    OK,
    UNAVAILABLE,
    BUSY,
}

/**
 * Keeps the torch on for [durationMillis] after each accepted button shot. A later
 * pulse restarts the deadline; torch problems only warn and never undo the shot.
 */
class TorchPulse(
    private val scope: CoroutineScope,
    private val setTorch: (Boolean) -> TorchResult,
    private val onWarning: (String) -> Unit,
    private val durationMillis: Long = DEFAULT_DURATION_MILLIS,
) {
    private var job: Job? = null

    fun pulse() {
        job?.cancel()
        when (setTorch(true)) {
            TorchResult.OK -> Unit
            TorchResult.UNAVAILABLE -> onWarning("Torch is unavailable, but the shot was accepted.")
            TorchResult.BUSY -> onWarning("Torch is busy, but the shot was accepted.")
        }
        job = scope.launch {
            delay(durationMillis)
            setTorch(false)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        setTorch(false)
    }

    companion object {
        const val DEFAULT_DURATION_MILLIS = 5_000L
    }
}
