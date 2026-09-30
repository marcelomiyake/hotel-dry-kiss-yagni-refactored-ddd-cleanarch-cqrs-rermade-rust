package com.stays.rate.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record RatePeriod(LocalDate checkIn, LocalDate checkOut) {
    private static final long MAX_NIGHTS = 365;

    public RatePeriod {
        if (checkIn == null || checkOut == null) {
            throw new IllegalArgumentException("Check-in and check-out dates are required.");
        }
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights < 1 || nights > MAX_NIGHTS) {
            throw new IllegalArgumentException("A rate period must contain between one and 365 nights.");
        }
    }

    public long nights() {
        return ChronoUnit.DAYS.between(checkIn, checkOut);
    }
}
