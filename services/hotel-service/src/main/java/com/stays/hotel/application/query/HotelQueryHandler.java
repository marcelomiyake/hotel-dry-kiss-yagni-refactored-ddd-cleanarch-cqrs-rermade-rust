package com.stays.hotel.application.query;

import java.util.List;
import java.util.UUID;

import com.stays.hotel.Hotel;
import com.stays.hotel.application.port.HotelReadPort;
import org.springframework.stereotype.Service;

@Service
public final class HotelQueryHandler implements HotelQueries {
    private final HotelReadPort hotels;

    public HotelQueryHandler(HotelReadPort hotels) {
        this.hotels = hotels;
    }

    @Override
    public List<Hotel> findHotels(String destination) {
        return hotels.findHotels(destination);
    }

    @Override
    public Hotel findHotel(UUID id) {
        return hotels.findHotel(id);
    }
}
