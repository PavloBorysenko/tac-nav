package com.tacnav.mortar

import com.tacnav.mortar.security.PinAttempt
import com.tacnav.mortar.security.PinPolicy
import com.tacnav.mortar.security.PinThrottleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinPolicyTest {
    @Test
    fun `default pin verifies`() {
        val policy = PinPolicy(now = { 1_000L })
        val record = policy.create(PinPolicy.DEFAULT_PIN)

        val (attempt, state) = policy.verify(
            PinPolicy.DEFAULT_PIN,
            record,
            PinThrottleState(),
        )

        assertEquals(PinAttempt.Accepted, attempt)
        assertEquals(PinThrottleState(), state)
    }

    @Test
    fun `five failures enforce a delay`() {
        val policy = PinPolicy(now = { 1_000L })
        val record = policy.create(PinPolicy.DEFAULT_PIN)
        var throttle = PinThrottleState()
        var attempt: PinAttempt = PinAttempt.Accepted

        repeat(5) {
            val result = policy.verify("0000", record, throttle)
            attempt = result.first
            throttle = result.second
        }

        assertTrue(attempt is PinAttempt.Throttled)
        assertEquals(31_000L, throttle.blockedUntilEpochMillis)
    }
}
