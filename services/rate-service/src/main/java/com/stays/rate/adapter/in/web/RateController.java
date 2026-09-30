package com.stays.rate.adapter.in.web;

import java.time.LocalDate;
import java.util.UUID;

import com.stays.rate.NightlyRate;
import com.stays.rate.RateDraft;
import com.stays.rate.RateQuote;
import com.stays.rate.RateSchedule;

import com.stays.rate.application.command.RateCommands;
import com.stays.rate.application.query.RateQueries;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RateController {
    private final RateQueries rateQueries;
    private final RateCommands rateCommands;

    public RateController(RateQueries rateQueries, RateCommands rateCommands) {
        this.rateQueries = rateQueries;
        this.rateCommands = rateCommands;
    }

    @GetMapping("/rates/quote")
    public RateQuote quote(
            @RequestParam(name = "roomTypeId") UUID roomTypeId,
            @RequestParam(name = "checkIn") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(name = "checkOut") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut) {
        return rateQueries.quote(roomTypeId, checkIn, checkOut);
    }

    @PutMapping("/admin/rates")
    public NightlyRate setRate(@Valid @RequestBody RateDraft draft) {
        return rateCommands.setRate(draft);
    }

    @PostMapping("/admin/rates/schedule")
    public ResponseEntity<Void> createSchedule(@Valid @RequestBody RateSchedule schedule) {
        rateCommands.createSchedule(schedule);
        return ResponseEntity.noContent().build();
    }
}
