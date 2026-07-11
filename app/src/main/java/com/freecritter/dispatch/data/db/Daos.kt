package com.freecritter.dispatch.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips WHERE status != 'ARCHIVED' ORDER BY startDate ASC")
    fun observeActive(): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE id = :id")
    fun observeById(id: String): Flow<Trip?>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getById(id: String): Trip?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(trip: Trip)

    @Delete
    suspend fun delete(trip: Trip)
}

@Dao
interface TripComponentDao {
    @Query("SELECT * FROM trip_components WHERE tripId = :tripId ORDER BY startEpochSec IS NULL, startEpochSec ASC, id ASC")
    fun observeForTrip(tripId: String): Flow<List<TripComponent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(component: TripComponent)

    @Delete
    suspend fun delete(component: TripComponent)

    @Query("SELECT * FROM trip_components WHERE id = :id")
    suspend fun getById(id: String): TripComponent?

    @Query("UPDATE trip_components SET booked = :booked, updatedAt = :now WHERE id = :id")
    suspend fun updateBooked(id: String, booked: Boolean, now: Long)

    @Query("UPDATE trip_components SET receiptObtained = :obtained, updatedAt = :now WHERE id = :id")
    suspend fun updateReceipt(id: String, obtained: Boolean, now: Long)
}

@Dao
interface ProspectDao {
    @Query("SELECT * FROM prospects ORDER BY name ASC")
    fun observeAll(): Flow<List<Prospect>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(prospect: Prospect)

    @Delete
    suspend fun delete(prospect: Prospect)
}

@Dao
interface InteractionNoteDao {
    @Query("SELECT * FROM interaction_notes WHERE prospectId = :prospectId ORDER BY occurredEpochSec DESC")
    fun observeForProspect(prospectId: String): Flow<List<InteractionNote>>

    @Query("SELECT * FROM interaction_notes WHERE tripId = :tripId ORDER BY occurredEpochSec DESC")
    fun observeForTrip(tripId: String): Flow<List<InteractionNote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: InteractionNote)

    @Delete
    suspend fun delete(note: InteractionNote)
}

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY name ASC")
    fun observeAll(): Flow<List<Place>>

    @Query("SELECT * FROM places WHERE parentCity = :city OR (kind = 'CITY' AND name = :city)")
    suspend fun forCity(city: String): List<Place>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(place: Place)

    @Delete
    suspend fun delete(place: Place)
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY displayName ASC")
    fun observeAll(): Flow<List<Contact>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: Contact)
}

@Dao
interface ShareGrantDao {
    @Query("SELECT * FROM share_grants WHERE tripId = :tripId")
    fun observeForTrip(tripId: String): Flow<List<ShareGrant>>

    @Query("SELECT * FROM share_grants WHERE tripId = :tripId")
    suspend fun forTrip(tripId: String): List<ShareGrant>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(grant: ShareGrant)

    @Delete
    suspend fun delete(grant: ShareGrant)
}

@Dao
interface ReceiptPhotoDao {
    @Query("SELECT * FROM receipt_photos WHERE componentId = :componentId")
    fun observeForComponent(componentId: String): Flow<List<ReceiptPhoto>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(photo: ReceiptPhoto)

    @Delete
    suspend fun delete(photo: ReceiptPhoto)
}

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox WHERE status = 'PENDING' ORDER BY createdAt ASC LIMIT :limit")
    suspend fun nextPending(limit: Int = 20): List<OutboxItem>

    @Query("SELECT COUNT(*) FROM outbox WHERE status != 'FAILED'")
    fun observePendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: OutboxItem)

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun deleteById(id: String)
}
