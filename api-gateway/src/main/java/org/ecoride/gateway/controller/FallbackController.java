package org.ecoride.gateway.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/fallback")
@Slf4j
public class FallbackController {

    @GetMapping("/trips")
    public ResponseEntity<FallbackResponse> tripsFallback() {
        log.warn("Circuit breaker activated for trip-service");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new FallbackResponse(
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "Trip Service is temporarily unavailable. Please try again later.",
                        LocalDateTime.now()
                ));
    }

    @GetMapping("/passengers")
    public ResponseEntity<FallbackResponse> passengersFallback() {
        log.warn("Circuit breaker activated for passenger-service");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new FallbackResponse(
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "Passenger Service is temporarily unavailable. Please try again later.",
                        LocalDateTime.now()
                ));
    }

    @GetMapping("/payments")
    public ResponseEntity<FallbackResponse> paymentsFallback() {
        log.warn("Circuit breaker activated for payment-service");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new FallbackResponse(
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "Payment Service is temporarily unavailable. Please try again later.",
                        LocalDateTime.now()
                ));
    }

    @Data
    @AllArgsConstructor
    public static class FallbackResponse {
        private int status;
        private String message;
        private LocalDateTime timestamp;
    }
}