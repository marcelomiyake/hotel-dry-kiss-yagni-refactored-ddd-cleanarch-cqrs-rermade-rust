package com.stays.reservation;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReservationRequest(
        @NotNull UUID reservationId,
        @NotNull UUID hotelId,
        @NotNull UUID roomTypeId,
        @NotNull LocalDate checkIn,
        @NotNull LocalDate checkOut,
        @Min(1) int rooms,
        @Min(1) int guests,
        @NotBlank String guestName,
        @NotBlank @Email String guestEmail) {
}
