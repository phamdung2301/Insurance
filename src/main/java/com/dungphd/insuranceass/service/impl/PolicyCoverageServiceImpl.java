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

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.dungphd.insuranceass.service.TransactionLedgerService transactionLedgerService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.dungphd.insuranceass.service.PermissionService permissionService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.dungphd.insuranceass.repository.PriceNegotiationRepository negotiationRepository;

    @Override
    public List<Coverage> getCoverages(String policyIdOrNumber, String locationId) {
        Policy policy = findPolicy(policyIdOrNumber);
        checkRead(policy);
        Location location = findLocationInPolicy(policy, locationId);
        return location.getCoverages() != null ? location.getCoverages() : Collections.emptyList();
    }

    @Override
    public Coverage getCoverageByCode(String policyIdOrNumber, String locationId, String coverageCode) {
        Policy policy = findPolicy(policyIdOrNumber);
        checkRead(policy);
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
            throw new BadRequestException("Mã quyền lợi bảo hiểm không được để trống.");
        }

        boolean exists = location.getCoverages().stream()
                .anyMatch(c -> code.equalsIgnoreCase(c.getCoverageCode()));
        if (exists) {
            throw new BadRequestException("Quyền lợi với mã " + code + " đã tồn tại tại địa điểm " + locationId);
        }

        Coverage coverage = coverageDto.toEntity();
        // Giá do SERVER quyết định: bỏ qua premium/baseRate client gửi (chống giả mạo phí).
        coverage.setBaseRate(premiumCalculationService.resolveServerBaseRate(coverage.getCoverageCode()));
        coverage.setPremium(premiumCalculationService.calculateServerPriced(coverage));

        location.getCoverages().add(coverage);
        policy.recalculateTotalPremium();
        policy.setUpdatedAt(Instant.now());

        log.info("Added coverage {} to location {} on policy {}", code, locationId, policy.getPolicyNumber());
        Policy saved = policyRepository.save(policy);
        if (transactionLedgerService != null) {
            transactionLedgerService.recordTransaction(
                    saved.getPolicyNumber(),
                    saved.getVersion(),
                    com.dungphd.insuranceass.model.TransactionType.ADD_COVERAGE,
                    "System",
                    "Added coverage " + code + " to location " + locationId
            );
        }
        return saved;
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
        // baseRate luôn do server tra bảng theo mã coverage — không nhận giá trị client gửi.
        existingCoverage.setBaseRate(premiumCalculationService.resolveServerBaseRate(existingCoverage.getCoverageCode()));

        // Giá do SERVER quyết định: bỏ qua premium client gửi (chống giả mạo phí).
        existingCoverage.setPremium(premiumCalculationService.calculateServerPriced(existingCoverage));

        policy.recalculateTotalPremium();
        policy.setUpdatedAt(Instant.now());

        log.info("Updated coverage {} in location {} on policy {}", coverageCode, locationId, policy.getPolicyNumber());
        Policy saved = policyRepository.save(policy);
        if (transactionLedgerService != null) {
            transactionLedgerService.recordTransaction(
                    saved.getPolicyNumber(),
                    saved.getVersion(),
                    com.dungphd.insuranceass.model.TransactionType.UPDATE_COVERAGE,
                    "System",
                    "Updated coverage " + coverageCode + " in location " + locationId
            );
        }
        return saved;
    }

    @Override
    public Policy removeCoverage(String policyIdOrNumber, String locationId, String coverageCode) {
        Policy policy = findPolicy(policyIdOrNumber);
        validatePolicyModifiable(policy);

        Location location = findLocationInPolicy(policy, locationId);
        if (location.getCoverages() == null || location.getCoverages().isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy quyền lợi với mã " + coverageCode + " tại địa điểm " + locationId);
        }

        boolean removed = location.getCoverages().removeIf(c -> coverageCode.equalsIgnoreCase(c.getCoverageCode()));
        if (!removed) {
            throw new ResourceNotFoundException("Không tìm thấy quyền lợi với mã " + coverageCode + " tại địa điểm " + locationId);
        }

        policy.recalculateTotalPremium();
        policy.setUpdatedAt(Instant.now());

        log.info("Removed coverage {} from location {} on policy {}", coverageCode, locationId, policy.getPolicyNumber());
        Policy saved = policyRepository.save(policy);
        if (transactionLedgerService != null) {
            transactionLedgerService.recordTransaction(
                    saved.getPolicyNumber(),
                    saved.getVersion(),
                    com.dungphd.insuranceass.model.TransactionType.REMOVE_COVERAGE,
                    "System",
                    "Removed coverage " + coverageCode + " from location " + locationId
            );
        }
        return saved;
    }

    private Policy findPolicy(String policyIdOrNumber) {
        if (policyIdOrNumber == null || policyIdOrNumber.isBlank()) {
            throw new BadRequestException("Vui lòng cung cấp số hợp đồng.");
        }

        Optional<Policy> optionalPolicy = policyRepository.findById(policyIdOrNumber);
        if (optionalPolicy.isPresent()) {
            return optionalPolicy.get();
        }

        return policyRepository.findByPolicyNumber(policyIdOrNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng: " + policyIdOrNumber));
    }

    private Location findLocationInPolicy(Policy policy, String locationId) {
        if (locationId == null || locationId.isBlank()) {
            throw new BadRequestException("Vui lòng cung cấp mã địa điểm.");
        }

        if (policy.getLocations() == null || policy.getLocations().isEmpty()) {
            throw new ResourceNotFoundException("Hợp đồng " + policy.getPolicyNumber() + " chưa có địa điểm nào.");
        }

        return policy.getLocations().stream()
                .filter(loc -> matchLocationId(loc, locationId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa điểm số " + locationId + " trong hợp đồng " + policy.getPolicyNumber()));
    }

    private boolean matchLocationId(Location loc, String targetId) {
        if (loc.getLocationId() == null) return false;
        String locStr = String.valueOf(loc.getLocationId());
        return locStr.equalsIgnoreCase(targetId.trim());
    }

    private Coverage findCoverageInLocation(Location location, String coverageCode) {
        if (coverageCode == null || coverageCode.isBlank()) {
            throw new BadRequestException("Vui lòng cung cấp mã quyền lợi.");
        }

        if (location.getCoverages() == null || location.getCoverages().isEmpty()) {
            throw new ResourceNotFoundException("Địa điểm " + location.getLocationId() + " chưa có quyền lợi nào.");
        }

        return location.getCoverages().stream()
                .filter(c -> coverageCode.equalsIgnoreCase(c.getCoverageCode()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Coverage with code " + coverageCode + " not found in location " + location.getLocationId()));
    }

    private void validatePolicyModifiable(Policy policy) {
        // Siết về DRAFT/QUOTED/BOUND (PLAN §1.4): ACTIVE phải đi endorsement.
        if (policy.getStatus() != PolicyStatus.DRAFT && policy.getStatus() != PolicyStatus.QUOTED
                && policy.getStatus() != PolicyStatus.BOUND) {
            throw new BadRequestException("Không thể sửa gói bảo hiểm khi hợp đồng ở trạng thái " + policy.getStatus()
                    + ". Vui lòng dùng endorsement để thay đổi hợp đồng đã có hiệu lực.");
        }
        checkWrite(policy);
        if (negotiationRepository != null && policy.getStatus() == PolicyStatus.QUOTED
                && (negotiationRepository.existsByPolicyNumberAndStatus(policy.getPolicyNumber(), com.dungphd.insuranceass.model.NegotiationStatus.ACCEPTED)
                    || negotiationRepository.existsByPolicyNumberAndStatus(policy.getPolicyNumber(), com.dungphd.insuranceass.model.NegotiationStatus.PENDING_CONFIRMATION))) {
            throw new BadRequestException("Giá hợp đồng đã chốt hoặc đang chờ xác nhận qua thương lượng, không được sửa đổi phí khi còn ở trạng thái QUOTED.");
        }
    }

    private void checkRead(Policy policy) {
        if (permissionService != null) {
            permissionService.checkPolicyRead(policy);
        }
    }

    private void checkWrite(Policy policy) {
        if (permissionService != null) {
            permissionService.checkPolicyWrite(policy);
        }
    }
}
