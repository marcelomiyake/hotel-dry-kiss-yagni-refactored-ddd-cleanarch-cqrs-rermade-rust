package com.stays.rate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.stays.common.ApiException;
import com.stays.rate.application.command.RateCommands;
import com.stays.rate.application.query.RateQueries;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = RateApplication.class)
@Testcontainers
class RateRepositoryIntegrationTest {
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
    private RateQueries queries;

    @Autowired
    private RateCommands commands;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void quotesAndUpdatesNightlyRates() {
        UUID roomId = UUID.randomUUID();
        LocalDate start = LocalDate.now().plusDays(20);
        commands.setRate(new RateDraft(roomId, start, new BigDecimal("125.50")));
        commands.setRate(new RateDraft(roomId, start.plusDays(1), new BigDecimal("175.00")));
        commands.setRate(new RateDraft(roomId, start, new BigDecimal("130.00")));

        RateQuote quote = queries.quote(roomId, start, start.plusDays(2));
        assertThat(quote.nights()).hasSize(2);
        assertThat(quote.total()).isEqualByComparingTo("305.00");
        assertThat(quote.nights().getFirst().amount()).isEqualByComparingTo("130.00");
    }

    @Test
    void validatesQuoteRangeAndRequiresEveryNightToHaveARate() {
        UUID roomId = UUID.randomUUID();
        LocalDate start = LocalDate.now().plusDays(30);
        LocalDate noNights = start;
        LocalDate beyondSchedule = start.plusDays(366);
        LocalDate missingRate = start.plusDays(1);
        assertThatThrownBy(() -> queries.quote(roomId, start, noNights)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> queries.quote(roomId, start, beyondSchedule)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> queries.quote(roomId, start, missingRate)).isInstanceOf(ApiException.class);
    }

    @Test
    void createsAFullYearScheduleWithWeekendPricing() {
        UUID roomId = UUID.randomUUID();
        commands.createSchedule(new RateSchedule(roomId, new BigDecimal("200.00")));
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM nightly_rates.rates WHERE room_type_id = ?", Integer.class, roomId);
        assertThat(count).isEqualTo(731);
        assertThat(queries.quote(roomId, LocalDate.now().plusDays(10), LocalDate.now().plusDays(11)).nights())
                .hasSize(1);
    }
}
