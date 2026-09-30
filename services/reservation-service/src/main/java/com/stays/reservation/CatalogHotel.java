package com.stays.reservation;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CatalogHotel(
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
        List<CatalogRoomType> roomTypes) {
}
