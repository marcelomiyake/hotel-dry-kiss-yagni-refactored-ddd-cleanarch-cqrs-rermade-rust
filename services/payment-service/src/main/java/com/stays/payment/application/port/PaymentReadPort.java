package com.stays.payment.application.port;

import java.util.Optional;
import java.util.UUID;

import com.stays.payment.Payment;

public interface PaymentReadPort {
    Optional<Payment> findByReservation(UUID reservationId);
}
