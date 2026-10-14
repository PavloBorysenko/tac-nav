package com.tacnav.mortar.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class PinRecord(
    val salt: ByteArray,
    val hash: ByteArray,
)

data class PinThrottleState(
    val failedAttempts: Int = 0,
    val blockedUntilEpochMillis: Long = 0,
)

sealed interface PinAttempt {
    data object Accepted : PinAttempt
    data class Rejected(val attemptsRemaining: Int) : PinAttempt
    data class Throttled(val retryAfterMillis: Long) : PinAttempt
}

class PinPolicy(
    private val now: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
) {
    fun create(pin: String): PinRecord {
        validateFormat(pin)
        val salt = ByteArray(16).also(random::nextBytes)
        return PinRecord(salt, derive(pin, salt))
    }

    fun verify(
        pin: String,
        record: PinRecord,
        throttle: PinThrottleState,
    ): Pair<PinAttempt, PinThrottleState> {
        val currentTime = now()
        if (currentTime < throttle.blockedUntilEpochMillis) {
            return PinAttempt.Throttled(throttle.blockedUntilEpochMillis - currentTime) to throttle
        }

        val matches = MessageDigest.isEqual(derive(pin, record.salt), record.hash)
        if (matches) {
            return PinAttempt.Accepted to PinThrottleState()
        }

        val failures = throttle.failedAttempts + 1
        if (failures < ATTEMPTS_BEFORE_DELAY) {
            return PinAttempt.Rejected(ATTEMPTS_BEFORE_DELAY - failures) to
                PinThrottleState(failedAttempts = failures)
        }

        val delayStep = failures - ATTEMPTS_BEFORE_DELAY
        val delay = (BASE_DELAY_MILLIS shl delayStep.coerceAtMost(5))
            .coerceAtMost(MAX_DELAY_MILLIS)
        return PinAttempt.Throttled(delay) to PinThrottleState(
            failedAttempts = failures,
            blockedUntilEpochMillis = currentTime + delay,
        )
    }

    fun validateFormat(pin: String) {
        require(pin.length >= 4 && pin.all(Char::isDigit)) {
            "PIN must contain at least four digits."
        }
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val specification = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(specification)
                .encoded
        } finally {
            specification.clearPassword()
        }
    }

    companion object {
        const val DEFAULT_PIN = "4321"
        private const val ATTEMPTS_BEFORE_DELAY = 5
        private const val BASE_DELAY_MILLIS = 30_000L
        private const val MAX_DELAY_MILLIS = 15 * 60_000L
    }
}
