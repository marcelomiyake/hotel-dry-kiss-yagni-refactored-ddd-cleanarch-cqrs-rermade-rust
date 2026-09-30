package com.stays.hotel;

import java.util.UUID;
import java.util.Objects;

public record RoomType(
        UUID id,
        UUID hotelId,
        String name,
        String details,
        int maxGuests,
        int totalInventory) {

    public RoomType {
        Objects.requireNonNull(id, "Room type id is required.");
        Objects.requireNonNull(hotelId, "A room type must belong to a hotel.");
        if (name == null || name.isBlank() || maxGuests < 1 || totalInventory < 1) {
            throw new IllegalArgumentException("A room type needs a name, guest capacity, and inventory.");
        }
    }
}
