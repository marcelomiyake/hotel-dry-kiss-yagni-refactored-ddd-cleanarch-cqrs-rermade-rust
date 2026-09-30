package com.stays.payment.application.query;

import java.util.UUID;

import com.stays.common.ApiException;
import com.stays.payment.Payment;
import com.stays.payment.application.port.PaymentReadPort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public final class PaymentQueryHandler implements PaymentQueries {
    private final PaymentReadPort payments;

    public PaymentQueryHandler(PaymentReadPort payments) {
        this.payments = payments;
    }

    @Override
    public Payment findByReservation(UUID reservationId) {
        return payments.findByReservation(reservationId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "payment_not_found",
                        "Payment not found."));
    }
}
