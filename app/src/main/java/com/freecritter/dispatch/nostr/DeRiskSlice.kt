package com.freecritter.dispatch.nostr

import android.util.Log
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchFirst
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import okhttp3.OkHttpClient
import java.util.UUID
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirmDetailed

/**
 * TEMPORARY — Build order step 1: the de-risk slice.
 *
 * Proves the entire novel part of Dispatch end to end:
 *   fresh keypair -> NIP-44 encrypt-to-self -> sign kind-30078 (opaque d) ->
 *   publish to relay -> OK confirmed -> read back -> decrypt -> plaintext matches.
 *
 * Run it, watch Logcat (filter: DeRiskSlice), then DELETE this file once the
 * green line prints. The real NostrSignerInternal/OutboxWorker implementations
 * replace it, built from the exact same calls proven here.
 */
object DeRiskSlice {
    private const val TAG = "DeRiskSlice"
    private const val RELAY = "wss://nos.lol"
    private const val RELAY2 = "wss://relay.primal.net"

    private val okHttp = OkHttpClient()

    /** Minimal WebsocketBuilder: Quartz's BasicOkHttpWebSocket over one shared OkHttpClient. */
    private val socketBuilder = object : WebsocketBuilder {
        override fun build(url: NormalizedRelayUrl, out: WebSocketListener): WebSocket =
            BasicOkHttpWebSocket(url, { okHttp }, out)
    }

    suspend fun run(onStatus: (String) -> Unit = {}) {
        fun log(msg: String) {
            Log.d(TAG, msg)
            onStatus(msg)
        }

        val client = NostrClient(socketBuilder)
        try {
            // 1. Fresh, never-published identity — the same call real onboarding will use.
            val signer = NostrSignerInternal(KeyPair())
            log("1/6 keypair generated, pubkey=${signer.pubKey.take(16)}…")

            // 2. NIP-44 encrypt-to-self (own pubkey as counterparty — NIP-51 private-items pattern).
            val plaintext = """{"v":1,"type":"slice","msg":"Dispatch round trip ${System.currentTimeMillis()}"}"""
            val ciphertext = signer.nip44Encrypt(plaintext, signer.pubKey)
            log("2/6 NIP-44 encrypted (${ciphertext.length} chars)")

            // 3. Kind-30078 addressable event, opaque UUID d tag, ciphertext as content.
            val d = UUID.randomUUID().toString()
            val template = AppSpecificDataEvent.build(dTag = d, description = ciphertext)
            val event = signer.sign(template)
            log("3/6 signed event id=${event.id.take(16)}… kind=${event.kind} d=$d")

            // 4. Publish to both defaults and wait for OKs.
            val relays = setOf(
                RelayUrlNormalizer.normalize(RELAY),
                RelayUrlNormalizer.normalize(RELAY2),
            )
            client.connect()
            val results = client.publishAndConfirmDetailed(event, relays, timeoutInSeconds = 15)
            results.forEach { (r, ok) -> log("4/6 ${r.url} -> ${if (ok) "accepted" else "rejected/timeout"}") }
            val okRelay = results.entries.firstOrNull { it.value }?.key
            if (okRelay == null) {
                log("4/6 no relay accepted — check Logcat tag publishAndConfirm for reasons")
                return
            }

            // 5. Read it back from the relay that accepted it.
            val fetched = client.fetchFirst(
                relay = okRelay,
                filter = Filter(
                    authors = listOf(signer.pubKey),
                    kinds = listOf(AppSpecificDataEvent.KIND),
                    limit = 1,
                ),
            )
            if (fetched == null) {
                log("5/6 FAILED: nothing came back from subscription")
                return
            }
            log("5/6 fetched event id=${fetched.id.take(16)}… matches=${fetched.id == event.id}")

            // 6. Decrypt and compare.
            val roundTripped = signer.nip44Decrypt(fetched.content, signer.pubKey)
            val match = roundTripped == plaintext
            log("6/6 decrypted, plaintext match=$match")
            log(if (match) "✅ DE-RISK SLICE COMPLETE — Plan A (Quartz) confirmed" else "❌ decrypt mismatch")
        } catch (e: Exception) {
            Log.e(TAG, "Slice failed", e)
            onStatus("❌ ${e.message}")
        } finally {
            client.close()
        }
    }
}
