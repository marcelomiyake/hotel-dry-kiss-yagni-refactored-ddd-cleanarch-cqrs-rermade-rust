package com.stays.reservation.adapter.out.http;

import java.util.UUID;

import com.stays.reservation.Payment;
import com.stays.reservation.PaymentRequest;

import com.stays.reservation.application.port.PaymentPort;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PaymentClient implements PaymentPort {
    private final RestClient client;

    public PaymentClient(RestClient.Builder builder, ServiceUrls urls) {
        this.client = builder.baseUrl(urls.payments()).build();
    }

    public Payment charge(PaymentRequest request) {
        return client.post().uri("/api/payments").body(request).retrieve().body(Payment.class);
    }

    public Payment refund(UUID reservationId) {
        return client.put().uri("/api/payments/{reservationId}/refund", reservationId)
                .retrieve().body(Payment.class);
    }
}
