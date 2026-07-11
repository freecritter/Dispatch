package com.freecritter.dispatch.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room entities double as domain models in v1 (single-layer, no mapping boilerplate).
 * Every timestamp is stored as epoch seconds + explicit IANA zone id (spec §4).
 * Display rule: event-local time; append "(TZ)" only when event TZ != device TZ.
 */

enum class TripStatus { UPCOMING, PAST, ARCHIVED }
enum class TripRating { UNRATED, BAD, FINE, GOOD, GREAT }
enum class ComponentType { FLIGHT, LODGING, EVENT_TICKETS, GROUND, OTHER }
enum class ReceiptLocation { NONE, IN_APP, PROTON_INBOX, EXPORTED_OFF_DEVICE, SUBMITTED }
enum class TicketRefType { NONE, LOCAL_FILE, URL, OPEN_APP }
enum class ShareTier { BASIC, FULL }
enum class PlaceKind { CITY, AIRPORT, RESTAURANT, HOTEL, VENUE }
enum class OutboxStatus { PENDING, IN_FLIGHT, FAILED }

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val purpose: String = "",
    val city: String = "",
    val venue: String = "",
    val startDate: String,
    val endDate: String,
    val status: TripStatus = TripStatus.UPCOMING,
    val notes: String = "",
    val rating: TripRating = TripRating.UNRATED,
    val goAgain: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis() / 1000
)

@Entity(tableName = "trip_components")
data class TripComponent(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val tripId: String,
    val type: ComponentType,
    val title: String,
    val startEpochSec: Long? = null,
    val startZoneId: String? = null,
    val endEpochSec: Long? = null,
    val endZoneId: String? = null,
    val address: String = "",
    val confirmationRef: String = "",
    val costCents: Long? = null,
    val booked: Boolean = false,
    val receiptObtained: Boolean = false,
    val receiptLocation: ReceiptLocation = ReceiptLocation.NONE,
    val ticketRefType: TicketRefType = TicketRefType.NONE,
    val ticketRefValue: String = "",
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis() / 1000
)

@Entity(tableName = "prospects")
data class Prospect(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val company: String = "",
    val role: String = "",
    val email: String = "",
    val phone: String = "",
    val whereMet: String = "",
    val socialX: String = "",
    val socialLinkedIn: String = "",
    val socialTelegram: String = "",
    val socialSignal: String = "",
    val socialNpub: String = "",
    val website: String = "",
    val updatedAt: Long = System.currentTimeMillis() / 1000
)

@Entity(tableName = "interaction_notes")
data class InteractionNote(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val prospectId: String,
    val tripId: String? = null,
    val occurredEpochSec: Long = System.currentTimeMillis() / 1000,
    val zoneId: String = java.util.TimeZone.getDefault().id,
    val content: String,
    val updatedAt: Long = System.currentTimeMillis() / 1000
)

@Entity(tableName = "places")
data class Place(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val kind: PlaceKind,
    val name: String,
    val parentCity: String = "",
    val rating: TripRating = TripRating.UNRATED,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis() / 1000
)

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey val npub: String,
    val displayName: String = "",
    val avatarUrl: String = "",
    val approvedSender: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis() / 1000
)

@Entity(tableName = "share_grants")
data class ShareGrant(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val tripId: String,
    val recipientNpub: String,
    val tier: ShareTier,
    val shareEventD: String = UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis() / 1000
)

@Entity(tableName = "receipt_photos")
data class ReceiptPhoto(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val componentId: String,
    val filePath: String,
    val createdAt: Long = System.currentTimeMillis() / 1000
)

@Entity(tableName = "outbox")
data class OutboxItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entityType: String,
    val entityId: String,
    val eventD: String,
    val recipientPubkeyHex: String? = null,
    val payloadJson: String,
    val status: OutboxStatus = OutboxStatus.PENDING,
    val attempts: Int = 0,
    val createdAt: Long = System.currentTimeMillis() / 1000
)
