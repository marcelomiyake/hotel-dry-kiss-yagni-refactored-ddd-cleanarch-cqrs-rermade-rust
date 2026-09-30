package com.stays.rate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record RateDraft(
        @NotNull UUID roomTypeId,
        @NotNull LocalDate date,
        @NotNull @DecimalMin("0.01") BigDecimal amount) {
}
