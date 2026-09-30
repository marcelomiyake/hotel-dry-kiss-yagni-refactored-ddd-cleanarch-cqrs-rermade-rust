package com.stays.hotel.application.command;

import java.util.UUID;

import com.stays.hotel.Hotel;
import com.stays.hotel.HotelDraft;
import com.stays.hotel.RoomType;
import com.stays.hotel.RoomTypeDraft;
import com.stays.hotel.application.port.HotelWritePort;
import org.springframework.stereotype.Service;

@Service
public final class HotelCommandHandler implements HotelCommands {
    private final HotelWritePort hotels;

    public HotelCommandHandler(HotelWritePort hotels) {
        this.hotels = hotels;
    }

    @Override
    public Hotel createHotel(HotelDraft draft) {
        return hotels.createHotel(draft);
    }

    @Override
    public Hotel updateHotel(UUID id, HotelDraft draft) {
        return hotels.updateHotel(id, draft);
    }

    @Override
    public void removeHotel(UUID id) {
        hotels.removeHotel(id);
    }

    @Override
    public RoomType createRoomType(UUID hotelId, RoomTypeDraft draft) {
        return hotels.createRoomType(hotelId, draft);
    }

    @Override
    public RoomType updateRoomType(UUID id, RoomTypeDraft draft) {
        return hotels.updateRoomType(id, draft);
    }

    @Override
    public void removeRoomType(UUID id) {
        hotels.removeRoomType(id);
    }
}
