package com.stays.reservation;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record InventoryDraft(
        @NotNull UUID hotelId,
        @NotNull UUID roomTypeId,
        @Min(1) int totalInventory) {
}
