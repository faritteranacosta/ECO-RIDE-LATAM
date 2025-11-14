package org.ecoride.tripservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.ecoride.tripservice.dto.CreateTripRequest;
import org.ecoride.tripservice.dto.ReservationResponse;
import org.ecoride.tripservice.dto.TripResponse;
import org.ecoride.tripservice.events.ReservationEvents;
import org.ecoride.tripservice.exception.BusinessException;
import org.ecoride.tripservice.model.entity.Reservation;
import org.ecoride.tripservice.model.entity.Trip;
import org.ecoride.tripservice.model.enums.ReservationStatus;
import org.ecoride.tripservice.model.enums.TripStatus;
import org.ecoride.tripservice.repository.ReservationRepository;
import org.ecoride.tripservice.repository.TripRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripService {

    private final TripRepository tripRepository;
    private final ReservationRepository reservationRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC_RESERVATION_REQUESTED = "reservation-requested";
    private static final String TOPIC_RESERVATION_CONFIRMED = "reservation-confirmed";
    private static final String TOPIC_RESERVATION_CANCELLED = "reservation-cancelled";

    @Transactional
    public TripResponse createTrip(CreateTripRequest request, UUID driverId) {
        log.info("Creando viaje para driver: {}", driverId);

        Trip trip = Trip.builder()
                .driverId(driverId)
                .origin(request.getOrigin())
                .destination(request.getDestination())
                .startTime(request.getStartTime())
                .seatsTotal(request.getSeatsTotal())
                .seatsAvailable(request.getSeatsTotal())
                .price(request.getPrice())
                .status(TripStatus.ACTIVE)
                .build();

        trip = tripRepository.save(trip);
        log.info("Viaje creado con ID: {}", trip.getId());

        return mapToResponse(trip);
    }

    @Transactional(readOnly = true)
    public List<TripResponse> searchTrips(String origin, String destination,
                                          LocalDateTime from, LocalDateTime to) {
        log.info("Buscando viajes: origin={}, destination={}, from={}, to={}",
                origin, destination, from, to);

        List<Trip> trips = tripRepository.searchTrips(
                TripStatus.ACTIVE, origin, destination, from, to
        );

        return trips.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TripResponse getTripById(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Viaje no encontrado: " + tripId));
        return mapToResponse(trip);
    }

    @Transactional
    public ReservationResponse createReservation(UUID tripId, UUID passengerId) {
        String correlationId = UUID.randomUUID().toString();
        log.info("[{}] Creando reserva para trip: {}, passenger: {}",
                correlationId, tripId, passengerId);

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Viaje no encontrado: " + tripId));

        if (!trip.hasAvailableSeats()) {
            throw new BusinessException("No hay asientos disponibles para este viaje");
        }

        if (reservationRepository.existsActiveReservation(tripId, passengerId)) {
            throw new BusinessException("Ya tienes una reserva activa para este viaje");
        }

        Reservation reservation = Reservation.builder()
                .trip(trip)
                .passengerId(passengerId)
                .status(ReservationStatus.PENDING)
                .build();

        trip.decrementSeats();
        reservation = reservationRepository.save(reservation);
        tripRepository.save(trip);

        log.info("[{}] Reserva creada con ID: {}, status: PENDING",
                correlationId, reservation.getId());

        ReservationEvents.ReservationRequested event = ReservationEvents.ReservationRequested.builder()
                .reservationId(reservation.getId())
                .tripId(tripId)
                .passengerId(passengerId)
                .amount(trip.getPrice())
                .correlationId(correlationId)
                .build();

        kafkaTemplate.send(TOPIC_RESERVATION_REQUESTED, event);
        log.info("[{}] Evento ReservationRequested publicado", correlationId);

        return mapToReservationResponse(reservation);
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservationById(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada: " + reservationId));
        return mapToReservationResponse(reservation);
    }

    @Transactional
    public void confirmReservation(UUID reservationId, String correlationId) {
        log.info("[{}] Confirmando reserva: {}", correlationId, reservationId);

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada: " + reservationId));

        reservation.confirm();
        reservationRepository.save(reservation);

        ReservationEvents.ReservationConfirmed event = ReservationEvents.ReservationConfirmed.builder()
                .reservationId(reservationId)
                .tripId(reservation.getTrip().getId())
                .passengerId(reservation.getPassengerId())
                .correlationId(correlationId)
                .build();

        kafkaTemplate.send(TOPIC_RESERVATION_CONFIRMED, event);
        log.info("[{}] Reserva confirmada y evento publicado", correlationId);
    }

    @Transactional
    public void cancelReservation(UUID reservationId, String reason, String correlationId) {
        log.info("[{}] Cancelando reserva: {}, reason: {}", correlationId, reservationId, reason);

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada: " + reservationId));

        reservation.cancel(reason);
        Trip trip = reservation.getTrip();
        trip.incrementSeats();

        reservationRepository.save(reservation);
        tripRepository.save(trip);

        ReservationEvents.ReservationCancelled event = ReservationEvents.ReservationCancelled.builder()
                .reservationId(reservationId)
                .tripId(trip.getId())
                .passengerId(reservation.getPassengerId())
                .reason(reason)
                .correlationId(correlationId)
                .build();

        kafkaTemplate.send(TOPIC_RESERVATION_CANCELLED, event);
        log.info("[{}] Reserva cancelada, asiento liberado, evento publicado", correlationId);
    }

    private TripResponse mapToResponse(Trip trip) {
        return TripResponse.builder()
                .id(trip.getId())
                .driverId(trip.getDriverId())
                .origin(trip.getOrigin())
                .destination(trip.getDestination())
                .startTime(trip.getStartTime())
                .seatsTotal(trip.getSeatsTotal())
                .seatsAvailable(trip.getSeatsAvailable())
                .price(trip.getPrice())
                .status(trip.getStatus())
                .createdAt(trip.getCreatedAt())
                .build();
    }

    private ReservationResponse mapToReservationResponse(Reservation reservation) {
        return ReservationResponse.builder()
                .id(reservation.getId())
                .tripId(reservation.getTrip().getId())
                .passengerId(reservation.getPassengerId())
                .status(reservation.getStatus())
                .createdAt(reservation.getCreatedAt())
                .updatedAt(reservation.getUpdatedAt())
                .build();
    }
}