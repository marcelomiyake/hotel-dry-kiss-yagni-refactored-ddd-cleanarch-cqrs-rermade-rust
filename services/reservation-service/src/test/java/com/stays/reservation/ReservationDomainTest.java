package com.stays.reservation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.stays.reservation.domain.ReservationStatus;
import org.junit.jupiter.api.Test;

class ReservationDomainTest {
    private static final LocalDate CHECK_IN = LocalDate.parse("2026-06-10");
    private static final UUID ID = UUID.randomUUID();

    @Test
    void rejectsInvalidStayAndBookingValues() {
        LocalDate checkOut = CHECK_IN.plusDays(1);

        assertThatThrownBy(() -> reservation(CHECK_IN, CHECK_IN, 1, BigDecimal.TEN, ReservationStatus.PAYMENT_PENDING))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reservation(CHECK_IN, checkOut, 0, BigDecimal.TEN, ReservationStatus.PAYMENT_PENDING))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reservation(CHECK_IN, checkOut, 1, BigDecimal.ZERO, ReservationStatus.PAYMENT_PENDING))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reservation(CHECK_IN, checkOut, 1, BigDecimal.TEN, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Reservation reservation(
            LocalDate checkIn,
            LocalDate checkOut,
            int rooms,
            BigDecimal total,
            ReservationStatus status) {
        return new Reservation(
                ID, ID, ID, "Hotel", "Lisbon", "Center", "/hotel.webp", "Hotel", "Suite",
                checkIn, checkOut, rooms, 2, "Guest", "guest@example.com", total, status, null, Instant.now());
    }
}
