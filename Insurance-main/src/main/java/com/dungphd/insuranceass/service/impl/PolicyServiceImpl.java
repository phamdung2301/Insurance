package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.request.PolicySearchCriteria;
import com.dungphd.insuranceass.dto.response.PageResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.service.PolicyService;
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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyServiceImpl implements PolicyService {

    private final PolicyRepository policyRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public PageResponse<Policy> searchPolicies(PolicySearchCriteria criteria) {
        if (criteria == null) {
            criteria = new PolicySearchCriteria();
        }

        List<Criteria> criteriaList = new ArrayList<>();

        // 1. Status Filter
        if (criteria.getStatus() != null) {
            criteriaList.add(Criteria.where("status").is(criteria.getStatus()));
        }

        // 2. Insured Name Filter (Case-insensitive partial match)
        if (criteria.getInsuredName() != null && !criteria.getInsuredName().isBlank()) {
            String sanitized = Pattern.quote(criteria.getInsuredName().trim());
            criteriaList.add(Criteria.where("insured.name").regex(sanitized, "i"));
        }

        // 3. Location Address Filter (Case-insensitive match inside nested locations array)
        if (criteria.getLocation() != null && !criteria.getLocation().isBlank()) {
            String sanitized = Pattern.quote(criteria.getLocation().trim());
            criteriaList.add(Criteria.where("locations.address").regex(sanitized, "i"));
        }

        // 4. Policy Number Filter
        if (criteria.getPolicyNumber() != null && !criteria.getPolicyNumber().isBlank()) {
            String sanitized = Pattern.quote(criteria.getPolicyNumber().trim());
            criteriaList.add(Criteria.where("policyNumber").regex(sanitized, "i"));
        }

        // 5. Effective Date Range Filter
        if (criteria.getEffectiveDateFrom() != null && criteria.getEffectiveDateTo() != null) {
            criteriaList.add(Criteria.where("effectiveDate")
                    .gte(criteria.getEffectiveDateFrom())
                    .lte(criteria.getEffectiveDateTo()));
        } else if (criteria.getEffectiveDateFrom() != null) {
            criteriaList.add(Criteria.where("effectiveDate").gte(criteria.getEffectiveDateFrom()));
        } else if (criteria.getEffectiveDateTo() != null) {
            criteriaList.add(Criteria.where("effectiveDate").lte(criteria.getEffectiveDateTo()));
        }

        // 6. Total Premium Range Filter
        if (criteria.getMinPremium() != null && criteria.getMaxPremium() != null) {
            criteriaList.add(Criteria.where("totalPremium")
                    .gte(criteria.getMinPremium())
                    .lte(criteria.getMaxPremium()));
        } else if (criteria.getMinPremium() != null) {
            criteriaList.add(Criteria.where("totalPremium").gte(criteria.getMinPremium()));
        } else if (criteria.getMaxPremium() != null) {
            criteriaList.add(Criteria.where("totalPremium").lte(criteria.getMaxPremium()));
        }

        // Construct Query
        Query query = new Query();
        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        // Pagination and Sorting
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
    public Policy getPolicyById(String policyIdOrNumber) {
        if (policyIdOrNumber == null || policyIdOrNumber.isBlank()) {
            throw new BadRequestException("Policy ID or Number must be provided");
        }

        return policyRepository.findById(policyIdOrNumber)
                .or(() -> policyRepository.findByPolicyNumber(policyIdOrNumber))
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found with ID or PolicyNumber: " + policyIdOrNumber));
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
}
