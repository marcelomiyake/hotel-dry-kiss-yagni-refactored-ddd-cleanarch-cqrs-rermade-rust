package com.stays.hotel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record HotelDraft(
        @NotBlank String name,
        @NotBlank String city,
        @NotBlank String district,
        @NotBlank String address,
        @NotBlank String country,
        @NotBlank String summary,
        @NotBlank String imagePath,
        @NotBlank String imageAlt,
        @NotNull Double rating) {
}
