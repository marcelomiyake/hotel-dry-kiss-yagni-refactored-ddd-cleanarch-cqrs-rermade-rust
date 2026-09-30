package com.stays.reservation.application.command;

import java.util.UUID;

import com.stays.reservation.InventoryDraft;
import com.stays.reservation.Reservation;
import com.stays.reservation.ReservationRequest;
import com.stays.reservation.ReservationService;
import com.stays.reservation.application.port.InventoryPort;
import org.springframework.stereotype.Service;

@Service
public final class ReservationCommandHandler implements ReservationCommands {
    private final ReservationService reservations;
    private final InventoryPort inventory;

    public ReservationCommandHandler(ReservationService reservations, InventoryPort inventory) {
        this.reservations = reservations;
        this.inventory = inventory;
    }

    @Override
    public Reservation book(ReservationRequest request) {
        return reservations.book(request);
    }

    @Override
    public Reservation cancel(UUID id) {
        return reservations.cancel(id);
    }

    @Override
    public void changeInventory(InventoryDraft draft) {
        inventory.changeTotal(draft);
    }
}
