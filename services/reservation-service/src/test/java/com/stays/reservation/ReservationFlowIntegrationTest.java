package com.stays.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import tools.jackson.databind.ObjectMapper;
import com.stays.common.ApiException;
import com.stays.reservation.application.port.CatalogPort;
import com.stays.reservation.application.port.InventoryPort;
import com.stays.reservation.application.port.PaymentPort;
import com.stays.reservation.application.port.RateQuotePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = ReservationApplication.class)
@AutoConfigureMockMvc
@Testcontainers
class ReservationFlowIntegrationTest {
    private static final UUID HOTEL_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final LocalDate CHECK_IN = LocalDate.now().plusDays(80);
    private static final LocalDate CHECK_OUT = CHECK_IN.plusDays(2);
    private static final UUID RESERVATION_ID = UUID.randomUUID();
    private static final UUID PAYMENT_ID = UUID.randomUUID();

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
        properties.add("app.admin.api-key", () -> "staff-test-key");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private InventoryPort inventory;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private CatalogPort catalog;

    @MockitoBean
    private RateQuotePort rates;

    @MockitoBean
    private PaymentPort payments;

    @BeforeEach
    void resetClients() {
        reset(catalog, rates, payments);
        inventory.changeTotal(new InventoryDraft(HOTEL_ID, ROOM_ID, 12));
    }

    @Test
    void searchesAvailableRoomsAndValidatesSearchInput() throws Exception {
        given(catalog.hotels("Lisbon")).willReturn(List.of(hotel()));
        given(rates.quote(ROOM_ID, CHECK_IN, CHECK_OUT)).willReturn(quote());

        mvc.perform(get("/api/search")
                        .queryParam("destination", "Lisbon")
                        .queryParam("checkIn", CHECK_IN.toString())
                        .queryParam("checkOut", CHECK_OUT.toString())
                        .queryParam("guests", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stays.length()").value(1))
                .andExpect(jsonPath("$.stays[0].rooms[0].availableRooms").value(13))
                .andExpect(jsonPath("$.stays[0].rooms[0].totalPrice").value(1240));

        mvc.perform(get("/api/search")
                        .queryParam("destination", "Lisbon")
                        .queryParam("checkIn", CHECK_IN.toString())
                        .queryParam("checkOut", CHECK_IN.toString())
                        .queryParam("guests", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_dates"));
        mvc.perform(get("/api/search")
                        .queryParam("destination", "Lisbon")
                        .queryParam("checkIn", CHECK_IN.toString())
                        .queryParam("checkOut", CHECK_OUT.toString())
                        .queryParam("guests", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_guest_count"));
        verify(catalog, times(1)).hotels("Lisbon");
    }

    @Test
    void reservesIdempotentlyListsAndCancelsTheBooking() throws Exception {
        given(catalog.hotel(HOTEL_ID)).willReturn(hotel());
        given(rates.quote(ROOM_ID, CHECK_IN, CHECK_OUT)).willReturn(quote());
        given(payments.charge(any(PaymentRequest.class))).willAnswer(invocation -> {
            PaymentRequest request = invocation.getArgument(0);
            return new Payment(PAYMENT_ID, request.reservationId(), request.amount(), "PAID", Instant.now());
        });
        given(payments.refund(RESERVATION_ID)).willReturn(
                new Payment(PAYMENT_ID, RESERVATION_ID, new BigDecimal("1240.00"), "REFUNDED", Instant.now()));

        String body = requestJson("Alex Guest", "alex@example.com");
        mvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.hotelName").value("Four Seasons Hotel Ritz Lisbon"))
                .andExpect(jsonPath("$.total").value(1240));
        mvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        verify(payments, times(1)).charge(any(PaymentRequest.class));

        mvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("Different Guest", "alex@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("idempotency_key_reused"));
        var history = mvc.perform(get("/api/reservations").queryParam("email", "ALEX@example.com")).andReturn();
        assertThat(history.getResolvedException()).isNull();
        assertThat(history.getResponse().getStatus()).isEqualTo(200);
        assertThat(history.getResponse().getContentAsString()).contains("Alex Guest");
        mvc.perform(get("/api/reservations/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("reservation_not_found"));

        mvc.perform(delete("/api/reservations/{id}", RESERVATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(delete("/api/reservations/{id}", RESERVATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        verify(payments, times(1)).refund(RESERVATION_ID);
        assertThat(inventory.available(HOTEL_ID, ROOM_ID, CHECK_IN, CHECK_OUT)).isEqualTo(13);
    }

    @Test
    void keepsInventoryUpdatesAtomicAndProtectsStaffEndpoint() throws Exception {
        UUID hotelId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        inventory.changeTotal(new InventoryDraft(hotelId, roomId, 5));
        assertThat(inventory.available(hotelId, roomId, CHECK_IN, CHECK_OUT)).isEqualTo(5);
        inventory.reserve(hotelId, roomId, CHECK_IN, CHECK_OUT, 5);
        InventoryDraft belowReservedCapacity = new InventoryDraft(hotelId, roomId, 4);
        assertThatThrownBy(() -> inventory.changeTotal(belowReservedCapacity))
                .isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForObject(
                "SELECT count(DISTINCT total_inventory) FROM reservations.room_inventory WHERE room_type_id = ?",
                Integer.class,
                roomId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT min(total_inventory) FROM reservations.room_inventory WHERE room_type_id = ?",
                Integer.class,
                roomId)).isEqualTo(5);

        mvc.perform(put("/api/admin/inventory").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new InventoryDraft(HOTEL_ID, ROOM_ID, 14))))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/inventory").header("X-Admin-Key", "staff-test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new InventoryDraft(HOTEL_ID, ROOM_ID, 14))))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsMalformedReservationAndReturnsUnknownErrorsSafely() throws Exception {
        mvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));
    }

    private CatalogHotel hotel() {
        return new CatalogHotel(HOTEL_ID, "Four Seasons Hotel Ritz Lisbon", "Lisbon", "Avenidas Novas",
                "Rua Rodrigo da Fonseca 88", "Portugal", "A landmark city address.",
                "/images/ritz.webp", "Hotel above the garden trees.", new BigDecimal("9.5"),
                List.of(new CatalogRoomType(ROOM_ID, HOTEL_ID, "Deluxe King", "King bed · 2 guests", 2, 12)));
    }

    private RateQuote quote() {
        return new RateQuote(ROOM_ID, List.of(
                new NightlyRate(CHECK_IN, new BigDecimal("620.00")),
                new NightlyRate(CHECK_IN.plusDays(1), new BigDecimal("620.00"))), new BigDecimal("1240.00"));
    }

    private String requestJson(String guestName, String email) throws Exception {
        return json.writeValueAsString(new ReservationRequest(RESERVATION_ID, HOTEL_ID, ROOM_ID,
                CHECK_IN, CHECK_OUT, 1, 2, guestName, email));
    }
}
