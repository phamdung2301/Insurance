package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.LocationDto;
import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.model.Location;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.service.PolicyLocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/policies/{policyId}/locations", "/api/policies/{policyId}/locations"})
@RequiredArgsConstructor
public class PolicyLocationController {

    private final PolicyLocationService policyLocationService;

    /**
     * GET /policies/{policyId}/locations
     * Get all locations of a policy document
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Location>>> getLocations(
            @PathVariable("policyId") String policyId
    ) {
        List<Location> locations = policyLocationService.getLocations(policyId);
        return ResponseEntity.ok(ApiResponse.success(locations, "Locations retrieved successfully"));
    }

    /**
     * GET /policies/{policyId}/locations/{locationId}
     * Get single location by ID in policy document
     */
    @GetMapping("/{locationId}")
    public ResponseEntity<ApiResponse<Location>> getLocationById(
            @PathVariable("policyId") String policyId,
            @PathVariable("locationId") Integer locationId
    ) {
        Location location = policyLocationService.getLocationById(policyId, locationId);
        return ResponseEntity.ok(ApiResponse.success(location, "Location retrieved successfully"));
    }

    /**
     * POST /policies/{policyId}/locations
     * Add a nested Location into policy document
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Policy>> addLocation(
            @PathVariable("policyId") String policyId,
            @Valid @RequestBody LocationDto locationDto
    ) {
        Policy updatedPolicy = policyLocationService.addLocation(policyId, locationDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(updatedPolicy, "Location added successfully to policy"));
    }

    /**
     * PUT /policies/{policyId}/locations/{locationId}
     * Update an existing Location inside policy document
     */
    @PutMapping("/{locationId}")
    public ResponseEntity<ApiResponse<Policy>> updateLocation(
            @PathVariable("policyId") String policyId,
            @PathVariable("locationId") Integer locationId,
            @Valid @RequestBody LocationDto locationDto
    ) {
        Policy updatedPolicy = policyLocationService.updateLocation(policyId, locationId, locationDto);
        return ResponseEntity.ok(ApiResponse.success(updatedPolicy, "Location updated successfully in policy"));
    }

    /**
     * DELETE /policies/{policyId}/locations/{locationId}
     * Remove a Location from policy document
     */
    @DeleteMapping("/{locationId}")
    public ResponseEntity<ApiResponse<Policy>> removeLocation(
            @PathVariable("policyId") String policyId,
            @PathVariable("locationId") Integer locationId
    ) {
        Policy updatedPolicy = policyLocationService.removeLocation(policyId, locationId);
        return ResponseEntity.ok(ApiResponse.success(updatedPolicy, "Location removed successfully from policy"));
    }
}
