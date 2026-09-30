package com.stays.hotel.application.query;

import java.util.List;
import java.util.UUID;

import com.stays.hotel.Hotel;

public interface HotelQueries {
    List<Hotel> findHotels(String destination);

    Hotel findHotel(UUID id);
}
