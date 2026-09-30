package com.stays.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import com.stays.common.ApiException;
import com.stays.payment.application.command.PaymentCommands;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = PaymentApplication.class)
@Testcontainers
class PaymentRepositoryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("hotel")
            .withUsername("hotel_app")
            .withPassword("hotel_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private PaymentCommands payments;

    @Test
    void chargesIdempotentlyAndRefundsOnce() {
        UUID reservationId = UUID.randomUUID();
        PaymentRequest request = new PaymentRequest(reservationId, new BigDecimal("250.00"), " GUEST@EXAMPLE.COM ");
        Payment first = payments.charge(request);
        Payment retry = payments.charge(new PaymentRequest(reservationId, new BigDecimal("250.00"), "guest@example.com"));
        assertThat(retry.id()).isEqualTo(first.id());
        assertThat(retry.status()).hasToString("PAID");

        assertThat(payments.refund(reservationId).status()).hasToString("REFUNDED");
        assertThat(payments.refund(reservationId).status()).hasToString("REFUNDED");
    }

    @Test
    void rejectsDifferentAmountsAndMissingPayments() {
        UUID reservationId = UUID.randomUUID();
        payments.charge(new PaymentRequest(reservationId, new BigDecimal("250.00"), "guest@example.com"));
        PaymentRequest differentAmount = new PaymentRequest(reservationId, new BigDecimal("251.00"), "guest@example.com");
        UUID unknownReservationId = UUID.randomUUID();
        assertThatThrownBy(() -> payments.charge(differentAmount))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> payments.refund(unknownReservationId)).isInstanceOf(ApiException.class);
    }
}
