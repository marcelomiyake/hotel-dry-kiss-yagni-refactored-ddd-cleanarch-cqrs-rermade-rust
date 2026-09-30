package com.stays.reservation;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SearchStay(
        UUID id,
        String name,
        String city,
        String district,
        String address,
        String summary,
        String imagePath,
        String imageAlt,
        BigDecimal rating,
        List<RoomOffer> rooms) {
}
