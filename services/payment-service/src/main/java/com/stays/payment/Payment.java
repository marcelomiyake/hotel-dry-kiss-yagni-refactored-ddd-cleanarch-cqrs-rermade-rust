package com.stays.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.stays.payment.domain.PaymentStatus;

public record Payment(UUID id, UUID reservationId, BigDecimal amount, PaymentStatus status, Instant createdAt) {
}
