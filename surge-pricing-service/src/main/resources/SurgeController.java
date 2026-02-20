package com.ridehailing.surge.controller;

import com.ridehailing.shared.dto.ApiResponse;
import com.ridehailing.shared.util.H3Util;
import com.ridehailing.surge.service.SurgeCalculatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/surge")
@RequiredArgsConstructor
public class SurgeController {

    private final SurgeCalculatorService surgeCalculatorService;

    /**
     * Get surge multiplier by H3 cell ID.
     */
    @GetMapping("/{geoCell}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getByCell(
            @PathVariable String geoCell) {

        double multiplier = surgeCalculatorService.getSurgeMultiplier(geoCell);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "geoCell", geoCell,
                "surgeMultiplier", multiplier
        )));
    }

    /**
     * Get surge multiplier by lat/lng — convenience endpoint for mobile clients.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getByLatLng(
            @RequestParam double lat,
            @RequestParam double lng) {

        String cell = H3Util.surgeCell(lat, lng);
        double multiplier = surgeCalculatorService.getSurgeMultiplier(cell);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "geoCell", cell,
                "latitude", lat,
                "longitude", lng,
                "surgeMultiplier", multiplier
        )));
    }
}
