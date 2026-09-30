package com.stays.reservation;

import java.math.BigDecimal;
import java.util.UUID;

import com.stays.reservation.application.port.CatalogPort;
import com.stays.reservation.application.port.PaymentPort;
import com.stays.reservation.application.port.RateQuotePort;
import com.stays.reservation.domain.ReservationStatus;
import com.stays.common.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ReservationService {
    private final CatalogPort catalog;
    private final RateQuotePort rates;
    private final PaymentPort payments;
    private final ReservationTransactions transactions;

    public ReservationService(
            CatalogPort catalog,
            RateQuotePort rates,
            PaymentPort payments,
            ReservationTransactions transactions) {
        this.catalog = catalog;
        this.rates = rates;
        this.payments = payments;
        this.transactions = transactions;
    }

    public Reservation book(ReservationRequest request) {
        CatalogHotel hotel = catalog.hotel(request.hotelId());
        CatalogRoomType room = hotel.roomTypes().stream()
                .filter(candidate -> candidate.id().equals(request.roomTypeId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "room_type_not_found", "Room type not found."));
        RateQuote quote = rates.quote(room.id(), request.checkIn(), request.checkOut());
        BigDecimal total = quote.total().multiply(BigDecimal.valueOf(request.rooms()));
        Reservation reservation;
        try {
            reservation = transactions.createPending(request, hotel, room, total);
        } catch (DataIntegrityViolationException _) {
            reservation = transactions.find(request.reservationId());
        }
        if (reservation.status() == ReservationStatus.CONFIRMED) {
            return reservation;
        }
        if (reservation.status() != ReservationStatus.PAYMENT_PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "reservation_closed", "This reservation cannot be completed.");
        }
        Payment payment = payments.charge(new PaymentRequest(request.reservationId(), reservation.total(), request.guestEmail()));
        return transactions.markPaid(request.reservationId(), payment.id());
    }

    public Reservation cancel(UUID id) {
        Reservation current = transactions.find(id);
        if (current.status() == ReservationStatus.CANCELLED) {
            return current;
        }
        if (current.status() == ReservationStatus.CONFIRMED) {
            payments.refund(id);
        }
        return transactions.cancel(id);
    }
}
