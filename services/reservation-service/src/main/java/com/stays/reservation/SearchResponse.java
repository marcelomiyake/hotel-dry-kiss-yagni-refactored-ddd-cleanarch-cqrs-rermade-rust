package com.stays.reservation;

import java.time.LocalDate;
import java.util.List;

public record SearchResponse(List<SearchStay> stays, LocalDate checkIn, LocalDate checkOut, int guests) {
}
