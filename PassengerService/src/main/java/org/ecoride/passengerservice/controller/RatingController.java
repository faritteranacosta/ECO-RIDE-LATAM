package org.ecoride.passengerservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ecoride.passengerservice.dto.RatingRequestDTO;
import org.ecoride.passengerservice.dto.RatingResponseDTO;
import org.ecoride.passengerservice.mapper.RatingMapper;
import org.ecoride.passengerservice.model.entity.Rating;
import org.ecoride.passengerservice.service.RatingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ratings")
@RequiredArgsConstructor
@Tag(name = "Rating", description = "Post-trip rating management")
@SecurityRequirement(name = "bearer-jwt")
public class RatingController {

    private final RatingService ratingService;

    @PostMapping
    @Operation(summary = "Create rating", description = "Rate another passenger after a trip")
    public ResponseEntity<RatingResponseDTO> createRating(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RatingRequestDTO request) {

        String keycloakSub = jwt.getSubject();
        Rating rating = ratingService.createRating(keycloakSub, request);
        return new ResponseEntity<>(RatingMapper.toDto(rating), HttpStatus.CREATED);
    }
}