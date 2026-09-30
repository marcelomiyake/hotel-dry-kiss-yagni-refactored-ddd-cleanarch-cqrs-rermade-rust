package com.stays.reservation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Payment(UUID id, UUID reservationId, BigDecimal amount, String status, Instant createdAt) {
}
