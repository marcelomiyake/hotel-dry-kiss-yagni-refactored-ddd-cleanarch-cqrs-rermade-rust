package com.stays.payment;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

public record PaymentRequest(
        @NotNull UUID reservationId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull @Email String guestEmail) {
}
