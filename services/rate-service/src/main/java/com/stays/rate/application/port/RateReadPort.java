package com.stays.rate.application.port;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.stays.rate.NightlyRate;

/** Persistence boundary for rate lookups. */
public interface RateReadPort {
    List<NightlyRate> findRates(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut);
}
