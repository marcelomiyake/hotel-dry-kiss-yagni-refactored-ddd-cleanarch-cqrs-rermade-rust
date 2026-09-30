package com.stays.reservation.adapter.out.jdbc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.stays.reservation.CatalogHotel;
import com.stays.reservation.CatalogRoomType;
import com.stays.reservation.Reservation;
import com.stays.reservation.ReservationRequest;

import com.stays.reservation.application.port.ReservationStore;
import com.stays.reservation.domain.ReservationStatus;
import com.stays.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReservationRepository implements ReservationStore {
    private static final String COLUMNS = "id, hotel_id, room_type_id, hotel_name, city, district, image_path, image_alt, room_type_name, check_in, check_out, "
            + "rooms, guests, guest_name, guest_email, total, status, payment_id, created_at";
    private static final String SELECT_BOOKINGS = "SELECT " + COLUMNS + " FROM reservations.bookings ";
    private final JdbcTemplate jdbc;

    public ReservationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Reservation> find(UUID id) {
        return jdbc.query(SELECT_BOOKINGS + "WHERE id = ?", ReservationRepository::map, id).stream().findFirst();
    }

    public Optional<Reservation> findByEmailAndId(UUID id, String email) {
        List<Reservation> rows = jdbc.query(
                SELECT_BOOKINGS + "WHERE id = ? AND lower(guest_email) = lower(?)",
                ReservationRepository::map,
                id,
                email.trim());
        return rows.stream().findFirst();
    }

    public List<Reservation> findByEmail(String email) {
        return jdbc.query(
                SELECT_BOOKINGS + "WHERE lower(guest_email) = lower(?) ORDER BY created_at DESC",
                ReservationRepository::map,
                email.trim());
    }

    public Reservation create(ReservationRequest request, CatalogHotel hotel, CatalogRoomType room, BigDecimal total) {
        jdbc.update(
                "INSERT INTO reservations.bookings "
                        + "(id, hotel_id, room_type_id, hotel_name, city, district, image_path, image_alt, room_type_name, "
                        + "check_in, check_out, rooms, guests, guest_name, guest_email, total, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PAYMENT_PENDING')",
                request.reservationId(), request.hotelId(), request.roomTypeId(), hotel.name(), hotel.city(), hotel.district(),
                hotel.imagePath(), hotel.imageAlt(), room.name(),
                request.checkIn(), request.checkOut(), request.rooms(), request.guests(), request.guestName().trim(),
                request.guestEmail().trim().toLowerCase(), total);
        return find(request.reservationId()).orElseThrow();
    }

    public Reservation setPaid(UUID id, UUID paymentId) {
        jdbc.update(
                "UPDATE reservations.bookings SET status = 'CONFIRMED', payment_id = ? WHERE id = ? AND status = 'PAYMENT_PENDING'",
                paymentId, id);
        return require(id);
    }

    public Reservation cancel(UUID id) {
        jdbc.update(
                "UPDATE reservations.bookings SET status = 'CANCELLED' WHERE id = ? AND status <> 'CANCELLED'", id);
        return require(id);
    }

    public Reservation require(UUID id) {
        return find(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "reservation_not_found", "Reservation not found."));
    }

    private static Reservation map(java.sql.ResultSet result, int row) throws java.sql.SQLException {
        return new Reservation(
                result.getObject("id", UUID.class),
                result.getObject("hotel_id", UUID.class),
                result.getObject("room_type_id", UUID.class),
                result.getString("hotel_name"),
                result.getString("city"),
                result.getString("district"),
                result.getString("image_path"),
                result.getString("image_alt"),
                result.getString("room_type_name"),
                result.getObject("check_in", LocalDate.class),
                result.getObject("check_out", LocalDate.class),
                result.getInt("rooms"),
                result.getInt("guests"),
                result.getString("guest_name"),
                result.getString("guest_email"),
                result.getBigDecimal("total"),
                ReservationStatus.valueOf(result.getString("status")),
                result.getObject("payment_id", UUID.class),
                result.getTimestamp("created_at").toInstant());
    }
}
