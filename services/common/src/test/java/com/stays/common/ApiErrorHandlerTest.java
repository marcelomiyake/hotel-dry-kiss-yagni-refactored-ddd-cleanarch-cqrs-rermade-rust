package com.stays.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiErrorHandlerTest {
    private final ApiErrorHandler handler = new ApiErrorHandler();

    @Test
    void returnsTheApiStatusAndCode() {
        ApiException exception = new ApiException(HttpStatus.CONFLICT, "conflict", "Already exists");

        var response = handler.handleApiException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "conflict");
        assertThat(response.getBody()).containsEntry("message", "Already exists");
        assertThat(response.getBody()).containsKey("timestamp");
    }

    @Test
    void hidesUnexpectedExceptionDetails() {
        var response = handler.handleUnexpectedError(new IllegalStateException("private detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("message", "The request could not be completed.");
        assertThat(response.getBody()).doesNotContainValue("private detail");
    }

    @Test
    void returnsClientErrorForBadInput() {
        var response = handler.handleInvalidRequest(new IllegalArgumentException("bad"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "invalid_request");
    }
}
