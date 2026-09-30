package com.stays.reservation.adapter.out.http;

import java.time.LocalDate;
import java.util.UUID;

import com.stays.reservation.RateQuote;

import com.stays.reservation.application.port.RateQuotePort;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RateClient implements RateQuotePort {
    private final RestClient client;

    public RateClient(RestClient.Builder builder, ServiceUrls urls) {
        this.client = builder.baseUrl(urls.rates()).build();
    }

    public RateQuote quote(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        return client.get()
                .uri(uri -> uri.path("/api/rates/quote")
                        .queryParam("roomTypeId", roomTypeId)
                        .queryParam("checkIn", checkIn)
                        .queryParam("checkOut", checkOut)
                        .build())
                .retrieve()
                .body(RateQuote.class);
    }
}
