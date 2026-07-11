package com.freecritter.dispatch.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun tripStatusToString(v: TripStatus) = v.name
    @TypeConverter fun stringToTripStatus(v: String) = TripStatus.valueOf(v)
    @TypeConverter fun tripRatingToString(v: TripRating) = v.name
    @TypeConverter fun stringToTripRating(v: String) = TripRating.valueOf(v)
    @TypeConverter fun componentTypeToString(v: ComponentType) = v.name
    @TypeConverter fun stringToComponentType(v: String) = ComponentType.valueOf(v)
    @TypeConverter fun receiptLocationToString(v: ReceiptLocation) = v.name
    @TypeConverter fun stringToReceiptLocation(v: String) = ReceiptLocation.valueOf(v)
    @TypeConverter fun ticketRefTypeToString(v: TicketRefType) = v.name
    @TypeConverter fun stringToTicketRefType(v: String) = TicketRefType.valueOf(v)
    @TypeConverter fun shareTierToString(v: ShareTier) = v.name
    @TypeConverter fun stringToShareTier(v: String) = ShareTier.valueOf(v)
    @TypeConverter fun placeKindToString(v: PlaceKind) = v.name
    @TypeConverter fun stringToPlaceKind(v: String) = PlaceKind.valueOf(v)
    @TypeConverter fun outboxStatusToString(v: OutboxStatus) = v.name
    @TypeConverter fun stringToOutboxStatus(v: String) = OutboxStatus.valueOf(v)
}
