package org.ecoride.passengerservice.mapper;

import org.ecoride.passengerservice.dto.RatingResponseDTO;
import org.ecoride.passengerservice.model.entity.Rating;

public class RatingMapper {
    public static RatingResponseDTO toDto(Rating rating) {
        return RatingResponseDTO.builder()
                .id(rating.getId())
                .tripId(rating.getTripId())
                .fromPassengerId(rating.getFromPassenger().getId())
                .toPassengerId(rating.getToPassenger().getId())
                .score(rating.getScore())
                .comment(rating.getComment())
                .build();
    }
}