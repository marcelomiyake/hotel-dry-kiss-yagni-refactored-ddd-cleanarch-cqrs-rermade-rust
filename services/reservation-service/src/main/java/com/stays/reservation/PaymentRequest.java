package com.stays.reservation;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequest(UUID reservationId, BigDecimal amount, String guestEmail) {
}
