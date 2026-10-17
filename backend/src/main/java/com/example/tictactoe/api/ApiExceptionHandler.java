package com.example.tictactoe.api;

import com.example.tictactoe.api.Dtos.ErrorResponse;
import com.example.tictactoe.domain.GameException;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns rule and validation failures into a consistent body: { "status": 409, "error": "..." }.
 * Each rejection is logged at WARN and counted in {@code tictactoe.requests.rejected{status}}.
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LogManager.getLogger(ApiExceptionHandler.class);

    private final MeterRegistry metrics;

    public ApiExceptionHandler(MeterRegistry metrics) {
        this.metrics = metrics;
    }

    @ExceptionHandler(GameException.class)
    public ResponseEntity<ErrorResponse> handleGame(GameException ex) {
        return respond(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return respond(HttpStatus.BAD_REQUEST.value(),
                "The request body is missing, malformed, or contains an invalid value.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return respond(HttpStatus.BAD_REQUEST.value(), "Invalid game id.");
    }

    private ResponseEntity<ErrorResponse> respond(int status, String message) {
        log.warn("Rejected request ({}): {}", status, message);
        metrics.counter("tictactoe.requests.rejected", "status", String.valueOf(status)).increment();
        return ResponseEntity.status(status).body(new ErrorResponse(status, message));
    }
}
