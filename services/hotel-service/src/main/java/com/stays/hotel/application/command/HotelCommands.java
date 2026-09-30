package com.stays.hotel.application.command;

import java.util.UUID;

import com.stays.hotel.Hotel;
import com.stays.hotel.HotelDraft;
import com.stays.hotel.RoomType;
import com.stays.hotel.RoomTypeDraft;

public interface HotelCommands {
    Hotel createHotel(HotelDraft draft);

    Hotel updateHotel(UUID id, HotelDraft draft);

    void removeHotel(UUID id);

    RoomType createRoomType(UUID hotelId, RoomTypeDraft draft);

    RoomType updateRoomType(UUID id, RoomTypeDraft draft);

    void removeRoomType(UUID id);
}
