package com.stays.reservation.adapter.out.http;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ServiceUrls {
    private final String catalog;
    private final String rates;
    private final String payments;

    public ServiceUrls(
            @Value("${app.catalog-url:http://localhost:8081}") String catalog,
            @Value("${app.rate-url:http://localhost:8082}") String rates,
            @Value("${app.payment-url:http://localhost:8084}") String payments) {
        this.catalog = catalog;
        this.rates = rates;
        this.payments = payments;
    }

    public String catalog() {
        return catalog;
    }

    public String rates() {
        return rates;
    }

    public String payments() {
        return payments;
    }
}
