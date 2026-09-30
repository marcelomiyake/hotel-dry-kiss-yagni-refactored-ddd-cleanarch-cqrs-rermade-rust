package com.stays.payment.application.command;

import java.util.UUID;

import com.stays.payment.Payment;
import com.stays.payment.PaymentRequest;

public interface PaymentCommands {
    Payment charge(PaymentRequest request);

    Payment refund(UUID reservationId);
}
