package com.stays.reservation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.stays.reservation.domain.ReservationStatus;

public record Reservation(
        UUID id,
        UUID hotelId,
        UUID roomTypeId,
        String hotelName,
        String city,
        String district,
        String imagePath,
        String imageAlt,
        String roomTypeName,
        LocalDate checkIn,
        LocalDate checkOut,
        int rooms,
        int guests,
        String guestName,
        String guestEmail,
        BigDecimal total,
        ReservationStatus status,
        UUID paymentId,
        Instant createdAt) {

    public Reservation {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("A reservation must have a positive stay period.");
        }
        if (rooms < 1 || guests < 1 || total == null || total.signum() <= 0) {
            throw new IllegalArgumentException("A reservation must include rooms, guests, and a positive total.");
        }
        if (status == null) {
            throw new IllegalArgumentException("A reservation must have a status.");
        }
    }
}
