package com.dungphd.insuranceass.service.Impl;

import com.dungphd.insuranceass.dto.CoverageDto;
import com.dungphd.insuranceass.dto.InsuredDto;
import com.dungphd.insuranceass.dto.LocationDto;
import com.dungphd.insuranceass.dto.request.CreatePolicyRequest;
import com.dungphd.insuranceass.dto.response.PolicyResponse;
import com.dungphd.insuranceass.exception.DuplicateResourceException;
import com.dungphd.insuranceass.exception.InvalidRequestException;
import com.dungphd.insuranceass.model.Coverage;
import com.dungphd.insuranceass.model.Insured;
import com.dungphd.insuranceass.model.Location;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.PolicyTransactionRepository;
import com.dungphd.insuranceass.service.PolicyService;
import com.dungphd.insuranceass.service.PremiumCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PolicyServiceImpl implements PolicyService {

    private final PolicyRepository policyRepository;
    private final PremiumCalculationService premiumCalculationService;
    private final PolicyTransactionRepository policyTransactionRepository;

    @Override
    public PolicyResponse createPolicy(CreatePolicyRequest request) {
        if (policyRepository.existsByPolicyNumber(request.getPolicyNumber())) {
            throw new DuplicateResourceException("Policy with number " + request.getPolicyNumber() + " already exists");
        }
        if (request.getEffectiveDate() != null && request.getExpirationDate() != null
                && request.getEffectiveDate().isAfter(request.getExpirationDate())) {
            throw new InvalidRequestException("Effective date must be before expiration date");
        }
        Policy policy = Policy.builder()
                .policyNumber(request.getPolicyNumber())
                .status(PolicyStatus.DRAFT)
                .insured(mapToInsured(request.getInsured()))
                .locations(request.getLocations() != null ?
                        request.getLocations().stream().map(this::mapToLocation).collect(Collectors.toList()) : new ArrayList<>())
                .effectiveDate(request.getEffectiveDate())
                .expirationDate(request.getExpirationDate())
                .version(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        policy.recalculateTotalPremium();
        Policy savedPolicy = policyRepository.save(policy);
        return mapToPolicyResponse(savedPolicy);
    }

    private PolicyResponse mapToPolicyResponse(Policy policy) {
        return PolicyResponse.builder()
                .id(policy.getId())
                .policyNumber(policy.getPolicyNumber())
                .status(policy.getStatus())
                .insured(mapToInsuredDto(policy.getInsured()))
                .locations(policy.getLocations() != null ?
                        policy.getLocations().stream().map(this::mapToLocationDto).collect(Collectors.toList()) : new ArrayList<>())
                .totalPremium(policy.getTotalPremium())
                .effectiveDate(policy.getEffectiveDate())
                .expirationDate(policy.getExpirationDate())
                .boundDate(policy.getBoundDate())
                .paymentDueDate(policy.getPaymentDueDate())
                .version(policy.getVersion())
                .createdAt(policy.getCreatedAt())
                .updatedAt(policy.getUpdatedAt())
                .build();
    }

    private Insured mapToInsured(InsuredDto dto) {
        if (dto == null) return null;
        return Insured.builder()
                .insuredId(dto.getInsuredId() != null && !dto.getInsuredId().isBlank() ? dto.getInsuredId() : "INS-" + (1000 + (int)(Math.random() * 9000)))
                .name(dto.getName())
                .type(dto.getType() != null && !dto.getType().isBlank() ? dto.getType() : "BUSINESS")
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .address(dto.getAddress())
                .build();
    }

    private InsuredDto mapToInsuredDto(Insured entity) {
        if (entity == null) return null;
        return InsuredDto.builder()
                .insuredId(entity.getInsuredId())
                .name(entity.getName())
                .type(entity.getType() != null ? entity.getType() : "BUSINESS")
                .email(entity.getEmail())
                .phone(entity.getPhone())
                .address(entity.getAddress())
                .build();
    }

    private Location mapToLocation(LocationDto dto) {
        if (dto == null) return null;
        return Location.builder()
                .locationId(dto.getLocationId())
                .address(dto.getAddress())
                .coverages(dto.getCoverages() != null ?
                        dto.getCoverages().stream().map(this::mapToCoverage).collect(Collectors.toList()) : new ArrayList<>())
                .build();
    }

    private LocationDto mapToLocationDto(Location entity) {
        if (entity == null) return null;
        return LocationDto.builder()
                .locationId(entity.getLocationId())
                .address(entity.getAddress())
                .coverages(entity.getCoverages() != null ?
                        entity.getCoverages().stream().map(this::mapToCoverageDto).collect(Collectors.toList()) : new ArrayList<>())
                .build();
    }

    private Coverage mapToCoverage(CoverageDto dto) {
        if (dto == null) return null;

        String name = (dto.getCoverageName() != null && !dto.getCoverageName().isBlank())
                ? dto.getCoverageName()
                : (dto.getCoverageCode() != null ? dto.getCoverageCode() : "Coverage");

        Coverage coverage = Coverage.builder()
                .coverageCode(dto.getCoverageCode())
                .coverageName(name)
                .coverageType(dto.getCoverageType() != null ? dto.getCoverageType() : "STANDARD")
                .limit(dto.getLimit())
                .deductible(dto.getDeductible())
                .termMonths(dto.getTermMonths() != null ? dto.getTermMonths() : 12)
                .baseRate(dto.getBaseRate())
                .build();

        if (dto.getPremium() != null && dto.getPremium() > 0 && dto.getBaseRate() != null) {
            coverage.setPremium(dto.getPremium());
        } else {
            coverage.setPremium(premiumCalculationService.calculate(coverage));
        }

        return coverage;
    }

    private CoverageDto mapToCoverageDto(Coverage entity) {
        if (entity == null) return null;
        return CoverageDto.builder()
                .coverageCode(entity.getCoverageCode())
                .coverageName(entity.getCoverageName())
                .coverageType(entity.getCoverageType())
                .limit(entity.getLimit())
                .deductible(entity.getDeductible())
                .termMonths(entity.getTermMonths())
                .baseRate(entity.getBaseRate())
                .premium(entity.getPremium())
                .build();
    }
}
