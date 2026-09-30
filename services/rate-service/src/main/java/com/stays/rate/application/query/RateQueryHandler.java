package com.stays.rate.application.query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.stays.common.ApiException;
import com.stays.rate.NightlyRate;
import com.stays.rate.RateQuote;
import com.stays.rate.application.port.RateReadPort;
import com.stays.rate.domain.RatePeriod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public final class RateQueryHandler implements RateQueries {
    private final RateReadPort rates;

    public RateQueryHandler(RateReadPort rates) {
        this.rates = rates;
    }

    @Override
    public RateQuote quote(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        RatePeriod period = ratePeriod(checkIn, checkOut);
        List<NightlyRate> nightlyRates = rates.findRates(roomTypeId, period.checkIn(), period.checkOut());
        if (nightlyRates.size() != period.nights()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "rate_not_found", "No rate is available for every night.");
        }
        BigDecimal total = nightlyRates.stream()
                .map(NightlyRate::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RateQuote(roomTypeId, nightlyRates, total);
    }

    private RatePeriod ratePeriod(LocalDate checkIn, LocalDate checkOut) {
        try {
            return new RatePeriod(checkIn, checkOut);
        } catch (IllegalArgumentException _) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "invalid_dates",
                    "Choose a stay between 1 and 365 nights.");
        }
    }
}
