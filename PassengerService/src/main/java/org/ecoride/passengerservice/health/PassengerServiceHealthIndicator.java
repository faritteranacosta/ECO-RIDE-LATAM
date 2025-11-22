package org.ecoride.passengerservice.health;

import lombok.RequiredArgsConstructor;
import org.ecoride.passengerservice.repository.PassengerRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor

 // Verifica la salud del servicio consultando la base de datos y expone métricas en /actuator/health para monitoreo.
public class PassengerServiceHealthIndicator implements HealthIndicator {

    private final PassengerRepository passengerRepository;

    @Override
    public Health health() {
        try {
            long count = passengerRepository.count();
            return Health.up()
                    .withDetail("passengers", count)
                    .withDetail("status", "Database connection OK")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}