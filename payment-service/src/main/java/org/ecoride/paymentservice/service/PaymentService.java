package org.ecoride.paymentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ecoride.paymentservice.events.ReservationEvents;
import org.ecoride.paymentservice.model.entity.Charge;
import org.ecoride.paymentservice.model.entity.PaymentIntent;
import org.ecoride.paymentservice.model.enums.PaymentStatus;
import org.ecoride.paymentservice.model.enums.Providers;
import org.ecoride.paymentservice.repository.ChargeRepository;
import org.ecoride.paymentservice.repository.PaymentIntentRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
@Service
public class PaymentService {
    private final PaymentIntentRepository paymentIntentRepository;
    private final ChargeRepository chargeRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Constantes para los tópicos de salida
    private static final String TOPIC_PAYMENT_AUTHORIZED = "payment-authorized";
    private static final String TOPIC_PAYMENT_FAILED = "payment-failed";


    @KafkaListener(topics = "reservation-requested", groupId = "payment-service-group")
    @Transactional
    public void processPayment(ReservationEvents.ReservationRequested event) {
        String correlationId = event.getCorrelationId();
        log.info("[{}] Iniciando procesamiento de pago para reserva: {}", correlationId, event.getReservationId());

        //  Idempotencia Verificar si ya existe un intento para esta reserva
        Optional<PaymentIntent> existingIntent = paymentIntentRepository.findByReservationId(event.getReservationId());
        if (existingIntent.isPresent()) {
            log.warn("[{}] Idempotencia: Ya existe un intento de pago para la reserva {}. Estado actual: {}",
                    correlationId, event.getReservationId(), existingIntent.get().getStatus());
            return; // No procesar de nuevo
        }

        //  Crear el PaymentIntent inicial (PENDING)
        PaymentIntent intent = PaymentIntent.builder()
                .reservationId(event.getReservationId())
                .amount(event.getAmount())
                .currency("COP")
                .status(PaymentStatus.PENDING)
                .build();

        intent = paymentIntentRepository.save(intent);
        log.debug("[{}] PaymentIntent creado con ID: {}", correlationId, intent.getId());

        try {
            //  Simulación de Pasarela de Pagos
            // simulada: si el monto es > 100000, falla.
            boolean success = event.getAmount().compareTo(new BigDecimal("100000")) <= 0;

            if (success) {
                handleSuccess(intent, correlationId, event.getReservationId());
            } else {
                handleFailure(intent, "Fondos insuficientes (Simulado: monto > 100000)", correlationId, event.getReservationId());
            }

        } catch (Exception e) {
            log.error("[{}] Error técnico procesando pago: {}", correlationId, e.getMessage());
            handleFailure(intent, "Error interno del sistema de pagos", correlationId, event.getReservationId());
        }
    }

    /**
     * exito crea el Charge, actualiza Intent y notifica.
     */
    private void handleSuccess(PaymentIntent intent, String correlationId, UUID reservationId) {
        intent.setStatus(PaymentStatus.AUTHORIZED);

        // crear el registro de Cobro cCharge)
        Charge charge = Charge.builder()
                .paymentIntent(intent)
                .amount(intent.getAmount())
                .provider(Providers.MOCK_BANK) // Usamos el Enum que ya tienes
                .providerRef(UUID.randomUUID().toString()) // Simulación de ID de transacción bancaria
                .build();

        chargeRepository.save(charge);
        paymentIntentRepository.save(intent); // Guardar el cambio de estado

        // publicar evento de exito
        ReservationEvents.PaymentAuthorized successEvent = ReservationEvents.PaymentAuthorized.builder()
                .reservationId(reservationId)
                .paymentIntentId(intent.getId())
                .chargeId(charge.getId())
                .correlationId(correlationId)
                .build();

        kafkaTemplate.send(TOPIC_PAYMENT_AUTHORIZED, successEvent);
        log.info("[{}] PAGO EXITOSO. ChargeId: {}. Evento enviado a {}", correlationId, charge.getId(), TOPIC_PAYMENT_AUTHORIZED);
    }

    /**
     * fallo actualiza Intent a FAILED y notifica para compensación.
     */
    private void handleFailure(PaymentIntent intent, String reason, String correlationId, UUID reservationId) {
        // actualizar estado
        intent.setStatus(PaymentStatus.FAILED);
        paymentIntentRepository.save(intent);

        // publicar evento de Fallo (Compensación)
        ReservationEvents.PaymentFailed failedEvent = ReservationEvents.PaymentFailed.builder()
                .reservationId(reservationId)
                .reason(reason)
                .correlationId(correlationId)
                .build();

        kafkaTemplate.send(TOPIC_PAYMENT_FAILED, failedEvent);
        log.warn("[{}] PAGO FALLIDO. Razón: {}. Evento enviado a {}", correlationId, reason, TOPIC_PAYMENT_FAILED);
    }
}
