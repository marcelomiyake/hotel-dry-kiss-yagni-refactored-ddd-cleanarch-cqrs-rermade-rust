package com.stays.reservation;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RateQuote(UUID roomTypeId, List<NightlyRate> nights, BigDecimal total) {
}
