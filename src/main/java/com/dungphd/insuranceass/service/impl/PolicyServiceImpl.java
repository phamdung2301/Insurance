package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.CoverageDto;
import com.dungphd.insuranceass.dto.InsuredDto;
import com.dungphd.insuranceass.dto.LocationDto;
import com.dungphd.insuranceass.dto.request.CreatePolicyRequest;
import com.dungphd.insuranceass.dto.request.PolicySearchCriteria;
import com.dungphd.insuranceass.dto.request.StatusTransitionRequest;
import com.dungphd.insuranceass.dto.request.UpdatePolicyRequest;
import com.dungphd.insuranceass.dto.response.PageResponse;
import com.dungphd.insuranceass.dto.response.PolicyResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.DuplicateResourceException;
import com.dungphd.insuranceass.exception.InvalidRequestException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.*;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.PolicyTransactionRepository;
import com.dungphd.insuranceass.service.PolicyService;
import com.dungphd.insuranceass.service.PremiumCalculationService;
import com.dungphd.insuranceass.service.TransactionLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyServiceImpl implements PolicyService {

    private final PolicyRepository policyRepository;
    private final MongoTemplate mongoTemplate;
    private final PremiumCalculationService premiumCalculationService;
    private final PolicyTransactionRepository policyTransactionRepository;
    private final TransactionLedgerService transactionLedgerService;

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;

    public static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt",
            "updatedAt",
            "policyNumber",
            "status",
            "totalPremium",
            "effectiveDate",
            "expirationDate",
            "boundDate",
            "paymentDueDate",
            "version"
    );

    @Override
    public PolicyResponse createPolicy(CreatePolicyRequest request) {
        if (policyRepository.existsByPolicyNumber(request.getPolicyNumber())) {
            throw new DuplicateResourceException("Policy with number " + request.getPolicyNumber() + " already exists");
        }
        Instant effectiveDate = request.getEffectiveDate() != null ? request.getEffectiveDate() : Instant.now();
        Instant expirationDate = request.getExpirationDate() != null ? request.getExpirationDate() : effectiveDate.plus(365, java.time.temporal.ChronoUnit.DAYS);

        if (effectiveDate.isAfter(expirationDate)) {
            throw new InvalidRequestException("Effective date must be before expiration date");
        }
        Policy policy = Policy.builder()
                .policyNumber(request.getPolicyNumber())
                .status(PolicyStatus.DRAFT)
                .insured(mapToInsured(request.getInsured()))
                .locations(request.getLocations() != null ?
                        request.getLocations().stream().map(this::mapToLocation).collect(Collectors.toList()) : new ArrayList<>())
                .effectiveDate(effectiveDate)
                .expirationDate(expirationDate)
                .version(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        policy.recalculateTotalPremium();
        Policy savedPolicy = policyRepository.save(policy);

        transactionLedgerService.recordTransaction(
                savedPolicy.getPolicyNumber(),
                savedPolicy.getVersion(),
                TransactionType.CREATE_POLICY,
                "System",
                "Created new insurance policy in DRAFT status"
        );

        return mapToPolicyResponse(savedPolicy);
    }

    @Override
    public Policy createPolicy(Policy policy) {
        if (policy == null) {
            throw new BadRequestException("Policy data cannot be null");
        }

        if (policy.getPolicyNumber() == null || policy.getPolicyNumber().isBlank()) {
            policy.setPolicyNumber("POL-" + System.currentTimeMillis());
        }

        if (policyRepository.existsByPolicyNumber(policy.getPolicyNumber())) {
            throw new BadRequestException("Policy with number " + policy.getPolicyNumber() + " already exists");
        }

        if (policy.getStatus() == null) {
            policy.setStatus(PolicyStatus.DRAFT);
        }

        policy.recalculateTotalPremium();
        return policyRepository.save(policy);
    }

    @Override
    public PolicyResponse getPolicyByNumber(String policyNumber) {
        Policy policy = policyRepository.findByPolicyNumber(policyNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found with number: " + policyNumber));
        return mapToPolicyResponse(policy);
    }

    @Override
    public Policy getPolicyById(String policyIdOrNumber) {
        if (policyIdOrNumber == null || policyIdOrNumber.isBlank()) {
            throw new BadRequestException("Policy ID or Number must be provided");
        }

        return policyRepository.findById(policyIdOrNumber)
                .or(() -> policyRepository.findByPolicyNumber(policyIdOrNumber))
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found with ID or PolicyNumber: " + policyIdOrNumber));
    }

    @Override
    public PageResponse<Policy> searchPolicies(PolicySearchCriteria criteria) {
        if (criteria == null) {
            criteria = new PolicySearchCriteria();
        }

        List<Criteria> criteriaList = new ArrayList<>();

        if (criteria.getStatus() != null) {
            criteriaList.add(Criteria.where("status").is(criteria.getStatus()));
        }

        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            String sanitized = sanitizePattern(criteria.getKeyword());
            Criteria keywordCriteria = new Criteria().orOperator(
                    Criteria.where("policyNumber").regex(sanitized, "i"),
                    Criteria.where("insured.name").regex(sanitized, "i"),
                    Criteria.where("insured.email").regex(sanitized, "i"),
                    Criteria.where("insured.phone").regex(sanitized, "i"),
                    Criteria.where("locations.address").regex(sanitized, "i")
            );
            criteriaList.add(keywordCriteria);
        }

        if (criteria.getInsuredName() != null && !criteria.getInsuredName().isBlank()) {
            String sanitized = sanitizePattern(criteria.getInsuredName());
            criteriaList.add(Criteria.where("insured.name").regex(sanitized, "i"));
        }

        if (criteria.getLocation() != null && !criteria.getLocation().isBlank()) {
            String sanitized = sanitizePattern(criteria.getLocation());
            criteriaList.add(Criteria.where("locations.address").regex(sanitized, "i"));
        }

        if (criteria.getPolicyNumber() != null && !criteria.getPolicyNumber().isBlank()) {
            String sanitized = sanitizePattern(criteria.getPolicyNumber());
            criteriaList.add(Criteria.where("policyNumber").regex(sanitized, "i"));
        }

        // --- $elemMatch Query on Nested Locations & Coverages (Index-Tuned) ---
        if (criteria.getCoverageCode() != null && !criteria.getCoverageCode().isBlank()) {
            String covCode = sanitizeInput(criteria.getCoverageCode());
            if (criteria.getCoverageType() != null && !criteria.getCoverageType().isBlank()) {
                String covType = sanitizePattern(criteria.getCoverageType());
                criteriaList.add(Criteria.where("locations").elemMatch(
                        Criteria.where("coverages").elemMatch(
                                Criteria.where("coverageCode").is(covCode)
                                        .and("coverageType").regex(covType, "i")
                        )
                ));
            } else {
                criteriaList.add(Criteria.where("locations").elemMatch(
                        Criteria.where("coverages").elemMatch(
                                Criteria.where("coverageCode").is(covCode)
                        )
                ));
            }
        } else if (criteria.getCoverageType() != null && !criteria.getCoverageType().isBlank()) {
            String covType = sanitizePattern(criteria.getCoverageType());
            criteriaList.add(Criteria.where("locations").elemMatch(
                    Criteria.where("coverages").elemMatch(
                            Criteria.where("coverageType").regex(covType, "i")
                    )
            ));
        }

        if (criteria.getEffectiveDateFrom() != null && criteria.getEffectiveDateTo() != null) {
            criteriaList.add(Criteria.where("effectiveDate")
                    .gte(criteria.getEffectiveDateFrom())
                    .lte(criteria.getEffectiveDateTo()));
        } else if (criteria.getEffectiveDateFrom() != null) {
            criteriaList.add(Criteria.where("effectiveDate").gte(criteria.getEffectiveDateFrom()));
        } else if (criteria.getEffectiveDateTo() != null) {
            criteriaList.add(Criteria.where("effectiveDate").lte(criteria.getEffectiveDateTo()));
        }

        if (criteria.getMinPremium() != null && criteria.getMaxPremium() != null) {
            criteriaList.add(Criteria.where("totalPremium")
                    .gte(criteria.getMinPremium())
                    .lte(criteria.getMaxPremium()));
        } else if (criteria.getMinPremium() != null) {
            criteriaList.add(Criteria.where("totalPremium").gte(criteria.getMinPremium()));
        } else if (criteria.getMaxPremium() != null) {
            criteriaList.add(Criteria.where("totalPremium").lte(criteria.getMaxPremium()));
        }

        Query query = new Query();
        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        int page = Math.max(criteria.getPage(), 0);
        int rawSize = criteria.getSize() > 0 ? criteria.getSize() : DEFAULT_PAGE_SIZE;
        int size = Math.min(rawSize, MAX_PAGE_SIZE);

        String sortBy = "updatedAt";
        if (criteria.getSortBy() != null && !criteria.getSortBy().isBlank()) {
            String requestedSort = criteria.getSortBy().trim();
            if (!ALLOWED_SORT_FIELDS.contains(requestedSort)) {
                throw new BadRequestException("Invalid sortBy field: '" + requestedSort + "'. Allowed sort fields are: " + ALLOWED_SORT_FIELDS);
            }
            sortBy = requestedSort;
        }

        Sort.Direction direction = "ASC".equalsIgnoreCase(criteria.getSortDirection())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        long totalElements = mongoTemplate.count(query, Policy.class);
        query.with(pageable);

        List<Policy> policies = mongoTemplate.find(query, Policy.class);
        Page<Policy> pageResult = new PageImpl<>(policies, pageable, totalElements);

        log.debug("Found {} policies matching search criteria (page {}/{})", totalElements, page, pageResult.getTotalPages());
        return PageResponse.fromPage(pageResult);
    }

    @Override
    public PolicyResponse updatePolicy(String policyNumber, UpdatePolicyRequest request) {
        Policy policy = policyRepository.findByPolicyNumber(policyNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found with number: " + policyNumber));

        if (policy.getStatus() == PolicyStatus.ACTIVE) {
            throw new InvalidRequestException("Direct modifications are not allowed on ACTIVE policies. Please use the Endorsement process to preserve historical integrity.");
        }
        if (policy.getStatus() == PolicyStatus.CANCELLED || policy.getStatus() == PolicyStatus.EXPIRED) {
            throw new InvalidRequestException("Cannot modify policy in " + policy.getStatus() + " status.");
        }

        if (request.getInsured() != null) {
            policy.setInsured(mapToInsured(request.getInsured()));
        }
        if (request.getEffectiveDate() != null) {
            policy.setEffectiveDate(request.getEffectiveDate());
        }
        if (request.getExpirationDate() != null) {
            policy.setExpirationDate(request.getExpirationDate());
        }

        if (policy.getEffectiveDate() != null && policy.getExpirationDate() != null
                && policy.getEffectiveDate().isAfter(policy.getExpirationDate())) {
            throw new InvalidRequestException("Effective date must be before expiration date");
        }

        policy.setVersion(policy.getVersion() != null ? policy.getVersion() + 1 : 2);
        policy.setUpdatedAt(Instant.now());
        policy.recalculateTotalPremium();
        Policy updatedPolicy = policyRepository.save(policy);

        transactionLedgerService.recordTransaction(
                policyNumber,
                policy.getVersion(),
                TransactionType.UPDATE_POLICY,
                "System",
                "Updated policy terms in " + policy.getStatus() + " status"
        );

        return mapToPolicyResponse(updatedPolicy);
    }

    @Override
    public void deletePolicy(String policyNumber) {
        Policy policy = policyRepository.findByPolicyNumber(policyNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found with number: " + policyNumber));

        if (policy.getStatus() != PolicyStatus.DRAFT) {
            throw new InvalidRequestException("Only policies in DRAFT status can be deleted. Current status is " + policy.getStatus());
        }

        transactionLedgerService.recordTransaction(
                policyNumber,
                policy.getVersion(),
                TransactionType.CANCEL_POLICY,
                "System",
                "Deleted policy in DRAFT status"
        );

        policyRepository.delete(policy);
    }

    @Override
    public PolicyResponse transitionStatus(String policyNumber, StatusTransitionRequest request, String actor) {
        Policy policy = policyRepository.findByPolicyNumber(policyNumber)
                .or(() -> policyRepository.findById(policyNumber))
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found with number or ID: " + policyNumber));

        PolicyStatus currentStatus = policy.getStatus() != null ? policy.getStatus() : PolicyStatus.DRAFT;
        PolicyStatus targetStatus = request.getTargetStatus();

        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new BadRequestException("Invalid state transition from " + currentStatus + " to " + targetStatus);
        }

        // Rule for QUOTED: must have at least 1 Location and each Location must have at least 1 Coverage
        if (targetStatus == PolicyStatus.QUOTED) {
            if (policy.getLocations() == null || policy.getLocations().isEmpty()) {
                throw new BadRequestException("Policy must contain at least 1 location to transition to QUOTED");
            }
            boolean allHaveCoverages = policy.getLocations().stream()
                    .allMatch(loc -> loc.getCoverages() != null && !loc.getCoverages().isEmpty());
            if (!allHaveCoverages) {
                throw new BadRequestException("Each location in policy must have at least 1 coverage to transition to QUOTED");
            }
        }

        // Set boundDate and paymentDueDate when BOUND
        if (targetStatus == PolicyStatus.BOUND) {
            policy.setBoundDate(Instant.now());
            Instant dueDate = request.getPaymentDueDate() != null
                    ? request.getPaymentDueDate()
                    : Instant.now().plus(7, java.time.temporal.ChronoUnit.DAYS);
            policy.setPaymentDueDate(dueDate);
        }

        // Set effectiveDate and expirationDate when ACTIVE
        if (targetStatus == PolicyStatus.ACTIVE) {
            Instant effective = policy.getEffectiveDate() != null ? policy.getEffectiveDate() : Instant.now();
            policy.setEffectiveDate(effective);
            policy.setExpirationDate(effective.plus(365, java.time.temporal.ChronoUnit.DAYS));
        }

        policy.setStatus(targetStatus);
        policy.setUpdatedAt(Instant.now());
        Policy savedPolicy = policyRepository.save(policy);

        String desc = "Status transitioned from " + currentStatus + " to " + targetStatus
                + (request.getReason() != null && !request.getReason().isBlank() ? ": " + request.getReason() : "");
        transactionLedgerService.recordTransaction(
                savedPolicy.getPolicyNumber(),
                savedPolicy.getVersion(),
                TransactionType.STATUS_TRANSITION,
                actor,
                desc
        );

        log.info("Transitioned policy {} from {} to {}", savedPolicy.getPolicyNumber(), currentStatus, targetStatus);
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

    private String sanitizePattern(String input) {
        if (input == null) return "";
        String trimmed = input.trim();
        if (trimmed.length() > 100) {
            trimmed = trimmed.substring(0, 100);
        }
        return Pattern.quote(trimmed);
    }

    private String sanitizeInput(String input) {
        if (input == null) return "";
        String trimmed = input.trim();
        if (trimmed.length() > 100) {
            trimmed = trimmed.substring(0, 100);
        }
        return trimmed;
    }
}
