package com.stays.rate.adapter.out.jdbc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.stays.rate.NightlyRate;
import com.stays.rate.RateDraft;
import com.stays.rate.RateSchedule;

import com.stays.rate.application.port.RateReadPort;
import com.stays.rate.application.port.RateWritePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RateRepository implements RateReadPort, RateWritePort {
    private final JdbcTemplate jdbc;

    public RateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<NightlyRate> findRates(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        return jdbc.query(
                "SELECT rate_date, amount FROM nightly_rates.rates "
                        + "WHERE room_type_id = ? AND rate_date >= ? AND rate_date < ? ORDER BY rate_date",
                (result, row) -> new NightlyRate(result.getDate("rate_date").toLocalDate(), result.getBigDecimal("amount")),
                roomTypeId,
                checkIn,
                checkOut);
    }

    public NightlyRate saveRate(RateDraft draft) {
        jdbc.update(
                "INSERT INTO nightly_rates.rates (room_type_id, rate_date, amount) VALUES (?, ?, ?) "
                        + "ON CONFLICT (room_type_id, rate_date) DO UPDATE SET amount = excluded.amount",
                draft.roomTypeId(), draft.date(), draft.amount());
        return new NightlyRate(draft.date(), draft.amount());
    }

    public void saveSchedule(RateSchedule schedule) {
        jdbc.update(
                "INSERT INTO nightly_rates.rates (room_type_id, rate_date, amount) "
                        + "SELECT ?, rate_date::date, round(? * CASE WHEN extract(isodow FROM rate_date) IN (5, 6) "
                        + "THEN 1.15 ELSE 1 END, 2) "
                        + "FROM generate_series(current_date, current_date + 730, interval '1 day') rate_date "
                        + "ON CONFLICT (room_type_id, rate_date) DO UPDATE SET amount = excluded.amount",
                schedule.roomTypeId(), schedule.baseRate());
    }
}
