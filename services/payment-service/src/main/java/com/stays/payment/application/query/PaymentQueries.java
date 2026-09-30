package com.stays.payment.application.query;

import java.util.UUID;

import com.stays.payment.Payment;

public interface PaymentQueries {
    Payment findByReservation(UUID reservationId);
}
