package com.stays.reservation.adapter.in.web;

import java.util.List;
import java.util.UUID;

import com.stays.reservation.InventoryDraft;
import com.stays.reservation.Reservation;
import com.stays.reservation.ReservationRequest;

import com.stays.reservation.application.command.ReservationCommands;
import com.stays.reservation.application.query.ReservationQueries;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReservationController {
    private final ReservationCommands reservationCommands;
    private final ReservationQueries reservationQueries;

    public ReservationController(
            ReservationCommands reservationCommands,
            ReservationQueries reservationQueries) {
        this.reservationCommands = reservationCommands;
        this.reservationQueries = reservationQueries;
    }

    @GetMapping("/reservations")
    public List<Reservation> history(@RequestParam(name = "email") String email) {
        return reservationQueries.findByEmail(email);
    }

    @GetMapping("/reservations/{id}")
    public Reservation find(@PathVariable("id") UUID id) {
        return reservationQueries.find(id);
    }

    @PostMapping("/reservations")
    public ResponseEntity<Reservation> book(@Valid @RequestBody ReservationRequest request) {
        return ResponseEntity.ok(reservationCommands.book(request));
    }

    @DeleteMapping("/reservations/{id}")
    public Reservation cancel(@PathVariable("id") UUID id) {
        return reservationCommands.cancel(id);
    }

    @PutMapping("/admin/inventory")
    public ResponseEntity<Void> changeInventory(@Valid @RequestBody InventoryDraft draft) {
        reservationCommands.changeInventory(draft);
        return ResponseEntity.noContent().build();
    }
}
