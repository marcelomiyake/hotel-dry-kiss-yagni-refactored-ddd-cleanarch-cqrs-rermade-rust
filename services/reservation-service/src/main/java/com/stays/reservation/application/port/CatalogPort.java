package com.stays.reservation.application.port;

import java.util.List;
import java.util.UUID;

import com.stays.reservation.CatalogHotel;

public interface CatalogPort {
    List<CatalogHotel> hotels(String destination);

    CatalogHotel hotel(UUID id);
}
