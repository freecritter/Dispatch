package com.freecritter.dispatch.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Trip::class, TripComponent::class, Prospect::class, InteractionNote::class,
        Place::class, Contact::class, ShareGrant::class, ReceiptPhoto::class, OutboxItem::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class DispatchDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun tripComponentDao(): TripComponentDao
    abstract fun prospectDao(): ProspectDao
    abstract fun interactionNoteDao(): InteractionNoteDao
    abstract fun placeDao(): PlaceDao
    abstract fun contactDao(): ContactDao
    abstract fun shareGrantDao(): ShareGrantDao
    abstract fun receiptPhotoDao(): ReceiptPhotoDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        @Volatile private var instance: DispatchDatabase? = null

        fun get(context: Context): DispatchDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DispatchDatabase::class.java,
                    "dispatch.db"
                ).build().also { instance = it }
            }
    }
}
