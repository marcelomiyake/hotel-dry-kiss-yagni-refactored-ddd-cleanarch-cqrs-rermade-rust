package com.stays.hotel;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class HotelDomainTest {
    private static final UUID HOTEL_ID = UUID.randomUUID();

    @Test
    void rejectsRoomTypesWithoutCapacityOrInventory() {
        UUID roomTypeWithoutCapacity = UUID.randomUUID();
        UUID roomTypeWithoutInventory = UUID.randomUUID();

        assertThatThrownBy(() -> new RoomType(roomTypeWithoutCapacity, HOTEL_ID, "Suite", "", 0, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RoomType(roomTypeWithoutInventory, HOTEL_ID, "Suite", "", 2, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsHotelsWithInvalidIdentityOrRating() {
        BigDecimal ratingAboveMaximum = new BigDecimal("10.1");
        BigDecimal negativeRating = new BigDecimal("-0.1");

        assertThatThrownBy(() -> hotel(" ", BigDecimal.TEN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hotel("Casa", ratingAboveMaximum)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hotel("Casa", negativeRating)).isInstanceOf(IllegalArgumentException.class);
    }

    private Hotel hotel(String name, BigDecimal rating) {
        return new Hotel(HOTEL_ID, name, "Lisbon", "Center", "Rua 1", "Portugal", "A hotel.",
                "/hotel.webp", "Hotel", rating, List.of());
    }
}
