package com.stays.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HealthControllerTest {
    @Test
    void reportsTheServiceAsUp() {
        assertThat(new HealthController().health()).containsEntry("status", "UP");
    }
}
