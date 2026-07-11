package com.freecritter.dispatch.nostr

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.freecritter.dispatch.DispatchApp
import com.freecritter.dispatch.data.db.OutboxStatus
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirmDetailed
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Drains the durable publish queue (spec §2): for each pending item,
 * NIP-44 encrypt (to self for backups; to recipient for future shares) ->
 * sign kind-30078 with the item's opaque d -> publish to all configured
 * relays -> delete on any OK -> retry with backoff otherwise.
 * Runs in the background with a network constraint; app need not be open.
 */
class OutboxWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as DispatchApp
        if (!app.keyManager.hasIdentity()) return Result.success() // nothing to do pre-onboarding

        val signer = app.keyManager.signer()
        val outbox = app.database.outboxDao()
        val pending = outbox.nextPending()
        if (pending.isEmpty()) return Result.success()

        val okHttp = OkHttpClient()
        val client = NostrClient(object : WebsocketBuilder {
            override fun build(url: NormalizedRelayUrl, out: WebSocketListener): WebSocket =
                BasicOkHttpWebSocket(url, { okHttp }, out)
        })

        var anyFailed = false
        try {
            client.connect()
            val relays = RelayConfig.normalized()

            for (item in pending) {
                try {
                    val recipient = item.recipientPubkeyHex ?: signer.pubKey // self = backup
                    val ciphertext =
                        if (item.payloadJson.isEmpty()) "" // tombstone: empty content overwrite
                        else signer.nip44Encrypt(item.payloadJson, recipient)

                    val template = AppSpecificDataEvent.build(dTag = item.eventD, description = ciphertext)
                    val event = signer.sign(template)

                    val results = client.publishAndConfirmDetailed(event, relays, timeoutInSeconds = 15)
                    val accepted = results.any { it.value }
                    Log.d(TAG, "publish d=${item.eventD.take(8)}… accepted=$accepted (${results.count { it.value }}/${results.size} relays)")

                    if (accepted) {
                        outbox.deleteById(item.id)
                    } else {
                        anyFailed = true
                        outbox.upsert(item.copy(status = OutboxStatus.PENDING, attempts = item.attempts + 1))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "publish failed for ${item.entityType}/${item.entityId}", e)
                    anyFailed = true
                    outbox.upsert(item.copy(attempts = item.attempts + 1))
                }
            }
        } finally {
            client.close()
        }

        return if (anyFailed) Result.retry() else Result.success()
    }

    companion object {
        private const val TAG = "OutboxWorker"
        private const val UNIQUE_NAME = "dispatch-outbox-drain"

        /** Ask WorkManager to drain soon (called after every save). Coalesces naturally. */
        fun requestDrain(context: Context) {
            val request = OneTimeWorkRequestBuilder<OutboxWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}