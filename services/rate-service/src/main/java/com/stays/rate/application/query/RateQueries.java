package com.stays.rate.application.query;

import java.time.LocalDate;
import java.util.UUID;

import com.stays.rate.RateQuote;

public interface RateQueries {
    RateQuote quote(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut);
}
