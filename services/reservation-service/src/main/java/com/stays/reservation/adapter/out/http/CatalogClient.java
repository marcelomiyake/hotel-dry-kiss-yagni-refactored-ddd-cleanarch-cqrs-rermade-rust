package com.stays.reservation.adapter.out.http;

import java.util.List;
import java.util.UUID;

import com.stays.reservation.CatalogHotel;

import com.stays.reservation.application.port.CatalogPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CatalogClient implements CatalogPort {
    private final RestClient client;

    public CatalogClient(RestClient.Builder builder, ServiceUrls urls) {
        this.client = builder.baseUrl(urls.catalog()).build();
    }

    public List<CatalogHotel> hotels(String destination) {
        return client.get()
                .uri(uri -> uri.path("/api/hotels").queryParam("destination", destination).build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });
    }

    public CatalogHotel hotel(UUID id) {
        return client.get().uri("/api/hotels/{id}", id).retrieve().body(CatalogHotel.class);
    }
}
