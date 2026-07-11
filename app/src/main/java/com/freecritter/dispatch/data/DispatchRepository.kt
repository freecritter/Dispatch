package com.freecritter.dispatch.data

import com.freecritter.dispatch.data.db.DispatchDatabase
import com.freecritter.dispatch.data.db.InteractionNote
import com.freecritter.dispatch.data.db.OutboxItem
import com.freecritter.dispatch.data.db.Place
import com.freecritter.dispatch.data.db.Prospect
import com.freecritter.dispatch.data.db.Trip
import com.freecritter.dispatch.data.db.TripComponent
import com.freecritter.dispatch.data.db.TripRating
import com.freecritter.dispatch.data.db.TripStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/**
 * Single source of truth gate (spec §2). ViewModels talk ONLY to this class.
 * Every save writes Room first (instant UI), then enqueues an encrypted-backup
 * publish in the outbox. Serialization here produces the PLAINTEXT payload;
 * encryption happens later, at publish time, inside the Nostr layer.
 */
class DispatchRepository(
    private val db: DispatchDatabase,
    private val onOutboxEnqueued: () -> Unit = {},
) {

    // --- Trips ---
    fun observeTrips() = db.tripDao().observeActive()
    fun observeTrip(id: String) = db.tripDao().observeById(id)

    suspend fun saveTrip(trip: Trip) {
        val stamped = trip.copy(updatedAt = now())
        db.tripDao().upsert(stamped)
        enqueueBackup("trip", stamped.id, tripPayload(stamped))
    }

    suspend fun duplicateTrip(sourceId: String, newStartDate: String, newEndDate: String): Trip? {
        val source = db.tripDao().getById(sourceId) ?: return null
        val clone = source.copy(
            id = UUID.randomUUID().toString(),
            name = source.name + " (copy)",
            startDate = newStartDate,
            endDate = newEndDate,
            status = TripStatus.UPCOMING,
            rating = TripRating.UNRATED,
            goAgain = false
        )
        saveTrip(clone)
        // TODO: clone components with booked=false, receiptObtained=false (build step 4)
        return clone
    }

    // --- Components ---
    fun observeComponents(tripId: String) = db.tripComponentDao().observeForTrip(tripId)

    suspend fun saveComponent(component: TripComponent) {
        val stamped = component.copy(updatedAt = now())
        db.tripComponentDao().upsert(stamped)
        enqueueBackup("component", stamped.id, componentPayload(stamped))
    }

    suspend fun setComponentBooked(id: String, booked: Boolean) {
        db.tripComponentDao().updateBooked(id, booked, now())
        db.tripComponentDao().getById(id)?.let { enqueueBackup("component", it.id, componentPayload(it)) }
        onOutboxEnqueued()
    }

    suspend fun setComponentReceipt(id: String, obtained: Boolean) {
        db.tripComponentDao().updateReceipt(id, obtained, now())
        db.tripComponentDao().getById(id)?.let { enqueueBackup("component", it.id, componentPayload(it)) }
        onOutboxEnqueued()
    }

    // --- CRM ---
    fun observeProspects() = db.prospectDao().observeAll()

    suspend fun saveProspect(prospect: Prospect) {
        val stamped = prospect.copy(updatedAt = now())
        db.prospectDao().upsert(stamped)
        enqueueBackup("prospect", stamped.id, prospectPayload(stamped))
    }

    suspend fun saveNote(note: InteractionNote) {
        val stamped = note.copy(updatedAt = now())
        db.interactionNoteDao().upsert(stamped)
        enqueueBackup("note", stamped.id, notePayload(stamped))
    }

    // --- Places ---
    fun observePlaces() = db.placeDao().observeAll()
    suspend fun placesForCity(city: String) = db.placeDao().forCity(city)

    suspend fun savePlace(place: Place) {
        val stamped = place.copy(updatedAt = now())
        db.placeDao().upsert(stamped)
        enqueueBackup("place", stamped.id, placePayload(stamped))
    }

    // --- Backup status ---
    fun observePendingBackups() = db.outboxDao().observePendingCount()

    // --- Internals ---
    private fun now() = System.currentTimeMillis() / 1000

    private suspend fun enqueueBackup(type: String, entityId: String, payloadJson: String) {
        db.outboxDao().upsert(
            OutboxItem(
                entityType = type,
                entityId = entityId,
                eventD = entityId, // entity UUID doubles as the opaque d tag
                recipientPubkeyHex = null, // encrypt-to-self
                payloadJson = payloadJson
            )
        )
        onOutboxEnqueued()
    }

    private val json = Json { encodeDefaults = true }

    // Payloads carry schema version "v" from day one (spec §5).
    private fun tripPayload(t: Trip) = json.encodeToString(
        JsonObject.serializer(),
        JsonObject(
            mapOf(
                "v" to JsonPrimitive(1), "type" to JsonPrimitive("trip"),
                "id" to JsonPrimitive(t.id), "name" to JsonPrimitive(t.name),
                "purpose" to JsonPrimitive(t.purpose), "city" to JsonPrimitive(t.city),
                "venue" to JsonPrimitive(t.venue), "startDate" to JsonPrimitive(t.startDate),
                "endDate" to JsonPrimitive(t.endDate), "status" to JsonPrimitive(t.status.name),
                "notes" to JsonPrimitive(t.notes), "rating" to JsonPrimitive(t.rating.name),
                "goAgain" to JsonPrimitive(t.goAgain), "updatedAt" to JsonPrimitive(t.updatedAt)
            )
        )
    )

    private fun componentPayload(c: TripComponent) = json.encodeToString(
        JsonObject.serializer(),
        JsonObject(
            mapOf(
                "v" to JsonPrimitive(1),
                "type" to JsonPrimitive("component"),
                "id" to JsonPrimitive(c.id),
                "tripId" to JsonPrimitive(c.tripId),
                "componentType" to JsonPrimitive(c.type.name),
                "title" to JsonPrimitive(c.title),
                "startEpochSec" to JsonPrimitive(c.startEpochSec),
                "startZoneId" to JsonPrimitive(c.startZoneId),
                "endEpochSec" to JsonPrimitive(c.endEpochSec),
                "endZoneId" to JsonPrimitive(c.endZoneId),
                "address" to JsonPrimitive(c.address),
                "confirmationRef" to JsonPrimitive(c.confirmationRef),
                "costCents" to JsonPrimitive(c.costCents),
                "booked" to JsonPrimitive(c.booked),
                "receiptObtained" to JsonPrimitive(c.receiptObtained),
                "receiptLocation" to JsonPrimitive(c.receiptLocation.name),
                "ticketRefType" to JsonPrimitive(c.ticketRefType.name),
                "ticketRefValue" to JsonPrimitive(c.ticketRefValue),
                "notes" to JsonPrimitive(c.notes),
                "updatedAt" to JsonPrimitive(c.updatedAt)
            )
        )
    )

    private fun prospectPayload(p: Prospect) = json.encodeToString(
        JsonObject.serializer(),
        JsonObject(
            mapOf(
                "v" to JsonPrimitive(1),
                "type" to JsonPrimitive("prospect"),
                "id" to JsonPrimitive(p.id),
                "name" to JsonPrimitive(p.name),
                "company" to JsonPrimitive(p.company),
                "role" to JsonPrimitive(p.role),
                "email" to JsonPrimitive(p.email),
                "phone" to JsonPrimitive(p.phone),
                "whereMet" to JsonPrimitive(p.whereMet),
                "socialX" to JsonPrimitive(p.socialX),
                "socialLinkedIn" to JsonPrimitive(p.socialLinkedIn),
                "socialTelegram" to JsonPrimitive(p.socialTelegram),
                "socialSignal" to JsonPrimitive(p.socialSignal),
                "socialNpub" to JsonPrimitive(p.socialNpub),
                "website" to JsonPrimitive(p.website),
                "updatedAt" to JsonPrimitive(p.updatedAt)
            )
        )
    )

    private fun notePayload(n: InteractionNote) = json.encodeToString(
        JsonObject.serializer(),
        JsonObject(
            mapOf(
                "v" to JsonPrimitive(1),
                "type" to JsonPrimitive("note"),
                "id" to JsonPrimitive(n.id),
                "prospectId" to JsonPrimitive(n.prospectId),
                "tripId" to JsonPrimitive(n.tripId),
                "occurredEpochSec" to JsonPrimitive(n.occurredEpochSec),
                "zoneId" to JsonPrimitive(n.zoneId),
                "content" to JsonPrimitive(n.content),
                "updatedAt" to JsonPrimitive(n.updatedAt)
            )
        )
    )

    private fun placePayload(p: Place) = json.encodeToString(
        JsonObject.serializer(),
        JsonObject(
            mapOf(
                "v" to JsonPrimitive(1), "type" to JsonPrimitive("place"),
                "id" to JsonPrimitive(p.id), "kind" to JsonPrimitive(p.kind.name),
                "name" to JsonPrimitive(p.name), "parentCity" to JsonPrimitive(p.parentCity),
                "rating" to JsonPrimitive(p.rating.name), "notes" to JsonPrimitive(p.notes),
                "updatedAt" to JsonPrimitive(p.updatedAt)
            )
        )
    )
}