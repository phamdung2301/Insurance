package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.CoverageDto;
import com.dungphd.insuranceass.dto.InsuredDto;
import com.dungphd.insuranceass.dto.LocationDto;
import com.dungphd.insuranceass.dto.request.CreatePolicyRequest;
import com.dungphd.insuranceass.dto.request.PolicySearchCriteria;
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

        if (criteria.getInsuredName() != null && !criteria.getInsuredName().isBlank()) {
            String sanitized = Pattern.quote(criteria.getInsuredName().trim());
            criteriaList.add(Criteria.where("insured.name").regex(sanitized, "i"));
        }

        if (criteria.getLocation() != null && !criteria.getLocation().isBlank()) {
            String sanitized = Pattern.quote(criteria.getLocation().trim());
            criteriaList.add(Criteria.where("locations.address").regex(sanitized, "i"));
        }

        if (criteria.getPolicyNumber() != null && !criteria.getPolicyNumber().isBlank()) {
            String sanitized = Pattern.quote(criteria.getPolicyNumber().trim());
            criteriaList.add(Criteria.where("policyNumber").regex(sanitized, "i"));
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
        int size = criteria.getSize() > 0 ? criteria.getSize() : 10;
        String sortBy = criteria.getSortBy() != null && !criteria.getSortBy().isBlank()
                ? criteria.getSortBy()
                : "createdAt";
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

        PolicyTransaction txn = PolicyTransaction.builder()
                .policyNumber(policyNumber)
                .version(policy.getVersion())
                .transactionType(TransactionType.UPDATE_POLICY)
                .actor("System")
                .description("Updated policy terms in " + policy.getStatus() + " status")
                .timestamp(Instant.now())
                .build();
        policyTransactionRepository.save(txn);

        return mapToPolicyResponse(updatedPolicy);
    }

    @Override
    public void deletePolicy(String policyNumber) {
        Policy policy = policyRepository.findByPolicyNumber(policyNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found with number: " + policyNumber));

        if (policy.getStatus() != PolicyStatus.DRAFT) {
            throw new InvalidRequestException("Only policies in DRAFT status can be deleted. Current status is " + policy.getStatus());
        }
        policyRepository.delete(policy);
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
