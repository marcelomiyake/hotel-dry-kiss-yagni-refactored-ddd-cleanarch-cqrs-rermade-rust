package com.stays.hotel.adapter.out.jdbc;

import java.util.List;
import java.util.UUID;

import com.stays.hotel.Hotel;
import com.stays.hotel.HotelDraft;
import com.stays.hotel.RoomType;
import com.stays.hotel.RoomTypeDraft;

import com.stays.hotel.application.port.HotelReadPort;
import com.stays.hotel.application.port.HotelWritePort;
import com.stays.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class HotelRepository implements HotelReadPort, HotelWritePort {
    private static final String HOTEL_NOT_FOUND_CODE = "hotel_not_found";
    private static final String HOTEL_NOT_FOUND_MESSAGE = "Hotel not found.";
    private static final String ROOM_TYPE_NOT_FOUND_CODE = "room_type_not_found";
    private static final String ROOM_TYPE_NOT_FOUND_MESSAGE = "Room type not found.";
    private static final String ACTIVE_BY_ID = "WHERE id = ? AND active = true";
    private static final String HOTEL_COLUMNS = "id, name, city, district, address, country, summary, image_path, image_alt, rating";
    private static final String ROOM_COLUMNS = "id, hotel_id, name, details, max_guests, total_inventory";
    private static final String HOTEL_SELECT = "SELECT " + HOTEL_COLUMNS + " FROM hotel_catalog.hotels ";
    private static final String ROOM_TYPE_SELECT = "SELECT " + ROOM_COLUMNS + " FROM hotel_catalog.room_types ";
    private static final RowMapper<Hotel> HOTEL_MAPPER = (result, row) -> new Hotel(
            result.getObject("id", UUID.class),
            result.getString("name"),
            result.getString("city"),
            result.getString("district"),
            result.getString("address"),
            result.getString("country"),
            result.getString("summary"),
            result.getString("image_path"),
            result.getString("image_alt"),
            result.getBigDecimal("rating"),
            List.of());
    private static final RowMapper<RoomType> ROOM_MAPPER = (result, row) -> new RoomType(
            result.getObject("id", UUID.class),
            result.getObject("hotel_id", UUID.class),
            result.getString("name"),
            result.getString("details"),
            result.getInt("max_guests"),
            result.getInt("total_inventory"));

    private final JdbcTemplate jdbc;

    public HotelRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Hotel> findHotels(String destination) {
        String filter = "%" + (destination == null ? "" : destination.trim()) + "%";
        List<Hotel> hotels = jdbc.query(
                HOTEL_SELECT
                        + "WHERE active = true AND (lower(name) LIKE lower(?) OR lower(city) LIKE lower(?)) "
                        + "ORDER BY name",
                HOTEL_MAPPER,
                filter,
                filter);
        return hotels.stream().map(this::withRoomTypes).toList();
    }

    public Hotel findHotel(UUID id) {
        List<Hotel> hotels = jdbc.query(
                HOTEL_SELECT + ACTIVE_BY_ID,
                HOTEL_MAPPER,
                id);
        if (hotels.isEmpty()) {
            throw notFound(HOTEL_NOT_FOUND_CODE, HOTEL_NOT_FOUND_MESSAGE);
        }
        return withRoomTypes(hotels.getFirst());
    }

    public Hotel createHotel(HotelDraft draft) {
        UUID id = jdbc.queryForObject(
                "INSERT INTO hotel_catalog.hotels "
                        + "(name, city, district, address, country, summary, image_path, image_alt, rating) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                UUID.class,
                draft.name().trim(), draft.city().trim(), draft.district().trim(), draft.address().trim(),
                draft.country().trim(), draft.summary().trim(), draft.imagePath().trim(), draft.imageAlt().trim(),
                draft.rating());
        return findHotel(id);
    }

    public Hotel updateHotel(UUID id, HotelDraft draft) {
        int updated = jdbc.update(
                "UPDATE hotel_catalog.hotels SET name = ?, city = ?, district = ?, address = ?, country = ?, "
                        + "summary = ?, image_path = ?, image_alt = ?, rating = ? " + ACTIVE_BY_ID,
                draft.name().trim(), draft.city().trim(), draft.district().trim(), draft.address().trim(),
                draft.country().trim(), draft.summary().trim(), draft.imagePath().trim(), draft.imageAlt().trim(),
                draft.rating(), id);
        requireUpdated(updated, HOTEL_NOT_FOUND_CODE, HOTEL_NOT_FOUND_MESSAGE);
        return findHotel(id);
    }

    public void removeHotel(UUID id) {
        requireUpdated(jdbc.update(
                "UPDATE hotel_catalog.hotels SET active = false " + ACTIVE_BY_ID, id),
                HOTEL_NOT_FOUND_CODE, HOTEL_NOT_FOUND_MESSAGE);
    }

    public RoomType createRoomType(UUID hotelId, RoomTypeDraft draft) {
        ensureHotelExists(hotelId);
        UUID id = jdbc.queryForObject(
                "INSERT INTO hotel_catalog.room_types (hotel_id, name, details, max_guests, total_inventory) "
                        + "VALUES (?, ?, ?, ?, ?) RETURNING id",
                UUID.class,
                hotelId, draft.name().trim(), draft.details().trim(), draft.maxGuests(), draft.totalInventory());
        return findRoomType(id);
    }

    public RoomType updateRoomType(UUID id, RoomTypeDraft draft) {
        int updated = jdbc.update(
                "UPDATE hotel_catalog.room_types SET name = ?, details = ?, max_guests = ?, total_inventory = ? "
                        + ACTIVE_BY_ID,
                draft.name().trim(), draft.details().trim(), draft.maxGuests(), draft.totalInventory(), id);
        requireUpdated(updated, ROOM_TYPE_NOT_FOUND_CODE, ROOM_TYPE_NOT_FOUND_MESSAGE);
        return findRoomType(id);
    }

    public void removeRoomType(UUID id) {
        requireUpdated(jdbc.update(
                "UPDATE hotel_catalog.room_types SET active = false " + ACTIVE_BY_ID, id),
                ROOM_TYPE_NOT_FOUND_CODE, ROOM_TYPE_NOT_FOUND_MESSAGE);
    }

    private Hotel withRoomTypes(Hotel hotel) {
        List<RoomType> roomTypes = jdbc.query(
                ROOM_TYPE_SELECT
                        + "WHERE hotel_id = ? AND active = true ORDER BY name",
                ROOM_MAPPER,
                hotel.id());
        return new Hotel(
                hotel.id(), hotel.name(), hotel.city(), hotel.district(), hotel.address(), hotel.country(),
                hotel.summary(), hotel.imagePath(), hotel.imageAlt(), hotel.rating(), roomTypes);
    }

    private RoomType findRoomType(UUID id) {
        List<RoomType> roomTypes = jdbc.query(
                ROOM_TYPE_SELECT + ACTIVE_BY_ID,
                ROOM_MAPPER,
                id);
        if (roomTypes.isEmpty()) {
            throw notFound(ROOM_TYPE_NOT_FOUND_CODE, ROOM_TYPE_NOT_FOUND_MESSAGE);
        }
        return roomTypes.getFirst();
    }

    private void ensureHotelExists(UUID hotelId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM hotel_catalog.hotels " + ACTIVE_BY_ID, Integer.class, hotelId);
        if (count == null || count == 0) {
            throw notFound(HOTEL_NOT_FOUND_CODE, HOTEL_NOT_FOUND_MESSAGE);
        }
    }

    private void requireUpdated(int updated, String code, String message) {
        if (updated == 0) {
            throw notFound(code, message);
        }
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
}
