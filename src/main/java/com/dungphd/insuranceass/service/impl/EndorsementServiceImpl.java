package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.CoverageDto;
import com.dungphd.insuranceass.dto.InsuredDto;
import com.dungphd.insuranceass.dto.LocationDto;
import com.dungphd.insuranceass.dto.request.EndorsementRequest;
import com.dungphd.insuranceass.dto.response.PolicyResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.PendingEndorsementException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.*;
import com.dungphd.insuranceass.repository.PendingEndorsementRepository;
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

    /**
     * Đợt 4B — Ngưỡng maker-checker: |phí mới - phí cũ| / phí cũ vượt số này
     * thì endorsement KHÔNG apply ngay mà lưu PendingEndorsement chờ UW duyệt.
     * USER CHỈ CẦN ĐỔI 1 SỐ NÀY là xong (ví dụ 0.10 = 10%, 0.30 = 30%).
     * So sánh: pct <= ngưỡng thì apply ngay (biên 20% đúng vẫn apply ngay).
     */
    public static final double PREMIUM_CHANGE_THRESHOLD = 0.20;

    private final PolicyRepository policyRepository;
    private final PolicyVersionRepository policyVersionRepository;
    private final TransactionLedgerService transactionLedgerService;
    private final PremiumCalculationService premiumCalculationService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.dungphd.insuranceass.service.PermissionService permissionService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private NotificationService notificationService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PendingEndorsementRepository pendingEndorsementRepository;

    @Override
    public PolicyResponse applyEndorsement(String policyNumber, EndorsementRequest request, String actor) {
        return applyEndorsementInternal(policyNumber, request, actor, false);
    }

    @Override
    public PolicyResponse applyEndorsementBypassingThreshold(String policyNumber, EndorsementRequest request, String actor) {
        return applyEndorsementInternal(policyNumber, request, actor, true);
    }

    /**
     * Luồng apply duy nhất. bypassThreshold = true chỉ dùng cho checker duyệt
     * pending (đã qua enforce UW ở PendingEndorsementService): bỏ qua ngưỡng
     * VÀ bỏ qua checkPolicyWrite (UW không nằm trong phe cầu nên check đó sẽ
     * 403 oan); vẫn giữ check ACTIVE, snapshot, tính phí, notification, ledger.
     */
    private PolicyResponse applyEndorsementInternal(String policyNumber, EndorsementRequest request, String actor, boolean bypassThreshold) {
        Policy policy = policyRepository.findByPolicyNumber(policyNumber)
                .or(() -> policyRepository.findById(policyNumber))
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found with number or ID: " + policyNumber));

        if (!bypassThreshold && permissionService != null) {
            permissionService.checkPolicyWrite(policy);
        }

        if (policy.getStatus() != PolicyStatus.ACTIVE) {
            throw new BadRequestException("Endorsement can only be applied to ACTIVE policies. Current status is " + policy.getStatus());
        }

        // Đợt 4B — Maker-checker, TÍNH TRƯỚC KHI SAVE: build policy SAU
        // endorsement trên bản clone (chưa save, policy gốc chưa đổi), tính phí
        // dự kiến bằng đúng recalculateTotalPremium() như luồng apply thật
        // (mapToLocation đã tính premium cho coverage thiếu premium qua
        // PremiumCalculationService, giống hệt code cũ). Chỉ khi quyết định
        // apply mới chạm tới policy gốc + snapshot + ledger bên dưới.
        if (!bypassThreshold) {
            Policy projected = buildProjectedPolicy(policy, request);
            double oldPremium = policy.getTotalPremium() != null ? policy.getTotalPremium() : 0.0;
            double projectedPremium = projected.getTotalPremium() != null ? projected.getTotalPremium() : 0.0;
            double premiumChangePct = computePremiumChangePct(oldPremium, projectedPremium);
            if (premiumChangePct > PREMIUM_CHANGE_THRESHOLD) {
                // KHÔNG apply, KHÔNG đổi policy, KHÔNG snapshot, KHÔNG ledger.
                // Chỉ lưu pending; caller nhận biết qua PendingEndorsementException
                // -> HTTP 202 (xem GlobalExceptionHandler), không phải 200 + PolicyResponse.
                PendingEndorsement pending = PendingEndorsement.builder()
                        .policyNumber(policy.getPolicyNumber())
                        .request(request)
                        .requestedBy(actor != null && !actor.isBlank() ? actor.trim() : "System")
                        .requestedAt(Instant.now())
                        .premiumChangePct(premiumChangePct)
                        .oldPremium(oldPremium)
                        .projectedPremium(projectedPremium)
                        .status(PendingEndorsementStatus.PENDING)
                        .build();
                if (pendingEndorsementRepository != null) {
                    pending = pendingEndorsementRepository.save(pending);
                }
                log.info("Endorsement on policy {} exceeds threshold (pct={}, old={}, projected={}) -> pending {}",
                        policy.getPolicyNumber(), premiumChangePct, oldPremium, projectedPremium, pending.getId());
                throw new PendingEndorsementException(pending.getId(), policy.getPolicyNumber(), premiumChangePct);
            }
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

        // 2. Apply modifications from EndorsementRequest (cùng helper mà bước
        // tính phí dự kiến dùng trên bản clone, nên phí dự kiến = phí thật).
        applyRequestToPolicy(policy, request);

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

    /**
     * Áp đúng các sửa đổi của EndorsementRequest lên một policy (gốc khi apply
     * thật, clone khi tính phí dự kiến). Tách ra từ code cũ, không đổi logic.
     */
    private void applyRequestToPolicy(Policy policy, EndorsementRequest request) {
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
    }

    /** Policy SAU endorsement nhưng CHƯA save — chỉ để tính phí dự kiến. */
    private Policy buildProjectedPolicy(Policy policy, EndorsementRequest request) {
        Policy projected = clonePolicy(policy);
        applyRequestToPolicy(projected, request);
        projected.recalculateTotalPremium();
        return projected;
    }

    /**
     * premiumChangePct = |phí mới - phí cũ| / phí cũ (làm tròn 4 chữ số).
     * Phí cũ = 0: phí mới = 0 thì 0.0, phí mới khác 0 thì 1.0 (coi như vượt
     * ngưỡng, vì không chia được cho 0; không dùng Infinity để JSON/DB an toàn).
     */
    static double computePremiumChangePct(double oldPremium, double newPremium) {
        double pct;
        if (oldPremium == 0.0) {
            pct = (newPremium == 0.0) ? 0.0 : 1.0;
        } else {
            pct = Math.abs(newPremium - oldPremium) / Math.abs(oldPremium);
        }
        return Math.round(pct * 10000.0) / 10000.0;
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
