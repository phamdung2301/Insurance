package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.CoverageDto;
import com.dungphd.insuranceass.dto.InsuredDto;
import com.dungphd.insuranceass.dto.LocationDto;
import com.dungphd.insuranceass.dto.request.EndorsementRequest;
import com.dungphd.insuranceass.dto.response.PolicyResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.*;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.PolicyVersionRepository;
import com.dungphd.insuranceass.service.EndorsementService;
import com.dungphd.insuranceass.service.NotificationService;
import com.dungphd.insuranceass.service.PremiumCalculationService;
import com.dungphd.insuranceass.service.TransactionLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EndorsementServiceImpl implements EndorsementService {

    private final PolicyRepository policyRepository;
    private final PolicyVersionRepository policyVersionRepository;
    private final TransactionLedgerService transactionLedgerService;
    private final PremiumCalculationService premiumCalculationService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.dungphd.insuranceass.service.PermissionService permissionService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private NotificationService notificationService;

    @Override
    public PolicyResponse applyEndorsement(String policyNumber, EndorsementRequest request, String actor) {
        Policy policy = policyRepository.findByPolicyNumber(policyNumber)
                .or(() -> policyRepository.findById(policyNumber))
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found with number or ID: " + policyNumber));

        if (permissionService != null) {
            permissionService.checkPolicyWrite(policy);
        }

        if (policy.getStatus() != PolicyStatus.ACTIVE) {
            throw new BadRequestException("Endorsement can only be applied to ACTIVE policies. Current status is " + policy.getStatus());
        }

        int currentVersion = policy.getVersion() != null ? policy.getVersion() : 1;

        // 1. Create and persist Snapshot of current state before applying changes
        Policy snapshot = clonePolicy(policy);
        PolicyVersion versionDoc = PolicyVersion.builder()
                .policyNumber(policy.getPolicyNumber())
                .version(currentVersion)
                .policySnapshot(snapshot)
                .createdBy(actor != null && !actor.isBlank() ? actor.trim() : "System")
                .createdAt(Instant.now())
                .build();
        policyVersionRepository.save(versionDoc);
        log.info("Saved snapshot for policy {} at version {}", policy.getPolicyNumber(), currentVersion);

        // 2. Apply modifications from EndorsementRequest
        if (request.getInsured() != null) {
            policy.setInsured(mapToInsured(request.getInsured()));
        }

        if (request.getLocations() != null) {
            List<Location> updatedLocations = request.getLocations().stream()
                    .map(this::mapToLocation)
                    .collect(Collectors.toList());
            policy.setLocations(updatedLocations);
        }

        if (request.getEffectiveDate() != null) {
            policy.setEffectiveDate(request.getEffectiveDate());
        }

        if (request.getExpirationDate() != null) {
            policy.setExpirationDate(request.getExpirationDate());
        }

        // 3. Increment version and recalculate total premium
        int newVersion = currentVersion + 1;
        policy.setVersion(newVersion);
        policy.recalculateTotalPremium();
        policy.setUpdatedAt(Instant.now());

        Policy savedPolicy = policyRepository.save(policy);

        // Hook đợt 2B: SAU khi endorsement đã save thành công.
        // NotificationService failure-isolated nên không cần try/catch thêm ở đây.
        if (notificationService != null) {
            notificationService.notifyEndorsementApplied(savedPolicy);
        }

        // 4. Record transaction in Ledger
        String desc = "Endorsement (v" + newVersion + "): " + request.getReason()
                + (request.getDescription() != null && !request.getDescription().isBlank() ? " - " + request.getDescription() : "");
        transactionLedgerService.recordTransaction(
                savedPolicy.getPolicyNumber(),
                newVersion,
                TransactionType.ENDORSEMENT,
                actor,
                desc
        );

        log.info("Successfully endorsed policy {} to version {}", savedPolicy.getPolicyNumber(), newVersion);
        return mapToPolicyResponse(savedPolicy);
    }

    @Override
    public List<PolicyVersion> getVersions(String policyNumber) {
        Policy policy = policyRepository.findByPolicyNumber(policyNumber)
                .or(() -> policyRepository.findById(policyNumber))
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found with number or ID: " + policyNumber));

        if (permissionService != null) {
            permissionService.checkPolicyRead(policy);
        }

        return policyVersionRepository.findByPolicyNumberOrderByVersionDesc(policy.getPolicyNumber());
    }

    @Override
    public PolicyVersion getVersion(String policyNumber, Integer version) {
        Policy policy = policyRepository.findByPolicyNumber(policyNumber)
                .or(() -> policyRepository.findById(policyNumber))
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found with number or ID: " + policyNumber));

        if (permissionService != null) {
            permissionService.checkPolicyRead(policy);
        }

        return policyVersionRepository.findByPolicyNumberAndVersion(policy.getPolicyNumber(), version)
                .orElseThrow(() -> new ResourceNotFoundException("Version snapshot " + version + " not found for policy " + policy.getPolicyNumber()));
    }

    @Override
    public Map<String, Object> compareVersions(String policyNumber, Integer v1, Integer v2) {
        Policy policy = policyRepository.findByPolicyNumber(policyNumber)
                .or(() -> policyRepository.findById(policyNumber))
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found with number or ID: " + policyNumber));

        if (permissionService != null) {
            permissionService.checkPolicyRead(policy);
        }

        Policy snap1 = resolvePolicyForVersion(policy, v1);
        Policy snap2 = resolvePolicyForVersion(policy, v2);

        double premium1 = snap1.getTotalPremium() != null ? snap1.getTotalPremium() : 0.0;
        double premium2 = snap2.getTotalPremium() != null ? snap2.getTotalPremium() : 0.0;
        double premiumDiff = Math.round((premium2 - premium1) * 100.0) / 100.0;

        int locCount1 = snap1.getLocations() != null ? snap1.getLocations().size() : 0;
        int locCount2 = snap2.getLocations() != null ? snap2.getLocations().size() : 0;

        int covCount1 = countCoverages(snap1);
        int covCount2 = countCoverages(snap2);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("policyNumber", policy.getPolicyNumber());
        result.put("version1", v1);
        result.put("version2", v2);
        result.put("totalPremium1", premium1);
        result.put("totalPremium2", premium2);
        result.put("premiumDifference", premiumDiff);
        result.put("locationCount1", locCount1);
        result.put("locationCount2", locCount2);
        result.put("coverageCount1", covCount1);
        result.put("coverageCount2", covCount2);
        result.put("snapshot1", snap1);
        result.put("snapshot2", snap2);

        return result;
    }

    private Policy resolvePolicyForVersion(Policy currentPolicy, Integer v) {
        if (v == null || v.equals(currentPolicy.getVersion())) {
            return clonePolicy(currentPolicy);
        }
        return policyVersionRepository.findByPolicyNumberAndVersion(currentPolicy.getPolicyNumber(), v)
                .map(PolicyVersion::getPolicySnapshot)
                .orElseThrow(() -> new ResourceNotFoundException("Version " + v + " not found for policy " + currentPolicy.getPolicyNumber()));
    }

    private int countCoverages(Policy p) {
        if (p == null || p.getLocations() == null) return 0;
        return p.getLocations().stream()
                .filter(loc -> loc.getCoverages() != null)
                .mapToInt(loc -> loc.getCoverages().size())
                .sum();
    }

    private Policy clonePolicy(Policy origin) {
        if (origin == null) return null;
        List<Location> clonedLocations = new ArrayList<>();
        if (origin.getLocations() != null) {
            for (Location loc : origin.getLocations()) {
                List<Coverage> clonedCovs = new ArrayList<>();
                if (loc.getCoverages() != null) {
                    for (Coverage c : loc.getCoverages()) {
                        clonedCovs.add(Coverage.builder()
                                .coverageCode(c.getCoverageCode())
                                .coverageName(c.getCoverageName())
                                .coverageType(c.getCoverageType())
                                .limit(c.getLimit())
                                .deductible(c.getDeductible())
                                .termMonths(c.getTermMonths())
                                .baseRate(c.getBaseRate())
                                .premium(c.getPremium())
                                .build());
                    }
                }
                clonedLocations.add(Location.builder()
                        .locationId(loc.getLocationId())
                        .address(loc.getAddress())
                        .coverages(clonedCovs)
                        .build());
            }
        }

        Insured clonedInsured = null;
        if (origin.getInsured() != null) {
            clonedInsured = Insured.builder()
                    .insuredId(origin.getInsured().getInsuredId())
                    .name(origin.getInsured().getName())
                    .type(origin.getInsured().getType())
                    .email(origin.getInsured().getEmail())
                    .phone(origin.getInsured().getPhone())
                    .address(origin.getInsured().getAddress())
                    .build();
        }

        return Policy.builder()
                .id(origin.getId())
                .policyNumber(origin.getPolicyNumber())
                .status(origin.getStatus())
                .insured(clonedInsured)
                .locations(clonedLocations)
                .totalPremium(origin.getTotalPremium())
                .assignedAgentEmail(origin.getAssignedAgentEmail())
                .negotiatedPremium(origin.getNegotiatedPremium())
                .paymentClaimedAt(origin.getPaymentClaimedAt())
                .paymentStatus(origin.getPaymentStatus())
                .effectiveDate(origin.getEffectiveDate())
                .expirationDate(origin.getExpirationDate())
                .boundDate(origin.getBoundDate())
                .paymentDueDate(origin.getPaymentDueDate())
                .version(origin.getVersion())
                .createdAt(origin.getCreatedAt())
                .updatedAt(origin.getUpdatedAt())
                .build();
    }

    private Insured mapToInsured(InsuredDto dto) {
        if (dto == null) return null;
        return Insured.builder()
                .insuredId(dto.getInsuredId() != null && !dto.getInsuredId().isBlank() ? dto.getInsuredId() : "INS-" + (1000 + (int)(Math.random() * 9000)))
                .name(dto.getName())
                .type(dto.getType() != null ? dto.getType() : "BUSINESS")
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .address(dto.getAddress())
                .build();
    }

    private Location mapToLocation(LocationDto dto) {
        if (dto == null) return null;
        List<Coverage> coverages = new ArrayList<>();
        if (dto.getCoverages() != null) {
            for (CoverageDto cdto : dto.getCoverages()) {
                Coverage c = cdto.toEntity();
                if (c.getPremium() == null || c.getPremium() <= 0) {
                    c.setPremium(premiumCalculationService.calculate(c));
                }
                coverages.add(c);
            }
        }
        return Location.builder()
                .locationId(dto.getLocationId())
                .address(dto.getAddress())
                .coverages(coverages)
                .build();
    }

    private PolicyResponse mapToPolicyResponse(Policy policy) {
        return PolicyResponse.builder()
                .id(policy.getId())
                .policyNumber(policy.getPolicyNumber())
                .status(policy.getStatus())
                .insured(policy.getInsured() != null ? InsuredDto.builder()
                        .insuredId(policy.getInsured().getInsuredId())
                        .name(policy.getInsured().getName())
                        .type(policy.getInsured().getType())
                        .email(policy.getInsured().getEmail())
                        .phone(policy.getInsured().getPhone())
                        .address(policy.getInsured().getAddress())
                        .build() : null)
                .locations(policy.getLocations() != null ? policy.getLocations().stream()
                        .map(LocationDto::fromEntity)
                        .collect(Collectors.toList()) : new ArrayList<>())
                .totalPremium(policy.getTotalPremium())
                .assignedAgentEmail(policy.getAssignedAgentEmail())
                .negotiatedPremium(policy.getNegotiatedPremium())
                .paymentClaimedAt(policy.getPaymentClaimedAt())
                .paymentStatus(policy.getPaymentStatus())
                .effectiveDate(policy.getEffectiveDate())
                .expirationDate(policy.getExpirationDate())
                .boundDate(policy.getBoundDate())
                .paymentDueDate(policy.getPaymentDueDate())
                .version(policy.getVersion())
                .createdAt(policy.getCreatedAt())
                .updatedAt(policy.getUpdatedAt())
                .build();
    }
}
