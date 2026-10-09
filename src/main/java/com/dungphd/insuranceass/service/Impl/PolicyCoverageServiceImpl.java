package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.CoverageDto;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.Coverage;
import com.dungphd.insuranceass.model.Location;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.service.PolicyCoverageService;
import com.dungphd.insuranceass.service.PremiumCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyCoverageServiceImpl implements PolicyCoverageService {

    private final PolicyRepository policyRepository;
    private final PremiumCalculationService premiumCalculationService;

    @Override
    public List<Coverage> getCoverages(String policyIdOrNumber, String locationId) {
        Policy policy = findPolicy(policyIdOrNumber);
        Location location = findLocationInPolicy(policy, locationId);
        return location.getCoverages() != null ? location.getCoverages() : Collections.emptyList();
    }

    @Override
    public Coverage getCoverageByCode(String policyIdOrNumber, String locationId, String coverageCode) {
        Policy policy = findPolicy(policyIdOrNumber);
        Location location = findLocationInPolicy(policy, locationId);
        return findCoverageInLocation(location, coverageCode);
    }

    @Override
    public Policy addCoverage(String policyIdOrNumber, String locationId, CoverageDto coverageDto) {
        Policy policy = findPolicy(policyIdOrNumber);
        validatePolicyModifiable(policy);

        Location location = findLocationInPolicy(policy, locationId);
        if (location.getCoverages() == null) {
            location.setCoverages(new ArrayList<>());
        }

        String code = coverageDto.getCoverageCode() != null ? coverageDto.getCoverageCode().trim() : "";
        if (code.isBlank()) {
            throw new BadRequestException("Coverage code cannot be empty");
        }

        boolean exists = location.getCoverages().stream()
                .anyMatch(c -> code.equalsIgnoreCase(c.getCoverageCode()));
        if (exists) {
            throw new BadRequestException("Coverage with code " + code + " already exists in location " + locationId);
        }

        Coverage coverage = coverageDto.toEntity();
        if (coverage.getPremium() == null || coverage.getPremium() <= 0) {
            double calculatedPremium = premiumCalculationService.calculate(coverage);
            coverage.setPremium(calculatedPremium);
        }

        location.getCoverages().add(coverage);
        policy.recalculateTotalPremium();
        policy.setUpdatedAt(Instant.now());

        log.info("Added coverage {} to location {} on policy {}", code, locationId, policy.getPolicyNumber());
        return policyRepository.save(policy);
    }

    @Override
    public Policy updateCoverage(String policyIdOrNumber, String locationId, String coverageCode, CoverageDto coverageDto) {
        Policy policy = findPolicy(policyIdOrNumber);
        validatePolicyModifiable(policy);

        Location location = findLocationInPolicy(policy, locationId);
        Coverage existingCoverage = findCoverageInLocation(location, coverageCode);

        if (coverageDto.getCoverageName() != null && !coverageDto.getCoverageName().isBlank()) {
            existingCoverage.setCoverageName(coverageDto.getCoverageName().trim());
        }
        if (coverageDto.getCoverageType() != null && !coverageDto.getCoverageType().isBlank()) {
            existingCoverage.setCoverageType(coverageDto.getCoverageType().trim());
        }
        if (coverageDto.getLimit() != null) {
            existingCoverage.setLimit(coverageDto.getLimit());
        }
        if (coverageDto.getDeductible() != null) {
            existingCoverage.setDeductible(coverageDto.getDeductible());
        }
        if (coverageDto.getTermMonths() != null) {
            existingCoverage.setTermMonths(coverageDto.getTermMonths());
        }
        if (coverageDto.getBaseRate() != null) {
            existingCoverage.setBaseRate(coverageDto.getBaseRate());
        }

        if (coverageDto.getPremium() != null && coverageDto.getPremium() > 0) {
            existingCoverage.setPremium(coverageDto.getPremium());
        } else {
            existingCoverage.setPremium(premiumCalculationService.calculate(existingCoverage));
        }

        policy.recalculateTotalPremium();
        policy.setUpdatedAt(Instant.now());

        log.info("Updated coverage {} in location {} on policy {}", coverageCode, locationId, policy.getPolicyNumber());
        return policyRepository.save(policy);
    }

    @Override
    public Policy removeCoverage(String policyIdOrNumber, String locationId, String coverageCode) {
        Policy policy = findPolicy(policyIdOrNumber);
        validatePolicyModifiable(policy);

        Location location = findLocationInPolicy(policy, locationId);
        if (location.getCoverages() == null || location.getCoverages().isEmpty()) {
            throw new ResourceNotFoundException("Coverage with code " + coverageCode + " not found in location " + locationId);
        }

        boolean removed = location.getCoverages().removeIf(c -> coverageCode.equalsIgnoreCase(c.getCoverageCode()));
        if (!removed) {
            throw new ResourceNotFoundException("Coverage with code " + coverageCode + " not found in location " + locationId);
        }

        policy.recalculateTotalPremium();
        policy.setUpdatedAt(Instant.now());

        log.info("Removed coverage {} from location {} on policy {}", coverageCode, locationId, policy.getPolicyNumber());
        return policyRepository.save(policy);
    }

    private Policy findPolicy(String policyIdOrNumber) {
        if (policyIdOrNumber == null || policyIdOrNumber.isBlank()) {
            throw new BadRequestException("Policy ID or policyNumber must be provided");
        }

        Optional<Policy> optionalPolicy = policyRepository.findById(policyIdOrNumber);
        if (optionalPolicy.isPresent()) {
            return optionalPolicy.get();
        }

        return policyRepository.findByPolicyNumber(policyIdOrNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found with ID or PolicyNumber: " + policyIdOrNumber));
    }

    private Location findLocationInPolicy(Policy policy, String locationId) {
        if (locationId == null || locationId.isBlank()) {
            throw new BadRequestException("Location ID must be provided");
        }

        if (policy.getLocations() == null || policy.getLocations().isEmpty()) {
            throw new ResourceNotFoundException("No locations found in policy " + policy.getPolicyNumber());
        }

        return policy.getLocations().stream()
                .filter(loc -> matchLocationId(loc, locationId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Location with ID " + locationId + " not found in policy " + policy.getPolicyNumber()));
    }

    private boolean matchLocationId(Location loc, String targetId) {
        if (loc.getLocationId() == null) return false;
        String locStr = String.valueOf(loc.getLocationId());
        return locStr.equalsIgnoreCase(targetId.trim());
    }

    private Coverage findCoverageInLocation(Location location, String coverageCode) {
        if (coverageCode == null || coverageCode.isBlank()) {
            throw new BadRequestException("Coverage code must be provided");
        }

        if (location.getCoverages() == null || location.getCoverages().isEmpty()) {
            throw new ResourceNotFoundException("No coverages found in location " + location.getLocationId());
        }

        return location.getCoverages().stream()
                .filter(c -> coverageCode.equalsIgnoreCase(c.getCoverageCode()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Coverage with code " + coverageCode + " not found in location " + location.getLocationId()));
    }

    private void validatePolicyModifiable(Policy policy) {
        if (policy.getStatus() == PolicyStatus.CANCELLED || policy.getStatus() == PolicyStatus.EXPIRED) {
            throw new BadRequestException("Cannot modify coverages on a " + policy.getStatus() + " policy");
        }
    }
}
