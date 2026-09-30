package com.stays.hotel;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Hotel(
        UUID id,
        String name,
        String city,
        String district,
        String address,
        String country,
        String summary,
        String imagePath,
        String imageAlt,
        BigDecimal rating,
        List<RoomType> roomTypes) {

    public Hotel {
        Objects.requireNonNull(id, "Hotel id is required.");
        if (name == null || name.isBlank() || city == null || city.isBlank()) {
            throw new IllegalArgumentException("A hotel needs a name and city.");
        }
        if (rating == null || rating.signum() < 0 || rating.compareTo(BigDecimal.TEN) > 0) {
            throw new IllegalArgumentException("A hotel rating must be between zero and ten.");
        }
        roomTypes = List.copyOf(Objects.requireNonNull(roomTypes, "Room types are required."));
    }
}
