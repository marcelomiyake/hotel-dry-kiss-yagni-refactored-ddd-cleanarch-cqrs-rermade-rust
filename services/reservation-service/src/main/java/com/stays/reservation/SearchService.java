package com.stays.reservation;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import com.stays.reservation.application.port.CatalogPort;
import com.stays.reservation.application.port.InventoryPort;
import com.stays.reservation.application.port.RateQuotePort;
import com.stays.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SearchService {
    private static final Clock CLOCK = Clock.systemUTC();
    private final CatalogPort catalog;
    private final RateQuotePort rates;
    private final InventoryPort inventory;

    public SearchService(CatalogPort catalog, RateQuotePort rates, InventoryPort inventory) {
        this.catalog = catalog;
        this.rates = rates;
        this.inventory = inventory;
    }

    public SearchResponse search(String destination, LocalDate checkIn, LocalDate checkOut, int guests) {
        validateSearch(destination, checkIn, checkOut, guests);
        List<SearchStay> stays = catalog.hotels(destination).stream()
                .map(hotel -> new SearchStay(
                        hotel.id(), hotel.name(), hotel.city(), hotel.district(), hotel.address(), hotel.summary(),
                        hotel.imagePath(), hotel.imageAlt(), hotel.rating(),
                        hotel.roomTypes().stream()
                                .filter(room -> room.maxGuests() >= guests)
                                .map(room -> offer(hotel, room, checkIn, checkOut))
                                .filter(offer -> offer.availableRooms() > 0)
                                .toList()))
                .filter(stay -> !stay.rooms().isEmpty())
                .toList();
        return new SearchResponse(stays, checkIn, checkOut, guests);
    }

    private RoomOffer offer(CatalogHotel hotel, CatalogRoomType room, LocalDate checkIn, LocalDate checkOut) {
        inventory.ensureRows(hotel.id(), room.id(), room.totalInventory());
        RateQuote quote = rates.quote(room.id(), checkIn, checkOut);
        int available = inventory.available(hotel.id(), room.id(), checkIn, checkOut);
        return new RoomOffer(room.id(), room.name(), room.details(), room.maxGuests(), available,
                quote.nights(), quote.total());
    }

    private void validateSearch(String destination, LocalDate checkIn, LocalDate checkOut, int guests) {
        LocalDate today = LocalDate.now(CLOCK);
        if (destination == null || destination.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "destination_required", "Enter a destination to search.");
        }
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)
                || checkIn.isBefore(today) || checkOut.isAfter(today.plusYears(2))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_dates", "Choose valid future dates within two years.");
        }
        if (guests < 1 || guests > 4) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_guest_count", "Choose between 1 and 4 guests.");
        }
    }
}
