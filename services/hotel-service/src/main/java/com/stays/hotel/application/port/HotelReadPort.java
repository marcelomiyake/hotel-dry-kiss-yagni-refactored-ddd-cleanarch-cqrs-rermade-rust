package com.stays.hotel.application.port;

import java.util.List;
import java.util.UUID;

import com.stays.hotel.Hotel;

/** Read-side persistence contract owned by the hotel application. */
public interface HotelReadPort {
    List<Hotel> findHotels(String destination);

    Hotel findHotel(UUID id);
}
