package com.tacnav.mortar.messaging

import com.tacnav.mortar.network.DeviceMailboxApi
import com.tacnav.mortar.network.MailboxDelivery
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reads the authoritative mailbox and acknowledges a delivery only after it is
 * recorded locally. FCM merely triggers [sync]; app start, resume, reconnect, and
 * the active-session loop call it too, so a lost push only adds latency.
 */
class MailboxSync(
    private val mailbox: DeviceMailboxApi,
    private val onReceived: (MailboxDelivery) -> Unit,
) {
    private val mutex = Mutex()
    private val seen = linkedSetOf<String>()

    suspend fun sync(): Int = mutex.withLock {
        var cursor: Long? = null
        var processed = 0
        while (true) {
            val batch = mailbox.inbox(cursor)
            if (batch.isEmpty()) break
            batch.forEach { delivery ->
                // Recorded before acknowledgement; a failed record leaves it pending.
                if (delivery.deliveryReference !in seen) {
                    onReceived(delivery)
                    seen.add(delivery.deliveryReference)
                    processed += 1
                }
                mailbox.acknowledge(delivery.deliveryReference)
            }
            while (seen.size > SEEN_LIMIT) seen.remove(seen.first())
            cursor = batch.last().cursor
            if (batch.size < PAGE_SIZE) break
        }
        processed
    }

    fun reset() {
        seen.clear()
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val SEEN_LIMIT = 500
    }
}
