package com.stays.reservation.application.port;

import java.util.UUID;

import com.stays.reservation.Payment;
import com.stays.reservation.PaymentRequest;

public interface PaymentPort {
    Payment charge(PaymentRequest request);

    Payment refund(UUID reservationId);
}
