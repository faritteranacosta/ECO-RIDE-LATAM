package org.ecoride.gateway.filter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@Slf4j
public class RequestLoggingFilter extends AbstractGatewayFilterFactory<RequestLoggingFilter.Config> {

    public RequestLoggingFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            Instant startTime = Instant.now();
            String path = exchange.getRequest().getURI().getPath();
            String method = exchange.getRequest().getMethod().name();
            String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-Id");

            log.info("[CUSTOM FILTER] [{}] REQUEST START: {} {} - BaseMessage: {}",
                    correlationId, method, path, config.getBaseMessage());

            return chain.filter(exchange)
                    .doOnSuccess(aVoid -> {
                        Instant endTime = Instant.now();
                        Duration duration = Duration.between(startTime, endTime);
                        int statusCode = exchange.getResponse().getStatusCode() != null
                                ? exchange.getResponse().getStatusCode().value()
                                : 0;

                        log.info("[CUSTOM FILTER] [{}] REQUEST END: {} {} - Status: {} - Duration: {}ms",
                                correlationId, method, path, statusCode, duration.toMillis());

                        if (config.isLogHeaders()) {
                            log.debug("[{}] Response Headers: {}",
                                    correlationId,
                                    exchange.getResponse().getHeaders());
                        }
                    })
                    .doOnError(error -> {
                        Instant endTime = Instant.now();
                        Duration duration = Duration.between(startTime, endTime);

                        log.error("[CUSTOM FILTER] [{}] REQUEST ERROR: {} {} - Duration: {}ms - Error: {}",
                                correlationId, method, path, duration.toMillis(), error.getMessage());
                    });
        };
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Config {
        private String baseMessage = "ECO-RIDE Gateway";
        private boolean logHeaders = false;
    }
}