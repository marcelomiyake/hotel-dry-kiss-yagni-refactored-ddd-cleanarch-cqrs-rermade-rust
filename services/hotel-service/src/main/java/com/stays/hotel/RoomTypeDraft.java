package com.stays.hotel;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RoomTypeDraft(
        @NotBlank String name,
        @NotBlank String details,
        @Min(1) int maxGuests,
        @Min(1) int totalInventory) {
}
