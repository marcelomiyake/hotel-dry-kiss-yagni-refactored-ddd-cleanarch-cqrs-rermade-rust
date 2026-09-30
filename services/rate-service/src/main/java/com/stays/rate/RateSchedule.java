package com.stays.rate;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record RateSchedule(
        @NotNull UUID roomTypeId,
        @NotNull @DecimalMin("0.01") BigDecimal baseRate) {
}
