package com.stays.reservation.adapter.out.jdbc;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.stays.reservation.InventoryDraft;

import com.stays.reservation.application.port.InventoryPort;
import com.stays.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class InventoryRepository implements InventoryPort {
    private final JdbcTemplate jdbc;

    public InventoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int available(UUID hotelId, UUID roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        List<InventoryRow> rows = rows(hotelId, roomTypeId, checkIn, checkOut);
        if (rows.size() != ChronoUnit.DAYS.between(checkIn, checkOut)) {
            return 0;
        }
        return rows.stream().mapToInt(InventoryRow::available).min().orElse(0);
    }

    public void reserve(UUID hotelId, UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int count) {
        List<InventoryRow> rows = rows(hotelId, roomTypeId, checkIn, checkOut);
        if (rows.size() != ChronoUnit.DAYS.between(checkIn, checkOut)) {
            throw unavailable();
        }
        for (InventoryRow row : rows) {
            if (row.available() < count) {
                throw unavailable();
            }
            int updated = jdbc.update(
                    "UPDATE reservations.room_inventory SET total_reserved = total_reserved + ?, version = version + 1 "
                            + "WHERE hotel_id = ? AND room_type_id = ? AND inventory_date = ? AND version = ? "
                            + "AND total_reserved + ? <= floor(total_inventory * 1.10)",
                    count, hotelId, roomTypeId, row.date(), row.version(), count);
            if (updated != 1) {
                throw new ApiException(HttpStatus.CONFLICT, "inventory_changed", "Availability changed. Search again before booking.");
            }
        }
    }

    public void release(UUID hotelId, UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int count) {
        jdbc.update(
                "UPDATE reservations.room_inventory SET total_reserved = total_reserved - ?, version = version + 1 "
                        + "WHERE hotel_id = ? AND room_type_id = ? AND inventory_date >= ? "
                        + "AND inventory_date < ? AND total_reserved >= ?",
                count, hotelId, roomTypeId, checkIn, checkOut, count);
    }

    public void ensureRows(UUID hotelId, UUID roomTypeId, int totalInventory) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM reservations.room_inventory WHERE room_type_id = ?", Integer.class, roomTypeId);
        if (count != null && count > 0) {
            return;
        }
        jdbc.update(
                "INSERT INTO reservations.room_inventory (hotel_id, room_type_id, inventory_date, total_inventory) "
                        + "SELECT ?, ?, inventory_date::date, ? FROM generate_series(current_date, current_date + 730, interval '1 day') inventory_date "
                        + "ON CONFLICT (hotel_id, room_type_id, inventory_date) DO NOTHING",
                hotelId, roomTypeId, totalInventory);
    }

    @Transactional
    public void changeTotal(InventoryDraft draft) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM reservations.room_inventory WHERE room_type_id = ? AND inventory_date >= current_date",
                Integer.class,
                draft.roomTypeId());
        if (count == null || count == 0) {
            ensureRows(draft.hotelId(), draft.roomTypeId(), draft.totalInventory());
            return;
        }
        int changed = jdbc.update(
                "UPDATE reservations.room_inventory SET total_inventory = ?, version = version + 1 "
                        + "WHERE hotel_id = ? AND room_type_id = ? AND inventory_date >= current_date "
                        + "AND total_reserved <= floor(? * 1.10)",
                draft.totalInventory(), draft.hotelId(), draft.roomTypeId(), draft.totalInventory());
        if (changed != count) {
            throw new ApiException(HttpStatus.CONFLICT, "inventory_below_reservations", "Inventory cannot be lower than existing reservations.");
        }
    }

    private List<InventoryRow> rows(UUID hotelId, UUID roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        return jdbc.query(
                "SELECT inventory_date, total_inventory, total_reserved, version FROM reservations.room_inventory "
                        + "WHERE hotel_id = ? AND room_type_id = ? AND inventory_date >= ? AND inventory_date < ? "
                        + "ORDER BY inventory_date",
                (result, row) -> new InventoryRow(
                        result.getDate("inventory_date").toLocalDate(),
                        result.getInt("total_inventory"),
                        result.getInt("total_reserved"),
                        result.getLong("version")),
                hotelId,
                roomTypeId,
                checkIn,
                checkOut);
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.CONFLICT, "not_available", "The selected room is no longer available for every night.");
    }

    private record InventoryRow(LocalDate date, int totalInventory, int totalReserved, long version) {
        private int available() {
            return Math.max(0, (int) Math.floor(totalInventory * 1.10) - totalReserved);
        }
    }
}
