package com.stays.rate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import com.stays.rate.domain.RatePeriod;
import org.junit.jupiter.api.Test;

class RatePeriodTest {
    @Test
    void countsTheNightsInAnInclusiveExclusivePeriod() {
        RatePeriod period = new RatePeriod(LocalDate.parse("2026-06-10"), LocalDate.parse("2026-06-13"));

        assertThat(period.nights()).isEqualTo(3);
    }

    @Test
    void rejectsMissingOrOutOfRangePeriods() {
        LocalDate start = LocalDate.parse("2026-06-10");
        LocalDate overlongEnd = start.plusDays(366);

        assertThatThrownBy(() -> new RatePeriod(null, start)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RatePeriod(start, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RatePeriod(start, start)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RatePeriod(start, overlongEnd))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
