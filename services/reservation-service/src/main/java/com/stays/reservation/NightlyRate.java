package com.stays.reservation;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NightlyRate(LocalDate date, BigDecimal amount) {
}
