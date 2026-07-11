package com.freecritter.dispatch.nostr

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.freecritter.dispatch.data.db.DispatchDatabase
import com.freecritter.dispatch.data.db.OutboxStatus

/**
 * Drains the durable publish queue in the background (spec §2): encrypt -> sign -> publish
 * per item, delete on relay OK, retry with backoff otherwise. Scheduled with a
 * network-connected constraint; runs with the app closed.
 *
 * Relay I/O + crypto are TODO pending the de-risk slice; until then this worker
 * simply reports success without touching items, so it is safe to schedule.
 */
class OutboxWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val outbox = DispatchDatabase.get(applicationContext).outboxDao()
        val pending = outbox.nextPending()
        if (pending.isEmpty()) return Result.success()

        // TODO(de-risk slice): for each item —
        //   1. signer.nip44Encrypt(payloadJson, recipient ?: self)
        //   2. signer.signEvent(KIND_APP_DATA, DispatchEvents.tags(eventD, recipient), ciphertext, now)
        //   3. publish to all configured relays; await OK
        //   4. on OK: outbox.deleteById(item.id); on failure: increment attempts, mark FAILED after N

        pending.forEach { item ->
            // Placeholder: leave items pending; no-op until Nostr layer lands.
            if (item.status == OutboxStatus.IN_FLIGHT) return@forEach
        }
        return Result.success()
    }
}
