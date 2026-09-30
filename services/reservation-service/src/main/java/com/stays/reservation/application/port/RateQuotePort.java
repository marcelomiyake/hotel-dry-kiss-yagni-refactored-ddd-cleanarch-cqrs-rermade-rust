package com.stays.reservation.application.port;

import java.time.LocalDate;
import java.util.UUID;

import com.stays.reservation.RateQuote;

public interface RateQuotePort {
    RateQuote quote(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut);
}
