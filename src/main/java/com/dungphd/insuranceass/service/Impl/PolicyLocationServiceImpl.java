package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.CoverageDto;
import com.dungphd.insuranceass.dto.LocationDto;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.Coverage;
import com.dungphd.insuranceass.model.Location;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.service.PolicyLocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyLocationServiceImpl implements PolicyLocationService {

    private final PolicyRepository policyRepository;

    @Override
    public List<Location> getLocations(String policyIdOrNumber) {
        Policy policy = findPolicy(policyIdOrNumber);
        return policy.getLocations() != null ? policy.getLocations() : Collections.emptyList();
    }

    @Override
    public Location getLocationById(String policyIdOrNumber, Integer locationId) {
        Policy policy = findPolicy(policyIdOrNumber);
        return findLocationInPolicy(policy, locationId);
    }

    @Override
    public Policy addLocation(String policyIdOrNumber, LocationDto locationDto) {
        Policy policy = findPolicy(policyIdOrNumber);
        validatePolicyModifiable(policy);

        if (policy.getLocations() == null) {
            policy.setLocations(new ArrayList<>());
        }

        // Determine or validate locationId
        int newLocationId;
        if (locationDto.getLocationId() != null) {
            final int requestedId = locationDto.getLocationId();
            boolean exists = policy.getLocations().stream()
                    .anyMatch(loc -> requestedId == loc.getLocationId());
            if (exists) {
                throw new BadRequestException("Location with ID " + requestedId + " already exists in policy " + policy.getPolicyNumber());
            }
            newLocationId = requestedId;
        } else {
            // Auto-increment locationId: max + 1
            int maxId = policy.getLocations().stream()
                    .filter(loc -> loc.getLocationId() != null)
                    .mapToInt(Location::getLocationId)
                    .max()
                    .orElse(0);
            newLocationId = maxId + 1;
        }

        List<Coverage> coverages = new ArrayList<>();
        if (locationDto.getCoverages() != null) {
            coverages = locationDto.getCoverages().stream()
                    .map(CoverageDto::toEntity)
                    .collect(Collectors.toList());
        }

        Location newLocation = Location.builder()
                .locationId(newLocationId)
                .address(locationDto.getAddress() != null ? locationDto.getAddress().trim() : "")
                .coverages(coverages)
                .build();

        policy.getLocations().add(newLocation);
        policy.recalculateTotalPremium();
        policy.setUpdatedAt(Instant.now());

        log.info("Added location {} to policy {}", newLocationId, policy.getPolicyNumber());
        return policyRepository.save(policy);
    }

    @Override
    public Policy updateLocation(String policyIdOrNumber, Integer locationId, LocationDto locationDto) {
        Policy policy = findPolicy(policyIdOrNumber);
        validatePolicyModifiable(policy);

        Location existingLocation = findLocationInPolicy(policy, locationId);

        if (locationDto.getAddress() != null && !locationDto.getAddress().isBlank()) {
            existingLocation.setAddress(locationDto.getAddress().trim());
        }

        if (locationDto.getCoverages() != null) {
            List<Coverage> updatedCoverages = locationDto.getCoverages().stream()
                    .map(CoverageDto::toEntity)
                    .collect(Collectors.toList());
            existingLocation.setCoverages(updatedCoverages);
        }

        policy.recalculateTotalPremium();
        policy.setUpdatedAt(Instant.now());
        log.info("Updated location {} in policy {}", locationId, policy.getPolicyNumber());
        return policyRepository.save(policy);
    }

    @Override
    public Policy removeLocation(String policyIdOrNumber, Integer locationId) {
        Policy policy = findPolicy(policyIdOrNumber);
        validatePolicyModifiable(policy);

        if (policy.getLocations() == null || policy.getLocations().isEmpty()) {
            throw new ResourceNotFoundException("Location with ID " + locationId + " not found in policy " + policy.getPolicyNumber());
        }

        boolean removed = policy.getLocations().removeIf(loc -> locationId.equals(loc.getLocationId()));
        if (!removed) {
            throw new ResourceNotFoundException("Location with ID " + locationId + " not found in policy " + policy.getPolicyNumber());
        }

        policy.recalculateTotalPremium();
        policy.setUpdatedAt(Instant.now());
        log.info("Removed location {} from policy {}", locationId, policy.getPolicyNumber());
        return policyRepository.save(policy);
    }

    private Policy findPolicy(String policyIdOrNumber) {
        if (policyIdOrNumber == null || policyIdOrNumber.isBlank()) {
            throw new BadRequestException("Policy ID or policyNumber must be provided");
        }

        // Try MongoDB ObjectId / String ID first
        Optional<Policy> optionalPolicy = policyRepository.findById(policyIdOrNumber);
        if (optionalPolicy.isPresent()) {
            return optionalPolicy.get();
        }

        // Try business policyNumber
        return policyRepository.findByPolicyNumber(policyIdOrNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found with ID or PolicyNumber: " + policyIdOrNumber));
    }

    private Location findLocationInPolicy(Policy policy, Integer locationId) {
        if (locationId == null) {
            throw new BadRequestException("Location ID must be provided");
        }

        if (policy.getLocations() == null || policy.getLocations().isEmpty()) {
            throw new ResourceNotFoundException("No locations found in policy " + policy.getPolicyNumber());
        }

        return policy.getLocations().stream()
                .filter(loc -> locationId.equals(loc.getLocationId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Location with ID " + locationId + " not found in policy " + policy.getPolicyNumber()));
    }

    private void validatePolicyModifiable(Policy policy) {
        if (policy.getStatus() == PolicyStatus.CANCELLED || policy.getStatus() == PolicyStatus.EXPIRED) {
            throw new BadRequestException("Cannot modify locations on a " + policy.getStatus() + " policy");
        }
    }
}
