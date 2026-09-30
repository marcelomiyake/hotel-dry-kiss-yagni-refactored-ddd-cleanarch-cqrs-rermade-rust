package com.stays.hotel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.stays.common.ApiException;
import com.stays.hotel.application.command.HotelCommands;
import com.stays.hotel.application.query.HotelQueries;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = HotelApplication.class)
@Testcontainers
class HotelRepositoryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("hotel")
            .withUsername("hotel_app")
            .withPassword("hotel_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private HotelQueries queries;

    @Autowired
    private HotelCommands commands;

    @Test
    void searchesSeededHotelsAndTheirActiveRoomTypes() {
        assertThat(queries.findHotels(null)).hasSize(2);
        assertThat(queries.findHotels("lisBON")).allSatisfy(hotel -> assertThat(hotel.roomTypes()).isNotEmpty());
        assertThat(queries.findHotels("nowhere")).isEmpty();
        assertThat(queries.findHotel(java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .roomTypes()).hasSize(2);
    }

    @Test
    void managesHotelsAndRoomTypesAndReturnsNotFoundForInactiveRecords() {
        Hotel created = commands.createHotel(hotelDraft("Casa Test"));
        UUID hotelId = created.id();
        assertThat(created.city()).isEqualTo("Lisbon");

        Hotel updated = commands.updateHotel(hotelId, hotelDraft("Casa Updated"));
        assertThat(updated.name()).isEqualTo("Casa Updated");

        RoomType room = commands.createRoomType(hotelId, new RoomTypeDraft("Suite", "Two beds", 4, 3));
        UUID roomId = room.id();
        assertThat(room.totalInventory()).isEqualTo(3);
        assertThat(queries.findHotel(hotelId).roomTypes()).contains(room);

        RoomType updatedRoom = commands.updateRoomType(roomId, new RoomTypeDraft("Family suite", "Two beds", 4, 4));
        assertThat(updatedRoom.name()).isEqualTo("Family suite");
        commands.removeRoomType(roomId);
        assertThat(queries.findHotel(hotelId).roomTypes()).isEmpty();
        RoomTypeDraft hiddenDraft = new RoomTypeDraft("Hidden", "Details", 2, 1);
        assertThatThrownBy(() -> commands.updateRoomType(roomId, hiddenDraft))
                .isInstanceOf(ApiException.class);

        commands.removeHotel(hotelId);
        assertThat(queries.findHotels("Casa Updated")).isEmpty();
        assertThatThrownBy(() -> queries.findHotel(hotelId)).isInstanceOf(ApiException.class);
        RoomTypeDraft replacementDraft = new RoomTypeDraft("Suite", "Details", 2, 1);
        assertThatThrownBy(() -> commands.createRoomType(hotelId, replacementDraft))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> commands.removeRoomType(roomId)).isInstanceOf(ApiException.class);
    }

    private HotelDraft hotelDraft(String name) {
        return new HotelDraft(name, "Lisbon", "Alcântara", "Rua Test 1", "Portugal", "A test hotel.",
                "/images/test.webp", "A test hotel", 8.5);
    }
}
