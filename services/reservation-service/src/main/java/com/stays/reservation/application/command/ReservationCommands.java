package com.stays.reservation.application.command;

import java.util.UUID;

import com.stays.reservation.InventoryDraft;
import com.stays.reservation.Reservation;
import com.stays.reservation.ReservationRequest;

public interface ReservationCommands {
    Reservation book(ReservationRequest request);

    Reservation cancel(UUID id);

    void changeInventory(InventoryDraft draft);
}
