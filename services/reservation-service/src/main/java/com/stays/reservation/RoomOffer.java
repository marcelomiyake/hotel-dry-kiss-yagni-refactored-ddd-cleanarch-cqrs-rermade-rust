package com.stays.reservation;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RoomOffer(
        UUID id,
        String name,
        String details,
        int maxGuests,
        int availableRooms,
        List<NightlyRate> nightlyRates,
        BigDecimal totalPrice) {
}
