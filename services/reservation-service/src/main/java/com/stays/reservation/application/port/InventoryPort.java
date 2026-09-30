package com.stays.reservation.application.port;

import java.time.LocalDate;
import java.util.UUID;

import com.stays.reservation.InventoryDraft;

public interface InventoryPort {
    int available(UUID hotelId, UUID roomTypeId, LocalDate checkIn, LocalDate checkOut);

    void reserve(UUID hotelId, UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int count);

    void release(UUID hotelId, UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int count);

    void ensureRows(UUID hotelId, UUID roomTypeId, int totalInventory);

    void changeTotal(InventoryDraft draft);
}
