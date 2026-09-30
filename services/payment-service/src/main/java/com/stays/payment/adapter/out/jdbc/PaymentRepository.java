package com.stays.payment.adapter.out.jdbc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.stays.payment.Payment;
import com.stays.payment.PaymentRequest;

import com.stays.payment.application.port.PaymentReadPort;
import com.stays.payment.application.port.PaymentWritePort;
import com.stays.payment.domain.PaymentStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepository implements PaymentReadPort, PaymentWritePort {
    private final JdbcTemplate jdbc;

    public PaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void recordCharge(PaymentRequest request) {
        jdbc.update(
                "INSERT INTO payments.transactions (reservation_id, amount, guest_email, status) "
                        + "VALUES (?, ?, ?, 'PAID') ON CONFLICT (reservation_id) DO NOTHING",
                request.reservationId(), request.amount(), request.guestEmail().trim().toLowerCase());
    }

    public void recordRefund(UUID reservationId) {
        jdbc.update(
                "UPDATE payments.transactions SET status = 'REFUNDED' "
                        + "WHERE reservation_id = ? AND status = 'PAID'",
                reservationId);
    }

    @Override
    public Optional<Payment> findByReservation(UUID reservationId) {
        List<Payment> payments = jdbc.query(
                "SELECT id, reservation_id, amount, status, created_at FROM payments.transactions WHERE reservation_id = ?",
                (result, row) -> new Payment(
                        result.getObject("id", UUID.class),
                        result.getObject("reservation_id", UUID.class),
                        result.getBigDecimal("amount"),
                        PaymentStatus.valueOf(result.getString("status")),
                        result.getTimestamp("created_at").toInstant()),
                reservationId);
        return payments.stream().findFirst();
    }
}
