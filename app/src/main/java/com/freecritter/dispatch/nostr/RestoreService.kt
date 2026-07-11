package com.freecritter.dispatch.nostr

import android.util.Log
import com.freecritter.dispatch.data.db.ComponentType
import com.freecritter.dispatch.data.db.DispatchDatabase
import com.freecritter.dispatch.data.db.InteractionNote
import com.freecritter.dispatch.data.db.Place
import com.freecritter.dispatch.data.db.PlaceKind
import com.freecritter.dispatch.data.db.Prospect
import com.freecritter.dispatch.data.db.ReceiptLocation
import com.freecritter.dispatch.data.db.TicketRefType
import com.freecritter.dispatch.data.db.Trip
import com.freecritter.dispatch.data.db.TripComponent
import com.freecritter.dispatch.data.db.TripRating
import com.freecritter.dispatch.data.db.TripStatus
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient

/**
 * Restore flow (spec §5): fetch { authors:[me], kinds:[30078] } from every
 * configured relay, NIP-44 decrypt-to-self, route by the payload's internal
 * "type", and merge into Room with last-write-wins by payload updatedAt
 * (event/entity id as tiebreaker via deterministic iteration).
 *
 * Writes go straight to DAOs — NOT through the repository — so restoring
 * never re-enqueues backup publishes for data that came from the relays.
 * Idempotent: safe to run on a populated database; newer local rows win.
 */
class RestoreService(
    private val db: DispatchDatabase,
    private val signer: NostrSigner,
) {
    data class Result(val eventsFound: Int, val restored: Int, val skipped: Int)

    suspend fun restore(): Result {
        val okHttp = OkHttpClient()
        val client = NostrClient(object : WebsocketBuilder {
            override fun build(url: NormalizedRelayUrl, out: WebSocketListener): WebSocket =
                BasicOkHttpWebSocket(url, { okHttp }, out)
        })

        var restored = 0
        var skipped = 0
        val seen = mutableMapOf<String, Long>() // entityId -> updatedAt already applied this run

        try {
            client.connect()
            val filter = Filter(
                authors = listOf(signer.pubKey),
                kinds = listOf(AppSpecificDataEvent.KIND),
            )

            // Collect from every relay: different relays may hold different latest versions.
            val events = RelayConfig.normalized()
                .flatMap { relay ->
                    runCatching { client.fetchAll(relay = relay, filter = filter) }
                        .getOrElse { e ->
                            Log.w(TAG, "fetch from ${relay.url} failed: ${e.message}")
                            emptyList()
                        }
                }
                .distinctBy { it.id }
                .sortedWith(compareBy({ it.createdAt }, { it.id })) // deterministic apply order

            Log.d(TAG, "fetched ${events.size} events")

            for (event in events) {
                try {
                    if (event.content.isEmpty()) { skipped++; continue } // tombstone
                    val plaintext = signer.nip44Decrypt(event.content, signer.pubKey)
                    val payload = Json.parseToJsonElement(plaintext).jsonObject
                    if (applyPayload(payload, seen)) restored++ else skipped++
                } catch (e: Exception) {
                    Log.w(TAG, "skipping undecodable event ${event.id.take(8)}…: ${e.message}")
                    skipped++
                }
            }
            return Result(events.size, restored, skipped)
        } finally {
            client.close()
        }
    }

    /** Returns true if the row was applied, false if skipped (older than existing). */
    private suspend fun applyPayload(p: JsonObject, seen: MutableMap<String, Long>): Boolean {
        val id = p.str("id") ?: return false
        val updatedAt = p.lng("updatedAt") ?: 0L

        // Skip if this run already applied a newer version of the same entity.
        seen[id]?.let { if (it >= updatedAt) return false }

        val applied: Boolean = when (p.str("type")) {
            "trip" -> {
                val existing = db.tripDao().getById(id)
                if (existing != null && existing.updatedAt >= updatedAt) false
                else {
                    db.tripDao().upsert(
                        Trip(
                            id = id,
                            name = p.str("name") ?: "Untitled",
                            purpose = p.str("purpose") ?: "",
                            city = p.str("city") ?: "",
                            venue = p.str("venue") ?: "",
                            startDate = p.str("startDate") ?: "",
                            endDate = p.str("endDate") ?: "",
                            status = enumOr(p.str("status"), TripStatus.UPCOMING),
                            notes = p.str("notes") ?: "",
                            rating = enumOr(p.str("rating"), TripRating.UNRATED),
                            goAgain = p.bool("goAgain") ?: false,
                            updatedAt = updatedAt,
                        ),
                    )
                    true
                }
            }
            "component" -> {
                val existing = db.tripComponentDao().getById(id)
                if (existing != null && existing.updatedAt >= updatedAt) false
                else {
                    db.tripComponentDao().upsert(
                        TripComponent(
                            id = id,
                            tripId = p.str("tripId") ?: return false,
                            type = enumOr(p.str("componentType"), ComponentType.OTHER),
                            title = p.str("title") ?: "",
                            startEpochSec = p.lng("startEpochSec"),
                            startZoneId = p.str("startZoneId"),
                            endEpochSec = p.lng("endEpochSec"),
                            endZoneId = p.str("endZoneId"),
                            address = p.str("address") ?: "",
                            confirmationRef = p.str("confirmationRef") ?: "",
                            costCents = p.lng("costCents"),
                            booked = p.bool("booked") ?: false,
                            receiptObtained = p.bool("receiptObtained") ?: false,
                            receiptLocation = enumOr(p.str("receiptLocation"), ReceiptLocation.NONE),
                            ticketRefType = enumOr(p.str("ticketRefType"), TicketRefType.NONE),
                            ticketRefValue = p.str("ticketRefValue") ?: "",
                            notes = p.str("notes") ?: "",
                            updatedAt = updatedAt,
                        ),
                    )
                    true
                }
            }
            "prospect" -> {
                db.prospectDao().upsert(
                    Prospect(
                        id = id,
                        name = p.str("name") ?: "",
                        company = p.str("company") ?: "",
                        role = p.str("role") ?: "",
                        email = p.str("email") ?: "",
                        phone = p.str("phone") ?: "",
                        whereMet = p.str("whereMet") ?: "",
                        socialX = p.str("socialX") ?: "",
                        socialLinkedIn = p.str("socialLinkedIn") ?: "",
                        socialTelegram = p.str("socialTelegram") ?: "",
                        socialSignal = p.str("socialSignal") ?: "",
                        socialNpub = p.str("socialNpub") ?: "",
                        website = p.str("website") ?: "",
                        updatedAt = updatedAt,
                    ),
                )
                true
            }
            "note" -> {
                db.interactionNoteDao().upsert(
                    InteractionNote(
                        id = id,
                        prospectId = p.str("prospectId") ?: return false,
                        tripId = p.str("tripId"),
                        occurredEpochSec = p.lng("occurredEpochSec") ?: 0L,
                        zoneId = p.str("zoneId") ?: "UTC",
                        content = p.str("content") ?: "",
                        updatedAt = updatedAt,
                    ),
                )
                true
            }
            "place" -> {
                db.placeDao().upsert(
                    Place(
                        id = id,
                        kind = enumOr(p.str("kind"), PlaceKind.CITY),
                        name = p.str("name") ?: "",
                        parentCity = p.str("parentCity") ?: "",
                        rating = enumOr(p.str("rating"), TripRating.UNRATED),
                        notes = p.str("notes") ?: "",
                        updatedAt = updatedAt,
                    ),
                )
                true
            }
            else -> false // unknown type (future schema) — leave alone
        }

        if (applied) seen[id] = updatedAt
        return applied
    }

    // --- tiny JsonObject helpers ---
    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.takeIf { it.isString || it.content != "null" }?.content?.takeIf { it != "null" }

    private fun JsonObject.lng(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull

    private fun JsonObject.bool(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        runCatching { if (name == null) default else enumValueOf<T>(name) }.getOrDefault(default)

    companion object {
        private const val TAG = "RestoreService"
    }
}
