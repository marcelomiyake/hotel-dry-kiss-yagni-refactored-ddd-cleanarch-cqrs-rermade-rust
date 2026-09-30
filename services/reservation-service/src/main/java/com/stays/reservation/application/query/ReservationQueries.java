package com.stays.reservation.application.query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.stays.reservation.Reservation;
import com.stays.reservation.SearchResponse;

public interface ReservationQueries {
    SearchResponse search(String destination, LocalDate checkIn, LocalDate checkOut, int guests);

    List<Reservation> findByEmail(String email);

    Reservation find(UUID id);
}
