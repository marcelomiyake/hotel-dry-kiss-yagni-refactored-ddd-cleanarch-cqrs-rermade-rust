package com.stays.reservation;

import java.util.UUID;

public record CatalogRoomType(UUID id, UUID hotelId, String name, String details, int maxGuests, int totalInventory) {
}
