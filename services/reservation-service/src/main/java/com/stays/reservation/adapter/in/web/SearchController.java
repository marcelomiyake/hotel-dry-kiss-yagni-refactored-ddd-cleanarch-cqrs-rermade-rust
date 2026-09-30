package com.stays.reservation.adapter.in.web;

import java.time.LocalDate;

import com.stays.reservation.SearchResponse;

import com.stays.reservation.application.query.ReservationQueries;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final ReservationQueries queries;

    public SearchController(ReservationQueries queries) {
        this.queries = queries;
    }

    @GetMapping
    public SearchResponse search(
            @RequestParam(name = "destination") String destination,
            @RequestParam(name = "checkIn") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(name = "checkOut") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(name = "guests", defaultValue = "2") int guests) {
        return queries.search(destination, checkIn, checkOut, guests);
    }
}
