package org.ecoride.gateway.config;

import lombok.RequiredArgsConstructor;
import org.ecoride.gateway.filter.RequestLoggingFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Configuration
@RequiredArgsConstructor
public class GatewayConfig {

    private final RequestLoggingFilter requestLoggingFilter;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()

                .route("trip-service", r -> r
                        .path("/api/trips/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .circuitBreaker(c -> c
                                        .setName("tripServiceCB")
                                        .setFallbackUri("forward:/fallback/trips")
                                )
                                .filter(requestLoggingFilter.apply(
                                        new RequestLoggingFilter.Config() {{
                                            setBaseMessage("TripService Request");
                                            setLogHeaders(false);
                                        }}
                                ))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(3)
                                        .setMethods(org.springframework.http.HttpMethod.GET)
                                )
                        )
                        .uri("lb://trip-service")
                )

                .route("passenger-service", r -> r
                        .path("/api/passengers/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .circuitBreaker(c -> c
                                        .setName("passengerServiceCB")
                                        .setFallbackUri("forward:/fallback/passengers")
                                )
                                .filter(requestLoggingFilter.apply(
                                        new RequestLoggingFilter.Config() {{
                                            setBaseMessage("PassengerService Request");
                                        }}
                                ))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(3)
                                        .setMethods(org.springframework.http.HttpMethod.GET)
                                )
                        )
                        .uri("lb://passenger-service")
                )

                .route("payment-service", r -> r
                        .path("/api/payments/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .circuitBreaker(c -> c
                                        .setName("paymentServiceCB")
                                        .setFallbackUri("forward:/fallback/payments")
                                )
                                .filter(requestLoggingFilter.apply(
                                        new RequestLoggingFilter.Config() {{
                                            setBaseMessage("PaymentService Request");
                                        }}
                                ))
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(redisRateLimiter())
                                )
                        )
                        .uri("lb://payment-service")
                )

                .route("notification-service", r -> r
                        .path("/api/notifications/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .filter(requestLoggingFilter.apply(
                                        new RequestLoggingFilter.Config() {{
                                            setBaseMessage("NotificationService Request");
                                        }}
                                ))
                        )
                        .uri("lb://notification-service")
                )

                .build();
    }

    @Bean
    public org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter redisRateLimiter() {
        return new org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter(100, 200);
    }

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            var userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null) return Mono.just(userId);

            var ip = Objects.requireNonNull(exchange.getRequest().getRemoteAddress())
                    .getAddress().getHostAddress();
            return Mono.just(ip);
        };
    }

}