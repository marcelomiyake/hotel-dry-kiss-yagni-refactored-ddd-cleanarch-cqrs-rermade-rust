package com.stays.reservation.application.query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.stays.reservation.Reservation;
import com.stays.reservation.ReservationTransactions;
import com.stays.reservation.SearchResponse;
import com.stays.reservation.SearchService;
import org.springframework.stereotype.Service;

@Service
public final class ReservationQueryHandler implements ReservationQueries {
    private final SearchService search;
    private final ReservationTransactions reservations;

    public ReservationQueryHandler(SearchService search, ReservationTransactions reservations) {
        this.search = search;
        this.reservations = reservations;
    }

    @Override
    public SearchResponse search(String destination, LocalDate checkIn, LocalDate checkOut, int guests) {
        return search.search(destination, checkIn, checkOut, guests);
    }

    @Override
    public List<Reservation> findByEmail(String email) {
        return reservations.findByEmail(email);
    }

    @Override
    public Reservation find(UUID id) {
        return reservations.find(id);
    }
}
