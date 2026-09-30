package com.stays.reservation.application.port;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.stays.reservation.CatalogHotel;
import com.stays.reservation.CatalogRoomType;
import com.stays.reservation.Reservation;
import com.stays.reservation.ReservationRequest;

public interface ReservationStore {
    Optional<Reservation> find(UUID id);

    List<Reservation> findByEmail(String email);

    Reservation create(ReservationRequest request, CatalogHotel hotel, CatalogRoomType room, BigDecimal total);

    Reservation setPaid(UUID id, UUID paymentId);

    Reservation cancel(UUID id);

    Reservation require(UUID id);
}
