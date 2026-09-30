package com.stays.payment.application.port;

import java.util.UUID;

import com.stays.payment.PaymentRequest;

public interface PaymentWritePort {
    void recordCharge(PaymentRequest request);

    void recordRefund(UUID reservationId);
}
