package com.stays.payment.application.command;

import java.util.UUID;

import com.stays.common.ApiException;
import com.stays.payment.Payment;
import com.stays.payment.PaymentRequest;
import com.stays.payment.application.port.PaymentWritePort;
import com.stays.payment.application.query.PaymentQueries;
import com.stays.payment.domain.PaymentStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public final class PaymentCommandHandler implements PaymentCommands {
    private final PaymentWritePort paymentWrites;
    private final PaymentQueries paymentQueries;

    public PaymentCommandHandler(PaymentWritePort paymentWrites, PaymentQueries paymentQueries) {
        this.paymentWrites = paymentWrites;
        this.paymentQueries = paymentQueries;
    }

    @Override
    public Payment charge(PaymentRequest request) {
        paymentWrites.recordCharge(request);
        Payment payment = paymentQueries.findByReservation(request.reservationId());
        if (payment.amount().compareTo(request.amount()) != 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "payment_conflict",
                    "The reservation already has a different payment amount.");
        }
        return payment;
    }

    @Override
    public Payment refund(UUID reservationId) {
        paymentWrites.recordRefund(reservationId);
        Payment payment = paymentQueries.findByReservation(reservationId);
        if (payment.status() != PaymentStatus.REFUNDED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "payment_not_refundable",
                    "The payment cannot be refunded.");
        }
        return payment;
    }
}
