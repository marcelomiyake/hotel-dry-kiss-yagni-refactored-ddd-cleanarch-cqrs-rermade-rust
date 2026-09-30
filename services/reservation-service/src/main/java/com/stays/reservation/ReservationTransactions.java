package com.stays.reservation;

import java.math.BigDecimal;
import java.util.UUID;

import com.stays.reservation.application.port.InventoryPort;
import com.stays.reservation.application.port.ReservationStore;
import com.stays.reservation.domain.ReservationStatus;
import com.stays.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationTransactions {
    private final ReservationStore reservations;
    private final InventoryPort inventory;

    public ReservationTransactions(ReservationStore reservations, InventoryPort inventory) {
        this.reservations = reservations;
        this.inventory = inventory;
    }

    @Transactional
    public Reservation createPending(
            ReservationRequest request,
            CatalogHotel hotel,
            CatalogRoomType room,
            BigDecimal total) {
        Reservation existing = reservations.find(request.reservationId()).orElse(null);
        if (existing != null) {
            requireSameRequest(existing, request);
            return existing;
        }
        inventory.reserve(request.hotelId(), request.roomTypeId(), request.checkIn(), request.checkOut(), request.rooms());
        return reservations.create(request, hotel, room, total);
    }

    @Transactional
    public Reservation markPaid(UUID reservationId, UUID paymentId) {
        return reservations.setPaid(reservationId, paymentId);
    }

    @Transactional
    public Reservation cancel(UUID reservationId) {
        Reservation reservation = reservations.require(reservationId);
        if (reservation.status() == ReservationStatus.CANCELLED) {
            return reservation;
        }
        inventory.release(
                reservation.hotelId(), reservation.roomTypeId(),
                reservation.checkIn(), reservation.checkOut(), reservation.rooms());
        return reservations.cancel(reservationId);
    }

    @Transactional(readOnly = true)
    public Reservation find(UUID reservationId) {
        return reservations.require(reservationId);
    }

    @Transactional(readOnly = true)
    public java.util.List<Reservation> findByEmail(String email) {
        return reservations.findByEmail(email);
    }

    private void requireSameRequest(Reservation existing, ReservationRequest request) {
        boolean same = existing.hotelId().equals(request.hotelId())
                && existing.roomTypeId().equals(request.roomTypeId())
                && existing.checkIn().equals(request.checkIn())
                && existing.checkOut().equals(request.checkOut())
                && existing.rooms() == request.rooms()
                && existing.guests() == request.guests()
                && existing.guestName().equals(request.guestName().trim())
                && existing.guestEmail().equalsIgnoreCase(request.guestEmail().trim());
        if (!same) {
            throw new ApiException(HttpStatus.CONFLICT, "idempotency_key_reused", "Use a new reservation ID for different booking details.");
        }
    }
}
